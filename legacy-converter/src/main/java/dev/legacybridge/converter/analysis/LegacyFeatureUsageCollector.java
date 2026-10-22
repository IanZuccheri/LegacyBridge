package dev.legacybridge.converter.analysis;

import dev.legacybridge.converter.bytecode.BytecodeReference;
import dev.legacybridge.converter.bytecode.BytecodeReferenceKind;
import dev.legacybridge.converter.content.ContentCategory;
import dev.legacybridge.converter.content.ContentCategorySummary;
import dev.legacybridge.converter.content.ContentInventory;
import dev.legacybridge.converter.report.BytecodeSummary;
import dev.legacybridge.converter.report.FeatureMarker;
import dev.legacybridge.converter.report.UnsupportedFeature;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Classifies every ASM reference occurrence synchronously. The classifier only
 * records static evidence and never resolves a type through a class loader.
 */
public final class LegacyFeatureUsageCollector {
    public static final int EVIDENCE_LIMIT = 16;
    public static final int EVIDENCE_CHARACTER_LIMIT = 4_096;
    public static final int EVIDENCE_ATTEMPT_LIMIT = 256;

    private static final Set<String> REFLECTIVE_CLASS_METHODS = Set.of(
            "forName", "getDeclaredClasses", "getDeclaredConstructors", "getDeclaredConstructor",
            "getDeclaredFields", "getDeclaredField", "getDeclaredMethods", "getDeclaredMethod",
            "getClasses", "getConstructors", "getConstructor", "getFields", "getField",
            "getMethods", "getMethod", "newInstance"
    );
    private static final Set<String> DYNAMIC_CLASS_LOADER_METHODS = Set.of(
            "loadClass", "defineClass", "findClass", "findSystemClass"
    );

    private final EnumMap<LegacyFeatureId, MutableUsage> usages =
            new EnumMap<>(LegacyFeatureId.class);

    public void accept(String classEntry, BytecodeReference reference) {
        Objects.requireNonNull(classEntry, "classEntry");
        Objects.requireNonNull(reference, "reference");
        String owner = reference.owner();
        EnumSet<LegacyFeatureId> matched = EnumSet.noneOf(LegacyFeatureId.class);
        if (isRegistry(reference)) matched.add(LegacyFeatureId.LEGACY_REGISTRIES);
        if (isEvent(reference)) matched.add(LegacyFeatureId.LEGACY_EVENTS);
        if (isNetworking(reference)) matched.add(LegacyFeatureId.LEGACY_NETWORKING);
        if (isCapability(owner)) matched.add(LegacyFeatureId.LEGACY_CAPABILITIES);
        if (isCustomEntity(reference)) matched.add(LegacyFeatureId.CUSTOM_ENTITIES);
        if (isTileEntity(reference)) matched.add(LegacyFeatureId.TILE_ENTITY);
        if (isRendering(reference)) matched.add(LegacyFeatureId.CLIENT_RENDERING);
        if (isGui(reference)) matched.add(LegacyFeatureId.GUI);
        if (isWorldGeneration(owner)) matched.add(LegacyFeatureId.WORLD_GENERATION);
        if (isDimension(reference)) matched.add(LegacyFeatureId.DIMENSIONS);
        if (isRecipe(reference)) matched.add(LegacyFeatureId.RECIPES);
        if (isLoot(owner)) matched.add(LegacyFeatureId.LOOT);
        if (isReflection(reference)) matched.add(LegacyFeatureId.REFLECTION);
        if (owner.startsWith("org/spongepowered/asm/mixin/")) {
            matched.add(LegacyFeatureId.MIXINS);
        }
        if (isTransformationService(owner)) {
            matched.add(LegacyFeatureId.TRANSFORMATION_SERVICE);
        }
        if (matched.isEmpty()) {
            return;
        }

        boolean evidenceWanted = matched.stream()
                .map(this::idUsage)
                .anyMatch(MutableUsage::canAttemptEvidence);
        if (!evidenceWanted) {
            matched.forEach(id -> idUsage(id).recordOccurrenceWithoutEvidence());
            return;
        }

        BytecodeReference retained = reference.boundedForReport();
        boolean evidenceTruncated = reference.requiresReportTruncation();
        String evidence = classEntry + "#" + retained.kind() + ":" + retained.owner()
                + (retained.memberName().isEmpty() ? "" : "." + retained.memberName())
                + (retained.descriptor().isEmpty() ? "" : retained.descriptor());
        if (evidence.length() > EVIDENCE_CHARACTER_LIMIT) {
            evidence = boundedEvidence(evidence);
            evidenceTruncated = true;
        }
        for (LegacyFeatureId id : matched) {
            record(id, evidence, evidenceTruncated);
        }
    }

