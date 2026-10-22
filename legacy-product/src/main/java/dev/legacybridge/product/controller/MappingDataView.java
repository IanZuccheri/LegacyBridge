package dev.legacybridge.product.controller;

import dev.legacybridge.converter.service.EngineError;
import dev.legacybridge.converter.service.MappingDataReview;
import java.util.Optional;

/** Bounded immutable data review; no mapping trees, installer token or inferred mod approval. */
public record MappingDataView(Status status, Optional<MappingDataReview> review, Optional<EngineError> error) {
    public enum Status { NOT_REQUESTED, RUNNING, REVIEWED, FAILED, CANCELLED }
    public MappingDataView {
        java.util.Objects.requireNonNull(status); java.util.Objects.requireNonNull(review); java.util.Objects.requireNonNull(error);
        if (review.isPresent() != (status == Status.REVIEWED)) throw new IllegalArgumentException("Only a completed data review has a value");
    }
    public static MappingDataView initial() { return new MappingDataView(Status.NOT_REQUESTED, Optional.empty(), Optional.empty()); }
}
