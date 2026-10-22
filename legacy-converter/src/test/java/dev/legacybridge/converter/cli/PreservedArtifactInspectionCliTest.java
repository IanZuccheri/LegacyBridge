package dev.legacybridge.converter.cli;

import dev.legacybridge.converter.service.*;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

class PreservedArtifactInspectionCliTest {
    @Test void badArgumentsCannotFallbackToEnvironmentOrProduceOutput() {
        for(String[] args:List.of(new String[]{"inspect-preserved"},new String[]{"inspect-preserved","a.jar","b.jar","--output","a.jar"})) {
            var out=new ByteArrayOutputStream(); assertEquals(2,LegacyBridgeCli.run(args,new PrintStream(out),new PrintStream(new ByteArrayOutputStream()),Map.of("LEGACYBRIDGE_MAPPING_BUNDLE","ignored"))); assertEquals(0,out.size());
        }
    }
    @Test void explicitHashesAndDistinctInputsAreMandatory() {
        assertThrows(IllegalArgumentException.class,()->new PreservedArtifactRequest(Path.of("a"),"missing",Path.of("b"),"a".repeat(64),MappingData.unavailable()));
        assertThrows(IllegalArgumentException.class,()->new PreservedArtifactRequest(Path.of("a"),"a".repeat(64),Path.of("a"),"b".repeat(64),MappingData.unavailable()));
    }
    @Test void includedOtherScopesNeverSilentlySupplyFullLogicData() throws Exception {
        try(var engine=new LegacyEngineService()) {
            for(var data:List.of(MappingData.builtinSimpleContent(),MappingData.builtinCohortPreview())) {
                var result=engine.inspectPreservedArtifact(new PreservedArtifactRequest(Path.of("missing-source"),"a".repeat(64),Path.of("missing-target"),"b".repeat(64),data)).completion().toCompletableFuture().get(10,TimeUnit.SECONDS);
                assertEquals("MAPPING_SCOPE_UNSUPPORTED",result.error().orElseThrow().code()); assertTrue(result.value().isEmpty());
            }
        }
    }
    @Test void inspectorCannotInvokeConverterPublisherGameOrOriginalClassLoader() throws Exception {
        for(String name:List.of("PreservedArtifactInspector","PreservedArtifactInspector$1","PreservedArtifactInspector$2","PreservedArtifactInspector$3","PreservedArtifactInspector$3$1","PreservedArtifactInspector$4")) {
            try(var in=getClass().getResourceAsStream("/dev/legacybridge/converter/conversion/"+name+".class")) {
                assertNotNull(in,name); var node=new ClassNode(); new ClassReader(in).accept(node,0);
                for(var method:node.methods) for(var instruction:method.instructions) if(instruction instanceof MethodInsnNode call) {
                    assertFalse(call.owner.contains("LogicPreservingConverter") || call.owner.contains("LoggedClassRemapper"),call.toString());
                    assertFalse(call.owner.startsWith("net/minecraft") || call.owner.startsWith("dev/legacybridge/product/install/"));
                    assertFalse(Set.of("defineClass","loadClass","forName","exit","halt","publish","prepare","convert","newOutputStream","createDirectories").contains(call.name),call.owner+"."+call.name);
                }
            }
        }
    }
}
