package dev.legacybridge.product.controller;

import dev.legacybridge.converter.service.*;
import dev.legacybridge.product.install.PreservedAdmissionResult;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static dev.legacybridge.product.controller.PreservedPreparationForm.Field.*;

class PreservedPreparationFormTest {
    @TempDir Path root;
    PreservedPreparationForm form() {
        var f=new PreservedPreparationForm(); f.set(ORIGINAL,root.resolve("absent-original.jar").toString()); f.set(ORIGINAL_HASH,"a".repeat(64));
        f.set(DATA,root.resolve("absent-bundle").toString()); f.set(DATA_HASH,"b".repeat(64)); return f;
    }
    PreservedPreparationView ready(UUID operation) {
        var inspection=new PreservedArtifactInspection(true,"a".repeat(64),"c".repeat(64),Optional.of(new PreservedArtifactInspection.Identity("fixture","1")),1,1,List.of(),Map.of());
        var admission=new PreservedAdmissionResult(PreservedAdmissionResult.State.RECORDED_NOT_INSTALLABLE,Optional.of(root),Optional.of("d".repeat(64)),Optional.of(inspection),Optional.empty());
        return new PreservedPreparationView(PreservedPreparationView.State.READY,Optional.of(operation),Optional.of(new PreservedPreparationRequest.Candidate(root.resolve("candidate.jar"),"c".repeat(64))),
                Optional.of(admission),Map.of("preparation",root.resolve("report.json")),Optional.empty(),Optional.empty());
    }
    @Test void defaultConvertsWithPinnedFullDataWithoutFilesystemLookup() {
        var f=form(); assertTrue(f.request().candidate().isEmpty()); assertEquals(MappingData.Kind.PORTABLE_BUNDLE,f.request().data().kind());
        assertEquals("b".repeat(64),f.request().data().expectedManifestSha256().orElseThrow());
    }
    @Test void existingCandidateModeRequiresPathAndHashAndRefusesSameOriginal() {
        var f=form(); f.convert(false); assertEquals(CANDIDATE,assertThrows(PreservedPreparationForm.InvalidField.class,f::request).field());
        f.set(CANDIDATE,f.get(ORIGINAL)); assertThrows(PreservedPreparationForm.InvalidField.class,f::request);
        f.set(CANDIDATE,root.resolve("candidate.jar").toString()); f.set(CANDIDATE_HASH,"c".repeat(64)); assertTrue(f.request().candidate().isPresent());
    }
    @Test void inputBoundsAndMalformedHashesFailWithoutTruncating() {
        var f=form(); for(var field:PreservedPreparationForm.Field.values()) assertThrows(PreservedPreparationForm.InvalidField.class,()->f.set(field,"x".repeat(field.limit()+1)));
        f.set(ORIGINAL_HASH,"A".repeat(64)); assertEquals(ORIGINAL_HASH,assertThrows(PreservedPreparationForm.InvalidField.class,f::request).field());
        f.set(ORIGINAL_HASH,"a".repeat(64)); f.set(DATA,"relative"); assertEquals(DATA,assertThrows(PreservedPreparationForm.InvalidField.class,f::request).field());
    }
    @Test void includedOrUnpinnedDataNeverSatisfyPreparationContract() {
        assertThrows(IllegalArgumentException.class,()->new PreservedPreparationRequest(root.resolve("o.jar"),"a".repeat(64),Optional.empty(),MappingData.builtinSimpleContent()));
        assertThrows(IllegalArgumentException.class,()->new PreservedPreparationRequest(root.resolve("o.jar"),"a".repeat(64),Optional.empty(),MappingData.bundle(root)));
    }
    @Test void editedOrSwitchedFormCannotHandOffOldReadinessEvenAfterReverting() {
        var f=form(); UUID op=UUID.randomUUID(); var v=ready(op); f.submitted(op); assertTrue(f.canUse(v,false));
        f.set(DATA_HASH,"c".repeat(64)); f.set(DATA_HASH,"b".repeat(64)); assertFalse(f.canUse(v,false));
        f.submitted(op); f.convert(false); f.convert(true); assertFalse(f.canUse(v,false));
    }
    @Test void matchingResultIsSingleUseAndNeverConfersInstallOrCloseAuthority() {
        var f=form(); UUID op=UUID.randomUUID(); var v=ready(op); assertFalse(f.canUse(v,false)); f.submitted(op);
        assertTrue(f.consume(v,true).isEmpty()); assertFalse(f.canUse(ready(UUID.randomUUID()),false));
        assertEquals(op,f.consume(v,false).orElseThrow()); assertTrue(f.consume(v,false).isEmpty());
        assertFalse(v.installationAllowed()); assertFalse(v.safeToClose()); assertEquals("NOT_OBSERVED",v.activation());
    }
    @Test void identicalWidgetRebuildPreservesSelectionButIncompleteViewCannotClaimReady() {
        var f=form(); UUID op=UUID.randomUUID(); f.submitted(op); for(var field:PreservedPreparationForm.Field.values()) f.set(field,f.get(field));
        assertTrue(f.canUse(ready(op),false));
        assertThrows(IllegalArgumentException.class,()->new PreservedPreparationView(PreservedPreparationView.State.READY,Optional.of(op),Optional.empty(),Optional.empty(),Map.of(),Optional.empty(),Optional.empty()));
    }
}
