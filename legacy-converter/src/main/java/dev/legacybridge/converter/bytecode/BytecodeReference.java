package dev.legacybridge.converter.bytecode;

import java.util.Objects;
import java.util.Set;

/**
 * One retained static reference sample in JVM naming form.
 *
 * <p>{@code owner} is an internal class name such as
 * {@code net/minecraft/world/World}. Member references also contain their
 * JVM member name and descriptor. Type and hierarchy references use an empty
 * member name; their descriptor contains the originating descriptor or
 * signature when one exists.</p>
 */
public record BytecodeReference(
        BytecodeReferenceKind kind,
        String owner,
        String memberName,
        String descriptor
) implements Comparable<BytecodeReference> {
    public static final int RETAINED_OWNER_CHARS = 1024;
    public static final int RETAINED_MEMBER_CHARS = 512;
    public static final int RETAINED_DESCRIPTOR_CHARS = 2048;

    public BytecodeReference {
        Objects.requireNonNull(kind, "kind");
        owner = requireNonBlank(owner, "owner");
        memberName = Objects.requireNonNull(memberName, "memberName");
        descriptor = Objects.requireNonNull(descriptor, "descriptor");

        boolean memberReference = kind == BytecodeReferenceKind.FIELD
                || kind == BytecodeReferenceKind.METHOD;
        if (memberReference && (memberName.isBlank() || descriptor.isBlank())) {
            throw new IllegalArgumentException("field and method references require a name and descriptor");
        }
        if (!memberReference && !memberName.isEmpty()) {
            throw new IllegalArgumentException("type and hierarchy references must not have a member name");
        }
    }

    public static BytecodeReference type(String owner, String sourceDescriptor) {
        return new BytecodeReference(BytecodeReferenceKind.TYPE, owner, "", sourceDescriptor);
    }

    public static BytecodeReference hierarchy(BytecodeReferenceKind kind, String owner) {
        if (kind != BytecodeReferenceKind.SUPERCLASS && kind != BytecodeReferenceKind.INTERFACE) {
            throw new IllegalArgumentException("hierarchy kind must be SUPERCLASS or INTERFACE");
        }
        return new BytecodeReference(kind, owner, "", "");
    }

    public static BytecodeReference member(
            BytecodeReferenceKind kind,
            String owner,
            String memberName,
            String descriptor
    ) {
        if (kind != BytecodeReferenceKind.FIELD && kind != BytecodeReferenceKind.METHOD) {
            throw new IllegalArgumentException("member kind must be FIELD or METHOD");
        }
        return new BytecodeReference(kind, owner, memberName, descriptor);
    }

    public boolean ownerIsDefinedBy(Set<String> internalDefinitions) {
        Objects.requireNonNull(internalDefinitions, "internalDefinitions");
        return internalDefinitions.contains(owner);
    }

    /** Returns a bounded representation suitable for deterministic report retention. */
    public BytecodeReference boundedForReport() {
        if (!requiresReportTruncation()) {
            return this;
        }
        return new BytecodeReference(
                kind,
                bound(owner, RETAINED_OWNER_CHARS),
                bound(memberName, RETAINED_MEMBER_CHARS),
                bound(descriptor, RETAINED_DESCRIPTOR_CHARS)
        );
    }

    public boolean requiresReportTruncation() {
        return owner.length() > RETAINED_OWNER_CHARS
                || memberName.length() > RETAINED_MEMBER_CHARS
                || descriptor.length() > RETAINED_DESCRIPTOR_CHARS;
    }

    @Override
    public int compareTo(BytecodeReference other) {
        int comparison = owner.compareTo(other.owner);
        if (comparison != 0) {
            return comparison;
        }
        comparison = kind.compareTo(other.kind);
        if (comparison != 0) {
            return comparison;
        }
        comparison = memberName.compareTo(other.memberName);
        if (comparison != 0) {
            return comparison;
        }
        return descriptor.compareTo(other.descriptor);
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String bound(String value, int limit) {
        if (value.length() <= limit) {
            return value;
        }
        String suffix = "...[length=" + value.length() + "]";
        int tailLength = Math.min(16, Math.max(0, limit - suffix.length() - 1));
        int prefixLength = limit - suffix.length() - tailLength;
        return value.substring(0, prefixLength)
                + suffix
                + value.substring(value.length() - tailLength);
    }
}
