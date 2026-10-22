package dev.legacybridge.converter.service;

import dev.legacybridge.converter.mapping.portable.PortableMappingBundle;
import dev.legacybridge.converter.mapping.source.MappingInputs;
import dev.legacybridge.converter.operation.OperationControl;
import java.io.IOException;
import java.util.*;

/** Small immutable declaration review; never retains mapping trees or approves a mod/conversion/install. */
public record MappingDataReview(MappingData selection, String scope, boolean complete, List<IndexCount> indexes,
                                List<Provenance> provenance, String distributionPolicy, List<String> operations) {
    public record IndexCount(String id, long classes, long members, long interfaces) {
        Map<String, Object> toTree() { return Map.of("id", id, "classes", classes, "members", members, "interfaces", interfaces); }
    }
    public record Provenance(String id, String sha256) { }
    public MappingDataReview {
        Objects.requireNonNull(selection); Objects.requireNonNull(scope); Objects.requireNonNull(distributionPolicy);
        indexes = List.copyOf(indexes); provenance = List.copyOf(provenance); operations = List.copyOf(operations);
        if (indexes.size() > 6 || provenance.size() > 64 || operations.size() > 10) throw new IllegalArgumentException("Review bounds");
    }
    public Map<String, Object> toTree() {
        var result = new TreeMap<String, Object>();
        result.put("schemaVersion", 1); result.put("reportType", "LEGACY_MAPPING_DATA_REVIEW");
        result.put("status", "DECLARATIONS_VALIDATED"); result.put("kind", selection.kind().name());
        result.put("manifestSha256", selection.expectedManifestSha256().orElse(""));
        result.put("scope", scope); result.put("completeMappingEnvironment", complete);
        result.put("indexes", indexes.stream().map(IndexCount::toTree).toList());
        result.put("provenance", provenance.stream().map(p -> Map.of("id", p.id(), "sha256", p.sha256())).toList());
        result.put("distributionPolicy", distributionPolicy); result.put("eligibleDataOperations", operations);
        result.put("publisherAuthenticated", false); result.put("semanticEquivalenceProven", false);
        result.put("conversionAuthorized", false); result.put("installationAllowed", false);
        result.put("classesExecuted", false); return Collections.unmodifiableMap(result);
    }
    static MappingDataReview inspect(MappingData selection) throws IOException {
        if (selection.kind() == MappingData.Kind.EXPLICIT_PROFILE)
            throw new IllegalArgumentException("Prepare a portable bundle explicitly before reviewing it; review never discovers development files");
        MappingData pinned = selection.kind() == MappingData.Kind.PORTABLE_BUNDLE && selection.expectedManifestSha256().isEmpty()
                ? MappingData.reviewedBundle(selection.path().orElseThrow(), PortableMappingBundle.manifestFingerprint(selection.path().orElseThrow())) : selection;
        MappingInputs inputs = pinned.load();
        List<IndexCount> counts = new ArrayList<>();
        var maps = List.of(inputs.legacyOfficial(), inputs.targetOfficial(), inputs.legacySrg(), inputs.targetSrg());
        var ids = List.of("legacy.official", "target.official", "legacy.srg", "target.srg");
        for (int i = 0; i < maps.size(); i++) {
            long members = 0;
            for (var type : maps.get(i).getClasses()) { OperationControl.checkpoint(); members += type.getMethods().size() + type.getFields().size(); }
            counts.add(new IndexCount(ids.get(i), maps.get(i).getClasses().size(), members, 0));
        }
        int side = 0;
        for (var api : List.of(inputs.legacyApi(), inputs.targetApi())) {
            long members = 0, interfaces = 0;
            for (var type : api.classes().values()) {
                OperationControl.checkpoint(); members += type.methods().size() + type.fields().size(); interfaces += type.interfaces().size();
            }
            counts.add(new IndexCount(side++ == 0 ? "legacy.api" : "target.api", api.classes().size(), members, interfaces));
        }
        List<String> operations = switch (inputs.scope()) {
            case SIMPLE_CONTENT_CONTRACT -> List.of("simple-content-v1");
            case FINITE_COHORT_CONTRACT -> List.of("inspect-cohort-exact-authored", "plan-cohort-exact-authored");
            case FULL_ENVIRONMENT -> List.of("mappings", "six-existing-profiles", "logic-with-recipes-preview", "cohort-inspection");
        };
        return new MappingDataReview(pinned, inputs.scope().name(), inputs.complete(), counts,
                inputs.sources().stream().map(s -> new Provenance(s.id(), s.sha256())).toList(),
                inputs.complete() ? PortableMappingBundle.DISTRIBUTION_POLICY : "INCLUDED_FINITE_COMPONENT_NOT_GENERAL_DATA", operations);
    }
}
