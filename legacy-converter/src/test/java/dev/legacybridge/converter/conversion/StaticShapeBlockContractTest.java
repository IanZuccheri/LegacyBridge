package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.operation.OperationLimits;
import dev.legacybridge.converter.operation.OperationStoppedException;
import dev.legacybridge.converter.transformation.TransformationException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/** Inert bytecode trees, never game/fixture execution or a replacement for the real API inventories. */
class StaticShapeBlockContractTest {
    private static final String ENTRY = "example/StaticShapeContent";
    private static final String BLOCK = "example/BottomHalfBlock";
    private static final String MOD = "static_shape_fixture";
    private static final String DEFERRED = "net/minecraftforge/registries/DeferredRegister";
    private static final String REGISTRY = "net/minecraftforge/registries/IForgeRegistry";
    private static final String REGISTRIES = "net/minecraftforge/registries/ForgeRegistries";
    private static final String CONTEXT = "net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext";
    private static final String BUS = "net/minecraftforge/eventbus/api/IEventBus";
    private static final Handle METAFACTORY = new Handle(H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);

    @Test void independentlyProvesSourceAndTargetWithoutMutatingOrExecutingEither() {
        var source = fixture(false); var target = fixture(true);
        var sourceBefore = bytes(source); var targetBefore = bytes(target);
        var proof = StaticShapeBlockContract.inspect(source);
        assertEquals(proof, StaticShapeBlockContract.inspect(target));
        assertEquals(ENTRY, proof.entry()); assertEquals(BLOCK, proof.block()); assertEquals("SHAPE", proof.shapeField());
        assertEquals(MOD, proof.plainProof().modId()); assertEquals("bridge_block", proof.plainProof().registryName());
        assertEquals(false, proof.toTree().get("classesExecuted")); assertEquals("NOT_RUN", proof.toTree().get("runtimeValidation"));
        var shape = (Map<?,?>)proof.toTree().get("shape");
        assertEquals(List.of(0.0D, 0.0D, 0.0D), shape.get("minimum"));
        assertEquals(List.of(16.0D, 8.0D, 16.0D), shape.get("maximum"));
        assertEquals("SAME_STATIC_FINAL_FIELD", shape.get("outlineAndCollision")); assertEquals("NONE", shape.get("contextUse"));
        assertBytes(sourceBefore, source); assertBytes(targetBefore, target);
        var content = StaticShapeBlockContract.validate(target, MOD, "1.0.0");
        assertEquals(1, content.blocks().size()); assertEquals(1, content.items().size());
        assertEquals(MOD + ":bridge_block", content.items().get(0).block());
        assertEquals(2.0F, content.blocks().get(0).properties().hardness());
        assertEquals(6.0F, content.blocks().get(0).properties().resistance());
        assertFalse(content.blocks().get(0).properties().noOcclusion());
    }

    @Test void checkedProjectionOnlyCopiesRegistrationEntryAndRetainsActualShapeGraph() {
        for (boolean target : new boolean[]{false, true}) {
            var nodes = fixture(target); var before = bytes(nodes); var proof = StaticShapeBlockContract.inspect(nodes);
            var projected = StaticShapeBlockContract.plainProjection(nodes);
            assertEquals(Set.of(ENTRY), projected.keySet());
            assertEquals(proof.plainProof(), PlainBlockContract.inspect(projected));
            var originalFactory = real(method(nodes.get(ENTRY), "createBlock"));
            assertEquals(BLOCK, ((TypeInsnNode)originalFactory.get(0)).desc);
            assertEquals(BLOCK, ((MethodInsnNode)originalFactory.get(originalFactory.size() - 2)).owner);
            var projectedFactory = real(method(projected.get(ENTRY), "createBlock"));
            assertEquals(nativeBlock(target), ((TypeInsnNode)projectedFactory.get(0)).desc);
            assertEquals(nativeBlock(target), ((MethodInsnNode)projectedFactory.get(projectedFactory.size() - 2)).owner);
            method(projected.get(ENTRY), "createBlock").instructions.clear();
            assertEquals(4, nodes.get(BLOCK).methods.size()); assertBytes(before, nodes);
            assertEquals(proof, StaticShapeBlockContract.inspect(nodes));
        }
        var malformed = fixture(false); method(malformed.get(BLOCK), "<clinit>").instructions.insert(new InsnNode(NOP));
        assertThrows(TransformationException.class, () -> StaticShapeBlockContract.plainProjection(malformed));
    }

