package dev.legacybridge.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeWorkspaceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void definesExactSiblingDirectoriesWithoutCreatingAnything() throws Exception {
        Path gameDir = temporaryDirectory.toRealPath();
        BridgeWorkspace workspace = BridgeWorkspace.inspect(gameDir);

        assertEquals(gameDir, workspace.gameDir());
        assertEquals(gameDir.resolve("legacybridge"), workspace.root());
        assertEquals(workspace.root().resolve("input"), workspace.input());
        assertEquals(workspace.root().resolve("staging"), workspace.staging());
        assertEquals(workspace.root().resolve("reports"), workspace.reports());
        assertEquals(workspace.root().resolve("cache"), workspace.cache());
        assertEquals(workspace.root().resolve("transactions"), workspace.transactions());
        assertEquals(gameDir.resolve("mods"), workspace.mods());
        for (Path path : reservedPaths(workspace)) {
            assertTrue(path.startsWith(gameDir));
            assertFalse(Files.exists(path, LinkOption.NOFOLLOW_LINKS));
            if (!path.equals(workspace.mods())) {
                assertFalse(path.startsWith(workspace.mods()));
            }
        }
        workspace.revalidate();
        try (var children = Files.list(gameDir)) {
            assertEquals(0, children.count());
        }
    }

    @Test
    void acceptsExistingLayoutAndDoesNotReadOrModifyJarContents() throws Exception {
        BridgeWorkspace workspace = BridgeWorkspace.inspect(temporaryDirectory);
        for (Path path : reservedPaths(workspace)) {
            Files.createDirectories(path);
        }
        Path original = workspace.input().resolve("untrusted.jar");
        Path installed = workspace.mods().resolve("modern.jar");
        Files.writeString(original, "not executable or even a valid jar");
        Files.writeString(installed, "unrelated content");

        BridgeWorkspace inspected = BridgeWorkspace.inspect(temporaryDirectory);
        inspected.revalidate();

        assertEquals("not executable or even a valid jar", Files.readString(original));
        assertEquals("unrelated content", Files.readString(installed));
    }

    @Test
    void rejectsRelativeAndMissingGameDirectories() {
        assertThrows(IllegalArgumentException.class, () -> BridgeWorkspace.inspect(Path.of("game")));
        assertThrows(IOException.class, () -> BridgeWorkspace.inspect(temporaryDirectory.resolve("missing")));
        assertFalse(Files.exists(temporaryDirectory.resolve("missing")));
    }

    @Test
    void rejectsAFileAsTheGameDirectory() throws Exception {
        Path file = Files.writeString(temporaryDirectory.resolve("game-file"), "preserve");
        assertThrows(IOException.class, () -> BridgeWorkspace.inspect(file));
        assertEquals("preserve", Files.readString(file));
    }

    @Test
    void resolvesTheSelectedAbsoluteGamePathCanonically() throws Exception {
        Path child = Files.createDirectory(temporaryDirectory.resolve("child"));
        BridgeWorkspace workspace = BridgeWorkspace.inspect(child.resolve(".."));
        assertEquals(temporaryDirectory.toRealPath(), workspace.gameDir());
    }

    @Test
    void rejectsFileCollisionsAtEveryReservedPathWithoutChangingThem() throws Exception {
        for (String branch : List.of("legacybridge", "legacybridge/input", "legacybridge/staging",
                "legacybridge/reports", "legacybridge/cache", "legacybridge/transactions", "mods")) {
            Path gameDir = Files.createTempDirectory(temporaryDirectory, "case-");
            Path collision = gameDir.resolve(branch);
            Files.createDirectories(collision.getParent());
            Files.writeString(collision, "preserve");
            assertThrows(IOException.class, () -> BridgeWorkspace.inspect(gameDir), branch);
            assertEquals("preserve", Files.readString(collision));
        }
    }

    @Test
    void revalidationRejectsAPathThatBecameAFile() throws Exception {
        BridgeWorkspace workspace = BridgeWorkspace.inspect(temporaryDirectory);
        Files.createDirectories(workspace.root());
        Files.writeString(workspace.staging(), "changed after inspection");
        assertThrows(IOException.class, workspace::revalidate);
        assertEquals("changed after inspection", Files.readString(workspace.staging()));
    }

    @Test
    void revalidationRejectsRemovalOfTheSelectedGameDirectory() throws Exception {
        Path gameDir = Files.createDirectory(temporaryDirectory.resolve("game"));
        BridgeWorkspace workspace = BridgeWorkspace.inspect(gameDir);
        Files.delete(gameDir);
        assertThrows(IOException.class, workspace::revalidate);
        assertFalse(Files.exists(gameDir));
    }

    @Test
    void rejectsSymlinkAliasesWhenSupportedAndReportsCapabilityOtherwise(TestReporter reporter) throws Exception {
        Path probeTarget = Files.createDirectory(temporaryDirectory.resolve("probe-target"));
        Path probeLink = temporaryDirectory.resolve("probe-link");
        try {
            Files.createSymbolicLink(probeLink, probeTarget);
        } catch (IOException | UnsupportedOperationException | SecurityException unavailable) {
            reporter.publishEntry("workspace-symlink-tests", "UNAVAILABLE: " + unavailable.getClass().getSimpleName());
            System.out.println("LEGACYBRIDGE_WORKSPACE_SYMLINK_TEST_UNAVAILABLE "
                    + unavailable.getClass().getSimpleName());
            assertFalse(Files.exists(probeLink, LinkOption.NOFOLLOW_LINKS));
            // This fallback checks fail-closed path collisions, not symlink rejection.
            // Keep the capability limitation explicit instead of reporting a skipped test as coverage.
            Path fallbackGame = Files.createDirectory(temporaryDirectory.resolve("fallback-game"));
            Files.writeString(fallbackGame.resolve("legacybridge"), "collision");
            assertThrows(IOException.class, () -> BridgeWorkspace.inspect(fallbackGame));
            return;
        }
        assertTrue(Files.isSymbolicLink(probeLink));

        for (String branch : List.of("legacybridge", "legacybridge/input", "legacybridge/staging",
                "legacybridge/reports", "legacybridge/cache", "legacybridge/transactions", "mods")) {
            Path gameDir = Files.createTempDirectory(temporaryDirectory, "symlink-case-");
            Path alias = gameDir.resolve(branch);
            Files.createDirectories(alias.getParent());
            Files.createSymbolicLink(alias, probeTarget);
            assertThrows(IOException.class, () -> BridgeWorkspace.inspect(gameDir), branch);
        }

        Path gameDir = Files.createDirectory(temporaryDirectory.resolve("sibling-alias-game"));
        BridgeWorkspace workspace = BridgeWorkspace.inspect(gameDir);
        Files.createDirectories(workspace.staging());
        Files.createSymbolicLink(workspace.input(), workspace.staging());
        assertThrows(IOException.class, workspace::revalidate);

        Path modsAliasGame = Files.createDirectory(temporaryDirectory.resolve("mods-alias-game"));
        BridgeWorkspace modsWorkspace = BridgeWorkspace.inspect(modsAliasGame);
        Files.createDirectories(modsWorkspace.mods());
        Files.createDirectories(modsWorkspace.root());
        Files.createSymbolicLink(modsWorkspace.input(), modsWorkspace.mods());
        assertThrows(IOException.class, modsWorkspace::revalidate);

        BridgeWorkspace selectedThroughTrustedLink = BridgeWorkspace.inspect(probeLink);
        assertEquals(probeTarget.toRealPath(), selectedThroughTrustedLink.gameDir());
        reporter.publishEntry("workspace-symlink-tests", "PASSED");
    }

    private static List<Path> reservedPaths(BridgeWorkspace workspace) {
        return List.of(workspace.root(), workspace.input(), workspace.staging(),
                workspace.reports(), workspace.cache(), workspace.transactions(), workspace.mods());
    }
}
