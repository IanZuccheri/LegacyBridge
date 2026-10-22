package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.conversion.logic.VerifiedLogicMappings;
import dev.legacybridge.converter.mapping.source.MappingInputs;
import dev.legacybridge.converter.mapping.source.MappingInputsLoader;
import dev.legacybridge.converter.transformation.LegacyReferenceScanner;
import dev.legacybridge.converter.transformation.MappingResolver;
import dev.legacybridge.converter.transformation.TransformationLog;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real reobfuscated fixture and pinned declaration data; no original class is loaded or run. */
@EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment",matches="true")
class PlainBlockLoweringIntegrationTest {
    private static final String ENTRY="dev/legacybridge/fixture/plainblock/PlainBlockContent";
    private static final String MATERIAL="net/minecraft/block/material/Material";
    private static final int PROPERTIES_LINE=777;
    private static MappingInputs inputs;

    @AfterAll static void releaseInputs(){inputs=null;}

    @ParameterizedTest @ValueSource(booleans={false,true})
    void actualLoweringPreservesSourceTreesAndDebugPosition(boolean multilinePropertiesExpression)throws Exception {
        var original=readFixture();
        if(multilinePropertiesExpression) {
            var factory=method(original,"createBlock");
            var material=Arrays.stream(factory.instructions.toArray())
                .filter(i->i instanceof FieldInsnNode f&&f.owner.equals(MATERIAL)).findFirst().orElseThrow();
            var label=new LabelNode();var debug=new InsnList();
            debug.add(label);debug.add(new LineNumberNode(PROPERTIES_LINE,label));
            factory.instructions.insertBefore(material,debug);
        }
        var scan=new LegacyReferenceScanner().scan(Map.of(ENTRY+".class",bytes(original)));
        var sourceNodes=scan.classNodes();var source=sourceNodes.get(ENTRY);
        byte[] before=bytes(source);
        var mappings=VerifiedLogicMappings.compilePlainBlock(sourceNodes,scan,inputs());
        var resolver=new MappingResolver(mappings.database(),mappings.adapterBindings(),sourceNodes.keySet());
        var target=PlainBlockLowering.remap(source,resolver,mappings.plainBlockRecipe(),new TransformationLog());

        assertArrayEquals(before,bytes(source),"Lowering may not alter caller-owned source trees or shared bootstrap arrays");
        assertEquals(source.sourceFile,target.sourceFile);
        assertEquals(source.sourceDebug,target.sourceDebug);
        assertEquals(PlainBlockContract.inspect(sourceNodes),PlainBlockContract.inspect(Map.of(target.name,target)));
        assertEquals(source.methods.size(),target.methods.size());
        for(int index=0;index<source.methods.size();index++) {
            var from=source.methods.get(index);var to=target.methods.get(index);
            assertEquals(lines(from),lines(to),"Line entries retained in order: "+from.name);
            assertEquals(labels(from),labels(to),"Debug/control-flow labels retained: "+from.name);
        }
        var targetFactory=method(target,"createBlock");
        assertFalse(Arrays.stream(targetFactory.instructions.toArray())
            .anyMatch(i->i instanceof FieldInsnNode f&&f.owner.equals(MATERIAL)));
        if(multilinePropertiesExpression) {
            var sourceMarker=marker(method(source,"createBlock"));var targetMarker=marker(targetFactory);
            assertTrue(mappings.plainBlockRecipe().sourceStone().matches(nextCode(sourceMarker)));
            assertTrue(mappings.plainBlockRecipe().loweringOperations().get(0).matches(nextCode(targetMarker)),
                "Target expansion must begin after the original Material expression's debug marker, not before it");
        }
    }

    private static MappingInputs inputs()throws Exception {
        if(inputs==null)inputs=new MappingInputsLoader().load(Path.of(System.getProperty("legacybridge.logicMappingProfile")));
        return inputs;
    }
    private static ClassNode readFixture()throws Exception {
        var path=Path.of(System.getProperty("legacybridge.fixtureRoot"),"plain-block-1.16.5/build/libs/legacybridge-plain-block-1.16.5-1.0.0.jar");
        try(var zip=new ZipFile(path.toFile());var stream=zip.getInputStream(Objects.requireNonNull(zip.getEntry(ENTRY+".class")))) {
            var node=new ClassNode();new ClassReader(stream.readAllBytes()).accept(node,0);return node;
        }
    }
    private static byte[] bytes(ClassNode node) {
        var writer=new ClassWriter(0);node.accept(writer);return writer.toByteArray();
    }
    private static MethodNode method(ClassNode node,String name) {
        return node.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();
    }
    private static List<Integer> lines(MethodNode method) {
        return Arrays.stream(method.instructions.toArray()).filter(LineNumberNode.class::isInstance)
            .map(LineNumberNode.class::cast).map(n->n.line).toList();
    }
    private static long labels(MethodNode method) {
        return Arrays.stream(method.instructions.toArray()).filter(LabelNode.class::isInstance).count();
    }
    private static LineNumberNode marker(MethodNode method) {
        return Arrays.stream(method.instructions.toArray()).filter(LineNumberNode.class::isInstance)
            .map(LineNumberNode.class::cast).filter(n->n.line==PROPERTIES_LINE).findFirst().orElseThrow();
    }
    private static AbstractInsnNode nextCode(AbstractInsnNode instruction) {
        do{instruction=instruction.getNext();}while(instruction!=null&&instruction.getOpcode()<0);
        return instruction;
    }
}
