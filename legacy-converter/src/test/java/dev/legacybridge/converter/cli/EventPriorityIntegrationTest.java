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
class EventPriorityIntegrationTest {
    private static final String ENTRY="dev/legacybridge/fixture/events/EventContent";
    private static final String FLOW="dev/legacybridge/fixture/events/EventFlow";
    private static final String EVENT="dev/legacybridge/fixture/events/DecisionEvent";
    private static final String ITEM="dev/legacybridge/fixture/events/EventItem";
    @TempDir Path directory;
    private static MappingInputs mappings;
    private static Path profile(){return Path.of(System.getProperty("legacybridge.logicMappingProfile"));}
    private static MappingInputs inputs() throws Exception {if(mappings==null)mappings=new MappingInputsLoader().load(profile());return mappings;}
    @AfterAll static void release(){mappings=null;}
    private static Path fixture(){return Path.of(System.getProperty("legacybridge.fixtureRoot"),"event-priority-1.16.5/build/libs/legacybridge-event-priority-1.16.5-1.0.0.jar");}

    @Test @Order(1) void serviceParityAndNoPublicPromotion() throws Exception {
        var output=directory.resolve("absent.jar"); CompositionCandidate proof;
        try(var service=new LegacyEngineService()){
            var request=new ConversionRequest(fixture(),output,MappingData.profile(profile()),Optional.of(hash(Files.readAllBytes(fixture()))),ConversionProfile.LIFECYCLE_PRESERVING_V1);
            var result=service.planEventPriority(request).completion().toCompletableFuture().get(90,TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED,result.status(),result.toString());proof=result.value().orElseThrow();
            assertThrows(IllegalArgumentException.class,()->service.planEventPriority(new ConversionRequest(fixture(),output,MappingData.profile(profile()))));
        }
        assertEquals(proof,LogicPreservingConverter.planEventPriority(fixture(),output,inputs()));
        for(var p:ConversionProfile.values()){
            var old=switch(p){
                case SIMPLE_CONTENT_V1,DATAPACK_RECIPES_V1->new SimpleContentConverter(p).convert(fixture(),output,inputs());
                default->new LogicPreservingConverter(p).convert(fixture(),output,inputs());
            };
            assertFalse(old.converted(),p.toString());
        }
        assertFalse(LogicPreservingConverter.planLogicWithRecipes(fixture(),output,inputs()).validated());
        assertFalse(LogicPreservingConverter.planOwnedConstructors(fixture(),output,inputs()).validated());
        assertFalse(LogicPreservingConverter.planOwnedHierarchy(fixture(),output,inputs()).validated());
        assertFalse(LogicPreservingConverter.planOwnedInterfaceDefaults(fixture(),output,inputs()).validated());
        assertFalse(LogicPreservingConverter.planOwnedReceiverHandles(fixture(),output,inputs()).validated());
        assertFalse(Files.exists(output));assertEquals(6,ConversionProfile.values().length);
    }

    @Test void realForgeArchivePreservesFullGraphAndResources() throws Exception {
        var before=Files.readAllBytes(fixture());var output=directory.resolve("absent.jar");
        var proof=LogicPreservingConverter.planEventPriority(fixture(),output,inputs());
        assertTrue(proof.validated(),proof.report().toString());assertFalse(proof.publicationAllowed());
        assertTrue(proof.candidateSha256().isPresent());assertFalse(Files.exists(output));assertArrayEquals(before,Files.readAllBytes(fixture()));
        var r=proof.report();assertEquals("LEGACY_EVENT_PRIORITY_CANDIDATE",r.get("reportType"));
        assertEquals("event-priority-candidate-v1",r.get("candidateIdentity"));assertEquals("NOT_RUN",r.get("runtimeValidation"));
        assertEquals(4,r.get("preservedClassCount"));assertEquals(12,r.get("preservedMethodCount"));assertEquals(0,r.get("replacedClassCount"));
        assertEquals("PASSED_EXACT_RULES",r.get("eventMappingCommutation"));
        assertEquals(r.get("sourceEventContract"),r.get("eventContract"));assertEquals(r.get("eventContract"),r.get("reopenedEventContract"));
        assertEquals(12,((Map<?,?>)((Map<?,?>)r.get("eventContract")).get("isolatedOracle")).get("afterWorkTrace"));
        assertEquals("PASSED_SCOPED",((Map<?,?>)r.get("serializedOfflineLinkage")).get("status"));
        assertEquals(5,((Map<?,?>)r.get("serializedOfflineLinkage")).get("lambdaSiteCount"));
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
        assertEquals(proof,LogicPreservingConverter.planEventPriority(fixture(),output,inputs()));
        System.out.println("EVENT_PRIORITY_CANDIDATE "+new CanonicalReportWriter().toJsonTree(r));
    }

