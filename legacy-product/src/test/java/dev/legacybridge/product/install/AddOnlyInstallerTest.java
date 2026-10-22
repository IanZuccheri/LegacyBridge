package dev.legacybridge.product.install;

import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.converter.conversion.TransformationBindings;
import dev.legacybridge.converter.conversion.extract.ContentPlan;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import dev.legacybridge.runtime.compat.registry.ModMetadataAdapter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import static org.junit.jupiter.api.Assertions.*;

class AddOnlyInstallerTest {
    @TempDir Path game;
    private Path original;
    private Path staged;
    private static final Set<String> LOADED = Set.of("minecraft", "forge", "legacybridge_runtime");
    private static final String ID = "fixture_install";

    @BeforeEach void prepare() throws Exception {
        for (String name : List.of("input", "staging", "reports", "cache", "transactions"))
            Files.createDirectories(game.resolve("legacybridge").resolve(name));
        Files.createDirectory(game.resolve("mods"));
        original = Files.writeString(game.resolve("legacybridge/input/original.jar"), "inert source fingerprint; never loaded");
        staged = jar(game.resolve("legacybridge/staging/converted.jar"), stagedEntries());
        installedMod(game.resolve("mods/LegacyBridge.jar"), "legacybridge_runtime", AdapterCatalog.RUNTIME_VERSION);
    }

    @Test void addsCompleteIndependentJarAndDurableReceiptsWithoutActivatingOrChangingOriginal() throws Exception {
        String source = AddOnlyInstaller.hash(original);
        String converted = AddOnlyInstaller.hash(staged);
        var result = apply(new AddOnlyInstaller(), converted, source, LOADED);
        assertEquals("PENDING_RESTART", result.status());
        assertEquals(List.of(ID), result.modIds());
        assertEquals(converted, AddOnlyInstaller.hash(result.installed()));
        assertEquals(source, AddOnlyInstaller.hash(original));
        assertEquals(converted, AddOnlyInstaller.hash(staged));
        assertFalse(Files.isSameFile(staged, result.installed()), "Staging must not become a mutable hard-link alias");
        assertTrue(Files.isSameFile(result.transaction().resolve("payload.jar"), result.installed()));
        assertTrue(Files.readString(result.transaction().resolve("prepared.json")).contains("PREPARED_ADD_ONLY"));
        assertTrue(Files.readString(result.transaction().resolve("committed.json")).contains("PENDING_RESTART"));
        assertEquals(result, new AddOnlyInstaller().inspect(game, result.transaction()));
        assertEquals(LOADED, Set.of("minecraft", "forge", "legacybridge_runtime"));
    }

    @Test void refusesChangedSessionFingerprintsWithoutPublishing() throws Exception {
        String originalHash = AddOnlyInstaller.hash(original);
        assertRefused(() -> apply(new AddOnlyInstaller(), "0".repeat(64), originalHash, LOADED));
        assertRefused(() -> apply(new AddOnlyInstaller(), AddOnlyInstaller.hash(staged), "0".repeat(64), LOADED));
        assertRefused(() -> apply(new AddOnlyInstaller(), AddOnlyInstaller.hash(staged), "not-a-hash", LOADED));
        assertEquals(1, installedCount());
    }

    @Test void rejectsOriginalOrStagedOutsideTheirReservedAreas() throws Exception {
        Path outside = Files.copy(staged, game.resolve("outside.jar"));
        var installer = new AddOnlyInstaller();
        assertRefused(() -> installer.apply(game, outside, AddOnlyInstaller.hash(outside), original, AddOnlyInstaller.hash(original), LOADED));
        assertRefused(() -> installer.apply(game, staged, AddOnlyInstaller.hash(staged), outside, AddOnlyInstaller.hash(outside), LOADED));
        assertTrue(Files.exists(outside));
        assertEquals(1, installedCount());
    }

    @Test void refusesLoadedAndUnloadedInstalledDuplicateModIds() throws Exception {
        Set<String> duplicateLoaded = Set.of("minecraft", "forge", "legacybridge_runtime", ID);
        assertRefused(() -> apply(new AddOnlyInstaller(), AddOnlyInstaller.hash(staged), AddOnlyInstaller.hash(original), duplicateLoaded));
        Path existing = installedMod(game.resolve("mods/unloaded.jar"), ID, "1.0");
        String before = AddOnlyInstaller.hash(existing);
        assertRefused(() -> apply(new AddOnlyInstaller(), AddOnlyInstaller.hash(staged), AddOnlyInstaller.hash(original), LOADED));
        assertEquals(before, AddOnlyInstaller.hash(existing));
        assertEquals(2, installedCount());
    }

