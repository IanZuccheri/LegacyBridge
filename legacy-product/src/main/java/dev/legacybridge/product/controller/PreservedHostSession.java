package dev.legacybridge.product.controller;

import dev.legacybridge.product.install.InstallSession;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Client host publishes only on its owning thread; workers read immutable loader/world observations. */
public final class PreservedHostSession implements Supplier<InstallSession> {
    private final Thread owner = Thread.currentThread();
    private final AtomicReference<InstallSession> current = new AtomicReference<>();
    public void publish(InstallSession value) { onOwner(); current.set(Objects.requireNonNull(value)); }
    public void invalidate() { onOwner(); current.set(null); }
    private void onOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Host snapshot publication on wrong thread");
    }
    @Override public InstallSession get() {
        return Objects.requireNonNull(current.get(), "Host session unavailable; a current client observation is required");
    }
}
