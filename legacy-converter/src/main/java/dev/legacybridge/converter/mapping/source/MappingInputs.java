package dev.legacybridge.converter.mapping.source;

import net.minecraftforge.srgutils.IMappingFile;

import java.util.List;
import java.util.Objects;

/** Full inputs compose official -> obfuscated -> SRG; finite scope supplies reviewed source aliases only. */
public record MappingInputs(
        IMappingFile legacyOfficial,
        IMappingFile targetOfficial,
        IMappingFile legacySrg,
        IMappingFile targetSrg,
        ApiIndex legacyApi,
        ApiIndex targetApi,
        List<MappingSource> sources,
        List<String> diagnostics,
        boolean complete,
        Scope scope
) {
    /** A finite bootstrap contract must never impersonate a complete mapping environment. */
    public enum Scope { FULL_ENVIRONMENT, SIMPLE_CONTENT_CONTRACT, FINITE_COHORT_CONTRACT }

    public MappingInputs(IMappingFile legacyOfficial, IMappingFile targetOfficial,
                         IMappingFile legacySrg, IMappingFile targetSrg,
                         ApiIndex legacyApi, ApiIndex targetApi, List<MappingSource> sources,
                         List<String> diagnostics, boolean complete) {
        this(legacyOfficial, targetOfficial, legacySrg, targetSrg, legacyApi, targetApi,
                sources, diagnostics, complete, Scope.FULL_ENVIRONMENT);
    }

    public MappingInputs {
        Objects.requireNonNull(legacyOfficial, "legacyOfficial");
        Objects.requireNonNull(targetOfficial, "targetOfficial");
        Objects.requireNonNull(legacySrg, "legacySrg");
        Objects.requireNonNull(targetSrg, "targetSrg");
        Objects.requireNonNull(legacyApi, "legacyApi");
        Objects.requireNonNull(targetApi, "targetApi");
        Objects.requireNonNull(scope, "scope");
        if (scope != Scope.FULL_ENVIRONMENT && complete)
            throw new IllegalArgumentException("A finite source contract is not a complete mapping environment");
        sources = List.copyOf(sources);
        diagnostics = List.copyOf(diagnostics);
    }
}
