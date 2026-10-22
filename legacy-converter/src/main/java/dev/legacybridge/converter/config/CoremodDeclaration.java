package dev.legacybridge.converter.config;

import java.util.Objects;

/** A coremod name and its declared script path. The path is never opened or executed. */
public record CoremodDeclaration(String name, String scriptPath) implements Comparable<CoremodDeclaration> {
    public CoremodDeclaration {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    @Override
    public int compareTo(CoremodDeclaration other) {
        int byName = name.compareTo(other.name);
        if (byName != 0) {
            return byName;
        }
        if (scriptPath == null) {
            return other.scriptPath == null ? 0 : -1;
        }
        return other.scriptPath == null ? 1 : scriptPath.compareTo(other.scriptPath);
    }
}
