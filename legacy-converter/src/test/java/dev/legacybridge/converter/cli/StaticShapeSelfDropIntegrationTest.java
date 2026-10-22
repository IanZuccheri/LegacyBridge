package dev.legacybridge.converter.cli;

import dev.legacybridge.converter.conversion.*;
import dev.legacybridge.converter.mapping.source.*;
import dev.legacybridge.converter.operation.*;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.service.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Separate authored self-drop resource subvariant; never loads/executed any original class. */
@EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment", matches="true")
class StaticShapeSelfDropIntegrationTest {
    private static final String MOD="legacybridge_static_shape_drop";
    private static final String LOOT="data/"+MOD+"/loot_tables/blocks/bridge_block.json";
    @TempDir Path directory;
    private static MappingInputs mappings;
    private static Path profile(){return Path.of(System.getProperty("legacybridge.logicMappingProfile"));}
    private static MappingInputs inputs()throws Exception{if(mappings==null)mappings=new MappingInputsLoader().load(profile());return mappings;}
    @AfterAll static void release(){mappings=null;}
    private static Path fixture(){return Path.of(System.getProperty("legacybridge.fixtureRoot"),
        "static-shape-drop-1.16.5/build/libs/legacybridge-static-shape-drop-1.16.5-1.0.0.jar");}

    @Test void authoredLootBytesAndContractSurviveCheckedReadbackWithoutExport()throws Exception{
        var original=Files.readAllBytes(fixture());var out=directory.resolve("absent.jar");
        var result=LogicPreservingConverter.planStaticShapeBlock(fixture(),out,inputs());
        assertTrue(result.validated(),()->result.report().toString());assertFalse(result.publicationAllowed());
        assertTrue(result.candidateSha256().isPresent());assertFalse(Files.exists(out));assertArrayEquals(original,Files.readAllBytes(fixture()));
        var report=result.report();assertEquals("STATIC_BOTTOM_HALF_SHAPE_SELF_DROP",report.get("staticShapeVariant"));
        assertEquals("static-shape-block-candidate-v1",report.get("candidateIdentity"));
        assertEquals(2,report.get("preservedClassCount"));assertEquals(8,report.get("preservedMethodCount"));
        assertEquals(0,report.get("replacedClassCount"));assertEquals(false,report.get("bytecodeExecuted"));assertEquals("NOT_RUN",report.get("runtimeValidation"));
        var contract=(Map<?,?>)report.get("staticShapeLootContract");assertNotNull(contract);
        assertEquals(contract,report.get("reopenedStaticShapeLootContract"));assertEquals(LOOT,contract.get("path"));
        assertEquals(hash(read().get(LOOT)),contract.get("sha256"));assertEquals(MOD+":bridge_block",contract.get("item"));
        assertEquals(report.get("sourceStaticShapeBlockContract"),report.get("staticShapeBlockContract"));
        assertEquals(report.get("staticShapeBlockContract"),report.get("reopenedStaticShapeBlockContract"));
        assertEquals("PASSED_SCOPED",((Map<?,?>)report.get("serializedOfflineLinkage")).get("status"));
        assertEquals(true,((Map<?,?>)report.get("candidateArchiveValidation")).get("scratchDeleted"));
        int lootEntries=0;
        for(var raw:(List<?>)((Map<?,?>)report.get("artifactPlan")).get("entries")){
            var e=(Map<?,?>)raw;String name=(String)e.get("path");
            if(name.equals(LOOT)){lootEntries++;assertEquals("PRESERVE_BYTES",e.get("disposition"));
                assertEquals(hash(read().get(LOOT)),e.get("sourceSha256"));assertEquals(e.get("sourceSha256"),e.get("targetSha256"));}
        }
        assertEquals(1,lootEntries);assertEquals(result,LogicPreservingConverter.planStaticShapeBlock(fixture(),out,inputs()));
        System.out.println("STATIC_SHAPE_SELF_DROP_CANDIDATE "+new CanonicalReportWriter().toJsonTree(report));
    }

