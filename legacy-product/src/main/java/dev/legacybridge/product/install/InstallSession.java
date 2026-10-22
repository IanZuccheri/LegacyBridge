package dev.legacybridge.product.install;

import java.nio.file.Path;
import java.util.Set;

/** Trusted actual loader snapshot. Constructing this value performs no filesystem work. */
public record InstallSession(Set<String> loadedModIds, Set<Path> loadedModFiles, boolean worldOpen) {
    public InstallSession {
        loadedModIds = Set.copyOf(loadedModIds);
        loadedModFiles = Set.copyOf(loadedModFiles);
        if (loadedModFiles.stream().anyMatch(path -> !path.isAbsolute()))
            throw new IllegalArgumentException("Loaded mod files must be explicitly absolute");
    }
}
