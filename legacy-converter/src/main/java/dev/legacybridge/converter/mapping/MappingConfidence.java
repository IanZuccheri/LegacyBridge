package dev.legacybridge.converter.mapping;

/** VERIFIED requires independent semantic validation; name/signature matches alone are HIGH. */
public enum MappingConfidence {
    VERIFIED, HIGH, HEURISTIC, NONE;

    static MappingConfidence weakest(MappingConfidence first, MappingConfidence second) {
        return first.ordinal() >= second.ordinal() ? first : second;
    }
}
