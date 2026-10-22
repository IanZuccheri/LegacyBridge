package dev.legacybridge.converter.namespace;

import java.util.Objects;

/** Fixed report-oriented buckets used to keep namespace analysis bounded. */
public enum NamespaceCategory {
    JAVA,
    JAVAX,
    MINECRAFT,
    MINECRAFT_CLIENT,
    FORGE,
    MODLAUNCHER,
    MIXIN,
    OTHER;

    public static NamespaceCategory classifyInternalName(String internalName) {
        Objects.requireNonNull(internalName, "internalName");
        if (matches(internalName, "net/minecraft/client")) {
            return MINECRAFT_CLIENT;
        }
        if (matches(internalName, "net/minecraft")) {
            return MINECRAFT;
        }
        if (matches(internalName, "net/minecraftforge")) {
            return FORGE;
        }
        if (matches(internalName, "cpw/mods")) {
            return MODLAUNCHER;
        }
        if (matches(internalName, "org/spongepowered/asm")) {
            return MIXIN;
        }
        if (matches(internalName, "java")) {
            return JAVA;
        }
        if (matches(internalName, "javax")) {
            return JAVAX;
        }
        return OTHER;
    }

    private static boolean matches(String internalName, String prefix) {
        return internalName.equals(prefix) || internalName.startsWith(prefix + "/");
    }
}
