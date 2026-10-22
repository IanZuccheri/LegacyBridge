package dev.legacybridge.runtime.compat.networking;

import dev.legacybridge.runtime.compat.AdapterCatalog;
import dev.legacybridge.runtime.compat.AdapterDescriptor;
import dev.legacybridge.runtime.compat.logic.PreservedLogicAdapter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.jar.Manifest;

/**
 * Legacy API: reviewed Forge 36.2.42 SimpleChannel six-argument S2C registration,
 * PacketBuffer and NetworkEvent.Context with explicit enqueueWork.
 * Target API: native Forge 47.4.23 SimpleChannel, FriendlyByteBuf and Context.
 * Transformation: compose exact verified descriptor components; preserve the
 * original codec, ordered session state, rejection branches and enqueueWork bodies.
 * Behavior differences: source dispatch may already be on the game thread; target
 * dispatch may start on Netty. The retained explicit enqueue is mandatory. Unknown
 * discriminators are rejected with Forge's ERROR diagnostic, not a promised kick.
 * Known limitations: network-snapshot-v1 reviewed authored method recipes only;
 * not arbitrary SimpleChannel support, C2S mutations, cross-version play, login
 * packets, arbitrary recipient distributors, or a general networking emulation.
 * This is a real OFFLINE descriptor operation, not a runtime placeholder shim.
 */
public final class SnapshotContractAdapter {
    public static final String PROFILE = "network-snapshot-v1";
    private SnapshotContractAdapter() { }

    public static String remapDescriptor(String descriptor, Map<String, String> classes) {
        return PreservedLogicAdapter.remapDescriptor(descriptor, classes);
    }

    /** Legacy API: validated inert manifest. Target API: explicit profile marker.
     * Transformation: preserve version and serialize provenance.
     * Behavior differences: no behavioral certificate. Known limitations: no active metadata. */
    public static byte[] targetManifest(String version) {
        try {
            Manifest manifest = new Manifest(new ByteArrayInputStream(PreservedLogicAdapter.targetManifest(version)));
            manifest.getMainAttributes().putValue("LegacyBridge-Profile", PROFILE);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            manifest.write(output);
            return output.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    /** Legacy API: reviewed operation set. Target API: schema-1 exact adapter IDs.
     * Transformation: serialize implemented bindings. Behavior differences: inert
     * provenance. Known limitations: not permission to translate unknown packets. */
    public static Map<String, Object> bindingTree() {
        return Map.of("schemaVersion", AdapterCatalog.SCHEMA_VERSION, "runtimeVersion", AdapterCatalog.RUNTIME_VERSION,
                "adapters", List.of(AdapterCatalog.NETWORK, AdapterCatalog.NETWORK_MANIFEST,
                        AdapterCatalog.NETWORK_BINDINGS, AdapterCatalog.METADATA, AdapterCatalog.PACK)
                        .stream().map(AdapterCatalog::require).map(AdapterDescriptor::referenceTree).toList());
    }
}
