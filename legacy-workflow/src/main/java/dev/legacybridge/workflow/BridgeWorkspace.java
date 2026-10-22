package dev.legacybridge.workflow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Read-only layout contract for one explicitly selected game installation.
 *
 * <p>The supplied absolute, existing game directory is trusted and resolved
 * canonically once. A launcher may therefore select a game directory through a
 * symbolic link. Existing descendants used by this contract, including
 * {@code mods}, must instead be real directories at their expected locations:
 * symbolic links, junctions resolving elsewhere and aliases are rejected.
 * Unrelated entries in the game directory are not inspected.</p>
 *
 * <p>Inspection does not create directories, inventory JARs, load mod classes,
 * install files, or change Forge discovery. In particular {@link #input()} is
 * outside {@link #mods()}; callers must never add it to a mod locator.</p>
 *
 * <p>These checks are a filesystem snapshot, not protection against concurrent
 * path replacement. Future writers must use their own transaction and
 * filesystem-race protections. Calling {@link #revalidate()} is not a substitute
 * for those protections. No method accepts an untrusted child identifier.</p>
 */
public final class BridgeWorkspace {
    private final Path gameDir;
    private final Path root;
    private final Path input;
    private final Path staging;
    private final Path reports;
    private final Path cache;
    private final Path transactions;
    private final Path mods;
    private final Object gameDirectoryFileKey;

    private BridgeWorkspace(Path gameDir, Object gameDirectoryFileKey) {
        this.gameDir = gameDir;
        this.gameDirectoryFileKey = gameDirectoryFileKey;
        root = gameDir.resolve("legacybridge");
        input = root.resolve("input");
        staging = root.resolve("staging");
        reports = root.resolve("reports");
        cache = root.resolve("cache");
        transactions = root.resolve("transactions");
        mods = gameDir.resolve("mods");
    }

    /**
     * Resolves and inspects a layout without creating any path.
     *
     * @throws IllegalArgumentException if the supplied directory is relative
     * @throws IOException if it is absent, not a directory, inaccessible, or an
     *                     existing reserved path violates the layout contract
     */
    public static BridgeWorkspace inspect(Path gameDir) throws IOException {
        Objects.requireNonNull(gameDir, "gameDir");
        if (!gameDir.isAbsolute()) {
            throw new IllegalArgumentException("gameDir must be explicitly absolute");
        }
        Path canonicalGameDir = gameDir.toRealPath();
        BasicFileAttributes attributes = requireExistingDirectory(canonicalGameDir);
        BridgeWorkspace workspace = new BridgeWorkspace(canonicalGameDir, attributes.fileKey());
        workspace.revalidate();
        return workspace;
    }

    /**
     * Rechecks this previously resolved installation and reserved paths read-only.
     * When supported by the provider, the original game-directory file key also
     * detects its replacement. Providers without file keys cannot offer that check.
     */
    public void revalidate() throws IOException {
        BasicFileAttributes gameAttributes = requireExistingDirectory(gameDir);
        if (gameDirectoryFileKey != null
                && !Objects.equals(gameDirectoryFileKey, gameAttributes.fileKey())) {
            throw unsafe(gameDir, "selected game directory was replaced");
        }

        List<Path> existing = new ArrayList<>();
        existing.add(gameDir);
        for (Path path : List.of(root, input, staging, reports, cache, transactions, mods)) {
            if (path.equals(gameDir) || !path.startsWith(gameDir)) {
                throw unsafe(path, "reserved path is outside the selected game directory");
            }
            if (!path.equals(mods) && path.startsWith(mods)) {
                throw unsafe(path, "workspace must be outside the Forge mods directory");
            }
            BasicFileAttributes attributes = readIfPresent(path);
            if (attributes == null) {
                continue;
            }
            requireRealDirectory(path, attributes);
            for (Path previous : existing) {
                if (Files.isSameFile(path, previous)) {
                    throw unsafe(path, "reserved directory aliases " + previous);
                }
            }
            existing.add(path);
        }
    }

    public Path gameDir() {
        return gameDir;
    }

    public Path root() {
        return root;
    }

    public Path input() {
        return input;
    }

    public Path staging() {
        return staging;
    }

    public Path reports() {
        return reports;
    }

    public Path cache() {
        return cache;
    }

    public Path transactions() {
        return transactions;
    }

    public Path mods() {
        return mods;
    }

    private static BasicFileAttributes requireExistingDirectory(Path path) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        requireRealDirectory(path, attributes);
        return attributes;
    }

    private static BasicFileAttributes readIfPresent(Path path) throws IOException {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException absent) {
            return null;
        }
    }

    private static void requireRealDirectory(Path path, BasicFileAttributes attributes) throws IOException {
        if (attributes.isSymbolicLink()) {
            throw unsafe(path, "symbolic links are not allowed in reserved paths");
        }
        if (!attributes.isDirectory()) {
            throw unsafe(path, "reserved path is not a directory");
        }
        if (!path.equals(path.toRealPath())) {
            throw unsafe(path, "reserved directory resolves to another location");
        }
    }

    private static IOException unsafe(Path path, String reason) {
        return new IOException("Unsafe LegacyBridge workspace: " + reason + " [" + path + "]");
    }
}
