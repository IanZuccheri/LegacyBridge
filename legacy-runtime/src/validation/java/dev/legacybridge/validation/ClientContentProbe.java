package dev.legacybridge.validation;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static dev.legacybridge.validation.LegacyBridgeValidation.require;

@Mod.EventBusSubscriber(modid = LegacyBridgeValidation.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientContentProbe {
    private static int ticks;
    private static boolean finished;

    private ClientContentProbe() { }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished || !Boolean.getBoolean("legacybridge.validation")) return;
        Minecraft game = Minecraft.getInstance();
        ticks++;
        if (game.getOverlay() != null || game.screen == null || ticks < 60) return;
        finished = true;
        try {
            verify(game);
            LogUtils.getLogger().info("LEGACYBRIDGE_CLIENT_VALIDATION_OK models=3 atlasSprites=2 languages=2 soundResource=resolved creativeTabs=2 modId={}",
                    LegacyBridgeValidation.FIXTURE_ID);
        } catch (Throwable failure) {
            LogUtils.getLogger().error("LEGACYBRIDGE_CLIENT_VALIDATION_FAILED", failure);
        } finally {
            game.stop();
        }
    }

    private static void verify(Minecraft game) throws IOException {
        require(ModList.get().isLoaded(LegacyBridgeValidation.FIXTURE_ID), "Converted fixture is not in ModList");
        var ingot = ForgeRegistries.ITEMS.getValue(id("bridge_ingot"));
        var block = ForgeRegistries.BLOCKS.getValue(id("bridge_block"));
        var blockItem = ForgeRegistries.ITEMS.getValue(id("bridge_block"));
        require(ingot != null && block != null && blockItem != null, "Converted fixture registry content is missing");
        var models = game.getModelManager();
        require(models.getModel(new ModelResourceLocation(id("bridge_ingot"), "inventory")) != models.getMissingModel(), "Item model is missing");
        require(models.getModel(new ModelResourceLocation(id("bridge_block"), "inventory")) != models.getMissingModel(), "BlockItem model is missing");
        require(game.getBlockRenderer().getBlockModel(block.defaultBlockState()) != models.getMissingModel(), "Blockstate baked model is missing");
        for (String texture : new String[]{"item/bridge_ingot", "block/bridge_block"}) {
            var sprite = game.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(id(texture));
            require(sprite.contents().name().equals(id(texture)), "Atlas sprite is missing: " + texture);
            require(sprite.contents().width() == 16 && sprite.contents().height() == 16, "Unexpected fixture sprite dimensions");
        }
        require(!I18n.get("item.legacybridge_example.bridge_ingot").equals("item.legacybridge_example.bridge_ingot"), "Item translation is unresolved");
        require(!I18n.get("block.legacybridge_example.bridge_block").equals("block.legacybridge_example.bridge_block"), "Block translation is unresolved");
        language(game, "en_us", "Bridge Ingot", "Bridge Block");
        language(game, "pt_br", "Lingote da Ponte", "Bloco da Ponte");
        var soundEvent = game.getSoundManager().getSoundEvent(id("bridge_ping"));
        require(soundEvent != null && soundEvent.getWeight() > 0, "Fixture sounds.json event was not loaded");
        var sound = soundEvent.getSound(RandomSource.create(0));
        require(sound != SoundManager.EMPTY_SOUND, "Simple sound resolves to missing audio");
        require(game.getResourceManager().getResource(sound.getPath()).isPresent(), "Referenced vanilla sound file is absent");

        CreativeModeTabs.tryRebuildTabContents(FeatureFlags.DEFAULT_FLAGS, false, VanillaRegistries.createLookup());
        var ingredients = BuiltInRegistries.CREATIVE_MODE_TAB.get(CreativeModeTabs.INGREDIENTS);
        var building = BuiltInRegistries.CREATIVE_MODE_TAB.get(CreativeModeTabs.BUILDING_BLOCKS);
        require(ingredients != null && ingredients.contains(new ItemStack(ingot)), "Legacy MISC adapter did not fill the native Ingredients tab");
        require(building != null && building.contains(new ItemStack(blockItem)), "Legacy BUILDING_BLOCKS adapter did not fill the native tab");
    }

    private static void language(Minecraft game, String language, String expectedItem, String expectedBlock) throws IOException {
        var resource = game.getResourceManager().getResource(id("lang/" + language + ".json")).orElseThrow();
        try (var reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
            var json = JsonParser.parseReader(reader).getAsJsonObject();
            require(json.get("item.legacybridge_example.bridge_ingot").getAsString().equals(expectedItem), "Unexpected item translation in " + language);
            require(json.get("block.legacybridge_example.bridge_block").getAsString().equals(expectedBlock), "Unexpected block translation in " + language);
        }
    }

    private static ResourceLocation id(String path) { return new ResourceLocation(LegacyBridgeValidation.FIXTURE_ID, path); }
}
