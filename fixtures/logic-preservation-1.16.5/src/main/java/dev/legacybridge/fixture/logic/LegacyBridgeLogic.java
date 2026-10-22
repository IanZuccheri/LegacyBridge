package dev.legacybridge.fixture.logic;

import net.minecraft.item.Item;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Genuine Forge 36 entrypoint. Its body must be preserved, not replaced by declarative content. */
@Mod(LegacyBridgeLogic.MOD_ID)
public final class LegacyBridgeLogic {
    public static final String MOD_ID = "legacybridge_logic";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> PARITY_TOKEN = ITEMS.register("parity_token", LogicItem::new);

    public LegacyBridgeLogic() {
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
    }
}
