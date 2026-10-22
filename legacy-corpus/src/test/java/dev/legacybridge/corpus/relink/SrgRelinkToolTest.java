package dev.legacybridge.corpus.relink;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.legacybridge.corpus.relink.RelinkFiles.*;

class SrgRelinkToolTest {
    @TempDir Path temp;
    @ParameterizedTest @ValueSource(strings={"../outside", "/absolute", "a\\b", "a:b", "a//b", "a/./b", "CON.txt", "a/NUL", "name.", "name ", "a\nfile"})
    void rejectsUnsafeArchivePaths(String name) { assertThrows(IOException.class, () -> safeName(name)); }
    @Test void archiveChecksCrcCentralDirectoryTruncationAndBounds() throws Exception {
        byte[] good=zip(Map.of("a", new byte[]{1,2,3})); assertArrayEquals(new byte[]{1,2,3}, unzip(good, 1, 3).get("a"));
        assertThrows(IOException.class, () -> unzip(good, 0, 3)); assertThrows(IOException.class, () -> unzip(good, 1, 2));
        assertThrows(IOException.class, () -> unzip(Arrays.copyOf(good, good.length-1), 1, 3));
        byte[] mismatch=good.clone(); for(int i=0;i<mismatch.length-46;i++) if(mismatch[i]==0x50 && mismatch[i+1]==0x4b && mismatch[i+2]==1 && mismatch[i+3]==2) { mismatch[i+16]^=1; break; }
        assertThrows(IOException.class, () -> unzip(mismatch, 1, 3));
        byte[] counts=good.clone(); counts[counts.length-12]=0; assertThrows(IOException.class, () -> unzip(counts, 1, 3));
    }
    @Test void rejectsCaseAliasesAndInvalidFingerprints() throws Exception {
        assertThrows(IOException.class, () -> unzip(zip(Map.of("a", new byte[0], "A", new byte[0])), 2, 1));
        assertThrows(IOException.class, () -> pin(new byte[]{1}, "bad")); assertThrows(IOException.class, () -> pin(new byte[]{1}, "0".repeat(64)));
    }
    @Test void failureCleansOnlyOwnedStageAndNeverOverwritesExistingOutput() throws Exception {
        Path existing=Files.writeString(temp.resolve("keep.txt"), "keep"); Path output=temp.resolve("new");
        assertThrows(IOException.class, () -> publishDirectory(output, stage -> { write(stage,"nested/a",new byte[]{1}); throw new IOException("injected write failure"); }));
        assertFalse(Files.exists(output)); assertEquals("keep",Files.readString(existing));
        try(var entries=Files.list(temp)){assertEquals(1,entries.count());}
        Files.createDirectory(output);
        assertThrows(IOException.class, () -> publishDirectory(output, stage -> fail("must not run")));
        assertTrue(Files.isDirectory(output));
    }
    @Test void publicationRechecksDestinationAndPreservesRacingForeignFile() throws Exception {
        Path output=temp.resolve("copy");
        assertThrows(IOException.class, () -> publishDirectory(output, stage -> { write(stage,"candidate",new byte[]{1}); Files.writeString(output,"foreign"); }));
        assertEquals("foreign", Files.readString(output));
        try(var paths=Files.list(temp)){assertEquals(1,paths.count());}
    }
    @Test void refusesCorruptOrUnreviewedProductBeforeCreatingKit() throws Exception {
        Path input=Files.write(temp.resolve("bad.jar"),new byte[]{1}); Path output=temp.resolve("kit");
        assertThrows(IOException.class, () -> SrgRelinkTool.prepare(input,hash(new byte[]{1}),output)); assertFalse(Files.exists(output));
        assertThrows(IOException.class, () -> SrgRelinkTool.prepare(input,"0".repeat(64),output)); assertFalse(Files.exists(output));
    }
    @Test void entryPayloadIsNotExecutedDuringArchiveOrAsmValidation() throws Exception {
        byte[] type=type(true, false); var entries=Map.of("net/minecraftforge/srgutils/Example.class",type);
        assertEquals(false,SrgRelinkTool.verifyAbi(entries,entries).get("classesLoaded"));
        assertEquals(false,SrgRelinkTool.verifyAbi(entries,entries).get("fullExternalLinkage"));
        var surface=SrgRelinkTool.verifyAbi(entries,entries);
        assertEquals(new ArrayList<>(new TreeSet<>(surface.keySet())),new ArrayList<>(surface.keySet()),"Source provenance serialization must be stable across fresh JVMs");
    }
    @Test void staticAbiRejectsRemovedMemberChangedDescriptorAndForeignOwnership() throws Exception {
        var original=Map.of("net/minecraftforge/srgutils/Example.class",type(true,false));
        assertThrows(IOException.class, () -> SrgRelinkTool.verifyAbi(original,Map.of("net/minecraftforge/srgutils/Example.class",type(false,false))));
        assertThrows(IOException.class, () -> SrgRelinkTool.verifyAbi(original,Map.of("net/minecraftforge/srgutils/Example.class",type(true,true))));
        assertThrows(IOException.class, () -> SrgRelinkTool.verifyAbi(original,Map.of("java/Example.class",type(true,false))));
    }
    @Test void incompleteKitAndInvalidCommandDoNotPublish() throws Exception {
        assertThrows(IOException.class, () -> SrgRelinkTool.build(temp,temp.resolve("annotations.jar"),temp.resolve("out")));
        assertFalse(Files.exists(temp.resolve("out")));
        assertThrows(IllegalArgumentException.class, () -> SrgRelinkTool.main(new String[]{"build"}));
        assertThrows(IllegalArgumentException.class, () -> SrgRelinkTool.main(new String[]{"download"}));
    }
    private static byte[] type(boolean member, boolean changed) {
        var writer=new ClassWriter(0); writer.visit(Opcodes.V1_8,Opcodes.ACC_PUBLIC|Opcodes.ACC_SUPER,"net/minecraftforge/srgutils/Example",null,"java/lang/Object",null);
        // Would throw if initialized; neither archive nor ABI verification may define this class.
        var init=writer.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null); init.visitCode(); init.visitInsn(Opcodes.ACONST_NULL); init.visitInsn(Opcodes.ATHROW); init.visitMaxs(1,0); init.visitEnd();
        if(member)writer.visitField(Opcodes.ACC_PUBLIC,"value",changed?"J":"I",null,null).visitEnd();
        writer.visitEnd();return writer.toByteArray();
    }
}
