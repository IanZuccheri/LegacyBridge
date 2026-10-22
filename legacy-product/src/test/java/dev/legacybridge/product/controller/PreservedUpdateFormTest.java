package dev.legacybridge.product.controller;

import dev.legacybridge.product.install.PreservedUpdatePreview;
import dev.legacybridge.product.install.RecoveryAction;
import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.converter.service.EngineError;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static dev.legacybridge.product.controller.PreservedDeveloperForm.Field.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual native form contract, exercised without loading Minecraft or opening any selected file. */
class PreservedUpdateFormTest {
    @TempDir Path root;
    PreservedDeveloperForm form() {
        var f=new PreservedDeveloperForm(); f.kind(PreservedDeveloperForm.Kind.UPDATE);
        f.set(ADMISSION,root.resolve("missing-admission").toString()); f.set(ADMISSION_HASH,"a".repeat(64));
        f.set(DATA,root.resolve("missing-data").toString()); f.set(DATA_HASH,"b".repeat(64));
        f.set(PREVIOUS_INSTALLATION,root.resolve("missing-predecessor").toString()); f.set(PREVIOUS_PLAN_HASH,"c".repeat(64));
        return f;
    }
    PreservedUpdateView view(PreservedDeveloperForm f, UUID operation, UUID review) {
        var observation=new PreservedInstallView(PreservedInstallView.State.REVIEWED,Optional.of(review),Optional.of(operation),
                Optional.empty(),Optional.empty(),Optional.of(root.resolve("missing-receipt")),Optional.empty());
        var identity=new PreservedUpdatePreview.Identity("fixture","1.0.0","1.0.1","d".repeat(64),"e".repeat(64));
        return new PreservedUpdateView(Optional.of(f.updateSelection()),Optional.of(new PreservedUpdatePreview(Optional.of(identity),Optional.empty())),observation);
    }
    @Test void explicitPredecessorIsSeparateFromIncomingAdmissionAndNeverReadOnUiThread() {
        var f=form(); var s=f.updateSelection();
        assertEquals(root.resolve("missing-predecessor"),s.previousInstallation());
        assertEquals("c".repeat(64),s.previousPlanSha256()); assertEquals(f.selection(),s.replacement());
        assertTrue(s.replacement().recovery().isEmpty());
        f.kind(PreservedDeveloperForm.Kind.ADD); assertThrows(IllegalStateException.class,f::updateSelection);
    }
    @Test void predecessorPathsAndPinsAreValidatedWithoutFilesystemFallback() {
        var f=form();
        for(String value:List.of("","relative",root.resolve("a/../b").toString(),root+"\n")) {
            f.set(PREVIOUS_INSTALLATION,value);
            assertEquals(PREVIOUS_INSTALLATION,assertThrows(PreservedDeveloperForm.InvalidField.class,f::updateSelection).field());
        }
        f=form();
        for(String value:List.of("","A".repeat(64),"a".repeat(63),"g".repeat(64))) {
            f.set(PREVIOUS_PLAN_HASH,value);
            assertEquals(PREVIOUS_PLAN_HASH,assertThrows(PreservedDeveloperForm.InvalidField.class,f::updateSelection).field());
        }
    }
    @Test void updateConsumesReviewNotOperationAndOnlyOnce() {
        var f=form(); UUID op=UUID.randomUUID(),review=UUID.randomUUID(); var v=view(f,op,review);
        assertFalse(f.canConfirm(v,false)); f.reviewStarted(op);
        assertFalse(f.canConfirm(v.observation(),false)); assertTrue(f.consume(v.observation(),false).isEmpty());
        assertTrue(f.consume(v,true).isEmpty()); assertEquals(review,f.consume(v,false).orElseThrow());
        assertNotEquals(op,review); assertTrue(f.consume(v,false).isEmpty());
    }
    @Test void wrongKindOrChangingKindBackNeverReusesConsent() {
        var f=form(); UUID op=UUID.randomUUID(); var v=view(f,op,UUID.randomUUID()); f.reviewStarted(op);
        f.kind(PreservedDeveloperForm.Kind.ADD); assertFalse(f.canConfirm(v,false));
        f.kind(PreservedDeveloperForm.Kind.UPDATE); assertFalse(f.canConfirm(v,false));
        f.reviewStarted(op); assertTrue(f.canConfirm(v,false));
    }
    @ParameterizedTest @EnumSource(PreservedDeveloperForm.Field.class)
    void everyFieldEditEvenRestoredInvalidatesTheReview(PreservedDeveloperForm.Field field) {
        var f=form(); UUID op=UUID.randomUUID(); var v=view(f,op,UUID.randomUUID()); f.reviewStarted(op);
        String old=f.get(field); f.set(field,"changed"); f.set(field,old);
        assertTrue(f.consume(v,false).isEmpty());
    }
    @Test void resizeAndUnchangedModeOrFieldsPreserveCurrentReview() {
        var f=form(); UUID op=UUID.randomUUID(); var v=view(f,op,UUID.randomUUID()); f.reviewStarted(op);
        for(var field:PreservedDeveloperForm.Field.values()) f.set(field,f.get(field));
        f.kind(f.kind()); f.mode(f.mode()); assertTrue(f.canConfirm(v,false));
        f.mode(PreservedDeveloperForm.Mode.RESUME); f.mode(PreservedDeveloperForm.Mode.APPLY); assertFalse(f.canConfirm(v,false));
    }
    @Test void mismatchedSelectionMissingOrFailedPreviewCannotEnableUpdate() {
        var f=form(); UUID op=UUID.randomUUID(); var v=view(f,op,UUID.randomUUID()); f.reviewStarted(op);
        assertFalse(f.canConfirm(new PreservedUpdateView(Optional.empty(),v.preview(),v.observation()),false));
        assertFalse(f.canConfirm(new PreservedUpdateView(v.selection(),Optional.empty(),v.observation()),false));
        assertFalse(f.canConfirm(new PreservedUpdateView(v.selection(),Optional.of(new PreservedUpdatePreview(Optional.empty(),Optional.empty())),v.observation()),false));
        var other=new PreservedUpdateSelection(f.selection(),root.resolve("another-predecessor"),"c".repeat(64));
        assertFalse(f.canConfirm(new PreservedUpdateView(Optional.of(other),v.preview(),v.observation()),false));
        assertFalse(f.canConfirm(view(f,UUID.randomUUID(),UUID.randomUUID()),false));
    }
    @Test void terminalOrReceiptlessObservationsNeverAuthorizeUpdate() {
        var f=form(); UUID op=UUID.randomUUID(); var v=view(f,op,UUID.randomUUID()); f.reviewStarted(op);
        for(var state:PreservedInstallView.State.values()) {
            var observation=new PreservedInstallView(state,v.observation().reviewId(),Optional.of(op),Optional.empty(),Optional.empty(),
                    state==PreservedInstallView.State.REVIEWED?Optional.empty():Optional.of(root),Optional.empty());
            assertFalse(f.canConfirm(new PreservedUpdateView(v.selection(),v.preview(),observation),false),state.toString());
        }
    }
    @ParameterizedTest @EnumSource(value=PreservedDeveloperForm.Mode.class,names={"RESUME","RESTORE"})
    void recoveryHasDistinctTransactionAndFreshConsentWithoutInventingVersionPreview(PreservedDeveloperForm.Mode mode) {
        var f=form(); f.mode(mode); UUID prior=UUID.randomUUID();
        f.set(TRANSACTION,root.resolve("update-transaction").toString()); f.set(PLAN_HASH,"f".repeat(64)); f.set(APPROVAL,prior.toString());
        var s=f.updateSelection(); var r=s.replacement().recovery().orElseThrow();
        assertNotEquals(s.previousInstallation(),r.transaction()); assertEquals(prior,r.priorApprovalId());
        assertEquals(mode==PreservedDeveloperForm.Mode.RESUME?RecoveryAction.RESUME:RecoveryAction.RESTORE,r.action());
        UUID op=UUID.randomUUID(),review=UUID.randomUUID(); var v=view(f,op,review);
        v=new PreservedUpdateView(v.selection(),Optional.empty(),v.observation());
        assertFalse(f.canConfirm(v,false)); f.reviewStarted(op); assertEquals(review,f.consume(v,false).orElseThrow());
        assertNotEquals(prior,review);
    }
    @Test void historyRefreshAndClosingInvalidateRatherThanRehydrate() {
        var f=form(); UUID op=UUID.randomUUID(); var v=view(f,op,UUID.randomUUID()); f.reviewStarted(op); f.invalidate();
        assertFalse(f.canConfirm(v,false)); assertTrue(f.consume(v,false).isEmpty());
    }
    @Test void previewIdentityWithErrorNeverAuthorizesAnUpdate() {
        var f=form(); UUID op=UUID.randomUUID(); var v=view(f,op,UUID.randomUUID()); f.reviewStarted(op);
        var failed=new PreservedUpdatePreview(v.preview().orElseThrow().identity(),Optional.of(new EngineError("CHANGED", "Changed", "Review again")));
        assertFalse(f.canConfirm(new PreservedUpdateView(v.selection(),Optional.of(failed),v.observation()),false));
    }
    @Test void dynamicFieldsKindsAndUpdateMessagesExistInBothLocales() throws Exception {
        for(String locale:List.of("en_us","pt_br")) {
            Map<?,?> translations;
            try(var stream=getClass().getResourceAsStream("/assets/legacybridge_runtime/lang/"+locale+".json")) {
                assertNotNull(stream); translations=(Map<?,?>)BoundedJsonParser.parse(new String(stream.readAllBytes(),StandardCharsets.UTF_8));
            }
            List<String> keys=new ArrayList<>(List.of("PREDECESSOR","last_observation_kind","update.apply","update.scope","update.confirm_title",
                    "update.confirm_warning","update.recovery_warning","update.recovery_observation","update.comparison","update.previous_hash","update.replacement_hash"));
            for(var field:PreservedDeveloperForm.Field.values()) keys.add(field.name());
            for(var kind:PreservedDeveloperForm.Kind.values()) keys.add("kind."+kind.name());
            for(var mode:PreservedDeveloperForm.Mode.values()) keys.add(mode.name());
            for(String key:keys) assertTrue(translations.get("legacybridge.preserved."+key) instanceof String value&&!value.isBlank(),locale+" "+key);
            String consent=String.format(Locale.ROOT,translations.get("legacybridge.preserved.update.confirm_warning").toString(),"fixture","1.0.0","1.0.1","review-id");
            for(String expected:List.of("fixture","1.0.0","1.0.1","review-id","launcher")) assertTrue(consent.contains(expected),locale+" "+expected);
        }
    }
}
