package dev.legacybridge.converter.transformation;

import dev.legacybridge.converter.mapping.LegacySymbol;
import dev.legacybridge.converter.mapping.MappingRule;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Descriptor-only pipeline stage. Only exact VERIFIED class-component substitutions are allowed;
 * changing primitive types, argument count/order, array rank or return shape needs a concrete recipe.
 * Chained source/target rules are rejected by MappingResolver, so a second stage cannot map A to B to C.
 */
public final class DescriptorTransformer {
    private final MappingResolver resolver;

    public DescriptorTransformer(MappingResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    public record DescriptorMapping(String before, String after, List<MappingRule> rules) {
        public DescriptorMapping {
            rules = List.copyOf(rules);
        }
    }

    public DescriptorMapping remap(String descriptor) {
        LegacySymbol.descriptor(descriptor); // Strict parser, not ASM Type's permissive factory alone.
        Map<LegacySymbol, MappingRule> used = new LinkedHashMap<>();
        String structural = mapType(Type.getType(descriptor), used).getDescriptor();
        resolver.findRule(LegacySymbol.descriptor(descriptor)).ifPresent(rule -> {
            if (!rule.target().descriptor().equals(structural)) {
                throw new TransformationException("Descriptor ABI change has no component-only recipe: " + descriptor
                        + " -> " + rule.target().descriptor());
            }
            used.put(rule.legacy(), rule);
        });
        return new DescriptorMapping(descriptor, structural, List.copyOf(used.values()));
    }

    /** Rewrites descriptor-bearing locations while leaving class declarations and member names alone. */
    public ClassNode transform(ClassNode source, TransformationLog log) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(log, "log");
        resolver.requireOwnedDeclaration(source.name);
        String location = source.sourceFile == null ? source.name + ".class" : source.name + " (" + source.sourceFile + ")";
        ClassNode output = new ClassNode();
        source.accept(new ClassRemapper(output, new Remapper() {
            @Override
            public String mapMethodName(String owner, String name, String descriptor) {
                requireDescriptorOnlyMember(owner, name, descriptor, true);
                return name;
            }

            @Override
            public String mapFieldName(String owner, String name, String descriptor) {
                requireDescriptorOnlyMember(owner, name, descriptor, false);
                return name;
            }

            @Override
            public String mapAnnotationAttributeName(String descriptor, String name) {
                resolver.annotationAttributeRule(Type.getType(descriptor).getInternalName(), name).ifPresent(rule -> {
                    if (!rule.target().name().equals(name)) {
                        throw new TransformationException("Annotation attribute name changes belong to the earlier ClassRemapper stage");
                    }
                });
                return name;
            }

            @Override
            public String mapInvokeDynamicMethodName(String name, String descriptor) {
                resolver.requireUnchangedInvokeDynamicName(name, descriptor);
                return name;
            }

            @Override
            public String mapDesc(String descriptor) {
                return loggedDescriptor(descriptor, location, log);
            }

            @Override
            public String mapMethodDesc(String descriptor) {
                return loggedDescriptor(descriptor, location, log);
            }

            @Override
            public String mapSignature(String signature, boolean typeSignature) {
                return loggedSignature(signature, typeSignature, location, log);
            }

            @Override
            public Object mapValue(Object value) {
                if (value instanceof Type type) {
                    if (type.getSort() == Type.VOID) {
                        return value; // Valid as an annotation Class literal, not a field descriptor.
                    }
                    return Type.getType(loggedDescriptor(type.getDescriptor(), location, log));
                }
                return super.mapValue(value);
            }
        }));
        return output;
    }

    private void requireDescriptorOnlyMember(String owner, String name, String descriptor, boolean method) {
        resolver.memberRule(owner, name, descriptor, method).ifPresent(rule -> {
            if (!rule.target().owner().equals(owner) || !rule.target().name().equals(name)) {
                throw new TransformationException("Owner/member name changes belong to the earlier ClassRemapper stage: "
                        + MappingResolver.readable(rule.legacy()));
            }
        });
    }

    String loggedDescriptor(String descriptor, String location, TransformationLog log) {
        DescriptorMapping result = remap(descriptor);
        if (!result.before().equals(result.after())) {
            for (MappingRule rule : result.rules()) {
                log.add(TransformationLog.Kind.DESCRIPTOR_REMAP, location, result.before(), result.after(),
                        MappingResolver.readable(rule.legacy()), rule.confidence(),
                        "Verified descriptor class-component substitution; " + String.join("; ", rule.evidence()),
                        resolver.adapterId(rule));
            }
        }
        return result.after();
    }

    String loggedSignature(String signature, boolean typeSignature, String location, TransformationLog log) {
        if (signature == null) {
            return null;
        }
        var used = new LinkedHashMap<LegacySymbol, MappingRule>();
        String mapped = new Remapper() {
            @Override
            public String map(String internalName) {
                resolver.findRule(LegacySymbol.classSymbol(internalName)).ifPresent(rule -> used.put(rule.legacy(), rule));
                return resolver.mapClassName(internalName);
            }
        }.mapSignature(signature, typeSignature);
        if (!signature.equals(mapped)) {
            for (MappingRule rule : used.values()) {
                log.add(TransformationLog.Kind.DESCRIPTOR_REMAP, location, signature, mapped,
                        MappingResolver.readable(rule.legacy()), rule.confidence(),
                        "Verified generic-signature class-component substitution; " + String.join("; ", rule.evidence()),
                        resolver.adapterId(rule));
            }
        }
        return mapped;
    }

    private Type mapType(Type input, Map<LegacySymbol, MappingRule> used) {
        if (input.getSort() == Type.OBJECT) {
            resolver.findRule(LegacySymbol.classSymbol(input.getInternalName())).ifPresent(rule -> used.put(rule.legacy(), rule));
            return Type.getObjectType(resolver.mapClassName(input.getInternalName()));
        }
        if (input.getSort() == Type.ARRAY) {
            return Type.getType("[".repeat(input.getDimensions()) + mapType(input.getElementType(), used).getDescriptor());
        }
        if (input.getSort() == Type.METHOD) {
            Type[] arguments = input.getArgumentTypes();
            for (int i = 0; i < arguments.length; i++) {
                arguments[i] = mapType(arguments[i], used);
            }
            return Type.getMethodType(mapType(input.getReturnType(), used), arguments);
        }
        return input;
    }
}