    public void accept(ContentInventory content) {
        recordContent(content, ContentCategory.DATA_RECIPES, LegacyFeatureId.RECIPES);
        recordContent(content, ContentCategory.DATA_LOOT_TABLES, LegacyFeatureId.LOOT);
        recordContent(content, ContentCategory.DATA_FORGE_LOOT_MODIFIERS, LegacyFeatureId.LOOT);
        recordContent(content, ContentCategory.DATA_WORLDGEN, LegacyFeatureId.WORLD_GENERATION);
        recordContent(content, ContentCategory.DATA_STRUCTURES, LegacyFeatureId.WORLD_GENERATION);
        recordContent(content, ContentCategory.DATA_DIMENSIONS, LegacyFeatureId.DIMENSIONS);
        recordContent(content, ContentCategory.DATA_DIMENSION_TYPES, LegacyFeatureId.DIMENSIONS);
    }

    /** Commits another collector after its class scan completed successfully. */
    public void merge(LegacyFeatureUsageCollector other) {
        Objects.requireNonNull(other, "other");
        other.usages.forEach((id, source) -> {
            MutableUsage target = idUsage(id);
            target.occurrences = Math.addExact(target.occurrences, source.occurrences);
            source.evidence.forEach(target::retainAttempt);
            target.truncated |= source.truncated;
        });
    }

    public void acceptMarkers(List<FeatureMarker> markers) {
        for (FeatureMarker marker : markers) {
            LegacyFeatureId id = switch (marker.id()) {
                case "MIXIN_CONFIGURATION" -> LegacyFeatureId.MIXINS;
                case "ACCESS_TRANSFORMER" -> LegacyFeatureId.ACCESS_TRANSFORMER;
                case "COREMOD" -> LegacyFeatureId.COREMOD;
                case "TRANSFORMATION_SERVICE" -> LegacyFeatureId.TRANSFORMATION_SERVICE;
                case "NATIVE_LIBRARY" -> LegacyFeatureId.NATIVE_CODE;
                case "NESTED_JAR" -> LegacyFeatureId.NESTED_JAR;
                case "SIGNED_JAR" -> LegacyFeatureId.SIGNED_JAR;
                case "MULTI_RELEASE" -> LegacyFeatureId.MULTI_RELEASE;
                case "SERVICE_PROVIDER" -> LegacyFeatureId.SERVICE_PROVIDER;
                case "EXTERNAL_CLASS_PATH" -> LegacyFeatureId.EXTERNAL_CLASS_PATH;
                case "LEGACY_BOOTSTRAP" -> LegacyFeatureId.LEGACY_BOOTSTRAP;
                default -> null;
            };
            if (id != null) {
                MutableUsage usage = idUsage(id);
                usage.occurrences = Math.addExact(usage.occurrences, marker.occurrenceCount());
                if (marker.evidence().isEmpty()) {
                    usage.retainAttempt("marker:" + marker.id());
                } else {
                    marker.evidence().forEach(usage::retainAttempt);
                }
                usage.truncated |= marker.evidenceTruncated();
            }
        }
    }

    public void acceptBytecode(BytecodeSummary bytecode) {
        if (bytecode.unreadableClassCount() > 0) {
            record(idUsage(LegacyFeatureId.UNREADABLE_BYTECODE), bytecode.unreadableClassCount(),
                    "unreadableClassCount=" + bytecode.unreadableClassCount(), false);
        }
        if (bytecode.nativeMethodCount() > 0) {
            record(idUsage(LegacyFeatureId.NATIVE_CODE), bytecode.nativeMethodCount(),
                    "nativeMethodCount=" + bytecode.nativeMethodCount(), false);
        }
        if (bytecode.previewClassCount() > 0) {
            record(idUsage(LegacyFeatureId.PREVIEW_BYTECODE), bytecode.previewClassCount(),
                    "previewClassCount=" + bytecode.previewClassCount(), false);
        }
        long tooNew = bytecode.classMajorVersions().entrySet().stream()
                .filter(entry -> entry.getKey() > 61)
                .mapToLong(Map.Entry::getValue)
                .sum();
        if (tooNew > 0) {
            String evidence = bytecode.classMajorVersions().keySet().stream()
                    .filter(major -> major > 61)
                    .map(major -> "classMajor=" + major)
                    .collect(java.util.stream.Collectors.joining(","));
            record(idUsage(LegacyFeatureId.UNSUPPORTED_CLASS_VERSION), tooNew, evidence, false);
        }
    }

