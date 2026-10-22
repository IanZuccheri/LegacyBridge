package dev.legacybridge.converter.conversion;

import dev.legacybridge.runtime.compat.logic.LifecycleContractAdapter;
import dev.legacybridge.runtime.compat.logic.PreservedLogicAdapter;
import dev.legacybridge.converter.transformation.ReviewedLifecycleLambda;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;
import static org.junit.jupiter.api.Assertions.*;

class LifecycleContractAdapterTest {
    @Test void erasedRegistryReturnIsAnExactCallSiteOperationNotATypeMapping() {
        String old="()Lnet/minecraftforge/registries/IForgeRegistryEntry;";
        assertEquals("()Ljava/lang/Object;", LifecycleContractAdapter.methodDescriptor("net/minecraftforge/fml/RegistryObject","get",old,old));
        assertEquals(old,LifecycleContractAdapter.methodDescriptor("another/RegistryObject","get",old,old));
        assertEquals(old,LifecycleContractAdapter.methodDescriptor("net/minecraftforge/fml/RegistryObject","other",old,old));
        assertEquals(old,PreservedLogicAdapter.remapDescriptor(old, Map.of()));
    }
    @Test void configInterfaceChangeDoesNotRewriteSpecFieldsOrFilenameOverloads() {
        String old="(Lnet/minecraftforge/fml/config/ModConfig$Type;Lnet/minecraftforge/common/ForgeConfigSpec;)V";
        assertEquals("(Lnet/minecraftforge/fml/config/ModConfig$Type;Lnet/minecraftforge/fml/config/IConfigSpec;)V",LifecycleContractAdapter.methodDescriptor("net/minecraftforge/fml/ModLoadingContext","registerConfig",old,old));
        String other=old.replace(";)V",";Ljava/lang/String;)V");
        assertEquals(other,LifecycleContractAdapter.methodDescriptor("net/minecraftforge/fml/ModLoadingContext","registerConfig",other,other));
        assertEquals("Lnet/minecraftforge/common/ForgeConfigSpec;",PreservedLogicAdapter.remapDescriptor("Lnet/minecraftforge/common/ForgeConfigSpec;",Map.of()));
    }
    @Test void markersAndBindingsAreVersionedAndPreviousProfileRemainsDistinct() throws Exception {
        assertEquals("lifecycle-preserving-v1",new Manifest(new ByteArrayInputStream(LifecycleContractAdapter.targetManifest("1.0.0"))).getMainAttributes().getValue("LegacyBridge-Profile"));
        assertEquals("logic-preserving-v1",new Manifest(new ByteArrayInputStream(PreservedLogicAdapter.targetManifest("1.0.0"))).getMainAttributes().getValue("LegacyBridge-Profile"));
        assertEquals(TransformationBindings.forLifecycle().bindingTree(),LifecycleContractAdapter.bindingTree());
    }
    @Test void callbackContractRejectsCapturesWrongEventsAndSamAdaptation() {
        Type consumer=Type.getMethodType("()Ljava/util/function/Consumer;"), sam=Type.getMethodType("(Ljava/lang/Object;)V");
        Type common=Type.getMethodType("(Lnet/minecraftforge/fml/event/lifecycle/FMLCommonSetupEvent;)V");
        assertTrue(ReviewedLifecycleLambda.accepts("java/util/function/Consumer","accept",sam,common,consumer));
        assertFalse(ReviewedLifecycleLambda.accepts("java/util/function/Consumer","accept",sam,Type.getMethodType("(Lnet/minecraftforge/fml/event/lifecycle/FMLClientSetupEvent;)V"),consumer));
        assertFalse(ReviewedLifecycleLambda.accepts("java/util/function/Consumer","accept",sam,common,Type.getMethodType("(I)Ljava/util/function/Consumer;")));
        assertFalse(ReviewedLifecycleLambda.accepts("java/util/function/Consumer","accept",Type.getMethodType("(Ljava/lang/String;)V"),common,consumer));
    }
}
