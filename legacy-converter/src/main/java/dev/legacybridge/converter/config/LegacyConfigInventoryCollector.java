package dev.legacybridge.converter.config;

import dev.legacybridge.converter.archive.ArchiveEntryClassifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Collects inert facts from known Forge/Mixin configuration files. This class never resolves
 * class names, loads classes, follows declared paths, or executes coremod scripts.
 */
public final class LegacyConfigInventoryCollector {
    public static final int MAX_ARTIFACT_BYTES = 1024 * 1024;
    public static final int MAX_DECLARATION_SAMPLES = 256;
    public static final int MAX_ACCESS_TRANSFORMER_SAMPLES = 64;
    public static final int MAX_RETAINED_VALUE_CHARS = 4096;

    private static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";
    private static final String ACCESS_TRANSFORMER_PATH = "META-INF/accesstransformer.cfg";
    private static final String COREMODS_PATH = "META-INF/coremods.json";
    private static final Object JSON_NULL = new Object();
    private static final List<String> MANIFEST_ALLOWLIST = List.of(
            "Automatic-Module-Name",
            "Class-Path",
            "ContainedDeps",
            "FMLCorePlugin",
            "FMLCorePluginContainsFMLMod",
            "ForceLoadAsMod",
            "Implementation-Title",
            "Implementation-Version",
            "MixinConfigs",
            "TweakClass"
    );

    private final Map<String, MixinConfigurationSummary> mixins = new TreeMap<>();
    private final Map<String, AccessTransformerSummary> accessTransformers = new TreeMap<>();
    private final Map<String, CoremodConfigurationSummary> coremods = new TreeMap<>();
    private final List<ArtifactDiagnostic> diagnostics = new ArrayList<>();
    private ManifestAttributesSummary manifest = ManifestAttributesSummary.absent();

    /** Returns whether this collector needs the bytes of an entry. */
    public boolean requiresBytes(String entryName) {
        Objects.requireNonNull(entryName, "entryName");
        return entryName.equals(MANIFEST_PATH)
                || entryName.equals(ACCESS_TRANSFORMER_PATH)
                || entryName.equals(COREMODS_PATH)
                || isMixinConfiguration(entryName);
    }

    /** Accepts a single archive entry; unrelated entries are ignored. */
    public void accept(String entryName, byte[] contents) {
        Objects.requireNonNull(entryName, "entryName");
        Objects.requireNonNull(contents, "contents");
        if (entryName.isBlank()) {
            throw new IllegalArgumentException("entryName must not be blank");
        }
        if (entryName.equals(MANIFEST_PATH)) {
            manifest = parseManifest(entryName, contents);
        } else if (entryName.equals(ACCESS_TRANSFORMER_PATH)) {
            accessTransformers.put(entryName, parseAccessTransformer(entryName, contents));
        } else if (entryName.equals(COREMODS_PATH)) {
            coremods.put(entryName, parseCoremods(entryName, contents));
        } else if (isMixinConfiguration(entryName)) {
            mixins.put(entryName, parseMixin(entryName, contents));
        }
    }

    /**
     * Accepts a Mixin configuration whose non-standard path was obtained from MixinConfigs.
     * This explicit method lets a two-pass archive reader handle arbitrary declared names.
     */
    public void acceptDeclaredMixinConfiguration(String entryName, byte[] contents) {
        Objects.requireNonNull(entryName, "entryName");
        Objects.requireNonNull(contents, "contents");
        if (entryName.isBlank()) {
            throw new IllegalArgumentException("entryName must not be blank");
        }
        mixins.put(entryName, parseMixin(entryName, contents));
    }

    /** Records that a manifest-declared config had no matching safe archive entry. */
    public void recordMissingDeclaredMixinConfiguration(String entryName) {
        Objects.requireNonNull(entryName, "entryName");
        if (entryName.isBlank()) {
            throw new IllegalArgumentException("entryName must not be blank");
        }
        diagnostic(
                "LB-CFG107",
                ArtifactDiagnosticSeverity.ERROR,
                entryName,
                "Manifest-declared Mixin configuration is absent from the archive."
        );
    }

    public LegacyConfigInventory snapshot() {
        List<ArtifactDiagnostic> sortedDiagnostics = diagnostics.stream().sorted().toList();
        return new LegacyConfigInventory(
                List.copyOf(mixins.values()),
                List.copyOf(accessTransformers.values()),
                List.copyOf(coremods.values()),
                manifest,
                sortedDiagnostics
        );
    }

