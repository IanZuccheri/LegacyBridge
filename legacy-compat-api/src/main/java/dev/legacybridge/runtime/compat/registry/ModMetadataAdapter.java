package dev.legacybridge.runtime.compat.registry;

import dev.legacybridge.runtime.compat.AdapterCatalog;
import dev.legacybridge.runtime.compat.UnsupportedCompatibilityException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Legacy API: Forge 36 javafml mods.toml and inert JAR manifest metadata.
 * Target API: Forge 47 javafml and the versioned concrete LegacyBridge runtime.
 * Transformation: validate pinned baselines, produce target ranges and safely serialized manifest bytes.
 * Behavior differences: the result requires the runtime; optional build-provenance headers are omitted.
 * Known limitations: only validated single-mod metadata; no dependency, signature or loader-hook emulation.
 */
public final class ModMetadataAdapter {
    private ModMetadataAdapter(){}
    public static Map<String,String> targetRanges(String legacyMinecraft,String legacyForge){
        if(!"1.16.5".equals(legacyMinecraft)||!"36.2.42".equals(legacyForge)) throw new UnsupportedCompatibilityException("Unverified metadata baseline");
        return Map.of("loaderVersion","[47,48)","forge","[47.4.23,48)","minecraft","[1.20.1,1.20.2)","legacybridge_runtime","["+AdapterCatalog.RUNTIME_VERSION+",0.2.0)");
    }
    public static byte[] targetManifest(String version){
        if(version==null||!version.matches("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,127}")) throw new UnsupportedCompatibilityException("Unsafe mod version");
        Manifest manifest=new Manifest();
        var attributes=manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION,"1.0");
        attributes.put(Attributes.Name.IMPLEMENTATION_VERSION,version);
        attributes.putValue("LegacyBridge-Profile","simple-content-v1");
        attributes.putValue("LegacyBridge-Adapter-Catalog",Integer.toString(AdapterCatalog.SCHEMA_VERSION));
        try{ByteArrayOutputStream out=new ByteArrayOutputStream();manifest.write(out);return out.toByteArray();}
        catch(IOException impossible){throw new IllegalStateException(impossible);}
    }
}
