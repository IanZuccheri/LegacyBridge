package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.conversion.logic.PlainBlockApiRecipe;
import dev.legacybridge.converter.mapping.MappingConfidence;
import dev.legacybridge.converter.transformation.*;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import java.util.*;
import org.objectweb.asm.tree.*;

/** The only body-changing region of the plain-block candidate. Never modifies input trees. */
final class PlainBlockLowering {
    private PlainBlockLowering() { }
    static ClassNode remap(ClassNode source, MappingResolver resolver, PlainBlockApiRecipe recipe, TransformationLog log) {
        if(recipe==null || !AdapterCatalog.PLAIN_BLOCK_PROPERTIES.equals(recipe.adapterId()))throw new TransformationException("PLAIN_BLOCK_RECIPE_REQUIRED");
        PlainBlockContract.inspect(Map.of(source.name,source));
        var mapped = remapEntry(source,resolver,recipe,log);
        if(!PlainBlockContract.inspect(Map.of(source.name,source)).equals(PlainBlockContract.inspect(Map.of(mapped.name,mapped))))
            throw new TransformationException("PLAIN_BLOCK_LOWERING_CONTRACT_DRIFT");
        return mapped;
    }

    static Map<String,ClassNode> remapHorizontal(Map<String,ClassNode> originals,MappingResolver resolver,
                                                PlainBlockApiRecipe recipe,TransformationLog log) {
        var proof=HorizontalBlockContract.inspect(originals);
        if(recipe==null || !AdapterCatalog.PLAIN_BLOCK_PROPERTIES.equals(recipe.adapterId()))throw new TransformationException("PLAIN_BLOCK_RECIPE_REQUIRED");
        var result=new TreeMap<String,ClassNode>();
        for(var source:originals.values()) {
            ClassNode mapped;
            if(source.name.equals(proof.entry())) mapped=remapEntry(source,resolver,recipe,log);
            else { mapped=LoggedClassRemapper.remap(source,resolver,log); LogicBodyPreservation.verify(source,mapped); }
            if(result.putIfAbsent(mapped.name,mapped)!=null)throw new TransformationException("HORIZONTAL_CLASS_COLLISION");
        }
        if(!proof.equals(HorizontalBlockContract.inspect(result)))throw new TransformationException("HORIZONTAL_BLOCK_LOWERING_DRIFT");
        return result;
    }

    static Map<String,ClassNode> remapBooleanToggleBlock(Map<String,ClassNode> originals,MappingResolver resolver,
                                                       PlainBlockApiRecipe recipe,TransformationLog log) {
        var proof=BooleanToggleBlockContract.inspect(originals);
        if(recipe==null || !AdapterCatalog.PLAIN_BLOCK_PROPERTIES.equals(recipe.adapterId()))
            throw new TransformationException("BOOLEAN_TOGGLE_PROPERTIES_RECIPE_REQUIRED");
        var result=new TreeMap<String,ClassNode>();
        for(var source:originals.values()) {
            ClassNode mapped;
            if(source.name.equals(proof.entry())) mapped=remapEntry(source,resolver,recipe,log);
            else { mapped=LoggedClassRemapper.remap(source,resolver,log); LogicBodyPreservation.verify(source,mapped); }
            if(result.putIfAbsent(mapped.name,mapped)!=null)throw new TransformationException("BOOLEAN_TOGGLE_CLASS_COLLISION");
        }
        if(!proof.equals(BooleanToggleBlockContract.inspect(result)))throw new TransformationException("BOOLEAN_TOGGLE_LOWERING_DRIFT");
        return result;
    }

    static Map<String,ClassNode> remapStaticShapeBlock(Map<String,ClassNode> originals,MappingResolver resolver,
                                                       PlainBlockApiRecipe recipe,TransformationLog log) {
        var proof=StaticShapeBlockContract.inspect(originals);
        if(recipe==null || !AdapterCatalog.STATIC_SHAPE_PROPERTIES.equals(recipe.adapterId()))
            throw new TransformationException("STATIC_SHAPE_PROPERTIES_RECIPE_REQUIRED");
        var result=new TreeMap<String,ClassNode>();
        for(var source:originals.values()) {
            ClassNode mapped;
            if(source.name.equals(proof.entry())) mapped=remapEntry(source,resolver,recipe,log);
            else { mapped=LoggedClassRemapper.remap(source,resolver,log); LogicBodyPreservation.verify(source,mapped); }
            if(result.putIfAbsent(mapped.name,mapped)!=null)throw new TransformationException("STATIC_SHAPE_CLASS_COLLISION");
        }
        if(!proof.equals(StaticShapeBlockContract.inspect(result)))throw new TransformationException("STATIC_SHAPE_BLOCK_LOWERING_DRIFT");
        return result;
    }

    private static ClassNode remapEntry(ClassNode source,MappingResolver resolver,PlainBlockApiRecipe recipe,TransformationLog log) {
        var projection=new ClassNode();source.accept(projection);
        MethodNode factory=null; AbstractInsnNode anchor=null; int hits=0;
        for(var method:projection.methods) {
            var code=ClientConfigContract.code(method);
            for(int i=0;i+1<code.size();i++) if(recipe.sourceStone().matches(code.get(i))) {
                if(!recipe.sourceFactory().matches(code.get(i+1)) || i==0)
                    throw new TransformationException("PLAIN_BLOCK_MATERIAL_FLOW");
                factory=method;anchor=code.get(i).getPrevious();
                method.instructions.remove(code.get(i));method.instructions.remove(code.get(i+1));hits++;
            }
        }
        if(hits!=1)throw new TransformationException("PLAIN_BLOCK_MATERIAL_OCCURRENCE");
        int methodIndex=projection.methods.indexOf(factory), anchorIndex=factory.instructions.indexOf(anchor);
        var mapped=LoggedClassRemapper.remap(projection,resolver,log);
        // Compare all retained instructions/debug/state before injecting only the checked native recipe.
        LogicBodyPreservation.verify(projection,mapped);
        var targetMethod=mapped.methods.get(methodIndex);
        var targetAnchor=targetMethod.instructions.get(anchorIndex);
        var expansion=new InsnList();
        for(var op:recipe.loweringOperations()) {
            expansion.add(op.opcode()==org.objectweb.asm.Opcodes.GETSTATIC
                ? new FieldInsnNode(op.opcode(),op.owner(),op.name(),op.descriptor())
                : new MethodInsnNode(op.opcode(),op.owner(),op.name(),op.descriptor(),false));
        }
        targetMethod.instructions.insert(targetAnchor,expansion);
        // Whole source/target proof is independently compared by each finite caller.
        String adapter=recipe.adapterId();
        log.add(TransformationLog.Kind.ADAPTER_CALL,source.name+"."+factory.name+factory.desc,
            recipe.sourceStone()+"; "+recipe.sourceFactory(),recipe.loweringOperations().toString(),
            adapter.equals(AdapterCatalog.STATIC_SHAPE_PROPERTIES) ? "static-shape-block.exact-stone-pair.v1" : "plain-block.exact-stone-pair.v1",MappingConfidence.VERIFIED,
            AdapterCatalog.require(adapter).transformation(),adapter);
        return mapped;
    }
}
