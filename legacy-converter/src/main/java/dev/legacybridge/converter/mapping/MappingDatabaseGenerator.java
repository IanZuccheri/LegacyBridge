package dev.legacybridge.converter.mapping;

import dev.legacybridge.converter.mapping.source.ApiIndex;
import dev.legacybridge.converter.mapping.source.MappingInputs;
import dev.legacybridge.converter.operation.OperationControl;
import net.minecraftforge.srgutils.IMappingFile;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.UnaryOperator;

/** Generates only requested rules from real namespace tables and ASM API declarations. */
public final class MappingDatabaseGenerator {
    private final MappingInputs inputs;
    private final Map<String, String> legacyCanonical = new HashMap<>();
    private final Set<String> conflictingAliases = new java.util.HashSet<>();
    private final Map<String, String> legacyRuntime = new HashMap<>();
    // Small semantic refusal policy, not a replacement mapping table. No target is invented.
    private static final Map<String, String> ADAPTER_POLICY = Map.of(
            "net/minecraftforge/registries/IForgeRegistryEntry", "Registry name ownership was removed; registration requires an adapter.",
            "net/minecraftforge/event/RegistryEvent$Register", "RegistryEvent.Register and RegisterEvent have different registration contracts.",
            "net/minecraftforge/common/capabilities/CapabilityInject", "Capability injection requires token/event-based adaptation.",
            "net/minecraftforge/fml/client/registry/RenderingRegistry", "Renderer registration moved into a different event lifecycle.",
            "net/minecraftforge/common/loot/GlobalLootModifierSerializer", "Global loot modifier serialization requires codec adaptation."
    );

    public MappingDatabaseGenerator(MappingInputs inputs) {
        this.inputs = inputs;
        if (inputs.legacyOfficial() != null && inputs.legacySrg() != null) {
            for (var type : inputs.legacyOfficial().getClasses()) {
                OperationControl.checkpoint();
                String runtime = inputs.legacySrg().remapClass(type.getMapped());
                legacyRuntime.put(type.getOriginal(), runtime);
                alias(type.getOriginal(), type.getOriginal());
                alias(runtime, type.getOriginal());
            }
        }
    }

    public MappingDatabase generate(Collection<LegacySymbol> requested) {
        var builder = MappingDatabase.builder();
        TreeSet<LegacySymbol> symbols = new TreeSet<>(requested);
        for (LegacySymbol symbol : List.copyOf(symbols)) {
            OperationControl.checkpoint();
            if (!symbol.owner().isEmpty()) symbols.add(clazz(symbol.owner()));
            if (!symbol.descriptor().isEmpty()) {
                descriptorOwners(symbol.descriptor()).stream()
                        .filter(owner -> !owner.startsWith("java/") && !owner.startsWith("javax/"))
                        .forEach(owner -> symbols.add(clazz(owner)));
            }
        }
        for (LegacySymbol symbol : symbols) {
            OperationControl.checkpoint();
            if (symbol.kind() != SymbolKind.DESCRIPTOR) builder.add(resolve(symbol));
        }
        return builder.build();
    }

    private void alias(String alias, String canonical) {
        String previous = legacyCanonical.putIfAbsent(alias, canonical);
        if (previous != null && !previous.equals(canonical)) conflictingAliases.add(alias);
    }

    private MappingRule resolve(LegacySymbol symbol) {
        if (!inputs.complete()) return manual(symbol, "Mapping inputs are incomplete; no identity fallback is permitted.");
        if (conflictingAliases.contains(symbol.owner())) return manual(symbol, "Conflicting source namespace aliases.");
        if (!symbol.descriptor().isEmpty() && descriptorOwners(symbol.descriptor()).stream().anyMatch(conflictingAliases::contains)) {
            return manual(symbol, "Descriptor includes a conflicting source namespace alias; no canonical type can be selected.");
        }
        String canonical = legacyCanonical.get(symbol.owner());
        if (canonical != null) return minecraft(symbol, canonical);
        if (symbol.owner().startsWith("net/minecraftforge/")) return forge(symbol);
        return manual(symbol, "No pinned mapping source covers this owner or namespace.");
    }

