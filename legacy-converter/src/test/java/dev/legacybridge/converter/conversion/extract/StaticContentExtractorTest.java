package dev.legacybridge.converter.conversion.extract;

import dev.legacybridge.converter.mapping.source.ApiIndex;
import dev.legacybridge.converter.mapping.source.MappingInputs;
import net.minecraftforge.srgutils.IMappingFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

class StaticContentExtractorTest {
    static final String OWNER = "fixture/SimpleMod";
    static final String MOD_ID = "fixturemod";
    static final String DR = "net/minecraftforge/registries/DeferredRegister";
    static final String RO = "net/minecraftforge/fml/RegistryObject";
    static final String FR = "net/minecraftforge/registries/ForgeRegistries";
    static final String REGISTRY = "net/minecraftforge/registries/IForgeRegistry";
    static final String ENTRY = "net/minecraftforge/registries/IForgeRegistryEntry";
    static final String CONTEXT = "net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext";
    static final String BUS = "net/minecraftforge/eventbus/api/IEventBus";
    static final String ITEM = "net/minecraft/item/Item";
    static final String IP = ITEM + "$Properties";
    static final String BLOCK = "net/minecraft/block/Block";
    static final String BP = "net/minecraft/block/AbstractBlock$Properties";
    static final String MATERIAL = "net/minecraft/block/material/Material";
    static final String TAB = "net/minecraft/item/ItemGroup";
    static final String SOUND = "net/minecraft/util/SoundEvent";
    static final String RL = "net/minecraft/util/ResourceLocation";
    static final Handle METAFACTORY = new Handle(H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);

