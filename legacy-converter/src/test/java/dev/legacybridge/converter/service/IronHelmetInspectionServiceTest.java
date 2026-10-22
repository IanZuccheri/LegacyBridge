package dev.legacybridge.converter.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/** Read-only service integration: recognizing a constructor never authorizes conversion. */
class IronHelmetInspectionServiceTest {
    @TempDir Path directory;

    @Test
    void requestRequiresAnExactSelectionHashAndNormalizesTheInput() {
        var input=directory.resolve("source.jar");
        var data=MappingData.profile(directory.resolve("explicit-profile.json"));
        for(String invalid:List.of("", "missing", "a".repeat(63), "a".repeat(65), "g".repeat(64)))
            assertThrows(IllegalArgumentException.class,()->new IronHelmetInspectionRequest(input,invalid,data));
        assertThrows(NullPointerException.class,()->new IronHelmetInspectionRequest(input,null,data));
        var request=new IronHelmetInspectionRequest(input,"a".repeat(64),data);
        assertEquals(input.toAbsolutePath().normalize(),request.input());
        assertEquals(data,request.mappingData());
        assertEquals(request,new IronHelmetInspectionRequest(input,"A".repeat(64),data));
    }

    @Test
    void resultOwnsDeepImmutableEvidenceAndCannotEraseItsBlockers(){
        var rows=new ArrayList<Object>();rows.add(new TreeMap<>(Map.of("nativeValidation","NOT_RUN")));
        var nested=new TreeMap<String,Object>();nested.put("rows",rows);
        var evidence=new TreeMap<String,Object>();evidence.put("audit",nested);
        var blockers=new ArrayList<EngineError>();blockers.add(new EngineError("HELMET_BLOCKED","No semantic recipe","Keep original"));
        var result=new IronHelmetInspection("a".repeat(64),false,Optional.empty(),blockers,evidence);
        evidence.clear();nested.clear();rows.clear();blockers.clear();
        assertEquals(Map.of("audit",Map.of("rows",List.of(Map.of("nativeValidation","NOT_RUN")))),result.checks());
        assertEquals(1,result.blockers().size());
        assertThrows(UnsupportedOperationException.class,()->result.blockers().clear());
        assertDeeplyUnmodifiable(result.toTree());
        assertThrows(IllegalArgumentException.class,()->new IronHelmetInspection("a".repeat(64),false,Optional.empty(),List.of(),Map.of()));
        assertThrows(IllegalArgumentException.class,()->new IronHelmetInspection("a".repeat(64),true,Optional.empty(),result.blockers(),Map.of()));
    }

    @Test
    void includedScopesAndUnavailableDataCannotSupplyAnImplicitHelmetContract()throws Exception{
        try(var service=new LegacyEngineService()){
            for(var data:List.of(MappingData.builtinSimpleContent(),MappingData.builtinCohortPreview())){
                var result=await(service.inspectIronHelmet(new IronHelmetInspectionRequest(
                    directory.resolve("not-read.jar"),"a".repeat(64),data)));
                assertEquals(EngineOutcome.Status.FAILED,result.status());
                assertEquals("MAPPING_SCOPE_UNSUPPORTED",result.error().orElseThrow().code());
                assertTrue(result.value().isEmpty());
            }
            var unavailable=await(service.inspectIronHelmet(new IronHelmetInspectionRequest(
                directory.resolve("not-read.jar"),"a".repeat(64),MappingData.unavailable())));
            assertEquals(EngineOutcome.Status.FAILED,unavailable.status());
            assertEquals("MAPPING_DATA_REQUIRED",unavailable.error().orElseThrow().code());
            assertTrue(unavailable.value().isEmpty());
        }
        assertDirectoryEmpty();
    }

