package dev.legacybridge.converter.cli;

import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.service.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

/** The CLI presents the same typed refusal; stdout is never conversion authority. */
class IronHelmetInspectionCliTest {
    @TempDir Path directory;

    @ParameterizedTest @MethodSource("invalidArguments")
    void invalidFlagsCannotFallbackToEnvironmentOrWriteAReport(String[] args)throws Exception{
        var result=run(args);
        assertEquals(ExitCode.INVALID_USAGE.value(),result.code(),result.errors());
        assertEquals("",result.json());assertFalse(result.errors().isBlank());
        try(var paths=Files.list(directory)){assertEquals(0,paths.count());}
    }

    static Stream<Arguments> invalidArguments(){
        String sha="a".repeat(64);
        return Stream.of(
            new String[]{"inspect-iron-helmet"},
            new String[]{"inspect-iron-helmet","original.jar"},
            new String[]{"inspect-iron-helmet","original.jar","--input-sha256",sha},
            new String[]{"inspect-iron-helmet","original.jar","--mapping-profile","explicit.json"},
            new String[]{"inspect-iron-helmet","original.jar","--input-sha256",sha,"--mapping-bundle","explicit"},
            new String[]{"inspect-iron-helmet","original.jar","--input-sha256",sha,"--output","converted.jar"},
            new String[]{"inspect-iron-helmet","original.jar","--input-sha256",sha,"--input-sha256",sha},
            new String[]{"inspect-iron-helmet","original.jar","--input-sha256","bad","--mapping-profile","explicit.json"},
            new String[]{"inspect-iron-helmet","original.jar","--input-sha256",sha,"--mapping-profile",""},
            new String[]{"inspect-iron-helmet","","--input-sha256",sha,"--mapping-profile","explicit.json"},
            new String[]{"inspect-iron-helmet","original.jar","--input-sha256",sha,"--mapping-profile","explicit.json","--output","converted.jar"},
            new String[]{"inspect-iron-helmet","original.jar","--input-sha256",sha,"--mapping-profile"}
        ).map(args->Arguments.of((Object)args));
    }

    @Test
    void explicitlyMissingMappingDataCannotFallbackToEnvironment()throws Exception{
        var result=run("inspect-iron-helmet",directory.resolve("unread-original.jar").toString(),
            "--input-sha256","a".repeat(64),"--mapping-profile",directory.resolve("missing.json").toString());
        assertEquals(ExitCode.INPUT_REJECTED.value(),result.code(),result.errors());
        assertEquals("",result.json());assertFalse(result.errors().isBlank());
        try(var paths=Files.list(directory)){assertEquals(0,paths.count());}
    }

    @Test
    @EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment",matches="true")
    void actualFixtureJsonIsIdenticalToTypedServiceButBothRefuseConversion()throws Exception{
        byte[] original=Files.readAllBytes(fixture());
        var input=directory.resolve("original.jar");Files.write(input,original);
        String sha=hash(original);
        var cli=run("inspect-iron-helmet",input.toString(),"--input-sha256",sha,"--mapping-profile",profile().toString());
        assertEquals(ExitCode.INPUT_REJECTED.value(),cli.code(),cli.errors()+cli.json());
        var tree=(Map<?,?>)BoundedJsonParser.parse(cli.json());
        assertEquals("UNSUPPORTED",tree.get("status"));
        assertEquals(true,tree.get("constructorRecognized"),cli.json());
        assertEquals(List.of("HELMET_EQUIPMENT_TYPE_CONTRACT_CHANGED","HELMET_USE_CONTRACT_CHANGED",
            "HELMET_INHERITED_BEHAVIOR_UNQUALIFIED"),
            ((List<?>)tree.get("blockers")).stream().map(value->((Map<?,?>)value).get("code")).toList(),cli.json());
        for(String key:List.of("classesExecuted","candidateGenerated","publicationAllowed","installed"))
            assertEquals(false,tree.get(key),key);
        try(var service=new LegacyEngineService()){
            var result=service.inspectIronHelmet(new IronHelmetInspectionRequest(input,sha,MappingData.profile(profile())))
                .completion().toCompletableFuture().get(90,TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.BLOCKED,result.status(),()->result.toString());
            assertTrue(result.value().orElseThrow().constructorRecognized(),()->result.value().orElseThrow().toTree().toString());
            assertEquals(new CanonicalReportWriter().toJsonTree(result.value().orElseThrow().toTree()),cli.json());
        }
        // Option ordering changes presentation only, never the source selection or proof.
        var reordered=run("inspect-iron-helmet",input.toString(),"--mapping-profile",profile().toString(),"--input-sha256",sha);
        assertEquals(cli,reordered);
        assertArrayEquals(original,Files.readAllBytes(input));
        assertArrayEquals(original,Files.readAllBytes(fixture()));
        try(var paths=Files.list(directory)){assertEquals(List.of(input),paths.toList());}
    }

    @Test
    @EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment",matches="true")
    void staleHashPrintsFailureInsteadOfAPartialProof()throws Exception{
        byte[] original=Files.readAllBytes(fixture());
        var result=run("inspect-iron-helmet",fixture().toString(),"--input-sha256","0".repeat(64),
            "--mapping-profile",profile().toString());
        assertEquals(ExitCode.INPUT_REJECTED.value(),result.code(),result.errors());
        assertEquals("",result.json());assertFalse(result.errors().isBlank());
        assertArrayEquals(original,Files.readAllBytes(fixture()));
        try(var paths=Files.list(directory)){assertEquals(0,paths.count());}
    }

    private static Result run(String... args){
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();
        int code=LegacyBridgeCli.run(args,new PrintStream(out,true,StandardCharsets.UTF_8),
            new PrintStream(err,true,StandardCharsets.UTF_8),
            Map.of("LEGACYBRIDGE_MAPPING_BUNDLE","must-not-supply-implicit-iron-helmet-data"));
        return new Result(code,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));
    }
    private record Result(int code,String json,String errors){}
    private static Path profile(){return Path.of(System.getProperty("legacybridge.logicMappingProfile"));}
    private static Path fixture(){return Path.of(System.getProperty("legacybridge.fixtureRoot"),
        "iron-helmet-1.16.5/build/libs/legacybridge-iron-helmet-1.16.5-1.0.0.jar");}
    private static String hash(byte[] value)throws Exception{
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
