package dev.legacybridge.converter.conversion.logic;

import dev.legacybridge.converter.mapping.source.ApiIndex;
import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.transformation.TransformationException;
import net.minecraftforge.srgutils.IMappingFile;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Remapper;

import java.util.HashSet;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Namespace composition for trusted declaration metadata only; never loads game/mod classes. */
final class LogicApiNamespace {
    private final IMappingFile official;
    private final IMappingFile srg;
    private final ApiIndex development;
    private final boolean legacy;
    private final Map<String, String> developmentCanonicalNames = new TreeMap<>();
    private final Map<String, String> productionNames = new TreeMap<>();

    LogicApiNamespace(IMappingFile official, IMappingFile srg, ApiIndex development, boolean legacy) {
        this.official = official;
        this.srg = srg;
        this.development = development;
        this.legacy = legacy;
        for (var type : official.getClasses()) {
            OperationControl.checkpoint();
            String canonical = type.getOriginal();
            String runtime = srg.remapClass(type.getMapped());
            // The legacy development API has MCP owners, not official owners. A canonical
            // name can legally name a DIFFERENT class in MCP (e.g. ItemRenderer). Never
            // union both namespaces into an ambiguous global alias table.
            alias(legacy ? runtime : canonical, canonical);
            productionNames.put(canonical, legacy ? runtime : canonical);
        }
    }

    private void alias(String alias, String canonical) {
        String old = developmentCanonicalNames.putIfAbsent(alias, canonical);
        if (old != null && !old.equals(canonical)) {
            throw failure("Ambiguous trusted API namespace alias: " + alias);
        }
    }

    String canonicalClass(String name) { return developmentCanonicalNames.getOrDefault(name, name); }

    String productionClass(String canonical) {
        String result = productionNames.get(canonical);
        if (result == null || result.equals(official.remapClass(canonical))) {
            throw failure("Missing exact " + (legacy ? "source" : "target") + " class mapping: " + canonical);
        }
        return result;
    }

    String productionDescriptor(String descriptor) {
        // Input to this method is expressly the canonical/official namespace.
        return descriptor(descriptor, owner -> productionNames.getOrDefault(owner, owner));
    }

    String canonicalDescriptor(String descriptor) { return descriptor(descriptor, this::canonicalClass); }

    String productionMethod(String canonicalOwner, String officialName, String canonicalDescriptor) {
        if (officialName.equals("<init>")) return officialName;
        var type = official.getClass(canonicalOwner);
        var member = type == null ? null : type.getMethod(officialName, canonicalDescriptor);
        if (member == null) throw failure("Missing exact official method: " + canonicalOwner + "." + officialName + canonicalDescriptor);
        var runtimeType = srg.getClass(type.getMapped());
        var runtimeMember = runtimeType == null ? null : runtimeType.getMethod(member.getMapped(), member.getMappedDescriptor());
        if (runtimeMember == null) throw failure("Missing exact production SRG method: " + canonicalOwner + "." + officialName + canonicalDescriptor);
        return runtimeMember.getMapped();
    }

    String productionField(String canonicalOwner, String officialName, String descriptor) {
        var type=official.getClass(canonicalOwner);
        var member=type==null?null:type.getField(officialName);
        if(member==null || !member.getDescriptor().equals(descriptor))
            throw failure("Missing exact official field: "+canonicalOwner+"."+officialName+descriptor);
        var runtimeType=srg.getClass(type.getMapped());
        var runtime=runtimeType==null?null:runtimeType.getField(member.getMapped());
        if(runtime==null)throw failure("Missing exact SRG field: "+canonicalOwner+"."+officialName);
        return runtime.getMapped();
    }

    ApiIndex normalize() {
        return normalize(development.classes().keySet());
    }