    @Test void targetContentViewRefusesSourceOrMetadataMismatch() {
        assertThrows(TransformationException.class, () -> StaticShapeBlockContract.validate(fixture(false), MOD, "1.0.0"));
        assertThrows(TransformationException.class, () -> StaticShapeBlockContract.validate(fixture(true), "different", "1.0.0"));
        assertThrows(TransformationException.class, () -> StaticShapeBlockContract.validate(fixture(true), MOD, "../invalid"));
    }

    @Test void ownershipRolesAreStructuralRatherThanFixtureClassOrFieldNames() {
        for (boolean target : new boolean[]{false, true}) {
            var renamed = new LinkedHashMap<String,ClassNode>();
            var remapper = new Remapper() {
                @Override public String map(String name) { return name.equals(ENTRY) ? "other/Content" : name.equals(BLOCK) ? "other/HalfCube" : name; }
                @Override public String mapFieldName(String owner, String name, String descriptor) {
                    return owner.equals(ENTRY) ? "entry_" + name : owner.equals(BLOCK) && name.equals("SHAPE") ? "OUTLINE" : name;
                }
            };
            for (var n : fixture(target).values()) {
                var copy = new ClassNode(); n.accept(new ClassRemapper(copy, remapper)); renamed.put(copy.name, copy);
            }
            var proof = StaticShapeBlockContract.inspect(renamed);
            assertEquals("other/Content", proof.entry()); assertEquals("other/HalfCube", proof.block()); assertEquals("OUTLINE", proof.shapeField());
            assertEquals(MOD, proof.plainProof().modId()); assertEquals("bridge_block", proof.plainProof().registryName());
        }
    }

    @Test void preservesDebugInformationAndMethodOrderAndAdmitsExactNativeInnerMetadata() {
        for (boolean target : new boolean[]{false, true}) {
            var nodes = fixture(target);
            for (var n : nodes.values()) {
                n.sourceFile = "AuthoredShape.java"; n.sourceDebug = "SMAP\nAuthoredShape.java\n";
                for (var m : n.methods) {
                    var label = new LabelNode(); m.instructions.insert(label); m.instructions.insert(label, new LineNumberNode(79, label));
                }
                Collections.reverse(n.methods);
            }
            String props = properties(target);
            nodes.get(BLOCK).innerClasses.add(new InnerClassNode(props, props.substring(0, props.indexOf('$')), "Properties", ACC_PUBLIC | ACC_STATIC));
            var before = bytes(nodes); StaticShapeBlockContract.inspect(nodes); assertBytes(before, nodes);
        }
    }

    @Test void zeroLiteralEncodingCanDifferButNumericContractCannot() {
        for (boolean target : new boolean[]{false, true}) {
            var nodes = fixture(target); var before = StaticShapeBlockContract.inspect(nodes);
            var clinit = method(nodes.get(BLOCK), "<clinit>");
            for (int i = 0; i < 3; i++) replace(clinit, i, new LdcInsnNode(0.0D));
            assertEquals(before, StaticShapeBlockContract.inspect(nodes));
        }
    }

    @Test void rejectsAbsentAdditionalAliasedOrMixedVersionClasses() {
        assertThrows(TransformationException.class, () -> StaticShapeBlockContract.inspect(null));
        assertThrows(TransformationException.class, () -> StaticShapeBlockContract.inspect(Map.of()));
        var nodes = fixture(false);
        assertThrows(TransformationException.class, () -> StaticShapeBlockContract.inspect(Map.of(ENTRY, nodes.get(ENTRY))));
        nodes.put("unknown/Extra", new ClassNode());
        assertThrows(TransformationException.class, () -> StaticShapeBlockContract.inspect(nodes));
        var aliases = fixture(false); aliases.put("alias", aliases.remove(BLOCK));
        assertThrows(TransformationException.class, () -> StaticShapeBlockContract.inspect(aliases));
        var mixed = fixture(false); mixed.put(BLOCK, fixture(true).get(BLOCK));
        assertThrows(TransformationException.class, () -> StaticShapeBlockContract.inspect(mixed));
    }

