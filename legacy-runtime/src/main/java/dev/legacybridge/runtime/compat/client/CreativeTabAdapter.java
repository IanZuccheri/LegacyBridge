package dev.legacybridge.runtime.compat.client;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Legacy API: {@code Item.Properties.tab(ItemGroup.TAB_MISC/TAB_BUILDING_BLOCKS)}.
 * Target API: {@code BuildCreativeModeTabContentsEvent.accept(Supplier)} for vanilla Ingredients/Building Blocks tabs.
 * Transformation: attaches a target mod-bus listener with validated item-to-tab assignments.
 * Behavior differences: legacy MISC becomes Ingredients; Building Blocks retains its vanilla purpose.
 * Known limitations: these two vanilla tabs only; custom groups, icons and item ordering are not emulated.
 */
public final class CreativeTabAdapter {
    private CreativeTabAdapter() { }

    public static void attach(IEventBus bus, Map<RegistryObject<Item>, String> assignments) {
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(assignments, "assignments");
        Map<RegistryObject<Item>, ResourceKey<CreativeModeTab>> tabs = new LinkedHashMap<>();
        assignments.forEach((item, token) -> {
            Objects.requireNonNull(item, "item");
            if (!"MISC".equals(token) && !"BUILDING_BLOCKS".equals(token)) {
                throw new IllegalArgumentException("Only verified vanilla creative tab assignments are supported");
            }
            tabs.put(item, token.equals("MISC") ? CreativeModeTabs.INGREDIENTS : CreativeModeTabs.BUILDING_BLOCKS);
        });
        bus.addListener((BuildCreativeModeTabContentsEvent event) -> tabs.forEach((item, tab) -> {
            if (event.getTabKey().equals(tab)) event.accept(item);
        }));
    }
}