    @Test void falseReceiveCancelledRemainsFalseWithDistinctExpectedOracle() throws Exception {
        var entries=read();var flow=node(entries,FLOW);var install=code(method(flow,"install"));
        method(flow,"install").instructions.set(install.get(8),new InsnNode(ICONST_0));
        entries.put(FLOW+".class",bytes(flow));var input=directory.resolve("false.jar");write(input,entries);
        var proof=LogicPreservingConverter.planEventPriority(input,directory.resolve("absent.jar"),inputs());
        assertTrue(proof.validated(),proof.report().toString());
        assertEquals(1,((Map<?,?>)((Map<?,?>)proof.report().get("eventContract")).get("isolatedOracle")).get("afterWorkTrace"));
    }

    @Test void admissionUsesRolesNotFixtureClassNames() throws Exception {
        var entries=read();var names=Map.of(ENTRY,ENTRY+"Renamed",FLOW,FLOW+"Renamed",EVENT,EVENT+"Renamed",ITEM,ITEM+"Renamed");
        for(String name:names.keySet()){
            var n=node(entries,name);var writer=new ClassWriter(0);n.accept(new ClassRemapper(writer,new SimpleRemapper(names)));
            entries.remove(name+".class");entries.put(names.get(name)+".class",writer.toByteArray());
        }
        var input=directory.resolve("renamed.jar");write(input,entries);
        var proof=LogicPreservingConverter.planEventPriority(input,directory.resolve("absent.jar"),inputs());
        assertTrue(proof.validated(),proof.report().toString());
    }

