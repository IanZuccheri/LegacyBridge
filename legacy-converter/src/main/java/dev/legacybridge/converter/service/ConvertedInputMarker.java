package dev.legacybridge.converter.service;

import dev.legacybridge.converter.archive.JarEntryVisitor;
import dev.legacybridge.converter.archive.SafeJarReader;
import dev.legacybridge.converter.archive.InputRejectedException;
import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.converter.report.CompatibilityReport;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.Manifest;

/** Correlated inert product markers, not authentication or a runnable-bytecode guarantee. */
final class ConvertedInputMarker {
    private ConvertedInputMarker() { }

    static boolean detect(Path input, CompatibilityReport report) throws IOException, InputRejectedException {
        if (report.metadata().mods().size() != 1 || !report.metadata().modLoader().equals("javafml")
                || !report.metadata().dependencies().stream().anyMatch(dep -> dep.modId().equals("legacybridge_runtime")
                        && dep.mandatory())) return false;
        String id = report.metadata().mods().get(0).modId();
        String content = "META-INF/legacybridge/" + id + "/content.json";
        String adapters = "META-INF/legacybridge/" + id + "/adapters.json";
        Set<String> paths = Set.of("META-INF/MANIFEST.MF", content, adapters);
        Map<String, byte[]> entries = new HashMap<>();
        var scan = new SafeJarReader().scan(input, new JarEntryVisitor() {
            @Override public boolean requiresBytes(String name) { return paths.contains(name); }
            @Override public long maxCapturedBytes(String name) { return 1_048_576; }
            @Override public void visit(String name, byte[] bytes) { entries.put(name, bytes); }
        });
        if (!scan.input().sha256().equals(report.input().sha256())) throw new IOException("Input changed while identifying converted markers");
        if (!entries.keySet().containsAll(paths)) return false;
        var attributes = new Manifest(new ByteArrayInputStream(entries.get("META-INF/MANIFEST.MF"))).getMainAttributes();
        if (!"simple-content-v1".equals(attributes.getValue("LegacyBridge-Profile"))
                || !"1".equals(attributes.getValue("LegacyBridge-Adapter-Catalog"))) return false;
        Object plan;
        Object bindings;
        try {
            plan = BoundedJsonParser.parse(decode(entries.get(content)));
            bindings = BoundedJsonParser.parse(decode(entries.get(adapters)));
        } catch (BoundedJsonParser.JsonSyntaxException invalid) { throw new IOException("Malformed converted-product markers", invalid); }
        return plan instanceof Map<?, ?> root && id.equals(root.get("modId"))
                && new java.math.BigDecimal("1").equals(root.get("schemaVersion"))
                && bindings instanceof Map<?, ?> bound && !bound.isEmpty();
    }

    private static String decode(byte[] bytes) throws IOException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
    }
}
