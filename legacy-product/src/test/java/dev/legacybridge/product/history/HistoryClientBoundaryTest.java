package dev.legacybridge.product.history;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import static org.junit.jupiter.api.Assertions.*;

/** Inspect product bytes without defining a screen or booting the game. Not native UI QA. */
class HistoryClientBoundaryTest {
    @Test void historyWidgetsStayClientOnlyAndDoNotInvokeInstallerOrOsPaths() throws Exception {
        Set<String> prohibited = Set.of("applyJob", "applyJobs", "openJobPath", "openInput", "recoverTransaction",
                "selectJob", "enqueue", "exit", "halt", "openFile", "openUri", "createDirectories");
        for (String type : List.of("HistoryScreen", "HistoryDetailsScreen", "SavedReportScreen", "SavedReportScreen$Line", "SavedReportScreen$Lines", "CompositionScreen", "CohortScreen", "CohortScreen$Line", "CohortScreen$Lines")) {
            var node = read("dev/legacybridge/product/client/" + type);
            var annotations = new ArrayList<org.objectweb.asm.tree.AnnotationNode>();
            if (node.visibleAnnotations != null) annotations.addAll(node.visibleAnnotations);
            if (node.invisibleAnnotations != null) annotations.addAll(node.invisibleAnnotations);
            var onlyIn = annotations.stream().filter(annotation -> annotation.desc.equals("Lnet/minecraftforge/api/distmarker/OnlyIn;"))
                    .findFirst().orElseThrow(() -> new AssertionError(type + " missing physical-side annotation"));
            assertEquals("CLIENT", ((String[]) onlyIn.values.get(1))[1]);
            for (var method : node.methods) for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    assertFalse(prohibited.contains(call.name), type + " invoked " + call.name);
                    assertFalse(call.owner.startsWith("java/nio/file/Files"), type + " performed filesystem I/O");
                    assertFalse(call.owner.startsWith("dev/legacybridge/product/install/"), type + " invoked installer");
                }
            }
        }
    }

    @Test void commonProjectionHasNoGameOrClientReferences() throws Exception {
        for (String type : List.of("history/HistoryPresentation", "history/SavedReport", "history/SavedReportStore", "controller/CohortPresentation", "controller/CohortInspectionView", "controller/MappingDataView")) {
        var node = read("dev/legacybridge/product/" + type);
        for (var method : node.methods) for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call) {
                assertFalse(call.owner.startsWith("net/minecraft"));
                assertFalse(call.owner.startsWith("dev/legacybridge/product/client/"));
            }
        }
    }

    private static ClassNode read(String name) throws IOException {
        try (var stream = HistoryClientBoundaryTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(stream); var node = new ClassNode(); new ClassReader(stream).accept(node, 0); return node;
        }
    }
}
