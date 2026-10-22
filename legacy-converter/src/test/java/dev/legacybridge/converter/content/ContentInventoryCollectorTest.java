package dev.legacybridge.converter.content;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentInventoryCollectorTest {
    @Test
    void inventoriesResourceAndDataPackContentByNamespaceAndKind() {
        ContentInventory inventory = ContentInventoryCollector.inventory(List.of(
                "META-INF/MANIFEST.MF",
                "pack.mcmeta",
                "pack.png",
                "assets/example/blockstates/machine.json",
                "assets/example/models/block/machine.json",
                "assets/example/textures/block/machine.png",
                "assets/example/lang/en_us.json",
                "assets/example/sounds.json",
                "assets/dependency/shaders/core/custom.json",
                "data/example/recipes/machine.json",
                "data/example/loot_tables/blocks/machine.json",
                "data/example/worldgen/configured_feature/ore.json",
                "data/example/dimension/moon.json",
                "data/example/dimension_type/moon.json",
                "data/example/tags/items/machines.json",
                "data/example/advancements/root.json",
                "data/example/functions/start.mcfunction",
                "data/example/predicates/is_moon.json",
                "data/example/item_modifiers/damage.json",
                "data/example/structures/tower.nbt",
                "data/example/loot_modifiers/bonus.json",
                "data/example/custom/extension.json"
        ));

        assertEquals(22, inventory.observedFileCount());
        assertEquals(6, inventory.assetEntryCount());
        assertEquals(13, inventory.dataPackEntryCount());
        assertTrue(inventory.hasAssets());
        assertTrue(inventory.hasDataPack());
        assertTrue(inventory.hasPackMetadata());
        assertTrue(inventory.hasPackIcon());
        assertEquals(List.of("dependency", "example"), inventory.assetNamespaces().samples());
        assertEquals(List.of("example"), inventory.dataNamespaces().samples());
        assertEquals(1, inventory.entryCount(ContentCategory.DATA_RECIPES));
        assertEquals(1, inventory.entryCount(ContentCategory.DATA_LOOT_TABLES));
        assertEquals(1, inventory.entryCount(ContentCategory.DATA_WORLDGEN));
        assertEquals(1, inventory.entryCount(ContentCategory.DATA_DIMENSIONS));
        assertEquals(1, inventory.entryCount(ContentCategory.DATA_DIMENSION_TYPES));
        assertEquals(1, inventory.entryCount(ContentCategory.DATA_TAGS));
        assertEquals(1, inventory.entryCount(ContentCategory.DATA_FORGE_LOOT_MODIFIERS));
        assertEquals(1, inventory.entryCount(ContentCategory.DATA_OTHER));
    }

    @Test
    void samplesAreBoundedSortedAndIndependentOfZipOrder() {
        List<String> entries = List.of(
                "data/zeta/recipes/z.json",
                "data/beta/recipes/b.json",
                "data/alpha/recipes/a.json"
        );
        ContentInventoryCollector forward = new ContentInventoryCollector(2);
        entries.forEach(forward);

        List<String> reversed = new ArrayList<>(entries);
        Collections.reverse(reversed);
        ContentInventoryCollector backward = new ContentInventoryCollector(2);
        reversed.forEach(backward);

        ContentInventory first = forward.snapshot();
        ContentInventory second = backward.snapshot();
        assertEquals(first, second);
        assertEquals(
                List.of("data/alpha/recipes/a.json", "data/beta/recipes/b.json"),
                first.categories().get(ContentCategory.DATA_RECIPES).samples()
        );
        assertTrue(first.categories().get(ContentCategory.DATA_RECIPES).samplesTruncated());
        assertEquals(List.of("alpha", "beta"), first.dataNamespaces().samples());
        assertTrue(first.dataNamespaces().samplesTruncated());
    }

    @Test
    void detectsRelevantConfigurationAndNonCanonicalResourcePaths() {
        ContentInventory inventory = ContentInventoryCollector.inventory(List.of(
                "META-INF/mods.toml",
                "META-INF/accesstransformer.cfg",
                "META-INF/coremods.json",
                "mixins.example.json",
                "example.mixins.json",
                "defaultconfigs/example-server.toml",
                "config/example.properties",
                "Assets/example/textures/item/wrong-root.png",
                "assets/Example/textures/item/wrong-namespace.png",
                "data/example/Recipes/wrong-path.json",
                "PACK.MCMETA",
                "ordinary.json"
        ));

        assertEquals(7, inventory.entryCount(ContentCategory.CONFIGURATION));
        assertEquals(4, inventory.entryCount(ContentCategory.NON_CANONICAL_RESOURCE_PATH));
        assertEquals(1, inventory.assetEntryCount());
        assertEquals(1, inventory.dataPackEntryCount());
        assertEquals(1, inventory.entryCount(ContentCategory.ASSET_TEXTURES));
        assertEquals(1, inventory.entryCount(ContentCategory.DATA_OTHER));
        assertFalse(inventory.hasPackMetadata());
    }

    @Test
    void ignoresDirectoriesAndRejectsInvalidCollectorArguments() {
        ContentInventoryCollector collector = new ContentInventoryCollector();
        collector.accept("assets/");
        collector.accept("data/example/");

        assertEquals(0, collector.snapshot().observedFileCount());
        assertThrows(NullPointerException.class, () -> collector.accept(null));
        assertThrows(IllegalArgumentException.class, () -> collector.accept(" "));
        assertThrows(IllegalArgumentException.class, () -> new ContentInventoryCollector(0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ContentInventoryCollector(ContentInventoryCollector.MAX_SAMPLE_LIMIT + 1)
        );
    }
}
