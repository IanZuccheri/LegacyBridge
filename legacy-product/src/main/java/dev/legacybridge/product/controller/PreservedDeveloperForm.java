package dev.legacybridge.product.controller;

import dev.legacybridge.converter.service.MappingData;
import dev.legacybridge.product.install.RecoveryAction;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Optional;
import java.util.UUID;

/** Bounded, I/O-free form. Editing invalidates consent even if the old text is restored. */
public final class PreservedDeveloperForm {
    public enum Kind { ADD, UPDATE }
    public enum Mode { APPLY, RESUME, RESTORE }
    public enum Field {
        ADMISSION(4096), ADMISSION_HASH(64), DATA(4096), DATA_HASH(64),
        TRANSACTION(4096), PLAN_HASH(64), APPROVAL(36),
        PREVIOUS_INSTALLATION(4096), PREVIOUS_PLAN_HASH(64);
        private final int limit;
        Field(int limit) { this.limit = limit; }
        public int limit() { return limit; }
    }
    public static final class InvalidField extends IllegalArgumentException {
        private final Field field;
        InvalidField(Field field) { super("INVALID_" + field.name()); this.field = field; }
        public Field field() { return field; }
    }
    private final EnumMap<Field, String> values = new EnumMap<>(Field.class);
    private Mode mode = Mode.APPLY;
    private Kind kind = Kind.ADD;
    private long revision, reviewedRevision = -1;
    private UUID operation;
    private boolean consumed;

    public String get(Field field) { return values.getOrDefault(field, ""); }
    public Mode mode() { return mode; }
    public Kind kind() { return kind; }
    public void kind(Kind next) {
        java.util.Objects.requireNonNull(next);
        if (kind != next) { kind = next; invalidate(); }
    }
    public void mode(Mode next) {
        java.util.Objects.requireNonNull(next);
        if (mode != next) { mode = next; invalidate(); }
    }
    public void set(Field field, String value) {
        if (value == null || value.length() > field.limit()) throw new InvalidField(field);
        if (!get(field).equals(value)) { values.put(field, value); invalidate(); }
    }
    public void invalidate() { revision++; operation = null; consumed = false; }
    public void reviewStarted(UUID id) { operation = java.util.Objects.requireNonNull(id); reviewedRevision = revision; consumed = false; }
    public boolean canConfirm(PreservedInstallView view, boolean busy) {
        return kind == Kind.ADD && currentReview(view, busy);
    }
    private boolean currentReview(PreservedInstallView view, boolean busy) {
        return !busy && !consumed && operation != null && revision == reviewedRevision
                && view.operationId().filter(operation::equals).isPresent()
                && view.state() == PreservedInstallView.State.REVIEWED && view.error().isEmpty()
                && view.receipt().isPresent() && view.reviewId().isPresent();
    }
    public Optional<UUID> consume(PreservedInstallView view, boolean busy) {
        if (!canConfirm(view, busy)) return Optional.empty();
        consumed = true; return view.reviewId();
    }
    /** The shared install observation alone cannot authorize an update or cross-kind consent. */
    public boolean canConfirm(PreservedUpdateView view, boolean busy) {
        if (kind != Kind.UPDATE || !currentReview(view.observation(), busy)) return false;
        try {
            return view.selection().filter(updateSelection()::equals).isPresent()
                    && (mode != Mode.APPLY || view.preview().filter(p -> p.reviewed()).isPresent());
        } catch (InvalidField invalid) { return false; }
    }
    public Optional<UUID> consume(PreservedUpdateView view, boolean busy) {
        if (!canConfirm(view, busy)) return Optional.empty();
        consumed = true; return view.observation().reviewId();
    }
    public PreservedUpdateSelection updateSelection() {
        if (kind != Kind.UPDATE) throw new IllegalStateException("UPDATE_NOT_SELECTED");
        return new PreservedUpdateSelection(selection(), path(Field.PREVIOUS_INSTALLATION), hash(Field.PREVIOUS_PLAN_HASH));
    }
    public PreservedInstallSelection selection() {
        var admission = path(Field.ADMISSION); var admissionHash = hash(Field.ADMISSION_HASH);
        var data = MappingData.reviewedBundle(path(Field.DATA), hash(Field.DATA_HASH));
        Optional<PreservedInstallSelection.Recovery> recovery = Optional.empty();
        if (mode != Mode.APPLY) recovery = Optional.of(new PreservedInstallSelection.Recovery(
                path(Field.TRANSACTION), hash(Field.PLAN_HASH), approval(),
                mode == Mode.RESUME ? RecoveryAction.RESUME : RecoveryAction.RESTORE));
        return new PreservedInstallSelection(admission, admissionHash, data, recovery);
    }
    private Path path(Field field) {
        try {
            Path path = Path.of(get(field));
            if (!path.isAbsolute() || !path.equals(path.normalize()) || get(field).chars().anyMatch(c -> c < 32 || c == 127))
                throw new InvalidField(field);
            return path;
        } catch (java.nio.file.InvalidPathException invalid) { throw new InvalidField(field); }
    }
    private String hash(Field field) {
        if (!get(field).matches("[a-f0-9]{64}")) throw new InvalidField(field);
        return get(field);
    }
    private UUID approval() {
        try {
            UUID id = UUID.fromString(get(Field.APPROVAL));
            if (!id.toString().equals(get(Field.APPROVAL))) throw new InvalidField(Field.APPROVAL);
            return id;
        } catch (IllegalArgumentException invalid) { throw new InvalidField(Field.APPROVAL); }
    }
}
