package dev.legacybridge.converter.testutil;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SyntheticForgeJarFactory {
    private SyntheticForgeJarFactory() {
    }

    public static Path create(Path target) throws IOException {
        return create(target, Map.of());
    }

    public static Path create(Path target, Map<String, byte[]> additionalEntries) throws IOException {
        Path fixtureRoot = Path.of(requiredProperty("legacybridge.fixtureRoot"));
        byte[] modsToml = Files.readAllBytes(
                fixtureRoot.resolve("synthetic-forge-1.16.5/META-INF/mods.toml")
        );
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("META-INF/mods.toml", modsToml);
        entries.put("dev/legacybridge/fixture/LegacyMod.class", legacyClass());
        entries.putAll(additionalEntries);
        return TestJarBuilder.write(target, entries);
    }

    public static Path createFeatureRich(Path target) throws IOException {
        Path fixtureRoot = Path.of(requiredProperty("legacybridge.fixtureRoot"));
        String modsToml = Files.readString(
                fixtureRoot.resolve("synthetic-forge-1.16.5/META-INF/mods.toml")
        ) + """

                [[dependencies.legacybridge_fixture]]
                modId="jei"
                mandatory=true
                versionRange="[7,)"
                ordering="AFTER"
                side="BOTH"
                """;
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("META-INF/mods.toml", modsToml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        entries.put("META-INF/MANIFEST.MF", ("Manifest-Version: 1.0\r\n"
                + "Implementation-Version: 2.3.4\r\n"
                + "MixinConfigs: mixins.legacybridge.json\r\n\r\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        entries.put("dev/legacybridge/fixture/LegacyMod.class", legacyClass());
        entries.put("dev/legacybridge/fixture/FeatureMod.class", featureRichClass());
        entries.put("mixins.legacybridge.json", ("""
                {"required":true,"package":"dev.legacybridge.mixin",
                 "compatibilityLevel":"JAVA_8","mixins":["CommonMixin"],
                 "client":["ClientMixin"]}
                """).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        entries.put("META-INF/accesstransformer.cfg", ("""
                # synthetic lexical fixture
                public net.minecraft.world.World field_234923_a_
                """).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        entries.put("META-INF/coremods.json",
                "{\"legacybridge_fixture\":\"coremods/example.js\"}"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        entries.put("coremods/example.js", "throw 'must never execute';"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        entries.put("assets/legacybridge_fixture/lang/en_us.json", "{}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        entries.put("data/legacybridge_fixture/recipes/example.json", "{}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        entries.put("data/legacybridge_fixture/loot_tables/blocks/example.json", "{}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        entries.put("data/legacybridge_fixture/worldgen/configured_feature/example.json", "{}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        entries.put("data/legacybridge_fixture/dimension/example.json", "{}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return TestJarBuilder.write(target, entries);
    }

    public static byte[] classWithObservableStaticInitializer(String propertyName) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                "dev/legacybridge/fixture/ExplosiveInitializer",
                null,
                "java/lang/Object",
                null
        );
        MethodVisitor initializer = writer.visitMethod(
                Opcodes.ACC_STATIC,
                "<clinit>",
                "()V",
                null,
                null
        );
        initializer.visitCode();
        initializer.visitLdcInsn(propertyName);
        initializer.visitLdcInsn("executed");
        initializer.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/lang/System",
                "setProperty",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                false
        );
        initializer.visitInsn(Opcodes.POP);
        initializer.visitInsn(Opcodes.RETURN);
        initializer.visitMaxs(2, 0);
        initializer.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] legacyClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                "dev/legacybridge/fixture/LegacyMod",
                null,
                "java/lang/Object",
                null
        );

        AnnotationVisitor annotation = writer.visitAnnotation("Lnet/minecraftforge/fml/common/Mod;", true);
        annotation.visit("value", "legacybridge_fixture");
        annotation.visitEnd();

        writer.visitField(
                Opcodes.ACC_PRIVATE,
                "identifier",
                "Lnet/minecraft/util/ResourceLocation;",
                null,
                null
        ).visitEnd();

        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] featureRichClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                "dev/legacybridge/fixture/FeatureMod",
                null,
                "net/minecraft/tileentity/TileEntity",
                new String[]{"net/minecraftforge/common/capabilities/ICapabilityProvider"}
        );
        writer.visitField(
                Opcodes.ACC_PRIVATE,
                "channel",
                "Lnet/minecraftforge/fml/network/simple/SimpleChannel;",
                null,
                null
        ).visitEnd();
        writer.visitField(
                Opcodes.ACC_PRIVATE,
                "entityType",
                "Lnet/minecraft/entity/EntityType;",
                null,
                null
        ).visitEnd();
        writer.visitField(
                Opcodes.ACC_PRIVATE,
                "generator",
                "Lnet/minecraft/world/gen/ChunkGenerator;",
                null,
                null
        ).visitEnd();
        writer.visitField(
                Opcodes.ACC_PRIVATE,
                "screen",
                "Lnet/minecraft/client/gui/screen/Screen;",
                null,
                null
        ).visitEnd();
        writer.visitField(
                Opcodes.ACC_PRIVATE,
                "renderer",
                "Lnet/minecraft/client/renderer/entity/EntityRenderer;",
                null,
                null
        ).visitEnd();
        writer.visitField(
                Opcodes.ACC_PRIVATE,
                "register",
                "Lnet/minecraftforge/registries/DeferredRegister;",
                null,
                null
        ).visitEnd();

        MethodVisitor probe = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "probe",
                "()V",
                null,
                new String[]{"java/lang/ClassNotFoundException"}
        );
        probe.visitCode();
        probe.visitLdcInsn("dev.legacybridge.fixture.OptionalTarget");
        probe.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/lang/Class",
                "forName",
                "(Ljava/lang/String;)Ljava/lang/Class;",
                false
        );
        probe.visitInsn(Opcodes.POP);
        probe.visitFieldInsn(
                Opcodes.GETSTATIC,
                "net/minecraftforge/common/MinecraftForge",
                "EVENT_BUS",
                "Lnet/minecraftforge/eventbus/api/IEventBus;"
        );
        probe.visitInsn(Opcodes.POP);
        probe.visitInsn(Opcodes.RETURN);
        probe.visitMaxs(1, 0);
        probe.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing test system property " + name);
        }
        return value;
    }
}