    public List<UnsupportedFeature> snapshot() {
        List<UnsupportedFeature> result = new ArrayList<>();
        usages.forEach((id, usage) -> result.add(usage.snapshot(id)));
        return List.copyOf(result);
    }

    private void recordContent(ContentInventory content, ContentCategory category, LegacyFeatureId id) {
        ContentCategorySummary summary = content.categories().get(category);
        if (summary == null || summary.entryCount() == 0) {
            return;
        }
        MutableUsage usage = idUsage(id);
        for (String sample : summary.samples()) {
            usage.retainAttempt(sample);
        }
        usage.occurrences = Math.addExact(usage.occurrences, summary.entryCount());
        usage.truncated |= summary.samplesTruncated();
    }

    private void record(LegacyFeatureId id, String evidence) {
        record(id, evidence, false);
    }

    private void record(LegacyFeatureId id, String evidence, boolean truncated) {
        MutableUsage usage = idUsage(id);
        usage.occurrences = Math.addExact(usage.occurrences, 1);
        usage.retainAttempt(evidence);
        usage.truncated |= truncated;
    }

    private static void record(MutableUsage usage, long occurrences, String evidence, boolean truncated) {
        usage.occurrences = Math.addExact(usage.occurrences, occurrences);
        usage.retainAttempt(evidence);
        usage.truncated |= truncated;
    }

    private MutableUsage idUsage(LegacyFeatureId id) {
        return usages.computeIfAbsent(id, ignored -> new MutableUsage());
    }

    private static String boundedEvidence(String evidence) {
        String suffix = "...[length=" + evidence.length() + "]";
        return evidence.substring(0, EVIDENCE_CHARACTER_LIMIT - suffix.length()) + suffix;
    }

    private static boolean isRegistry(BytecodeReference reference) {
        String owner = reference.owner();
        return owner.startsWith("net/minecraftforge/registries/")
                || owner.equals("net/minecraftforge/fml/RegistryObject")
                || owner.startsWith("net/minecraft/util/registry/")
                || owner.equals("net/minecraft/util/RegistryKey")
                || owner.startsWith("net/minecraftforge/event/RegistryEvent");
    }

    private static boolean isEvent(BytecodeReference reference) {
        String owner = reference.owner();
        if (owner.startsWith("net/minecraftforge/event/")
                || owner.startsWith("net/minecraftforge/client/event/")
                || owner.startsWith("net/minecraftforge/fml/event/")) {
            return true;
        }
        if (owner.startsWith("net/minecraftforge/eventbus/api/")) {
            return true;
        }
        return owner.equals("net/minecraftforge/common/MinecraftForge")
                && reference.kind() == BytecodeReferenceKind.FIELD
                && reference.memberName().equals("EVENT_BUS");
    }

    private static boolean isNetworking(BytecodeReference reference) {
        String owner = reference.owner();
        if (owner.startsWith("net/minecraftforge/fml/network/")) {
            return true;
        }
        if (owner.equals("net/minecraftforge/fml/common/registry/IEntityAdditionalSpawnData")) {
            return true;
        }
        if (!owner.startsWith("net/minecraft/network/")) {
            return false;
        }
        return owner.endsWith("/IPacket") || owner.contains("NetworkManager")
                || owner.contains("CustomPayload") || owner.contains("CustomPacket");
    }

    private static boolean isCapability(String owner) {
        return owner.startsWith("net/minecraftforge/common/capabilities/")
                || owner.equals("net/minecraftforge/common/util/LazyOptional")
                || owner.startsWith("net/minecraftforge/items/")
                || owner.startsWith("net/minecraftforge/energy/")
                || owner.startsWith("net/minecraftforge/fluids/capability/")
                || owner.startsWith("net/minecraftforge/event/AttachCapabilitiesEvent");
    }

    private static boolean isCustomEntity(BytecodeReference reference) {
        String owner = reference.owner();
        if (reference.kind() == BytecodeReferenceKind.SUPERCLASS
                && owner.startsWith("net/minecraft/entity/")) {
            return true;
        }
        return owner.startsWith("net/minecraft/entity/EntityType")
                || owner.equals("net/minecraft/entity/EntitySpawnPlacementRegistry")
                || owner.endsWith("/EntityAttributeCreationEvent")
                || owner.equals("net/minecraftforge/fml/common/registry/IEntityAdditionalSpawnData")
                || owner.equals("net/minecraftforge/fml/network/NetworkHooks")
                && reference.kind() == BytecodeReferenceKind.METHOD
                && reference.memberName().equals("getEntitySpawningPacket");
    }