    @Test void optionalTableNeverGetsInventedForNoLootInput()throws Exception{
        var entries=read();assertNotNull(entries.remove(LOOT));var path=directory.resolve("no-loot.jar");write(path,entries);
        var result=LogicPreservingConverter.planStaticShapeBlock(path,directory.resolve("absent.jar"),inputs());
        assertTrue(result.validated(),()->result.report().toString());assertEquals("STATIC_BOTTOM_HALF_SHAPE",result.report().get("staticShapeVariant"));
        for(var raw:(List<?>)((Map<?,?>)result.report().get("artifactPlan")).get("entries"))
            assertFalse(((String)((Map<?,?>)raw).get("path")).contains("/loot_tables/"),"No generated or unowned loot table");
    }

    @Test void serviceParityAndOldPublicProfilesRemainClosed()throws Exception{
        var out=directory.resolve("absent.jar");var direct=LogicPreservingConverter.planStaticShapeBlock(fixture(),out,inputs());
        try(var service=new LegacyEngineService()){
            var request=new ConversionRequest(fixture(),out,MappingData.profile(profile()),
                Optional.of(hash(Files.readAllBytes(fixture()))),ConversionProfile.LOGIC_PRESERVING_V1);
            var result=service.planStaticShapeBlock(request).completion().toCompletableFuture().get(90,TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED,result.status(),()->result.toString());assertEquals(direct,result.value().orElseThrow());
        }
        assertFalse(LogicPreservingConverter.planPlainBlock(fixture(),out,inputs()).validated());
        assertFalse(LogicPreservingConverter.planHorizontalBlock(fixture(),out,inputs()).validated());
        for(var p:ConversionProfile.values()){
            if(p==ConversionProfile.SIMPLE_CONTENT_V1||p==ConversionProfile.DATAPACK_RECIPES_V1)continue;
            assertFalse(new LogicPreservingConverter(p).convert(fixture(),out,inputs()).converted(),p.toString());
        }
        assertFalse(Files.exists(out));
    }

