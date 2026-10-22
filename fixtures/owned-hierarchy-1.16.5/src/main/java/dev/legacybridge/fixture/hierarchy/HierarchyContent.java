package dev.legacybridge.fixture.hierarchy;
import dev.legacybridge.fixture.hierarchy.leaf.HierarchyItem;
import net.minecraft.item.Item;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

@Mod(HierarchyContent.MOD_ID)
public final class HierarchyContent {
    public static final String MOD_ID = "legacybridge_hierarchy";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> TOKEN = ITEMS.register("count_token", HierarchyItem::new);
    public HierarchyContent() { ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus()); }
}
