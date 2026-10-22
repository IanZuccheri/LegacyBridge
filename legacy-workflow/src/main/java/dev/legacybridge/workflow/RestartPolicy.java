package dev.legacybridge.workflow;

import java.util.Objects;
import java.util.Optional;

/**
 * Conservative UI decision policy, not a process launcher or a restart implementation.
 *
 * <p>No verified provider is supplied by this module. Until a real provider has been
 * implemented and validated for the exact context, callers must supply an empty
 * evidence value and offer application plus shutdown/manual relaunch. A launcher name,
 * a URL, the presence of a launcher process or a successfully closed game is not
 * restart evidence.</p>
 */
public final class RestartPolicy {
    private RestartPolicy() {
    }

    public static Decision decide(RestartContext currentContext,
                                  Optional<VerifiedProviderEvidence> verifiedProvider) {
        Objects.requireNonNull(currentContext, "currentContext");
        Objects.requireNonNull(verifiedProvider, "verifiedProvider");
        if (verifiedProvider.isEmpty()) {
            return new Decision(Action.APPLY_AND_CLOSE, Reason.NO_VERIFIED_PROVIDER, Optional.empty());
        }
        VerifiedProviderEvidence evidence = verifiedProvider.orElseThrow();
        if (!currentContext.equals(evidence.validatedContext())) {
            return new Decision(Action.APPLY_AND_CLOSE, Reason.CONTEXT_MISMATCH, Optional.empty());
        }
        return new Decision(Action.AUTOMATIC_RESTART, Reason.EXACT_CONTEXT_VERIFIED,
                Optional.of(evidence.providerId()));
    }

    public enum Action {
        /** Future provider operation; never implemented by merely terminating the game. */
        AUTOMATIC_RESTART("legacybridge.restart.apply_and_restart"),
        /** Apply and close; the user must reopen the game through the launcher. */
        APPLY_AND_CLOSE("legacybridge.restart.apply_and_close");

        private final String labelKey;

        Action(String labelKey) {
            this.labelKey = labelKey;
        }

        public String labelKey() {
            return labelKey;
        }
    }

    public enum Reason {
        NO_VERIFIED_PROVIDER,
        CONTEXT_MISMATCH,
        EXACT_CONTEXT_VERIFIED
    }

    /**
     * Exact scope of a future provider validation. IDs/fingerprints must distinguish
     * relevant launch mode, platform and installation configuration; no wildcards or
     * version-range matching is performed. Use non-sensitive identifiers, never tokens,
     * account credentials or raw process arguments. The configuration fingerprint must
     * change when restart-relevant configuration changes and must not encode credentials.
     */
    public record RestartContext(String launcherId, String launcherVersion, String platformId,
                                 String installationId, String minecraftVersion, String forgeVersion,
                                 int javaFeatureVersion, String bridgeVersion,
                                 String configurationFingerprint) {
        public RestartContext {
            launcherId = requireIdentifier(launcherId, "launcherId");
            launcherVersion = requireIdentifier(launcherVersion, "launcherVersion");
            platformId = requireIdentifier(platformId, "platformId");
            installationId = requireIdentifier(installationId, "installationId");
            minecraftVersion = requireIdentifier(minecraftVersion, "minecraftVersion");
            forgeVersion = requireIdentifier(forgeVersion, "forgeVersion");
            bridgeVersion = requireIdentifier(bridgeVersion, "bridgeVersion");
            configurationFingerprint = requireIdentifier(configurationFingerprint, "configurationFingerprint");
            if (javaFeatureVersion < 1) {
                throw new IllegalArgumentException("javaFeatureVersion must be positive");
            }
        }
    }

    /**
     * Assertion from the trusted application/provider registry, not user input and not
     * an independently verified certificate. Creating this value does not validate a
     * provider. The caller must check that the real provider is installed, callable and
     * has passed the referenced end-to-end restart validation in this exact context.
     * There is intentionally no fake/default provider or reflective provider loading.
     */
    public record VerifiedProviderEvidence(String providerId, RestartContext validatedContext,
                                           String validationReference) {
        public VerifiedProviderEvidence {
            providerId = requireIdentifier(providerId, "providerId");
            Objects.requireNonNull(validatedContext, "validatedContext");
            validationReference = requireIdentifier(validationReference, "validationReference");
        }
    }

    /**
     * Presentation decision only. Neither action authorizes touching files or exiting;
     * installation must commit safely first, and shutdown must obey session/world rules.
     */
    public record Decision(Action action, Reason reason, Optional<String> providerId) {
        public Decision {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(providerId, "providerId");
            if (action == Action.AUTOMATIC_RESTART
                    && (reason != Reason.EXACT_CONTEXT_VERIFIED || providerId.isEmpty())) {
                throw new IllegalArgumentException("Automatic restart requires exact-context provider evidence");
            }
            if (action == Action.APPLY_AND_CLOSE
                    && (reason == Reason.EXACT_CONTEXT_VERIFIED || providerId.isPresent())) {
                throw new IllegalArgumentException("Manual relaunch must not claim a verified provider");
            }
            providerId.ifPresent(id -> requireIdentifier(id, "providerId"));
        }

        public String labelKey() {
            return action.labelKey();
        }
    }

    private static String requireIdentifier(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || !value.equals(value.strip()) || value.length() > 256
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " must be a nonblank bounded identifier without controls");
        }
        return value;
    }
}
