package dev.legacybridge.product.controller;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Reads actual compiled Forge-facing bytes without loading screens. Not visual/runtime proof. */
class PreservedDeveloperClientBoundaryTest {
    private ClassNode read(String name) throws Exception {
        try(var input=getClass().getResourceAsStream("/dev/legacybridge/product/"+name+".class")) {
            assertNotNull(input); var node=new ClassNode(); new ClassReader(input).accept(node,0); return node;
        }
    }
    @Test void screenAndWidgetsArePhysicalClientOnlyAndNeverPerformBlockingIoOrCallInstaller() throws Exception {
        for(String name:List.of("PreservedDeveloperScreen","PreservedDeveloperScreen$Consent","PreservedDeveloperScreen$Lines","PreservedDeveloperScreen$Line",
                "PreservedPreparationScreen","PreservedPreparationScreen$Lines","PreservedPreparationScreen$Line")) {
            var node=read("client/"+name); List<AnnotationNode> annotations=new ArrayList<>();
            if(node.visibleAnnotations!=null) annotations.addAll(node.visibleAnnotations);
            if(node.invisibleAnnotations!=null) annotations.addAll(node.invisibleAnnotations);
            var only=annotations.stream().filter(a->a.desc.equals("Lnet/minecraftforge/api/distmarker/OnlyIn;")).findFirst().orElseThrow();
            assertEquals("CLIENT",((String[])only.values.get(1))[1]);
            for(var m:node.methods) for(var i:m.instructions) if(i instanceof MethodInsnNode call) {
                assertFalse(call.owner.startsWith("java/nio/file/Files"),call.toString());
                assertFalse(call.owner.startsWith("java/io/")||call.owner.startsWith("java/net/"));
                assertFalse(call.owner.startsWith("dev/legacybridge/converter/cli"));
                assertFalse(call.owner.startsWith("dev/legacybridge/converter/mapping/"));
                if(call.owner.startsWith("dev/legacybridge/product/install/")) {
                    // Immutable observations are intentional; installer/store invocation is not.
                    String type=call.owner.substring("dev/legacybridge/product/install/".length());
                    boolean observation=switch(type) {
                        case "InstallSession" -> call.name.equals("<init>");
                        case "PreservedAdmissionResult" -> Set.of("inspection","state","transaction","planSha256").contains(call.name);
                        case "PreservedInstallResult" -> Set.of("state","transaction","planSha256","installed").contains(call.name);
                        case "PreservedUpdatePreview" -> call.name.equals("identity");
                        case "PreservedUpdatePreview$Identity" -> Set.of("modId","previousVersion","replacementVersion","previousArtifactSha256","replacementArtifactSha256").contains(call.name);
                        default -> false;
                    };
                    assertTrue(observation,type+"."+call.name);
                }
                String location=name+"."+m.name+" -> "+call.owner+"."+call.name;
                assertFalse(Set.of("newSingleThreadExecutor","newFixedThreadPool","exit","halt","startProcess","loadClass").contains(call.name),location);
                assertFalse(call.owner.equals("java/lang/Thread")&&Set.of("sleep","join").contains(call.name),location);
                assertFalse(call.owner.equals("java/lang/Object")&&call.name.equals("wait"),location);
                if(m.name.equals("render")||m.name.equals("tick"))
                    assertFalse(Set.of("reviewPreservedInstall","confirmPreservedInstall","refreshPreservedHistory","preparePreserved",
                            "reviewPreservedUpdate","confirmPreservedUpdate","refreshPreservedUpdateHistory").contains(call.name));
            }
        }
    }
    @Test void formAndSnapshotBridgeDoNotResolveGameOrClientCode() throws Exception {
        for(String name:List.of("PreservedDeveloperForm","PreservedHostSession","PreservedPreparationForm","PreservedPreparationRequest","PreservedPreparationView")) {
            var node=read("controller/"+name);
            for(var m:node.methods) for(var i:m.instructions) if(i instanceof MethodInsnNode call) {
                assertFalse(call.owner.startsWith("net/minecraft")); assertFalse(call.owner.startsWith("dev/legacybridge/product/client"));
                assertFalse(call.owner.equals("java/nio/file/Files"));
            }
        }
    }
    @Test void developerRouteUsesExplicitFalseByDefaultFlagAndSeparateFactory() throws Exception {
        var hook=read("client/ClientMenuHooks"); var clinit=hook.methods.stream().filter(m->m.name.equals("<clinit>")).findFirst().orElseThrow();
        boolean property=false,booleanProperty=false;
        for(var i:clinit.instructions) { if(i instanceof LdcInsnNode l&&l.cst.equals("legacybridge.developer.preservedUi")) property=true;
            if(i instanceof MethodInsnNode c&&c.owner.equals("java/lang/Boolean")&&c.name.equals("getBoolean")) booleanProperty=true; }
        assertTrue(property&&booleanProperty);
        var init=hook.methods.stream().filter(m->m.name.equals("afterTitleInit")).findFirst().orElseThrow();
        assertTrue(Arrays.stream(init.instructions.toArray()).anyMatch(i->i instanceof FieldInsnNode f&&f.name.equals("PRESERVED_DEVELOPER_UI")&&f.getOpcode()==Opcodes.GETSTATIC));
        var screen=read("client/PreservedDeveloperScreen");
        assertTrue(screen.methods.stream().flatMap(m->Arrays.stream(m.instructions.toArray())).anyMatch(i->i instanceof MethodInsnNode c&&c.name.equals("forPreservedDevelopment")));
        var publicScreen=read("client/ConversionScreen");
        assertFalse(publicScreen.methods.stream().flatMap(m->Arrays.stream(m.instructions.toArray())).anyMatch(i->i instanceof MethodInsnNode c&&c.name.equals("forPreservedDevelopment")));
    }
    @Test void shutdownIsManualStopAndOnlyReceiptSafeOperationMayReachIt() throws Exception {
        var node=read("client/PreservedDeveloperScreen"); var update=node.methods.stream().filter(m->m.name.equals("update")).findFirst().orElseThrow();
        var calls=Arrays.stream(update.instructions.toArray()).filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).map(c->c.name).toList();
        assertTrue(calls.contains("safeToClose")); assertTrue(calls.contains("worldClosed")); assertTrue(calls.contains("busy")); assertTrue(calls.contains("operationId")); assertTrue(calls.contains("stop"));
        assertFalse(calls.contains("exit")); assertFalse(calls.contains("exec"));
    }
    @Test void sameOwnedScreenRoutesUpdatesThroughCoordinatorAndNeverFromHistoryOrTick() throws Exception {
        var node=read("client/PreservedDeveloperScreen");
        var calls=node.methods.stream().flatMap(m->Arrays.stream(m.instructions.toArray()))
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        for(String name:List.of("reviewPreservedUpdate","confirmPreservedUpdate","refreshPreservedUpdateHistory"))
            assertTrue(calls.stream().anyMatch(c->c.owner.equals("dev/legacybridge/product/controller/ConversionController")&&c.name.equals(name)),name);
        for(String method:List.of("details","filteredUpdates","render","tick")) {
            var m=node.methods.stream().filter(v->v.name.equals(method)).findFirst().orElseThrow();
            for(var i:m.instructions) if(i instanceof MethodInsnNode c)
                assertFalse(Set.of("set","reviewStarted","consume","reviewPreservedUpdate","confirmPreservedUpdate").contains(c.name),method+" -> "+c.name);
        }
        var publicScreen=read("client/ConversionScreen");
        assertFalse(publicScreen.methods.stream().flatMap(m->Arrays.stream(m.instructions.toArray()))
                .anyMatch(i->i instanceof MethodInsnNode c&&Set.of("reviewPreservedUpdate","confirmPreservedUpdate").contains(c.name)));
    }
}
