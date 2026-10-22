package dev.legacybridge.product.install;

import dev.legacybridge.runtime.compat.AdapterCatalog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Test-only inert platform declaration and precise installer fault; never shipped. */
public final class ReceiptInstallerFixtures {
    private ReceiptInstallerFixtures() { }
    public static void platform(Path game) throws IOException {
        AddOnlyInstallerTest.installedMod(Files.createDirectories(game.resolve("mods")).resolve("LegacyBridge.jar"),
                "legacybridge_runtime", AdapterCatalog.RUNTIME_VERSION);
    }
    public static TransactionalInstaller partial() {
        return new TransactionalInstaller((boundary, index) -> {
            if (boundary == TransactionalInstaller.Boundary.PUBLISHED && index == 0)
                throw new IOException("Synthetic interruption after first publication");
        });
    }
    public static PreservedUpdateInstaller partialPreservedUpdate() {
        return new PreservedUpdateInstaller((boundary, transaction) -> {
            if (boundary == PreservedUpdateInstaller.Boundary.PUBLISHED)
                throw new IOException("Synthetic interruption after preserved update publication");
        });
    }
}
