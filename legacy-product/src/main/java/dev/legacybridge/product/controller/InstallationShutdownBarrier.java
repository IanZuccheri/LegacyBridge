package dev.legacybridge.product.controller;

import java.util.Objects;

/**
 * Keeps an orderly JVM shutdown waiting for already accepted filesystem installations.
 *
 * <p>Acquire before accepting/enqueuing installation work, then close the lease from
 * its final completion/refusal path, including enqueue failure. There is one hook
 * for this product class, not one per screen, controller, operation or lease.
 * Neither acquire nor close waits for an installation or calls Minecraft.</p>
 *
 * <p>Registration must succeed before the first lease can escape. Once the hook
 * enters, further acquisitions are refused; acquisitions racing ahead of that
 * transition are included in its wait. Java exposes no public shutdown-state
 * query after registration. This is an orderly-shutdown barrier, not a defence
 * against Runtime.halt, forced process termination, VM failure or power loss.
 * Interrupted hook waits continue: a timeout/interrupt cannot turn an unfinished
 * installation into safe completion. Transaction recovery remains necessary.</p>
 */
public final class InstallationShutdownBarrier {
    private static final State SHARED = new State(Runtime.getRuntime()::addShutdownHook);

    private InstallationShutdownBarrier() { }

    /**
     * @throws IllegalStateException if shutdown was observed or hook registration
     *                               is refused because JVM shutdown already began
     * @throws SecurityException if the JVM forbids registering the required hook
     */
    public static Lease acquire() { return SHARED.acquire(); }

    /** Exactly one release, even when multiple completion paths close this handle. */
    public static final class Lease implements AutoCloseable {
        private final State owner;
        private boolean closed;

        private Lease(State owner) { this.owner = owner; }
        @Override public void close() { owner.release(this); }
    }

    /** Package-private registration seam allows lifecycle tests without shutting down the test JVM. */
    @FunctionalInterface interface HookRegistrar { void register(Thread hook); }

    static final class State {
        private final Object gate = new Object();
        private final HookRegistrar registrar;
        private final Thread hook;
        private boolean registered;
        private boolean shuttingDown;
        private int accepted;

        State(HookRegistrar registrar) {
            this.registrar = Objects.requireNonNull(registrar);
            hook = new Thread(this::awaitCompletion, "legacybridge-installation-shutdown");
        }

        Lease acquire() {
            synchronized (gate) {
                if (shuttingDown) throw new IllegalStateException("Installation cannot be accepted during shutdown");
                if (!registered) {
                    try { registrar.register(hook); }
                    catch (IllegalStateException shutdown) {
                        shuttingDown = true;
                        throw shutdown;
                    }
                    registered = true;
                }
                // Allocate before retaining a count: an allocation failure cannot strand a lease.
                Lease lease = new Lease(this);
                accepted = Math.incrementExact(accepted);
                return lease;
            }
        }

        private void release(Lease lease) {
            synchronized (gate) {
                if (lease.closed) return;
                lease.closed = true;
                accepted--;
                if (accepted == 0) gate.notifyAll();
            }
        }

        private void awaitCompletion() {
            boolean interrupted = false;
            synchronized (gate) {
                shuttingDown = true;
                while (accepted != 0) {
                    try { gate.wait(); }
                    catch (InterruptedException interruption) { interrupted = true; }
                }
            }
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
