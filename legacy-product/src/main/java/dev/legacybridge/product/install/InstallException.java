package dev.legacybridge.product.install;

import dev.legacybridge.converter.service.EngineError;
import java.nio.file.Path;
import java.util.Optional;

/** An installed path means publication won; never describe that result as a clean cancellation. */
public final class InstallException extends Exception {
    private final EngineError error;
    private final Optional<Path> installed;
    private final Optional<Path> transaction;

    public InstallException(String code, String message, String remediation,
                            Optional<Path> installed, Optional<Path> transaction) {
        this(new EngineError(code, message, remediation), installed, transaction);
    }
    private InstallException(EngineError error, Optional<Path> installed, Optional<Path> transaction) {
        super(error.message());
        this.error = error;
        this.installed = java.util.Objects.requireNonNull(installed);
        this.transaction = java.util.Objects.requireNonNull(transaction);
    }
    public String code() { return error.code(); }
    public String remediation() { return error.remediation(); }
    public Optional<Path> installed() { return installed; }
    public Optional<Path> transaction() { return transaction; }
}