    /** Recognizes the two common Mixin conventions plus the singular .mixin.json convention. */
    public static boolean isMixinConfiguration(String entryName) {
        Objects.requireNonNull(entryName, "entryName");
        return ArchiveEntryClassifier.isMixinConfiguration(entryName);
    }

    private MixinConfigurationSummary parseMixin(String path, byte[] bytes) {
        Object root = parseJson(path, bytes, "LB-CFG101", "Mixin configuration");
        if (root == null) {
            return MixinConfigurationSummary.unreadable(path);
        }
        if (!(root instanceof Map<?, ?> rawMap)) {
            diagnostic("LB-CFG102", ArtifactDiagnosticSeverity.ERROR, path,
                    "Mixin configuration root must be a JSON object.");
            return MixinConfigurationSummary.unreadable(path);
        }
        Map<String, Object> values = stringKeyedMap(rawMap);
        ParseState state = new ParseState();
        String mixinPackage = optionalString(values, "package", path, "LB-CFG103", state);
        String plugin = optionalString(values, "plugin", path, "LB-CFG103", state);
        String compatibilityLevel = optionalString(values, "compatibilityLevel", path, "LB-CFG103", state);
        Boolean required = optionalBoolean(values, "required", path, state);
        StringList mixinsList = stringList(values, "mixins", path, state);
        StringList clientList = stringList(values, "client", path, state);
        StringList serverList = stringList(values, "server", path, state);
        boolean truncated = mixinsList.truncated() || clientList.truncated() || serverList.truncated();
        if (truncated) {
            diagnostic("LB-CFG105", ArtifactDiagnosticSeverity.WARNING, path,
                    "Mixin declaration samples were truncated by policy.");
        }
        if (state.valueTruncated) {
            diagnostic("LB-CFG106", ArtifactDiagnosticSeverity.WARNING, path,
                    "One or more Mixin string values were truncated by policy.");
        }
        return new MixinConfigurationSummary(
                path, true, mixinPackage, plugin, required, compatibilityLevel,
                mixinsList.declaredCount(), mixinsList.values(),
                clientList.declaredCount(), clientList.values(),
                serverList.declaredCount(), serverList.values(),
                truncated, state.partial
        );
    }

    private AccessTransformerSummary parseAccessTransformer(String path, byte[] bytes) {
        String text = decodeText(path, bytes, "LB-CFG001", "LB-CFG002");
        if (text == null) {
            return AccessTransformerSummary.unreadable(path);
        }
        if (!text.isEmpty() && text.charAt(0) == '\ufeff') {
            text = text.substring(1);
        }
        AccessRuleAccumulator rules = new AccessRuleAccumulator();
        text.lines().forEach(line -> {
            int comment = line.indexOf('#');
            String withoutComment = comment >= 0 ? line.substring(0, comment) : line;
            String normalized = normalizeWhitespace(withoutComment);
            if (normalized.isEmpty()) {
                return;
            }
            rules.record(normalized);
        });
        boolean truncated = rules.truncated;
        if (truncated) {
            diagnostic("LB-CFG003", ArtifactDiagnosticSeverity.WARNING, path,
                    "Access-transformer rule samples were truncated by policy.");
        }
        return new AccessTransformerSummary(
                path,
                true,
                rules.ruleCount,
                List.copyOf(rules.samples),
                truncated
        );
    }

    private CoremodConfigurationSummary parseCoremods(String path, byte[] bytes) {
        Object root = parseJson(path, bytes, "LB-CFG201", "Coremod configuration");
        if (root == null) {
            return CoremodConfigurationSummary.unreadable(path);
        }
        if (!(root instanceof Map<?, ?> rawMap)) {
            diagnostic("LB-CFG202", ArtifactDiagnosticSeverity.ERROR, path,
                    "Coremod configuration root must be a JSON object.");
            return CoremodConfigurationSummary.unreadable(path);
        }
        Map<String, Object> values = stringKeyedMap(rawMap);
        boolean partial = false;
        boolean valueTruncated = false;
        List<CoremodDeclaration> declarations = new ArrayList<>();
        for (Map.Entry<String, Object> entry : new TreeMap<>(values).entrySet()) {
            String name = entry.getKey().trim();
            if (name.isEmpty()) {
                partial = true;
                diagnostic("LB-CFG203", ArtifactDiagnosticSeverity.WARNING, path,
                        "A coremod declaration has an empty name.");
                continue;
            }
            String scriptPath = null;
            if (entry.getValue() instanceof String value && !value.isBlank()) {
                valueTruncated |= value.trim().length() > MAX_RETAINED_VALUE_CHARS;
                scriptPath = limitValue(value.trim());
            } else {
                partial = true;
                diagnostic("LB-CFG204", ArtifactDiagnosticSeverity.WARNING, path,
                        "A coremod declaration does not contain a non-empty script path string.");
            }
            if (declarations.size() < MAX_DECLARATION_SAMPLES) {
                valueTruncated |= name.length() > MAX_RETAINED_VALUE_CHARS;
                declarations.add(new CoremodDeclaration(limitValue(name), scriptPath));
            }
        }
        declarations.sort(CoremodDeclaration::compareTo);
        boolean truncated = values.size() > MAX_DECLARATION_SAMPLES || valueTruncated;
        if (truncated) {
            diagnostic("LB-CFG205", ArtifactDiagnosticSeverity.WARNING, path,
                    "Coremod declaration samples were truncated by policy.");
        }
        return new CoremodConfigurationSummary(
                path, true, values.size(), declarations, truncated, partial || valueTruncated
        );
    }

