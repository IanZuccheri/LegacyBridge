package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.transformation.TransformationException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/** Inert structural tests; these trees are never loaded as classes or substituted for API indexes. */
class PlainBlockContractTest {
    private static final String ENTRY = "example/PlainBlockContent";
    private static final String MOD = "plain_block_fixture";
    private static final String DEFERRED = "net/minecraftforge/registries/DeferredRegister";
    private static final String REGISTRY = "net/minecraftforge/registries/IForgeRegistry";
    private static final String REGISTRIES = "net/minecraftforge/registries/ForgeRegistries";
    private static final String CONTEXT = "net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext";
    private static final String BUS = "net/minecraftforge/eventbus/api/IEventBus";
    private static final Handle METAFACTORY = new Handle(H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);

    @Test void provesEquivalentSourceAndTargetWithoutMutatingTrees() {
        var source = fixture(false); var target = fixture(true);
        byte[] sourceBefore = bytes(source), targetBefore = bytes(target);
        var proof = PlainBlockContract.inspect(Map.of(ENTRY, source));
        assertEquals(proof, PlainBlockContract.inspect(Map.of(ENTRY, target)));
        assertEquals(2.0F, proof.hardness()); assertEquals(6.0F, proof.resistance()); assertEquals(32, proof.stackSize());
        assertArrayEquals(sourceBefore, bytes(source)); assertArrayEquals(targetBefore, bytes(target));
        assertEquals("NOT_RUN", proof.toTree().get("runtimeValidation"));
        assertEquals(false, proof.toTree().get("classesExecuted"));
        var plan = PlainBlockContract.validate(Map.of(ENTRY, target), MOD, "1.0.0");
        assertEquals(1, plan.blocks().size()); assertEquals(1, plan.items().size());
        assertEquals(MOD + ":bridge_block", plan.items().get(0).block());
        assertEquals(32, plan.items().get(0).properties().stackSize());
        assertNull(plan.items().get(0).properties().creativeTab());
        assertFalse(plan.blocks().get(0).properties().requiresCorrectTool());
        assertFalse(plan.blocks().get(0).properties().noOcclusion());
        assertEquals("STONE", plan.blocks().get(0).properties().material());
        assertTrue(plan.sounds().isEmpty());
    }

