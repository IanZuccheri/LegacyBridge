package dev.legacybridge.workflow;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestartPolicyTest {
    private static final RestartPolicy.RestartContext CONTEXT = context("test-launcher", "1", "windows-11-x64",
            "test-installation", "1.20.1", "47.4.23", 17, "0.1.1-SNAPSHOT", "test-config-v1");

    @Test
    void absentProviderAlwaysOffersApplyAndCloseWithManualRelaunch() {
        RestartPolicy.Decision decision = RestartPolicy.decide(CONTEXT, Optional.empty());
        assertEquals(RestartPolicy.Action.APPLY_AND_CLOSE, decision.action());
        assertEquals(RestartPolicy.Reason.NO_VERIFIED_PROVIDER, decision.reason());
        assertEquals("legacybridge.restart.apply_and_close", decision.labelKey());
        assertTrue(decision.providerId().isEmpty());
    }

    @Test
    void trustedExactContextEvidenceAllowsSelectingAFutureRealProvider() {
        // Synthetic contract data only: this test does not implement or validate a launcher.
        RestartPolicy.VerifiedProviderEvidence evidence = evidence(CONTEXT);
        RestartPolicy.Decision decision = RestartPolicy.decide(CONTEXT, Optional.of(evidence));
        assertEquals(RestartPolicy.Action.AUTOMATIC_RESTART, decision.action());
        assertEquals(RestartPolicy.Reason.EXACT_CONTEXT_VERIFIED, decision.reason());
        assertEquals(Optional.of("test-provider"), decision.providerId());
        assertEquals("legacybridge.restart.apply_and_restart", decision.labelKey());
    }

    @Test
    void everyContextDimensionMustMatchExactly() {
        List<RestartPolicy.RestartContext> mismatches = List.of(
                context("other-launcher", "1", "windows-11-x64", "test-installation", "1.20.1", "47.4.23", 17,
                        "0.1.1-SNAPSHOT", "test-config-v1"),
                context("test-launcher", "2", "windows-11-x64", "test-installation", "1.20.1", "47.4.23", 17,
                        "0.1.1-SNAPSHOT", "test-config-v1"),
                context("test-launcher", "1", "linux-x64", "test-installation", "1.20.1", "47.4.23", 17,
                        "0.1.1-SNAPSHOT", "test-config-v1"),
                context("test-launcher", "1", "windows-11-x64", "other-installation", "1.20.1", "47.4.23", 17,
                        "0.1.1-SNAPSHOT", "test-config-v1"),
                context("test-launcher", "1", "windows-11-x64", "test-installation", "1.20.2", "47.4.23", 17,
                        "0.1.1-SNAPSHOT", "test-config-v1"),
                context("test-launcher", "1", "windows-11-x64", "test-installation", "1.20.1", "47.4.24", 17,
                        "0.1.1-SNAPSHOT", "test-config-v1"),
                context("test-launcher", "1", "windows-11-x64", "test-installation", "1.20.1", "47.4.23", 21,
                        "0.1.1-SNAPSHOT", "test-config-v1"),
                context("test-launcher", "1", "windows-11-x64", "test-installation", "1.20.1", "47.4.23", 17,
                        "0.1.2-SNAPSHOT", "test-config-v1"),
                context("test-launcher", "1", "windows-11-x64", "test-installation", "1.20.1", "47.4.23", 17,
                        "0.1.1-SNAPSHOT", "test-config-v2"));
        for (RestartPolicy.RestartContext mismatch : mismatches) {
            RestartPolicy.Decision decision = RestartPolicy.decide(mismatch, Optional.of(evidence(CONTEXT)));
            assertEquals(RestartPolicy.Action.APPLY_AND_CLOSE, decision.action(), mismatch.toString());
            assertEquals(RestartPolicy.Reason.CONTEXT_MISMATCH, decision.reason());
            assertTrue(decision.providerId().isEmpty());
        }
    }

    @Test
    void wildcardEvidenceDoesNotMatchAConcreteContext() {
        RestartPolicy.RestartContext wildcard = context("*", "*", "*", "*", "*", "*", 17, "*", "*");
        assertEquals(RestartPolicy.Action.APPLY_AND_CLOSE,
                RestartPolicy.decide(CONTEXT, Optional.of(evidence(wildcard))).action());
    }

    @Test
    void caseDifferencesAreNotSilentlyNormalizedIntoVerifiedMatches() {
        RestartPolicy.RestartContext differentCase = context("TEST-LAUNCHER", "1", "windows-11-x64",
                "test-installation", "1.20.1", "47.4.23", 17, "0.1.1-SNAPSHOT", "test-config-v1");
        assertEquals(RestartPolicy.Reason.CONTEXT_MISMATCH,
                RestartPolicy.decide(differentCase, Optional.of(evidence(CONTEXT))).reason());
    }

    @Test
    void evidenceRequiresProviderContextAndNonblankValidationReference() {
        assertThrows(IllegalArgumentException.class,
                () -> new RestartPolicy.VerifiedProviderEvidence("", CONTEXT, "test-validation"));
        assertThrows(NullPointerException.class,
                () -> new RestartPolicy.VerifiedProviderEvidence("test-provider", null, "test-validation"));
        assertThrows(IllegalArgumentException.class,
                () -> new RestartPolicy.VerifiedProviderEvidence("test-provider", CONTEXT, " "));
        assertThrows(NullPointerException.class,
                () -> new RestartPolicy.VerifiedProviderEvidence("test-provider", CONTEXT, null));
    }

    @Test
    void identifiersRejectControlCharactersPaddingAndOversizedValues() {
        for (String invalid : List.of("test\nprovider", " test-provider", "test-provider ", "x".repeat(257))) {
            assertThrows(IllegalArgumentException.class,
                    () -> new RestartPolicy.VerifiedProviderEvidence(invalid, CONTEXT, "test-validation"));
        }
        assertThrows(IllegalArgumentException.class,
                () -> context("", "1", "platform", "install", "1.20.1", "47.4.23", 17, "0.1.1", "config"));
        assertThrows(IllegalArgumentException.class,
                () -> context("launcher", "1", "platform", "install", "1.20.1", "47.4.23", 0, "0.1.1", "config"));
    }

    @Test
    void nullDecisionInputsCannotImplyAnAvailableProvider() {
        assertThrows(NullPointerException.class, () -> RestartPolicy.decide(null, Optional.empty()));
        assertThrows(NullPointerException.class, () -> RestartPolicy.decide(CONTEXT, null));
    }

    @Test
    void inconsistentPresentationDecisionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RestartPolicy.Decision(
                RestartPolicy.Action.AUTOMATIC_RESTART, RestartPolicy.Reason.NO_VERIFIED_PROVIDER,
                Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new RestartPolicy.Decision(
                RestartPolicy.Action.AUTOMATIC_RESTART, RestartPolicy.Reason.EXACT_CONTEXT_VERIFIED,
                Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new RestartPolicy.Decision(
                RestartPolicy.Action.APPLY_AND_CLOSE, RestartPolicy.Reason.EXACT_CONTEXT_VERIFIED,
                Optional.of("test-provider")));
        assertThrows(IllegalArgumentException.class, () -> new RestartPolicy.Decision(
                RestartPolicy.Action.APPLY_AND_CLOSE, RestartPolicy.Reason.NO_VERIFIED_PROVIDER,
                Optional.of("test-provider")));
    }

    private static RestartPolicy.VerifiedProviderEvidence evidence(RestartPolicy.RestartContext context) {
        return new RestartPolicy.VerifiedProviderEvidence("test-provider", context, "synthetic-test-only");
    }

    private static RestartPolicy.RestartContext context(String launcher, String launcherVersion,
            String platform, String installation, String minecraft, String forge, int javaFeature,
            String bridge, String configuration) {
        return new RestartPolicy.RestartContext(launcher, launcherVersion, platform, installation,
                minecraft, forge, javaFeature, bridge, configuration);
    }
}
