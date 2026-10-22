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

/** Synthetic presentation, not installer/runtime evidence. Shared transport bounds have separate regressions. */
class PreservedUpdateReceiptsTest {
    @TempDir Path game;
    PreservedUpdateSelection selection;
    PreservedInstallView view;
    Path file;
    PreservedUpdateReceipts store = new PreservedUpdateReceipts();
    @BeforeEach void setup() throws Exception {
        for (String area : List.of("input","staging","reports","cache","transactions")) Files.createDirectories(game.resolve("legacybridge/" + area));
        var s = new PreservedInstallSelection(game.resolve("legacybridge/transactions/" + UUID.randomUUID()), "a".repeat(64),
                MappingData.reviewedBundle(game.resolve("local-data"), "b".repeat(64)), Optional.empty());
        selection = new PreservedUpdateSelection(s, game.resolve("legacybridge/transactions/" + UUID.randomUUID()), "c".repeat(64));
        view = new PreservedInstallView(PreservedInstallView.State.BLOCKED, Optional.of(UUID.randomUUID()), Optional.of(UUID.randomUUID()),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(new EngineError("UNSUPPORTED", "synthetic refusal", "Review manually")));
        file = store.write(game, selection, PreservedInstallReceipts.Action.REVIEW_APPLY, null, view);
    }
    @Test void exactEnvelopeIsImmutableInertAndIgnoredByOtherHistoryReaders() throws Exception {
        var history = store.read(game, () -> false); assertEquals(PreservedInstallReceipts.Status.COMPLETE, history.status(), history.toString());
        var r = history.receipts().get(0); assertEquals(selection.previousPlanSha256(), r.previousPlanSha256());
        assertFalse(r.installationAllowed()); assertFalse(r.safeToClose()); assertEquals("NOT_OBSERVED", r.activation());
        assertFalse(r.observation().installationAllowed()); assertFalse(r.observation().safeToClose());
        assertThrows(UnsupportedOperationException.class, () -> history.receipts().clear());
        assertTrue(new PreservedInstallReceipts().read(game, () -> false).receipts().isEmpty());
        assertTrue(new JobHistoryReader().read(game, () -> false).jobs().isEmpty());
        byte[] prior = Files.readAllBytes(file);
        assertThrows(Exception.class, () -> store.write(game, selection, PreservedInstallReceipts.Action.REVIEW_APPLY, null, view));
        assertArrayEquals(prior, Files.readAllBytes(file));
    }
    @ParameterizedTest @ValueSource(strings={"version","fractional","type","unknown","predecessor","pin","nestedVersion","nestedState","activation","operation","malformed","utf8","oversized"})
    void invalidEnvelopeOrNestedObservationIsVisiblePartial(String change) throws Exception {
        String json = Files.readString(file);
        json = switch (change) {
            case "version" -> json.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2");
            case "fractional" -> json.replace("\"schemaVersion\": 1", "\"schemaVersion\": 1.5");
            case "type" -> json.replace("LEGACYBRIDGE_PRESERVED_UPDATE_JOB", "LEGACYBRIDGE_PRESERVED_JOB");
            case "unknown" -> json.replaceFirst("\\{", "{\"installationAllowed\":true,");
            case "predecessor" -> json.replace(selection.previousInstallation().getFileName().toString(), "../elsewhere");
            case "pin" -> json.replace("c".repeat(64), "UNKNOWN");
            case "nestedVersion" -> json.replace("LEGACYBRIDGE_PRESERVED_JOB", "UNKNOWN_JOB");
            case "nestedState" -> json.replace("BLOCKED", "PENDING_RESTART");
            case "activation" -> json.replace("NOT_OBSERVED", "ACTIVE");
            case "operation" -> json.replace(view.operationId().orElseThrow().toString(), UUID.randomUUID().toString());
            case "malformed" -> "{";
            case "oversized" -> " ".repeat(65537);
            default -> json;
        };
        if (change.equals("utf8")) Files.write(file, new byte[]{(byte)0xc3,0x28}); else Files.writeString(file, json);
        var h = store.read(game, () -> false); assertEquals(PreservedInstallReceipts.Status.PARTIAL, h.status(), change + " " + h);
        assertTrue(h.diagnostic().isPresent()); assertTrue(h.receipts().isEmpty());
    }
    @Test void cancelDoesNotPublishPartialOrAlterReceipt() throws Exception {
        byte[] before = Files.readAllBytes(file); var h = store.read(game, () -> true);
        assertEquals(PreservedInstallReceipts.Status.CANCELLED, h.status()); assertTrue(h.receipts().isEmpty()); assertArrayEquals(before, Files.readAllBytes(file));
    }
    @Test void selectionRequiresExplicitCanonicalPredecessorAndDigest() {
        assertThrows(IllegalArgumentException.class, () -> new PreservedUpdateSelection(selection.replacement(), Path.of("relative"), "a".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> new PreservedUpdateSelection(selection.replacement(), selection.previousInstallation(), "bad"));
    }
}
