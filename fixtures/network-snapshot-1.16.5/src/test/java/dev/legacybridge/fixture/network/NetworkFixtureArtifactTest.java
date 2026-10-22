package dev.legacybridge.fixture.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static artifact checks only: no Class.forName, fixture references, reflection or original execution. */
final class NetworkFixtureArtifactTest {
    private static final String OWNER = "dev/legacybridge/fixture/network/NetworkSnapshot";
    private static final String PREFIX = "net/minecraftforge/fml/network/";

    @Test
    void identityAndDependenciesPinTheGenuineLegacyEnvironment() throws IOException {
        try (JarFile jar = input()) {
            String toml = new String(bytes(jar, "META-INF/mods.toml"), StandardCharsets.UTF_8);
            assertTrue(toml.contains("modId=\"legacybridge_network\""));
            assertTrue(toml.contains("version=\"1.0.0\""));
            assertTrue(toml.contains("versionRange=\"[36.2.42,37)\""));
            assertTrue(toml.contains("versionRange=\"[1.16.5,1.17)\""));
            assertEquals("network-snapshot-v1", jar.getManifest().getMainAttributes().getValue("LegacyBridge-Fixture"));
            assertEquals("1.0.0", jar.getManifest().getMainAttributes().getValue("Implementation-Version"));
        }
    }

