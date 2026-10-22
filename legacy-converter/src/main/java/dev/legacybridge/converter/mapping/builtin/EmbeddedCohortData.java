package dev.legacybridge.converter.mapping.builtin;

import dev.legacybridge.converter.mapping.source.*;
import dev.legacybridge.converter.operation.OperationControl;
import net.minecraftforge.srgutils.IMappingBuilder;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.*;
import java.util.*;

/**
 * One public Forge annotation declaration on each side, no name mappings or Minecraft API.
 * The source inspector additionally requires the actual archive hash to belong to the
 * existing finite recipe before scanning classes: a pruned inventory cannot prove general
 * platform ownership. JDK declarations still come from the running Java17 platform, not data.
 */
public final class EmbeddedCohortData {
    public static final String RESOURCE_PATH = "META-INF/legacybridge/cohort-preview-v1/platform-contract-v1.tsv";
    public static final String DATA_SHA256 = "20fda70724153dee806f1fa20812a4cc8713da440d58692255d8597d1503eafe";
    public static final String MOD = "net/minecraftforge/fml/common/Mod";
    public static final int SCHEMA_VERSION = 1, REVISION = 1;
    static final int MAX_BYTES = 2048;
    private EmbeddedCohortData() { }
    public static MappingInputs load() throws IOException {
        try (var stream = EmbeddedCohortData.class.getResourceAsStream("/" + RESOURCE_PATH)) {
            if (stream == null) throw new IOException("Included cohort contract is missing; reinstall the matching LegacyBridge JAR. No fallback.");
            return load(stream);
        }
    }
    /** Test seam still enforces the production digest and bounds. No unpinned import format. */
    static MappingInputs load(InputStream stream) throws IOException {
        if (Runtime.version().feature() != 17) throw new IOException("Finite cohort declarations require the reviewed Java17 platform");
        var captured = new ByteArrayOutputStream(); byte[] buffer = new byte[256];
        for (int count; (count = stream.read(buffer)) != -1;) {
            OperationControl.checkpoint();
            if (captured.size() + count > MAX_BYTES) throw new IOException("Included cohort contract exceeds 2 KiB");
            captured.write(buffer, 0, count);
        }
        byte[] bytes = captured.toByteArray();
        String digest;
        try { digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        if (!digest.equals(DATA_SHA256)) throw new IOException("Included cohort contract SHA-256 mismatch; no fallback permitted");
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        String[] lines = text.split("\n", -1);
        if (lines.length != 8 || !lines[0].equals("LEGACYBRIDGE_COHORT_PLATFORM_CONTRACT\t1\t1.16.5\t36.2.42\t1.20.1\t47.4.23\t17\t1")
                || !lines[7].isEmpty()) throw new IOException("Unsupported cohort contract schema/baselines");
        var sources = new ArrayList<MappingSource>();
        for (int i = 1; i <= 4; i++) {
            String[] row = lines[i].split("\t", -1);
            if (row.length != 3 || !row[0].equals("S") || !row[2].matches("[0-9a-f]{64}")) throw new IOException("Invalid cohort provenance");
            sources.add(new MappingSource(row[1], row[2], "DEVELOPMENT_VERIFICATION_ONLY_NOT_EMBEDDED"));
        }
        String[] type = lines[5].split("\t", -1), method = lines[6].split("\t", -1);
        if (type.length != 5 || !type[0].equals("C") || !type[1].equals(MOD) || method.length != 4 || !method[0].equals("M"))
            throw new IOException("Unsupported finite declaration shape");
        ApiIndex api;
        try {
            api = new ApiIndex(Map.of(type[1], new ApiIndex.ApiClass(type[1], Integer.parseInt(type[2]), type[3], List.of(type[4]),
                    List.of(new ApiIndex.ApiMember(method[1], method[2], Integer.parseInt(method[3]))), List.of())));
        } catch (IllegalArgumentException invalid) { throw new IOException("Invalid finite declaration", invalid); }
        sources.add(new MappingSource("embedded.cohort-preview-v1.platform-contract-v1", digest, "EMBEDDED_FINITE_COHORT_CONTRACT"));
        var empty = IMappingBuilder.create("source", "target").build().getMap("source", "target");
        return new MappingInputs(empty, empty, empty, empty, api, api, sources,
                List.of("FINITE_COHORT_CONTRACT: exact authored source archives only; one Forge annotation, no complete API or name mapping inventory",
                        "JDK17 declarations are read from the current Java platform, not embedded; not Java8/native behavior proof"),
                false, MappingInputs.Scope.FINITE_COHORT_CONTRACT);
    }
}
