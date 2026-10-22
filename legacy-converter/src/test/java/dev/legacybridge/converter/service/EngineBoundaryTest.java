package dev.legacybridge.converter.service;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import static org.junit.jupiter.api.Assertions.*;

class EngineBoundaryTest {
    @Test void synchronousFrontendOverloadsAlsoRequireExplicitData() {
        var path = java.nio.file.Path.of("unused.jar");
        assertThrows(IllegalArgumentException.class, () -> new dev.legacybridge.converter.mapping.MappingAnalysisService()
                .analyze(path, (java.nio.file.Path) null));
        assertThrows(IllegalArgumentException.class, () -> new dev.legacybridge.converter.conversion.SimpleContentConverter()
                .convert(path, path, (java.nio.file.Path) null));
    }

    @Test void serviceCannotInvokeAConsoleFrontendOrManageProcesses() throws Exception {
        for (Class<?> type : List.of(LegacyEngineService.class, EngineJob.class, MappingData.class)) {
            try (var bytes = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                assertNotNull(bytes);
                ClassNode node = new ClassNode();
                new ClassReader(bytes).accept(node, 0);
                for (var method : node.methods) for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call) {
                        assertFalse(call.owner.startsWith("dev/legacybridge/converter/cli/"));
                        assertFalse(call.owner.equals("java/lang/System") && call.name.equals("exit"));
                        assertFalse(call.owner.equals("java/lang/Runtime") &&
                                List.of("exec", "exit", "halt").contains(call.name));
                        assertNotEquals("java/lang/ProcessBuilder", call.owner);
                    }
                    if (instruction instanceof FieldInsnNode field) {
                        assertFalse(field.owner.equals("java/lang/System") &&
                                List.of("in", "out", "err").contains(field.name));
                    }
                }
            }
        }
    }
}
