package dev.legacybridge.converter.cli;

import dev.legacybridge.converter.conversion.*;
import dev.legacybridge.converter.mapping.source.*;
import dev.legacybridge.converter.operation.*;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.service.*;
import dev.legacybridge.converter.transformation.*;
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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/** Actual reobfuscated archive and explicit pinned API declarations; original classes never execute. */
@EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment", matches="true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BooleanToggleBlockIntegrationTest {
    private static final String ENTRY="dev/legacybridge/fixture/booleantoggle/BooleanToggleContent";
    private static final String OWNED_BLOCK="dev/legacybridge/fixture/booleantoggle/BooleanToggleBlock";
    private static final String MOD="legacybridge_boolean_toggle";
    private static final String BLOCK="net/minecraft/block/Block";
    private static final String ITEM="net/minecraft/item/Item";
    private static final String PROPS="net/minecraft/block/AbstractBlock$Properties";
    private static final String MATERIAL="net/minecraft/block/material/Material";
    private static final String REGISTRY="net/minecraftforge/registries/DeferredRegister";
    private static final String REGISTRY_OBJECT="net/minecraftforge/fml/RegistryObject";
    private static final String MINING_TAG="data/minecraft/tags/blocks/mineable/pickaxe.json";
    @TempDir Path directory;
    private static MappingInputs mappings;
    private static Path profile(){return Path.of(System.getProperty("legacybridge.logicMappingProfile"));}
    private static MappingInputs inputs()throws Exception{if(mappings==null)mappings=new MappingInputsLoader().load(profile());return mappings;}
    @AfterAll static void release(){mappings=null;}
    private static Path fixture(){return Path.of(System.getProperty("legacybridge.fixtureRoot"),"boolean-toggle-block-1.16.5/build/libs/legacybridge-boolean-toggle-block-1.16.5-1.0.0.jar");}

    @Test @Order(1) void boundedServiceParityAndUnchangedPublicProfiles()throws Exception{
        var output=directory.resolve("absent.jar");CompositionCandidate proof;
        try(var service=new LegacyEngineService()){
            var request=new ConversionRequest(fixture(),output,MappingData.profile(profile()),
                Optional.of(hash(Files.readAllBytes(fixture()))),ConversionProfile.LOGIC_PRESERVING_V1);
            var result=service.planBooleanToggleBlock(request).completion().toCompletableFuture().get(90,TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED,result.status(),()->result.toString());proof=result.value().orElseThrow();
            assertThrows(IllegalArgumentException.class,()->service.planBooleanToggleBlock(new ConversionRequest(fixture(),output,MappingData.profile(profile()))));
        }
        assertEquals(proof,LogicPreservingConverter.planBooleanToggleBlock(fixture(),output,inputs()));
        assertEquals(6,ConversionProfile.values().length);
        assertFalse(LogicPreservingConverter.planLogicWithRecipes(fixture(),output,inputs()).validated());
        assertFalse(LogicPreservingConverter.planDurableItem(fixture(),output,inputs()).validated());
        assertFalse(LogicPreservingConverter.planPlainBlock(fixture(),output,inputs()).validated());
        assertFalse(LogicPreservingConverter.planHorizontalBlock(fixture(),output,inputs()).validated());
        assertFalse(LogicPreservingConverter.planStaticShapeBlock(fixture(),output,inputs()).validated());
        assertFalse(Files.exists(output));
    }

    @ParameterizedTest @EnumSource(ConversionProfile.class)
    void everyPublicProfileRefusesPlanningAndPublicationThroughActualServiceDispatch(ConversionProfile profile)throws Exception{
        byte[] original=Files.readAllBytes(fixture());
        var output=directory.resolve("public-"+profile.id()+".jar");
        var request=new ConversionRequest(fixture(),output,MappingData.profile(profile()),
            Optional.of(hash(original)),profile);
        try(var service=new LegacyEngineService()){
            // Includes SIMPLE_CONTENT_V1 and DATAPACK_RECIPES_V1, which dispatch through
            // StaticContentExtractor rather than LogicPreservingConverter. Neither may
            // replace this custom behavior with a declarative plain-block bootstrap.
            var planned=service.plan(request).completion().toCompletableFuture().get(90,TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.BLOCKED,planned.status(),()->profile+": "+planned);
            assertFalse(planned.value().orElseThrow().convertible());
            assertTrue(planned.value().orElseThrow().candidateSha256().isEmpty());
            assertFalse(Files.exists(output));
            var converted=service.convert(request).completion().toCompletableFuture().get(90,TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.BLOCKED,converted.status(),()->profile+": "+converted);
            assertFalse(converted.value().orElseThrow().converted());
            assertFalse(Files.exists(output));
        }
        assertArrayEquals(original,Files.readAllBytes(fixture()));
    }

    @Test void realCandidateRetainsBodiesResourcesAndHasNoPublicationAuthority()throws Exception{
        var before=Files.readAllBytes(fixture());var output=directory.resolve("absent.jar");
        var proof=LogicPreservingConverter.planBooleanToggleBlock(fixture(),output,inputs());
        assertTrue(proof.validated(),()->proof.report().toString());assertFalse(proof.publicationAllowed());
        assertTrue(proof.candidateSha256().isPresent());assertFalse(Files.exists(output));
        assertArrayEquals(before,Files.readAllBytes(fixture()));assertFalse(read().containsKey(MINING_TAG));
        var r=proof.report();
        assertEquals("LEGACY_BOOLEAN_TOGGLE_BLOCK_CANDIDATE",r.get("reportType"));
        assertEquals("boolean-toggle-block-candidate-v1",r.get("candidateIdentity"));
        assertEquals("NOT_RUN",r.get("runtimeValidation"));
        assertEquals(2,r.get("preservedClassCount"));assertEquals(8,r.get("preservedMethodCount"));
        assertEquals(0,r.get("replacedClassCount"));assertEquals(false,r.get("bytecodeExecuted"));
        assertEquals("PASSED_EXACT_RULES",r.get("booleanToggleBlockMappingCommutation"));
        assertEquals(r.get("sourceBooleanToggleBlockContract"),r.get("booleanToggleBlockContract"));
        assertEquals(r.get("booleanToggleBlockContract"),r.get("reopenedBooleanToggleBlockContract"));
        assertNotNull(r.get("booleanToggleBlockContract"));
        assertEquals("PASSED_SCOPED",((Map<?,?>)r.get("serializedOfflineLinkage")).get("status"));
        assertEquals(true,((Map<?,?>)r.get("candidateArchiveValidation")).get("scratchDeleted"));
        int generatedMiningTags=0;
        for(var raw:(List<?>)((Map<?,?>)r.get("artifactPlan")).get("entries")){
            var e=(Map<?,?>)raw;var path=(String)e.get("path");
            if(path.equals(MINING_TAG)){
                assertEquals("GENERATE",e.get("disposition"));assertNull(e.get("sourceSha256"));
                assertNotNull(e.get("adapter"));assertNotNull(e.get("targetSha256"));generatedMiningTags++;
            }else if(path.startsWith("assets/")||path.startsWith("data/")){
                assertEquals("PRESERVE_BYTES",e.get("disposition"));assertEquals(e.get("sourceSha256"),e.get("targetSha256"));
            }
        }
        assertEquals(1,generatedMiningTags);
        boolean miningLogged=false;
        for(var raw:(List<?>)((Map<?,?>)r.get("transformationLog")).get("entries")){
            var e=(Map<?,?>)raw;assertEquals("VERIFIED",e.get("confidence"));assertNotNull(e.get("adapter"));
            if(e.toString().contains(MINING_TAG))miningLogged=true;
        }
        assertTrue(miningLogged,"Generated target mining behavior must have an explicit transformation log");
        assertEquals(proof,LogicPreservingConverter.planBooleanToggleBlock(fixture(),output,inputs()));
        System.out.println("BOOLEAN_TOGGLE_BLOCK_CANDIDATE "+new CanonicalReportWriter().toJsonTree(r));
    }

    @ParameterizedTest @ValueSource(ints={1,64})
    void boundedLiteralsRemainDataNotFixtureHashWhitelist(int stackSize)throws Exception{
        var entries=read();var entry=node(entries);var block=method(entry,"createBlock");var item=method(entry,"createBlockItem");
        float hardness=stackSize==1?0.0F:32767.0F,resistance=stackSize==1?32767.0F:0.0F;
        var strength=call(block,PROPS,m->m.desc.equals("(FF)L"+PROPS+";"));
        var oldResistance=previousCode(strength);var oldHardness=previousCode(oldResistance);
        block.instructions.set(oldHardness,new LdcInsnNode(hardness));block.instructions.set(oldResistance,new LdcInsnNode(resistance));
        var stacks=call(item,ITEM+"$Properties",m->m.desc.equals("(I)L"+ITEM+"$Properties;"));
        item.instructions.set(previousCode(stacks),new LdcInsnNode(stackSize));entries.put(ENTRY+".class",bytes(entry));
        var path=directory.resolve("literal.jar");write(path,entries);
        var proof=LogicPreservingConverter.planBooleanToggleBlock(path,directory.resolve("absent.jar"),inputs());
        assertTrue(proof.validated(),()->proof.report().toString());
        assertNotNull(proof.report().get("booleanToggleBlockContract"));
    }

    @Test void renamedOwnedClassesAndFieldsRetainSemantics()throws Exception{
        var entries=read();var declaredFields=new HashSet<String>();
        for(var owner:List.of(ENTRY,OWNED_BLOCK))for(var field:node(entries,owner).fields)
            declaredFields.add(owner+"."+field.name+":"+field.desc);
        var remapper=new Remapper(){
            @Override public String map(String n){return n.equals(ENTRY)?"example/NamedContent":n.equals(OWNED_BLOCK)?"example/NamedHorizontalBlock":n;}
            @Override public String mapFieldName(String owner,String name,String desc){return declaredFields.contains(owner+"."+name+":"+desc)?"renamed_"+name:name;}
        };
        for(var owner:List.of(ENTRY,OWNED_BLOCK)){
            var mapped=new ClassNode();node(entries,owner).accept(new ClassRemapper(mapped,remapper));
            entries.remove(owner+".class");entries.put(mapped.name+".class",bytes(mapped));
        }
        var path=directory.resolve("renamed.jar");write(path,entries);
        var proof=LogicPreservingConverter.planBooleanToggleBlock(path,directory.resolve("absent.jar"),inputs());
        assertTrue(proof.validated(),()->proof.report().toString());
    }

    @ParameterizedTest @ValueSource(strings={"materialWood","wrongPropertiesOverload","negativeStrength","nanStrength","infiniteResistance",
        "invalidStackSize","extraMethod","extraState","extraInterface","notFinal","constructorArgument","handler",
        "earlyGet","swappedRegistry","wrongBus","wrongBusOrder","wrongRegistryGet","wrongHelperHandle","unknownCall","wrongOpcode",
        "foreignDependency","globalMinecraftTag","missingRecipe","missingModel","unknownResource","unknownLoot","badResource","foreignAnnotation"})
    void rejectsUnaccountedContractsWithoutOutput(String mutation)throws Exception{
        var entries=read();var entry=node(entries);var block=method(entry,"createBlock");var item=method(entry,"createBlockItem");
        var constructor=method(entry,"<init>");var clinit=method(entry,"<clinit>");
        switch(mutation){
            case "materialWood"->field(block,MATERIAL).name="field_151575_d";
            case "wrongPropertiesOverload"->call(block,PROPS,m->m.getOpcode()==INVOKESTATIC).desc="(L"+MATERIAL+";Lnet/minecraft/block/material/MaterialColor;)L"+PROPS+";";
            case "negativeStrength","nanStrength","infiniteResistance"->{
                var strength=call(block,PROPS,m->m.desc.equals("(FF)L"+PROPS+";"));
                var operand=previousCode(strength);
                if(!mutation.equals("infiniteResistance"))operand=previousCode(operand);
                block.instructions.set(operand,new LdcInsnNode(mutation.equals("negativeStrength")?-1.0F:mutation.equals("nanStrength")?Float.NaN:Float.POSITIVE_INFINITY));
            }
            case "invalidStackSize"->{var stacks=call(item,ITEM+"$Properties",m->m.desc.equals("(I)L"+ITEM+"$Properties;"));item.instructions.set(previousCode(stacks),new InsnNode(ICONST_0));}
            case "extraMethod"->{var m=new MethodNode(ACC_PRIVATE|ACC_STATIC,"unreviewed","()V",null,null);m.instructions.add(new InsnNode(RETURN));entry.methods.add(m);}
            case "extraState"->entry.fields.add(new FieldNode(ACC_PUBLIC,"shared","I",null,null));
            case "extraInterface"->entry.interfaces.add("java/util/function/Supplier");
            case "notFinal"->entry.access &= ~ACC_FINAL;
            case "constructorArgument"->constructor.desc="(I)V";
            case "handler"->{var start=new LabelNode();var end=new LabelNode();constructor.instructions.insert(start);constructor.instructions.add(end);constructor.tryCatchBlocks.add(new TryCatchBlockNode(start,end,end,"java/lang/Exception"));}
            case "earlyGet"->{
                var sequence=new InsnList();sequence.add(new FieldInsnNode(GETSTATIC,ENTRY,"BLOCK","L"+REGISTRY_OBJECT+";"));
                var get=call(item,REGISTRY_OBJECT,m->m.name.equals("get"));sequence.add(new MethodInsnNode(get.getOpcode(),get.owner,get.name,get.desc,get.itf));
                sequence.add(new InsnNode(POP));clinit.instructions.insertBefore(code(clinit).get(code(clinit).size()-1),sequence);
            }
            case "swappedRegistry"->{var registry=field(clinit,"net/minecraftforge/registries/ForgeRegistries");registry.name="ITEMS";}
            case "wrongBus"->call(constructor,"net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext",m->m.name.equals("getModEventBus")).name="getForgeEventBus";
            case "wrongBusOrder"->{for(var insn:constructor.instructions)if(insn instanceof FieldInsnNode f && f.owner.equals(ENTRY)){if(f.name.equals("BLOCKS"))f.name="ITEMS";else if(f.name.equals("ITEMS"))f.name="BLOCKS";}}
            case "wrongRegistryGet"->field(item,ENTRY).name="BLOCK_ITEM";
            case "wrongHelperHandle"->{for(var insn:clinit.instructions)if(insn instanceof InvokeDynamicInsnNode indy){var h=(Handle)indy.bsmArgs[1];indy.bsmArgs=indy.bsmArgs.clone();indy.bsmArgs[1]=new Handle(H_INVOKEVIRTUAL,h.getOwner(),h.getName(),h.getDesc(),false);break;}}
            case "unknownCall"->block.instructions.insertBefore(code(block).get(0),new MethodInsnNode(INVOKESTATIC,"java/lang/System","gc","()V",false));
            case "wrongOpcode"->{var get=call(item,REGISTRY_OBJECT,m->m.name.equals("get"));item.instructions.set(get,new MethodInsnNode(INVOKESTATIC,get.owner,get.name,get.desc,false));}
            case "foreignDependency"->{var key="META-INF/mods.toml";entries.put(key,(new String(entries.get(key),StandardCharsets.UTF_8)
                +"\n[[dependencies."+MOD+"]]\nmodId=\"unknown_dependency\"\nmandatory=true\nversionRange=\"[1,)\"\nordering=\"AFTER\"\nside=\"BOTH\"\n").getBytes(StandardCharsets.UTF_8));}
            case "globalMinecraftTag"->entries.put(MINING_TAG,("{\"replace\":true,\"values\":[\""+MOD+":bridge_block\"]}").getBytes(StandardCharsets.UTF_8));
            case "missingRecipe"->entries.remove("data/"+MOD+"/recipes/bridge_block.json");
            case "missingModel"->entries.remove("assets/"+MOD+"/models/block/bridge_block.json");
            case "unknownResource"->entries.put("assets/"+MOD+"/shaders/core/unreviewed.json","{}".getBytes(StandardCharsets.UTF_8));
            case "unknownLoot"->entries.put("data/"+MOD+"/loot_tables/blocks/bridge_block.json","{\"type\":\"minecraft:block\",\"pools\":[]}".getBytes(StandardCharsets.UTF_8));
            case "badResource"->entries.put("data/"+MOD+"/recipes/bridge_block.json","{\"type\":\"unknown:serializer\"}".getBytes(StandardCharsets.UTF_8));
            case "foreignAnnotation"->{entry.visibleAnnotations.add(new AnnotationNode("Lexample/Unknown;"));}
            default->fail(mutation);
        }
        entries.put(ENTRY+".class",bytes(entry));var path=directory.resolve(mutation+".jar");write(path,entries);
        var before=Files.readAllBytes(path);var output=directory.resolve("absent.jar");
        var result=LogicPreservingConverter.planBooleanToggleBlock(path,output,inputs());
        assertFalse(result.validated(),mutation);assertTrue(result.candidateSha256().isEmpty());
        assertFalse(Files.exists(output));assertArrayEquals(before,Files.readAllBytes(path));
        assertEquals(false,result.report().get("transformationsCommitted"));
    }

    @ParameterizedTest @ValueSource(strings={"wrongSuper","notFinalSubclass","extraState","mutableProperty","instanceProperty","foreignInterface",
        "propertyName","propertyFactory","defaultTrue","constructorExtraEffect","constructorWrongSuper","builderExtraEffect","builderPrivate",
        "useStatic","usePrivate","useWrongDescriptor","useException","reverseSideGuard","ungatedWrite","wrongSideField",
        "wrongClientResult","wrongServerResult","flagsZero","flagsTwo","flagsSixteen","wrongWorldArgument","wrongPositionArgument",
        "wrongStateArgument","missingCycleCast","wrongCycleProperty","wrongCycleMethod","wrongCycleOpcode","wrongWriteOpcode",
        "writeExtraEffect","duplicateWrite","clientWrite","extraCallback","foreignAnnotation","missingOwnedBlock"})
    void sideStatePositionAndEffectsMustMatchAuditedUseBody(String mutation)throws Exception{
        var entries=read();var n=node(entries,OWNED_BLOCK);var constructor=method(n,"<init>");var clinit=method(n,"<clinit>");
        var use=n.methods.stream().filter(m->m.desc.endsWith(")Lnet/minecraft/util/ActionResultType;")).findFirst().orElseThrow();
        var builder=n.methods.stream().filter(m->m.desc.equals("(Lnet/minecraft/state/StateContainer$Builder;)V")).findFirst().orElseThrow();
        var world="net/minecraft/world/World";var state="net/minecraft/block/BlockState";
        switch(mutation){
            case "wrongSuper"->n.superName="net/minecraft/block/LeverBlock";
            case "notFinalSubclass"->n.access &= ~ACC_FINAL;
            case "extraState"->n.fields.add(new FieldNode(ACC_PRIVATE|ACC_STATIC,"counter","I",null,null));
            case "mutableProperty"->n.fields.get(0).access &= ~ACC_FINAL;
            case "instanceProperty"->n.fields.get(0).access &= ~ACC_STATIC;
            case "foreignInterface"->n.interfaces.add("java/lang/Runnable");
            case "propertyName"->{var literal=code(clinit).stream().filter(i->i instanceof LdcInsnNode).map(i->(LdcInsnNode)i).findFirst().orElseThrow();assertEquals("active",literal.cst);literal.cst="powered";}
            case "propertyFactory"->call(clinit,"net/minecraft/state/BooleanProperty",m->true).name="unreviewed";
            case "defaultTrue"->{var f=field(constructor,"java/lang/Boolean");assertEquals("FALSE",f.name);f.name="TRUE";}
            case "constructorExtraEffect"->constructor.instructions.insertBefore(code(constructor).get(0),new MethodInsnNode(INVOKESTATIC,"java/lang/System","gc","()V",false));
            case "constructorWrongSuper"->call(constructor,BLOCK,m->m.name.equals("<init>")).owner="net/minecraft/block/LeverBlock";
            case "builderExtraEffect"->builder.instructions.insertBefore(code(builder).get(0),new MethodInsnNode(INVOKESTATIC,"java/lang/System","gc","()V",false));
            case "builderPrivate"->builder.access=(builder.access&~ACC_PROTECTED)|ACC_PRIVATE;
            case "useStatic"->use.access |= ACC_STATIC;
            case "usePrivate"->use.access=(use.access&~ACC_PUBLIC)|ACC_PRIVATE;
            case "useWrongDescriptor"->use.desc=use.desc.replace("Lnet/minecraft/world/World;","Lnet/minecraft/world/IWorld;");
            case "useException"->use.exceptions.add("java/lang/Exception");
            case "reverseSideGuard"->{var jump=code(use).stream().filter(i->i instanceof JumpInsnNode).map(i->(JumpInsnNode)i).findFirst().orElseThrow();assertEquals(IFEQ,jump.getOpcode());use.instructions.set(jump,new JumpInsnNode(IFNE,jump.label));}
            case "ungatedWrite"->{var jump=code(use).stream().filter(i->i instanceof JumpInsnNode).map(i->(JumpInsnNode)i).findFirst().orElseThrow();
                var gateField=previousCode(jump);var gateReceiver=previousCode(gateField);use.instructions.remove(gateField);use.instructions.remove(gateReceiver);use.instructions.set(jump,new JumpInsnNode(GOTO,jump.label));}
            case "wrongSideField"->field(use,world).name="unreviewed";
            case "wrongClientResult"->{var f=field(use,"net/minecraft/util/ActionResultType");assertEquals("SUCCESS",f.name);f.name="PASS";}
            case "wrongServerResult"->{var values=code(use).stream().filter(i->i instanceof FieldInsnNode f&&f.owner.equals("net/minecraft/util/ActionResultType")).map(i->(FieldInsnNode)i).toList();assertEquals("CONSUME",values.get(1).name);values.get(1).name="SUCCESS";}
            case "flagsZero","flagsTwo","flagsSixteen"->{var write=call(use,world,m->m.desc.endsWith(";I)Z"));var flags=previousCode(write);assertEquals(ICONST_3,flags.getOpcode());
                use.instructions.set(flags,new LdcInsnNode(mutation.equals("flagsZero")?0:mutation.equals("flagsTwo")?2:16));}
            case "wrongWorldArgument"->{var loads=code(use).stream().filter(i->i instanceof VarInsnNode v&&v.getOpcode()==ALOAD&&v.var==2).map(i->(VarInsnNode)i).toList();assertEquals(2,loads.size());loads.get(1).var=0;}
            case "wrongPositionArgument"->{var load=code(use).stream().filter(i->i instanceof VarInsnNode v&&v.getOpcode()==ALOAD&&v.var==3).map(i->(VarInsnNode)i).findFirst().orElseThrow();load.var=6;}
            case "wrongStateArgument"->{var load=code(use).stream().filter(i->i instanceof VarInsnNode v&&v.getOpcode()==ALOAD&&v.var==1).map(i->(VarInsnNode)i).findFirst().orElseThrow();load.var=0;}
            case "missingCycleCast"->use.instructions.remove(code(use).stream().filter(i->i instanceof TypeInsnNode t&&t.getOpcode()==CHECKCAST&&t.desc.equals(state)).findFirst().orElseThrow());
            case "wrongCycleProperty"->field(use,OWNED_BLOCK).name="unreviewed";
            case "wrongCycleMethod"->call(use,state,m->true).name="unreviewed";
            case "wrongCycleOpcode"->{var m=call(use,state,x->true);use.instructions.set(m,new MethodInsnNode(INVOKESTATIC,m.owner,m.name,m.desc,false));}
            case "wrongWriteOpcode"->{var m=call(use,world,x->x.desc.endsWith(";I)Z"));use.instructions.set(m,new MethodInsnNode(INVOKESTATIC,m.owner,m.name,m.desc,false));}
            case "writeExtraEffect"->use.instructions.insertBefore(code(use).get(code(use).size()-2),new MethodInsnNode(INVOKESTATIC,"java/lang/System","gc","()V",false));
            case "duplicateWrite","clientWrite"->{
                var sequence=new InsnList();sequence.add(new VarInsnNode(ALOAD,2));sequence.add(new VarInsnNode(ALOAD,3));sequence.add(new VarInsnNode(ALOAD,1));
                var property=field(use,OWNED_BLOCK);sequence.add(new FieldInsnNode(GETSTATIC,property.owner,property.name,property.desc));
                var cycle=call(use,state,m->true);sequence.add(new MethodInsnNode(cycle.getOpcode(),cycle.owner,cycle.name,cycle.desc,cycle.itf));
                sequence.add(new TypeInsnNode(CHECKCAST,state));sequence.add(new InsnNode(ICONST_3));
                var write=call(use,world,m->m.desc.endsWith(";I)Z"));sequence.add(new MethodInsnNode(write.getOpcode(),write.owner,write.name,write.desc,write.itf));sequence.add(new InsnNode(POP));
                use.instructions.insertBefore(mutation.equals("clientWrite")?field(use,"net/minecraft/util/ActionResultType"):code(use).get(code(use).size()-2),sequence);
            }
            case "extraCallback"->{var m=new MethodNode(ACC_PUBLIC,"unreviewed","()V",null,null);m.instructions.add(new InsnNode(RETURN));n.methods.add(m);}
            case "foreignAnnotation"->{n.visibleAnnotations=new ArrayList<>();n.visibleAnnotations.add(new AnnotationNode("Lexample/Unknown;"));}
            case "missingOwnedBlock"->{}
            default->fail(mutation);
        }
        if(mutation.equals("missingOwnedBlock"))entries.remove(OWNED_BLOCK+".class");else entries.put(OWNED_BLOCK+".class",bytes(n));
        assertBlocked(entries,mutation);
    }

    @ParameterizedTest @ValueSource(strings={"missingFalse","missingTrue","swappedModels","wrongPropertyKey","unknownState","stateRotation","duplicateVariant",
        "missingActiveModel","sameTextures","unknownTexture","unknownActiveParent","activeModelExtraField","itemUsesActive","lootTable","unknownJson"})
    void twoResourceVariantsMustExposeTheSameAuditedBooleanState(String mutation)throws Exception{
        var entries=read();String key="assets/"+MOD+"/blockstates/bridge_block.json";
        if(Set.of("sameTextures","unknownTexture","unknownActiveParent","activeModelExtraField","missingActiveModel").contains(mutation))
            key="assets/"+MOD+"/models/block/bridge_block_active.json";
        if(mutation.equals("itemUsesActive"))key="assets/"+MOD+"/models/item/bridge_block.json";
        String originalText=new String(entries.get(key),StandardCharsets.UTF_8);String text=originalText;
        switch(mutation){
            case "missingFalse"->text="{\"variants\":{\"active=true\":{\"model\":\""+MOD+":block/bridge_block_active\"}}}";
            case "missingTrue"->text="{\"variants\":{\"active=false\":{\"model\":\""+MOD+":block/bridge_block\"}}}";
            case "swappedModels"->text=text.replace("bridge_block_active","temporary_model").replace("bridge_block","bridge_block_active").replace("temporary_model","bridge_block");
            case "wrongPropertyKey"->text=text.replace("active=","powered=");
            case "unknownState"->text=text.replace("active=true","active=maybe");
            case "stateRotation"->text=text.replace("\"model\":","\"y\":90,\"model\":");
            case "duplicateVariant"->text=text.replace("active=true","active=false");
            case "missingActiveModel"->{entries.remove(key);assertBlocked(entries,mutation);return;}
            case "sameTextures"->text=text.replace("minecraft:block/cobblestone","minecraft:block/stone");
            case "unknownTexture"->text=text.replace("minecraft:block/cobblestone","unreviewed:block/texture");
            case "unknownActiveParent"->text=text.replace("minecraft:block/cube_all","unreviewed:parent");
            case "activeModelExtraField"->text=text.replace("\"parent\":","\"loader\":\"unreviewed:loader\",\"parent\":");
            case "itemUsesActive"->text=text.replace("block/bridge_block","block/bridge_block_active");
            case "lootTable"->{entries.put("data/"+MOD+"/loot_tables/blocks/bridge_block.json","{\"type\":\"minecraft:block\",\"pools\":[]}".getBytes(StandardCharsets.UTF_8));assertBlocked(entries,mutation);return;}
            case "unknownJson"->{entries.put("assets/"+MOD+"/unreviewed.json","{}".getBytes(StandardCharsets.UTF_8));assertBlocked(entries,mutation);return;}
            default->fail(mutation);
        }
        assertNotEquals(originalText,text,"Mutation must change resource: "+mutation);
        entries.put(key,text.getBytes(StandardCharsets.UTF_8));assertBlocked(entries,mutation);
    }

    private void assertBlocked(Map<String,byte[]> entries,String mutation)throws Exception{
        var path=directory.resolve(mutation+".jar");write(path,entries);var before=Files.readAllBytes(path);var out=directory.resolve("absent.jar");
        var result=LogicPreservingConverter.planBooleanToggleBlock(path,out,inputs());
        assertFalse(result.validated(),mutation);assertTrue(result.candidateSha256().isEmpty());assertFalse(Files.exists(out));
        assertArrayEquals(before,Files.readAllBytes(path));assertEquals(false,result.report().get("transformationsCommitted"));
    }

    @ParameterizedTest @ValueSource(strings={"targetStoneMutable","targetStoneInstance","targetInstrumentPrivate","targetInstrumentMissing","targetInstrumentMutable","targetInstrumentInstance",
        "targetOfMissing","targetOfInstance","targetMapColorPrivate","targetInstrumentSetterStatic","targetBlockConstructorPrivate",
        "sourceStoneMutable","sourceStoneInstance","sourceOfMissing"})
    void realDeclarationChangesBlockInsteadOfPromotingConfidence(String mutation)throws Exception{
        var original=inputs();boolean old=mutation.startsWith("source");
        var api=old?original.legacyApi():original.targetApi();var index=new TreeMap<>(api.classes());
        String properties=old?PROPS:"net/minecraft/world/level/block/state/BlockBehaviour$Properties";
        boolean stone=mutation.contains("Stone"),instrumentField=mutation.startsWith("targetInstrument")&&!mutation.contains("Setter");
        String owner=stone?(old?MATERIAL:"net/minecraft/world/level/material/MapColor"):
            instrumentField?"net/minecraft/world/level/block/state/properties/NoteBlockInstrument":
            mutation.contains("Constructor")?"net/minecraft/world/level/block/Block":properties;
        var type=index.get(owner);assertNotNull(type,owner);
        var fields=new ArrayList<>(type.fields());var methods=new ArrayList<>(type.methods());
        var members=stone||instrumentField?fields:methods;
        String name=stone?"STONE":instrumentField?"BASEDRUM":mutation.contains("Of")?"of":
            mutation.contains("MapColor")?"mapColor":mutation.contains("Setter")?"instrument":"<init>";
        var found=members.stream().filter(m->m.name().equals(name)
            && (!name.equals("of")||m.descriptor().equals(old?"(L"+MATERIAL+";)L"+PROPS+";":"()L"+properties+";"))
            && (!name.equals("mapColor")||m.descriptor().startsWith("(Lnet/minecraft/world/level/material/MapColor;"))).toList();
        assertEquals(1,found.size(),mutation);var member=found.get(0);members.remove(member);
        if(!mutation.endsWith("Missing")){
            int access=member.access();
            if(mutation.endsWith("Static"))access|=ACC_STATIC;
            if(mutation.endsWith("Private"))access=(access & ~ACC_PUBLIC)|ACC_PRIVATE;
            if(mutation.endsWith("Instance"))access &= ~ACC_STATIC;
            if(mutation.endsWith("Mutable"))access &= ~ACC_FINAL;
            members.add(new ApiIndex.ApiMember(member.name(),member.descriptor(),access));
        }
        index.put(owner,new ApiIndex.ApiClass(type.name(),type.access(),type.superName(),type.interfaces(),methods,fields));
        var modified=new ApiIndex(index);
        var data=new MappingInputs(original.legacyOfficial(),original.targetOfficial(),original.legacySrg(),original.targetSrg(),
            old?modified:original.legacyApi(),old?original.targetApi():modified,original.sources(),original.diagnostics(),original.complete());
        var output=directory.resolve("absent.jar");var result=LogicPreservingConverter.planBooleanToggleBlock(fixture(),output,data);
        assertFalse(result.validated(),mutation);assertTrue(result.candidateSha256().isEmpty());assertFalse(Files.exists(output));
    }

    @Test void staleSelectionCannotAuthorizeCandidate()throws Exception{
        try(var service=new LegacyEngineService()){
            var output=directory.resolve("absent.jar");
            var request=new ConversionRequest(fixture(),output,MappingData.profile(profile()),Optional.of("0".repeat(64)),ConversionProfile.LOGIC_PRESERVING_V1);
            var result=service.planBooleanToggleBlock(request).completion().toCompletableFuture().get(90,TimeUnit.SECONDS);
            assertNotEquals(EngineOutcome.Status.COMPLETED,result.status());assertFalse(Files.exists(output));
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
        try(var scope=control.enter()){assertThrows(OperationStoppedException.class,()->LogicPreservingConverter.planBooleanToggleBlock(fixture(),output,inputs()));}
        assertFalse(Files.exists(output));
        if(point.equals("REOPEN")){assertFalse(observed.get().isEmpty());for(var path:observed.get())assertFalse(Files.exists(path));}
    }

    private static Set<Path> scratch(){
        try(var files=Files.list(Path.of(System.getProperty("java.io.tmpdir")))){
            return files.filter(p->p.getFileName().toString().startsWith("legacybridge-compose-")).collect(java.util.stream.Collectors.toSet());
        }catch(java.io.IOException e){throw new AssertionError(e);}
    }
    private static Map<String,byte[]> classes(Map<String,byte[]> entries){var result=new TreeMap<String,byte[]>();entries.forEach((p,b)->{if(p.endsWith(".class"))result.put(p,b);});return result;}
    private static ClassNode node(Map<String,byte[]> entries){return node(entries,ENTRY);}
    private static ClassNode node(Map<String,byte[]> entries,String owner){var n=new ClassNode();new ClassReader(entries.get(owner+".class")).accept(n,0);return n;}
    private static MethodNode method(ClassNode n,String name){return n.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();}
    private static List<AbstractInsnNode> code(MethodNode m){var out=new ArrayList<AbstractInsnNode>();for(var i:m.instructions)if(i.getOpcode()>=0)out.add(i);return out;}
    private static FieldInsnNode field(MethodNode method,String owner){return code(method).stream().filter(i->i instanceof FieldInsnNode f&&f.owner.equals(owner)).map(i->(FieldInsnNode)i).findFirst().orElseThrow();}
    private static MethodInsnNode call(MethodNode method,String owner,java.util.function.Predicate<MethodInsnNode> predicate){return code(method).stream().filter(i->i instanceof MethodInsnNode m&&m.owner.equals(owner)&&predicate.test(m)).map(i->(MethodInsnNode)i).findFirst().orElseThrow();}
    private static AbstractInsnNode previousCode(AbstractInsnNode instruction){do{instruction=instruction.getPrevious();}while(instruction!=null&&instruction.getOpcode()<0);return Objects.requireNonNull(instruction);}
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
