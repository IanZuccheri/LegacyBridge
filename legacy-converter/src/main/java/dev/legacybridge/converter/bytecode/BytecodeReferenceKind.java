package dev.legacybridge.converter.bytecode;

/**
 * Stable semantic kinds used by the static bytecode reference inventory.
 *
 * <p>The enum deliberately describes what is referenced rather than the ASM
 * callback that exposed it. References originating in descriptors,
 * signatures, annotations, frames, constants and type instructions are all
 * represented as {@link #TYPE}.</p>
 */
public enum BytecodeReferenceKind {
    SUPERCLASS,
    INTERFACE,
    FIELD,
    METHOD,
    TYPE
}
