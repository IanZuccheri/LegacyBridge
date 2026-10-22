package dev.legacybridge.converter.content;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Inventories JAR entry names without opening, parsing, or executing their contents.
 *
 * <p>The safe archive reader can feed this collector every non-directory entry.
 * Counts remain exact. Retained entry and namespace names are bounded to the
 * lexicographically first {@link #DEFAULT_SAMPLE_LIMIT} values by default, so
 * results do not depend on ZIP entry order.</p>
 */
public final class ContentInventoryCollector implements Consumer<String> {
    public static final int DEFAULT_SAMPLE_LIMIT = 16;
    public static final int MAX_SAMPLE_LIMIT = 256;

    private static final Pattern VALID_NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern VALID_RESOURCE_PATH = Pattern.compile("[a-z0-9/._-]+");

    private final int sampleLimit;
    private final Map<ContentCategory, MutableCategory> categories =
            new EnumMap<>(ContentCategory.class);
    private final MutableNamespaces assetNamespaces;
    private final MutableNamespaces dataNamespaces;
    private long observedFileCount;
    private long assetEntryCount;
    private long dataPackEntryCount;

    public ContentInventoryCollector() {
        this(DEFAULT_SAMPLE_LIMIT);
    }

    public ContentInventoryCollector(int sampleLimit) {
        if (sampleLimit < 1 || sampleLimit > MAX_SAMPLE_LIMIT) {
            throw new IllegalArgumentException(
                    "sampleLimit must be between 1 and " + MAX_SAMPLE_LIMIT
            );
        }
        this.sampleLimit = sampleLimit;
        this.assetNamespaces = new MutableNamespaces(sampleLimit);
        this.dataNamespaces = new MutableNamespaces(sampleLimit);
    }

    /** Records one JAR entry name. Directory entries are deliberately ignored. */
    @Override
    public void accept(String entryName) {
        Objects.requireNonNull(entryName, "entryName");
        if (entryName.isBlank()) {
            throw new IllegalArgumentException("entryName must not be blank");
        }
        if (entryName.endsWith("/")) {
            return;
        }

        observedFileCount = Math.addExact(observedFileCount, 1);
        if (entryName.equals("pack.mcmeta")) {
            record(ContentCategory.PACK_METADATA, entryName);
            return;
        }
        if (entryName.equals("pack.png")) {
            record(ContentCategory.PACK_ICON, entryName);
            return;
        }

        if (entryName.startsWith("assets/")) {
            recordPackEntry(entryName, "assets/", true);
            return;
        }
        if (entryName.startsWith("data/")) {
            recordPackEntry(entryName, "data/", false);
            return;
        }

        String lower = entryName.toLowerCase(Locale.ROOT);
        if (lower.equals("pack.mcmeta") || lower.equals("pack.png")
                || lower.startsWith("assets/") || lower.startsWith("data/")) {
            record(ContentCategory.NON_CANONICAL_RESOURCE_PATH, entryName);
            return;
        }
        if (isNonCanonicalLoaderPath(entryName, lower)) {
            record(ContentCategory.CONFIGURATION, entryName);
            record(ContentCategory.NON_CANONICAL_RESOURCE_PATH, entryName);
            return;
        }
        if (lower.endsWith(".class") && !entryName.endsWith(".class")) {
            record(ContentCategory.NON_CANONICAL_RESOURCE_PATH, entryName);
            return;
        }
        if (isRelevantConfiguration(entryName, lower)) {
            record(ContentCategory.CONFIGURATION, entryName);
        }
    }

    public ContentInventory snapshot() {
        EnumMap<ContentCategory, ContentCategorySummary> frozen =
                new EnumMap<>(ContentCategory.class);
        categories.forEach((category, value) -> frozen.put(category, value.snapshot()));
        return new ContentInventory(
                observedFileCount,
                assetEntryCount,
                dataPackEntryCount,
                assetNamespaces.snapshot(),
                dataNamespaces.snapshot(),
                frozen
        );
    }

    /** Convenience entry point for callers that already have an entry-name iterable. */
    public static ContentInventory inventory(Iterable<String> entryNames) {
        Objects.requireNonNull(entryNames, "entryNames");
        ContentInventoryCollector collector = new ContentInventoryCollector();
        for (String entryName : entryNames) {
            collector.accept(entryName);
        }
        return collector.snapshot();
    }

    private void recordPackEntry(String entryName, String root, boolean asset) {
        String remainder = entryName.substring(root.length());
        int separator = remainder.indexOf('/');
        if (separator <= 0 || separator == remainder.length() - 1) {
            record(ContentCategory.NON_CANONICAL_RESOURCE_PATH, entryName);
            return;
        }

        String namespace = remainder.substring(0, separator);
        String relativePath = remainder.substring(separator + 1);
        if (asset) {
            assetEntryCount = Math.addExact(assetEntryCount, 1);
            assetNamespaces.record(namespace);
            record(classifyAsset(relativePath), entryName);
        } else {
            dataPackEntryCount = Math.addExact(dataPackEntryCount, 1);
            dataNamespaces.record(namespace);
            record(classifyData(relativePath), entryName);
        }

        if (!VALID_NAMESPACE.matcher(namespace).matches()
                || !VALID_RESOURCE_PATH.matcher(relativePath).matches()) {
            record(ContentCategory.NON_CANONICAL_RESOURCE_PATH, entryName);
        }
    }

    private static ContentCategory classifyAsset(String relativePath) {
        String firstSegment = firstSegment(relativePath);
        return switch (firstSegment) {
            case "blockstates" -> ContentCategory.ASSET_BLOCKSTATES;
            case "models" -> ContentCategory.ASSET_MODELS;
            case "textures" -> ContentCategory.ASSET_TEXTURES;
            case "lang" -> ContentCategory.ASSET_LANG;
            case "sounds" -> ContentCategory.ASSET_SOUNDS;
            case "shaders" -> ContentCategory.ASSET_SHADERS;
            case "font", "fonts" -> ContentCategory.ASSET_FONTS;
            case "particles" -> ContentCategory.ASSET_PARTICLES;
            case "atlases" -> ContentCategory.ASSET_ATLASES;
            default -> relativePath.equals("sounds.json")
                    ? ContentCategory.ASSET_SOUNDS
                    : ContentCategory.ASSET_OTHER;
        };
    }

    private static ContentCategory classifyData(String relativePath) {
        return switch (firstSegment(relativePath)) {
            case "recipes" -> ContentCategory.DATA_RECIPES;
            case "loot_tables" -> ContentCategory.DATA_LOOT_TABLES;
            case "worldgen" -> ContentCategory.DATA_WORLDGEN;
            case "dimension" -> ContentCategory.DATA_DIMENSIONS;
            case "dimension_type" -> ContentCategory.DATA_DIMENSION_TYPES;
            case "tags" -> ContentCategory.DATA_TAGS;
            case "advancements" -> ContentCategory.DATA_ADVANCEMENTS;
            case "functions" -> ContentCategory.DATA_FUNCTIONS;
            case "predicates" -> ContentCategory.DATA_PREDICATES;
            case "item_modifiers" -> ContentCategory.DATA_ITEM_MODIFIERS;
            case "structures" -> ContentCategory.DATA_STRUCTURES;
            case "loot_modifiers" -> ContentCategory.DATA_FORGE_LOOT_MODIFIERS;
            default -> ContentCategory.DATA_OTHER;
        };
    }

    private static String firstSegment(String relativePath) {
        int separator = relativePath.indexOf('/');
        return separator < 0 ? relativePath : relativePath.substring(0, separator);
    }

    private static boolean isRelevantConfiguration(String entryName, String lower) {
        if (lower.startsWith("config/") || lower.startsWith("defaultconfigs/")) {
            return true;
        }
        if (lower.equals("meta-inf/mods.toml")
                || lower.equals("meta-inf/accesstransformer.cfg")
                || lower.equals("meta-inf/coremods.json")) {
            return true;
        }

        int separator = lower.lastIndexOf('/');
        String fileName = separator < 0 ? lower : lower.substring(separator + 1);
        if (fileName.endsWith(".mixins.json")
                || fileName.startsWith("mixins.") && fileName.endsWith(".json")) {
            return true;
        }

        boolean rootLevel = separator < 0;
        boolean metadataLevel = lower.startsWith("meta-inf/") && separator == "meta-inf".length();
        return (rootLevel || metadataLevel)
                && (fileName.endsWith(".toml")
                || fileName.endsWith(".cfg")
                || fileName.endsWith(".properties")
                || fileName.endsWith(".yaml")
                || fileName.endsWith(".yml"));
    }

    private static boolean isNonCanonicalLoaderPath(String entryName, String lower) {
        return lower.equals("meta-inf/manifest.mf") && !entryName.equals("META-INF/MANIFEST.MF")
                || lower.equals("meta-inf/mods.toml") && !entryName.equals("META-INF/mods.toml")
                || lower.equals("meta-inf/accesstransformer.cfg")
                && !entryName.equals("META-INF/accesstransformer.cfg")
                || lower.equals("meta-inf/coremods.json") && !entryName.equals("META-INF/coremods.json")
                || lower.equals("meta-inf/services/cpw.mods.modlauncher.api.itransformationservice")
                && !entryName.equals("META-INF/services/cpw.mods.modlauncher.api.ITransformationService")
                || lower.startsWith("meta-inf/services/") && !entryName.startsWith("META-INF/services/")
                || lower.startsWith("meta-inf/versions/") && !entryName.startsWith("META-INF/versions/")
                || lower.equals("module-info.class") && !entryName.equals("module-info.class");
    }

    private void record(ContentCategory category, String entryName) {
        categories.computeIfAbsent(
                category,
                ignored -> new MutableCategory(sampleLimit)
        ).record(entryName);
    }

    private static final class MutableCategory {
        private final int sampleLimit;
        private final TreeSet<String> samples = new TreeSet<>();
        private long entryCount;

        private MutableCategory(int sampleLimit) {
            this.sampleLimit = sampleLimit;
        }

        private void record(String entryName) {
            entryCount = Math.addExact(entryCount, 1);
            samples.add(entryName);
            if (samples.size() > sampleLimit) {
                samples.pollLast();
            }
        }

        private ContentCategorySummary snapshot() {
            return new ContentCategorySummary(
                    entryCount,
                    Collections.unmodifiableList(samples.stream().toList()),
                    entryCount > samples.size()
            );
        }
    }

    private static final class MutableNamespaces {
        private final int sampleLimit;
        private final Set<String> namespaces = new TreeSet<>();

        private MutableNamespaces(int sampleLimit) {
            this.sampleLimit = sampleLimit;
        }

        private void record(String namespace) {
            namespaces.add(namespace);
        }

        private ContentNamespaceSummary snapshot() {
            return new ContentNamespaceSummary(
                    namespaces.size(),
                    namespaces.stream().limit(sampleLimit).toList(),
                    namespaces.size() > sampleLimit
            );
        }
    }
}