    @Test void cancellationPreservesControlPlaneFailure() {
        var control = new OperationControl(OperationLimits.defaults(), event -> { });
        assertTrue(control.requestCancel());
        try (var ignored = control.enter()) {
            var stopped = assertThrows(OperationStoppedException.class, () -> StaticShapeBlockContract.inspect(fixture(false)));
            assertEquals(OperationStoppedException.Code.CANCELLED, stopped.code());
        }
    }

    @TestFactory Stream<DynamicTest> everyCoordinateAndVersionIsClosed() {
        return Stream.of(false, true).flatMap(target -> java.util.stream.IntStream.range(0, 6).mapToObj(index ->
            DynamicTest.dynamicTest((target ? "target" : "source") + " bound " + index, () -> {
                var nodes = fixture(target); replace(method(nodes.get(BLOCK), "<clinit>"), index, new LdcInsnNode(index < 3 ? 1.0D : 15.0D));
                assertRefused(nodes);
            })));
    }

    @TestFactory Stream<DynamicTest> refusesUnknownGeometryEffectsMetadataAndDispatch() {
        var mutations = new LinkedHashMap<String, Consumer<Map<String,ClassNode>>>();
        mutations.put("nonfinal block", n -> n.get(BLOCK).access &= ~ACC_FINAL);
        mutations.put("wrong superclass", n -> n.get(BLOCK).superName = "unknown/Block");
        mutations.put("extra interface", n -> n.get(BLOCK).interfaces.add("unknown/Hook"));
        mutations.put("new class version", n -> n.get(BLOCK).version = V17);
        mutations.put("generic subclass", n -> n.get(BLOCK).signature = "Lunknown/Block;");
        mutations.put("class annotation", n -> n.get(BLOCK).visibleAnnotations = new ArrayList<>(List.of(new AnnotationNode("Lunknown/Hook;"))));
        mutations.put("class attribute", n -> n.get(BLOCK).attrs = new ArrayList<>(List.of(new Attribute("Unverified") { })));
        mutations.put("outer owner", n -> n.get(BLOCK).outerClass = ENTRY);
        mutations.put("nest host", n -> n.get(BLOCK).nestHostClass = ENTRY);
        mutations.put("permitted subclasses", n -> n.get(BLOCK).permittedSubclasses = new ArrayList<>(List.of("unknown/Extension")));
        mutations.put("unknown inner metadata", n -> n.get(BLOCK).innerClasses.add(new InnerClassNode("unknown/A$B", "unknown/A", "B", ACC_PUBLIC | ACC_STATIC)));
        mutations.put("extra mutable state", n -> n.get(BLOCK).fields.add(new FieldNode(ACC_PRIVATE, "state", "I", null, null)));
        mutations.put("nonfinal shape", n -> n.get(BLOCK).fields.get(0).access &= ~ACC_FINAL);
        mutations.put("instance shape", n -> n.get(BLOCK).fields.get(0).access &= ~ACC_STATIC);
        mutations.put("public shape", n -> n.get(BLOCK).fields.get(0).access = ACC_PUBLIC | ACC_STATIC | ACC_FINAL);
        mutations.put("foreign shape type", n -> n.get(BLOCK).fields.get(0).desc = "Lunknown/VoxelShape;");
        mutations.put("shape field signature", n -> n.get(BLOCK).fields.get(0).signature = "Lunknown/Shape;");
        mutations.put("shape constant attribute", n -> n.get(BLOCK).fields.get(0).value = "not a native shape");
        mutations.put("field annotation", n -> n.get(BLOCK).fields.get(0).visibleAnnotations = new ArrayList<>(List.of(new AnnotationNode("Lunknown/Hook;"))));
        mutations.put("constructor reads wrong local", n -> ((VarInsnNode)real(method(n.get(BLOCK), "<init>")).get(1)).var = 0);
        mutations.put("constructor unknown super", n -> ((MethodInsnNode)real(method(n.get(BLOCK), "<init>")).get(2)).owner = "unknown/Block");
        mutations.put("constructor virtual super", n -> ((MethodInsnNode)real(method(n.get(BLOCK), "<init>")).get(2)).setOpcode(INVOKEVIRTUAL));
        mutations.put("constructor extra effect", n -> method(n.get(BLOCK), "<init>").instructions.insert(new InsnNode(NOP)));
        mutations.put("private constructor", n -> method(n.get(BLOCK), "<init>").access = ACC_PRIVATE);
        mutations.put("NaN coordinate", n -> replace(method(n.get(BLOCK), "<clinit>"), 0, new LdcInsnNode(Double.NaN)));
        mutations.put("infinite coordinate", n -> replace(method(n.get(BLOCK), "<clinit>"), 3, new LdcInsnNode(Double.POSITIVE_INFINITY)));
        mutations.put("negative zero", n -> replace(method(n.get(BLOCK), "<clinit>"), 1, new LdcInsnNode(-0.0D)));
        mutations.put("near zero", n -> replace(method(n.get(BLOCK), "<clinit>"), 2, new LdcInsnNode(1.0E-10D)));
        mutations.put("float coordinate", n -> replace(method(n.get(BLOCK), "<clinit>"), 4, new LdcInsnNode(8.0F)));
        mutations.put("integer coordinate", n -> replace(method(n.get(BLOCK), "<clinit>"), 4, new LdcInsnNode(8)));
        mutations.put("long coordinate", n -> replace(method(n.get(BLOCK), "<clinit>"), 4, new LdcInsnNode(8L)));
        mutations.put("inverted bounds", n -> replace(method(n.get(BLOCK), "<clinit>"), 0, new LdcInsnNode(17.0D)));
        mutations.put("degenerate shape", n -> replace(method(n.get(BLOCK), "<clinit>"), 4, new InsnNode(DCONST_0)));
        mutations.put("full cube", n -> replace(method(n.get(BLOCK), "<clinit>"), 4, new LdcInsnNode(16.0D)));
        mutations.put("thin shape", n -> replace(method(n.get(BLOCK), "<clinit>"), 4, new LdcInsnNode(1.0D)));
        mutations.put("dynamic shape factory", n -> ((MethodInsnNode)real(method(n.get(BLOCK), "<clinit>")).get(6)).name = "unknown");
        mutations.put("other factory owner", n -> ((MethodInsnNode)real(method(n.get(BLOCK), "<clinit>")).get(6)).owner = "unknown/Shapes");
        mutations.put("virtual shape factory", n -> ((MethodInsnNode)real(method(n.get(BLOCK), "<clinit>")).get(6)).setOpcode(INVOKEVIRTUAL));
        mutations.put("factory interface flag", n -> ((MethodInsnNode)real(method(n.get(BLOCK), "<clinit>")).get(6)).itf = true);
        mutations.put("factory wrong descriptor", n -> ((MethodInsnNode)real(method(n.get(BLOCK), "<clinit>")).get(6)).desc = "()Ljava/lang/Object;");
        mutations.put("shape assigned to foreign owner", n -> ((FieldInsnNode)real(method(n.get(BLOCK), "<clinit>")).get(7)).owner = ENTRY);
        mutations.put("shape assigned to wrong field", n -> ((FieldInsnNode)real(method(n.get(BLOCK), "<clinit>")).get(7)).name = "OTHER");
        mutations.put("instance shape assignment", n -> ((FieldInsnNode)real(method(n.get(BLOCK), "<clinit>")).get(7)).setOpcode(PUTFIELD));
        mutations.put("extra initializer effect", n -> method(n.get(BLOCK), "<clinit>").instructions.insert(new InsnNode(NOP)));
        mutations.put("static outline", n -> outline(n).access |= ACC_STATIC);
        mutations.put("private collision", n -> collision(n).access = ACC_PRIVATE);
        mutations.put("unknown outline name", n -> outline(n).name = "unknownShape");
        mutations.put("missing collision", n -> n.get(BLOCK).methods.remove(collision(n)));
        mutations.put("wrong collision descriptor", n -> collision(n).desc = "()Ljava/lang/Object;");
        mutations.put("method signature", n -> outline(n).signature = "()Ljava/lang/Object;");
        mutations.put("method annotation", n -> collision(n).visibleAnnotations = new ArrayList<>(List.of(new AnnotationNode("Lunknown/Hook;"))));
        mutations.put("method exception", n -> outline(n).exceptions.add("java/lang/Exception"));
        mutations.put("handler", n -> collision(n).tryCatchBlocks.add(new TryCatchBlockNode(new LabelNode(), new LabelNode(), new LabelNode(), "java/lang/Exception")));
        mutations.put("wrong outline field", n -> ((FieldInsnNode)real(outline(n)).get(0)).name = "OTHER");
        mutations.put("foreign collision field owner", n -> ((FieldInsnNode)real(collision(n)).get(0)).owner = ENTRY);
        mutations.put("instance outline field access", n -> ((FieldInsnNode)real(outline(n)).get(0)).setOpcode(GETFIELD));
        mutations.put("collision computes extra shape", n -> collision(n).instructions.insert(new InsnNode(NOP)));
        mutations.put("outline reads context", n -> outline(n).instructions.insert(new VarInsnNode(ALOAD, 4)));
        mutations.put("collision reads world", n -> collision(n).instructions.insert(new VarInsnNode(ALOAD, 2)));
        mutations.put("outline branch", n -> outline(n).instructions.insert(new JumpInsnNode(GOTO, new LabelNode())));
        mutations.put("extra callback", n -> add(n.get(BLOCK), ACC_PUBLIC, "tick", "()V").instructions.add(new InsnNode(RETURN)));
        mutations.put("entry creates native cube", n -> ((TypeInsnNode)real(method(n.get(ENTRY), "createBlock")).get(0)).desc = n.get(BLOCK).superName);
        mutations.put("entry invokes native constructor", n -> { var c = real(method(n.get(ENTRY), "createBlock")); ((MethodInsnNode)c.get(c.size() - 2)).owner = n.get(BLOCK).superName; });
        mutations.put("extra entry effect", n -> method(n.get(ENTRY), "<init>").instructions.insert(new InsnNode(NOP)));
        return mutations.entrySet().stream().flatMap(e -> Stream.of(false, true).map(target -> DynamicTest.dynamicTest(
            (target ? "target " : "source ") + e.getKey(), () -> { var n = fixture(target); e.getValue().accept(n); assertRefused(n); })));
    }