    @Test
    void queuedCancellationNeverLoadsMappingsAndLeavesTheSharedWorkerUsable()throws Exception{
        var started=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var service=new LegacyEngineService()){
            var running=service.submit(()->{
                started.countDown();
                assertTrue(release.await(15,TimeUnit.SECONDS));
                return EngineOutcome.completed("queue-holder");
            });
            try{
                assertTrue(started.await(10,TimeUnit.SECONDS));
                var queued=service.inspectIronHelmet(new IronHelmetInspectionRequest(
                    directory.resolve("unread-original.jar"),"a".repeat(64),
                    MappingData.profile(directory.resolve("unread-explicit-profile.json"))));
                assertTrue(queued.cancel());
                var cancelled=await(queued);
                assertEquals(EngineOutcome.Status.CANCELLED,cancelled.status());
                assertTrue(cancelled.value().isEmpty());
                assertEquals("CANCELLED",cancelled.error().orElseThrow().code());
                assertFalse(queued.cancel());
            }finally{release.countDown();}
            assertEquals("queue-holder",await(running).value().orElseThrow());
            assertEquals("next",await(service.submit(()->EngineOutcome.completed("next"))).value().orElseThrow());
        }finally{release.countDown();}
        assertDirectoryEmpty();
    }

    @Test
    @EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment",matches="true")
    void realOriginalProducesConstructorProofButRemainsBlockedAndUntouched()throws Exception{
        byte[] original=Files.readAllBytes(fixture());
        var input=directory.resolve("original.jar");Files.write(input,original);
        try(var service=new LegacyEngineService()){
            var job=service.inspectIronHelmet(new IronHelmetInspectionRequest(input,hash(original),MappingData.profile(profile())));
            var outcome=await(job);
            assertEquals(EngineOutcome.Status.BLOCKED,outcome.status(),()->outcome.toString());
            var inspection=outcome.value().orElseThrow();
            assertTrue(inspection.constructorRecognized(),()->inspection.toTree().toString());
            assertFalse(inspection.blockers().isEmpty(),()->inspection.toTree().toString());
            assertEquals(List.of("HELMET_EQUIPMENT_TYPE_CONTRACT_CHANGED","HELMET_USE_CONTRACT_CHANGED",
                "HELMET_INHERITED_BEHAVIOR_UNQUALIFIED"),
                inspection.blockers().stream().map(EngineError::code).toList(),()->inspection.toTree().toString());
            assertTrue(inspection.blockers().contains(outcome.error().orElseThrow()));
            var tree=inspection.toTree();
            assertEquals("UNSUPPORTED",tree.get("status"));
            for(String key:List.of("classesExecuted","candidateGenerated","publicationAllowed","installed"))
                assertEquals(false,tree.get(key),key);
            assertDeeplyUnmodifiable(tree);
            assertFalse(job.cancel());
        }
        assertArrayEquals(original,Files.readAllBytes(input));
        assertArrayEquals(original,Files.readAllBytes(fixture()));
        try(var paths=Files.list(directory)){assertEquals(List.of(input),paths.toList());}
        assertEquals(6,ConversionProfile.values().length,"Inspection must not become a public conversion profile");
    }

    @ParameterizedTest @EnumSource(ConversionProfile.class)
    @EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment",matches="true")
    void everyPublicProfileRefusesPlanningAndPublication(ConversionProfile selectedProfile)throws Exception{
        byte[] original=Files.readAllBytes(fixture());
        var input=directory.resolve("original.jar");Files.write(input,original);
        var output=directory.resolve("must-not-convert.jar");
        var request=new ConversionRequest(input,output,MappingData.profile(profile()),Optional.of(hash(original)),selectedProfile);
        try(var service=new LegacyEngineService()){
            var planned=await(service.plan(request));
            assertEquals(EngineOutcome.Status.BLOCKED,planned.status(),()->selectedProfile+": "+planned);
            assertFalse(planned.value().orElseThrow().convertible());
            assertTrue(planned.value().orElseThrow().candidateSha256().isEmpty());
            assertFalse(Files.exists(output));
            var converted=await(service.convert(request));
            assertEquals(EngineOutcome.Status.BLOCKED,converted.status(),()->selectedProfile+": "+converted);
            assertFalse(converted.value().orElseThrow().converted());
            assertFalse(Files.exists(output));
        }
        assertArrayEquals(original,Files.readAllBytes(input));
        assertArrayEquals(original,Files.readAllBytes(fixture()));
        try(var paths=Files.list(directory)){assertEquals(List.of(input),paths.toList());}
    }

    @Test
    @EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment",matches="true")
    void staleSelectedHashIsFailureNotAConstructorOnlyCompatibilityResult()throws Exception{
        byte[] original=Files.readAllBytes(fixture());
        var input=directory.resolve("original.jar");Files.write(input,original);
        assertNotEquals("0".repeat(64),hash(original));
        try(var service=new LegacyEngineService()){
            var outcome=await(service.inspectIronHelmet(new IronHelmetInspectionRequest(
                input,"0".repeat(64),MappingData.profile(profile()))));
            assertEquals(EngineOutcome.Status.FAILED,outcome.status(),()->outcome.toString());
            assertTrue(outcome.value().isEmpty());
            assertEquals("INPUT_CHANGED",outcome.error().orElseThrow().code());
        }
        assertArrayEquals(original,Files.readAllBytes(input));
        try(var paths=Files.list(directory)){assertEquals(List.of(input),paths.toList());}
    }

    private void assertDirectoryEmpty()throws Exception{
        try(var paths=Files.list(directory)){assertEquals(0,paths.count());}
    }
    @SuppressWarnings("unchecked")
    private static void assertDeeplyUnmodifiable(Object tree){
        if(tree instanceof Map<?,?> map){
            assertThrows(UnsupportedOperationException.class,()->((Map<Object,Object>)map).put("__mutation__",false));
            map.values().forEach(IronHelmetInspectionServiceTest::assertDeeplyUnmodifiable);
        }else if(tree instanceof List<?> list){
            assertThrows(UnsupportedOperationException.class,()->((List<Object>)list).add("__mutation__"));
            list.forEach(IronHelmetInspectionServiceTest::assertDeeplyUnmodifiable);
        }
    }
    private static <T> EngineOutcome<T> await(EngineJob<T> job)throws Exception{
        return job.completion().toCompletableFuture().get(90,TimeUnit.SECONDS);
    }
    private static Path profile(){return Path.of(System.getProperty("legacybridge.logicMappingProfile"));}
    private static Path fixture(){return Path.of(System.getProperty("legacybridge.fixtureRoot"),
        "iron-helmet-1.16.5/build/libs/legacybridge-iron-helmet-1.16.5-1.0.0.jar");}
    private static String hash(byte[] value)throws Exception{
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
