package dev.legacybridge.converter.report;

/** Migration-effort triage band. It is not a runnability probability. */
public enum Complexity {
    SIMPLE,
    MODERATE,
    COMPLEX,
    EXTREME;

    public static Complexity fromRiskScore(int riskScore) {
        if (riskScore < 0 || riskScore > 100) {
            throw new IllegalArgumentException("riskScore must be between 0 and 100");
        }
        if (riskScore >= 60) {
            return EXTREME;
        }
        if (riskScore >= 35) {
            return COMPLEX;
        }
        if (riskScore >= 15) {
            return MODERATE;
        }
        return SIMPLE;
    }
}
