package dev.legacybridge.runtime;

import java.util.List;
import java.util.Objects;

/** Honest declaration of the runtime capabilities enabled by this build. */
public record RuntimeDescriptor(
        String runtimeVersion,
        String minecraftVersion,
        int forgeMajor,
        RuntimeCapabilityCatalog capabilityCatalog
) {
    public RuntimeDescriptor {
        Objects.requireNonNull(runtimeVersion, "runtimeVersion");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(capabilityCatalog, "capabilityCatalog");
    }

    public boolean transformationsEnabled() {
        return capabilityCatalog.hasEnabledCapability(RuntimeCapabilityType.BYTECODE_TRANSFORMATION);
    }

    public List<RuntimeCapability> enabledShims() {
        return capabilityCatalog.enabledCapabilities(RuntimeCapabilityType.LEGACY_API_SHIM);
    }

    public static RuntimeDescriptor current() {
        String version = RuntimeDescriptor.class.getPackage().getImplementationVersion();
        return new RuntimeDescriptor(
                version == null ? "development" : version,
                "1.20.1",
                47,
                RuntimeCapabilityCatalog.current()
        );
    }
}
