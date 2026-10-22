package dev.legacybridge.converter.cli;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Set;
import dev.legacybridge.converter.service.ConversionProfile;

record CliArguments(String command, Path input, String reportTarget, boolean overwrite,
                    Path mappingProfile, Path mappingBundle, Path output, boolean fromDevelopmentCache,
                    ConversionProfile conversionProfile, String mappingSha256) {
    static CliArguments parse(String[] arguments) {
        if (arguments.length == 0 || !Set.of("analyze", "mappings", "convert", "prepare-mappings").contains(arguments[0])) {
            throw new IllegalArgumentException("Expected analyze, mappings, convert or prepare-mappings");
        }
        String command = arguments[0];
        boolean preparation = "prepare-mappings".equals(command);
        if (!preparation && (arguments.length < 2 || arguments[1].isBlank() || arguments[1].startsWith("--"))) {
            throw new IllegalArgumentException(command + " requires an input JAR");
        }
        Path input = preparation ? null : path(arguments[1], "Input");
        String report = "-";
        boolean reportSpecified = false;
        boolean overwrite = false;
        Path mappingProfile = null;
        Path mappingBundle = null;
        String mappingSha256 = null;
        Path output = null;
        boolean fromDevelopmentCache = false;
        ConversionProfile conversionProfile = ConversionProfile.SIMPLE_CONTENT_V1;
        boolean conversionProfileSpecified = false;
        for (int index = preparation ? 1 : 2; index < arguments.length; index++) {
            String argument = arguments[index];
            if ("--report".equals(argument) && !preparation) {
                if (reportSpecified) throw new IllegalArgumentException("--report must be specified exactly once with a value");
                report = value(arguments, ++index, argument);
                reportSpecified = true;
            } else if ("--overwrite".equals(argument) && !preparation) {
                if (overwrite) throw new IllegalArgumentException("--overwrite must be specified at most once");
                overwrite = true;
            } else if ("--mapping-profile".equals(argument) && !"analyze".equals(command)) {
                if (mappingProfile != null) throw new IllegalArgumentException("--mapping-profile must be specified at most once");
                mappingProfile = path(value(arguments, ++index, argument), "Mapping profile");
            } else if ("--mapping-bundle".equals(argument) && Set.of("mappings", "convert").contains(command)) {
                if (mappingBundle != null) throw new IllegalArgumentException("--mapping-bundle must be specified at most once");
                mappingBundle = path(value(arguments, ++index, argument), "Mapping bundle");
            } else if ("--mapping-sha256".equals(argument) && Set.of("mappings", "convert").contains(command)) {
                if (mappingSha256 != null) throw new IllegalArgumentException("--mapping-sha256 must be specified at most once");
                mappingSha256 = value(arguments, ++index, argument);
                if (!mappingSha256.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("--mapping-sha256 requires lowercase SHA-256 from inspect-data");
            } else if ("--output".equals(argument) && ("convert".equals(command) || preparation)) {
                if (output != null) throw new IllegalArgumentException("--output must be specified at most once");
                output = path(value(arguments, ++index, argument), "Output");
            } else if ("--conversion-profile".equals(argument) && "convert".equals(command)) {
                if (conversionProfileSpecified) throw new IllegalArgumentException("--conversion-profile must be specified at most once");
                conversionProfile = ConversionProfile.parse(value(arguments, ++index, argument));
                conversionProfileSpecified = true;
            } else if ("--from-development-cache".equals(argument) && preparation) {
                if (fromDevelopmentCache) throw new IllegalArgumentException("--from-development-cache must be specified at most once");
                fromDevelopmentCache = true;
            } else {
                throw new IllegalArgumentException("Unknown option: " + argument);
            }
        }
        if (mappingProfile != null && mappingBundle != null) {
            throw new IllegalArgumentException("--mapping-profile and --mapping-bundle are mutually exclusive");
        }
        if (mappingSha256 != null && mappingBundle == null) throw new IllegalArgumentException("--mapping-sha256 requires explicit --mapping-bundle, not environment or profile selection");
        if ("-".equals(report) && overwrite) {
            throw new IllegalArgumentException("--overwrite cannot be used when the report target is stdout");
        }
        if (!"-".equals(report)) path(report, "Report");
        if (("convert".equals(command) || preparation) && output == null) {
            throw new IllegalArgumentException(command + " requires --output <" + (preparation ? "new-directory" : "converted.jar") + ">");
        }
        if (preparation && ((mappingProfile != null) == fromDevelopmentCache)) {
            throw new IllegalArgumentException("prepare-mappings requires exactly one of --mapping-profile <file> or --from-development-cache");
        }
        return new CliArguments(command, input, report, overwrite, mappingProfile, mappingBundle, output, fromDevelopmentCache, conversionProfile, mappingSha256);
    }

    private static String value(String[] arguments, int index, String option) {
        if (index >= arguments.length || arguments[index].isBlank() || arguments[index].startsWith("--")) {
            throw new IllegalArgumentException(option + " requires exactly one non-blank value");
        }
        return arguments[index];
    }

    private static Path path(String value, String label) {
        try {
            return Path.of(value);
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException(label + " path is invalid", exception);
        }
    }

    boolean reportToStdout() { return "-".equals(reportTarget); }
    Path reportPath() { return Path.of(reportTarget); }
}
