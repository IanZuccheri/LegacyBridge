package dev.legacybridge.product.history;

import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.config.BoundedJsonParser;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SavedReportStoreTest {
    @TempDir Path game;
    private final UUID job = UUID.randomUUID();
    private final String inputHash = "a".repeat(64);
    private final SavedReportStore store = new SavedReportStore();
    private Path directory, index, receiptFile;
    private final Map<SavedReport.Kind, Path> paths = new EnumMap<>(SavedReport.Kind.class);
    private JobHistory.Receipt receipt;

    private void fixture(boolean indexed, String body) throws Exception {
        Files.writeString(game.resolve("input.jar"), "inert original used only for path protection");
        directory = Files.createDirectories(game.resolve("legacybridge/reports/" + UUID.randomUUID()));
        index = directory.resolve("reports-" + job + ".json");
        receiptFile = directory.resolve("job-" + UUID.randomUUID() + ".json");
        var data = new LinkedHashMap<String,Object>();
        data.put("schemaVersion",2); data.put("reportType","LEGACYBRIDGE_JOB"); data.put("jobId",job.toString());
        data.put("state","FAILED"); data.put("input","input.jar"); data.put("inputSha256",inputHash);
        for (String key : List.of("artifactSha256","artifact","installed","transaction","error","installation")) data.put(key,null);
        data.put("sequence",1); data.put("event","CONVERSION"); data.put("activation","NOT_OBSERVED");
        Files.writeString(receiptFile,new CanonicalReportWriter().toJsonTree(data));
        for (var kind : SavedReport.Kind.values()) {
            Path parent = kind == SavedReport.Kind.COMPATIBILITY ? directory
                    : Files.createDirectories(game.resolve("legacybridge/reports/" + UUID.randomUUID()));
            paths.put(kind,Files.writeString(parent.resolve(kind.fileName()),body));
        }
        var history = new JobHistoryReader().read(game,()->false);
        assertEquals(JobHistory.Status.COMPLETE,history.status(),history.issues().toString());
        receipt = history.jobs().get(0).highestRecorded().orElseThrow();
        if(indexed) store.writeIndex(game,job,inputHash,directory,paths,game.resolve("input.jar"));
    }

    @Test void indexedReportsFromDifferentOperationsReopenWithoutWritesOrAuthority() throws Exception {
        fixture(true,"{\n  \"value\": 3\n}\n");
        var before = snapshot();
        for(var kind:SavedReport.Kind.values()) {
            var result=store.read(game,receipt,kind,()->false);
            assertTrue(result.value().isPresent(),result.error().toString());
            assertEquals(Files.readString(paths.get(kind)),result.value().orElseThrow().text());
            assertEquals(job,result.value().orElseThrow().jobId());
        }
        assertEquals(before,snapshot()); assertFalse(Files.exists(game.resolve("mods")));
        assertEquals(JobHistory.Activation.NOT_OBSERVED,receipt.activation());
    }
    @Test void legacyIndexMissingAndUnrecordedKindAreExplicitNotInvented() throws Exception {
        fixture(false,"{}"); var before=snapshot(); code("REPORT_INDEX_MISSING"); assertEquals(before,snapshot());
        store.writeIndex(game,job,inputHash,directory,Map.of(SavedReport.Kind.CONVERSION,paths.get(SavedReport.Kind.CONVERSION)),game.resolve("input.jar"));
        code("REPORT_NOT_RECORDED");
    }
    @Test void changedContentAndMissingFileRefuseWithoutPublishingText() throws Exception {
        fixture(true,"{}"); Path report=paths.get(SavedReport.Kind.COMPATIBILITY);
        Files.writeString(report,"{\"changed\":true}"); code("REPORT_HASH_MISMATCH");
        Files.delete(report); code("REPORT_READ_FAILED");
    }
    @Test void changedReceiptIdentityAndIndexSchemaRefuse() throws Exception {
        fixture(true,"{}"); String original=Files.readString(receiptFile);
        Files.writeString(receiptFile,original.replace(inputHash,"b".repeat(64))); code("REPORT_IDENTITY_MISMATCH");
        Files.writeString(receiptFile,original);
        String json=Files.readString(index); Files.writeString(index,json.replace("\"schemaVersion\": 1","\"schemaVersion\": 99")); code("REPORT_INDEX_SCHEMA");
    }
    @ParameterizedTest @ValueSource(strings={"../secret.json","legacybridge/reports/../analysis.json","legacybridge/reports/00000000-0000-0000-0000-000000000000/evil.jar","C:/secret.json","legacybridge/reports/00000000-0000-0000-0000-000000000000/analysis.json:stream"})
    void unsafeIndexPathsAreRejected(String path) throws Exception {
        fixture(true,"{}"); var tree=indexTree(); rows(tree).get(0).put("path",path); writeTree(tree); code("REPORT_PATH_UNSAFE");
    }
    @Test void duplicateKindAndUnknownFieldsAreRejected() throws Exception {
        fixture(true,"{}"); var tree=indexTree(); rows(tree).get(1).put("kind","COMPATIBILITY"); writeTree(tree); code("REPORT_INDEX_INVALID");
        tree.put("installAllowed",true); writeTree(tree); code("REPORT_INDEX_INVALID");
    }
    @Test void oversizedReportIndexAndInvalidEncodingFailClosed() throws Exception {
        fixture(false,"{}"); Path report=paths.get(SavedReport.Kind.COMPATIBILITY);
        Files.write(report,new byte[SavedReportStore.REPORT_BYTES+1]);
        assertThrows(java.io.IOException.class,()->store.writeIndex(game,job,inputHash,directory,paths,game.resolve("input.jar")));
        assertFalse(Files.exists(index)); Files.write(report,new byte[]{(byte)0xc3,0x28});
        store.writeIndex(game,job,inputHash,directory,paths,game.resolve("input.jar")); code("REPORT_UTF8_INVALID");
        Files.write(index,new byte[16385]); code("REPORT_TOO_LARGE");
    }
    @Test void validHashDoesNotAcceptMalformedOrExcessivelyDeepJson() throws Exception {
        fixture(true,"{broken"); code("REPORT_JSON_INVALID");
        Files.delete(index); Files.writeString(paths.get(SavedReport.Kind.COMPATIBILITY),"[".repeat(80)+"0"+"]".repeat(80));
        store.writeIndex(game,job,inputHash,directory,paths,game.resolve("input.jar")); code("REPORT_JSON_INVALID");
    }
    @Test void cancellationAndReadRacesPublishNoSnapshot() throws Exception {
        fixture(true,"{}"); var before=snapshot();
        var cancelled=store.read(game,receipt,SavedReport.Kind.COMPATIBILITY,()->true);
        assertEquals("CANCELLED",cancelled.error().orElseThrow().code()); assertTrue(cancelled.value().isEmpty()); assertEquals(before,snapshot());
        AtomicInteger calls=new AtomicInteger();
        assertTrue(store.read(game,receipt,SavedReport.Kind.COMPATIBILITY,()->{calls.incrementAndGet();return false;}).value().isPresent());
        int checkpoints=calls.get(); calls.set(0);
        var raced=store.read(game,receipt,SavedReport.Kind.COMPATIBILITY,()->{
            if(calls.incrementAndGet()==checkpoints-2) try { Files.writeString(index," ",StandardOpenOption.APPEND); } catch(java.io.IOException failure) { throw new AssertionError(failure); }
            return false;
        });
        assertTrue(raced.value().isEmpty()); assertEquals("REPORT_CHANGED",raced.error().orElseThrow().code());
    }
    @Test void publicationNeverOverwritesAnExistingIndex() throws Exception {
        fixture(true,"{}"); var before=snapshot();
        assertThrows(java.io.IOException.class,()->store.writeIndex(game,job,inputHash,directory,paths,game.resolve("input.jar")));
        assertEquals(before,snapshot());
    }
    @Test void symlinkReportIsRejectedWhenPlatformAllowsCreatingIt() throws Exception {
        fixture(true,"{}"); Path target=paths.get(SavedReport.Kind.CONVERSION), link=paths.get(SavedReport.Kind.COMPATIBILITY);
        Files.delete(link);
        try { Files.createSymbolicLink(link,target); }
        catch(java.io.IOException | UnsupportedOperationException | SecurityException unavailable) { assumeTrue(false,"Symlink creation not permitted"); }
        code("REPORT_PATH_UNSAFE");
    }
    @Test void pagedLiteralTextKeepsUnicodeWithoutInterpretingComponents() throws Exception {
        String body="{\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/stop\"},\"text\":\""+"x".repeat(7000)+"§c\u202e😀\"}";
        fixture(true,body); var report=store.read(game,receipt,SavedReport.Kind.COMPATIBILITY,()->false).value().orElseThrow();
        assertTrue(report.displaySanitized()); assertFalse(report.text().contains("§")); assertFalse(report.text().contains("\u202e"));
        StringBuilder joined=new StringBuilder(); for(int i=0;i<report.pages();i++) joined.append(report.page(i)); assertEquals(report.text(),joined.toString());
        var crossing=new SavedReport(job,SavedReport.Kind.COMPATIBILITY,inputHash,"x".repeat(4095)+"😀z",false);
        assertEquals(crossing.text(),crossing.page(0)+crossing.page(1)); assertThrows(IllegalArgumentException.class,()->crossing.page(-1));
    }
    private void code(String code) { var outcome=store.read(game,receipt,SavedReport.Kind.COMPATIBILITY,()->false); assertTrue(outcome.value().isEmpty()); assertEquals(code,outcome.error().orElseThrow().code()); }
    @SuppressWarnings("unchecked") private Map<String,Object> indexTree() throws Exception { return (Map<String,Object>)BoundedJsonParser.parse(Files.readString(index)); }
    @SuppressWarnings("unchecked") private List<Map<String,Object>> rows(Map<String,Object> tree) { return (List<Map<String,Object>>)tree.get("reports"); }
    private void writeTree(Map<String,Object> tree) throws Exception { Files.writeString(index,new CanonicalReportWriter().toJsonTree(tree)); }
    private Map<String,String> snapshot() throws Exception { var result=new TreeMap<String,String>(); try(var files=Files.walk(game)){for(Path path:files.filter(Files::isRegularFile).toList())result.put(game.relativize(path).toString(),Base64.getEncoder().encodeToString(Files.readAllBytes(path)));}return result; }
}
