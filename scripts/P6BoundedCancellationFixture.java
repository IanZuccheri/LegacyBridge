import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Dev-only bounded resource-load fixture. Never defines or executes any original class. */
public final class P6BoundedCancellationFixture {
    private static final String SOURCE_SHA = "eb62c092c79887c3c5528e530c099bfeb569d01cfe22272257d583d6e4875e60";
    private static final List<String> LANGUAGES = List.of("de_de", "es_es", "fr_fr", "it_it",
            "nl_nl", "pl_pl", "sv_se", "fi_fi", "da_dk", "nb_no", "cs_cz", "sk_sk",
            "hu_hu", "ro_ro", "tr_tr", "el_gr", "uk_ua", "ru_ru", "ja_jp", "ko_kr",
            "zh_cn", "zh_tw", "en_gb", "pt_pt");

    public static void main(String[] args) throws Exception {
        require(Runtime.version().feature() == 17 && args.length == 1, "Java 17 and project root required");
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        canonical(root);
        Path source = root.resolve("build/p6-public/negative-fixtures/p6-cancel.jar");
        canonical(source);
        require(Files.size(source) == 6124 && hash(Files.readAllBytes(source)).equals(SOURCE_SHA), "Source identity mismatch");
        var original = new TreeMap<String, byte[]>();
        try (var zip = new ZipFile(source.toFile(), StandardCharsets.UTF_8)) {
            var iterator = zip.entries();
            while (iterator.hasMoreElements()) {
                var entry = iterator.nextElement();
                if (entry.isDirectory()) continue;
                require(original.size() < 64 && entry.getSize() >= 0 && entry.getSize() < 65536,
                        "Unexpected source entry bounds");
                require(!entry.getName().contains("..") && !entry.getName().startsWith("/"), "Unsafe source entry");
                try (var stream = zip.getInputStream(entry)) {
                    byte[] bytes = stream.readNBytes(65536);
                    require(bytes.length == entry.getSize() && original.put(entry.getName(), bytes) == null,
                            "Unexpected source entry data/duplicate");
                }
            }
        }
        require(original.keySet().stream().filter(name -> name.endsWith(".class")).toList()
                .equals(List.of("dev/legacybridge/fixture/LegacyBridgeExample.class")), "Unexpected source classes");
        var all = new TreeMap<>(original);
        var random = new Random(20260907L);
        var json = new StringBuilder("{");
        for (int key = 0; key < 64; key++) {
            if (key != 0) json.append(',');
            json.append('"').append("p6_cancel.qa.load.").append(key).append("\":\"");
            for (int character = 0; character < 8000; character++) json.append((char) ('a' + random.nextInt(26)));
            json.append('"');
        }
        byte[] payload = json.append('}').toString().getBytes(StandardCharsets.UTF_8);
        require(payload.length < 1048576, "Payload exceeds existing resource limit");
        for (String language : LANGUAGES) require(all.put("assets/p6_cancel/lang/" + language + ".json", payload) == null,
                "Would replace original resource");
        Path parent = root.resolve("build/public-current-negatives-2026-09-07-run1");
        canonical(parent);
        Path destination = parent.resolve("p6-cancel-bounded.jar");
        Path temporary = Files.createTempFile(parent, "bounded-fixture-", ".tmp");
        try {
            try (var zip = new ZipOutputStream(Files.newOutputStream(temporary), StandardCharsets.UTF_8)) {
                for (var value : all.entrySet()) {
                    var entry = new ZipEntry(value.getKey());
                    entry.setTimeLocal(LocalDateTime.of(1980, 1, 1, 0, 0));
                    entry.setMethod(ZipEntry.STORED); // No compressed bomb or CPU delay hook.
                    entry.setSize(value.getValue().length);
                    var crc = new CRC32(); crc.update(value.getValue()); entry.setCrc(crc.getValue());
                    zip.putNextEntry(entry); zip.write(value.getValue()); zip.closeEntry();
                }
            }
            require(Files.size(temporary) < 13L * 1024 * 1024, "Fixture aggregate bound exceeded");
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                canonical(destination);
                require(Files.mismatch(temporary, destination) == -1, "Refusing changed existing fixture");
            } else Files.createLink(destination, temporary); // Atomic no-replace publication on this local filesystem.
            try (var zip = new ZipFile(destination.toFile(), StandardCharsets.UTF_8)) {
                require(zip.size() == all.size(), "Unexpected generated entry count");
                for (var entry : original.entrySet()) try (var stream = zip.getInputStream(zip.getEntry(entry.getKey()))) {
                    require(Arrays.equals(entry.getValue(), stream.readAllBytes()), "Original bytes changed");
                }
            }
            require(hash(Files.readAllBytes(source)).equals(SOURCE_SHA), "Source changed");
            System.out.println("BOUNDED_CANCELLATION_FIXTURE_VERIFIED bytes=" + Files.size(destination)
                    + " addedLanguageFiles=" + LANGUAGES.size() + " sha256=" + hash(Files.readAllBytes(destination)));
        } finally { Files.deleteIfExists(temporary); }
    }

    private static void canonical(Path path) throws IOException {
        require(path.equals(path.toRealPath()), "Noncanonical path");
        for (Path part = path; part != null; part = part.getParent()) require(!Files.isSymbolicLink(part), "Aliased path");
    }
    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static void require(boolean ok, String message) throws IOException { if (!ok) throw new IOException(message); }
}
