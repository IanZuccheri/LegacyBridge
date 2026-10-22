package dev.legacybridge.corpus.relink;

import dev.legacybridge.converter.report.CanonicalReportWriter;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static dev.legacybridge.corpus.relink.RelinkFiles.*;

/** Explicit local published-library rebuild. Executes only the reviewed library/engine, never fixture mod classes. */
class SrgRelinkEnvironmentTest {
    @TempDir Path temp;
    @Test void rebuildReplaceAndExerciseActualPublicEngineWithoutMinecraft() throws Exception {
        String selected=System.getProperty("legacybridge.relinkProduct", "");
        assumeTrue(!selected.isBlank(), "Explicit local relinking artifacts not selected; no downloads");
        Path product=Path.of(selected); Path annotations=Path.of(System.getProperty("legacybridge.relinkAnnotations"));
        Path libraries=Path.of(System.getProperty("legacybridge.relinkCliLibraries")); Path fixture=Path.of(System.getProperty("legacybridge.relinkFixture"));
        byte[] original=read(product,MAX_BYTES); String fingerprint=hash(original); String originalFixture=hash(read(fixture,1<<20));
        Path kit=temp.resolve("kit"); var prepared=SrgRelinkTool.prepare(product,fingerprint,kit);
        assertEquals("SOURCE_KIT_PREPARED",prepared.get("status"));
        Path baseline=temp.resolve("baseline"),repeat=temp.resolve("repeat");
        var first=SrgRelinkTool.build(kit,annotations,baseline); var again=SrgRelinkTool.build(kit,annotations,repeat);
        assertEquals(first,again); assertArrayEquals(read(baseline.resolve("srgutils-local.jar"),2<<20),read(repeat.resolve("srgutils-local.jar"),2<<20));
        assertEquals(false,first.get("binaryIdenticalToPublished")); // no upstream compiler/build attestation
        Path modifiedSource=kit.resolve("src/net/minecraftforge/srgutils/MappingFile.java");
        String source=Files.readString(modifiedSource); String marker="public String remapClass(String cls) {";
        assertEquals(1,source.split(java.util.regex.Pattern.quote(marker),-1).length-1);
        String changed=source.replace(marker,marker+"\n        // LegacyBridge local modification: 2026-09-12 - private sentinel only, not a released mapping rule.\n        if (\"legacybridge/relink/Sentinel\".equals(cls)) return \"legacybridge/relink/Modified\";");
        Files.writeString(modifiedSource,changed);
        Path modified=temp.resolve("modified"); var rebuilt=SrgRelinkTool.build(kit,annotations,modified);
        assertNotEquals(first.get("rebuiltLibrarySha256"),rebuilt.get("rebuiltLibrarySha256"));
        Path copy=temp.resolve("copy"); var relinked=SrgRelinkTool.replace(product,fingerprint,modified,(String)rebuilt.get("rebuiltLibrarySha256"),copy);
        byte[] privateJar=read(copy.resolve("LegacyBridge-relinked-PRIVATE.jar"),MAX_BYTES);
        var before=unzip(original,4096,MAX_BYTES); var after=unzip(privateJar,4096,MAX_BYTES);
        assertEquals(before.size()+1,after.size()); assertEquals(before.size()-3,((Number)relinked.get("unrelatedEntriesByteIdentical")).intValue());
        assertEquals(false,relinked.get("installationAllowed")); assertEquals("NOT_RUN",relinked.get("forgeLoading"));
        assertEquals("legacybridge/relink/Sentinel",remap(kit.resolve("original-library.jar"),"legacybridge/relink/Sentinel"));
        assertEquals("legacybridge/relink/Sentinel",remap(baseline.resolve("srgutils-local.jar"),"legacybridge/relink/Sentinel"));
        assertEquals("legacybridge/relink/Modified",remap(modified.resolve("srgutils-local.jar"),"legacybridge/relink/Sentinel"));
        assertEquals("b/B",remap(modified.resolve("srgutils-local.jar"),"a/A"));
        var ordinary=exerciseEngine(product,kit.resolve("original-library.jar"),libraries,fixture,temp.resolve("ordinary-engine"));
        var replacement=exerciseEngine(copy.resolve("LegacyBridge-relinked-PRIVATE.jar"),modified.resolve("srgutils-local.jar"),libraries,fixture,temp.resolve("modified-engine"));
        assertEquals(ordinary,replacement); assertEquals("541cc64b44b97d0e4e79a193dd2ce3a86a5feba6e15714d0ee24ad63751492bb",ordinary.get("convertedSha256"));
        assertEquals(fingerprint,hash(read(product,MAX_BYTES))); assertEquals(originalFixture,hash(read(fixture,1<<20)));
        // All refusals must leave the requested directory absent and preserve the previous valid copies.
        Path rejected=temp.resolve("rejected");
        assertThrows(IOException.class,()->SrgRelinkTool.replace(product,"0".repeat(64),modified,(String)rebuilt.get("rebuiltLibrarySha256"),rejected));
        assertFalse(Files.exists(rejected));
        Files.write(modified.resolve("srgutils-local-sources.zip"),new byte[]{0},StandardOpenOption.APPEND);
        assertThrows(IOException.class,()->SrgRelinkTool.replace(product,fingerprint,modified,(String)rebuilt.get("rebuiltLibrarySha256"),rejected)); assertFalse(Files.exists(rejected));
        Files.writeString(modifiedSource,changed.replace("LegacyBridge local modification:","unmarked change:"));
        assertThrows(IOException.class,()->SrgRelinkTool.build(kit,annotations,rejected)); assertFalse(Files.exists(rejected));
        Files.writeString(modifiedSource,changed.replace("Copyright (c) Forge Development LLC and contributors","removed"));
        assertThrows(IOException.class,()->SrgRelinkTool.build(kit,annotations,rejected)); assertFalse(Files.exists(rejected));
        Files.writeString(modifiedSource,changed+"\nthis is not Java;\n");
        assertThrows(IOException.class,()->SrgRelinkTool.build(kit,annotations,rejected)); assertFalse(Files.exists(rejected));
        try(var paths=Files.list(temp)){assertFalse(paths.anyMatch(p->p.getFileName().toString().startsWith(".legacybridge-relink-")));}
        String evidence=System.getProperty("legacybridge.relinkEvidence","");
        if(!evidence.isBlank()) {
            Path destination=Path.of(evidence); Files.createDirectory(destination);
            Map<String,Object> report=new TreeMap<>(); report.put("schemaVersion",1);report.put("productSha256",fingerprint);report.put("baselineBuild",first);report.put("modifiedBuild",rebuilt);report.put("replacement",relinked);
            report.put("engineComparison",ordinary);report.put("modifiedSentinelObserved",true);report.put("originalsUnchanged",true);report.put("nativeForge","NOT_RUN");report.put("libraryExecuted",true);report.put("modClassesExecuted",false);
            Files.writeString(destination.resolve("proof.json"),new CanonicalReportWriter().toJsonTree(report),StandardOpenOption.CREATE_NEW);
            Files.write(destination.resolve("LegacyBridge-relinked-PRIVATE.jar"),privateJar,StandardOpenOption.CREATE_NEW);
            // Keep the valid modified source/library snapshots from the candidate, not the deliberately corrupted negative input.
            Files.write(destination.resolve("srgutils-local.jar"),after.get(SrgRelinkTool.LIBRARY),StandardOpenOption.CREATE_NEW);
            Files.write(destination.resolve("srgutils-local-sources.zip"),after.get(SrgRelinkTool.SOURCES),StandardOpenOption.CREATE_NEW);
        }
    }
    private static String remap(Path jar,String value) throws Exception {
        try(var loader=new URLClassLoader(new URL[]{jar.toUri().toURL()},ClassLoader.getPlatformClassLoader())){
            var api=loader.loadClass("net.minecraftforge.srgutils.IMappingFile");
            Object mapping=api.getMethod("load",InputStream.class).invoke(null,new ByteArrayInputStream("CL: a/A b/B\n".getBytes(StandardCharsets.UTF_8)));
            assertEquals(jar.toUri().toURL(),api.getProtectionDomain().getCodeSource().getLocation());
            return (String)api.getMethod("remapClass",String.class).invoke(mapping,value);
        }
    }
    private static Map<String,Object> exerciseEngine(Path product,Path library,Path libraries,Path input,Path work) throws Exception {
        Files.createDirectory(work); var urls=new ArrayList<URL>(); urls.add(product.toUri().toURL());urls.add(library.toUri().toURL());
        for(String name:List.of("asm-9.9.1.jar","asm-tree-9.9.1.jar","asm-analysis-9.9.1.jar","asm-commons-9.9.1.jar","asm-util-9.9.1.jar","core-3.6.4.jar","toml-3.6.4.jar")) urls.add(libraries.resolve(name).toUri().toURL());
        // No parent application loader, original/converted mod JAR, standalone engine JAR or Forge classes.
        try(var loader=new URLClassLoader(urls.toArray(URL[]::new),ClassLoader.getPlatformClassLoader())){
            var api=loader.loadClass("dev.legacybridge.converter.service.LegacyEngineService");
            assertEquals(product.toUri().toURL(),api.getProtectionDomain().getCodeSource().getLocation());
            assertEquals(library.toUri().toURL(),loader.loadClass("net.minecraftforge.srgutils.IMappingFile").getProtectionDomain().getCodeSource().getLocation());
            Object engine=api.getConstructor().newInstance();
            try {
                var data=loader.loadClass("dev.legacybridge.converter.service.MappingData");
                var request=loader.loadClass("dev.legacybridge.converter.service.ConversionRequest");
                Object source=data.getMethod("builtinSimpleContent").invoke(null);
                Object converted=request.getConstructor(Path.class,Path.class,data).newInstance(input,work.resolve("converted.jar"),source);
                Object outcome=await(api.getMethod("convert",request).invoke(engine,converted));
                assertEquals("COMPLETED",outcome.getClass().getMethod("status").invoke(outcome).toString());
                Object value=((Optional<?>)outcome.getClass().getMethod("value").invoke(outcome)).orElseThrow();
                Object report=value.getClass().getMethod("report").invoke(value);
                Path bad=Files.write(work.resolve("corrupt.jar"),new byte[]{1,2,3});
                Object invalid=request.getConstructor(Path.class,Path.class,data).newInstance(bad,work.resolve("must-not-exist.jar"),source);
                Object refused=await(api.getMethod("convert",request).invoke(engine,invalid));
                assertEquals("FAILED",refused.getClass().getMethod("status").invoke(refused).toString());assertFalse(Files.exists(work.resolve("must-not-exist.jar")));
                Object error=((Optional<?>)refused.getClass().getMethod("error").invoke(refused)).orElseThrow();
                return Map.of("convertedSha256",hash(read(work.resolve("converted.jar"),1<<20)),"conversionReport",report,
                        "negativeCode",error.getClass().getMethod("code").invoke(error),"nativeForgeLoading",false,"modClassesExecuted",false);
            } finally { ((AutoCloseable)engine).close(); }
        }
    }
    private static Object await(Object job) throws Exception {
        return ((CompletionStage<?>)job.getClass().getMethod("completion").invoke(job)).toCompletableFuture().get(90,TimeUnit.SECONDS);
    }
}
