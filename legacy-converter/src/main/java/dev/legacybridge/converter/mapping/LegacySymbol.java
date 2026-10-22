package dev.legacybridge.converter.mapping;

/** A symbol in the declared source namespace; names and descriptors use JVM notation. */
public record LegacySymbol(SymbolKind kind, String owner, String name, String descriptor)
        implements Comparable<LegacySymbol> {
    public LegacySymbol {
        SymbolValidation.validate(kind, owner, name, descriptor);
    }

    public static LegacySymbol classSymbol(String owner) {
        return new LegacySymbol(SymbolKind.CLASS, owner, "", "");
    }

    public static LegacySymbol method(String owner, String name, String descriptor) {
        return new LegacySymbol(SymbolKind.METHOD, owner, name, descriptor);
    }

    public static LegacySymbol field(String owner, String name, String descriptor) {
        return new LegacySymbol(SymbolKind.FIELD, owner, name, descriptor);
    }

    public static LegacySymbol constructor(String owner, String descriptor) {
        return new LegacySymbol(SymbolKind.CONSTRUCTOR, owner, "<init>", descriptor);
    }

    public static LegacySymbol descriptor(String descriptor) {
        return new LegacySymbol(SymbolKind.DESCRIPTOR, "", "", descriptor);
    }

    public String stableKey() {
        return kind.name() + "\u0000" + owner + "\u0000" + name + "\u0000" + descriptor;
    }

    @Override
    public int compareTo(LegacySymbol other) {
        return stableKey().compareTo(other.stableKey());
    }
}