    @Test void missingNextLaunchRuntimeOrLoadedBaselineIsRefused() throws Exception {
        assertRefused(() -> apply(new AddOnlyInstaller(), AddOnlyInstaller.hash(staged), AddOnlyInstaller.hash(original), Set.of("minecraft", "forge")));
        Files.move(game.resolve("mods/LegacyBridge.jar"), game.resolve("runtime-disabled.jar"));
        assertRefused(() -> apply(new AddOnlyInstaller(), AddOnlyInstaller.hash(staged), AddOnlyInstaller.hash(original), LOADED));
        assertEquals(0, installedCount());
    }

    @Test void acceptsCompatibleOptionalAndMandatoryIncomingVersionRanges() throws Exception {
        installedDependent("optional_consumer", "[1,2)", false);
        installedDependent("required_consumer", "[1.0]", true);
        installedDependent("inclusive_consumer", "(,1.0]", false);
        installedDependent("unbounded_consumer", null, true);
        var result = apply(new AddOnlyInstaller(), AddOnlyInstaller.hash(staged), AddOnlyInstaller.hash(original),
                Set.of("minecraft", "forge", "legacybridge_runtime", "optional_consumer"));
        assertEquals("PENDING_RESTART", result.status());
        assertEquals(6, installedCount());
    }

    @Test void refusesOptionalDependencyThatBecomesIncompatibleWhenCandidateIsInstalled() throws Exception {
        Path dependent = installedDependent("optional_consumer", "[2,3)", false);
        String existingHash = AddOnlyInstaller.hash(dependent);
        String sourceHash = AddOnlyInstaller.hash(original);
        var error = assertRefused(() -> apply(new AddOnlyInstaller(), AddOnlyInstaller.hash(staged), sourceHash,
                Set.of("minecraft", "forge", "legacybridge_runtime", "optional_consumer")));
        assertTrue(error.getMessage().contains("optional_consumer -> " + ID));
        assertTrue(error.getMessage().contains("candidate 1.0"));
        assertEquals(existingHash, AddOnlyInstaller.hash(dependent));
        assertEquals(sourceHash, AddOnlyInstaller.hash(original));
        assertEquals(2, installedCount());
    }

    @Test void refusesMandatoryDependencyWithExclusiveIncomingVersionBoundary() throws Exception {
        installedDependent("required_consumer", "[0,1.0)", true);
        var error = assertRefused(() -> apply(new AddOnlyInstaller(), AddOnlyInstaller.hash(staged), AddOnlyInstaller.hash(original), LOADED));
        assertTrue(error.getMessage().contains("Installed dependency on incoming mod"));
        assertEquals(2, installedCount());
    }

    @Test void refusesUnknownIncomingVersionRangeWithoutGuessing() throws Exception {
        installedDependent("qualified_consumer", "[1.0-SNAPSHOT,2)", false);
        var error = assertRefused(() -> apply(new AddOnlyInstaller(), AddOnlyInstaller.hash(staged), AddOnlyInstaller.hash(original), LOADED));
        assertTrue(error.getMessage().contains("Unknown installed dependency range grammar"));
        assertEquals(2, installedCount());
    }

    @Test void refusesUncomparableCandidateVersionWhenAnIncomingRangeMustBeChecked() throws Exception {
        Files.delete(staged); jar(staged, stagedEntries("1.0-SNAPSHOT"));
        installedDependent("numeric_consumer", "[1,2)", false);
        var error = assertRefused(() -> apply(new AddOnlyInstaller(), AddOnlyInstaller.hash(staged), AddOnlyInstaller.hash(original), LOADED));
        assertTrue(error.getMessage().contains("cannot be compared"));
        assertTrue(error.getMessage().contains("candidate 1.0-SNAPSHOT"));
        assertEquals(2, installedCount());
    }

    @Test void absentIncomingVersionRangeIsUnboundedWithoutGuessingQualifiedVersionOrder() throws Exception {
        Files.delete(staged); jar(staged, stagedEntries("1.0-SNAPSHOT"));
        installedDependent("unbounded_consumer", null, false);
        var result = apply(new AddOnlyInstaller(), AddOnlyInstaller.hash(staged), AddOnlyInstaller.hash(original), LOADED);
        assertEquals("PENDING_RESTART", result.status());
        assertEquals(3, installedCount());
    }