    private ManifestAttributesSummary parseManifest(String path, byte[] bytes) {
        if (!withinSizeLimit(path, bytes)) {
            return ManifestAttributesSummary.unreadable(path);
        }
        try {
            Attributes main = new Manifest(new ByteArrayInputStream(bytes)).getMainAttributes();
            Map<String, String> retained = new TreeMap<>();
            boolean truncated = false;
            for (String attribute : MANIFEST_ALLOWLIST) {
                String value = main.getValue(attribute);
                if (value == null || value.isBlank()) {
                    continue;
                }
                String normalized = value.trim();
                if (normalized.length() > MAX_RETAINED_VALUE_CHARS) {
                    normalized = normalized.substring(0, MAX_RETAINED_VALUE_CHARS);
                    truncated = true;
                }
                retained.put(attribute, normalized);
            }
            String declaredMixins = retained.get("MixinConfigs");
            if (declaredMixins != null
                    && distinctCommaSeparatedValues(declaredMixins) > MAX_DECLARATION_SAMPLES) {
                truncated = true;
                diagnostic("LB-CFG303", ArtifactDiagnosticSeverity.ERROR, path,
                        "Manifest declares more Mixin configurations than the analysis policy allows.");
            }
            if (truncated) {
                diagnostic("LB-CFG302", ArtifactDiagnosticSeverity.WARNING, path,
                        "One or more allowlisted manifest values were truncated by policy.");
            }
            return new ManifestAttributesSummary(path, true, true, retained, truncated);
        } catch (IOException | RuntimeException exception) {
            diagnostic("LB-CFG301", ArtifactDiagnosticSeverity.ERROR, path,
                    "Manifest main attributes could not be parsed.");
            return ManifestAttributesSummary.unreadable(path);
        }
    }

    private Object parseJson(String path, byte[] bytes, String syntaxCode, String label) {
        String text = decodeText(path, bytes, "LB-CFG001", "LB-CFG002");
        if (text == null) {
            return null;
        }
        if (!text.isEmpty() && text.charAt(0) == '\ufeff') {
            text = text.substring(1);
        }
        try {
            Object parsed = BoundedJsonParser.parse(text);
            return parsed == null ? JSON_NULL : parsed;
        } catch (BoundedJsonParser.JsonSyntaxException exception) {
            diagnostic(syntaxCode, ArtifactDiagnosticSeverity.ERROR, path,
                    label + " is malformed or exceeds JSON parsing limits: " + exception.getMessage());
            return null;
        }
    }

    private String decodeText(String path, byte[] bytes, String sizeCode, String encodingCode) {
        if (!withinSizeLimit(path, bytes, sizeCode)) {
            return null;
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            diagnostic(encodingCode, ArtifactDiagnosticSeverity.ERROR, path,
                    "Configuration artifact is not valid UTF-8.");
            return null;
        }
    }

    private boolean withinSizeLimit(String path, byte[] bytes) {
        return withinSizeLimit(path, bytes, "LB-CFG001");
    }

    private boolean withinSizeLimit(String path, byte[] bytes, String code) {
        if (bytes.length <= MAX_ARTIFACT_BYTES) {
            return true;
        }
        diagnostic(code, ArtifactDiagnosticSeverity.ERROR, path,
                "Configuration artifact exceeds the one-megabyte parsing limit.");
        return false;
    }