    @ParameterizedTest @ValueSource(strings={"NO_CANCELABLE","EVENT_OVERRIDE","EVENT_STATE","EVENT_CTOR_EFFECT","WRONG_BUS",
            "MOD_BUS_POST","WRONG_SIDE","INFERRED_LISTENER","MONITOR","SAME_PRIORITY","CANCEL_FALSE","RECEIVE_HIGH",
            "DUPLICATE_HANDLER","UNKNOWN_EXTERNAL","EARLY_REGISTRY","POST_DURING_SETUP","REGISTER_TWICE","QUEUE_TWICE",
            "STATIC_INITIALIZER","CROSS_THREAD","INSTANCE_HANDLER","BOXING","ALT_META","EXCEPTION_HANDLER",
            "UNKNOWN_ANNOTATION","POST_REUSED","PUBLIC_STATE","UNKNOWN_DEPENDENCY","EVENT_INHERITANCE","MISSING_EVENT","INVISIBLE_CANCELABLE","INVISIBLE_MOD"})
    void unknownOrSemanticallyDifferentEffectsBlockWholeCandidate(String defect) throws Exception {
        var entries=read();var flow=node(entries,FLOW);var entry=node(entries,ENTRY);var event=node(entries,EVENT);
        var install=method(flow,"install");var list=code(install);var ctor=method(entry,"<init>");var setup=method(entry,"setup");
        switch(defect){
            case "NO_CANCELABLE"->event.visibleAnnotations.clear();
            case "INVISIBLE_CANCELABLE"->{event.invisibleAnnotations=event.visibleAnnotations;event.visibleAnnotations=null;}
            case "INVISIBLE_MOD"->{entry.invisibleAnnotations=entry.visibleAnnotations;entry.visibleAnnotations=null;}
            case "EVENT_OVERRIDE"->{var m=new MethodNode(ACC_PUBLIC,"isCancelable","()Z",null,null);m.instructions.add(new InsnNode(ICONST_1));m.instructions.add(new InsnNode(IRETURN));event.methods.add(m);}
            case "EVENT_STATE"->event.fields.add(new FieldNode(ACC_PUBLIC,"value","I",null,null));
            case "EVENT_CTOR_EFFECT"->method(event,"<init>").instructions.insert(new MethodInsnNode(INVOKESTATIC,FLOW,"install","()V",false));
            case "WRONG_BUS"->((FieldInsnNode)list.get(0)).owner="unknown/ModBus";
            case "MOD_BUS_POST"->{
                var run=method(flow,"run");var first=code(run).get(0);
                run.instructions.insertBefore(first,new MethodInsnNode(INVOKESTATIC,"net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext","get","()Lnet/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext;",false));
                run.instructions.set(first,new MethodInsnNode(INVOKEVIRTUAL,"net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext","getModEventBus","()Lnet/minecraftforge/eventbus/api/IEventBus;",false));
            }
            case "WRONG_SIDE"->((LdcInsnNode)code(ctor).get(10)).cst=Type.getObjectType("net/minecraftforge/fml/event/lifecycle/FMLClientSetupEvent");
            case "INFERRED_LISTENER"->((MethodInsnNode)list.get(5)).desc="(Ljava/util/function/Consumer;)V";
            case "MONITOR"->((FieldInsnNode)list.get(1)).name="MONITOR";
            case "SAME_PRIORITY"->((FieldInsnNode)list.get(7)).name="HIGH";
            case "CANCEL_FALSE"->{var m=method(flow,"cancel");m.instructions.set(code(m).get(7),new InsnNode(ICONST_0));}
            case "RECEIVE_HIGH"->install.instructions.set(list.get(2),new InsnNode(ICONST_1));
            case "DUPLICATE_HANDLER"->((InvokeDynamicInsnNode)list.get(10)).bsmArgs[1]=((InvokeDynamicInsnNode)list.get(4)).bsmArgs[1];
            case "UNKNOWN_EXTERNAL"->method(flow,"observe").instructions.insert(new MethodInsnNode(INVOKESTATIC,"unknown/Api","run","()V",false));
            case "EARLY_REGISTRY"->{
                var extra=new InsnList();
                extra.add(new FieldInsnNode(GETSTATIC,ENTRY,"TOKEN","Lnet/minecraftforge/fml/RegistryObject;"));
                extra.add(new MethodInsnNode(INVOKEVIRTUAL,"net/minecraftforge/fml/RegistryObject","get","()Lnet/minecraftforge/registries/IForgeRegistryEntry;",false));
                extra.add(new InsnNode(POP));ctor.instructions.insertBefore(code(ctor).get(14),extra);
            }
            case "POST_DURING_SETUP"->{
                setup.instructions.clear();setup.localVariables.clear();
                setup.instructions.add(new MethodInsnNode(INVOKESTATIC,FLOW,"run","()V",false));setup.instructions.add(new InsnNode(RETURN));
            }
            case "REGISTER_TWICE"->ctor.instructions.insertBefore(code(ctor).get(14),new MethodInsnNode(INVOKESTATIC,FLOW,"install","()V",false));
            case "QUEUE_TWICE"->setup.instructions.insertBefore(code(setup).get(4),new MethodInsnNode(INVOKESTATIC,FLOW,"run","()V",false));
            case "STATIC_INITIALIZER"->{var m=new MethodNode(ACC_STATIC,"<clinit>","()V",null,null);m.instructions.add(new InsnNode(RETURN));flow.methods.add(m);}
            case "CROSS_THREAD"->method(flow,"run").instructions.insert(new MethodInsnNode(INVOKESTATIC,"java/lang/Thread","yield","()V",false));
            case "INSTANCE_HANDLER"->method(flow,"cancel").access=ACC_PRIVATE;
            case "BOXING"->((InvokeDynamicInsnNode)list.get(4)).bsmArgs[2]=Type.getMethodType("(Ljava/lang/Object;)V");
            case "ALT_META"->{var d=(InvokeDynamicInsnNode)list.get(4);d.bsm=new Handle(H_INVOKESTATIC,d.bsm.getOwner(),"altMetafactory",d.bsm.getDesc(),false);}
            case "EXCEPTION_HANDLER"->method(flow,"cancel").exceptions.add("java/lang/Exception");
            case "UNKNOWN_ANNOTATION"->event.visibleAnnotations.add(new AnnotationNode("Lunknown/Annotation;"));
            case "POST_REUSED"->{
                var run=method(flow,"run");run.instructions.insertBefore(code(run).get(6),new MethodInsnNode(INVOKESTATIC,FLOW,"run","()V",false));
            }
            case "PUBLIC_STATE"->flow.fields.get(0).access=ACC_PUBLIC|ACC_STATIC;
            case "UNKNOWN_DEPENDENCY"->{
                var old=new String(entries.get("META-INF/mods.toml"),java.nio.charset.StandardCharsets.UTF_8);
                entries.put("META-INF/mods.toml",(old+"\n[[dependencies.legacybridge_events]]\nmodId=\"absent_mod\"\nmandatory=true\nversionRange=\"[1]\"\nordering=\"AFTER\"\nside=\"BOTH\"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            case "EVENT_INHERITANCE"->event.access&=~ACC_FINAL;
            case "MISSING_EVENT"->entries.remove(EVENT+".class");
            default->throw new AssertionError(defect);
        }
        entries.put(FLOW+".class",bytes(flow));entries.put(ENTRY+".class",bytes(entry));
        if(!defect.equals("MISSING_EVENT"))entries.put(EVENT+".class",bytes(event));
        var input=directory.resolve(defect+".jar");write(input,entries);byte[] original=Files.readAllBytes(input);
        var output=directory.resolve("absent.jar");var proof=LogicPreservingConverter.planEventPriority(input,output,inputs());
        assertFalse(proof.validated(),defect+proof.report());assertTrue(proof.candidateSha256().isEmpty());
        if(defect.equals("EARLY_REGISTRY")) assertTrue(proof.report().get("diagnostics").toString().contains("Constructor registration/listener/install envelope"));
        assertFalse(Files.exists(output));assertArrayEquals(original,Files.readAllBytes(input));
    }

    @Test void offlineVerifierRederivesEventProofInsteadOfTrustingPolicy() throws Exception {
        var entries=read();var scan=new LegacyReferenceScanner().scan(classes(entries));
        var mappings=dev.legacybridge.converter.conversion.logic.VerifiedLogicMappings.compileEvents(scan.classNodes(),scan,inputs());
        var api=mappings.sourceApi();var bytes=classes(entries);
        assertThrows(TransformationException.class,()->new OfflineLinkageVerifier().verify(bytes,api,OfflineLinkageVerifier.CallbackPolicy.LIFECYCLE));
        var e=node(entries,EVENT);e.visibleAnnotations.clear();bytes.put(EVENT+".class",bytes(e));
        assertThrows(TransformationException.class,()->new OfflineLinkageVerifier().verify(bytes,api,OfflineLinkageVerifier.CallbackPolicy.OWNED_EVENT_PRIORITY));
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
            assertThrows(OperationStoppedException.class,()->LogicPreservingConverter.planEventPriority(fixture(),output,inputs()));
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
