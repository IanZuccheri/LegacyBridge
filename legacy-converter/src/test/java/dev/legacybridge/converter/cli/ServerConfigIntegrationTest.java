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
class ServerConfigIntegrationTest {
    private static final String ENTRY="dev/legacybridge/fixture/serverconfig/ServerConfigContent";
    private static final String FLOW="dev/legacybridge/fixture/serverconfig/ServerConfigHooks";
    private static final String EVENT="dev/legacybridge/fixture/serverconfig/ServerConfigValues";
    private static final String ITEM="dev/legacybridge/fixture/serverconfig/ServerConfigItem";
    @TempDir Path directory;
    private static MappingInputs mappings;
    private static Path profile(){return Path.of(System.getProperty("legacybridge.logicMappingProfile"));}
    private static MappingInputs inputs() throws Exception {if(mappings==null)mappings=new MappingInputsLoader().load(profile());return mappings;}
    @AfterAll static void release(){mappings=null;}
    private static Path fixture(){return Path.of(System.getProperty("legacybridge.fixtureRoot"),"server-config-1.16.5/build/libs/legacybridge-server-config-1.16.5-1.0.0.jar");}

    @Test @Order(1) void serviceParityAndNoPublicPromotion() throws Exception {
        var output=directory.resolve("absent.jar"); CompositionCandidate proof;
        try(var service=new LegacyEngineService()){
            var request=new ConversionRequest(fixture(),output,MappingData.profile(profile()),Optional.of(hash(Files.readAllBytes(fixture()))),ConversionProfile.LIFECYCLE_PRESERVING_V1);
            var result=service.planServerConfig(request).completion().toCompletableFuture().get(90,TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED,result.status(),result.toString());proof=result.value().orElseThrow();
            assertThrows(IllegalArgumentException.class,()->service.planServerConfig(new ConversionRequest(fixture(),output,MappingData.profile(profile()))));
        }
        assertEquals(proof,LogicPreservingConverter.planServerConfig(fixture(),output,inputs()));
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
        var proof=LogicPreservingConverter.planServerConfig(fixture(),output,inputs());
        assertTrue(proof.validated(),proof.report().toString());assertFalse(proof.publicationAllowed());
        assertTrue(proof.candidateSha256().isPresent());assertFalse(Files.exists(output));assertArrayEquals(before,Files.readAllBytes(fixture()));
        var r=proof.report();assertEquals("LEGACY_SERVER_CONFIG_CANDIDATE",r.get("reportType"));
        assertEquals("server-config-candidate-v1",r.get("candidateIdentity"));assertEquals("NOT_RUN",r.get("runtimeValidation"));
        assertEquals(4,r.get("preservedClassCount"));assertEquals(16,r.get("preservedMethodCount"));assertEquals(0,r.get("replacedClassCount"));
        assertEquals("PASSED_EXACT_RULES",r.get("serverConfigMappingCommutation"));
        assertEquals(r.get("sourceServerConfigContract"),r.get("serverConfigContract"));assertEquals(r.get("serverConfigContract"),r.get("reopenedServerConfigContract"));
        assertEquals(Map.of("active",true,"enabled",true,"factor",3,"starts",1,"stops",0),((Map<?,?>)((Map<?,?>)r.get("serverConfigContract")).get("isolatedOracle")).get("freshServerStarted"));
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
        assertEquals(proof,LogicPreservingConverter.planServerConfig(fixture(),output,inputs()));
        System.out.println("SERVER_CONFIG_CANDIDATE "+new CanonicalReportWriter().toJsonTree(r));
    }

    @Test void changedValidDefaultsAndRangeAreRetainedNotHardcoded() throws Exception {
        var entries=read();var config=node(entries,EVENT);var init=method(config,"<clinit>");var c=code(init);
        init.instructions.set(c.get(6),new InsnNode(ICONST_0));
        init.instructions.set(c.get(11),new InsnNode(ICONST_5));
        init.instructions.set(c.get(12),new InsnNode(ICONST_2));
        init.instructions.set(c.get(13),new IntInsnNode(BIPUSH,12));
        entries.put(EVENT+".class",bytes(config));var input=directory.resolve("defaults.jar");write(input,entries);
        var proof=LogicPreservingConverter.planServerConfig(input,directory.resolve("absent.jar"),inputs());
        assertTrue(proof.validated(),proof.report().toString());
        var schema=(Map<?,?>)((Map<?,?>)proof.report().get("serverConfigContract")).get("schema");
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
        var proof=LogicPreservingConverter.planServerConfig(input,directory.resolve("absent.jar"),inputs());
        assertTrue(proof.validated(),proof.report().toString());
    }