    private String optionalString(
            Map<String, Object> values,
            String key,
            String path,
            String code,
            ParseState state
    ) {
        Object raw = values.get(key);
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof String value)) {
            state.partial = true;
            diagnostic(code, ArtifactDiagnosticSeverity.WARNING, path,
                    "Mixin field '" + key + "' is not a string.");
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            state.partial = true;
            diagnostic(code, ArtifactDiagnosticSeverity.WARNING, path,
                    "Mixin field '" + key + "' is empty.");
            return null;
        }
        if (normalized.length() > MAX_RETAINED_VALUE_CHARS) {
            state.partial = true;
            state.valueTruncated = true;
        }
        return limitValue(normalized);
    }

    private Boolean optionalBoolean(Map<String, Object> values, String key, String path, ParseState state) {
        Object raw = values.get(key);
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof Boolean value)) {
            state.partial = true;
            diagnostic("LB-CFG103", ArtifactDiagnosticSeverity.WARNING, path,
                    "Mixin field '" + key + "' is not a boolean.");
            return null;
        }
        return value;
    }

    private StringList stringList(Map<String, Object> values, String key, String path, ParseState state) {
        Object raw = values.get(key);
        if (raw == null) {
            return StringList.empty();
        }
        if (!(raw instanceof List<?> list)) {
            state.partial = true;
            diagnostic("LB-CFG103", ArtifactDiagnosticSeverity.WARNING, path,
                    "Mixin field '" + key + "' is not an array.");
            return StringList.empty();
        }
        TreeSet<String> valid = new TreeSet<>();
        boolean invalidMember = false;
        boolean valueTruncated = false;
        for (Object item : list) {
            if (item instanceof String value && !value.isBlank()) {
                String normalized = value.trim();
                valueTruncated |= normalized.length() > MAX_RETAINED_VALUE_CHARS;
                valid.add(limitValue(normalized));
            } else {
                invalidMember = true;
            }
        }
        if (invalidMember) {
            state.partial = true;
            diagnostic("LB-CFG104", ArtifactDiagnosticSeverity.WARNING, path,
                    "Mixin field '" + key + "' contains a non-string or empty declaration.");
        }
        if (valueTruncated) {
            state.partial = true;
            state.valueTruncated = true;
        }
        boolean truncated = valid.size() > MAX_DECLARATION_SAMPLES || valueTruncated;
        return new StringList(
                list.size(),
                valid.stream().limit(MAX_DECLARATION_SAMPLES).toList(),
                truncated
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stringKeyedMap(Map<?, ?> raw) {
        // BoundedJsonParser constructs objects with String keys only.
        return (Map<String, Object>) raw;
    }

    private void diagnostic(String code, ArtifactDiagnosticSeverity severity, String path, String message) {
        diagnostics.add(new ArtifactDiagnostic(code, severity, path, message));
    }

    private static String normalizeWhitespace(String value) {
        StringBuilder result = new StringBuilder(value.length());
        boolean pendingSpace = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isWhitespace(current)) {
                pendingSpace = result.length() > 0;
            } else {
                if (pendingSpace) {
                    result.append(' ');
                    pendingSpace = false;
                }
                result.append(current);
            }
        }
        return result.toString();
    }

    private static int distinctCommaSeparatedValues(String value) {
        TreeSet<String> declarations = new TreeSet<>();
        for (String item : value.split(",")) {
            String normalized = item.trim();
            if (!normalized.isEmpty()) {
                declarations.add(normalized);
            }
        }
        return declarations.size();
    }

    private static String limitValue(String value) {
        return value.length() <= MAX_RETAINED_VALUE_CHARS
                ? value
                : value.substring(0, MAX_RETAINED_VALUE_CHARS);
    }

    private static final class ParseState {
        private boolean partial;
        private boolean valueTruncated;
    }

    private static final class AccessRuleAccumulator {
        private int ruleCount;
        private final TreeSet<String> samples = new TreeSet<>();
        private boolean truncated;

        private void record(String normalized) {
            ruleCount = Math.incrementExact(ruleCount);
            truncated |= normalized.length() > MAX_RETAINED_VALUE_CHARS;
            if (samples.add(limitValue(normalized))
                    && samples.size() > MAX_ACCESS_TRANSFORMER_SAMPLES) {
                samples.pollLast();
                truncated = true;
            }
        }
    }

    private record StringList(int declaredCount, List<String> values, boolean truncated) {
        private StringList {
            values = List.copyOf(values);
        }

        static StringList empty() {
            return new StringList(0, List.of(), false);
        }
    }
}
