package dev.legacybridge.converter.content;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Immutable inventory of resource-pack, data-pack, and relevant config files. */
public record ContentInventory(
        long observedFileCount,
        long assetEntryCount,
        long dataPackEntryCount,
        ContentNamespaceSummary assetNamespaces,
        ContentNamespaceSummary dataNamespaces,
        Map<ContentCategory, ContentCategorySummary> categories
) {
    public static ContentInventory empty() {
        return new ContentInventory(
                0, 0, 0,
                ContentNamespaceSummary.EMPTY,
                ContentNamespaceSummary.EMPTY,
                Map.of()
        );
    }

    public ContentInventory {
        if (observedFileCount < 0 || assetEntryCount < 0 || dataPackEntryCount < 0) {
            throw new IllegalArgumentException("entry counts must not be negative");
        }
        if (assetEntryCount > observedFileCount || dataPackEntryCount > observedFileCount) {
            throw new IllegalArgumentException("content counts must not exceed observedFileCount");
        }
        assetNamespaces = Objects.requireNonNull(assetNamespaces, "assetNamespaces");
        dataNamespaces = Objects.requireNonNull(dataNamespaces, "dataNamespaces");
        Objects.requireNonNull(categories, "categories");
        EnumMap<ContentCategory, ContentCategorySummary> copy =
                new EnumMap<>(ContentCategory.class);
        categories.forEach((category, summary) -> copy.put(
                Objects.requireNonNull(category, "categories must not contain a null key"),
                Objects.requireNonNull(summary, "categories must not contain a null value")
        ));
        categories = Collections.unmodifiableMap(copy);
    }

    public boolean hasAssets() {
        return assetEntryCount > 0;
    }

    public boolean hasDataPack() {
        return dataPackEntryCount > 0;
    }

    public boolean hasPackMetadata() {
        return entryCount(ContentCategory.PACK_METADATA) > 0;
    }

    public boolean hasPackIcon() {
        return entryCount(ContentCategory.PACK_ICON) > 0;
    }

    public long entryCount(ContentCategory category) {
        ContentCategorySummary summary = categories.get(Objects.requireNonNull(category, "category"));
        return summary == null ? 0 : summary.entryCount();
    }
}
