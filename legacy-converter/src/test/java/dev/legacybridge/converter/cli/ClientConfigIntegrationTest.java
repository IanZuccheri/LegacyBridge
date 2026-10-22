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
class ClientConfigIntegrationTest {
    private static final String ENTRY="dev/legacybridge/fixture/clientconfig/ClientConfigContent";
    private static final String FLOW="dev/legacybridge/fixture/clientconfig/ClientConfigHooks";
    private static final String EVENT="dev/legacybridge/fixture/clientconfig/ClientConfigValues";
    private static final String ITEM="dev/legacybridge/fixture/clientconfig/ClientConfigItem";
    @TempDir Path directory;
    private static MappingInputs mappings;
    private static Path profile(){return Path.of(System.getProperty("legacybridge.logicMappingProfile"));}
    private static MappingInputs inputs() throws Exception {if(mappings==null)mappings=new MappingInputsLoader().load(profile());return mappings;}
    @AfterAll static void release(){mappings=null;}
    private static Path fixture(){return Path.of(System.getProperty("legacybridge.fixtureRoot"),"client-config-1.16.5/build/libs/legacybridge-client-config-1.16.5-1.0.0.jar");}

    @Test @Order(1) void serviceParityAndNoPublicPromotion() throws Exception {
        var output=directory.resolve("absent.jar"); CompositionCandidate proof;
        try(var service=new LegacyEngineService()){
            var request=new ConversionRequest(fixture(),output,MappingData.profile(profile()),Optional.of(hash(Files.readAllBytes(fixture()))),ConversionProfile.LIFECYCLE_PRESERVING_V1);
            var result=service.planClientConfig(request).completion().toCompletableFuture().get(90,TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED,result.status(),result.toString());proof=result.value().orElseThrow();
            assertThrows(IllegalArgumentException.class,()->service.planClientConfig(new ConversionRequest(fixture(),output,MappingData.profile(profile()))));
        }
        assertEquals(proof,LogicPreservingConverter.planClientConfig(fixture(),output,inputs()));
        for(var p:ConversionProfile.values()){
            var old=switch(p){
                case SIMPLE_CONTENT_V1,DATAPACK_RECIPES_V1->new SimpleContentConverter(p).convert(fixture(),output,inputs());
                default->new LogicPreservingConverter(p).convert(fixture(),output,inputs());
            };
            assertFalse(old.converted(),p.toString());
        }
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
        var proof=LogicPreservingConverter.planClientConfig(fixture(),output,inputs());
        assertTrue(proof.validated(),proof.report().toString());assertFalse(proof.publicationAllowed());
        assertTrue(proof.candidateSha256().isPresent());assertFalse(Files.exists(output));assertArrayEquals(before,Files.readAllBytes(fixture()));
        var r=proof.report();assertEquals("LEGACY_CLIENT_CONFIG_CANDIDATE",r.get("reportType"));
        assertEquals("client-config-candidate-v1",r.get("candidateIdentity"));assertEquals("NOT_RUN",r.get("runtimeValidation"));
        assertEquals(4,r.get("preservedClassCount"));assertEquals(14,r.get("preservedMethodCount"));assertEquals(0,r.get("replacedClassCount"));
        assertEquals("PASSED_EXACT_RULES",r.get("clientConfigMappingCommutation"));
        assertEquals(r.get("sourceClientConfigContract"),r.get("clientConfigContract"));assertEquals(r.get("clientConfigContract"),r.get("reopenedClientConfigContract"));
        assertEquals(Map.of("enabled",true,"factor",3,"loads",1),((Map<?,?>)((Map<?,?>)r.get("clientConfigContract")).get("isolatedOracle")).get("freshClient"));
        assertEquals("PASSED_SCOPED",((Map<?,?>)r.get("serializedOfflineLinkage")).get("status"));
        assertEquals(3,((Map<?,?>)r.get("serializedOfflineLinkage")).get("lambdaSiteCount"));
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
        assertEquals(proof,LogicPreservingConverter.planClientConfig(fixture(),output,inputs()));
        System.out.println("CLIENT_CONFIG_CANDIDATE "+new CanonicalReportWriter().toJsonTree(r));
    }