    private static void assertRefused(Map<String,ClassNode> nodes) {
        var failure = assertThrows(TransformationException.class, () -> StaticShapeBlockContract.inspect(nodes));
        assertTrue(failure.getMessage().startsWith("STATIC_SHAPE_BLOCK_UNSUPPORTED:"), failure.getMessage());
    }

    private static Map<String,ClassNode> fixture(boolean target) {
        var entry = entryFixture(target); var factory = real(method(entry, "createBlock"));
        ((TypeInsnNode)factory.get(0)).desc = BLOCK; ((MethodInsnNode)factory.get(factory.size() - 2)).owner = BLOCK;
        String nativeBlock = nativeBlock(target), props = properties(target), shape = shape(target);
        String state = target ? "net/minecraft/world/level/block/state/BlockState" : "net/minecraft/block/BlockState";
        String reader = target ? "net/minecraft/world/level/BlockGetter" : "net/minecraft/world/IBlockReader";
        String position = target ? "net/minecraft/core/BlockPos" : "net/minecraft/util/math/BlockPos";
        String context = target ? "net/minecraft/world/phys/shapes/CollisionContext" : "net/minecraft/util/math/shapes/ISelectionContext";
        var block = new ClassNode(); block.version = V1_8; block.access = ACC_PUBLIC | ACC_FINAL | ACC_SUPER;
        block.name = BLOCK; block.superName = nativeBlock;
        block.fields.add(new FieldNode(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "SHAPE", "L" + shape + ";", null, null));
        var ctor = add(block, ACC_PUBLIC, "<init>", "(L" + props + ";)V");
        ctor.instructions.add(new VarInsnNode(ALOAD, 0)); ctor.instructions.add(new VarInsnNode(ALOAD, 1));
        call(ctor, INVOKESPECIAL, nativeBlock, "<init>", "(L" + props + ";)V"); ctor.instructions.add(new InsnNode(RETURN));
        var clinit = add(block, ACC_STATIC, "<clinit>", "()V");
        for (int i = 0; i < 3; i++) clinit.instructions.add(new InsnNode(DCONST_0));
        for (double bound : new double[]{16.0D, 8.0D, 16.0D}) clinit.instructions.add(new LdcInsnNode(bound));
        call(clinit, INVOKESTATIC, nativeBlock, target ? "m_49796_" : "func_208617_a", "(DDDDDD)L" + shape + ";");
        clinit.instructions.add(new FieldInsnNode(PUTSTATIC, BLOCK, "SHAPE", "L" + shape + ";")); clinit.instructions.add(new InsnNode(RETURN));
        for (String name : target ? new String[]{"m_5940_", "m_5939_"} : new String[]{"func_220053_a", "func_220071_b"}) {
            var m = add(block, ACC_PUBLIC, name, "(L" + state + ";L" + reader + ";L" + position + ";L" + context + ";)L" + shape + ";");
            m.maxLocals = 5; m.instructions.add(new FieldInsnNode(GETSTATIC, BLOCK, "SHAPE", "L" + shape + ";")); m.instructions.add(new InsnNode(ARETURN));
        }
        return new LinkedHashMap<>(Map.of(ENTRY, entry, BLOCK, block));
    }