    /** All declarations of reviewed roots and their real ancestry; unrelated game APIs are excluded. */
    ApiIndex normalize(Set<String> developmentRoots) {
        Set<String> reachable = new HashSet<>();
        var pending = new ArrayDeque<String>();
        for (String root : developmentRoots) {
            // A missing direct root is diagnosed by the compiler's exact symbol approval.
            if (development.hasClass(root)) pending.add(root);
        }
        while (!pending.isEmpty()) {
            OperationControl.checkpoint();
            String owner = pending.removeFirst();
            if (owner.startsWith("java/") || !reachable.add(owner)) continue;
            if (reachable.size() > 512) throw failure("Reviewed API hierarchy exceeds 512 types");
            var type = development.getClass(owner);
            if (type == null) throw failure("Required reviewed API ancestor declaration absent: " + owner);
            if (type.superName() != null) pending.add(type.superName());
            pending.addAll(type.interfaces());
        }
        Map<String, ApiIndex.ApiClass> output = new TreeMap<>();
        for (var type : development.classes().values()) {
            if (!reachable.contains(type.name())) continue;
            OperationControl.checkpoint();
            String name = productionOwner(type.name());
            List<ApiIndex.ApiMember> methods = type.methods().stream().map(member -> {
                OperationControl.checkpoint();
                return new ApiIndex.ApiMember(memberName(type.name(), member, true, new HashSet<>()),
                        productionDescriptor(canonicalDescriptor(member.descriptor())), member.access());
            }).toList();
            List<ApiIndex.ApiMember> fields = type.fields().stream().map(member ->
                    new ApiIndex.ApiMember(memberName(type.name(), member, false, new HashSet<>()),
                            productionDescriptor(canonicalDescriptor(member.descriptor())), member.access())).toList();
            unique(methods, "method", name);
            unique(fields, "field", name);
            var normalized = new ApiIndex.ApiClass(name, type.access(),
                    type.superName() == null ? null : productionOwner(type.superName()),
                    type.interfaces().stream().map(this::productionOwner).toList(), methods, fields);
            var old = output.putIfAbsent(name, normalized);
            if (old != null && !old.equals(normalized)) throw failure("Conflicting normalized API declaration: " + name);
        }
        return new ApiIndex(output);
    }

    private String productionOwner(String owner) {
        return productionNames.getOrDefault(canonicalClass(owner), owner);
    }

    private String memberName(String owner, ApiIndex.ApiMember declaration, boolean method, Set<String> visited) {
        if (declaration.name().startsWith("<")) return declaration.name();
        if (!visited.add(owner)) return declaration.name();
        if (visited.size() > 512) throw failure("Trusted API hierarchy exceeds 512 nodes");
        var type = official.getClass(canonicalClass(owner));
        String desc = canonicalDescriptor(declaration.descriptor());
        Set<String> names = new HashSet<>();
        if (type != null) {
            var runtimeType = srg.getClass(type.getMapped());
            if (method) {
                for (var member : type.getMethods()) {
                    if (!member.getDescriptor().equals(desc)) continue;
                    var runtime = runtimeType == null ? null : runtimeType.getMethod(member.getMapped(), member.getMappedDescriptor());
                    String runtimeName = runtime == null ? null : runtime.getMapped();
                    if (declaration.name().equals(member.getOriginal()) || declaration.name().equals(runtimeName)) {
                        if (runtimeName == null) throw failure("Missing SRG declaration for trusted API method " + owner + "." + declaration.name() + desc);
                        names.add(runtimeName);
                    }
                }
            } else {
                for (var member : type.getFields()) {
                    if (!member.getDescriptor().equals(desc)) continue;
                    var runtime = runtimeType == null ? null : runtimeType.getField(member.getMapped());
                    String runtimeName = runtime == null ? null : runtime.getMapped();
                    if (declaration.name().equals(member.getOriginal()) || declaration.name().equals(runtimeName)) {
                        if (runtimeName == null) throw failure("Missing SRG declaration for trusted API field " + owner + "." + declaration.name());
                        names.add(runtimeName);
                    }
                }
            }
        }
        if (names.size() > 1) throw failure("Ambiguous trusted API member mapping: " + owner + "." + declaration.name() + desc);
        if (names.size() == 1) return names.iterator().next();
        // Forge-added overrides can inherit vanilla names even though they have no own Mojang row.
        if (method && (declaration.access() & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) == 0) {
            ApiIndex.ApiClass parent = development.getClass(owner);
            if (parent != null) {
                Set<String> inherited = new HashSet<>();
                if (parent.superName() != null) inherited.add(memberName(parent.superName(), declaration, true, new HashSet<>(visited)));
                for (String contract : parent.interfaces()) inherited.add(memberName(contract, declaration, true, new HashSet<>(visited)));
                inherited.remove(declaration.name());
                if (inherited.size() > 1) throw failure("Conflicting inherited trusted API mappings: " + owner + "." + declaration.name());
                if (inherited.size() == 1) return inherited.iterator().next();
            }
        }
        return declaration.name(); // Unmapped Forge/JDK member names remain exactly as declared.
    }

    private static void unique(List<ApiIndex.ApiMember> members, String kind, String owner) {
        Set<String> signatures = new HashSet<>();
        for (var member : members) {
            if (!signatures.add(member.name() + member.descriptor())) throw failure("Normalization collides on " + kind + " " + owner + "." + member.name());
        }
    }

    static String descriptor(String descriptor, java.util.function.UnaryOperator<String> classes) {
        var remapper = new Remapper() {
            @Override public String map(String name) { return classes.apply(name); }
        };
        return descriptor.startsWith("(") ? remapper.mapMethodDesc(descriptor) : remapper.mapDesc(descriptor);
    }

    private static TransformationException failure(String message) {
        return new TransformationException("LOGIC_MAPPING_DATA_REQUIRED: " + message);
    }
}