    @Test void changedValidDefaultsAndRangeAreRetainedNotHardcoded() throws Exception {
        var entries=read();var config=node(entries,EVENT);var init=method(config,"<clinit>");var c=code(init);
        init.instructions.set(c.get(6),new InsnNode(ICONST_0));
        init.instructions.set(c.get(11),new InsnNode(ICONST_5));
        init.instructions.set(c.get(12),new InsnNode(ICONST_2));
        init.instructions.set(c.get(13),new IntInsnNode(BIPUSH,12));
        entries.put(EVENT+".class",bytes(config));var input=directory.resolve("defaults.jar");write(input,entries);
        var proof=LogicPreservingConverter.planClientConfig(input,directory.resolve("absent.jar"),inputs());
        assertTrue(proof.validated(),proof.report().toString());
        var schema=(Map<?,?>)((Map<?,?>)proof.report().get("clientConfigContract")).get("schema");
        assertEquals(Map.of("key","factor","default",5,"min",2,"max",12),schema.get("integer"));
        assertEquals(Map.of("key","enabled","default",false),schema.get("boolean"));
    }

    @Test void admissionUsesRolesNotFixtureClassNames() throws Exception {
        var entries=read();var names=Map.of(ENTRY,ENTRY+"Renamed",FLOW,FLOW+"Renamed",EVENT,EVENT+"Renamed",ITEM,ITEM+"Renamed");
        for(String name:names.keySet()){
            var n=node(entries,name);var writer=new ClassWriter(0);n.accept(new ClassRemapper(writer,new SimpleRemapper(names)));
            entries.remove(name+".class");entries.put(names.get(name)+".class",writer.toByteArray());
        }
        var input=directory.resolve("renamed.jar");write(input,entries);
        var proof=LogicPreservingConverter.planClientConfig(input,directory.resolve("absent.jar"),inputs());
        assertTrue(proof.validated(),proof.report().toString());
    }