    private static ClassNode entryFixture(boolean target) {
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

    private static String nativeBlock(boolean target) { return target ? "net/minecraft/world/level/block/Block" : "net/minecraft/block/Block"; }
    private static String properties(boolean target) { return target ? "net/minecraft/world/level/block/state/BlockBehaviour$Properties" : "net/minecraft/block/AbstractBlock$Properties"; }
    private static String shape(boolean target) { return target ? "net/minecraft/world/phys/shapes/VoxelShape" : "net/minecraft/util/math/shapes/VoxelShape"; }
    private static String object(boolean target) { return "L" + (target ? "net/minecraftforge/registries/RegistryObject" : "net/minecraftforge/fml/RegistryObject") + ";"; }
    private static MethodNode add(ClassNode n, int access, String name, String descriptor) { var m = new MethodNode(access, name, descriptor, null, null); m.maxStack = 16; m.maxLocals = 2; n.methods.add(m); return m; }
    private static void call(MethodNode m, int opcode, String owner, String name, String descriptor) { m.instructions.add(new MethodInsnNode(opcode, owner, name, descriptor, false)); }
    private static MethodNode method(ClassNode n, String name) { return n.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow(); }
    private static MethodNode outline(Map<String,ClassNode> n) { return n.get(BLOCK).methods.stream().filter(m -> Set.of("func_220053_a", "m_5940_").contains(m.name)).findFirst().orElseThrow(); }
    private static MethodNode collision(Map<String,ClassNode> n) { return n.get(BLOCK).methods.stream().filter(m -> Set.of("func_220071_b", "m_5939_").contains(m.name)).findFirst().orElseThrow(); }
    private static List<AbstractInsnNode> real(MethodNode m) { return ClientConfigContract.code(m); }
    private static void replace(MethodNode m, int index, AbstractInsnNode replacement) { m.instructions.set(real(m).get(index), replacement); }
    private static Map<String,byte[]> bytes(Map<String,ClassNode> nodes) { var out = new TreeMap<String,byte[]>(); nodes.forEach((name, node) -> { var w = new ClassWriter(0); node.accept(w); out.put(name, w.toByteArray()); }); return out; }
    private static void assertBytes(Map<String,byte[]> expected, Map<String,ClassNode> nodes) { var actual = bytes(nodes); assertEquals(expected.keySet(), actual.keySet()); expected.forEach((name, value) -> assertArrayEquals(value, actual.get(name), name)); }
}

