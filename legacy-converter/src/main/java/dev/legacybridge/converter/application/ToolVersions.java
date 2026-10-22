package dev.legacybridge.converter.application;

final class ToolVersions {
    private ToolVersions() {
    }

    static String current() {
        String version = ToolVersions.class.getPackage().getImplementationVersion();
        return version == null || version.isBlank() ? "development" : version;
    }
}

