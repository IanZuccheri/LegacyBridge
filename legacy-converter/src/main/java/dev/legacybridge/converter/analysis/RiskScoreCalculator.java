package dev.legacybridge.converter.analysis;

import dev.legacybridge.converter.report.Complexity;
import dev.legacybridge.converter.report.ThirdPartyDependency;
import dev.legacybridge.converter.report.UnsupportedFeature;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Deterministic triage heuristic; scores migration effort, not probability of success. */
public final class RiskScoreCalculator {
    public Result calculate(
            List<UnsupportedFeature> features,
            List<ThirdPartyDependency> dependencies,
            int classCount
    ) {
        int score = classScopeWeight(classCount);
        int floor = 0;
        for (UnsupportedFeature feature : features) {
            LegacyFeatureId id;
            try {
                id = LegacyFeatureId.valueOf(feature.id());
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            score = Math.addExact(score, id.weight());
            floor = Math.max(floor, minimumScore(id.minimumComplexity()));
        }

        Set<String> required = new HashSet<>();
        Set<String> optional = new HashSet<>();
        for (ThirdPartyDependency dependency : dependencies) {
            if (!"DECLARED_MOD".equals(dependency.source())) continue;
            (dependency.mandatory() ? required : optional).add(dependency.identifier());
        }
        score += Math.min(12, required.size() * 4);
        score += Math.min(3, optional.size());
        score = Math.max(score, floor);
        score = Math.min(100, score);
        return new Result(score, Complexity.fromRiskScore(score));
    }

    private static int classScopeWeight(int classCount) {
        if (classCount <= 25) return 0;
        if (classCount <= 100) return 3;
        if (classCount <= 300) return 6;
        return 10;
    }

    private static int minimumScore(Complexity complexity) {
        return switch (complexity) {
            case SIMPLE -> 0;
            case MODERATE -> 15;
            case COMPLEX -> 35;
            case EXTREME -> 60;
        };
    }

    public record Result(int riskScore, Complexity complexity) {
    }
}