    @Test void extractsItemBlockBlockItemAndSimpleSoundWithoutLoadingAnySourceClass() throws Exception {
        byte[] bytes = fixture("valid");
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
        ContentPlan result = extract(bytes);
        assertEquals(MOD_ID, result.modId());
        assertEquals("1.2.3", result.modVersion());
        assertEquals(2, result.items().size());
        assertEquals(1, result.blocks().size());
        assertEquals(1, result.sounds().size());
        var item = result.items().stream().filter(value -> value.name().equals("ingot")).findFirst().orElseThrow();
        assertEquals(new ContentPlan.ItemProperties(32, 0, false, "COMMON", "MISC"), item.properties());
        assertNull(item.block());
        assertEquals(MOD_ID + ":block", result.items().get(0).block());
        assertEquals(new ContentPlan.BlockProperties("STONE", 2, 6, "STONE", false, false), result.blocks().get(0).properties());
        assertEquals(new ContentPlan.SoundDefinition("ping", MOD_ID + ":ping"), result.sounds().get(0));
        assertArrayEquals(hash, MessageDigest.getInstance("SHA-256").digest(bytes));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("fixture.SimpleMod"));
    }

    @Test void rejectsCustomSubclass() throws Exception { rejects("subclass", "subclasses"); }
    @Test void rejectsReflectionEvenInAnUncalledMethod() throws Exception { rejects("reflection", "Unsupported API/type"); }
    @Test void rejectsArbitraryMethodEvenWhenUncalled() throws Exception { rejects("unused", "Unaccounted executable method"); }
    @Test void rejectsBranchesInsteadOfGuessingAPath() throws Exception { rejects("branch", "Branches and loops"); }
    @Test void rejectsUnsupportedAnnotation() throws Exception { rejects("annotation", "Unsupported class annotation"); }
    @Test void rejectsNetworkTypeInFieldSignature() throws Exception { rejects("network", "Unsupported API/type"); }
    @Test void rejectsInstanceState() throws Exception { rejects("instance", "static final"); }
    @Test void rejectsUnattachedRegistry() throws Exception { rejects("unattached", "attach exactly once"); }
    @Test void rejectsTwiceAttachedRegistry() throws Exception { rejects("doubleattach", "attach exactly once"); }
    @Test void rejectsForeignRegistryNamespace() throws Exception { rejects("foreign", "namespace differs"); }
    @Test void rejectsCapturedSupplier() throws Exception { rejects("capture", "capture-free"); }
    @Test void rejectsUnsupportedItemProperty() throws Exception { rejects("food", "Unsupported API/type"); }
    @Test void rejectsLegacyPropertyCombinationThatWouldThrow() throws Exception { rejects("durabilitystack", "would throw"); }
    @Test void rejectsUnknownSourceApiDeclaration() throws Exception {
        byte[] bytes = fixture("valid");
        MappingInputs original = inputs(bytes);
        var missing = new MappingInputs(original.legacyOfficial(), original.targetOfficial(), original.legacySrg(), original.targetSrg(),
                ApiIndex.empty(), ApiIndex.empty(), List.of(), List.of(), true);
        var error = assertThrows(SubsetRejectionException.class, () -> new StaticContentExtractor().extract(Map.of(OWNER, bytes), missing, MOD_ID, "1"));
        assertTrue(error.getMessage().contains("declaration missing"));
    }

    @Test void rejectsIncompleteMappingInputs() throws Exception {
        byte[] bytes = fixture("valid");
        MappingInputs original = inputs(bytes);
        var incomplete = new MappingInputs(original.legacyOfficial(), original.targetOfficial(), original.legacySrg(), original.targetSrg(),
                original.legacyApi(), original.targetApi(), List.of(), List.of(), false);
        assertThrows(SubsetRejectionException.class, () -> new StaticContentExtractor().extract(Map.of(OWNER, bytes), incomplete, MOD_ID, "1"));
    }

    @Test void rejectsForgedEntryNameAndMalformedBytes() throws Exception {
        byte[] bytes = fixture("valid");
        assertThrows(SubsetRejectionException.class, () -> new StaticContentExtractor().extract(Map.of("different/Name.class", bytes), inputs(bytes), MOD_ID, "1"));
        assertThrows(SubsetRejectionException.class, () -> new StaticContentExtractor().extract(Map.of(OWNER, new byte[]{1}), inputs(bytes), MOD_ID, "1"));
    }

    @Test void sourceNamesAreComposedFromOfficialAndSrgDataAndCheckedAgainstApi() throws Exception {
        IMappingFile official = mapping("""
                net.minecraft.world.item.Item$Properties -> a:
                    1:2:net.minecraft.world.item.Item$Properties stacksTo(int) -> b
                """);
        IMappingFile srg = mapping("a net/minecraft/item/Item$Properties\n\tb (I)La; func_fixture\n");
        ApiIndex api = new ApiIndex(Map.of(IP, new ApiIndex.ApiClass(IP, ACC_PUBLIC, "java/lang/Object", List.of(),
                List.of(new ApiIndex.ApiMember("stacksTo", "(I)L" + IP + ";", ACC_PUBLIC)), List.of())));
        LegacyApiNames names = new LegacyApiNames(new MappingInputs(official, official, srg, srg, api, api, List.of(), List.of(), true));
        assertEquals(new LegacyApiNames.Member(IP, "stacksTo", "(I)L" + IP + ";"), names.member(IP, "func_fixture", "(I)L" + IP + ";", false, false));
        assertEquals("stacksTo", names.member("net/minecraft/world/item/Item$Properties", "stacksTo",
                "(I)Lnet/minecraft/world/item/Item$Properties;", false, false).name());
        assertThrows(LegacyApiNames.Refusal.class, () -> names.member(IP, "func_guessed", "(I)L" + IP + ";", false, false));
        assertThrows(LegacyApiNames.Refusal.class, () -> names.member(IP, "func_fixture", "(I)L" + IP + ";", false, true));
    }

    @Test void planListsAreImmutable() {
        var items = new ArrayList<ContentPlan.ItemDefinition>();
        var plan = new ContentPlan(MOD_ID, "1", items, List.of(), List.of());
        items.add(new ContentPlan.ItemDefinition("x", null, null));
        assertTrue(plan.items().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> plan.items().add(items.get(0)));
    }

    static ContentPlan extract(byte[] bytes) throws Exception {
        return new StaticContentExtractor().extract(Map.of(OWNER + ".class", bytes), inputs(bytes), MOD_ID, "1.2.3");
    }
    static void rejects(String shape, String diagnostic) throws Exception {
        byte[] bytes = fixture(shape);
        var error = assertThrows(SubsetRejectionException.class, () -> extract(bytes));
        assertTrue(error.getMessage().contains(diagnostic), error.getMessage());
        assertEquals(List.of(error.getMessage()), error.diagnostics());
    }

    /** Declaration-only API fixture; production tests use the pinned actual API inventories. */
    static MappingInputs inputs(byte[] bytes) throws Exception {
        Map<String, List<ApiIndex.ApiMember>> methods = new HashMap<>(), fields = new HashMap<>();
        new ClassReader(bytes).accept(new ClassVisitor(ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(ASM9) {
                    @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
                        if (!owner.equals(OWNER)) methods.computeIfAbsent(owner, key -> new ArrayList<>()).add(new ApiIndex.ApiMember(name, descriptor,
                                ACC_PUBLIC | (opcode == INVOKESTATIC ? ACC_STATIC : 0)));
                    }
                    @Override public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                        if (!owner.equals(OWNER)) fields.computeIfAbsent(owner, key -> new ArrayList<>()).add(new ApiIndex.ApiMember(name, descriptor, ACC_PUBLIC | ACC_STATIC));
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        Map<String, ApiIndex.ApiClass> classes = new HashMap<>();
        var owners = new java.util.HashSet<>(methods.keySet()); owners.addAll(fields.keySet());
        for (String owner : owners) classes.put(owner, new ApiIndex.ApiClass(owner, ACC_PUBLIC, "java/lang/Object", List.of(),
                methods.getOrDefault(owner, List.of()).stream().distinct().toList(), fields.getOrDefault(owner, List.of()).stream().distinct().toList()));
        IMappingFile empty = mapping("placeholder Placeholder\n");
        ApiIndex api = new ApiIndex(classes);
        return new MappingInputs(empty, empty, empty, empty, api, api, List.of(), List.of(), true);
    }
    static IMappingFile mapping(String text) throws Exception {
        return IMappingFile.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    static byte[] fixture(String shape) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V1_8, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, OWNER, null, shape.equals("subclass") ? ITEM : "java/lang/Object", null);
        var mod = writer.visitAnnotation("Lnet/minecraftforge/fml/common/Mod;", true); mod.visit("value", MOD_ID); mod.visitEnd();
        if (shape.equals("annotation")) writer.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", true).visitEnd();
        if (shape.equals("network")) writer.visitField(ACC_STATIC | ACC_FINAL, "NETWORK", "Lnet/minecraftforge/fml/network/simple/SimpleChannel;", null, null).visitEnd();
        if (shape.equals("instance")) writer.visitField(ACC_PRIVATE, "state", "I", null, null).visitEnd();
        for (String registrar : List.of("ITEMS", "BLOCKS", "SOUND_EVENTS")) writer.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, registrar, "L" + DR + ";", null, null).visitEnd();
        for (String field : List.of("INGOT", "BLOCK", "BLOCK_ITEM", "PING")) writer.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, field, "L" + RO + ";", null, null).visitEnd();
        MethodVisitor init = method(writer, ACC_STATIC, "<clinit>", "()V");
        for (String registrar : List.of("ITEMS", "BLOCKS", "SOUND_EVENTS")) {
            init.visitFieldInsn(GETSTATIC, FR, registrar, "L" + REGISTRY + ";");
            init.visitLdcInsn(shape.equals("foreign") ? "other_mod" : MOD_ID);
            init.visitMethodInsn(INVOKESTATIC, DR, "create", "(L" + REGISTRY + ";Ljava/lang/String;)L" + DR + ";", false);
            init.visitFieldInsn(PUTSTATIC, OWNER, registrar, "L" + DR + ";");
        }
        register(init, "ITEMS", "ingot", "INGOT", "item", ITEM, shape.equals("capture"));
        register(init, "BLOCKS", "block", "BLOCK", "block", BLOCK, false);
        register(init, "ITEMS", "block", "BLOCK_ITEM", "blockItem", ITEM, false);
        register(init, "SOUND_EVENTS", "ping", "PING", "sound", SOUND, false);
        end(init, RETURN);
        MethodVisitor constructor = method(writer, ACC_PUBLIC, "<init>", "()V");
        constructor.visitVarInsn(ALOAD, 0); constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitMethodInsn(INVOKESTATIC, CONTEXT, "get", "()L" + CONTEXT + ";", false);
        constructor.visitMethodInsn(INVOKEVIRTUAL, CONTEXT, "getModEventBus", "()L" + BUS + ";", false);
        constructor.visitVarInsn(ASTORE, 1);
        for (String registrar : List.of("ITEMS", "BLOCKS", "SOUND_EVENTS")) {
            if (shape.equals("unattached") && registrar.equals("ITEMS")) continue;
            for (int i = 0; i < (shape.equals("doubleattach") && registrar.equals("ITEMS") ? 2 : 1); i++) {
                constructor.visitFieldInsn(GETSTATIC, OWNER, registrar, "L" + DR + ";"); constructor.visitVarInsn(ALOAD, 1);
                constructor.visitMethodInsn(INVOKEVIRTUAL, DR, "register", "(L" + BUS + ";)V", false);
            }
        }
        end(constructor, RETURN);
        MethodVisitor item = method(writer, ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC, "item", "()L" + ITEM + ";");
        item.visitTypeInsn(NEW, ITEM); item.visitInsn(DUP); itemProperties(item, "TAB_MISC");
        if (shape.equals("durabilitystack")) { item.visitIntInsn(BIPUSH, 20); item.visitMethodInsn(INVOKEVIRTUAL, IP, "durability", "(I)L" + IP + ";", false); }
        if (shape.equals("food")) item.visitMethodInsn(INVOKEVIRTUAL, IP, "food", "(Lnet/minecraft/item/Food;)L" + IP + ";", false);
        item.visitIntInsn(BIPUSH, 32); item.visitMethodInsn(INVOKEVIRTUAL, IP, "stacksTo", "(I)L" + IP + ";", false);
        item.visitMethodInsn(INVOKESPECIAL, ITEM, "<init>", "(L" + IP + ";)V", false);
        if (shape.equals("branch")) { Label target = new Label(); item.visitJumpInsn(GOTO, target); item.visitLabel(target); }
        end(item, ARETURN);
        MethodVisitor block = method(writer, ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC, "block", "()L" + BLOCK + ";");
        block.visitTypeInsn(NEW, BLOCK); block.visitInsn(DUP); block.visitFieldInsn(GETSTATIC, MATERIAL, "STONE", "L" + MATERIAL + ";");
        block.visitMethodInsn(INVOKESTATIC, BP, "of", "(L" + MATERIAL + ";)L" + BP + ";", false);
        block.visitInsn(FCONST_2); block.visitLdcInsn(6f); block.visitMethodInsn(INVOKEVIRTUAL, BP, "strength", "(FF)L" + BP + ";", false);
        block.visitMethodInsn(INVOKESPECIAL, BLOCK, "<init>", "(L" + BP + ";)V", false); end(block, ARETURN);
        MethodVisitor blockItem = method(writer, ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC, "blockItem", "()L" + ITEM + ";");
        String bi = "net/minecraft/item/BlockItem";
        blockItem.visitTypeInsn(NEW, bi); blockItem.visitInsn(DUP); blockItem.visitFieldInsn(GETSTATIC, OWNER, "BLOCK", "L" + RO + ";");
        blockItem.visitMethodInsn(INVOKEVIRTUAL, RO, "get", "()L" + ENTRY + ";", false); blockItem.visitTypeInsn(CHECKCAST, BLOCK);
        itemProperties(blockItem, "TAB_BUILDING_BLOCKS"); blockItem.visitMethodInsn(INVOKESPECIAL, bi, "<init>", "(L" + BLOCK + ";L" + IP + ";)V", false); end(blockItem, ARETURN);
        MethodVisitor sound = method(writer, ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC, "sound", "()L" + SOUND + ";");
        sound.visitTypeInsn(NEW, SOUND); sound.visitInsn(DUP); sound.visitTypeInsn(NEW, RL); sound.visitInsn(DUP);
        sound.visitLdcInsn(MOD_ID); sound.visitLdcInsn("ping"); sound.visitMethodInsn(INVOKESPECIAL, RL, "<init>", "(Ljava/lang/String;Ljava/lang/String;)V", false);
        sound.visitMethodInsn(INVOKESPECIAL, SOUND, "<init>", "(L" + RL + ";)V", false); end(sound, ARETURN);
        if (shape.equals("reflection")) {
            MethodVisitor extra = method(writer, ACC_STATIC, "unrelated", "()V"); extra.visitLdcInsn("java.lang.System");
            extra.visitMethodInsn(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", false); extra.visitInsn(POP); end(extra, RETURN);
        }
        if (shape.equals("unused")) end(method(writer, ACC_STATIC, "unrelated", "()V"), RETURN);
        writer.visitEnd(); return writer.toByteArray();
    }
    static void itemProperties(MethodVisitor visitor, String tab) {
        visitor.visitTypeInsn(NEW, IP); visitor.visitInsn(DUP); visitor.visitMethodInsn(INVOKESPECIAL, IP, "<init>", "()V", false);
        visitor.visitFieldInsn(GETSTATIC, TAB, tab, "L" + TAB + ";"); visitor.visitMethodInsn(INVOKEVIRTUAL, IP, "tab", "(L" + TAB + ";)L" + IP + ";", false);
    }
    static void register(MethodVisitor visitor, String registrar, String name, String field, String helper, String type, boolean captured) {
        visitor.visitFieldInsn(GETSTATIC, OWNER, registrar, "L" + DR + ";"); visitor.visitLdcInsn(name);
        visitor.visitInvokeDynamicInsn("get", captured ? "(Ljava/lang/String;)Ljava/util/function/Supplier;" : "()Ljava/util/function/Supplier;", METAFACTORY,
                Type.getMethodType("()Ljava/lang/Object;"), new Handle(H_INVOKESTATIC, OWNER, helper, "()L" + type + ";", false), Type.getMethodType("()L" + type + ";"));
        visitor.visitMethodInsn(INVOKEVIRTUAL, DR, "register", "(Ljava/lang/String;Ljava/util/function/Supplier;)L" + RO + ";", false);
        visitor.visitFieldInsn(PUTSTATIC, OWNER, field, "L" + RO + ";");
    }
    static MethodVisitor method(ClassWriter writer, int access, String name, String descriptor) {
        MethodVisitor visitor = writer.visitMethod(access, name, descriptor, null, null); visitor.visitCode(); return visitor;
    }
    static void end(MethodVisitor visitor, int opcode) { visitor.visitInsn(opcode); visitor.visitMaxs(0, 0); visitor.visitEnd(); }
}