    private static boolean isTileEntity(BytecodeReference reference) {
        String owner = reference.owner();
        return owner.startsWith("net/minecraft/tileentity/")
                || owner.equals("net/minecraftforge/common/extensions/IForgeTileEntity")
                || owner.startsWith("net/minecraft/client/renderer/tileentity/")
                || owner.equals("net/minecraftforge/fml/client/registry/ClientRegistry")
                && reference.kind() == BytecodeReferenceKind.METHOD
                && reference.memberName().equals("bindTileEntityRenderer");
    }

    private static boolean isRendering(BytecodeReference reference) {
        String owner = reference.owner();
        return owner.startsWith("net/minecraft/client/renderer/")
                || owner.startsWith("net/minecraft/client/particle/")
                || owner.startsWith("com/mojang/blaze3d/")
                || owner.startsWith("net/minecraftforge/client/model/")
                || owner.startsWith("net/minecraftforge/client/extensions/")
                || owner.startsWith("net/minecraftforge/client/textures/")
                || owner.equals("net/minecraftforge/client/CloudRenderHandler")
                || owner.equals("net/minecraftforge/client/ICloudRenderHandler")
                || owner.equals("net/minecraftforge/client/IRenderHandler")
                || owner.equals("net/minecraftforge/client/ISkyRenderHandler")
                || owner.equals("net/minecraftforge/client/IWeatherParticleRenderHandler")
                || owner.equals("net/minecraftforge/client/IWeatherRenderHandler")
                || owner.equals("net/minecraftforge/client/SkyRenderHandler")
                || owner.equals("net/minecraftforge/client/WeatherRenderHandler")
                || owner.equals("net/minecraftforge/client/ForgeRenderTypes")
                || owner.startsWith("net/minecraftforge/client/event/ColorHandlerEvent")
                || owner.startsWith("net/minecraftforge/client/event/DrawHighlightEvent")
                || owner.startsWith("net/minecraftforge/client/event/EntityViewRenderEvent")
                || owner.startsWith("net/minecraftforge/client/event/FOVUpdateEvent")
                || owner.startsWith("net/minecraftforge/client/event/ParticleFactoryRegisterEvent")
                || owner.startsWith("net/minecraftforge/client/event/Render")
                || owner.startsWith("net/minecraftforge/client/event/Model")
                || owner.startsWith("net/minecraftforge/client/event/Texture")
                || owner.equals("net/minecraftforge/fml/client/registry/RenderingRegistry")
                || owner.equals("net/minecraftforge/fml/client/registry/ClientRegistry")
                && reference.kind() == BytecodeReferenceKind.METHOD
                && reference.memberName().equals("bindTileEntityRenderer")
                || owner.startsWith("org/lwjgl/opengl/");
    }

    private static boolean isGui(BytecodeReference reference) {
        String owner = reference.owner();
        return owner.startsWith("net/minecraft/client/gui/")
                || owner.startsWith("net/minecraft/inventory/container/")
                || owner.startsWith("net/minecraftforge/client/gui/")
                || owner.startsWith("net/minecraftforge/fml/client/gui/")
                || owner.startsWith("net/minecraftforge/client/ForgeWorldTypeScreens")
                || owner.startsWith("net/minecraftforge/client/event/Gui")
                || owner.startsWith("net/minecraftforge/client/event/RenderGameOverlayEvent")
                || owner.startsWith("net/minecraftforge/client/event/RenderTooltipEvent")
                || owner.equals("net/minecraftforge/fml/network/IContainerFactory")
                || owner.equals("net/minecraftforge/fml/network/NetworkHooks")
                && reference.kind() == BytecodeReferenceKind.METHOD
                && reference.memberName().equals("openGui");
    }

    private static boolean isWorldGeneration(String owner) {
        return owner.startsWith("net/minecraft/world/gen/")
                || owner.startsWith("net/minecraft/world/biome/")
                || owner.startsWith("net/minecraftforge/common/world/BiomeGenerationSettingsBuilder")
                || owner.startsWith("net/minecraftforge/common/world/MobSpawnInfoBuilder")
                || owner.equals("net/minecraftforge/common/BiomeManager")
                || owner.equals("net/minecraftforge/common/world/ForgeWorldType")
                || owner.startsWith("net/minecraftforge/event/world/BiomeLoadingEvent");
    }

