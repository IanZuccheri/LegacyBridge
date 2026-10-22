package dev.legacybridge.product.install;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import static org.junit.jupiter.api.Assertions.*;

class PreservedInstallBoundaryTest {
    @Test void newInstallCodeDoesNotReachClientLauncherCliOrOriginalClassExecution() throws Exception {
        for(Class<?> type:List.of(PreservedAddOnlyInstaller.class,PreservedInstallJournal.class,PreservedInstallApproval.class,PreservedInstallResult.class,
                PreservedUpdateInstaller.class,PreservedUpdateJournal.class,PreservedUpdateApproval.class,PreservedUpdatePreview.class,
                dev.legacybridge.product.controller.PreservedUpdateSelection.class,dev.legacybridge.product.controller.PreservedUpdateView.class,
                dev.legacybridge.product.history.PreservedUpdateReceipts.class)) {
            try(var bytes=type.getResourceAsStream(type.getSimpleName()+".class")) {
                new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitMethodInsn(int op,String owner,String method,String desc,boolean itf) {
                                assertFalse(owner.startsWith("net/minecraft/") || owner.startsWith("net/minecraftforge/")
                                        || owner.startsWith("dev/legacybridge/product/client/") || owner.startsWith("dev/legacybridge/converter/cli/")
                                        || owner.startsWith("java/lang/reflect/"),owner);
                                assertFalse(Set.of("java/lang/ClassLoader","java/net/URLClassLoader","java/lang/ProcessBuilder","java/lang/Runtime").contains(owner),owner);
                                assertFalse(owner.equals("java/lang/Class") && method.equals("forName"));
                                assertFalse(owner.equals("java/lang/System") && method.equals("exit"));
                                assertFalse(owner.endsWith("TransactionalInstaller") && Set.of("apply","recover").contains(method));
                                assertFalse(owner.endsWith("StagedArtifactValidator") && method.equals("validate"));
                            }
                        };
                    }
                },0);
            }
        }
    }
    @Test void approvalRequiresExplicitAbsolutePathDigestAndNonce() {
        assertThrows(IllegalArgumentException.class,()->new PreservedInstallApproval(Path.of("relative"),"0".repeat(64),UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,()->new PreservedInstallApproval(Path.of(".").toAbsolutePath().normalize(),"not-a-hash",UUID.randomUUID()));
        assertThrows(NullPointerException.class,()->new PreservedInstallApproval(Path.of(".").toAbsolutePath().normalize(),"0".repeat(64),null));
        var replacement=new PreservedInstallApproval(Path.of(".").toAbsolutePath().normalize(),"0".repeat(64),UUID.randomUUID());
        assertThrows(IllegalArgumentException.class,()->new PreservedUpdateApproval(replacement,Path.of("relative"),"0".repeat(64)));
        assertThrows(IllegalArgumentException.class,()->new PreservedUpdateApproval(replacement,Path.of(".").toAbsolutePath().normalize(),"bad"));
    }
    @Test void unprovenTerminalResultCannotGrantClose() {
        assertThrows(IllegalArgumentException.class,()->new PreservedInstallResult(PreservedInstallResult.State.COMMITTED,
                Optional.of(Path.of("tx")),Optional.of("0".repeat(64)),Optional.empty(),Optional.empty()));
        var failed=new PreservedInstallResult(PreservedInstallResult.State.RECOVERY_REQUIRED,Optional.empty(),Optional.empty(),Optional.empty(),
                Optional.of(new dev.legacybridge.converter.service.EngineError("IO","unknown outcome","Inspect explicitly")));
        assertFalse(failed.safeToClose());
    }
}