    @Test void malformedInstalledJarCannotBeSilentlySkipped() throws Exception {
        Path broken = Files.writeString(game.resolve("mods/broken.jar"), "invalid");
        assertRefused(() -> apply(new AddOnlyInstaller(), AddOnlyInstaller.hash(staged), AddOnlyInstaller.hash(original), LOADED));
        assertEquals("invalid", Files.readString(broken));
        assertEquals(2, installedCount());
    }

    @Test void unknownNestedInstalledDependencyClosureIsRefused() throws Exception {
        jar(game.resolve("mods/nested.jar"), Map.of(
                "META-INF/mods.toml", metadata("nested_parent", "1.0", false).getBytes(StandardCharsets.UTF_8),
                "fixture/Declared.class", declaration("fixture/Declared", "nested_parent"),
                "META-INF/libraries/unknown.jar", new byte[]{1, 2, 3}));
        assertRefused(() -> apply(new AddOnlyInstaller(), AddOnlyInstaller.hash(staged), AddOnlyInstaller.hash(original), LOADED));
        assertEquals(2, installedCount());
    }

    @Test void retainedTransactionBudgetRequiresReviewInsteadOfUnboundedGrowth() throws Exception {
        for (int count = 0; count < 32; count++)
            Files.createDirectory(game.resolve("legacybridge/transactions").resolve(java.util.UUID.randomUUID().toString()));
        InstallException error = assertRefused(() -> apply(new AddOnlyInstaller(), AddOnlyInstaller.hash(staged), AddOnlyInstaller.hash(original), LOADED));
        assertTrue(error.getMessage().contains("Retained transaction limit"));
        assertTrue(error.transaction().isEmpty());
        assertEquals(1, installedCount());
    }

    @Test void arbitraryExtraClassIsRejectedEvenWithCallerSuppliedMatchingHash() throws Exception {
        Map<String, byte[]> entries = stagedEntries();
        entries.put("foreign/Extra.class", declaration("foreign/Extra", "other_id"));
        Files.delete(staged); jar(staged, entries);
        assertRefused(() -> apply(new AddOnlyInstaller(), AddOnlyInstaller.hash(staged), AddOnlyInstaller.hash(original), LOADED));
        assertEquals(1, installedCount());
    }

    @Test void mismatchedBindingsAndChangedResourceAreRejected() throws Exception {
        Map<String, byte[]> entries = stagedEntries();
        entries.put("META-INF/legacybridge/" + ID + "/adapters.json", "{}".getBytes(StandardCharsets.UTF_8));
        Files.delete(staged); jar(staged, entries);
        assertRefused(() -> apply(new AddOnlyInstaller(), AddOnlyInstaller.hash(staged), AddOnlyInstaller.hash(original), LOADED));
        entries = stagedEntries(); entries.put("assets/" + ID + "/models/item/ingot.json", "{}".getBytes(StandardCharsets.UTF_8));
        Files.delete(staged); jar(staged, entries);
        assertRefused(() -> apply(new AddOnlyInstaller(), AddOnlyInstaller.hash(staged), AddOnlyInstaller.hash(original), LOADED));
        assertEquals(1, installedCount());
    }

    @Test void refusalAtPreparedBoundaryKeepsRecoverableEvidenceButNoInstalledJar() throws Exception {
        var installer = new AddOnlyInstaller(boundary -> { if (boundary == AddOnlyInstaller.Boundary.PREPARED) throw new IOException("injected before publication"); });
        InstallException error = assertRefused(() -> apply(installer, AddOnlyInstaller.hash(staged), AddOnlyInstaller.hash(original), LOADED));
        Path transaction = error.transaction().orElseThrow();
        assertTrue(Files.exists(transaction.resolve("prepared.json")));
        assertEquals("NOT_PUBLISHED", assertThrows(InstallException.class, () -> new AddOnlyInstaller().inspect(game, transaction)).code());
        assertEquals(1, installedCount());
    }

    @Test void postPublicationFailureReportsInstalledPathAndInspectionProvesPendingRestart() throws Exception {
        var installer = new AddOnlyInstaller(boundary -> { if (boundary == AddOnlyInstaller.Boundary.PUBLISHED) throw new IOException("injected after hard link"); });
        InstallException error = assertThrows(InstallException.class, () -> apply(installer, AddOnlyInstaller.hash(staged), AddOnlyInstaller.hash(original), LOADED));
        assertEquals("PUBLISHED_RECEIPT_INCOMPLETE", error.code());
        Path installed = error.installed().orElseThrow();
        Path transaction = error.transaction().orElseThrow();
        assertFalse(Files.exists(transaction.resolve("committed.json")));
        assertEquals(AddOnlyInstaller.hash(staged), AddOnlyInstaller.hash(installed));
        assertEquals(installed, new AddOnlyInstaller().inspect(game, transaction).installed());
        assertEquals(2, installedCount());
    }

