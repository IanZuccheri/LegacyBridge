package dev.legacybridge.runtime.compat.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import java.util.Set;

/**
 * Legacy API: {@code net.minecraft.util.SoundEvent(ResourceLocation)}.
 * Target API: {@code net.minecraft.sounds.SoundEvent#createVariableRangeEvent(ResourceLocation)}.
 * Transformation: constructs the target variable-range event using the preserved local resource identifier.
 * Behavior differences: the target factory explicitly chooses variable range, matching the legacy constructor shape.
 * Known limitations: simple sounds.json resources only; playback code and custom dynamic behavior are not converted.
 */
public final class SoundEventAdapter {
    private SoundEventAdapter() { }

    public static SoundEvent create(String resourceLocation) {
        if (resourceLocation == null || resourceLocation.length() > 321
                || !resourceLocation.matches("[a-z][a-z0-9_]{1,63}:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Sound adapter requires a bounded local resource identifier");
        }
        String[] parts = resourceLocation.split(":", 2);
        if (parts[1].length() > 256 || Set.of("minecraft", "forge", "legacybridge_runtime").contains(parts[0])
                || parts[1].startsWith("/") || parts[1].endsWith("/") || parts[1].contains("//")) {
            throw new IllegalArgumentException("Sound adapter requires an owned unambiguous resource path");
        }
        for (String segment : parts[1].split("/")) {
            if (segment.equals(".") || segment.equals("..")) throw new IllegalArgumentException("Ambiguous sound resource path");
        }
        return SoundEvent.createVariableRangeEvent(new ResourceLocation(resourceLocation));
    }
}