    @ParameterizedTest @ValueSource(strings={"wrongType","missingType","unknownRoot","randomSequence","tableFunctions","zeroPools","multiplePools",
        "wrongRolls","stringRolls","explicitBonus","poolFunctions","poolName","emptyEntries","multipleEntries","foreignItem","missingLocalItem",
        "entryType","entryWeight","entryQuality","entryFunctions","entryConditions","missingConditions","wrongCondition","conditionExtraField",
        "multipleConditions","duplicateKey","truncated","trailing","nonUtf8","extraLoot","foreignLoot","globalLootModifier","unknownLootResource"})
    void unknownLootStructureAndReferencesAbortWholeCandidate(String mutation)throws Exception{
        var entries=read();String originalText=new String(entries.get(LOOT),StandardCharsets.UTF_8);String text=originalText;
        boolean differentEntry=false;
        switch(mutation){
            case "wrongType"->text=text.replace("minecraft:block","minecraft:entity");
            case "missingType"->text=text.replace("\"type\": \"minecraft:block\",","");
            case "unknownRoot"->text=text.replace("\"pools\":","\"unreviewed\":true,\"pools\":");
            case "randomSequence"->text=text.replace("\"pools\":","\"random_sequence\":\""+MOD+":blocks/bridge_block\",\"pools\":");
            case "tableFunctions"->text=text.replace("\"pools\":","\"functions\":[],\"pools\":");
            case "zeroPools"->text="{\"type\":\"minecraft:block\",\"pools\":[]}";
            case "multiplePools"->text=text.replace("\"pools\": [","\"pools\": [{\"rolls\":1,\"entries\":[]},");
            case "wrongRolls"->text=text.replace("\"rolls\": 1","\"rolls\": 2");
            case "stringRolls"->text=text.replace("\"rolls\": 1","\"rolls\": \"1\"");
            case "explicitBonus"->text=text.replace("\"rolls\": 1","\"rolls\": 1,\"bonus_rolls\":0");
            case "poolFunctions"->text=text.replace("\"rolls\": 1","\"rolls\": 1,\"functions\":[]");
            case "poolName"->text=text.replace("\"rolls\": 1","\"rolls\": 1,\"name\":\"unreviewed\"");
            case "emptyEntries"->text="{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":[],\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}]}]}";
            case "multipleEntries"->text=text.replace("\"entries\": [","\"entries\": [{\"type\":\"minecraft:item\",\"name\":\""+MOD+":bridge_block\"},");
            case "foreignItem"->text=text.replace(MOD+":bridge_block","minecraft:stone");
            case "missingLocalItem"->text=text.replace(MOD+":bridge_block",MOD+":not_registered");
            case "entryType"->text=text.replace("minecraft:item","minecraft:loot_table");
            case "entryWeight"->text=text.replace("\"name\":","\"weight\":1,\"name\":");
            case "entryQuality"->text=text.replace("\"name\":","\"quality\":0,\"name\":");
            case "entryFunctions"->text=text.replace("\"name\":","\"functions\":[],\"name\":");
            case "entryConditions"->text=text.replace("\"name\":","\"conditions\":[],\"name\":");
            case "missingConditions"->text="{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\""+MOD+":bridge_block\"}]}]}";
            case "wrongCondition"->text=text.replace("minecraft:survives_explosion","minecraft:random_chance");
            case "conditionExtraField"->text=text.replace("\"condition\":","\"unreviewed\":true,\"condition\":");
            case "multipleConditions"->text=text.replace("\"conditions\": [","\"conditions\": [{\"condition\":\"minecraft:survives_explosion\"},");
            case "duplicateKey"->text=text.replace("\"rolls\": 1","\"rolls\": 1,\"rolls\":2");
            case "truncated"->text=text.substring(0,text.length()-3);
            case "trailing"->text=text+"{}";
            case "nonUtf8"->{entries.put(LOOT,new byte[]{(byte)0xc3,(byte)0x28});differentEntry=true;}
            case "extraLoot"->{entries.put("data/"+MOD+"/loot_tables/blocks/unregistered.json",entries.get(LOOT));differentEntry=true;}
            case "foreignLoot"->{entries.put("data/minecraft/loot_tables/blocks/stone.json",entries.get(LOOT));differentEntry=true;}
            case "globalLootModifier"->{entries.put("data/forge/loot_modifiers/global_loot_modifiers.json","{\"replace\":false,\"entries\":[]}".getBytes(StandardCharsets.UTF_8));differentEntry=true;}
            case "unknownLootResource"->{entries.put("data/"+MOD+"/loot_tables/custom.nbt",new byte[]{1,2,3});differentEntry=true;}
            default->fail(mutation);
        }
        if(!differentEntry){assertNotEquals(originalText,text,"Mutation must change resource: "+mutation);entries.put(LOOT,text.getBytes(StandardCharsets.UTF_8));}
        var path=directory.resolve(mutation+".jar");write(path,entries);var original=Files.readAllBytes(path);var output=directory.resolve("absent.jar");
        var result=LogicPreservingConverter.planStaticShapeBlock(path,output,inputs());assertFalse(result.validated(),mutation);
        assertTrue(result.candidateSha256().isEmpty());assertFalse(result.publicationAllowed());assertFalse(Files.exists(output));
        assertArrayEquals(original,Files.readAllBytes(path));assertEquals(false,result.report().get("transformationsCommitted"));
    }

    @ParameterizedTest @ValueSource(strings={"SERIALIZATION","PREPARED"})
    void selfDropVariantDoesNotBypassCancellation(String point)throws Exception{
        inputs();var ref=new AtomicReference<OperationControl>();var control=new OperationControl(OperationLimits.defaults(),e->{
            if(e.stage().name().equals(point))ref.get().requestCancel();
        });ref.set(control);var output=directory.resolve("absent.jar");
        try(var scope=control.enter()){assertThrows(OperationStoppedException.class,()->LogicPreservingConverter.planStaticShapeBlock(fixture(),output,inputs()));}
        assertFalse(Files.exists(output));
    }

    private static Map<String,byte[]> read()throws Exception{
        var entries=new TreeMap<String,byte[]>();try(var zip=new ZipFile(fixture().toFile())){
            for(var e:Collections.list(zip.entries()))if(!e.isDirectory())try(var input=zip.getInputStream(e)){entries.put(e.getName(),input.readAllBytes());}
        }return entries;
    }
    private static void write(Path path,Map<String,byte[]> entries)throws Exception{
        try(var zip=new ZipOutputStream(Files.newOutputStream(path))){for(var e:entries.entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue());zip.closeEntry();}}
    }
    private static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
}