    private MappingRule minecraft(LegacySymbol symbol, String canonical) {
        var sourceClass = inputs.legacyOfficial().getClass(canonical);
        String sourceRuntime = legacyRuntime.get(canonical);
        var sourceApi = inputs.legacyApi().getClass(sourceRuntime);
        if (sourceApi == null) return manual(symbol, "Source owner is not present in the supplied Forge API inventory.");
        var targetClass = inputs.targetOfficial().getClass(canonical);
        var targetApi = inputs.targetApi().getClass(canonical);
        if (targetClass == null || targetApi == null) {
            return missingClass(symbol, canonical, true);
        }
        if (symbol.kind() == SymbolKind.CLASS) {
            if (!compatibleType(sourceApi.access(), targetApi.access())) {
                return manual(symbol, "Class/interface kind or visibility changed between API inventories.");
            }
            return resolved(symbol, new TargetSymbol(SymbolKind.CLASS, canonical, "", ""),
                    "Exact official owner in both versions, corroborated by both Forge API inventories.");
        }
        String descriptor = translate(symbol.descriptor(), owner -> legacyCanonical.getOrDefault(owner, owner));
        var sourceSrgClass = inputs.legacySrg().getClass(sourceClass.getMapped());
        if (symbol.kind() == SymbolKind.FIELD) {
            List<IMappingFile.IField> matches = new ArrayList<>();
            for (var member : sourceClass.getFields()) {
                OperationControl.checkpoint();
                String srg = sourceSrgClass == null ? member.getMapped() : sourceSrgClass.remapField(member.getMapped());
                if ((symbol.name().equals(member.getOriginal()) || symbol.name().equals(srg))
                        && descriptor.equals(member.getDescriptor())) matches.add(member);
            }
            if (matches.size() != 1) return manual(symbol, "Source field alias and exact descriptor are absent or ambiguous.");
            var source = matches.get(0);
            String sourceDevDescriptor = translate(descriptor, owner -> legacyRuntime.getOrDefault(owner, owner));
            var sourceDeclaration = find(sourceApi.fields(), source.getOriginal(), sourceDevDescriptor);
            var target = targetClass.getField(source.getOriginal());
            if (target == null) return removed(symbol, "Official field name is absent from the exact target owner; no replacement is known.");
            if (!descriptor.equals(target.getDescriptor())) return unsupportedAdapter(symbol, "Field descriptor changed; an adapter must define conversion semantics.");
            var targetDeclaration = find(targetApi.fields(), target.getOriginal(), target.getDescriptor());
            if (!compatibleMember(sourceDeclaration, targetDeclaration)) return manual(symbol, "Field declaration, staticness, or visibility could not be verified.");
            var targetSrgClass = inputs.targetSrg().getClass(targetClass.getMapped());
            var targetSrg = targetSrgClass == null ? null : targetSrgClass.getField(target.getMapped());
            String targetName = targetSrg != null ? targetSrg.getMapped()
                    : target.getMapped().equals(target.getOriginal()) ? target.getOriginal() : null;
            if (targetName == null) return manual(symbol, "Target SRG field name is unavailable.");
            return resolved(symbol, new TargetSymbol(SymbolKind.FIELD, canonical, targetName, descriptor),
                    "Exact official field signature and Forge declaration; target production SRG member name.");
        }
        List<IMappingFile.IMethod> matches = new ArrayList<>();
        for (var member : sourceClass.getMethods()) {
            OperationControl.checkpoint();
            String srg = sourceSrgClass == null ? member.getMapped()
                    : sourceSrgClass.remapMethod(member.getMapped(), member.getMappedDescriptor());
            if ((symbol.name().equals(member.getOriginal()) || symbol.name().equals(srg))
                    && descriptor.equals(member.getDescriptor())) matches.add(member);
        }
        if (matches.size() != 1) return manual(symbol, "Source method/constructor alias and exact descriptor are absent or ambiguous.");
        var source = matches.get(0);
        String sourceDevDescriptor = translate(descriptor, owner -> legacyRuntime.getOrDefault(owner, owner));
        var sourceDeclaration = find(sourceApi.methods(), source.getOriginal(), sourceDevDescriptor);
        var target = targetClass.getMethod(source.getOriginal(), descriptor);
        if (target == null) {
            long overloads = targetClass.getMethods().stream().filter(member -> member.getOriginal().equals(source.getOriginal())).count();
            if (overloads > 0) return unsupportedAdapter(symbol, "Same official member name has different target descriptors; overload selection is not automatic.");
            return removed(symbol, "Exact official member is absent from the target owner; no replacement is known.");
        }
        var targetDeclaration = find(targetApi.methods(), target.getOriginal(), descriptor);
        if (!compatibleMember(sourceDeclaration, targetDeclaration)) return manual(symbol, "Method declaration, staticness, or visibility could not be verified.");
        var targetSrgClass = inputs.targetSrg().getClass(targetClass.getMapped());
        var targetSrg = targetSrgClass == null ? null : targetSrgClass.getMethod(target.getMapped(), target.getMappedDescriptor());
        String targetName = symbol.kind() == SymbolKind.CONSTRUCTOR ? "<init>"
                : targetSrg != null ? targetSrg.getMapped()
                : target.getMapped().equals(target.getOriginal()) ? target.getOriginal() : null;
        if (targetName == null) return manual(symbol, "Target SRG method name is unavailable.");
        return resolved(symbol, new TargetSymbol(symbol.kind(), canonical, targetName, descriptor),
                "Exact official method/constructor signature and Forge declaration; target production SRG member name.");
    }

