package dev.legacybridge.fixture.ironsword;

import net.minecraft.item.Item;
import net.minecraft.item.ItemTier;
import net.minecraft.item.SwordItem;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Original Forge 36 fixture for inherited sword-semantics auditing.
 * Constructor similarity does not approve mining or Forge ToolActions equivalence.
 * The analyzer and converter must never execute this class.
 */
@Mod(IronSwordContent.MOD_ID)
public final class IronSwordContent {
    public static final String MOD_ID = "legacybridge_iron_sword";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> SWORD = ITEMS.register("bridge_sword", IronSwordContent::createSword);

    public IronSwordContent() {
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
    }

    private static Item createSword() {
        return new SwordItem(ItemTier.IRON, 3, -2.4F, new Item.Properties());
    }
}
