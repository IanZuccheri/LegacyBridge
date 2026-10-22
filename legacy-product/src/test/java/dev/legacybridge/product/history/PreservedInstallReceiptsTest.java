package dev.legacybridge.product.history;

import dev.legacybridge.converter.service.*;
import dev.legacybridge.product.controller.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic presentation JSON only; never a passed validator, consent or executable mod. */
class PreservedInstallReceiptsTest {
    @TempDir Path game;
    final PreservedInstallReceipts store=new PreservedInstallReceipts();
    Path file;
    @BeforeEach void setup() throws Exception {
        for(String area:List.of("input","staging","reports","cache","transactions")) Files.createDirectories(game.resolve("legacybridge/"+area));
        var selection=new PreservedInstallSelection(game.resolve("legacybridge/transactions").resolve(UUID.randomUUID().toString()),
                "a".repeat(64),MappingData.reviewedBundle(game.resolve("explicit-local-data"),"b".repeat(64)),Optional.empty());
        var view=new PreservedInstallView(PreservedInstallView.State.BLOCKED,Optional.of(UUID.randomUUID()),Optional.of(UUID.randomUUID()),
                Optional.empty(),Optional.empty(),Optional.empty(),Optional.of(new EngineError("UNSUPPORTED","synthetic refusal","Inspect manually")));
        file=store.write(game,selection,PreservedInstallReceipts.Action.REVIEW_APPLY,null,view);
    }
    @Test void exactSchemaReadIsInertAndOldReaderIgnoresDistinctReceiptPrefix() {
        var h=store.read(game,()->false); assertEquals(PreservedInstallReceipts.Status.COMPLETE,h.status(),h.toString());
        assertEquals(1,h.receipts().size()); var r=h.receipts().get(0);
        assertFalse(r.installationAllowed()); assertFalse(r.safeToClose()); assertEquals("NOT_OBSERVED",r.activation());
        assertEquals(JobHistory.Status.COMPLETE,new JobHistoryReader().read(game,()->false).status());
        assertTrue(new JobHistoryReader().read(game,()->false).jobs().isEmpty());
        assertThrows(UnsupportedOperationException.class,()->h.receipts().clear());
    }
    @ParameterizedTest @ValueSource(strings={"schema","fractional","unknown","activation","id","hash","type","state","surrogate","malformed","oversized","utf8","error"})
    void hostileOrUnknownReceiptIsVisiblePartialNotApproval(String mutation) throws Exception {
        String json=Files.readString(file);
        json=switch(mutation) {
            case "schema" -> json.replace("\"schemaVersion\": 1","\"schemaVersion\": 2");
            case "fractional" -> json.replace("\"schemaVersion\": 1","\"schemaVersion\": 1.5");
            case "unknown" -> json.replaceFirst("\\{","{\"installAllowed\":true,");
            case "activation" -> json.replace("NOT_OBSERVED","ACTIVE");
            case "id" -> json.replace(file.getParent().getFileName().toString(),UUID.randomUUID().toString());
            case "hash" -> json.replace("a".repeat(64),"../arbitrary");
            case "type" -> json.replace("\"BLOCKED\"","true");
            case "state" -> json.replace("BLOCKED","PENDING_RESTART");
            case "surrogate" -> json.replace("UNSUPPORTED","\\ud800");
            case "error" -> json.replace("UNSUPPORTED","\\u00a7formatted\\ncontrol");
            case "malformed" -> "{";
            case "oversized" -> " ".repeat(65537);
            default -> json;
        };
        if(mutation.equals("utf8")) Files.write(file,new byte[]{(byte)0xc3,0x28}); else Files.writeString(file,json);
        var h=store.read(game,()->false); assertEquals(PreservedInstallReceipts.Status.PARTIAL,h.status(),mutation+" "+h);
        assertTrue(h.diagnostic().isPresent()); assertTrue(h.receipts().isEmpty());
    }
    @Test void hardLinkDuplicateAndUnexpectedNamesCannotBecomeLatestResult() throws Exception {
        UUID id=UUID.randomUUID(); Path dir=Files.createDirectory(game.resolve("legacybridge/reports").resolve(id.toString()));
        Path alias=Files.createLink(dir.resolve("preserved-job-"+id+".json"),file);
        assertTrue(Files.isSameFile(alias,file));
        assertEquals(PreservedInstallReceipts.Status.PARTIAL,store.read(game,()->false).status());
    }
    @Test void cancellationReturnsNoPartialSelectionAndDoesNotWrite() throws Exception {
        byte[] before=Files.readAllBytes(file); var h=store.read(game,()->true);
        assertEquals(PreservedInstallReceipts.Status.CANCELLED,h.status()); assertTrue(h.receipts().isEmpty());
        assertArrayEquals(before,Files.readAllBytes(file));
    }
    @Test void entryBudgetCountsUnrelatedFiles() throws Exception {
        for(int i=0;i<1025;i++) Files.createFile(file.getParent().resolve("unrelated-"+i));
        var h=store.read(game,()->false); assertEquals(PreservedInstallReceipts.Status.PARTIAL,h.status());
        assertEquals("HISTORY_ENTRY_LIMIT",h.diagnostic().orElseThrow());
    }
    @Test void absenceDoesNotCreateWorkspaceOrInventHistory() throws Exception {
        Path absent=game.resolve("separate-unused-installation");
        Files.createDirectory(absent);
        var h=store.read(absent,()->false); assertEquals(PreservedInstallReceipts.Status.COMPLETE,h.status());
        assertFalse(Files.exists(absent.resolve("legacybridge")));
    }
}
