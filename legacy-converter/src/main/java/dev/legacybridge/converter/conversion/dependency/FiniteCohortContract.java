package dev.legacybridge.converter.conversion.dependency;

import java.util.Map;
import java.util.Set;

/** Exact archive admission shared by the existing target recipe and its smaller platform data scope. */
public final class FiniteCohortContract {
    public static final Map<String, String> REVIEWED = Map.of(
            "lb_cohort_library", "7ccceb72ef6d1518310b21253acf18cff0502e8f06a892758ea254ae799f5cd4",
            "lb_cohort_consumer", "a67a24e331c1d81b5e85ce267c9d55e622294f01134cdc9cca8cdcad76e7e8f1");
    public static final Map<String, Set<String>> CLASSES = Map.of(
            "lb_cohort_library", Set.of("dev/legacybridge/fixture/cohort/library/LibraryMod.class", "dev/legacybridge/fixture/cohort/library/Score.class"),
            "lb_cohort_consumer", Set.of("dev/legacybridge/fixture/cohort/consumer/ConsumerMod.class"));
    private FiniteCohortContract() { }
    public static boolean reviewedHash(String sha256) { return REVIEWED.containsValue(sha256); }
}
