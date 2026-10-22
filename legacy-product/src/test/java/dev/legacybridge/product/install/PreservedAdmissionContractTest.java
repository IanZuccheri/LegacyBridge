package dev.legacybridge.product.install;

import dev.legacybridge.converter.service.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import static org.junit.jupiter.api.Assertions.*;

class PreservedAdmissionContractTest {
    @TempDir Path game;
    @Test void unpinnedOrIncludedDataCannotCreateAdmissionRecords() throws Exception {
        for(String area:List.of("input","staging","reports","cache","transactions")) Files.createDirectories(game.resolve("legacybridge/"+area));
        for(MappingData data:List.of(MappingData.bundle(game),MappingData.profile(game),MappingData.builtinSimpleContent(),MappingData.builtinCohortPreview(),MappingData.unavailable())) {
            var result=new PreservedAdmissionStore().prepare(game,new PreservedArtifactRequest(game.resolve("a.jar"),"0".repeat(64),game.resolve("b.jar"),"1".repeat(64),data));
            assertEquals(PreservedAdmissionResult.State.RECOVERY_REQUIRED,result.state()); assertTrue(result.transaction().isEmpty());
            assertFalse(result.installationAllowed()); assertFalse(result.safeToClose());
        }
        try(var paths=Files.list(game.resolve("legacybridge/transactions"))) { assertEquals(0,paths.count()); }
    }
    @Test void bindingAndStoreCannotReachInstallerPublisherClientOrCodeExecution() throws Exception {
        for(Class<?> type:List.of(PreservedAdmissionStore.class,PreservedAdmissionJournal.class,PreservedAdmissionResult.class)) {
            try(var stream=type.getResourceAsStream(type.getSimpleName()+".class")) {
                new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitMethodInsn(int op,String owner,String method,String desc,boolean itf) {
                                assertFalse(owner.startsWith("net/minecraft/") || owner.startsWith("net/minecraftforge/")
                                        || owner.startsWith("java/lang/reflect/") || owner.startsWith("dev/legacybridge/product/client/"),owner);
                                assertFalse(owner.endsWith("TransactionalInstaller") && Set.of("apply","recover").contains(method));
                                assertFalse(owner.endsWith("AddOnlyInstaller") && method.equals("apply"));
                                assertFalse(Set.of("java/lang/ClassLoader","java/net/URLClassLoader","java/lang/ProcessBuilder","java/lang/Runtime").contains(owner));
                            }
                        };
                    }
                },0);
            }
        }
    }
    @Test @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void longWindowsGuardPathIsRefusedBeforeCreatingAnyTransactionOrCanary() throws Exception {
        Path longer=Files.createDirectory(game.resolve("long-"+"x".repeat(100)));
        for(String area:List.of("input","staging","reports","cache","transactions")) Files.createDirectories(longer.resolve("legacybridge/"+area));
        var request=new PreservedArtifactRequest(longer.resolve("a.jar"),"0".repeat(64),longer.resolve("b.jar"),"1".repeat(64),MappingData.unavailable());
        var result=new PreservedAdmissionStore().prepare(longer,request);
        assertEquals(PreservedAdmissionResult.State.RECOVERY_REQUIRED,result.state());
        assertTrue(result.error().orElseThrow().message().contains("247-character"),result.toString()); assertTrue(result.transaction().isEmpty());
        try(var paths=Files.walk(longer)) { assertEquals(0,paths.filter(Files::isRegularFile).count()); }
    }
}
