package dev.legacybridge.converter.bytecode;

import java.util.Objects;

/** Bounded archive-level retention of one class' exact counters and detail samples. */
public record ClassReferenceDetail(
        String entryName,
        String internalName,
        BytecodeReferenceInventory references
) implements Comparable<ClassReferenceDetail> {
    public ClassReferenceDetail {
        entryName = requireNonBlank(entryName, "entryName");
        internalName = requireNonBlank(internalName, "internalName");
        Objects.requireNonNull(references, "references");
    }

    public static ClassReferenceDetail from(ClassBytecodeScan scan) {
        Objects.requireNonNull(scan, "scan");
        return new ClassReferenceDetail(scan.entryName(), scan.internalName(), scan.references());
    }

    @Override
    public int compareTo(ClassReferenceDetail other) {
        int comparison = entryName.compareTo(other.entryName);
        return comparison != 0 ? comparison : internalName.compareTo(other.internalName);
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
