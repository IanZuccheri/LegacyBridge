package dev.legacybridge.converter.metadata;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import dev.legacybridge.converter.policy.NumericVersionRange;
import dev.legacybridge.converter.report.DependencyDeclaration;
import dev.legacybridge.converter.report.Finding;
import dev.legacybridge.converter.report.MetadataSummary;
import dev.legacybridge.converter.report.ModDeclaration;
import dev.legacybridge.converter.report.Severity;

import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Parses and conservatively validates the declarative Forge metadata without initializing Forge. */
public final class ForgeMetadataAnalyzer {
    public static final String MODS_TOML = "META-INF/mods.toml";
    private static final int MAX_REPORTED_TEXT = 1_024;
    public static final String MOD_ID_PATTERN = "^[a-z][a-z0-9_]{1,63}$";
    private static final Pattern MOD_ID = Pattern.compile(MOD_ID_PATTERN);
    private static final Set<String> DEPENDENCY_ORDERING = Set.of("NONE", "BEFORE", "AFTER");
    private static final Set<String> DEPENDENCY_SIDE = Set.of("BOTH", "CLIENT", "SERVER");

    public MetadataAnalysis analyze(byte[] bytes) {
        if (bytes == null) {
            return new MetadataAnalysis(
                    MetadataSummary.missing(),
                    List.of(new Finding(
                            "LB-M001",
                            Severity.ERROR,
                            "metadata",
                            "META-INF/mods.toml is missing.",
                            List.of()
                    ))
            );
        }

        try {
            String source = decodeUtf8(bytes);
            TomlStructuralPreflight.validate(source);
            StringReader reader = new StringReader(source);
            Config config = new TomlParser().parse(reader);
            List<Finding> findings = new ArrayList<>();

            Object rawModLoader = config.get("modLoader");
            String modLoader = text(rawModLoader);
            validateModLoader(rawModLoader, modLoader, findings);

            Object rawLoaderVersion = config.get("loaderVersion");
            String loaderVersion = text(rawLoaderVersion);
            validateLoaderVersion(rawLoaderVersion, loaderVersion, findings);

            Set<String> declaredModIds = new HashSet<>();
            List<ModDeclaration> mods = parseMods(config.get("mods"), declaredModIds, findings);
            List<DependencyDeclaration> dependencies = parseDependencies(
                    config.get("dependencies"),
                    declaredModIds,
                    findings
            );

            return new MetadataAnalysis(
                    new MetadataSummary(true, true, modLoader, loaderVersion, mods, dependencies),
                    findings
            );
        } catch (Exception exception) {
            return new MetadataAnalysis(
                    MetadataSummary.malformed(),
                    List.of(new Finding(
                            "LB-M006",
                            Severity.ERROR,
                            "metadata",
                            "META-INF/mods.toml could not be parsed as TOML.",
                            List.of(MODS_TOML, exception.getClass().getSimpleName())
                    ))
            );
        } catch (StackOverflowError error) {
            return new MetadataAnalysis(
                    MetadataSummary.malformed(),
                    List.of(new Finding(
                            "LB-M006", Severity.ERROR, "metadata",
                            "META-INF/mods.toml exceeded parser structural limits.",
                            List.of(MODS_TOML, "StackOverflowError")
                    ))
            );
        }
    }

    private static void validateModLoader(Object raw, String value, List<Finding> findings) {
        if (!(raw instanceof String) || value.isBlank()) {
            findings.add(new Finding(
                    "LB-M002", Severity.ERROR, "metadata",
                    "mods.toml must declare modLoader as a non-empty string.", List.of(MODS_TOML)
            ));
        } else if (!"javafml".equals(value)) {
            findings.add(new Finding(
                    "LB-M003", Severity.BLOCKER, "metadata",
                    "Only the javafml language loader is a future LegacyBridge candidate.",
                    List.of(MODS_TOML + "#modLoader=" + value)
            ));
        }
    }

