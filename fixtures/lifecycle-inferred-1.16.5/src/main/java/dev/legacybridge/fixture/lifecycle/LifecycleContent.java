package dev.legacybridge.fixture.lifecycle;

import net.minecraft.item.Item;
import java.util.function.Consumer;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Original Forge 36 inferred-listener variant. Analysis/conversion must never initialize it. */
@Mod(LifecycleContent.MOD_ID)
public final class LifecycleContent {
    public static final String MOD_ID = "legacybridge_lifecycle";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> LIFECYCLE_TOKEN = ITEMS.register("lifecycle_token", LifecycleItem::new);

    private static int setupEvents;
    private static int queuedWorkRuns;
    private static Item setupItem;
    private static boolean setupEnabled;
    private static int setupFactor;

    public LifecycleContent() {
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, LifecycleConfig.SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(
                (Consumer<FMLCommonSetupEvent>) LifecycleContent::commonSetup);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        setupEvents++;
        event.enqueueWork((Runnable) LifecycleContent::finishSetup);
    }

    /** The only RegistryObject.get in this fixture runs after registry events, on deferred work. */
    private static void finishSetup() {
        setupItem = LIFECYCLE_TOKEN.get();
        setupEnabled = LifecycleConfig.isEnabled();
        setupFactor = LifecycleConfig.factor();
        queuedWorkRuns++;
    }

    public static int setupEventCount() {
        return setupEvents;
    }

    public static int queuedWorkCount() {
        return queuedWorkRuns;
    }

    public static Item observedItem() {
        return setupItem;
    }

    public static boolean observedEnabled() {
        return setupEnabled;
    }

    public static int observedFactor() {
        return setupFactor;
    }
}
