package dev.legacybridge.converter.cli;

import dev.legacybridge.converter.conversion.LogicPreservingConverter;
import dev.legacybridge.converter.mapping.source.MappingInputsLoader;
import dev.legacybridge.converter.operation.*;
import dev.legacybridge.converter.service.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Genuine source bytecode only. These tests never load/initialize the fixture classes. */
@EnabledIfSystemProperty(named="legacybridge.verifyLifecycleEnvironment", matches="true")
class LifecycleProfileIntegrationTest {
    @TempDir Path temporary;
    Path fixture() { return Path.of(System.getProperty("legacybridge.fixtureRoot"), "lifecycle-content-1.16.5/build/libs/legacybridge-lifecycle-1.16.5-1.0.0.jar"); }
    Path profile() { return Path.of(System.getProperty("legacybridge.lifecycleMappingProfile")); }
    @Test void cliAndServicePreserveOwnedClassesResourcesAndExactResult() throws Exception {
        byte[] original=Files.readAllBytes(fixture()); Path cli=Files.createDirectory(temporary.resolve("cli")).resolve("converted.jar"), api=Files.createDirectory(temporary.resolve("api")).resolve("converted.jar");
        var stdout=new ByteArrayOutputStream(); var stderr=new ByteArrayOutputStream();
        int code=LegacyBridgeCli.run(new String[]{"convert",fixture().toString(),"--mapping-profile",profile().toString(),"--conversion-profile","lifecycle-preserving-v1","--output",cli.toString()},new PrintStream(stdout),new PrintStream(stderr),Map.of());
        assertEquals(0,code,stdout.toString()+stderr.toString());
        try(var service=new LegacyEngineService()) {
            var request=new ConversionRequest(fixture(),api,MappingData.profile(profile()),Optional.empty(),ConversionProfile.LIFECYCLE_PRESERVING_V1);
            var plan=service.plan(request).completion().toCompletableFuture().get(90,TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED,plan.status(),plan.toString()); assertFalse(Files.exists(api));
            var result=service.convert(request).completion().toCompletableFuture().get(90,TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED,result.status(),result.toString());
            var report=result.value().orElseThrow().report(); assertEquals("NOT_RUN",report.get("runtimeValidation")); assertEquals(0,report.get("replacedClassCount"));
            assertTrue(report.containsKey("sourceDependencyGraph")); assertTrue(report.containsKey("targetDependencyGraph"));
        }
        assertArrayEquals(Files.readAllBytes(cli),Files.readAllBytes(api)); assertArrayEquals(original,Files.readAllBytes(fixture()));
        var before=read(fixture()); var after=read(cli);
        assertEquals(before.keySet().stream().filter(n->n.endsWith(".class")).toList(),after.keySet().stream().filter(n->n.endsWith(".class")).toList());
        for(String name:before.keySet()) if(name.startsWith("assets/")) assertArrayEquals(before.get(name),after.get(name));
        assertFalse(after.keySet().stream().anyMatch(n->n.endsWith("content.json")));
    }
    @Test void originalProfilesRemainClosedAndCancellationPublishesNothing() throws Exception {
        var inputs=new MappingInputsLoader().load(profile());
        assertFalse(new LogicPreservingConverter().convert(fixture(),temporary.resolve("old.jar"),inputs).converted());
        assertFalse(new dev.legacybridge.converter.conversion.SimpleContentConverter().convert(fixture(),temporary.resolve("simple.jar"),inputs).converted());
        var ref=new java.util.concurrent.atomic.AtomicReference<OperationControl>();
        var control=new OperationControl(OperationLimits.defaults(),p->{if(p.stage()==OperationStage.CLASS_REMAP)ref.get().requestCancel();}); ref.set(control);
        try(var scope=control.enter()) { assertThrows(OperationStoppedException.class,()->new LogicPreservingConverter(true).convert(fixture(),temporary.resolve("cancelled.jar"),inputs)); }
        assertFalse(Files.exists(temporary.resolve("cancelled.jar")));
    }
    @Test void missingMandatoryDependencyAndUnknownExternalCallsAbortWithoutOutput() throws Exception {
        var inputs=new MappingInputsLoader().load(profile()); var bytes=read(fixture());
        String toml=new String(bytes.get("META-INF/mods.toml"),StandardCharsets.UTF_8)+"\n[[dependencies.legacybridge_lifecycle]]\nmodId=\"requiredlib\"\nmandatory=true\nversionRange=\"[2,3)\"\nordering=\"AFTER\"\nside=\"SERVER\"\n";
        bytes.put("META-INF/mods.toml",toml.getBytes(StandardCharsets.UTF_8));
        var missing=new LogicPreservingConverter(true).convert(write("missing.jar",bytes),temporary.resolve("missing-output.jar"),inputs);
        assertFalse(missing.converted()); assertTrue(missing.report().toString().contains("requiredlib")); assertFalse(Files.exists(temporary.resolve("missing-output.jar")));
        bytes=read(fixture()); String owner="dev/legacybridge/fixture/lifecycle/LifecycleContent.class"; ClassNode node=new ClassNode(); new ClassReader(bytes.get(owner)).accept(node,0);
        MethodNode method=new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"unknown","()V",null,null);
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"unknown/dependency/Api","run","()V",false)); method.instructions.add(new InsnNode(Opcodes.RETURN)); node.methods.add(method);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); bytes.put(owner,writer.toByteArray());
        var unknown=new LogicPreservingConverter(true).convert(write("unknown.jar",bytes),temporary.resolve("unknown-output.jar"),inputs);
        assertFalse(unknown.converted()); assertTrue(unknown.report().toString().contains("unknown/dependency/Api")); assertFalse(Files.exists(temporary.resolve("unknown-output.jar")));
    }
    static Map<String,byte[]> read(Path file)throws IOException { Map<String,byte[]> entries=new TreeMap<>(); try(var zip=new ZipFile(file.toFile())){for(var e:zip.stream().toList())if(!e.isDirectory())try(var in=zip.getInputStream(e)){entries.put(e.getName(),in.readAllBytes());}}return entries; }
    Path write(String name,Map<String,byte[]> entries)throws IOException { Path output=temporary.resolve(name);try(var zip=new ZipOutputStream(Files.newOutputStream(output,StandardOpenOption.CREATE_NEW))){for(var e:entries.entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue());zip.closeEntry();}}return output; }
}