    private MappingRule forge(LegacySymbol symbol) {
        var source = inputs.legacyApi().getClass(symbol.owner());
        if (source == null) return manual(symbol, "Forge source owner is absent from the supplied API artifacts.");
        if (ADAPTER_POLICY.containsKey(symbol.owner())) return unsupportedAdapter(symbol, ADAPTER_POLICY.get(symbol.owner()));
        var target = inputs.targetApi().getClass(symbol.owner());
        if (target == null) return missingClass(symbol, symbol.owner(), false);
        if (symbol.kind() == SymbolKind.CLASS) {
            if (!compatibleType(source.access(), target.access())) return manual(symbol, "Forge class/interface kind or visibility changed.");
            return resolved(symbol, new TargetSymbol(SymbolKind.CLASS, symbol.owner(), "", ""), "Same exact Forge class in both pinned API artifacts.");
        }
        // Forge declarations use the development namespace. Normalize Minecraft owner types first.
        String targetDescriptor = translate(symbol.descriptor(), owner -> legacyCanonical.getOrDefault(owner, owner));
        String sourceDescriptor = translate(symbol.descriptor(), owner -> legacyRuntime.getOrDefault(owner, owner));
        var sourceMembers = symbol.kind() == SymbolKind.FIELD ? source.fields() : source.methods();
        var targetMembers = symbol.kind() == SymbolKind.FIELD ? target.fields() : target.methods();
        var from = find(sourceMembers, symbol.name(), sourceDescriptor);
        if (from == null) return manual(symbol, "Forge member is not declared on this exact owner; inherited/renamed members need review.");
        var to = find(targetMembers, symbol.name(), targetDescriptor);
        if (to == null) {
            if (targetMembers.stream().anyMatch(member -> member.name().equals(symbol.name()))) {
                return unsupportedAdapter(symbol, "Forge member descriptors changed; no automatic argument/return adaptation.");
            }
            return removed(symbol, "Forge member is absent from the exact target owner; no automatic equivalent is known.");
        }
        if (!compatibleMember(from, to)) return manual(symbol, "Forge member staticness or visibility changed.");
        // Reject descriptors whose platform classes disappeared even if the string signature matches.
        for (String owner : descriptorOwners(targetDescriptor)) {
            if ((owner.startsWith("net/minecraft/") || owner.startsWith("net/minecraftforge/"))
                    && !inputs.targetApi().hasClass(owner)) return manual(symbol, "Descriptor includes a platform class missing from the target inventory.");
        }
        return resolved(symbol, new TargetSymbol(symbol.kind(), symbol.owner(), symbol.name(), targetDescriptor),
                "Exact Forge member declaration after official descriptor composition; semantic behavior remains unreviewed.");
    }

    private MappingRule missingClass(LegacySymbol symbol, String canonical, boolean minecraft) {
        String simple = canonical.substring(canonical.lastIndexOf('/') + 1);
        List<String> candidates = inputs.targetApi().classes().keySet().stream()
                .peek(ignored -> OperationControl.checkpoint())
                .filter(owner -> owner.startsWith(minecraft ? "net/minecraft/" : "net/minecraftforge/"))
                .filter(owner -> owner.substring(owner.lastIndexOf('/') + 1).equals(simple)).sorted().toList();
        if (!candidates.isEmpty()) {
            TargetSymbol candidate = symbol.kind() == SymbolKind.CLASS && candidates.size() == 1
                    ? new TargetSymbol(SymbolKind.CLASS, candidates.get(0), "", "") : null;
            return new MappingRule(symbol, candidate, candidate == null ? MappingCategory.NO_AUTOMATIC_EQUIVALENT : MappingCategory.PACKAGE_MOVE,
                    MappingConfidence.HEURISTIC, MappingStatus.MANUAL,
                    List.of("Same simple class name is only a candidate, not an authoritative cross-version mapping.",
                            "candidates=" + String.join(",", candidates.stream().limit(8).toList())));
        }
        return removed(symbol, "Canonical owner is absent from the pinned target inventory; no automatic equivalent is known.");
    }

