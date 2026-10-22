package dev.legacybridge.converter.cli;

import dev.legacybridge.converter.conversion.*;
import dev.legacybridge.converter.conversion.logic.VerifiedLogicMappings;
import dev.legacybridge.converter.mapping.source.*;
import dev.legacybridge.converter.operation.*;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.service.*;
import dev.legacybridge.converter.transformation.*;
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
class DurableItemIntegrationTest {
    private static final String ENTRY="dev/legacybridge/fixture/durable/DurableContent";
    private static final String ITEM="dev/legacybridge/fixture/durable/RepairableItem";
    private static final String BASE="net/minecraft/item/Item", STACK="net/minecraft/item/ItemStack";
    @TempDir Path directory;
    private static MappingInputs mappings;
    private static Path profile(){return Path.of(System.getProperty("legacybridge.logicMappingProfile"));}
    private static MappingInputs inputs()throws Exception{if(mappings==null)mappings=new MappingInputsLoader().load(profile());return mappings;}
    @AfterAll static void release(){mappings=null;}
    private static Path fixture(){return Path.of(System.getProperty("legacybridge.fixtureRoot"),"durable-item-1.16.5/build/libs/legacybridge-durable-1.16.5-1.0.0.jar");}
    @Test @Order(1) void boundedServiceParityAndUnchangedPublicProfiles()throws Exception{
        var output=directory.resolve("absent.jar");CompositionCandidate proof;
        try(var service=new LegacyEngineService()){
            var request=new ConversionRequest(fixture(),output,MappingData.profile(profile()),
                Optional.of(hash(Files.readAllBytes(fixture()))),ConversionProfile.LOGIC_PRESERVING_V1);
            var result=service.planDurableItem(request).completion().toCompletableFuture().get(90,TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED,result.status(),()->result.toString());proof=result.value().orElseThrow();
            assertThrows(IllegalArgumentException.class,()->service.planDurableItem(new ConversionRequest(fixture(),output,MappingData.profile(profile()))));
        }
        assertEquals(proof,LogicPreservingConverter.planDurableItem(fixture(),output,inputs()));
        assertEquals(6,ConversionProfile.values().length);
        for(var p:ConversionProfile.values()){
            var result=switch(p){
                case SIMPLE_CONTENT_V1,DATAPACK_RECIPES_V1->new SimpleContentConverter(p).convert(fixture(),output,inputs());
                default->new LogicPreservingConverter(p).convert(fixture(),output,inputs());
            };assertFalse(result.converted(),p.toString());
        }
        assertFalse(LogicPreservingConverter.planLogicWithRecipes(fixture(),output,inputs()).validated());
        assertFalse(LogicPreservingConverter.planLifecycleSuite(fixture(),output,inputs()).validated());
        assertFalse(Files.exists(output));
    }
    @Test void realCandidateRetainsBodiesResourcesAndHasNoPublicationAuthority()throws Exception{
        var before=Files.readAllBytes(fixture());var output=directory.resolve("absent.jar");
        var proof=LogicPreservingConverter.planDurableItem(fixture(),output,inputs());
        assertTrue(proof.validated(),()->proof.report().toString());assertFalse(proof.publicationAllowed());
        assertTrue(proof.candidateSha256().isPresent());assertFalse(Files.exists(output));
        assertArrayEquals(before,Files.readAllBytes(fixture()));
        var r=proof.report();
        assertEquals("LEGACY_DURABLE_ITEM_CANDIDATE",r.get("reportType"));
        assertEquals("durable-item-candidate-v1",r.get("candidateIdentity"));
        assertEquals("NOT_RUN",r.get("runtimeValidation"));
        assertEquals(2,r.get("preservedClassCount"));assertEquals(4,r.get("preservedMethodCount"));
        assertEquals(0,r.get("replacedClassCount"));assertEquals(false,r.get("bytecodeExecuted"));
        assertEquals("PASSED_EXACT_RULES",r.get("durableItemMappingCommutation"));
        assertEquals(r.get("sourceDurableItemContract"),r.get("durableItemContract"));
        assertEquals(r.get("durableItemContract"),r.get("reopenedDurableItemContract"));
        var contract=(Map<?,?>)r.get("durableItemContract");
        assertEquals(128,contract.get("durability"));assertEquals(1,contract.get("maxStackSize"));
        assertEquals("minecraft:iron_ingot",contract.get("repairIngredient"));
        assertEquals("PASSED_SCOPED",((Map<?,?>)r.get("serializedOfflineLinkage")).get("status"));
        assertEquals(true,((Map<?,?>)r.get("candidateArchiveValidation")).get("scratchDeleted"));
        for(var raw:(List<?>)((Map<?,?>)r.get("artifactPlan")).get("entries")){
            var e=(Map<?,?>)raw;var path=(String)e.get("path");
            if(path.startsWith("assets/")||path.startsWith("data/")){
                assertEquals("PRESERVE_BYTES",e.get("disposition"));assertEquals(e.get("sourceSha256"),e.get("targetSha256"));
            }
        }
        for(var raw:(List<?>)((Map<?,?>)r.get("transformationLog")).get("entries")){
            var e=(Map<?,?>)raw;assertEquals("VERIFIED",e.get("confidence"));assertNotNull(e.get("adapter"));
        }
        assertEquals(proof,LogicPreservingConverter.planDurableItem(fixture(),output,inputs()));
        System.out.println("DURABLE_ITEM_CANDIDATE "+new CanonicalReportWriter().toJsonTree(r));
    }
    @Test void sourceAdmissionIsImmutableAndRecipeCompilerRemainsOptIn()throws Exception{
        var scan=new LegacyReferenceScanner().scan(classes(read()));var nodes=scan.classNodes();
        var before=new TreeMap<String,byte[]>();nodes.forEach((k,v)->before.put(k,bytes(v)));
        assertEquals(DurableItemContract.inspect(nodes),DurableItemContract.inspect(nodes));
        for(var e:nodes.entrySet())assertArrayEquals(before.get(e.getKey()),bytes(e.getValue()));
        assertThrows(TransformationException.class,()->VerifiedLogicMappings.compile(nodes,scan,inputs()));
        var rules=VerifiedLogicMappings.compileDurableItem(nodes,scan,inputs());
        assertTrue(rules.rules().toString().contains("durable.iron-ingredient-virtual-override.v1"));
        assertTrue(rules.rules().toString().contains("durable.native-iron-ingredient.v1"));
    }
    @ParameterizedTest @ValueSource(ints={1,32767})
    void boundedLiteralsRemainDataNotFixtureHashWhitelist(int durability)throws Exception{
        var entries=read();var item=node(entries,ITEM);var ctor=method(item,"<init>");
        ctor.instructions.set(code(ctor).get(4),new LdcInsnNode(durability));entries.put(ITEM+".class",bytes(item));
        var path=directory.resolve("literal.jar");write(path,entries);
        var proof=LogicPreservingConverter.planDurableItem(path,directory.resolve("absent.jar"),inputs());
        assertTrue(proof.validated(),()->proof.report().toString());
        assertEquals(durability,((Map<?,?>)proof.report().get("durableItemContract")).get("durability"));
    }
    @Test void renamedOwnedClassesAndRegistrationFieldsRetainSemantics()throws Exception{
        var entries=read();var renames=Map.of(ENTRY,"example/NamedContent",ITEM,"example/NamedItem");
        for(String name:List.of(ENTRY,ITEM)){
            var mapped=new ClassNode();node(entries,name).accept(new ClassRemapper(mapped,new Remapper(){
                @Override public String map(String n){return renames.getOrDefault(n,n);}
                @Override public String mapFieldName(String owner,String name,String desc){return renames.containsKey(owner)?"renamed_"+name:name;}
            }));entries.remove(name+".class");entries.put(mapped.name+".class",bytes(mapped));
        }
        var path=directory.resolve("renamed.jar");write(path,entries);
        var proof=LogicPreservingConverter.planDurableItem(path,directory.resolve("absent.jar"),inputs());
        assertTrue(proof.validated(),()->proof.report().toString());
    }
    @ParameterizedTest @ValueSource(strings={"zero","negative","huge","stacksTo","defaultDurability","noRepair","remainder",
        "wrongIngredient","wrongGetter","firstArgument","invert","wrongBranch","wrongReturn","extraState","extraHelper",
        "extraOverride","extraInterface","notFinal","constructorArgument","handler","unknownCall","earlyRegistry",
        "foreignDependency","badResource","missingModel","missingRecipe","wrongOpcode","foreignAnnotation"})
    void rejectsUnaccountedContractsWithoutOutput(String mutation)throws Exception{
        var entries=read();var item=node(entries,ITEM);var ctor=method(item,"<init>");
        var repair=method(item,"func_82789_a");var p=code(repair);var c=code(ctor);
        switch(mutation){
            case "zero","negative","huge"->ctor.instructions.set(c.get(4),new LdcInsnNode(mutation.equals("zero")?0:mutation.equals("negative")?-1:32768));
            case "stacksTo"->((MethodInsnNode)c.get(5)).name="func_200917_a";
            case "defaultDurability"->((MethodInsnNode)c.get(5)).name="func_200915_b";
            case "noRepair"->ctor.instructions.insert(c.get(5),new MethodInsnNode(INVOKEVIRTUAL,BASE+"$Properties","setNoRepair","()L"+BASE+"$Properties;",false));
            case "remainder"->ctor.instructions.insert(c.get(5),new MethodInsnNode(INVOKEVIRTUAL,BASE+"$Properties","func_200919_a","(L"+BASE+";)L"+BASE+"$Properties;",false));
            case "wrongIngredient"->((FieldInsnNode)p.get(2)).name="field_151043_k";
            case "wrongGetter"->((MethodInsnNode)p.get(1)).name="unreviewed";
            case "firstArgument"->((VarInsnNode)p.get(0)).var=1;
            case "invert"->repair.instructions.set(p.get(3),new JumpInsnNode(IF_ACMPEQ,((JumpInsnNode)p.get(3)).label));
            case "wrongBranch"->((JumpInsnNode)p.get(3)).label=((JumpInsnNode)p.get(5)).label;
            case "wrongReturn"->repair.instructions.set(p.get(4),new InsnNode(ICONST_0));
            case "extraState"->item.fields.add(new FieldNode(ACC_PUBLIC,"shared","I",null,null));
            case "extraHelper"->{var m=new MethodNode(ACC_PRIVATE|ACC_STATIC,"helper","()V",null,null);m.instructions.add(new InsnNode(RETURN));item.methods.add(m);}
            case "extraOverride"->{var m=new MethodNode(ACC_PUBLIC,"isRepairable","(L"+STACK+";)Z",null,null);m.instructions.add(new InsnNode(ICONST_1));m.instructions.add(new InsnNode(IRETURN));item.methods.add(m);}
            case "extraInterface"->item.interfaces.add("java/util/function/Supplier");
            case "notFinal"->item.access &= ~ACC_FINAL;
            case "constructorArgument"->ctor.desc="(I)V";
            case "handler"->{var start=new LabelNode();var end=new LabelNode();ctor.instructions.insert(start);ctor.instructions.add(end);ctor.tryCatchBlocks.add(new TryCatchBlockNode(start,end,end,"java/lang/Exception"));}
            case "unknownCall"->repair.instructions.insertBefore(p.get(0),new MethodInsnNode(INVOKESTATIC,"java/lang/System","gc","()V",false));
            case "earlyRegistry"->{var entry=node(entries,ENTRY);var init=method(entry,"<clinit>");
                init.instructions.insertBefore(code(init).get(9),new FieldInsnNode(GETSTATIC,ENTRY,"TOKEN","Lnet/minecraftforge/fml/RegistryObject;"));
                entries.put(ENTRY+".class",bytes(entry));}
            case "foreignDependency"->{var key="META-INF/mods.toml";entries.put(key,(new String(entries.get(key),java.nio.charset.StandardCharsets.UTF_8)
                +"\n[[dependencies.legacybridge_durable]]\nmodId=\"unknown_dependency\"\nmandatory=true\nversionRange=\"[1,)\"\nordering=\"AFTER\"\nside=\"BOTH\"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            case "badResource"->entries.put("data/legacybridge_durable/recipes/token.json","{\"type\":\"unknown:serializer\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            case "missingModel"->entries.remove("assets/legacybridge_durable/models/item/token.json");
            case "missingRecipe"->entries.remove("data/legacybridge_durable/recipes/token.json");
            case "wrongOpcode"->repair.instructions.set(p.get(1),new MethodInsnNode(INVOKESTATIC,STACK,"func_77973_b","()L"+BASE+";",false));
            case "foreignAnnotation"->{item.visibleAnnotations=new ArrayList<>();item.visibleAnnotations.add(new AnnotationNode("Lexample/Unknown;"));}
            default->fail(mutation);
        }
        entries.put(ITEM+".class",bytes(item));var path=directory.resolve(mutation+".jar");write(path,entries);
        var before=Files.readAllBytes(path);var output=directory.resolve("absent.jar");
        var result=LogicPreservingConverter.planDurableItem(path,output,inputs());
        assertFalse(result.validated(),mutation);assertTrue(result.candidateSha256().isEmpty());
        assertFalse(Files.exists(output));assertArrayEquals(before,Files.readAllBytes(path));
        assertEquals(false,result.report().get("transformationsCommitted"));
    }
    @ParameterizedTest @ValueSource(strings={"sourceDurabilityMissing","targetDurabilityStatic","sourceRepairFinal","targetRepairFinal",
        "targetGetterPrivate","targetIronInstance","sourceIronMissing","missingTargetItem","targetIronMutable","targetStackExtensible"})
    void realDeclarationChangesBlockInsteadOfPromotingConfidence(String mutation)throws Exception{
        var original=inputs();boolean old=mutation.startsWith("source");
        var api=old?original.legacyApi():original.targetApi();var index=new TreeMap<>(api.classes());
        String prefix=old?"net/minecraft/item/":"net/minecraft/world/item/";
        String owner=prefix+(mutation.contains("Durability")?"Item$Properties":(mutation.contains("Getter")||mutation.contains("Stack"))?"ItemStack":mutation.contains("Iron")?"Items":"Item");
        var type=index.get(owner);assertNotNull(type);
        if(mutation.equals("missingTargetItem"))index.remove(owner);
        else if(mutation.equals("targetStackExtensible"))index.put(owner,new ApiIndex.ApiClass(type.name(),type.access() & ~ACC_FINAL,type.superName(),type.interfaces(),type.methods(),type.fields()));
        else {
            var fields=new ArrayList<>(type.fields());var methods=new ArrayList<>(type.methods());
            boolean field=mutation.contains("Iron");var members=field?fields:methods;
            Set<String> names=mutation.contains("Durability")?Set.of("durability","func_200918_c","m_41503_"):
                mutation.contains("Repair")?Set.of("isValidRepairItem","func_82789_a","m_6832_"):
                mutation.contains("Getter")?Set.of("getItem","func_77973_b","m_41720_"):Set.of("IRON_INGOT","field_151042_j","f_42416_");
            var found=members.stream().filter(m->names.contains(m.name())).toList();assertEquals(1,found.size());
            var member=found.get(0);members.remove(member);
            if(!mutation.endsWith("Missing")){
                int access=member.access();
                if(mutation.endsWith("Static"))access|=ACC_STATIC;
                if(mutation.endsWith("Final"))access|=ACC_FINAL;
                if(mutation.endsWith("Private"))access=(access & ~ACC_PUBLIC)|ACC_PRIVATE;
                if(mutation.endsWith("Instance"))access &= ~ACC_STATIC;
                if(mutation.endsWith("Mutable"))access &= ~ACC_FINAL;
                members.add(new ApiIndex.ApiMember(member.name(),member.descriptor(),access));
            }
            index.put(owner,new ApiIndex.ApiClass(type.name(),type.access(),type.superName(),type.interfaces(),methods,fields));
        }
        var modified=new ApiIndex(index);
        var data=new MappingInputs(original.legacyOfficial(),original.targetOfficial(),original.legacySrg(),original.targetSrg(),
            old?modified:original.legacyApi(),old?original.targetApi():modified,original.sources(),original.diagnostics(),original.complete());
        var output=directory.resolve("absent.jar");
        var result=LogicPreservingConverter.planDurableItem(fixture(),output,data);
        assertFalse(result.validated(),mutation);assertTrue(result.candidateSha256().isEmpty());assertFalse(Files.exists(output));
    }
    @Test void staleSelectionCannotAuthorizeCandidate()throws Exception{
        try(var service=new LegacyEngineService()){
            var path=directory.resolve("absent.jar");
            var request=new ConversionRequest(fixture(),path,MappingData.profile(profile()),Optional.of("0".repeat(64)),ConversionProfile.LOGIC_PRESERVING_V1);
            var result=service.planDurableItem(request).completion().toCompletableFuture().get(90,TimeUnit.SECONDS);
            assertNotEquals(EngineOutcome.Status.COMPLETED,result.status());assertFalse(Files.exists(path));
        }
    }
    @ParameterizedTest @ValueSource(strings={"SERIALIZATION","REOPEN","FINAL_LINKAGE","PREPARED"})
    void cancellationCannotPublishOrLeaveCandidateScratch(String point)throws Exception{
        inputs();int[] validation={0};var ref=new AtomicReference<OperationControl>();var observed=new AtomicReference<Set<Path>>(Set.of());var before=scratch();
        var control=new OperationControl(OperationLimits.defaults(),e->{
            boolean stop=e.stage()==OperationStage.SERIALIZATION && point.equals("SERIALIZATION")
                || e.stage()==OperationStage.PREPARED && point.equals("PREPARED");
            if(point.equals("REOPEN") && e.stage()==OperationStage.ARCHIVE_SCAN){
                var now=new HashSet<>(scratch());now.removeAll(before);if(!now.isEmpty()){observed.set(now);stop=true;}
            }
            if(e.stage()==OperationStage.BYTECODE_VALIDATION && ++validation[0]==3 && point.equals("FINAL_LINKAGE"))stop=true;
            if(stop)ref.get().requestCancel();
        });ref.set(control);var output=directory.resolve("absent.jar");
        try(var scope=control.enter()){assertThrows(OperationStoppedException.class,()->LogicPreservingConverter.planDurableItem(fixture(),output,inputs()));}
        assertFalse(Files.exists(output));
        if(point.equals("REOPEN")){assertFalse(observed.get().isEmpty());for(var path:observed.get())assertFalse(Files.exists(path));}
    }
    private static Set<Path> scratch(){
        try(var files=Files.list(Path.of(System.getProperty("java.io.tmpdir")))){
            return files.filter(p->p.getFileName().toString().startsWith("legacybridge-compose-")).collect(java.util.stream.Collectors.toSet());
        }catch(java.io.IOException e){throw new AssertionError(e);}
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
