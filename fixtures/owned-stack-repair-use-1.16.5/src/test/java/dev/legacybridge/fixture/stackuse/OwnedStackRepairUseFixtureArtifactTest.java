package dev.legacybridge.fixture.stackuse;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Archive metadata checks only: no fixture/native class is ever loaded by this suite. */
final class OwnedStackRepairUseFixtureArtifactTest {
    private static final String PREFIX="assets/legacybridge_stack_use/";

    @Test void metadataPinsOriginalLoaderAndIdentity() throws IOException {
        try(JarFile jar=input()) {
            String metadata=text(jar,"META-INF/mods.toml");
            assertTrue(metadata.contains("modId=\"legacybridge_stack_use\""));
            assertTrue(metadata.contains("version=\"1.0.0\""));
            assertTrue(metadata.contains("versionRange=\"[36.2.42,37)\""));
            assertTrue(metadata.contains("versionRange=\"[1.16.5,1.17)\""));
            assertTrue(text(jar,"pack.mcmeta").contains("\"pack_format\": 6"));
            assertEquals("1.0.0",jar.getManifest().getMainAttributes().getValue("Implementation-Version"));
            assertEquals("owned-stack-repair-use-v1",jar.getManifest().getMainAttributes().getValue("LegacyBridge-Fixture"));
            assertFalse(metadata.contains("legacybridge_runtime"));
        }
    }

    @Test void exactlyTwoOwnedJava8ClassesSixMethodsAndNoBundledDependencies() throws IOException {
        try(JarFile jar=input()) {
            Enumeration<JarEntry> entries=jar.entries();int count=0;
            while(entries.hasMoreElements()) {
                JarEntry entry=entries.nextElement();String name=entry.getName();
                assertFalse(name.startsWith("dev/legacybridge/validation/"),name);
                assertFalse(name.startsWith("META-INF/services/"),name);
                assertFalse(name.startsWith("com/google/"),name);
                assertFalse(name.endsWith(".jar"),name);
                if(!name.endsWith(".class"))continue;
                assertTrue(name.equals("dev/legacybridge/fixture/stackuse/OwnedStackRepairUseContent.class")
                    ||name.equals("dev/legacybridge/fixture/stackuse/StackRepairUseItem.class"),name);
                try(DataInputStream bytes=new DataInputStream(jar.getInputStream(entry))) {
                    assertEquals(0xcafebabe,bytes.readInt());assertEquals(0,bytes.readUnsignedShort());
                    assertEquals(52,bytes.readUnsignedShort());
                    assertEquals(3,methodCount(bytes),
                        "Six methods: registration, own ctor, helper and native use callback");
                }
                count++;
            }
            assertEquals(2,count);
        }
    }

    @Test void oneGeneratedModelOnlyReferencesVanillaResources() throws IOException {
        try(JarFile jar=input()) {
            String model=text(jar,PREFIX+"models/item/bridge_stack_use_item.json");
            assertTrue(model.contains("minecraft:item/generated"));assertTrue(model.contains("minecraft:item/iron_ingot"));
            Enumeration<JarEntry> entries=jar.entries();int models=0;
            while(entries.hasMoreElements()) {
                String name=entries.nextElement().getName();
                assertFalse(name.startsWith("assets/minecraft/"),name);assertFalse(name.startsWith("data/minecraft/"),name);
                assertFalse(name.endsWith(".png"),name);assertFalse(name.contains("/blockstates/"),name);
                assertFalse(name.contains("/loot_tables/"),name);
                if(name.startsWith(PREFIX+"models/")&&name.endsWith(".json"))models++;
            }
            assertEquals(1,models);
        }
    }

    @Test void oneAuthoredRecipeAndBothTranslationsArePresent() throws IOException {
        try(JarFile jar=input()) {
            String recipe=text(jar,"data/legacybridge_stack_use/recipes/bridge_stack_use_item.json");
            assertTrue(recipe.contains("minecraft:crafting_shapeless"));assertTrue(recipe.contains("minecraft:iron_ingot"));
            assertTrue(recipe.contains("legacybridge_stack_use:bridge_stack_use_item"));
            assertTrue(text(jar,PREFIX+"lang/en_us.json").contains("Bridge Stack Repair Use Item"));
            assertTrue(text(jar,PREFIX+"lang/pt_br.json").contains("Item de reparo por uso da ponte"));
            Enumeration<JarEntry> entries=jar.entries();int recipes=0;
            while(entries.hasMoreElements()) {
                String name=entries.nextElement().getName();
                if(name.startsWith("data/legacybridge_stack_use/recipes/")&&name.endsWith(".json"))recipes++;
            }
            assertEquals(1,recipes);
        }
    }

    private static JarFile input() throws IOException {
        String path=System.getProperty("fixture.jar");assertNotNull(path,"Standalone build supplies reobfuscated archive");return new JarFile(path);
    }
    /** Cursor immediately follows major version; neither defineClass nor reflection is used. */
    private static int methodCount(DataInputStream bytes) throws IOException {
        int entries=bytes.readUnsignedShort();
        for(int index=1;index<entries;index++) {
            int tag=bytes.readUnsignedByte();
            switch(tag) {
                case 1: skip(bytes,bytes.readUnsignedShort());break;
                case 3:case 4:case 9:case 10:case 11:case 12:case 18:skip(bytes,4);break;
                case 5:case 6:skip(bytes,8);index++;break;
                case 7:case 8:case 16:skip(bytes,2);break;
                case 15:skip(bytes,3);break;
                default:throw new IOException("Unexpected Java-8 constant-pool tag "+tag);
            }
        }
        skip(bytes,6);skip(bytes,2*bytes.readUnsignedShort());int fields=bytes.readUnsignedShort();
        for(int field=0;field<fields;field++) {
            skip(bytes,6);int attributes=bytes.readUnsignedShort();
            for(int attribute=0;attribute<attributes;attribute++) {
                bytes.readUnsignedShort();int length=bytes.readInt();
                assertTrue(length>=0&&length<=65_536,"Bounded authored classfile attribute");skip(bytes,length);
            }
        }
        return bytes.readUnsignedShort();
    }
    private static void skip(DataInputStream bytes,int count) throws IOException {
        while(count>0){int skipped=bytes.skipBytes(count);assertTrue(skipped>0,"Truncated classfile");count-=skipped;}
    }
    private static String text(JarFile jar,String name) throws IOException {
        JarEntry entry=jar.getJarEntry(name);assertNotNull(entry,name);
        assertTrue(entry.getSize()>=0&&entry.getSize()<=16_384,"Bounded authored resource: "+name);
        try(InputStream input=jar.getInputStream(entry);ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[1024];int count;
            while((count=input.read(buffer))!=-1){assertTrue(bytes.size()+count<=16_384,"Bounded resource read");bytes.write(buffer,0,count);}
            return new String(bytes.toByteArray(),StandardCharsets.UTF_8);
        }
    }
}