    @Test void acceptsOnlyBoundedLiteralVariationsAndMatchingRenamedHelpers() {
        for (boolean target : new boolean[]{false, true}) {
            var tree = fixture(target);
            var block = method(tree, "createBlock"); var item = method(tree, "createBlockItem");
            block.instructions.set(real(block).get(target ? 7 : 4), new LdcInsnNode(0.5F));
            block.instructions.set(real(block).get(target ? 8 : 5), new LdcInsnNode(32767.0F));
            item.instructions.set(real(item).get(8), new IntInsnNode(BIPUSH, 64));
            block.name = "makeStone"; item.name = "makeStoneItem";
            for (var instruction : method(tree, "<clinit>").instructions) if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                var old = (Handle) dynamic.bsmArgs[1];
                dynamic.bsmArgs[1] = new Handle(old.getTag(), old.getOwner(),
                    old.getName().equals("createBlock") ? block.name : item.name, old.getDesc(), old.isInterface());
            }
            var proof = PlainBlockContract.inspect(Map.of(ENTRY, tree));
            assertEquals(0.5F, proof.hardness()); assertEquals(32767.0F, proof.resistance()); assertEquals(64, proof.stackSize());
        }
    }

    @Test void rejectsSourceResourceViewAndMetadataMismatch() {
        assertThrows(TransformationException.class, () -> PlainBlockContract.validate(Map.of(ENTRY, fixture(false)), MOD, "1.0.0"));
        assertThrows(TransformationException.class, () -> PlainBlockContract.validate(Map.of(ENTRY, fixture(true)), "different", "1.0.0"));
        assertThrows(TransformationException.class, () -> PlainBlockContract.validate(Map.of(ENTRY, fixture(true)), MOD, "../invalid"));
        assertThrows(TransformationException.class, () -> PlainBlockContract.inspect(Map.of()));
    }

    @Test void acceptsConsistentlyRenamedRegistrationRolesWithoutNameWhitelist() {
        for(boolean target:new boolean[]{false,true}) {
            var tree=fixture(target); var before=PlainBlockContract.inspect(Map.of(ENTRY,tree));
            var names=new HashMap<String,String>(); int index=0;
            for(var field:tree.fields){String renamed="field"+(index++);names.put(field.name,renamed);field.name=renamed;}
            for(var method:tree.methods)for(var instruction:method.instructions)
                if(instruction instanceof FieldInsnNode field && field.owner.equals(ENTRY))field.name=names.get(field.name);
            assertEquals(before,PlainBlockContract.inspect(Map.of(ENTRY,tree)));
        }
    }

    @TestFactory Stream<DynamicTest> rejectsUnknownSourceEffectsAndContracts() {
        var mutations = new LinkedHashMap<String, Consumer<ClassNode>>();
        mutations.put("wrong material", n -> ((FieldInsnNode) real(method(n,"createBlock")).get(2)).name = "field_151575_d");
        mutations.put("unknown property method", n -> ((MethodInsnNode) real(method(n,"createBlock")).get(6)).name = "unverified");
        mutations.put("NaN hardness", n -> replace(method(n,"createBlock"),4,new LdcInsnNode(Float.NaN)));
        mutations.put("infinite resistance", n -> replace(method(n,"createBlock"),5,new LdcInsnNode(Float.POSITIVE_INFINITY)));
        mutations.put("negative zero", n -> replace(method(n,"createBlock"),4,new LdcInsnNode(-0.0F)));
        mutations.put("negative hardness", n -> replace(method(n,"createBlock"),4,new LdcInsnNode(-1.0F)));
        mutations.put("unbounded strength", n -> replace(method(n,"createBlock"),5,new LdcInsnNode(32768.0F)));
        mutations.put("double instead of float", n -> replace(method(n,"createBlock"),5,new LdcInsnNode(6.0D)));
        mutations.put("zero stack", n -> replace(method(n,"createBlockItem"),8,new InsnNode(ICONST_0)));
        mutations.put("oversize stack", n -> replace(method(n,"createBlockItem"),8,new IntInsnNode(BIPUSH,65)));
        mutations.put("eager object access", n -> method(n,"<clinit>").instructions.insert(new FieldInsnNode(GETSTATIC,ENTRY,"BLOCK",object(false))));
        mutations.put("changed registration order", n -> ((FieldInsnNode) real(method(n,"<init>")).get(2)).name = "ITEMS");
        mutations.put("registry name mismatch", n -> replace(method(n,"<clinit>"),14,new LdcInsnNode("different")));
        mutations.put("different mod namespace", n -> replace(method(n,"<clinit>"),1,new LdcInsnNode("different")));
        mutations.put("supplier wrong helper", n -> { var d=(InvokeDynamicInsnNode)real(method(n,"<clinit>")).get(10); var h=(Handle)d.bsmArgs[1]; d.bsmArgs[1]=new Handle(H_INVOKESTATIC,ENTRY,"absent",h.getDesc(),false); });
        mutations.put("capturing supplier", n -> ((InvokeDynamicInsnNode)real(method(n,"<clinit>")).get(10)).desc="(Ljava/lang/Object;)Ljava/util/function/Supplier;");
        mutations.put("wrong get opcode", n -> ((MethodInsnNode)real(method(n,"createBlockItem")).get(3)).setOpcode(INVOKESTATIC));
        mutations.put("missing cast", n -> replace(method(n,"createBlockItem"),4,new InsnNode(NOP)));
        mutations.put("unknown class annotation", n -> n.visibleAnnotations.add(new AnnotationNode("Lunknown/Hook;")));
        mutations.put("unknown method annotation", n -> method(n,"createBlock").visibleAnnotations=new ArrayList<>(List.of(new AnnotationNode("Lunknown/Hook;"))));
        mutations.put("extra field", n -> n.fields.add(new FieldNode(ACC_PUBLIC|ACC_STATIC,"state","I",null,null)));
        mutations.put("generic unknown dependency", n -> n.fields.get(1).signature="L"+DEFERRED+"<Lunknown/Block;>;" );
        mutations.put("unknown inner metadata", n -> n.innerClasses.add(new InnerClassNode("unknown/X$Y","unknown/X","Y",ACC_PUBLIC|ACC_STATIC)));
        mutations.put("declared exception", n -> method(n,"createBlock").exceptions.add("java/lang/Exception"));
        mutations.put("nonprivate factory", n -> method(n,"createBlock").access=ACC_PUBLIC|ACC_STATIC);
        mutations.put("nonfinal class", n -> n.access=ACC_PUBLIC|ACC_SUPER);
        return mutations.entrySet().stream().map(e -> DynamicTest.dynamicTest(e.getKey(), () -> {
            var tree=fixture(false); e.getValue().accept(tree);
            var refusal=assertThrows(TransformationException.class, () -> PlainBlockContract.inspect(Map.of(ENTRY,tree)));
            assertTrue(refusal.getMessage().startsWith("PLAIN_BLOCK_UNSUPPORTED:"),refusal.getMessage());
        }));
    }

    @TestFactory Stream<DynamicTest> rejectsMissingTargetSemanticDefaults() {
        return Stream.of(2,3,4,5,6).map(index -> DynamicTest.dynamicTest("target properties instruction "+index, () -> {
            var tree=fixture(true); replace(method(tree,"createBlock"),index,new InsnNode(NOP));
            assertThrows(TransformationException.class, () -> PlainBlockContract.inspect(Map.of(ENTRY,tree)));
        }));
    }

    private static ClassNode fixture(boolean target) {
        String block=target?"net/minecraft/world/level/block/Block":"net/minecraft/block/Block";
        String item=target?"net/minecraft/world/item/Item":"net/minecraft/item/Item";
        String blockItem=target?"net/minecraft/world/item/BlockItem":"net/minecraft/item/BlockItem";
        String props=target?"net/minecraft/world/level/block/state/BlockBehaviour$Properties":"net/minecraft/block/AbstractBlock$Properties";
        String itemProps=item+"$Properties", object=object(target);
        var n=new ClassNode(); n.version=V1_8; n.access=ACC_PUBLIC|ACC_FINAL|ACC_SUPER; n.name=ENTRY; n.superName="java/lang/Object"; n.sourceFile="PlainBlockContent.java";
        n.visibleAnnotations=new ArrayList<>(); var mod=new AnnotationNode("Lnet/minecraftforge/fml/common/Mod;"); mod.values=new ArrayList<>(List.of("value",MOD)); n.visibleAnnotations.add(mod);
        n.fields.add(new FieldNode(ACC_PUBLIC|ACC_STATIC|ACC_FINAL,"MOD_ID","Ljava/lang/String;",null,MOD));
        for(String name:List.of("BLOCKS","ITEMS"))n.fields.add(new FieldNode(ACC_PUBLIC|ACC_STATIC|ACC_FINAL,name,"L"+DEFERRED+";",null,null));
        for(String name:List.of("BLOCK","BLOCK_ITEM"))n.fields.add(new FieldNode(ACC_PUBLIC|ACC_STATIC|ACC_FINAL,name,object,null,null));
        var init=add(n,ACC_STATIC,"<clinit>","()V");
        for(String field:List.of("BLOCKS","ITEMS")){
            init.instructions.add(new FieldInsnNode(GETSTATIC,REGISTRIES,field,"L"+REGISTRY+";"));init.instructions.add(new LdcInsnNode(MOD));
            call(init,INVOKESTATIC,DEFERRED,"create","(L"+REGISTRY+";Ljava/lang/String;)L"+DEFERRED+";");init.instructions.add(new FieldInsnNode(PUTSTATIC,ENTRY,field,"L"+DEFERRED+";"));
        }
        for(String[] role:new String[][]{{"BLOCKS","BLOCK","createBlock",block},{"ITEMS","BLOCK_ITEM","createBlockItem",blockItem}}){
            init.instructions.add(new FieldInsnNode(GETSTATIC,ENTRY,role[0],"L"+DEFERRED+";"));init.instructions.add(new LdcInsnNode("bridge_block"));
            init.instructions.add(new InvokeDynamicInsnNode("get","()Ljava/util/function/Supplier;",METAFACTORY,Type.getMethodType("()Ljava/lang/Object;"),
                new Handle(H_INVOKESTATIC,ENTRY,role[2],"()L"+role[3]+";",false),Type.getMethodType("()L"+role[3]+";")));
            call(init,INVOKEVIRTUAL,DEFERRED,"register","(Ljava/lang/String;Ljava/util/function/Supplier;)"+object);init.instructions.add(new FieldInsnNode(PUTSTATIC,ENTRY,role[1],object));
        }init.instructions.add(new InsnNode(RETURN));
        var ctor=add(n,ACC_PUBLIC,"<init>","()V");ctor.instructions.add(new VarInsnNode(ALOAD,0));call(ctor,INVOKESPECIAL,"java/lang/Object","<init>","()V");
        for(String field:List.of("BLOCKS","ITEMS")){
            ctor.instructions.add(new FieldInsnNode(GETSTATIC,ENTRY,field,"L"+DEFERRED+";"));call(ctor,INVOKESTATIC,CONTEXT,"get","()L"+CONTEXT+";");
            call(ctor,INVOKEVIRTUAL,CONTEXT,"getModEventBus","()L"+BUS+";");call(ctor,INVOKEVIRTUAL,DEFERRED,"register","(L"+BUS+";)V");
        }ctor.instructions.add(new InsnNode(RETURN));
        var b=add(n,ACC_PRIVATE|ACC_STATIC,"createBlock","()L"+block+";");b.instructions.add(new TypeInsnNode(NEW,block));b.instructions.add(new InsnNode(DUP));
        if(target){
            call(b,INVOKESTATIC,props,"m_284310_","()L"+props+";");String color="net/minecraft/world/level/material/MapColor",instrument="net/minecraft/world/level/block/state/properties/NoteBlockInstrument";
            b.instructions.add(new FieldInsnNode(GETSTATIC,color,"f_283947_","L"+color+";"));call(b,INVOKEVIRTUAL,props,"m_284180_","(L"+color+";)L"+props+";");
            b.instructions.add(new FieldInsnNode(GETSTATIC,instrument,"BASEDRUM","L"+instrument+";"));call(b,INVOKEVIRTUAL,props,"m_280658_","(L"+instrument+";)L"+props+";");
        }else{String material="net/minecraft/block/material/Material";b.instructions.add(new FieldInsnNode(GETSTATIC,material,"field_151576_e","L"+material+";"));call(b,INVOKESTATIC,props,"func_200945_a","(L"+material+";)L"+props+";");}
        b.instructions.add(new InsnNode(FCONST_2));b.instructions.add(new LdcInsnNode(6.0F));call(b,INVOKEVIRTUAL,props,target?"m_60913_":"func_200948_a","(FF)L"+props+";");call(b,INVOKESPECIAL,block,"<init>","(L"+props+";)V");b.instructions.add(new InsnNode(ARETURN));
        var i=add(n,ACC_PRIVATE|ACC_STATIC,"createBlockItem","()L"+blockItem+";");i.instructions.add(new TypeInsnNode(NEW,blockItem));i.instructions.add(new InsnNode(DUP));i.instructions.add(new FieldInsnNode(GETSTATIC,ENTRY,"BLOCK",object));
        call(i,INVOKEVIRTUAL,object.substring(1,object.length()-1),"get","()L"+(target?"java/lang/Object":"net/minecraftforge/registries/IForgeRegistryEntry")+";");i.instructions.add(new TypeInsnNode(CHECKCAST,block));
        i.instructions.add(new TypeInsnNode(NEW,itemProps));i.instructions.add(new InsnNode(DUP));call(i,INVOKESPECIAL,itemProps,"<init>","()V");i.instructions.add(new IntInsnNode(BIPUSH,32));
        call(i,INVOKEVIRTUAL,itemProps,target?"m_41487_":"func_200917_a","(I)L"+itemProps+";");call(i,INVOKESPECIAL,blockItem,"<init>","(L"+block+";L"+itemProps+";)V");i.instructions.add(new InsnNode(ARETURN));
        return n;
    }
    private static String object(boolean target){return "L"+(target?"net/minecraftforge/registries/RegistryObject":"net/minecraftforge/fml/RegistryObject")+";";}
    private static MethodNode add(ClassNode n,int access,String name,String descriptor){var m=new MethodNode(access,name,descriptor,null,null);m.maxStack=16;m.maxLocals=1;n.methods.add(m);return m;}
    private static void call(MethodNode m,int opcode,String owner,String name,String descriptor){m.instructions.add(new MethodInsnNode(opcode,owner,name,descriptor,false));}
    private static MethodNode method(ClassNode n,String name){return n.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();}
    private static List<AbstractInsnNode> real(MethodNode m){return ClientConfigContract.code(m);}
    private static void replace(MethodNode m,int index,AbstractInsnNode replacement){m.instructions.set(real(m).get(index),replacement);}
    private static byte[] bytes(ClassNode n){var writer=new ClassWriter(0);n.accept(writer);return writer.toByteArray();}
}