    @Test void inspectionRejectsTamperedPreparedSchemaFieldsWithoutChangingInstalledJar() throws Exception {
        var result = apply(new AddOnlyInstaller(), AddOnlyInstaller.hash(staged), AddOnlyInstaller.hash(original), LOADED);
        Path prepared = result.transaction().resolve("prepared.json");
        byte[] receiptBytes = Files.readAllBytes(prepared);
        @SuppressWarnings("unchecked")
        var receipt = (Map<String, Object>) BoundedJsonParser.parse(new String(receiptBytes, StandardCharsets.UTF_8));
        for (var mutation : Map.<String, Object>of("state", "ACTIVATED", "durability", "POWER_LOSS_SAFE",
                "originalSha256", "A".repeat(64)).entrySet()) {
            var altered = new TreeMap<>(receipt); altered.put(mutation.getKey(), mutation.getValue());
            Files.write(prepared, json(altered));
            InstallException error = assertThrows(InstallException.class, () -> new AddOnlyInstaller().inspect(game, result.transaction()));
            assertEquals("RECOVERY_REQUIRED", error.code());
            assertTrue(error.installed().isEmpty(), "Invalid receipt must not manufacture proof of completion");
            assertEquals(result.sha256(), AddOnlyInstaller.hash(result.installed()));
            assertEquals(2, installedCount());
        }
        var malformedType = new TreeMap<>(receipt); malformedType.put("originalSha256", 123);
        Files.write(prepared, json(malformedType));
        assertEquals("RECOVERY_REQUIRED", assertThrows(InstallException.class,
                () -> new AddOnlyInstaller().inspect(game, result.transaction())).code());
        Files.write(prepared, receiptBytes);
        assertEquals(result, new AddOnlyInstaller().inspect(game, result.transaction()));
    }

    @Test void sourceChangeAtPreparedBoundaryAbortsWithoutChangingAnyInstalledFile() throws Exception {
        var installer = new AddOnlyInstaller(boundary -> { if (boundary == AddOnlyInstaller.Boundary.PREPARED) Files.writeString(original, "external replacement"); });
        assertRefused(() -> apply(installer, AddOnlyInstaller.hash(staged), AddOnlyInstaller.hash(original), LOADED));
        assertEquals("external replacement", Files.readString(original));
        assertEquals(1, installedCount());
    }

    @Test void repeatedApplyNeverAddsASecondCopyOrOverwritesTheFirst() throws Exception {
        InstallResult first = apply(new AddOnlyInstaller(), AddOnlyInstaller.hash(staged), AddOnlyInstaller.hash(original), LOADED);
        String hash = AddOnlyInstaller.hash(first.installed());
        assertRefused(() -> apply(new AddOnlyInstaller(), AddOnlyInstaller.hash(staged), AddOnlyInstaller.hash(original), LOADED));
        assertEquals(hash, AddOnlyInstaller.hash(first.installed()));
        assertEquals(2, installedCount());
    }

    private InstallResult apply(AddOnlyInstaller installer, String stagedHash, String sourceHash, Set<String> loaded) throws InstallException {
        return installer.apply(game, staged, stagedHash, original, sourceHash, loaded);
    }
    private static InstallException assertRefused(Throwing action) {
        InstallException error = assertThrows(InstallException.class, action::run);
        assertTrue(error.installed().isEmpty(), "Refusal must not disguise an already published result"); return error;
    }
    private long installedCount() throws IOException { try (var files = Files.list(game.resolve("mods"))) { return files.count(); } }
    @FunctionalInterface private interface Throwing { void run() throws Exception; }

