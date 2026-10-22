package dev.legacybridge.converter.mapping.builtin;

import dev.legacybridge.converter.conversion.TransformationBindings;
import dev.legacybridge.converter.conversion.extract.ContentPlan;
import dev.legacybridge.converter.conversion.extract.StaticContentExtractor;
import dev.legacybridge.converter.conversion.extract.SubsetRejectionException;
import dev.legacybridge.converter.conversion.extract.SyntheticContentFixture;
import dev.legacybridge.converter.mapping.portable.PortableMappingBundle;
import dev.legacybridge.converter.mapping.source.MappingInputs;
import dev.legacybridge.runtime.compat.UnsupportedCompatibilityException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddedSimpleContentDataTest {
    @TempDir Path temporary;

    @Test void loadsOnlyABytePinnedFiniteSourceContract() throws Exception {
        byte[] resource = resource();
        assertEquals(6603, resource.length);
        assertEquals(EmbeddedSimpleContentData.DATA_SHA256,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(resource)));
        var inputs = EmbeddedSimpleContentData.load();
        assertEquals(MappingInputs.Scope.SIMPLE_CONTENT_CONTRACT, inputs.scope());
        assertFalse(inputs.complete());
        assertEquals(18, inputs.legacyApi().classes().size());
        assertEquals(26, inputs.legacyApi().classes().values().stream().mapToInt(value -> value.methods().size()).sum());
        assertEquals(31, inputs.legacyApi().classes().values().stream().mapToInt(value -> value.fields().size()).sum());
        assertEquals(18, inputs.legacyOfficial().getClasses().size());
        assertTrue(inputs.legacySrg().getClasses().isEmpty(), "No obfuscated-to-SRG table is embedded");
        assertTrue(inputs.targetOfficial().getClasses().isEmpty());
        assertTrue(inputs.targetSrg().getClasses().isEmpty());
        assertTrue(inputs.targetApi().classes().isEmpty());
        assertEquals(6, inputs.sources().size());
        assertTrue(inputs.diagnostics().stream().anyMatch(value -> value.contains("no obfuscated mapping or complete API hierarchy")));
        assertThrows(IllegalArgumentException.class, () -> new MappingInputs(inputs.legacyOfficial(), inputs.targetOfficial(),
                inputs.legacySrg(), inputs.targetSrg(), inputs.legacyApi(), inputs.targetApi(), inputs.sources(),
                inputs.diagnostics(), true, MappingInputs.Scope.SIMPLE_CONTENT_CONTRACT));
        assertThrows(IOException.class, () -> PortableMappingBundle.export(inputs, temporary.resolve("not-a-full-bundle")));
    }

    @Test void corruptOrOversizedResourceCannotFallBackToLocalData() throws Exception {
        byte[] damaged = resource(); damaged[damaged.length / 2] ^= 1;
        assertTrue(assertThrows(IOException.class, () -> EmbeddedSimpleContentData.load(new ByteArrayInputStream(damaged)))
                .getMessage().contains("SHA-256 mismatch"));
        assertThrows(IOException.class, () -> EmbeddedSimpleContentData.load(new ByteArrayInputStream(new byte[0])));
        assertThrows(IOException.class, () -> EmbeddedSimpleContentData.load(new ByteArrayInputStream(new byte[EmbeddedSimpleContentData.MAX_BYTES + 1])));
    }

    @ParameterizedTest
    @ValueSource(strings = {"schema", "profile", "baseline", "revision", "source-id", "source-hash", "duplicate-source",
            "missing-source", "row-type", "column-count", "duplicate-class", "duplicate-member", "alias-collision",
            "private-access", "instance-field", "descriptor", "constructor", "foreign-owner", "unterminated", "crlf"})
    void rejectsMalformedOrUnsupportedContract(String mutation) throws Exception {
        String original = new String(resource(), StandardCharsets.UTF_8);
        String source = "S\tlegacy.clientMappings\t7931ed6d723eceb1d621d05a76e10ddf643bf468c6ecf1c4ecf377bd72cf8b8c\n";
        String member = "M\tstacksTo\tfunc_200917_a\t(I)Lnet/minecraft/item/Item$Properties;\t1\n";
        String changed = switch (mutation) {
            case "schema" -> original.replace("CONTRACT\t1", "CONTRACT\t2");
            case "profile" -> original.replace("simple-content-v1", "logic-preserving-v1");
            case "baseline" -> original.replace("1.16.5", "1.16.4");
            case "revision" -> original.replace("\t17\t1\n", "\t17\t2\n");
            case "source-id" -> original.replace("S\tlegacy.clientMappings", "S\tunapproved.source");
            case "source-hash" -> original.replace("7931ed6d", "notahash");
            case "duplicate-source" -> original.replace(source, source + source);
            case "missing-source" -> original.replace(source, "");
            case "row-type" -> original + "EXEC\tanything\n";
            case "column-count" -> original.replace(member, member.stripTrailing() + "\textra\n");
            case "duplicate-class" -> original + "C\tnet/minecraft/item/Item\tnet/minecraft/world/item/Item\t33\tjava/lang/Object\t-\n";
            case "duplicate-member" -> original.replace(member, member + member);
            case "alias-collision" -> original.replace(member, member + member.replace("\tstacksTo\t", "\tother\t"));
            case "private-access" -> original.replace(member, member.replace("\t1\n", "\t2\n"));
            case "instance-field" -> original.replace("F\tITEMS\tITEMS\tLnet/minecraftforge/registries/IForgeRegistry;\t25", "F\tITEMS\tITEMS\tLnet/minecraftforge/registries/IForgeRegistry;\t17");
            case "descriptor" -> original.replace(member, member.replace("(I)", "(Q)"));
            case "constructor" -> original.replace("M\t<init>\t<init>", "M\t<init>\tother");
            case "foreign-owner" -> original.replace("C\tnet/minecraft/item/Item\t", "C\tthird/party/Item\t");
            case "unterminated" -> original.substring(0, original.length() - 1);
            case "crlf" -> original.replace("\n", "\r\n");
            default -> throw new AssertionError(mutation);
        };
        assertNotEquals(original, changed, mutation);
        assertThrows(IOException.class, () -> EmbeddedSimpleContentData.parse(changed.getBytes(StandardCharsets.UTF_8)), mutation);
    }

    @Test void rejectsMalformedUtf8AndParserByteOverflow() {
        assertThrows(IOException.class, () -> EmbeddedSimpleContentData.parse(new byte[]{(byte) 0xC3, 0x28}));
        assertThrows(IOException.class, () -> EmbeddedSimpleContentData.parse(new byte[EmbeddedSimpleContentData.MAX_BYTES + 1]));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sourceRuntimeAndOfficialAliasesProduceTheSameProvenContent(boolean official) throws Exception {
        var inputs = EmbeddedSimpleContentData.load();
        byte[] original = SyntheticContentFixture.bytecode("valid");
        var expected = extract(original, inputs);
        var writer = new ClassWriter(0);
        var reverse = inputs.legacyOfficial().reverse();
        new ClassReader(original).accept(new ClassRemapper(writer, new Remapper() {
            @Override public String map(String owner) { return official ? reverse.remapClass(owner) : owner; }
            @Override public String mapMethodName(String owner, String name, String descriptor) {
                var definition = inputs.legacyOfficial().getClass(reverse.remapClass(owner));
                if (official || definition == null) return name;
                var member = definition.getMethod(name, reverse.remapDescriptor(descriptor));
                return member == null ? name : member.getMapped();
            }
            @Override public String mapFieldName(String owner, String name, String descriptor) {
                var definition = inputs.legacyOfficial().getClass(reverse.remapClass(owner));
                return official || definition == null || definition.getField(name) == null ? name : definition.getField(name).getMapped();
            }
        }), 0);
        byte[] alternative = writer.toByteArray();
        assertFalse(Arrays.equals(original, alternative));
        assertEquals(expected, extract(alternative, inputs));
        assertEquals(2, expected.items().size()); assertEquals(1, expected.blocks().size()); assertEquals(1, expected.sounds().size());
        assertNotNull(TransformationBindings.forPlan(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"subclass", "reflection", "unused", "branch", "annotation", "network", "instance",
            "unattached", "doubleattach", "foreign", "capture", "food", "durabilitystack"})
    void unsupportedBehaviorRemainsUnsupportedWithEmbeddedData(String shape) throws Exception {
        var inputs = EmbeddedSimpleContentData.load();
        assertThrows(SubsetRejectionException.class, () -> extract(SyntheticContentFixture.bytecode(shape), inputs));
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown-member", "wrong-descriptor", "wrong-staticness"})
    void neverTrustsAnUnknownOrForgedExternalReference(String mutation) throws Exception {
        ClassNode node = new ClassNode();
        new ClassReader(SyntheticContentFixture.bytecode("valid")).accept(node, 0);
        boolean modified = false;
        for (var method : node.methods) for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.equals("stacksTo")) {
                if (mutation.equals("unknown-member")) call.name = "func_guessed";
                else if (mutation.equals("wrong-descriptor")) call.desc = call.desc.replace("(I)", "(F)");
                else call.setOpcode(Opcodes.INVOKESTATIC);
                modified = true;
            }
        }
        assertTrue(modified);
        ClassWriter writer = new ClassWriter(0); node.accept(writer);
        var inputs = EmbeddedSimpleContentData.load();
        assertThrows(SubsetRejectionException.class, () -> extract(writer.toByteArray(), inputs));
    }

    @Test void recognizingDurabilityDoesNotEnableAnUnsupportedAdapter() throws Exception {
        var inputs = EmbeddedSimpleContentData.load();
        assertTrue(inputs.legacyApi().getClass("net/minecraft/item/Item$Properties").methods().stream()
                .anyMatch(method -> method.name().equals("durability")));
        var plan = new ContentPlan("fixturemod", "1", List.of(new ContentPlan.ItemDefinition("unsupported", null,
                new ContentPlan.ItemProperties(1, 128, false, "COMMON", "MISC"))), List.of(), List.of());
        assertThrows(UnsupportedCompatibilityException.class, () -> TransformationBindings.forPlan(plan));
    }

    private static ContentPlan extract(byte[] bytes, MappingInputs inputs) throws SubsetRejectionException {
        return new StaticContentExtractor().extract(Map.of("fixture/SimpleMod.class", bytes), inputs, "fixturemod", "1.2.3");
    }

    private static byte[] resource() throws IOException {
        try (var input = EmbeddedSimpleContentDataTest.class.getResourceAsStream("/" + EmbeddedSimpleContentData.RESOURCE_PATH)) {
            assertNotNull(input); return input.readAllBytes();
        }
    }
}