    private static void validateLoaderVersion(Object raw, String value, List<Finding> findings) {
        if (!(raw instanceof String) || value.isBlank()) {
            findings.add(new Finding(
                    "LB-M004", Severity.ERROR, "metadata",
                    "mods.toml must declare loaderVersion as a non-empty string.", List.of(MODS_TOML)
            ));
        } else if (!NumericVersionRange.isSupported(value)) {
            findings.add(new Finding(
                    "LB-M007", Severity.WARNING, "metadata",
                    "loaderVersion is valid-looking metadata but outside this analyzer's numeric inference grammar.",
                    List.of(MODS_TOML + "#loaderVersion")
            ));
        }
    }

    private static List<ModDeclaration> parseMods(
            Object rawMods,
            Set<String> declaredModIds,
            List<Finding> findings
    ) {
        if (!(rawMods instanceof List<?> values)) {
            findings.add(new Finding(
                    "LB-M005", Severity.ERROR, "metadata",
                    "mods.toml contains no valid [[mods]] declaration list.", List.of(MODS_TOML)
            ));
            return List.of();
        }

        List<ModDeclaration> mods = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            Object value = values.get(index);
            String location = MODS_TOML + "#mods[" + index + "]";
            if (!(value instanceof UnmodifiableConfig mod)) {
                findings.add(new Finding(
                        "LB-M008", Severity.ERROR, "metadata",
                        "Every [[mods]] element must be a TOML table.", List.of(location)
                ));
                continue;
            }

            Object rawModId = mod.get("modId");
            String modId = text(rawModId);
            if (!(rawModId instanceof String) || !isValidModId(modId)) {
                findings.add(new Finding(
                        "LB-M009", Severity.ERROR, "metadata",
                        "Each [[mods]] declaration needs a modId matching " + MOD_ID_PATTERN + ".",
                        List.of(location + "#modId")
                ));
            } else if (!declaredModIds.add(modId)) {
                findings.add(new Finding(
                        "LB-M010", Severity.ERROR, "metadata",
                        "Duplicate [[mods]] modId declarations are not valid input.",
                        List.of(location + "#modId=" + modId)
                ));
            }

            String version = mod.valueMap().containsKey("version")
                    ? text(mod.get("version"))
                    : "1";
            mods.add(new ModDeclaration(
                    modId,
                    version,
                    text(mod.get("displayName"))
            ));
        }
        if (mods.isEmpty()) {
            findings.add(new Finding(
                    "LB-M005", Severity.ERROR, "metadata",
                    "mods.toml contains no [[mods]] declarations.", List.of(MODS_TOML)
            ));
        }
        return mods;
    }

    private static List<DependencyDeclaration> parseDependencies(
            Object rawDependencies,
            Set<String> declaredModIds,
            List<Finding> findings
    ) {
        if (rawDependencies == null) {
            return List.of();
        }
        if (!(rawDependencies instanceof UnmodifiableConfig dependencyTables)) {
            findings.add(new Finding(
                    "LB-M011", Severity.ERROR, "metadata",
                    "dependencies must be TOML tables keyed by their owning modId.",
                    List.of(MODS_TOML + "#dependencies")
            ));
            return List.of();
        }

        List<DependencyDeclaration> dependencies = new ArrayList<>();
        for (Map.Entry<String, Object> ownerEntry : dependencyTables.valueMap().entrySet()) {
            String ownerModId = text(ownerEntry.getKey());
            String ownerLocation = MODS_TOML + "#dependencies." + ownerModId;
            boolean validOwner = isValidModId(ownerModId);
            if (!validOwner) {
                findings.add(new Finding(
                        "LB-M012", Severity.ERROR, "metadata",
                        "Each dependency group needs an owner modId matching " + MOD_ID_PATTERN + ".",
                        List.of(ownerLocation)
                ));
            } else if (!declaredModIds.contains(ownerModId)) {
                findings.add(new Finding(
                        "LB-M013", Severity.ERROR, "metadata",
                        "A dependency group owner must name a declared [[mods]] modId.",
                        List.of(ownerLocation)
                ));
            }

            if (!(ownerEntry.getValue() instanceof List<?> declarations)) {
                findings.add(new Finding(
                        "LB-M011", Severity.ERROR, "metadata",
                        "Each dependency group must contain [[dependencies.<modId>]] tables.",
                        List.of(ownerLocation)
                ));
                continue;
            }
            for (int index = 0; index < declarations.size(); index++) {
                Object value = declarations.get(index);
                String location = ownerLocation + "[" + index + "]";
                if (!(value instanceof UnmodifiableConfig dependency)) {
                    findings.add(new Finding(
                            "LB-M011", Severity.ERROR, "metadata",
                            "Every dependency declaration must be a TOML table.", List.of(location)
                    ));
                    continue;
                }

                Object rawModId = dependency.get("modId");
                String modId = text(rawModId);
                if (!(rawModId instanceof String) || !isValidModId(modId)) {
                    findings.add(new Finding(
                            "LB-M014", Severity.ERROR, "metadata",
                            "Each dependency needs a modId matching " + MOD_ID_PATTERN + ".",
                            List.of(location + "#modId")
                    ));
                }

                boolean mandatoryDeclared = dependency.valueMap().containsKey("mandatory");
                Object rawMandatory = dependency.get("mandatory");
                boolean mandatory = rawMandatory instanceof Boolean booleanValue && booleanValue;
                if (!mandatoryDeclared || !(rawMandatory instanceof Boolean)) {
                    findings.add(new Finding(
                            "LB-M015", Severity.ERROR, "metadata",
                            "Each dependency must explicitly declare mandatory as a boolean.",
                            List.of(location + "#mandatory")
                    ));
                }

                Object rawVersionRange = dependency.get("versionRange");
                String versionRange = text(rawVersionRange);
                if (rawVersionRange != null
                        && (!(rawVersionRange instanceof String) || versionRange.isBlank())) {
                    findings.add(new Finding(
                            "LB-M016", Severity.ERROR, "metadata",
                            "Dependency versionRange must be a non-empty string when declared.",
                            List.of(location + "#versionRange")
                    ));
                } else if (rawVersionRange != null && !NumericVersionRange.isSupported(versionRange)) {
                    findings.add(new Finding(
                            "LB-M019", Severity.WARNING, "metadata",
                            "Dependency versionRange is outside this analyzer's numeric inference grammar.",
                            List.of(location + "#versionRange")
                    ));
                }

                Object rawOrdering = dependency.get("ordering");
                String ordering = rawOrdering == null ? "NONE" : text(rawOrdering);
                if (rawOrdering != null
                        && (!(rawOrdering instanceof String) || !DEPENDENCY_ORDERING.contains(ordering))) {
                    findings.add(new Finding(
                            "LB-M017", Severity.ERROR, "metadata",
                            "Dependency ordering must be NONE, BEFORE, or AFTER when declared.",
                            List.of(location + "#ordering")
                    ));
                }

                Object rawSide = dependency.get("side");
                String side = rawSide == null ? "BOTH" : text(rawSide);
                if (rawSide != null && (!(rawSide instanceof String) || !DEPENDENCY_SIDE.contains(side))) {
                    findings.add(new Finding(
                            "LB-M018", Severity.ERROR, "metadata",
                            "Dependency side must be BOTH, CLIENT, or SERVER when declared.",
                            List.of(location + "#side")
                    ));
                }

                dependencies.add(new DependencyDeclaration(
                        ownerModId,
                        modId,
                        mandatoryDeclared,
                        mandatory,
                        versionRange,
                        ordering,
                        side
                ));
            }
        }
        return dependencies;
    }

    /** Forge 1.16.x mod identifiers are lowercase, 2-64 characters, without hyphens. */
    public static boolean isValidModId(String value) {
        return value != null && MOD_ID.matcher(value).matches();
    }

    private static String text(Object value) {
        if (value == null) {
            return "";
        }
        String string = String.valueOf(value);
        if (string.length() <= MAX_REPORTED_TEXT) {
            return string;
        }
        return string.substring(0, MAX_REPORTED_TEXT) + "...";
    }

    private static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }
}
