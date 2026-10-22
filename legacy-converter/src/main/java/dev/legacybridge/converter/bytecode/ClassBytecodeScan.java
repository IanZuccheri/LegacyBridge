package dev.legacybridge.converter.bytecode;

import dev.legacybridge.converter.namespace.NamespaceSummary;

import java.util.Objects;

/**
 * Immutable, short-lived result for one class-file entry.
 *
 * <p>The whole-archive pipeline merges this value immediately and does not retain it. Only
 * bounded namespace samples and scalar counters belong here; details that are not emitted in
 * the compatibility report must not turn into archive-wide collections.</p>
 */
public record ClassBytecodeScan(
        String entryName,
        String internalName,
        int minorVersion,
        int majorVersion,
        boolean preview,
        int accessFlags,
        BytecodeFeatureCounts features,
        NamespaceSummary namespaces,
        BytecodeReferenceInventory references
) {
    /** Backward-compatible constructor for callers that do not provide detailed references. */
    public ClassBytecodeScan(
            String entryName,
            String internalName,
            int minorVersion,
            int majorVersion,
            boolean preview,
            int accessFlags,
            BytecodeFeatureCounts features,
            NamespaceSummary namespaces
    ) {
        this(
                entryName,
                internalName,
                minorVersion,
                majorVersion,
                preview,
                accessFlags,
                features,
                namespaces,
                BytecodeReferenceInventory.EMPTY
        );
    }

    public ClassBytecodeScan {
        if (entryName == null || entryName.isBlank()) {
            throw new IllegalArgumentException("entryName must not be blank");
        }
        if (internalName == null || internalName.isBlank()) {
            throw new IllegalArgumentException("internalName must not be blank");
        }
        if (minorVersion < 0 || minorVersion > 0xFFFF) {
            throw new IllegalArgumentException("minorVersion must be an unsigned short");
        }
        if (majorVersion < 0 || majorVersion > 0xFFFF) {
            throw new IllegalArgumentException("majorVersion must be an unsigned short");
        }
        if (preview != (minorVersion == 0xFFFF)) {
            throw new IllegalArgumentException("preview must match the class-file minor version");
        }
        Objects.requireNonNull(features, "features");
        Objects.requireNonNull(namespaces, "namespaces");
        Objects.requireNonNull(references, "references");
    }
}
