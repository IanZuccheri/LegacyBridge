package dev.legacybridge.converter.mapping;

public enum MappingCategory {
    IDENTITY,
    PURE_RENAME,
    PACKAGE_MOVE,
    DESCRIPTOR_CHANGE,
    REMOVED_API,
    ADAPTER_REQUIRED,
    NO_AUTOMATIC_EQUIVALENT
}
