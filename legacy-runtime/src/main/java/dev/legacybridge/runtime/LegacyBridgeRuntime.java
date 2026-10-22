package dev.legacybridge.runtime;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(LegacyBridgeRuntime.MOD_ID)
public final class LegacyBridgeRuntime {
    public static final String MOD_ID = "legacybridge_runtime";
    private static final Logger LOGGER = LogUtils.getLogger();

    public LegacyBridgeRuntime() {
        RuntimeDescriptor descriptor = RuntimeDescriptor.current();
        LOGGER.info(
                "LegacyBridge Runtime {} loaded for Minecraft {}; enabled capabilities={}, transformations={}, enabled shims={}",
                descriptor.runtimeVersion(),
                descriptor.minecraftVersion(),
                descriptor.capabilityCatalog().enabledCapabilities().size(),
                descriptor.transformationsEnabled(),
                descriptor.enabledShims().size()
        );
    }
}
