package dev.legacybridge.product.controller;

import dev.legacybridge.product.install.InstallSession;
import dev.legacybridge.product.install.RecoveryAction;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static dev.legacybridge.product.controller.PreservedDeveloperForm.Field.*;

/** Pure form/thread bridge checks; never defines Minecraft classes or touches a selected file. */
class PreservedDeveloperFormTest {
    @TempDir Path root;
    PreservedDeveloperForm form() {
        var f = new PreservedDeveloperForm(); f.set(ADMISSION, root.resolve("admission").toString());
        f.set(ADMISSION_HASH, "a".repeat(64)); f.set(DATA, root.resolve("missing-data").toString());
        f.set(DATA_HASH, "b".repeat(64)); return f;
    }
    PreservedInstallView reviewed(UUID operation, UUID review) {
        return new PreservedInstallView(PreservedInstallView.State.REVIEWED, Optional.of(review), Optional.of(operation),
                Optional.empty(), Optional.empty(), Optional.of(root.resolve("receipt.json")), Optional.empty());
    }
    @Test void parsingDoesNotReadOrDownloadMissingDataAndDoesNotDefaultToEmbedded() {
        var selection = form().selection(); assertEquals(root.resolve("missing-data"), selection.data().path().orElseThrow());
        assertEquals("b".repeat(64), selection.data().expectedManifestSha256().orElseThrow()); assertTrue(selection.recovery().isEmpty());
        var empty = new PreservedDeveloperForm(); assertEquals(ADMISSION, assertThrows(PreservedDeveloperForm.InvalidField.class, empty::selection).field());
    }
    @Test void hashMustBeCompleteLowercaseAndNoWhitespace() {
        var f = form(); for (String bad : List.of("A".repeat(64), "a".repeat(63), " "+"a".repeat(63), "g".repeat(64))) {
            f.set(DATA_HASH, bad); assertEquals(DATA_HASH, assertThrows(PreservedDeveloperForm.InvalidField.class, f::selection).field());
        }
    }
    @Test void relativeTraversalAndControlPathsAreRejectedBeforeReview() {
        var f = form(); for (String bad : List.of("relative", "", root.resolve("a/../b").toString(), root+"\n")) {
            f.set(ADMISSION, bad); assertEquals(ADMISSION, assertThrows(PreservedDeveloperForm.InvalidField.class, f::selection).field());
        }
    }
    @Test void allFieldsHaveFiniteLimitsWithoutSilentTruncation() {
        var f = form(); for (var field : PreservedDeveloperForm.Field.values()) {
            assertThrows(PreservedDeveloperForm.InvalidField.class, () -> f.set(field, "x".repeat(field.limit()+1)));
            assertThrows(PreservedDeveloperForm.InvalidField.class, () -> f.set(field, null));
        }
    }
    @Test void recoveryRequiresExplicitTransactionPlanAndCanonicalPriorConsent() {
        var f = form(); f.mode(PreservedDeveloperForm.Mode.RESUME);
        assertEquals(TRANSACTION, assertThrows(PreservedDeveloperForm.InvalidField.class, f::selection).field());
        f.set(TRANSACTION, root.resolve("transaction").toString()); f.set(PLAN_HASH,"c".repeat(64)); f.set(APPROVAL,"1-1-1-1-1");
        assertEquals(APPROVAL, assertThrows(PreservedDeveloperForm.InvalidField.class, f::selection).field());
        UUID prior = UUID.randomUUID(); f.set(APPROVAL,prior.toString());
        assertEquals(RecoveryAction.RESUME, f.selection().recovery().orElseThrow().action());
        assertEquals(prior, f.selection().recovery().orElseThrow().priorApprovalId());
        f.mode(PreservedDeveloperForm.Mode.RESTORE); assertEquals(RecoveryAction.RESTORE,f.selection().recovery().orElseThrow().action());
        f.mode(PreservedDeveloperForm.Mode.APPLY); assertTrue(f.selection().recovery().isEmpty());
    }
    @Test void freshOperationIsRequiredAndTokenCanBeConsumedOnlyOnce() {
        var f = form(); UUID op=UUID.randomUUID(), review=UUID.randomUUID(); var v=reviewed(op,review);
        assertFalse(f.canConfirm(v,false)); f.reviewStarted(op);
        assertTrue(f.canConfirm(v,false)); assertTrue(f.consume(v,true).isEmpty());
        assertEquals(review,f.consume(v,false).orElseThrow()); assertTrue(f.consume(v,false).isEmpty());
    }
    @Test void editsEvenRestoredToSameTextRequireNewReview() {
        var f=form(); UUID op=UUID.randomUUID(); var v=reviewed(op,UUID.randomUUID()); f.reviewStarted(op);
        f.set(DATA_HASH,"c".repeat(64)); f.set(DATA_HASH,"b".repeat(64)); assertFalse(f.canConfirm(v,false));
        f.reviewStarted(op); f.mode(PreservedDeveloperForm.Mode.RESUME); f.mode(PreservedDeveloperForm.Mode.APPLY);
        assertFalse(f.canConfirm(v,false));
    }
    @Test void widgetRebuildDoesNotInvalidateUnchangedFields() {
        var f=form(); UUID op=UUID.randomUUID(); var v=reviewed(op,UUID.randomUUID()); f.reviewStarted(op);
        for(var field:PreservedDeveloperForm.Field.values()) f.set(field,f.get(field));
        f.mode(f.mode()); assertTrue(f.canConfirm(v,false));
    }
    @Test void staleTerminalOrReceiptlessResultsNeverEnableConfirmation() {
        var f=form(); UUID op=UUID.randomUUID(), review=UUID.randomUUID(); f.reviewStarted(op);
        assertFalse(f.canConfirm(reviewed(UUID.randomUUID(),review),false));
        for(var state:PreservedInstallView.State.values()) if(state!=PreservedInstallView.State.REVIEWED)
            assertFalse(f.canConfirm(new PreservedInstallView(state,Optional.of(review),Optional.of(op),Optional.empty(),Optional.empty(),Optional.of(root),Optional.empty()),false));
        assertFalse(f.canConfirm(new PreservedInstallView(PreservedInstallView.State.REVIEWED,Optional.of(review),Optional.of(op),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty()),false));
    }
    @Test void leavingOrRefreshingHistoryInvalidatesCurrentConsent() {
        var f=form(); UUID op=UUID.randomUUID(); var v=reviewed(op,UUID.randomUUID()); f.reviewStarted(op); f.invalidate();
        assertFalse(f.canConfirm(v,false)); assertTrue(f.consume(v,false).isEmpty());
    }
    @Test void immutableSessionIsReadableOnWorkerButWritableOnlyOnHostThread() throws Exception {
        var bridge=new PreservedHostSession(); Set<String> ids=new HashSet<>(Set.of("forge"));
        var snapshot=new InstallSession(ids,Set.of(root.resolve("runtime.jar")),false); bridge.publish(snapshot); ids.add("untrusted");
        var worker=Executors.newSingleThreadExecutor();
        try {
            assertEquals(Set.of("forge"),worker.submit(bridge::get).get(5,TimeUnit.SECONDS).loadedModIds());
            assertInstanceOf(IllegalStateException.class,assertThrows(ExecutionException.class,
                    ()->worker.submit(()->bridge.publish(snapshot)).get(5,TimeUnit.SECONDS)).getCause());
            assertInstanceOf(IllegalStateException.class,assertThrows(ExecutionException.class,
                    ()->worker.submit(bridge::invalidate).get(5,TimeUnit.SECONDS)).getCause());
            bridge.invalidate(); assertThrows(NullPointerException.class,bridge::get);
        } finally {worker.shutdownNow(); assertTrue(worker.awaitTermination(5,TimeUnit.SECONDS));}
    }
    @Test void hostStartsUnavailableAndPublishesWorldTransitionRatherThanReusingClosedState() {
        var bridge=new PreservedHostSession(); assertThrows(NullPointerException.class,bridge::get);
        bridge.publish(new InstallSession(Set.of(),Set.of(),false)); assertFalse(bridge.get().worldOpen());
        bridge.publish(new InstallSession(Set.of(),Set.of(),true)); assertTrue(bridge.get().worldOpen());
        bridge.invalidate(); assertThrows(NullPointerException.class,bridge::get);
    }
}