    @Test void ownedMembersMayBeRenamedWithoutChangingScopeOrEffects() throws Exception {
        var entries=read();var owned=Set.of(ENTRY,FLOW,EVENT,ITEM);
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
        var proof=LogicPreservingConverter.planServerConfig(input,directory.resolve("absent.jar"),inputs());
        assertTrue(proof.validated(),proof.report().toString());
        var contract=(Map<?,?>)proof.report().get("serverConfigContract");
        assertEquals("renamed_active",((Map<?,?>)contract.get("state")).get("active"));
        assertEquals("renamed_started",((Map<?,?>)contract.get("lifecycle")).get("started"));
    }

    @ParameterizedTest @ValueSource(strings={"COMMON_SCOPE","CLIENT_SCOPE","DEDICATED_GUARD","EARLY_SAME_SIZE",
            "STATIC_READ","READ_DURING_INSTALL","WRONG_BUS","EARLY_PHASE","SWAP_PHASES","NO_CLEAR","STALE_SNAPSHOT",
            "STOP_READS_CONFIG","WRONG_PRIORITY","RECEIVE_CANCELLED","UNKNOWN_EXTERNAL","EVENT_PAYLOAD","ALT_META","BOXING",
            "REGISTER_TWICE","STATIC_INITIALIZER","PUBLIC_STATE","INVALID_DEFAULT","DUPLICATE_KEY","UNKNOWN_DEPENDENCY",
            "INVISIBLE_MOD","INHERITANCE","MISSING_CONFIG","NO_FINAL_SPEC","EARLY_REGISTRY","CAPTURED_CALLBACK",
            "EXCEPTION","WRONG_OPCODE","FALSE_COUNTER","DUPLICATE_OBSERVATION","CLIENT_GAME_ACCESS","INFERRED_LISTENER",
            "SAME_STATE_FIELD","NO_SERVER_REGISTRATION","WRONG_STOP_PHASE","CONFIG_UNLOADING","CONFIG_RELOADING",
            "MISSING_STOP_LISTENER","REGISTER_RETURN_VALUE","INSTANCE_STATE"})
    void unknownOrSemanticallyDifferentEffectsBlockWholeCandidate(String defect) throws Exception {
        var entries=read();var hooks=node(entries,FLOW);var entry=node(entries,ENTRY);var config=node(entries,EVENT);
        var install=method(hooks,"install");var list=code(install);var ctor=method(entry,"<init>");var c=code(ctor);
        var begin=method(hooks,"started");var end=method(hooks,"stopped");var init=method(config,"<clinit>");
        switch(defect) {
            case "EARLY_SAME_SIZE"->{
                ctor.instructions.set(c.get(2),new MethodInsnNode(INVOKESTATIC,EVENT,"factor","()I",false));
                ctor.instructions.set(c.get(3),new InsnNode(POP));
                for(int i=4;i<6;i++)ctor.instructions.set(c.get(i),new InsnNode(NOP));
            }
            case "COMMON_SCOPE"->((FieldInsnNode)list.get(1)).name="COMMON";
            case "CLIENT_SCOPE"->((FieldInsnNode)list.get(1)).name="CLIENT";
            case "DEDICATED_GUARD"->{
                var label=new LabelNode();ctor.instructions.insertBefore(c.get(7),label);var extra=new InsnList();
                extra.add(new FieldInsnNode(GETSTATIC,"net/minecraftforge/fml/loading/FMLEnvironment","dist","Lnet/minecraftforge/api/distmarker/Dist;"));
                extra.add(new FieldInsnNode(GETSTATIC,"net/minecraftforge/api/distmarker/Dist","DEDICATED_SERVER","Lnet/minecraftforge/api/distmarker/Dist;"));
                extra.add(new JumpInsnNode(IF_ACMPNE,label));ctor.instructions.insertBefore(c.get(6),extra);
            }
            case "SAME_STATE_FIELD"->((FieldInsnNode)code(begin).get(3)).name="starts";
            case "STATIC_READ"->{var extra=new InsnList();extra.add(new MethodInsnNode(INVOKESTATIC,EVENT,"factor","()I",false));extra.add(new InsnNode(POP));method(entry,"<clinit>").instructions.insert(extra);}
            case "READ_DURING_INSTALL"->{var extra=new InsnList();extra.add(new MethodInsnNode(INVOKESTATIC,EVENT,"isEnabled","()Z",false));extra.add(new InsnNode(POP));install.instructions.insert(extra);}
            case "WRONG_BUS"->{var extra=new InsnList();extra.add(new MethodInsnNode(INVOKESTATIC,"net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext","get","()Lnet/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext;",false));extra.add(new MethodInsnNode(INVOKEVIRTUAL,"net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext","getModEventBus","()Lnet/minecraftforge/eventbus/api/IEventBus;",false));install.instructions.insertBefore(list.get(4),extra);install.instructions.remove(list.get(4));}
            case "EARLY_PHASE"->replaceEvent(hooks,ServerConfigContract.START,"net/minecraftforge/fml/event/server/FMLServerAboutToStartEvent");
            case "WRONG_STOP_PHASE"->replaceEvent(hooks,ServerConfigContract.STOP,"net/minecraftforge/fml/event/server/FMLServerStoppingEvent");
            case "CONFIG_UNLOADING"->replaceEvent(hooks,ServerConfigContract.STOP,"net/minecraftforge/fml/event/config/ModConfigEvent$Unloading");
            case "CONFIG_RELOADING"->replaceEvent(hooks,ServerConfigContract.START,"net/minecraftforge/fml/config/ModConfig$Reloading");
            case "SWAP_PHASES"->{replaceEvent(hooks,ServerConfigContract.START,"temporary/Event");replaceEvent(hooks,ServerConfigContract.STOP,ServerConfigContract.START);replaceEvent(hooks,"temporary/Event",ServerConfigContract.STOP);}
            case "NO_CLEAR"->end.instructions.set(code(end).get(0),new InsnNode(ICONST_1));
            case "STALE_SNAPSHOT"->{var ec=code(end);for(int i=2;i<6;i++)end.instructions.remove(ec.get(i));}
            case "STOP_READS_CONFIG"->end.instructions.set(code(end).get(4),new MethodInsnNode(INVOKESTATIC,EVENT,"factor","()I",false));
            case "WRONG_PRIORITY"->((FieldInsnNode)list.get(5)).name="HIGH";
            case "RECEIVE_CANCELLED"->install.instructions.set(list.get(6),new InsnNode(ICONST_1));
            case "UNKNOWN_EXTERNAL"->begin.instructions.insert(new MethodInsnNode(INVOKESTATIC,"unknown/Api","run","()V",false));
            case "EVENT_PAYLOAD"->{var extra=new InsnList();extra.add(new VarInsnNode(ALOAD,0));extra.add(new MethodInsnNode(INVOKEVIRTUAL,ServerConfigContract.START,"getServer","()Lnet/minecraft/server/MinecraftServer;",false));extra.add(new InsnNode(POP));begin.instructions.insert(extra);}
            case "CLIENT_GAME_ACCESS"->{var extra=new InsnList();extra.add(new MethodInsnNode(INVOKESTATIC,"net/minecraft/client/Minecraft","getInstance","()Lnet/minecraft/client/Minecraft;",false));extra.add(new InsnNode(POP));begin.instructions.insert(extra);}
            case "ALT_META"->{var d=(InvokeDynamicInsnNode)list.get(8);d.bsm=new Handle(H_INVOKESTATIC,d.bsm.getOwner(),"altMetafactory",d.bsm.getDesc(),false);}
            case "BOXING"->((InvokeDynamicInsnNode)list.get(8)).bsmArgs[2]=Type.getMethodType("(Ljava/lang/Object;)V");
            case "REGISTER_TWICE"->ctor.instructions.insertBefore(c.get(7),new MethodInsnNode(INVOKESTATIC,FLOW,"install","()V",false));
            case "NO_SERVER_REGISTRATION"->{for(int i=0;i<4;i++)install.instructions.remove(list.get(i));}
            case "MISSING_STOP_LISTENER"->{for(int i=10;i<16;i++)install.instructions.remove(list.get(i));}
            case "REGISTER_RETURN_VALUE"->((MethodInsnNode)list.get(9)).desc="(Lnet/minecraftforge/eventbus/api/EventPriority;ZLjava/lang/Class;Ljava/util/function/Consumer;)Z";
            case "STATIC_INITIALIZER"->{var m=new MethodNode(ACC_STATIC,"<clinit>","()V",null,null);m.instructions.add(new InsnNode(RETURN));hooks.methods.add(m);}
            case "PUBLIC_STATE"->hooks.fields.get(0).access=ACC_PUBLIC|ACC_STATIC;
            case "INSTANCE_STATE"->hooks.fields.get(0).access=ACC_PRIVATE;
            case "INVALID_DEFAULT"->init.instructions.set(code(init).get(11),new IntInsnNode(BIPUSH,20));
            case "DUPLICATE_KEY"->((LdcInsnNode)code(init).get(10)).cst="enabled";
            case "INVISIBLE_MOD"->{entry.invisibleAnnotations=entry.visibleAnnotations;entry.visibleAnnotations=null;}
            case "INHERITANCE"->config.access&=~ACC_FINAL;
            case "MISSING_CONFIG"->entries.remove(EVENT+".class");
            case "NO_FINAL_SPEC"->config.fields.stream().filter(f->f.name.equals("SPEC")).findFirst().orElseThrow().access=ACC_PUBLIC|ACC_STATIC;
            case "EARLY_REGISTRY"->{var extra=new InsnList();extra.add(new FieldInsnNode(GETSTATIC,ENTRY,"TOKEN","Lnet/minecraftforge/fml/RegistryObject;"));extra.add(new InsnNode(POP));ctor.instructions.insertBefore(c.get(6),extra);}
            case "CAPTURED_CALLBACK"->((InvokeDynamicInsnNode)list.get(8)).desc="(Ljava/lang/Object;)Ljava/util/function/Consumer;";
            case "EXCEPTION"->begin.exceptions.add("java/lang/Exception");
            case "WRONG_OPCODE"->((MethodInsnNode)list.get(3)).setOpcode(INVOKESTATIC);
            case "FALSE_COUNTER"->begin.instructions.set(code(begin).get(7),new InsnNode(ICONST_0));
            case "DUPLICATE_OBSERVATION"->((FieldInsnNode)code(method(hooks,"starts")).get(0)).name="factor";
            case "INFERRED_LISTENER"->((MethodInsnNode)list.get(9)).desc="(Ljava/util/function/Consumer;)V";
            case "UNKNOWN_DEPENDENCY"->{
                var old=new String(entries.get("META-INF/mods.toml"),java.nio.charset.StandardCharsets.UTF_8);
                entries.put("META-INF/mods.toml",(old+"\n[[dependencies.legacybridge_serverconfig]]\nmodId=\"absent_mod\"\nmandatory=true\nversionRange=\"[1]\"\nordering=\"AFTER\"\nside=\"BOTH\"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            default->throw new AssertionError(defect);
        }
        entries.put(FLOW+".class",bytes(hooks));entries.put(ENTRY+".class",bytes(entry));
        if(!defect.equals("MISSING_CONFIG"))entries.put(EVENT+".class",bytes(config));
        var input=directory.resolve(defect+".jar");write(input,entries);var original=Files.readAllBytes(input);
        var output=directory.resolve("absent.jar");var proof=LogicPreservingConverter.planServerConfig(input,output,inputs());
        assertFalse(proof.validated(),defect+proof.report());assertTrue(proof.candidateSha256().isEmpty());
        assertFalse(proof.report().get("diagnostics").toString().contains("CLIENT_CONFIG_UNSUPPORTED"), "SERVER refusal must identify its own contract");
        assertFalse(Files.exists(output));assertArrayEquals(original,Files.readAllBytes(input));
    }

    @Test void offlineVerifierRederivesLifecycleInsteadOfTrustingPolicy() throws Exception {
        var entries=read();var scan=new LegacyReferenceScanner().scan(classes(entries));
        var mapping=dev.legacybridge.converter.conversion.logic.VerifiedLogicMappings.compileServerConfig(scan.classNodes(),scan,inputs());
        var api=mapping.sourceApi();var archive=classes(entries);
        assertThrows(TransformationException.class,()->new OfflineLinkageVerifier().verify(archive,api,OfflineLinkageVerifier.CallbackPolicy.LIFECYCLE));
        var hooks=node(entries,FLOW);var end=method(hooks,"stopped");end.instructions.set(code(end).get(0),new InsnNode(ICONST_1));archive.put(FLOW+".class",bytes(hooks));
        assertThrows(TransformationException.class,()->new OfflineLinkageVerifier().verify(archive,api,OfflineLinkageVerifier.CallbackPolicy.SERVER_CONFIG));
    }

    private static void replaceEvent(ClassNode n,String from,String to) {
        var copy=new ClassNode();n.accept(new ClassRemapper(copy,new SimpleRemapper(Map.of(from,to))));
        n.methods=copy.methods;
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
            assertThrows(OperationStoppedException.class,()->LogicPreservingConverter.planServerConfig(fixture(),output,inputs()));
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
