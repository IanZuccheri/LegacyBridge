package dev.legacybridge.runtime;

import java.util.List;
import java.util.Objects;

/**
 * Immutable inventory of runtime behavior that is both implemented and enabled.
 *
 * <p>Only the declarative simple-content adapter is enabled. The runtime does
 * not transform classes or emulate arbitrary legacy APIs.</p>
 */
public final class RuntimeCapabilityCatalog {
    public static final int SCHEMA_VERSION = 1;
    public static final String RESOURCE_PATH = "META-INF/legacybridge/runtime-capabilities.json";

    private static final RuntimeCapabilityCatalog CURRENT = new RuntimeCapabilityCatalog(List.of(
            new RuntimeCapability("simple-content-v1", RuntimeCapabilityType.LEGACY_API_SHIM)));

    private final List<RuntimeCapability> enabledCapabilities;

    private RuntimeCapabilityCatalog(List<RuntimeCapability> enabledCapabilities) {
        this.enabledCapabilities = List.copyOf(enabledCapabilities);
    }

    public static RuntimeCapabilityCatalog current() {
        return CURRENT;
    }

    public List<RuntimeCapability> enabledCapabilities() {
        return enabledCapabilities;
    }

    public List<dev.legacybridge.runtime.compat.AdapterDescriptor> implementedAdapters() {
        return dev.legacybridge.runtime.compat.AdapterCatalog.all();
    }

    public List<RuntimeCapability> enabledCapabilities(RuntimeCapabilityType type) {
        Objects.requireNonNull(type, "type");
        return enabledCapabilities.stream()
                .filter(capability -> capability.type() == type)
                .toList();
    }

    public boolean hasEnabledCapability(RuntimeCapabilityType type) {
        Objects.requireNonNull(type, "type");
        return enabledCapabilities.stream().anyMatch(capability -> capability.type() == type);
    }
}
