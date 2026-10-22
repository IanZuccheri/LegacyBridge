package dev.legacybridge.converter.transformation;

import dev.legacybridge.converter.mapping.LegacySymbol;
import dev.legacybridge.converter.mapping.MappingRule;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.ClassNode;

import java.util.Objects;

/** ASM's ClassRemapper with exact-rule gating and audit logging; debug attributes are not skipped. */
public final class LoggedClassRemapper {
    private LoggedClassRemapper() {}

    public static ClassNode remap(ClassNode source, MappingResolver resolver, TransformationLog log) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(log, "log");
        resolver.requireTransformableClass(source.name);
        String location = source.sourceFile == null ? source.name + ".class" : source.name + " (" + source.sourceFile + ")";
        DescriptorTransformer descriptors = new DescriptorTransformer(resolver);
        ClassNode output = new ClassNode();
        source.accept(new ClassRemapper(output, new Remapper() {
            @Override
            public String map(String internalName) {
                String target = resolver.mapClassName(internalName);
                if (!internalName.equals(target)) {
                    MappingRule rule = resolver.requireResolved(LegacySymbol.classSymbol(internalName));
                    add(TransformationLog.Kind.CLASS_REMAP, internalName, target, rule);
                }
                return target;
            }

            @Override
            public String mapMethodName(String owner, String name, String descriptor) {
                return resolver.memberRule(owner, name, descriptor, true).map(rule -> {
                    if (!name.equals(rule.target().name())) {
                        add(TransformationLog.Kind.METHOD_REMAP, owner + "." + name + descriptor,
                                rule.target().owner() + "." + rule.target().name() + rule.target().descriptor(), rule);
                    }
                    return rule.target().name();
                }).orElse(name);
            }

            @Override
            public String mapFieldName(String owner, String name, String descriptor) {
                return resolver.memberRule(owner, name, descriptor, false).map(rule -> {
                    if (!name.equals(rule.target().name())) {
                        add(TransformationLog.Kind.FIELD_REMAP, owner + "." + name + ":" + descriptor,
                                rule.target().owner() + "." + rule.target().name() + ":" + rule.target().descriptor(), rule);
                    }
                    return rule.target().name();
                }).orElse(name);
            }

            @Override
            public String mapRecordComponentName(String owner, String name, String descriptor) {
                // ObjectMethods bootstrap strings and canonical parameter names are not simple member references.
                var field = resolver.memberRule(owner, name, descriptor, false);
                var accessor = resolver.memberRule(owner, name, "()" + descriptor, true);
                if (field.isPresent() && !name.equals(field.get().target().name())
                        || accessor.isPresent() && !name.equals(accessor.get().target().name())) {
                    throw new TransformationException("Record component rename requires a complete ObjectMethods/canonical-parameter recipe: " + owner + "." + name);
                }
                return name;
            }

            @Override
            public String mapDesc(String descriptor) {
                return descriptors.loggedDescriptor(descriptor, location, log);
            }

            @Override
            public String mapMethodDesc(String descriptor) {
                return descriptors.loggedDescriptor(descriptor, location, log);
            }

            @Override
            public String mapSignature(String signature, boolean typeSignature) {
                return descriptors.loggedSignature(signature, typeSignature, location, log);
            }

            @Override
            public String mapAnnotationAttributeName(String descriptor, String name) {
                return resolver.annotationAttributeRule(Type.getType(descriptor).getInternalName(), name).map(rule -> {
                    if (!name.equals(rule.target().name())) {
                        add(TransformationLog.Kind.METHOD_REMAP, name, rule.target().name(), rule);
                    }
                    return rule.target().name();
                }).orElse(name);
            }

            @Override
            public String mapInvokeDynamicMethodName(String name, String descriptor) {
                resolver.requireUnchangedInvokeDynamicName(name, descriptor);
                return name;
            }

            private void add(TransformationLog.Kind kind, String before, String after, MappingRule rule) {
                log.add(kind, location, before, after, MappingResolver.readable(rule.legacy()), rule.confidence(),
                        String.join("; ", rule.evidence()).isBlank() ? "Explicit verified fixture recipe" : String.join("; ", rule.evidence()),
                        resolver.adapterId(rule));
            }
        }) {
            @Override protected MethodVisitor createMethodRemapper(MethodVisitor visitor) {
                return new MethodRemapper(visitor, remapper) {
                    @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
                        String structural = remapper.mapMethodDesc(descriptor);
                        String target = resolver.callDescriptor(owner, name, descriptor);
                        if (!target.equals(structural)) {
                            MappingRule rule = resolver.memberRule(owner, name, descriptor, true).orElseThrow();
                            log.add(TransformationLog.Kind.DESCRIPTOR_REMAP, location, owner + "." + name + descriptor,
                                    rule.target().owner() + "." + rule.target().name() + target,
                                    MappingResolver.readable(rule.legacy()), rule.confidence(), String.join("; ", rule.evidence()), resolver.adapterId(rule));
                        }
                        mv.visitMethodInsn(opcode, remapper.mapType(owner), remapper.mapMethodName(owner, name, descriptor), target, itf);
                    }
                };
            }
        });
        if (MappingResolver.isProtectedClass(output.name)) {
            throw new TransformationException("Protected output class declaration: " + output.name);
        }
        return output;
    }
}
