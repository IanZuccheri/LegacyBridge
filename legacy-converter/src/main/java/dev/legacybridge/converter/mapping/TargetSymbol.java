package dev.legacybridge.converter.mapping;

/** A symbol in the declared target namespace; names and descriptors use JVM notation. */
public record TargetSymbol(SymbolKind kind, String owner, String name, String descriptor)
        implements Comparable<TargetSymbol> {
    public TargetSymbol {
        SymbolValidation.validate(kind, owner, name, descriptor);
    }

    public static TargetSymbol classSymbol(String owner) {
        return new TargetSymbol(SymbolKind.CLASS, owner, "", "");
    }

    public static TargetSymbol method(String owner, String name, String descriptor) {
        return new TargetSymbol(SymbolKind.METHOD, owner, name, descriptor);
    }

    public static TargetSymbol field(String owner, String name, String descriptor) {
        return new TargetSymbol(SymbolKind.FIELD, owner, name, descriptor);
    }

    public static TargetSymbol constructor(String owner, String descriptor) {
        return new TargetSymbol(SymbolKind.CONSTRUCTOR, owner, "<init>", descriptor);
    }

    public static TargetSymbol descriptor(String descriptor) {
        return new TargetSymbol(SymbolKind.DESCRIPTOR, "", "", descriptor);
    }

    public String stableKey() {
        return kind.name() + "\u0000" + owner + "\u0000" + name + "\u0000" + descriptor;
    }

    @Override
    public int compareTo(TargetSymbol other) {
        return stableKey().compareTo(other.stableKey());
    }
}
