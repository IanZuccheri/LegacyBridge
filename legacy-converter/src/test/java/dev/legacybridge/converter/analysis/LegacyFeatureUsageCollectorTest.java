package dev.legacybridge.converter.analysis;

import dev.legacybridge.converter.bytecode.BytecodeReference;
import dev.legacybridge.converter.bytecode.BytecodeReferenceKind;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyFeatureUsageCollectorTest {
    @Test
    void weakGenericTypesDoNotBecomeStrongFeatures() {
        LegacyFeatureUsageCollector collector = new LegacyFeatureUsageCollector();
        collector.accept("Fixture.class", BytecodeReference.type(
                "net/minecraft/util/ResourceLocation", "Lnet/minecraft/util/ResourceLocation;"));
        collector.accept("Fixture.class", BytecodeReference.type(
                "net/minecraft/network/PacketBuffer", "Lnet/minecraft/network/PacketBuffer;"));
        collector.accept("Fixture.class", BytecodeReference.type(
                "net/minecraft/entity/player/PlayerEntity", "Lnet/minecraft/entity/player/PlayerEntity;"));
        collector.accept("Fixture.class", BytecodeReference.type(
                "net/minecraftforge/api/distmarker/OnlyIn", "Lnet/minecraftforge/api/distmarker/OnlyIn;"));
        collector.accept("Fixture.class", BytecodeReference.type(
                "java/lang/invoke/MethodHandle", "Ljava/lang/invoke/MethodHandle;"));

        assertTrue(collector.snapshot().isEmpty());
    }

    @Test
    void packetBufferUsedByRecipeDoesNotImplyNetworking() {
        LegacyFeatureUsageCollector collector = new LegacyFeatureUsageCollector();
        collector.accept("Recipe.class", BytecodeReference.member(
                BytecodeReferenceKind.METHOD,
                "net/minecraft/item/crafting/IRecipeSerializer",
                "fromNetwork",
                "(Lnet/minecraft/util/ResourceLocation;Lnet/minecraft/network/PacketBuffer;)Lnet/minecraft/item/crafting/IRecipe;"
        ));
        collector.accept("Recipe.class", BytecodeReference.type(
                "net/minecraft/network/PacketBuffer", "Lnet/minecraft/network/PacketBuffer;"));

        Set<String> ids = collector.snapshot().stream()
                .map(feature -> feature.id()).collect(Collectors.toSet());
        assertTrue(ids.contains("RECIPES"));
        assertFalse(ids.contains("LEGACY_NETWORKING"));
    }

    @Test
    void explicitClassReflectionIsDetected() {
        LegacyFeatureUsageCollector collector = new LegacyFeatureUsageCollector();
        collector.accept("Reflector.class", BytecodeReference.member(
                BytecodeReferenceKind.METHOD,
                "java/lang/Class",
                "forName",
                "(Ljava/lang/String;)Ljava/lang/Class;"
        ));

        assertEquals("REFLECTION", collector.snapshot().get(0).id());
    }

    @Test
    void explicitDynamicClassAndServiceLoadingAreDetected() {
        LegacyFeatureUsageCollector collector = new LegacyFeatureUsageCollector();
        collector.accept("Loader.class", BytecodeReference.member(
                BytecodeReferenceKind.METHOD,
                "java/lang/ClassLoader",
                "loadClass",
                "(Ljava/lang/String;)Ljava/lang/Class;"
        ));
        collector.accept("Loader.class", BytecodeReference.member(
                BytecodeReferenceKind.METHOD,
                "java/util/ServiceLoader",
                "load",
                "(Ljava/lang/Class;)Ljava/util/ServiceLoader;"
        ));

        var feature = collector.snapshot().get(0);
        assertEquals("REFLECTION", feature.id());
        assertEquals(2, feature.occurrenceCount());
    }

    @Test
    void mappedAndSrgDimensionAnchorsAreDetected() {
        LegacyFeatureUsageCollector collector = new LegacyFeatureUsageCollector();
        collector.accept("DimensionUser.class", field(
                "net/minecraft/world/World", "THE_NETHER", "Lnet/minecraft/util/RegistryKey;"));
        collector.accept("DimensionUser.class", field(
                "net/minecraft/world/World", "field_234920_i_", "Lnet/minecraft/util/RegistryKey;"));
        collector.accept("DimensionUser.class", field(
                "net/minecraft/util/registry/Registry", "field_239698_ad_", "Lnet/minecraft/util/RegistryKey;"));
        collector.accept("DimensionUser.class", BytecodeReference.member(
                BytecodeReferenceKind.METHOD,
                "net/minecraft/entity/Entity",
                "func_241206_a_",
                "(Lnet/minecraft/world/server/ServerWorld;)Lnet/minecraft/entity/Entity;"
        ));

        var byId = collector.snapshot().stream()
                .collect(Collectors.toMap(feature -> feature.id(), Function.identity()));
        assertEquals(4, byId.get("DIMENSIONS").occurrenceCount());
    }

    @Test
    void realForgeThirtySixClientTypesAreClassifiedAsGuiAndRendering() {
        LegacyFeatureUsageCollector collector = new LegacyFeatureUsageCollector();
        collector.accept("ClientHooks.class", type(
                "net/minecraftforge/client/IWeatherRenderHandler"));
        collector.accept("ClientHooks.class", type(
                "net/minecraftforge/client/textures/ITextureAtlasSpriteLoader"));
        collector.accept("ClientHooks.class", type(
                "net/minecraftforge/client/event/EntityViewRenderEvent$CameraSetup"));
        collector.accept("ClientHooks.class", type(
                "net/minecraftforge/client/event/GuiContainerEvent$DrawForeground"));
        collector.accept("ClientHooks.class", type(
                "net/minecraftforge/client/event/RenderTooltipEvent$Pre"));
        collector.accept("ClientHooks.class", type(
                "net/minecraftforge/client/ForgeWorldTypeScreens"));

        var byId = collector.snapshot().stream()
                .collect(Collectors.toMap(feature -> feature.id(), Function.identity()));
        assertEquals(4, byId.get("CLIENT_RENDERING").occurrenceCount());
        assertEquals(3, byId.get("GUI").occurrenceCount());
        assertEquals(3, byId.get("LEGACY_EVENTS").occurrenceCount());
    }

    @Test
    void featureEvidenceUsesBoundedReferenceComponents() {
        LegacyFeatureUsageCollector collector = new LegacyFeatureUsageCollector();
        String oversizedDescriptor = "(" + "Ljava/lang/String;".repeat(1_000)
                + ")Ljava/lang/Class;";
        collector.accept("Reflector.class", BytecodeReference.member(
                BytecodeReferenceKind.METHOD,
                "java/lang/Class",
                "forName",
                oversizedDescriptor
        ));

        var feature = collector.snapshot().get(0);
        assertEquals("REFLECTION", feature.id());
        assertTrue(feature.evidenceTruncated());
        assertTrue(feature.evidence().get(0).length()
                <= LegacyFeatureUsageCollector.EVIDENCE_CHARACTER_LIMIT);
    }

    @Test
    void featureEvidenceSamplingStopsWhileOccurrenceCountRemainsExact() {
        LegacyFeatureUsageCollector collector = new LegacyFeatureUsageCollector();
        for (int index = 0; index < 1_000; index++) {
            collector.accept("Reflector.class", BytecodeReference.member(
                    BytecodeReferenceKind.METHOD,
                    "java/lang/Class",
                    "forName",
                    "(Ljava/lang/String;)Ljava/lang/Class;"
            ));
        }

        var feature = collector.snapshot().get(0);
        assertEquals(1_000, feature.occurrenceCount());
        assertEquals(1, feature.evidence().size());
        assertTrue(feature.evidenceTruncated());
    }

    private static BytecodeReference type(String owner) {
        return BytecodeReference.type(owner, "L" + owner + ";");
    }

    private static BytecodeReference field(String owner, String name, String descriptor) {
        return BytecodeReference.member(BytecodeReferenceKind.FIELD, owner, name, descriptor);
    }
}
