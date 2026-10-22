package dev.legacybridge.converter.conversion.extract;

import dev.legacybridge.converter.mapping.source.MappingInputs;
import net.minecraftforge.srgutils.IMappingFile;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Resolves legacy production SRG and official development aliases against actual source declarations. */
final class LegacyApiNames {
    record Member(String owner, String name, String descriptor) { }
    private final MappingInputs inputs;
    private final Map<String, String> owners = new HashMap<>();
    private final Map<String, IMappingFile.IClass> definitions = new HashMap<>();
    private final Set<String> collisions = new HashSet<>();

    LegacyApiNames(MappingInputs inputs) {
        this.inputs = inputs;
        for (var definition : inputs.legacyOfficial().getClasses()) {
            String runtime = inputs.legacySrg().remapClass(definition.getMapped());
            alias(runtime, runtime);
            alias(definition.getOriginal(), runtime);
            definitions.put(runtime, definition);
        }
    }

    private void alias(String alias, String target) {
        String old = owners.putIfAbsent(alias, target);
        if (old != null && !old.equals(target)) collisions.add(alias);
    }

    String owner(String owner) {
        if (collisions.contains(owner)) throw new Refusal("Conflicting legacy namespace alias: " + owner);
        return owners.getOrDefault(owner, owner);
    }

    String descriptor(String descriptor) {
        try { return type(Type.getType(descriptor)); }
        catch (IllegalArgumentException invalid) { throw new Refusal("Malformed JVM descriptor: " + descriptor); }
    }

    private String type(Type type) {
        return switch (type.getSort()) {
            case Type.OBJECT -> "L" + owner(type.getInternalName()) + ";";
            case Type.ARRAY -> "[".repeat(type.getDimensions()) + type(type.getElementType());
            case Type.METHOD -> {
                var result = new StringBuilder("(");
                for (Type arg : type.getArgumentTypes()) result.append(type(arg));
                yield result.append(')').append(type(type.getReturnType())).toString();
            }
            default -> type.getDescriptor();
        };
    }

    Member member(String rawOwner, String rawName, String rawDescriptor, boolean field, boolean isStatic) {
        String owner = owner(rawOwner);
        String descriptor = descriptor(rawDescriptor);
        String name = rawName;
        var definition = definitions.get(owner);
        if (definition != null) {
            var srg = inputs.legacySrg().getClass(definition.getMapped());
            Set<String> candidates = new HashSet<>();
            if (field) {
                for (var member : definition.getFields()) {
                    String alias = srg == null ? member.getMapped() : srg.remapField(member.getMapped());
                    if ((rawName.equals(member.getOriginal()) || rawName.equals(alias))
                            && descriptor.equals(descriptor(member.getDescriptor()))) candidates.add(member.getOriginal());
                }
            } else {
                for (var member : definition.getMethods()) {
                    String alias = srg == null ? member.getMapped()
                            : srg.remapMethod(member.getMapped(), member.getMappedDescriptor());
                    if ((rawName.equals(member.getOriginal()) || rawName.equals(alias))
                            && descriptor.equals(descriptor(member.getDescriptor()))) candidates.add(member.getOriginal());
                }
            }
            if (candidates.size() != 1) throw new Refusal("Absent or ambiguous source mapping: " + rawOwner + "." + rawName + rawDescriptor);
            name = candidates.iterator().next();
        }
        var api = inputs.legacyApi().getClass(owner);
        if (api == null) throw new Refusal("Source API declaration missing: " + owner);
        String canonicalName = name;
        boolean found = (field ? api.fields() : api.methods()).stream().anyMatch(member ->
                member.name().equals(canonicalName) && member.descriptor().equals(descriptor)
                        && ((member.access() & Opcodes.ACC_STATIC) != 0) == isStatic
                        && (member.access() & Opcodes.ACC_PUBLIC) != 0);
        if (!found) throw new Refusal("Source API member is not public or signature/staticness is unverified: "
                + owner + "." + name + descriptor);
        return new Member(owner, name, descriptor);
    }

    static final class Refusal extends RuntimeException {
        Refusal(String message) { super(message); }
    }
}
