package dev.legacybridge.converter.transformation;

import dev.legacybridge.converter.conversion.TransformationBindings;
import dev.legacybridge.converter.conversion.extract.ContentPlan;
import dev.legacybridge.converter.mapping.MappingConfidence;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lowers only a graph already accepted in full by StaticContentExtractor.
 * This is an explicit whole-graph adapter recipe, never a generic API-name substitution.
 */
final class LegacyApiTransformer {
    ClassNode transform(Map<String, ClassNode> remapped, Map<String, String> sourceLocations, String entrypoint, ContentPlan plan,
                        TransformationBindings bindings, TransformationLog log) {
        var adapter = bindings.require(AdapterCatalog.BOOTSTRAP);
        ClassNode original = remapped.get(entrypoint);
        if (original == null) throw new TransformationException("Proven mod entrypoint disappeared during class remapping");
        String endpoint = adapter.owner() + "." + adapter.method() + adapter.descriptor();
        for (var source : remapped.values()) {
            String location = sourceLocations.get(source.name);
            for (var field : source.fields) {
                log.add(TransformationLog.Kind.FIELD_REMAP, location, source.name + "." + field.name + " " + field.desc,
                        "META-INF/legacybridge/" + plan.modId() + "/content.json (no retained legacy field)",
                        "simple-content-v1:lower-field", MappingConfidence.VERIFIED,
                        "The complete finite registration graph consumed this field; no legacy field handle escapes.", adapter.id());
            }
            for (var method : source.methods) {
                log.add(TransformationLog.Kind.ADAPTER_CALL, location + "#" + method.name + method.desc,
                        source.name + "." + method.name + method.desc + " [" + method.instructions.size() + " ASM nodes]",
                        endpoint + " + validated declarative plan", "simple-content-v1:lower-method-body",
                        MappingConfidence.VERIFIED,
                        "Whole-method lowering after every executable method was accounted for. Only constructor entry line/this-local survive; removed locals and line ranges have no equivalent instructions.", adapter.id());
            }
            if (!source.name.equals(entrypoint)) {
                log.add(TransformationLog.Kind.ADAPTER_CALL, location, source.name + ".class",
                        "content.json + " + endpoint, "simple-content-v1:consume-registration-holder",
                        MappingConfidence.VERIFIED,
                        "Fully interpreted helper class removed; no arbitrary helpers or external callers are supported.", adapter.id());
            }
        }
        var target = new ClassNode(Opcodes.ASM9);
        target.version = Opcodes.V17;
        target.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER;
        target.name = entrypoint;
        target.superName = "java/lang/Object";
        target.sourceFile = original.sourceFile;
        target.sourceDebug = original.sourceDebug;
        var annotation = new AnnotationNode("Lnet/minecraftforge/fml/common/Mod;");
        annotation.values = new ArrayList<>(List.of("value", plan.modId()));
        target.visibleAnnotations = new ArrayList<>(List.of(annotation));
        var constructor = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        var start = new LabelNode();
        var end = new LabelNode();
        constructor.instructions.add(start);
        MethodNode oldConstructor = original.methods.stream().filter(m -> m.name.equals("<init>") && m.desc.equals("()V"))
                .findFirst().orElseThrow(() -> new TransformationException("Proven constructor missing"));
        for (var instruction : oldConstructor.instructions) {
            if (instruction instanceof LineNumberNode line) {
                constructor.instructions.add(new LineNumberNode(line.line, start));
                break;
            }
        }
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        constructor.instructions.add(new InsnNode(Opcodes.RETURN));
        constructor.instructions.add(end);
        // Only retain an actual source local; do not fabricate debug information for a stripped fixture.
        if (oldConstructor.localVariables != null) {
            oldConstructor.localVariables.stream().filter(v -> v.index == 0).findFirst().ifPresent(v ->
                    constructor.localVariables.add(new LocalVariableNode(v.name, "L" + target.name + ";", null, start, end, 0)));
        }
        target.methods.add(constructor);
        log.add(TransformationLog.Kind.CLASS_REMAP, sourceLocations.get(original.name),
                original.name + " classfile=" + original.version + " access=" + original.access + " registration-holder metadata",
                target.name + " classfile=" + target.version + " access=" + target.access + " native bootstrap metadata",
                "simple-content-v1:bootstrap-header", MappingConfidence.VERIFIED,
                "Java 17 bootstrap retains SourceFile/SourceDebugExtension and @Mod identity; obsolete fields, methods, nest/inner metadata are lowered with the complete graph.", adapter.id());
        return target;
    }

    static String location(ClassNode source) {
        if (source.sourceFile == null) return source.name + ".class";
        int separator = source.name.lastIndexOf('/');
        return (separator < 0 ? "" : source.name.substring(0, separator + 1)) + source.sourceFile;
    }
}
