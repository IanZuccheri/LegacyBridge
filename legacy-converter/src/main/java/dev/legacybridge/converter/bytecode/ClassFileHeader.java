package dev.legacybridge.converter.bytecode;

/**
 * The fixed eight-byte prefix of a JVM class file. Keeping this fact separate
 * from the ASM parse lets the analyzer report a trustworthy target-JVM
 * incompatibility even when the remainder of the class is malformed or uses
 * structures the configured ASM version cannot parse.
 */
public record ClassFileHeader(int minorVersion, int majorVersion) {
    public ClassFileHeader {
        if (minorVersion < 0 || minorVersion > 0xFFFF) {
            throw new IllegalArgumentException("minorVersion must be an unsigned short");
        }
        if (majorVersion < 0 || majorVersion > 0xFFFF) {
            throw new IllegalArgumentException("majorVersion must be an unsigned short");
        }
    }

    public boolean preview() {
        return minorVersion == 0xFFFF;
    }
}