    private static boolean isDimension(BytecodeReference reference) {
        String owner = reference.owner();
        if (owner.equals("net/minecraft/world/Dimension")
                || owner.equals("net/minecraft/world/DimensionType")
                || owner.equals("net/minecraft/world/gen/DimensionSettings")
                || owner.equals("net/minecraft/world/gen/settings/DimensionGeneratorSettings")
                || owner.equals("net/minecraft/client/world/DimensionRenderInfo")
                || owner.equals("net/minecraftforge/common/world/ForgeWorldType")
                || owner.equals("net/minecraftforge/common/util/ITeleporter")
                || owner.endsWith("/EntityTravelToDimensionEvent")
                || owner.endsWith("/PlayerChangedDimensionEvent")) {
            return true;
        }
        if (owner.equals("net/minecraft/world/World")
                && reference.kind() == BytecodeReferenceKind.FIELD) {
            return Set.of(
                    "OVERWORLD", "NETHER", "END", "THE_NETHER", "THE_END",
                    "field_234918_g_", "field_234919_h_", "field_234920_i_"
            ).contains(reference.memberName());
        }
        if (owner.equals("net/minecraft/util/registry/Registry")
                && reference.kind() == BytecodeReferenceKind.FIELD) {
            return Set.of(
                    "WORLD_KEY", "DIMENSION_TYPE_KEY", "DIMENSION_KEY",
                    "DIMENSION_TYPE_REGISTRY", "DIMENSION_REGISTRY", "LEVEL_STEM_REGISTRY",
                    "field_239698_ad_", "field_239699_ae_", "field_239700_af_"
            ).contains(reference.memberName());
        }
        return owner.equals("net/minecraft/entity/Entity")
                && reference.kind() == BytecodeReferenceKind.METHOD
                && Set.of("changeDimension", "func_241206_a_").contains(reference.memberName());
    }

    private static boolean isRecipe(BytecodeReference reference) {
        String owner = reference.owner();
        return owner.startsWith("net/minecraft/item/crafting/")
                || owner.startsWith("net/minecraftforge/common/crafting/");
    }

    private static boolean isLoot(String owner) {
        return owner.startsWith("net/minecraft/loot/")
                || owner.startsWith("net/minecraftforge/common/loot/")
                || owner.startsWith("net/minecraftforge/event/LootTableLoadEvent");
    }

    private static boolean isReflection(BytecodeReference reference) {
        String owner = reference.owner();
        if (owner.startsWith("java/lang/reflect/")
                || owner.equals("net/minecraftforge/fml/common/ObfuscationReflectionHelper")
                || owner.equals("sun/misc/Unsafe")
                || owner.equals("jdk/internal/misc/Unsafe")) {
            return true;
        }
        if ((owner.equals("java/lang/invoke/MethodHandles")
                || owner.startsWith("java/lang/invoke/MethodHandles$")
                || owner.equals("java/lang/invoke/MethodHandle"))
                && (reference.kind() == BytecodeReferenceKind.METHOD
                || reference.kind() == BytecodeReferenceKind.FIELD)) {
            return true;
        }
        if (owner.equals("java/lang/ClassLoader")
                && reference.kind() == BytecodeReferenceKind.METHOD
                && DYNAMIC_CLASS_LOADER_METHODS.contains(reference.memberName())) {
            return true;
        }
        if (owner.equals("java/util/ServiceLoader")
                && reference.kind() == BytecodeReferenceKind.METHOD
                && Set.of("load", "loadInstalled").contains(reference.memberName())) {
            return true;
        }
        return owner.equals("java/lang/Class")
                && reference.kind() == BytecodeReferenceKind.METHOD
                && REFLECTIVE_CLASS_METHODS.contains(reference.memberName());
    }

    private static boolean isTransformationService(String owner) {
        return owner.equals("cpw/mods/modlauncher/api/ITransformationService")
                || owner.startsWith("cpw/mods/modlauncher/api/ITransformationService$")
                || owner.startsWith("cpw/mods/modlauncher/api/ITransformer");
    }

    private static final class MutableUsage {
        private long occurrences;
        private final TreeSet<String> evidence = new TreeSet<>();
        private int evidenceAttempts;
        private boolean truncated;

        private boolean canAttemptEvidence() {
            return evidenceAttempts < EVIDENCE_ATTEMPT_LIMIT;
        }

        private void recordOccurrenceWithoutEvidence() {
            occurrences = Math.addExact(occurrences, 1);
            truncated = true;
        }

        private void retainAttempt(String value) {
            if (!canAttemptEvidence()) {
                truncated = true;
                return;
            }
            evidenceAttempts = Math.incrementExact(evidenceAttempts);
            retain(value);
        }

        private void retain(String value) {
            evidence.add(value);
            if (evidence.size() > EVIDENCE_LIMIT) {
                evidence.pollLast();
                truncated = true;
            }
        }

        private UnsupportedFeature snapshot(LegacyFeatureId id) {
            return new UnsupportedFeature(id.name(), occurrences, List.copyOf(evidence), truncated);
        }
    }
}
