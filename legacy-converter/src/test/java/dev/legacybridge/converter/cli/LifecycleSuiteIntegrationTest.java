package dev.legacybridge.converter.cli;

import dev.legacybridge.converter.conversion.*;
import dev.legacybridge.converter.mapping.source.*;
import dev.legacybridge.converter.operation.*;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.service.*;
import dev.legacybridge.converter.transformation.*;
import dev.legacybridge.converter.transformation.linkage.OfflineLinkageVerifier;
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
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.commons.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

@EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment", matches="true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LifecycleSuiteIntegrationTest {
    private static final String ENTRY="dev/legacybridge/fixture/suite/SuiteContent";
    private static final String FLOW="dev/legacybridge/fixture/suite/ConfigEvents";
    private static final String EVENT="dev/legacybridge/fixture/suite/SuiteConfig";
    private static final String ITEM="dev/legacybridge/fixture/suite/SuiteItem";
    private static final String COMMANDS="dev/legacybridge/fixture/suite/SuiteCommands";
    private static final String EVENT_FLOW="dev/legacybridge/fixture/suite/EventFlow";
    private static final String DECISION="dev/legacybridge/fixture/suite/DecisionEvent";
    @TempDir Path directory;
    private static MappingInputs mappings;
    private static Path profile(){return Path.of(System.getProperty("legacybridge.logicMappingProfile"));}
    private static MappingInputs inputs() throws Exception {if(mappings==null)mappings=new MappingInputsLoader().load(profile());return mappings;}
    @AfterAll static void release(){mappings=null;}
    private static Path fixture(){return Path.of(System.getProperty("legacybridge.fixtureRoot"),"lifecycle-suite-1.16.5/build/libs/legacybridge-lifecycle-suite-1.16.5-1.0.0.jar");}

    @Test @Order(1) void serviceParityAndNoPublicPromotion() throws Exception {
        var output=directory.resolve("absent.jar"); CompositionCandidate proof;
        try(var service=new LegacyEngineService()){
            var request=new ConversionRequest(fixture(),output,MappingData.profile(profile()),Optional.of(hash(Files.readAllBytes(fixture()))),ConversionProfile.LIFECYCLE_PRESERVING_V1);
            var result=service.planLifecycleSuite(request).completion().toCompletableFuture().get(90,TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED,result.status(),result.toString());proof=result.value().orElseThrow();
            assertThrows(IllegalArgumentException.class,()->service.planLifecycleSuite(new ConversionRequest(fixture(),output,MappingData.profile(profile()))));
        }
        assertEquals(proof,LogicPreservingConverter.planLifecycleSuite(fixture(),output,inputs()));
        for(var p:ConversionProfile.values()){
            var old=switch(p){
                case SIMPLE_CONTENT_V1,DATAPACK_RECIPES_V1->new SimpleContentConverter(p).convert(fixture(),output,inputs());
                default->new LogicPreservingConverter(p).convert(fixture(),output,inputs());
            };
            assertFalse(old.converted(),p.toString());
        }
        assertFalse(LogicPreservingConverter.planClientConfig(fixture(),output,inputs()).validated());
        assertFalse(LogicPreservingConverter.planEventPriority(fixture(),output,inputs()).validated());
        assertFalse(LogicPreservingConverter.planLogicWithRecipes(fixture(),output,inputs()).validated());
        assertFalse(LogicPreservingConverter.planOwnedConstructors(fixture(),output,inputs()).validated());
        assertFalse(LogicPreservingConverter.planOwnedHierarchy(fixture(),output,inputs()).validated());
        assertFalse(LogicPreservingConverter.planOwnedInterfaceDefaults(fixture(),output,inputs()).validated());
        assertFalse(LogicPreservingConverter.planOwnedReceiverHandles(fixture(),output,inputs()).validated());
        assertFalse(Files.exists(output));assertEquals(6,ConversionProfile.values().length);
    }

    @Test void realForgeArchivePreservesFullGraphAndResources() throws Exception {
        var before=Files.readAllBytes(fixture());var output=directory.resolve("absent.jar");
        var proof=LogicPreservingConverter.planLifecycleSuite(fixture(),output,inputs());
        assertTrue(proof.validated(),proof.report().toString());assertFalse(proof.publicationAllowed());
        assertTrue(proof.candidateSha256().isPresent());assertFalse(Files.exists(output));assertArrayEquals(before,Files.readAllBytes(fixture()));
        var r=proof.report();assertEquals("LEGACY_LIFECYCLE_SUITE_CANDIDATE",r.get("reportType"));
        assertEquals("lifecycle-suite-candidate-v1",r.get("candidateIdentity"));assertEquals("NOT_RUN",r.get("runtimeValidation"));
        assertEquals(7,r.get("preservedClassCount"));assertEquals(34,r.get("preservedMethodCount"));assertEquals(0,r.get("replacedClassCount"));
        assertEquals("PASSED_EXACT_RULES",r.get("lifecycleSuiteMappingCommutation"));
        assertEquals(r.get("sourceLifecycleSuiteContract"),r.get("lifecycleSuiteContract"));assertEquals(r.get("lifecycleSuiteContract"),r.get("reopenedLifecycleSuiteContract"));
        var eventView=(Map<?,?>)((Map<?,?>)r.get("lifecycleSuiteContract")).get("eventAdmissionView");
        assertEquals(false,eventView.get("emitted"));assertEquals("finish",eventView.get("actualQueuedWork"));
        assertEquals(List.of("LATE_REGISTRY_READ","OWNED_EVENT_POST"),eventView.get("actualWorkSequence"));
        assertEquals("PASSED_SCOPED",((Map<?,?>)r.get("serializedOfflineLinkage")).get("status"));
        assertEquals(10,((Map<?,?>)r.get("serializedOfflineLinkage")).get("lambdaSiteCount"));
        assertEquals("PASSED_STATIC_REOPENED",((Map<?,?>)r.get("bytecodeValidation")).get("status"));
        assertEquals(true,((Map<?,?>)r.get("candidateArchiveValidation")).get("scratchDeleted"));
        for(var raw:(List<?>)((Map<?,?>)r.get("artifactPlan")).get("entries")){
            var e=(Map<?,?>)raw;var path=(String)e.get("path");
            if(path.startsWith("data/")||path.startsWith("assets/")){
                assertEquals("PRESERVE_BYTES",e.get("disposition"));assertEquals(e.get("sourceSha256"),e.get("targetSha256"));
            }
        }
        for(var raw:(List<?>)((Map<?,?>)r.get("transformationLog")).get("entries")){
            var e=(Map<?,?>)raw;assertEquals("VERIFIED",e.get("confidence"));assertNotNull(e.get("adapter"));
        }
        assertEquals(proof,LogicPreservingConverter.planLifecycleSuite(fixture(),output,inputs()));
        System.out.println("LIFECYCLE_SUITE_CANDIDATE "+new CanonicalReportWriter().toJsonTree(r));
    }

    @Test void admissionProjectionNeverMutatesOriginalTreesOrBootstrapArrays() throws Exception {
        var nodes=new LegacyReferenceScanner().scan(classes(read())).classNodes();
        var before=new TreeMap<String,byte[]>();nodes.forEach((k,v)->before.put(k,bytes(v)));
        var first=LifecycleSuiteContract.inspect(nodes);assertEquals(first,LifecycleSuiteContract.inspect(nodes));
        for(var e:nodes.entrySet())assertArrayEquals(before.get(e.getKey()),bytes(e.getValue()),e.getKey());
    }
    @Test void getSpecRecipeIsContextualAndHasAnImplementedBinding() throws Exception {
        String config="net/minecraftforge/fml/config/ModConfig";
        String before="()Lnet/minecraftforge/common/ForgeConfigSpec;",after="()Lnet/minecraftforge/fml/config/IConfigSpec;";
        assertEquals(after,dev.legacybridge.runtime.compat.logic.LifecycleSuiteAdapter.methodDescriptor(config,"getSpec",before,before));
        assertEquals(before,dev.legacybridge.runtime.compat.logic.LifecycleSuiteAdapter.methodDescriptor(config,"unreviewed",before,before));
        assertEquals(before,dev.legacybridge.runtime.compat.logic.LifecycleSuiteAdapter.methodDescriptor("unknown/Owner","getSpec",before,before));
        var scan=new LegacyReferenceScanner().scan(classes(read()));
        var mappings=dev.legacybridge.converter.conversion.logic.VerifiedLogicMappings.compileLifecycleSuite(scan.classNodes(),scan,inputs());
        var symbol=dev.legacybridge.converter.mapping.LegacySymbol.method(config,"getSpec",before);
        var rule=mappings.database().resolve(symbol);
        assertEquals(after,rule.target().descriptor());
        assertEquals(dev.legacybridge.runtime.compat.AdapterCatalog.CONFIG_SPEC_IDENTITY,mappings.adapterBindings().get(symbol));
        assertEquals("net/minecraftforge/common/ForgeConfigSpec",mappings.database().resolve(dev.legacybridge.converter.mapping.LegacySymbol.classSymbol("net/minecraftforge/common/ForgeConfigSpec")).target().owner());
        assertNotEquals(before,after);
    }
    @Test void changedValidDefaultsAndRangeAreRetainedNotHardcoded() throws Exception {
        var entries=read();var config=node(entries,EVENT);var init=method(config,"<clinit>");var c=code(init);
        init.instructions.set(c.get(6),new InsnNode(ICONST_0));
        init.instructions.set(c.get(11),new InsnNode(ICONST_5));
        init.instructions.set(c.get(12),new InsnNode(ICONST_2));
        init.instructions.set(c.get(13),new IntInsnNode(BIPUSH,12));
        entries.put(EVENT+".class",bytes(config));var input=directory.resolve("defaults.jar");write(input,entries);
        var proof=LogicPreservingConverter.planLifecycleSuite(input,directory.resolve("absent.jar"),inputs());
        assertTrue(proof.validated(),proof.report().toString());
        var schema=(Map<?,?>)((Map<?,?>)proof.report().get("lifecycleSuiteContract")).get("schema");
        assertEquals(5,schema.get("integerDefault")); assertEquals(2,schema.get("minimum")); assertEquals(12,schema.get("maximum")); assertEquals(false,schema.get("booleanDefault"));
    }

    @Test void admissionUsesRolesNotFixtureClassNames() throws Exception {
        var entries=read();var names=Map.of(ENTRY,ENTRY+"Renamed",FLOW,FLOW+"Renamed",EVENT,EVENT+"Renamed",ITEM,ITEM+"Renamed",COMMANDS,COMMANDS+"Renamed",EVENT_FLOW,EVENT_FLOW+"Renamed",DECISION,DECISION+"Renamed");
        for(String name:names.keySet()){
            var n=node(entries,name);var writer=new ClassWriter(0);n.accept(new ClassRemapper(writer,new SimpleRemapper(names)));
            entries.remove(name+".class");entries.put(names.get(name)+".class",writer.toByteArray());
        }
        var input=directory.resolve("renamed.jar");write(input,entries);
        var proof=LogicPreservingConverter.planLifecycleSuite(input,directory.resolve("absent.jar"),inputs());
        assertTrue(proof.validated(),proof.report().toString());
    }

    @Test void ownedMembersMayBeRenamedWithoutChangingScopeOrEffects() throws Exception {
        var entries=read();var owned=Set.of(ENTRY,FLOW,EVENT,ITEM,COMMANDS,EVENT_FLOW,DECISION);
        var remapper=new Remapper() {
            @Override public String mapMethodName(String owner,String name,String descriptor) {
                return owned.contains(owner) && !name.startsWith("<") ? "renamed_"+name : name;
            }
            @Override public String mapFieldName(String owner,String name,String descriptor) {
                return owned.contains(owner) ? "renamed_"+name : name;
            }
        };
        for(String name:owned) {
            var writer=new ClassWriter(0);node(entries,name).accept(new ClassRemapper(writer,remapper));
            entries.put(name+".class",writer.toByteArray());
        }
        var input=directory.resolve("renamed-members.jar");write(input,entries);
        var proof=LogicPreservingConverter.planLifecycleSuite(input,directory.resolve("absent.jar"),inputs());
        assertTrue(proof.validated(),proof.report().toString());
        var contract=(Map<?,?>)proof.report().get("lifecycleSuiteContract");
        assertEquals("renamed_loading",((Map<?,?>)contract.get("configuration")).get("loading"));
    }

    @ParameterizedTest @ValueSource(strings={"WRONG_SCOPE","WRONG_BUS","WRONG_PRIORITY","CANCELLED_CONFIG","SPEC_FILTER","TYPE_FILTER",
        "WRONG_BRANCH","MISSING_FILTER","EARLY_REGISTRY","EARLY_CONFIG","DUPLICATE_INSTALL","NO_VOLATILE","PUBLIC_STATE","SHARED_COUNTER",
        "INVALID_DEFAULT","DUPLICATE_KEY","EXTRA_EFFECT","UNKNOWN_DEPENDENCY","CLIENT_ACCESS","CAPTURED_CALLBACK","ALT_META","EXCEPTION",
        "WRONG_OPCODE","REGISTRY_NO_CAST","PERMISSION_ZERO","PERMISSION_BYPASS","INTEGER_RANGE","BOOL_PARSER","WRONG_ARGUMENT",
        "COMMAND_BUS","COMMAND_SIDE","COMMAND_CANCELLED","DUPLICATE_LITERAL","CACHED_DISPATCHER","COMMAND_CONFIG_BYPASS",
        "EVENT_WRONG_BUS","EVENT_WRONG_PRIORITY","EVENT_CANCEL_BYPASS","UNLOADING","MISSING_CLASS","EXTRA_CLASS","NONFINAL_SPEC"})
    void semanticDefectsBlockWholeComposition(String defect) throws Exception {
        var entries=read();var entry=node(entries,ENTRY);var hooks=node(entries,FLOW);var config=node(entries,EVENT);
        var commands=node(entries,COMMANDS);var flow=node(entries,EVENT_FLOW);
        var ctor=method(entry,"<init>");var c=code(ctor);
        var install=method(hooks,"install");var ins=code(install);
        var loading=method(hooks,"loading");var l=code(loading);
        var register=method(commands,"register");var r=code(register);
        switch(defect) {
            case "WRONG_SCOPE" -> ((FieldInsnNode)c.get(7)).name="SERVER";
            case "WRONG_BUS" -> ((MethodInsnNode)ins.get(1)).name="getWrongBus";
            case "WRONG_PRIORITY" -> ((FieldInsnNode)ins.get(2)).name="HIGH";
            case "CANCELLED_CONFIG" -> install.instructions.set(ins.get(3),new InsnNode(ICONST_1));
            case "SPEC_FILTER" -> ((FieldInsnNode)l.get(3)).owner=ENTRY;
            case "TYPE_FILTER" -> ((FieldInsnNode)l.get(8)).name="CLIENT";
            case "WRONG_BRANCH" -> ((JumpInsnNode)l.get(4)).setOpcode(IF_ACMPEQ);
            case "MISSING_FILTER" -> loading.instructions.remove(l.get(4));
            case "EARLY_REGISTRY" -> ctor.instructions.insertBefore(c.get(6),new MethodInsnNode(INVOKESTATIC,ENTRY,"finish","()V",false));
            case "EARLY_CONFIG" -> ctor.instructions.insertBefore(c.get(6),new MethodInsnNode(INVOKESTATIC,EVENT,"factor","()I",false));
            case "DUPLICATE_INSTALL" -> ctor.instructions.insertBefore(c.get(19),new MethodInsnNode(INVOKESTATIC,FLOW,"install","()V",false));
            case "NO_VOLATILE" -> hooks.fields.get(0).access &= ~ACC_VOLATILE;
            case "PUBLIC_STATE" -> hooks.fields.get(0).access=ACC_PUBLIC|ACC_STATIC|ACC_VOLATILE;
            case "SHARED_COUNTER" -> {for(var i:method(hooks,"reloading").instructions)if(i instanceof FieldInsnNode f&&f.name.equals("reloads"))f.name="loads";}
            case "INVALID_DEFAULT" -> {var m=method(config,"<clinit>");m.instructions.set(code(m).get(11),new IntInsnNode(BIPUSH,99));}
            case "DUPLICATE_KEY" -> ((LdcInsnNode)code(method(config,"<clinit>")).get(10)).cst="enabled";
            case "EXTRA_EFFECT" -> loading.instructions.insert(new MethodInsnNode(INVOKESTATIC,"unknown/Api","run","()V",false));
            case "CLIENT_ACCESS" -> loading.instructions.insert(new MethodInsnNode(INVOKESTATIC,"net/minecraft/client/Minecraft","getInstance","()Lnet/minecraft/client/Minecraft;",false));
            case "CAPTURED_CALLBACK" -> ((InvokeDynamicInsnNode)ins.get(5)).desc="(Ljava/lang/Object;)Ljava/util/function/Consumer;";
            case "ALT_META" -> {var d=(InvokeDynamicInsnNode)ins.get(5);d.bsm=new Handle(H_INVOKESTATIC,d.bsm.getOwner(),"altMetafactory",d.bsm.getDesc(),false);}
            case "EXCEPTION" -> loading.exceptions.add("java/lang/Exception");
            case "WRONG_OPCODE" -> ((MethodInsnNode)l.get(2)).setOpcode(INVOKESTATIC);
            case "REGISTRY_NO_CAST" -> {var m=method(entry,"finish");m.instructions.remove(code(m).get(2));}
            case "PERMISSION_ZERO" -> {var m=method(commands,"permission");m.instructions.set(code(m).get(1),new InsnNode(ICONST_0));}
            case "PERMISSION_BYPASS" -> register.instructions.remove(r.get(9));
            case "INTEGER_RANGE" -> register.instructions.set(r.get(15),new InsnNode(ICONST_M1));
            case "BOOL_PARSER" -> ((MethodInsnNode)r.get(26)).name="string";
            case "WRONG_ARGUMENT" -> ((LdcInsnNode)code(method(commands,"flag")).get(1)).cst="unknown";
            case "COMMAND_BUS" -> ((String[])commands.visibleAnnotations.get(0).values.get(3))[1]="MOD";
            case "COMMAND_SIDE" -> {commands.visibleAnnotations.get(0).values.add("value");commands.visibleAnnotations.get(0).values.add(new ArrayList<>(List.of(new String[]{"Lnet/minecraftforge/api/distmarker/Dist;","CLIENT"})));}
            case "COMMAND_CANCELLED" -> register.visibleAnnotations.get(0).values=new ArrayList<>(List.of("receiveCanceled",true));
            case "DUPLICATE_LITERAL" -> ((LdcInsnNode)r.get(23)).cst="scale";
            case "CACHED_DISPATCHER" -> commands.fields.add(new FieldNode(ACC_PRIVATE|ACC_STATIC,"cached","Lcom/mojang/brigadier/CommandDispatcher;",null,null));
            case "COMMAND_CONFIG_BYPASS" -> {var m=method(commands,"scale");m.instructions.set(code(m).get(8),new InsnNode(ICONST_1));}
            case "EVENT_WRONG_BUS" -> ((FieldInsnNode)code(method(flow,"run")).get(0)).name="OTHER";
            case "EVENT_WRONG_PRIORITY" -> ((FieldInsnNode)code(method(flow,"install")).get(1)).name="LOW";
            case "EVENT_CANCEL_BYPASS" -> {var m=method(flow,"cancel");m.instructions.set(code(m).get(7),new InsnNode(ICONST_0));}
            case "UNLOADING" -> ((LdcInsnNode)ins.get(11)).cst=Type.getObjectType(LifecycleSuiteContract.NEW_EVENT+"$Unloading");
            case "MISSING_CLASS" -> entries.remove(DECISION+".class");
            case "EXTRA_CLASS" -> {var n=new ClassNode();n.version=V1_8;n.access=ACC_PUBLIC|ACC_FINAL|ACC_SUPER;n.name="owned/Extra";n.superName="java/lang/Object";entries.put(n.name+".class",bytes(n));}
            case "NONFINAL_SPEC" -> config.fields.stream().filter(f->f.name.equals("SPEC")).findFirst().orElseThrow().access&=~ACC_FINAL;
            case "UNKNOWN_DEPENDENCY" -> {
                var old=new String(entries.get("META-INF/mods.toml"),java.nio.charset.StandardCharsets.UTF_8);
                entries.put("META-INF/mods.toml",(old+"\n[[dependencies.legacybridge_suite]]\nmodId=\"absent_mod\"\nmandatory=true\nversionRange=\"[1]\"\nordering=\"AFTER\"\nside=\"BOTH\"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            default -> throw new AssertionError(defect);
        }
        for(var n:List.of(entry,hooks,config,commands,flow))entries.put(n.name+".class",bytes(n));
        var input=directory.resolve(defect+".jar");write(input,entries);var before=Files.readAllBytes(input);var output=directory.resolve("absent.jar");
        var proof=LogicPreservingConverter.planLifecycleSuite(input,output,inputs());
        assertFalse(proof.validated(),defect+proof.report());assertTrue(proof.candidateSha256().isEmpty());assertFalse(Files.exists(output));assertArrayEquals(before,Files.readAllBytes(input));
    }
    @Test void offlineGateRechecksEntireGraphAndOlderPoliciesRejectIt() throws Exception {
        var entries=read();var scan=new LegacyReferenceScanner().scan(classes(entries));
        var mapping=dev.legacybridge.converter.conversion.logic.VerifiedLogicMappings.compileLifecycleSuite(scan.classNodes(),scan,inputs());
        var api=mapping.sourceApi();
        for(var policy:OfflineLinkageVerifier.CallbackPolicy.values())if(policy!=OfflineLinkageVerifier.CallbackPolicy.LIFECYCLE_SUITE)
            assertThrows(TransformationException.class,()->new OfflineLinkageVerifier().verify(classes(entries),api,policy),policy.toString());
        var n=node(entries,FLOW);((JumpInsnNode)code(method(n,"loading")).get(4)).setOpcode(IF_ACMPEQ);entries.put(FLOW+".class",bytes(n));
        assertThrows(TransformationException.class,()->new OfflineLinkageVerifier().verify(classes(entries),api,OfflineLinkageVerifier.CallbackPolicy.LIFECYCLE_SUITE));
    }

    @ParameterizedTest @ValueSource(strings={"SERIALIZATION","REOPEN","FINAL_LINKAGE","PREPARED"})
    void cancellationNeverPublishes(String point) throws Exception {
        var ref=new AtomicReference<OperationControl>();int[] archives={0},linkage={0};
        var scratchBefore=scratch();var observedScratch=new AtomicReference<Set<Path>>(Set.of());
        var control=new OperationControl(OperationLimits.defaults(),e->{
            boolean cancel=point.equals(e.stage().name());
            if(e.stage()==OperationStage.ARCHIVE_SCAN && e.completed()==0
                    && e.detail().equals("Scanning private input snapshot") && ++archives[0]==2 && point.equals("REOPEN")){
                var paths=new HashSet<>(scratch());paths.removeAll(scratchBefore);observedScratch.set(Set.copyOf(paths));cancel=true;
            }
            if(e.stage()==OperationStage.BYTECODE_VALIDATION && ++linkage[0]==3 && point.equals("FINAL_LINKAGE"))cancel=true;
            if(cancel)ref.get().requestCancel();
        });ref.set(control);inputs();var output=directory.resolve("absent.jar");
        try(var scope=control.enter()){
            assertThrows(OperationStoppedException.class,()->LogicPreservingConverter.planLifecycleSuite(fixture(),output,inputs()));
        }
        assertFalse(Files.exists(output));
        if(point.equals("REOPEN")){
            assertEquals(1,observedScratch.get().size(),"Cancellation must occur while the private candidate scratch file exists");
            for(var path:observedScratch.get())assertFalse(Files.exists(path),"Candidate scratch must be deleted after cancellation");
        }
    }
    @Test void bothRealBrigadierVersionsAgreeInIsolatedApiOnlyProcesses() throws Exception {
        var props=new Properties();try(var in=Files.newInputStream(profile())){props.load(in);}
        var source=Path.of(System.getProperty("legacybridge.fixtureRoot"),"lifecycle-suite-api/BrigadierSuiteProbe.java");
        var compiler=javax.tools.ToolProvider.getSystemJavaCompiler();assertNotNull(compiler,"JDK required for API-only probe");
        String first=null;
        for(String side:List.of("legacy","target")) {
            var jars=Arrays.stream(props.getProperty(side+".apiJars").split(";"))
                .map(Path::of).filter(p->p.getFileName().toString().startsWith("brigadier-")).toList();
            assertEquals(1,jars.size(),"Exactly the explicitly supplied real Brigadier API");var jar=jars.get(0);
            var output=Files.createDirectory(directory.resolve(side));var compilerOutput=new java.io.ByteArrayOutputStream();
            assertEquals(0,compiler.run(null,compilerOutput,compilerOutput,"--release","8","-classpath",jar.toString(),"-d",output.toString(),source.toString()),compilerOutput.toString());
            var log=directory.resolve(side+".log");
            var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Xmx64m","-cp",
                output+java.io.File.pathSeparator+jar,"BrigadierSuiteProbe").redirectErrorStream(true).redirectOutput(log.toFile()).start();
            try{assertTrue(process.waitFor(30,TimeUnit.SECONDS),"Bounded API-only process");
                assertEquals(0,process.exitValue(),Files.readString(log));assertTrue(Files.size(log)<16384);
            } finally {if(process.isAlive())process.destroyForcibly();}
            var result=Files.readString(log).trim();
            assertEquals("API_ONLY accepted=8 rejected=18 dispatcherGenerations=2 effects=8",result);
            if(first==null)first=result;else assertEquals(first,result);
            System.out.println("BRIGADIER_SUITE "+side+" sha256="+hash(Files.readAllBytes(jar))+" "+result);
        }
    }
    private static Set<Path> scratch() {
        try(var paths=Files.list(Path.of(System.getProperty("java.io.tmpdir")))){
            return paths.filter(p->p.getFileName().toString().startsWith("legacybridge-compose-"))
                    .collect(java.util.stream.Collectors.toSet());
        } catch(java.io.IOException failure){throw new AssertionError("Cannot inspect scratch lifetime",failure);}
    }
    private static Map<String,byte[]> classes(Map<String,byte[]> entries){var result=new TreeMap<String,byte[]>();entries.forEach((p,b)->{if(p.endsWith(".class"))result.put(p,b);});return result;}
    private static ClassNode node(Map<String,byte[]> entries,String name){var n=new ClassNode();new ClassReader(entries.get(name+".class")).accept(n,0);return n;}
    private static MethodNode method(ClassNode n,String name){return n.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();}
    private static List<AbstractInsnNode> code(MethodNode m){var out=new ArrayList<AbstractInsnNode>();for(var i:m.instructions)if(i.getOpcode()>=0)out.add(i);return out;}
    private static byte[] bytes(ClassNode n){var w=new ClassWriter(ClassWriter.COMPUTE_MAXS);n.accept(w);return w.toByteArray();}
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