    @ParameterizedTest @ValueSource(strings={"WRONG_DIST","COMMON_SCOPE","SERVER_SCOPE","INVERT_GUARD","BYPASS_GUARD",
            "BACKWARD_GUARD","UNGUARDED_READ","STATIC_READ","DIRECT_SETUP_READ","READ_DURING_INSTALL","COMMON_SETUP",
            "WRONG_PRIORITY","RECEIVE_CANCELLED","UNKNOWN_EXTERNAL","WRONG_QUEUE","ALT_META","BOXING",
            "REGISTER_TWICE","STATIC_INITIALIZER","PUBLIC_STATE","INVALID_DEFAULT","DUPLICATE_KEY","UNKNOWN_DEPENDENCY",
            "INVISIBLE_MOD","INHERITANCE","MISSING_CONFIG","NO_FINAL_SPEC","EARLY_REGISTRY","CAPTURED_CALLBACK",
            "EXCEPTION","WRONG_OPCODE","FALSE_COUNTER","DUPLICATE_OBSERVATION","CLIENT_GAME_ACCESS","INFERRED_LISTENER",
            "EARLY_SAME_SIZE","SAME_STATE_FIELD"})
    void unknownOrSemanticallyDifferentEffectsBlockWholeCandidate(String defect) throws Exception {
        var entries=read();var hooks=node(entries,FLOW);var entry=node(entries,ENTRY);var config=node(entries,EVENT);
        var install=method(hooks,"install");var list=code(install);var ctor=method(entry,"<init>");var c=code(ctor);
        var setup=method(hooks,"setup");var init=method(config,"<clinit>");
        switch(defect) {
            case "EARLY_SAME_SIZE"->{
                ctor.instructions.set(c.get(2),new MethodInsnNode(INVOKESTATIC,FLOW,"read","()V",false));
                for(int i=3;i<6;i++)ctor.instructions.set(c.get(i),new InsnNode(NOP));
            }
            case "SAME_STATE_FIELD"->((FieldInsnNode)code(method(hooks,"read")).get(3)).name="loads";
            case "WRONG_DIST"->((FieldInsnNode)c.get(7)).name="DEDICATED_SERVER";
            case "COMMON_SCOPE"->((FieldInsnNode)list.get(1)).name="COMMON";
            case "SERVER_SCOPE"->((FieldInsnNode)list.get(1)).name="SERVER";
            case "INVERT_GUARD"->ctor.instructions.set(c.get(8),new JumpInsnNode(IF_ACMPEQ,((JumpInsnNode)c.get(8)).label));
            case "BYPASS_GUARD"->{var label=new LabelNode();ctor.instructions.insertBefore(c.get(9),label);((JumpInsnNode)c.get(8)).label=label;}
            case "BACKWARD_GUARD"->{var label=new LabelNode();ctor.instructions.insertBefore(c.get(6),label);((JumpInsnNode)c.get(8)).label=label;}
            case "UNGUARDED_READ"->ctor.instructions.insertBefore(c.get(6),new MethodInsnNode(INVOKESTATIC,FLOW,"read","()V",false));
            case "STATIC_READ"->method(entry,"<clinit>").instructions.insert(new MethodInsnNode(INVOKESTATIC,FLOW,"read","()V",false));
            case "DIRECT_SETUP_READ"->{setup.instructions.clear();setup.localVariables.clear();setup.instructions.add(new MethodInsnNode(INVOKESTATIC,FLOW,"read","()V",false));setup.instructions.add(new InsnNode(RETURN));}
            case "READ_DURING_INSTALL"->install.instructions.insert(new MethodInsnNode(INVOKESTATIC,FLOW,"read","()V",false));
            case "COMMON_SETUP"->((LdcInsnNode)list.get(8)).cst=Type.getObjectType("net/minecraftforge/fml/event/lifecycle/FMLCommonSetupEvent");
            case "WRONG_PRIORITY"->((FieldInsnNode)list.get(6)).name="HIGH";
            case "RECEIVE_CANCELLED"->install.instructions.set(list.get(7),new InsnNode(ICONST_1));
            case "UNKNOWN_EXTERNAL"->method(hooks,"read").instructions.insert(new MethodInsnNode(INVOKESTATIC,"unknown/Api","run","()V",false));
            case "CLIENT_GAME_ACCESS"->{var extra=new InsnList();extra.add(new MethodInsnNode(INVOKESTATIC,"net/minecraft/client/Minecraft","getInstance","()Lnet/minecraft/client/Minecraft;",false));extra.add(new InsnNode(POP));method(hooks,"read").instructions.insert(extra);}
            case "WRONG_QUEUE"->((MethodInsnNode)code(setup).get(2)).name="unknownQueue";
            case "ALT_META"->{var d=(InvokeDynamicInsnNode)list.get(9);d.bsm=new Handle(H_INVOKESTATIC,d.bsm.getOwner(),"altMetafactory",d.bsm.getDesc(),false);}
            case "BOXING"->((InvokeDynamicInsnNode)list.get(9)).bsmArgs[2]=Type.getMethodType("(Ljava/lang/Object;)V");
            case "REGISTER_TWICE"->ctor.instructions.insertBefore(c.get(10),new MethodInsnNode(INVOKESTATIC,FLOW,"install","()V",false));
            case "STATIC_INITIALIZER"->{var m=new MethodNode(ACC_STATIC,"<clinit>","()V",null,null);m.instructions.add(new InsnNode(RETURN));hooks.methods.add(m);}
            case "PUBLIC_STATE"->hooks.fields.get(0).access=ACC_PUBLIC|ACC_STATIC;
            case "INVALID_DEFAULT"->init.instructions.set(code(init).get(11),new IntInsnNode(BIPUSH,20));
            case "DUPLICATE_KEY"->((LdcInsnNode)code(init).get(10)).cst="enabled";
            case "INVISIBLE_MOD"->{entry.invisibleAnnotations=entry.visibleAnnotations;entry.visibleAnnotations=null;}
            case "INHERITANCE"->config.access&=~ACC_FINAL;
            case "MISSING_CONFIG"->entries.remove(EVENT+".class");
            case "NO_FINAL_SPEC"->config.fields.stream().filter(f->f.name.equals("SPEC")).findFirst().orElseThrow().access=ACC_PUBLIC|ACC_STATIC;
            case "EARLY_REGISTRY"->{var extra=new InsnList();extra.add(new FieldInsnNode(GETSTATIC,ENTRY,"TOKEN","Lnet/minecraftforge/fml/RegistryObject;"));extra.add(new InsnNode(POP));ctor.instructions.insertBefore(c.get(6),extra);}
            case "CAPTURED_CALLBACK"->((InvokeDynamicInsnNode)list.get(9)).desc="(Ljava/lang/Object;)Ljava/util/function/Consumer;";
            case "EXCEPTION"->method(hooks,"read").exceptions.add("java/lang/Exception");
            case "WRONG_OPCODE"->((MethodInsnNode)list.get(3)).setOpcode(INVOKESTATIC);
            case "FALSE_COUNTER"->{var m=method(hooks,"read");m.instructions.set(code(m).get(5),new InsnNode(ICONST_0));}
            case "DUPLICATE_OBSERVATION"->((FieldInsnNode)code(method(hooks,"loads")).get(0)).name="factor";
            case "INFERRED_LISTENER"->((MethodInsnNode)list.get(10)).desc="(Ljava/util/function/Consumer;)V";
            case "UNKNOWN_DEPENDENCY"->{
                var old=new String(entries.get("META-INF/mods.toml"),java.nio.charset.StandardCharsets.UTF_8);
                entries.put("META-INF/mods.toml",(old+"\n[[dependencies.legacybridge_clientconfig]]\nmodId=\"absent_mod\"\nmandatory=true\nversionRange=\"[1]\"\nordering=\"AFTER\"\nside=\"BOTH\"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            default->throw new AssertionError(defect);
        }
        entries.put(FLOW+".class",bytes(hooks));entries.put(ENTRY+".class",bytes(entry));
        if(!defect.equals("MISSING_CONFIG"))entries.put(EVENT+".class",bytes(config));
        var input=directory.resolve(defect+".jar");write(input,entries);var original=Files.readAllBytes(input);
        var output=directory.resolve("absent.jar");var proof=LogicPreservingConverter.planClientConfig(input,output,inputs());
        assertFalse(proof.validated(),defect+proof.report());assertTrue(proof.candidateSha256().isEmpty());
        assertFalse(Files.exists(output));assertArrayEquals(original,Files.readAllBytes(input));
    }

    @Test void offlineVerifierRederivesGuardInsteadOfTrustingPolicy() throws Exception {
        var entries=read();var scan=new LegacyReferenceScanner().scan(classes(entries));
        var mapping=dev.legacybridge.converter.conversion.logic.VerifiedLogicMappings.compileClientConfig(scan.classNodes(),scan,inputs());
        var api=mapping.sourceApi();var archive=classes(entries);
        assertThrows(TransformationException.class,()->new OfflineLinkageVerifier().verify(archive,api,OfflineLinkageVerifier.CallbackPolicy.LIFECYCLE));
        var entry=node(entries,ENTRY);((FieldInsnNode)code(method(entry,"<init>")).get(7)).name="DEDICATED_SERVER";archive.put(ENTRY+".class",bytes(entry));
        assertThrows(TransformationException.class,()->new OfflineLinkageVerifier().verify(archive,api,OfflineLinkageVerifier.CallbackPolicy.CLIENT_CONFIG));
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
            assertThrows(OperationStoppedException.class,()->LogicPreservingConverter.planClientConfig(fixture(),output,inputs()));
        }
        assertFalse(Files.exists(output));
        if(point.equals("REOPEN")){
            assertEquals(1,observedScratch.get().size(),"Cancellation must occur while the private candidate scratch file exists");
            for(var path:observedScratch.get())assertFalse(Files.exists(path),"Candidate scratch must be deleted after cancellation");
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
