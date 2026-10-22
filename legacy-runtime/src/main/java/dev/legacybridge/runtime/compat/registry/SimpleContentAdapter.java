package dev.legacybridge.runtime.compat.registry;

import com.mojang.logging.LogUtils;
import dev.legacybridge.runtime.SimpleContentPlan;
import dev.legacybridge.runtime.compat.AdapterBindings;
import dev.legacybridge.runtime.compat.client.CreativeTabAdapter;
import dev.legacybridge.runtime.compat.event.ModEventBusAdapter;
import dev.legacybridge.runtime.compat.world.BlockPropertiesAdapter;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Legacy API: Forge 36 DeferredRegister for plain Item, Block, BlockItem and SoundEvent suppliers.
 * Target API: Forge 47 DeferredRegister with target-native constructors and registry objects.
 * Transformation: consumes the converter-proven mod-scoped JSON plan from a generated target Mod constructor.
 * Behavior differences: legacy code becomes declarative registrations; property, event, creative and mining adapters are explicit.
 * Known limitations: finite grammar only; no legacy class execution, custom subclasses, callbacks or general registry emulation.
 */
public final class SimpleContentAdapter {
    public static final String CAPABILITY_ID = "simple-content-v1";
    private static final Set<String> REGISTERED_MODS = new HashSet<>();

    private SimpleContentAdapter() { }

    public static void register(String modId, ClassLoader loader, String planResource) {
        Objects.requireNonNull(loader, "loader");
        if (modId == null || !modId.matches("[a-z][a-z0-9_]{1,63}")) throw new IllegalArgumentException("Invalid converted modId");
        if (!("META-INF/legacybridge/" + modId + "/content.json").equals(planResource)) {
            throw new IllegalArgumentException("Content plan must use the unique mod-scoped resource path");
        }
        final SimpleContentPlan plan;
        try (InputStream input = loader.getResourceAsStream(planResource)) {
            if (input == null) throw new IOException("Converted content plan is missing");
            plan = SimpleContentPlan.read(input, modId);
            AdapterBindings.verify(loader, modId, plan);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid converted content plan for " + modId, exception);
        }
        IEventBus bus = ModEventBusAdapter.requireContext(modId, plan.modVersion());
        synchronized (REGISTERED_MODS) {
            if (!REGISTERED_MODS.add(modId)) throw new IllegalStateException("Converted mod registered twice: " + modId);
        }
        DeferredRegister<Block> blocks = DeferredRegister.create(ForgeRegistries.BLOCKS, modId);
        DeferredRegister<Item> items = DeferredRegister.create(ForgeRegistries.ITEMS, modId);
        DeferredRegister<SoundEvent> sounds = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, modId);
        Map<String, RegistryObject<Block>> blockObjects = new HashMap<>();
        Map<RegistryObject<Item>, String> creativeItems = new LinkedHashMap<>();
        for (var definition : plan.blocks()) {
            blockObjects.put(modId + ":" + definition.name(), blocks.register(definition.name(),
                    () -> new Block(BlockPropertiesAdapter.create(definition.properties()))));
        }
        for (var definition : plan.items()) {
            RegistryObject<Item> item = items.register(definition.name(), () -> definition.block() == null
                    ? new Item(ItemPropertiesAdapter.create(definition.properties()))
                    : BlockItemAdapter.create(blockObjects.get(definition.block()).get(), definition.properties()));
            if (definition.properties().creativeTab() != null) creativeItems.put(item, definition.properties().creativeTab());
        }
        for (var definition : plan.sounds()) {
            sounds.register(definition.name(), () -> SoundEventAdapter.create(definition.resourceLocation()));
        }
        blocks.register(bus);
        items.register(bus);
        sounds.register(bus);
        if (!creativeItems.isEmpty()) CreativeTabAdapter.attach(bus, creativeItems);
        LogUtils.getLogger().info("LEGACYBRIDGE_SIMPLE_CONTENT_REGISTERED modId={} version={} blocks={} items={} sounds={}",
                modId, plan.modVersion(), plan.blocks().size(), plan.items().size(), plan.sounds().size());
    }
}
