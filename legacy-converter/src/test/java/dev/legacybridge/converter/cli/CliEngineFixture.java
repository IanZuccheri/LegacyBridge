package dev.legacybridge.converter.cli;

import dev.legacybridge.converter.conversion.extract.SyntheticContentFixture;
import dev.legacybridge.converter.testutil.TestJarBuilder;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/** Local declaration-only test data; never defines or executes legacy/game classes. */
final class CliEngineFixture {
    private CliEngineFixture() { }

    static Path profile(Path directory) throws Exception {
        byte[] fixture = SyntheticContentFixture.bytecode("valid");
        var api = SyntheticContentFixture.mappingInputs(fixture).legacyApi();
        Map<String, byte[]> entries = new TreeMap<>();
        for (var declaration : api.classes().values()) {
            var writer = new ClassWriter(0);
            writer.visit(Opcodes.V1_8, declaration.access(), declaration.name(), null,
                    declaration.superName(), declaration.interfaces().toArray(String[]::new));
            for (var method : declaration.methods()) {
                writer.visitMethod(method.access(), method.name(), method.descriptor(), null, null).visitEnd();
            }
            for (var field : declaration.fields()) {
                writer.visitField(field.access(), field.name(), field.descriptor(), null, null).visitEnd();
            }
            writer.visitEnd();
            entries.put(declaration.name() + ".class", writer.toByteArray());
        }
        TestJarBuilder.write(directory.resolve("api.jar"), entries);
        Files.writeString(directory.resolve("official.txt"), "example.Placeholder -> placeholder:\n");
        Files.writeString(directory.resolve("mapping.tsrg"), "placeholder example/Placeholder\n");
        Path profile = directory.resolve("mappings.properties");
        Files.writeString(profile, """
                legacy.minecraft=1.16.5
                target.minecraft=1.20.1
                legacy.forge=36.2.42
                target.forge=47.4.23
                legacy.clientMappings=official.txt
                legacy.serverMappings=official.txt
                target.clientMappings=official.txt
                target.serverMappings=official.txt
                legacy.srg=mapping.tsrg
                target.srg=mapping.tsrg
                legacy.apiJars=api.jar
                target.apiJars=api.jar
                """);
        return profile;
    }

    static Path input(Path path, String shape) throws Exception {
        Map<String, byte[]> source = new TreeMap<>();
        source.put("fixture/SimpleMod.class", SyntheticContentFixture.bytecode(shape));
        text(source, "META-INF/mods.toml", """
                modLoader="javafml"
                loaderVersion="[36,)"
                license="MIT"
                [[mods]]
                modId="fixturemod"
                version="1.2.3"
                displayName="Inert CLI fixture"
                description="Declaration-only test fixture"
                [[dependencies.fixturemod]]
                modId="forge"
                mandatory=true
                versionRange="[36.2.42,37)"
                ordering="NONE"
                side="BOTH"
                [[dependencies.fixturemod]]
                modId="minecraft"
                mandatory=true
                versionRange="[1.16.5,1.17)"
                ordering="NONE"
                side="BOTH"
                """);
        text(source, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nImplementation-Version: 1.2.3\r\n\r\n");
        text(source, "pack.mcmeta", "{\"pack\":{\"pack_format\":6,\"description\":\"Inert CLI fixture\"}}");
        text(source, "assets/fixturemod/models/item/ingot.json", "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/iron_ingot\"}}");
        text(source, "assets/fixturemod/models/item/block.json", "{\"parent\":\"fixturemod:block/block\"}");
        text(source, "assets/fixturemod/models/block/block.json", "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"minecraft:block/stone\"}}");
        text(source, "assets/fixturemod/blockstates/block.json", "{\"variants\":{\"\":{\"model\":\"fixturemod:block/block\"}}}");
        text(source, "assets/fixturemod/lang/en_us.json", "{\"item.fixturemod.ingot\":\"Fixture Ingot\",\"block.fixturemod.block\":\"Fixture Block\"}");
        text(source, "assets/fixturemod/sounds.json", "{\"ping\":{\"sounds\":[{\"name\":\"minecraft:block.note_block.pling\",\"type\":\"event\"}]}}");
        text(source, "data/fixturemod/recipes/ingot.json", "{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\"II\",\"II\"],\"key\":{\"I\":{\"item\":\"minecraft:iron_ingot\"}},\"result\":{\"item\":\"fixturemod:ingot\",\"count\":1}}");
        return TestJarBuilder.write(path, source);
    }

    private static void text(Map<String, byte[]> entries, String path, String value) {
        entries.put(path, value.getBytes(StandardCharsets.UTF_8));
    }
}