    private static Map<String, byte[]> stagedEntries() { return stagedEntries("1.0"); }
    private static Map<String, byte[]> stagedEntries(String version) { return stagedEntries(ID, version); }
    static Map<String, byte[]> stagedEntries(String modId, String version) {
        ContentPlan plan = new ContentPlan(modId, version, List.of(new ContentPlan.ItemDefinition("ingot", null,
                new ContentPlan.ItemProperties(64, 0, false, "COMMON", null))), List.of(), List.of());
        Map<String, Object> item = new TreeMap<>(); item.put("name", "ingot"); item.put("block", null);
        Map<String, Object> properties = new TreeMap<>(Map.of("stackSize", 64, "durability", 0, "fireResistant", false, "rarity", "COMMON"));
        properties.put("creativeTab", null); item.put("properties", properties);
        Map<String, byte[]> entries = new TreeMap<>();
        entries.put("META-INF/MANIFEST.MF", ModMetadataAdapter.targetManifest(version));
        entries.put("META-INF/mods.toml", metadata(modId, version, true).getBytes(StandardCharsets.UTF_8));
        entries.put("META-INF/legacybridge/" + modId + "/content.json", json(Map.of("schemaVersion", 1, "modId", modId, "modVersion", version, "items", List.of(item), "blocks", List.of(), "sounds", List.of())));
        entries.put("META-INF/legacybridge/" + modId + "/adapters.json", json(TransformationBindings.forPlan(plan).bindingTree()));
        entries.put("pack.mcmeta", json(Map.of("pack", Map.of("pack_format", 15, "description", "Installer fixture"))));
        entries.put("assets/" + modId + "/models/item/ingot.json", json(Map.of("parent", "minecraft:item/generated", "textures", Map.of("layer0", "minecraft:item/iron_ingot"))));
        String owner = "dev/legacybridge/generated/" + modId + "/ConvertedMod";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, owner, null, "java/lang/Object", null);
        var mod = writer.visitAnnotation("Lnet/minecraftforge/fml/common/Mod;", true); mod.visit("value", modId); mod.visitEnd();
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null); method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        method.visitLdcInsn(modId); method.visitLdcInsn(Type.getObjectType(owner));
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;", false);
        method.visitLdcInsn("META-INF/legacybridge/" + modId + "/content.json");
        var adapter = AdapterCatalog.require(AdapterCatalog.BOOTSTRAP);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, adapter.owner(), adapter.method(), adapter.descriptor(), false);
        method.visitInsn(Opcodes.RETURN); method.visitMaxs(0, 0); method.visitEnd(); writer.visitEnd();
        entries.put(owner + ".class", writer.toByteArray()); return entries;
    }
    private static byte[] json(Map<String, Object> value) { return new CanonicalReportWriter().toJsonTree(new TreeMap<>(value)).getBytes(StandardCharsets.UTF_8); }
    static Path installedMod(Path path, String id, String version) throws IOException {
        return jar(path, Map.of("META-INF/mods.toml", metadata(id, version, false).getBytes(StandardCharsets.UTF_8), "fixture/Declared.class", declaration("fixture/Declared", id)));
    }
    private Path installedDependent(String id, String incomingRange, boolean mandatory) throws IOException {
        String text = metadata(id, "1.0", false) + "[[dependencies." + id + "]]\nmodId=\"" + ID
                + "\"\nmandatory=" + mandatory + "\nordering=\"NONE\"\nside=\"BOTH\"\n"
                + (incomingRange == null ? "" : "versionRange=\"" + incomingRange + "\"\n");
        return jar(game.resolve("mods").resolve(id + ".jar"), Map.of("META-INF/mods.toml", text.getBytes(StandardCharsets.UTF_8),
                "fixture/Declared.class", declaration("fixture/Declared", id)));
    }
    static byte[] declaration(String owner, String id) {
        ClassWriter writer = new ClassWriter(0); writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        var annotation = writer.visitAnnotation("Lnet/minecraftforge/fml/common/Mod;", true); annotation.visit("value", id); annotation.visitEnd(); writer.visitEnd(); return writer.toByteArray();
    }
    static String metadata(String id, String version, boolean converted) {
        var ranges = ModMetadataAdapter.targetRanges("1.16.5", "36.2.42");
        StringBuilder text = new StringBuilder("modLoader=\"javafml\"\nloaderVersion=\"[47,48)\"\nlicense=\"MIT\"\n[[mods]]\nmodId=\"")
                .append(id).append("\"\nversion=\"").append(version).append("\"\n");
        for (String dependency : converted ? List.of("forge", "minecraft", "legacybridge_runtime") : List.of("forge", "minecraft"))
            text.append("[[dependencies.").append(id).append("]]\nmodId=\"").append(dependency)
                    .append("\"\nmandatory=true\nversionRange=\"").append(ranges.get(dependency)).append("\"\nordering=\"")
                    .append(dependency.equals("legacybridge_runtime") ? "AFTER" : "NONE").append("\"\nside=\"BOTH\"\n");
        return text.toString();
    }
    static Path jar(Path path, Map<String, byte[]> entries) throws IOException {
        try (var zip = new ZipOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE_NEW))) {
            for (var entry : new TreeMap<>(entries).entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry();
            }
        }
        return path;
    }
}