    private static ApiIndex.ApiMember find(List<ApiIndex.ApiMember> members, String name, String descriptor) {
        return members.stream().peek(ignored -> OperationControl.checkpoint())
                .filter(member -> member.name().equals(name) && member.descriptor().equals(descriptor)).findFirst().orElse(null);
    }

    private static boolean compatibleType(int from, int to) {
        int mask = Opcodes.ACC_INTERFACE | Opcodes.ACC_ANNOTATION | Opcodes.ACC_ENUM;
        return (from & mask) == (to & mask) && (to & Opcodes.ACC_PUBLIC) != 0;
    }

    private static boolean compatibleMember(ApiIndex.ApiMember from, ApiIndex.ApiMember to) {
        if (from == null || to == null) return false;
        int visibility = Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED;
        return (from.access() & Opcodes.ACC_STATIC) == (to.access() & Opcodes.ACC_STATIC)
                && (to.access() & visibility) != 0
                && ((from.access() & Opcodes.ACC_PUBLIC) == 0 || (to.access() & Opcodes.ACC_PUBLIC) != 0);
    }

    private static MappingRule resolved(LegacySymbol symbol, TargetSymbol target, String evidence) {
        MappingCategory category = !symbol.descriptor().equals(target.descriptor()) ? MappingCategory.DESCRIPTOR_CHANGE
                : !packageName(symbol.owner()).equals(packageName(target.owner())) ? MappingCategory.PACKAGE_MOVE
                : !symbol.owner().equals(target.owner()) || !symbol.name().equals(target.name()) ? MappingCategory.PURE_RENAME
                : MappingCategory.IDENTITY;
        return new MappingRule(symbol, target, category, MappingConfidence.HIGH, MappingStatus.RESOLVED,
                List.of(evidence, "Structural symbol resolution only; no semantic approval and no bytecode rewriting."));
    }

    private static String packageName(String owner) { return owner.substring(0, Math.max(0, owner.lastIndexOf('/'))); }
    private static LegacySymbol clazz(String owner) { return new LegacySymbol(SymbolKind.CLASS, owner, "", ""); }
    private static MappingRule manual(LegacySymbol symbol, String evidence) {
        return new MappingRule(symbol, null, MappingCategory.NO_AUTOMATIC_EQUIVALENT, MappingConfidence.NONE, MappingStatus.MANUAL, List.of(evidence));
    }
    private static MappingRule unsupportedAdapter(LegacySymbol symbol, String evidence) {
        // A whole-graph content lowerer is not a semantically equivalent adapter for an arbitrary API call.
        return new MappingRule(symbol, null, MappingCategory.NO_AUTOMATIC_EQUIVALENT, MappingConfidence.HIGH,
                MappingStatus.UNSUPPORTED, List.of(evidence,
                "No implemented semantically equivalent direct-call adapter; declarative fixture adapter cannot satisfy arbitrary call."));
    }
    private static MappingRule removed(LegacySymbol symbol, String evidence) {
        return new MappingRule(symbol, null, MappingCategory.REMOVED_API, MappingConfidence.HIGH, MappingStatus.UNSUPPORTED, List.of(evidence));
    }

    static Set<String> descriptorOwners(String descriptor) {
        TreeSet<String> result = new TreeSet<>();
        translate(descriptor, owner -> { result.add(owner); return owner; });
        return result;
    }

    private static String translate(String descriptor, UnaryOperator<String> mapper) {
        Type type = Type.getType(descriptor);
        return translateType(type, mapper);
    }
    private static String translateType(Type type, UnaryOperator<String> mapper) {
        return switch (type.getSort()) {
            case Type.OBJECT -> "L" + mapper.apply(type.getInternalName()) + ";";
            case Type.ARRAY -> "[".repeat(type.getDimensions()) + translateType(type.getElementType(), mapper);
            case Type.METHOD -> {
                StringBuilder result = new StringBuilder("(");
                for (Type argument : type.getArgumentTypes()) result.append(translateType(argument, mapper));
                yield result.append(')').append(translateType(type.getReturnType(), mapper)).toString();
            }
            default -> type.getDescriptor();
        };
    }
}
