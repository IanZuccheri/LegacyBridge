package dev.legacybridge.fixture.durable;

import net.minecraft.item.Item;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Genuine original Forge 36 durable-only entrypoint, never executed by analysis or conversion. */
@Mod(DurableContent.MOD_ID)
public final class DurableContent {
    public static final String MOD_ID = "legacybridge_durable";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> TOKEN = ITEMS.register("token", RepairableItem::new);

    public DurableContent() {
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
    }
}