    @Test
    void exactlyTwoOwnedJava8ClassesAndNoHarnessPlatformOrNestedLibraries() throws IOException {
        Set<String> expected = new HashSet<String>(Arrays.asList(OWNER + ".class",
                "dev/legacybridge/fixture/network/SnapshotMessage.class"));
        try (JarFile jar = input()) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                assertFalse(name.endsWith(".jar") || name.endsWith(".java"), name);
                assertFalse(name.startsWith("META-INF/services/") || name.startsWith("assets/minecraft/"), name);
                if (!name.endsWith(".class")) continue;
                assertTrue(expected.remove(name), name);
                ClassNode node = node(jar, name);
                assertEquals(Opcodes.V1_8, node.version);
                assertTrue((node.access & Opcodes.ACC_FINAL) != 0);
            }
            assertTrue(expected.isEmpty(), expected.toString());
        }
    }

    @Test
    void singleIdZeroUsesSixArgumentsAndExplicitPlayToClient() throws IOException {
        try (JarFile jar = input()) {
            ClassNode node = node(jar, OWNER + ".class");
            int registrations = 0;
            boolean direction = false;
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof FieldInsnNode) {
                        FieldInsnNode field = (FieldInsnNode) instruction;
                        if (field.owner.equals(PREFIX + "NetworkDirection")) {
                            assertEquals("PLAY_TO_CLIENT", field.name);
                            direction = true;
                        }
                    }
                    if (instruction instanceof MethodInsnNode) {
                        MethodInsnNode call = (MethodInsnNode) instruction;
                        if (call.name.equals("registerMessage")) {
                            registrations++;
                            assertEquals("<init>", method.name);
                            assertEquals(PREFIX + "simple/SimpleChannel", call.owner);
                            assertEquals(6, Type.getArgumentTypes(call.desc).length);
                            assertEquals("Ljava/util/Optional;", Type.getArgumentTypes(call.desc)[5].getDescriptor());
                            assertTrue(call.desc.endsWith("L" + PREFIX + "simple/IndexedMessageCodec$MessageHandler;"));
                            assertTrue(hasOpcode(method, Opcodes.ICONST_0));
                        }
                    }
                }
            }
            assertEquals(1, registrations);
            assertTrue(direction);
            assertTrue(constants(method(node, "protocol")).contains("1"));
            assertTrue(constants(method(node, "accepts")).contains("1"));
        }
    }

    @Test
    void codecRequiresExactlyEightBytesAndUsesOnlyTwoFixedIntReadsAndWrites() throws IOException {
        try (JarFile jar = input()) {
            ClassNode node = node(jar, OWNER + ".class");
            MethodNode decoder = method(node, "decode");
            assertTrue(hasInt(decoder, 8));
            assertTrue(hasOpcode(decoder, Opcodes.IF_ICMPEQ) || hasOpcode(decoder, Opcodes.IF_ICMPNE));
            assertTrue(hasOpcode(decoder, Opcodes.ICONST_M1));
            assertEquals(Arrays.asList("readableBytes()I", "readInt()I", "readInt()I"), bufferCalls(decoder));
            assertEquals(Arrays.asList("writeInt(I)Lio/netty/buffer/ByteBuf;",
                    "writeInt(I)Lio/netty/buffer/ByteBuf;"), bufferCalls(method(node, "encode")));
            ClassNode dto = node(jar, "dev/legacybridge/fixture/network/SnapshotMessage.class");
            assertEquals(2, dto.fields.size());
            dto.fields.forEach(field -> {
                assertEquals("I", field.desc);
                assertTrue((field.access & Opcodes.ACC_FINAL) != 0);
            });
        }
    }

    @Test
    void handlerEnqueuesOwnedCaptureAndHasObservableBoundedErrorsWithoutC2SOrWorldCalls() throws IOException {
        try (JarFile jar = input()) {
            ClassNode node = node(jar, OWNER + ".class");
            MethodNode handler = method(node, "handle");
            boolean enqueued = false;
            boolean handled = false;
            boolean capture = false;
            for (AbstractInsnNode instruction : handler.instructions) {
                if (instruction instanceof InvokeDynamicInsnNode) {
                    InvokeDynamicInsnNode dynamic = (InvokeDynamicInsnNode) instruction;
                    assertEquals("(Ldev/legacybridge/fixture/network/SnapshotMessage;L" + PREFIX
                            + "NetworkEvent$Context;)Ljava/lang/Runnable;", dynamic.desc);
                    capture = true;
                }
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (call.name.equals("enqueueWork")) enqueued = true;
                    if (call.name.equals("setPacketHandled")) {
                        assertTrue(enqueued);
                        handled = true;
                    }
                }
            }
            assertTrue(capture && enqueued && handled);
            Set<String> reasons = new HashSet<String>(constants(method(node, "apply")));
            assertTrue(reasons.contains("NETWORK_CLOSED_CONTEXT"));
            assertTrue(reasons.contains("NETWORK_INVALID_PAYLOAD"));
            assertTrue(reasons.contains("NETWORK_SESSION_MUST_START_AT_ONE"));
            assertTrue(reasons.contains("NETWORK_DIFFERENT_LIVE_CONNECTION"));
            assertTrue(reasons.contains("NETWORK_OUT_OF_ORDER"));
            assertTrue(hasInt(method(node, "reject"), 20));
            assertTrue(constants(method(node, "reject")).contains("NETWORK_REJECTIONS_RATE_LIMITED"));
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode instruction : method.instructions) {
                    if (!(instruction instanceof MethodInsnNode)) continue;
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    assertFalse(call.owner.startsWith("net/minecraft/client/"), call.owner);
                    assertFalse(call.owner.startsWith("net/minecraft/world/"), call.owner);
                    assertFalse(call.owner.startsWith("java/lang/reflect/"), call.owner);
                    assertFalse(call.name.equals("sendToServer") || call.name.equals("getSender")
                            || call.name.equals("reply") || call.name.equals("disconnect"), call.name);
                }
            }
        }
    }

    private static List<String> bufferCalls(MethodNode method) {
        List<String> calls = new ArrayList<String>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.owner.equals("net/minecraft/network/PacketBuffer")) calls.add(call.name + call.desc);
            }
        }
        return calls;
    }

    private static MethodNode method(ClassNode node, String name) {
        for (MethodNode method : node.methods) if (method.name.equals(name)) return method;
        throw new AssertionError(name);
    }

    private static List<String> constants(MethodNode method) {
        List<String> values = new ArrayList<String>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof LdcInsnNode && ((LdcInsnNode) instruction).cst instanceof String) {
                values.add((String) ((LdcInsnNode) instruction).cst);
            }
        }
        return values;
    }

    private static boolean hasInt(MethodNode method, int value) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof IntInsnNode && ((IntInsnNode) instruction).operand == value) return true;
        }
        return false;
    }

    private static boolean hasOpcode(MethodNode method, int opcode) {
        for (AbstractInsnNode instruction : method.instructions) if (instruction.getOpcode() == opcode) return true;
        return false;
    }

    private static ClassNode node(JarFile jar, String path) throws IOException {
        ClassNode node = new ClassNode();
        new ClassReader(bytes(jar, path)).accept(node, 0);
        return node;
    }

    private static JarFile input() throws IOException {
        String path = System.getProperty("fixture.jar");
        assertNotNull(path, "The standalone test task must supply the production-SRG artifact");
        return new JarFile(path);
    }

    private static byte[] bytes(JarFile jar, String path) throws IOException {
        JarEntry entry = jar.getJarEntry(path);
        assertNotNull(entry, path);
        assertTrue(entry.getSize() >= 0 && entry.getSize() <= 1024 * 1024, path);
        try (InputStream input = jar.getInputStream(entry); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > 1024 * 1024) throw new IOException("Fixture entry too large");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }
}
