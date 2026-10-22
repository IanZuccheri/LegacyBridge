package dev.legacybridge.product.controller;

import dev.legacybridge.converter.service.MappingData;
import java.nio.file.Path;
import java.util.*;

/** No filesystem work; a changed form cannot hand off an earlier completed operation. */
public final class PreservedPreparationForm {
    public enum Field { ORIGINAL, ORIGINAL_HASH, CANDIDATE, CANDIDATE_HASH, DATA, DATA_HASH;
        public int limit() { return name().endsWith("_HASH") ? 64 : 4096; }
    }
    public static final class InvalidField extends IllegalArgumentException {
        private final Field field;
        InvalidField(Field field) { super("INVALID_" + field); this.field = field; }
        public Field field() { return field; }
    }
    private final Map<Field, String> values = new EnumMap<>(Field.class);
    private boolean convert = true, consumed;
    private long revision, submittedRevision;
    private UUID operation;
    public String get(Field field) { return values.getOrDefault(field, ""); }
    public boolean convert() { return convert; }
    public void convert(boolean value) { if (convert != value) { convert = value; revision++; } }
    public void set(Field field, String value) {
        if (value == null || value.length() > field.limit()) throw new InvalidField(field);
        if (!get(field).equals(value)) { values.put(field, value); revision++; }
    }
    public void submitted(UUID id) { operation = Objects.requireNonNull(id); submittedRevision = revision; consumed = false; }
    public boolean canUse(PreservedPreparationView view, boolean busy) {
        return !busy && !consumed && operation != null && revision == submittedRevision
                && view.operationId().filter(operation::equals).isPresent() && view.state() == PreservedPreparationView.State.READY;
    }
    public Optional<UUID> consume(PreservedPreparationView view, boolean busy) {
        if (!canUse(view, busy)) return Optional.empty(); consumed = true; return Optional.of(operation);
    }
    public PreservedPreparationRequest request() {
        var original = path(Field.ORIGINAL); var originalHash = hash(Field.ORIGINAL_HASH);
        var data = MappingData.reviewedBundle(path(Field.DATA), hash(Field.DATA_HASH));
        Optional<PreservedPreparationRequest.Candidate> candidate = Optional.empty();
        if (!convert) {
            var target = path(Field.CANDIDATE); if (target.equals(original)) throw new InvalidField(Field.CANDIDATE);
            candidate = Optional.of(new PreservedPreparationRequest.Candidate(target, hash(Field.CANDIDATE_HASH)));
        }
        return new PreservedPreparationRequest(original, originalHash, candidate, data);
    }
    private Path path(Field field) {
        try { return PreservedPreparationRequest.checkedPath(Path.of(get(field))); }
        catch (IllegalArgumentException failure) { throw new InvalidField(field); }
    }
    private String hash(Field field) {
        try { return PreservedPreparationRequest.hash(get(field)); }
        catch (IllegalArgumentException failure) { throw new InvalidField(field); }
    }
}
