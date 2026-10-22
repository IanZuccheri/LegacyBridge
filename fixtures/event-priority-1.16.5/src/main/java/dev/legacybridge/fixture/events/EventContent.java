package dev.legacybridge.fixture.events;
import net.minecraft.item.Item;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

@Mod(EventContent.MOD_ID)
public final class EventContent {
    public static final String MOD_ID = "legacybridge_events";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> TOKEN = ITEMS.register("event_token", EventItem::new);
    public EventContent() {
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        FMLJavaModLoadingContext.get().getModEventBus().addListener(
            EventPriority.NORMAL, false, FMLCommonSetupEvent.class, EventContent::setup);
        EventFlow.install();
    }
    private static void setup(FMLCommonSetupEvent event) { event.enqueueWork(EventFlow::run); }
}
