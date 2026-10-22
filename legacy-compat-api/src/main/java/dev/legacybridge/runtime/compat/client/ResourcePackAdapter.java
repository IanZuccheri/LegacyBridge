package dev.legacybridge.runtime.compat.client;

import dev.legacybridge.runtime.compat.UnsupportedCompatibilityException;

/**
 * Legacy API: pack.mcmeta pack_format 6 for the fixture's Minecraft 1.16.5 resources.
 * Target API: pack_format 15 for Minecraft 1.20.1.
 * Transformation: return the target format only after the converter validates the complete resource language.
 * Behavior differences: platform advertisement changes; accepted resource bytes remain unchanged.
 * Known limitations: not a universal pack upgrader; no codec, shader, animation or custom geometry conversion.
 */
public final class ResourcePackAdapter {
    private ResourcePackAdapter(){}
    public static int targetFormat(int format){
        if(format!=6) throw new UnsupportedCompatibilityException("Resource format has no verified adapter: "+format);
        return 15;
    }
}
