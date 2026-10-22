package dev.legacybridge.converter.transformation.linkage;

import dev.legacybridge.converter.mapping.source.ApiIndex;
import dev.legacybridge.converter.transformation.TransformationException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/** Inert owned class trees; no stub Minecraft APIs and no class definition or execution. */
class OwnedInterfaceDefaultsTest {
    private static final String INTERFACE="fixture/api/Scoring", HELPER="fixture/impl/Counter", CALLER="fixture/Use";
    @Test void derivesSelectionAndOnlySeparateVerifierAdmitsExactDefault() {
        var nodes=nodes(); var proof=OwnedInterfaceDefaults.inspect(nodes);
        assertEquals(3,proof.sites().size());
        for(var site:proof.sites()) assertEquals(site.opcode()==INVOKESPECIAL?INTERFACE:HELPER,site.selected().owner());
        assertEquals(false,proof.toTree().get("classesExecuted")); assertEquals(false,proof.toTree().get("publicationAllowed"));
        assertEquals("NON_NULL_OWNED_HELPER",proof.toTree().get("receiverAssumption"));
        assertThrows(UnsupportedOperationException.class,()->proof.sites().clear());
        var mutable=new ArrayList<>(proof.sites()); var copy=new OwnedInterfaceDefaults.Proof(proof.contract(),proof.helper(),proof.defaultMethod(),proof.abstractMethod(),mutable);
        mutable.clear(); assertEquals(proof,copy);
        var verifier=new OfflineLinkageVerifier();
        assertThrows(TransformationException.class,()->verifier.verify(bytes(nodes),ApiIndex.empty()));
        assertEquals("PASSED_SCOPED",verifier.verifyOwnedInterfaceDefaults(bytes(nodes),ApiIndex.empty()).status());
        assertThrows(TransformationException.class,()->verifier.verifyOwnedConstructors(bytes(nodes),ApiIndex.empty(),HELPER));
    }
    @Test void proofIgnoresDebugNodesButBytecodeGateStillChecksBodies() {
        var nodes=nodes(); var proof=OwnedInterfaceDefaults.inspect(nodes);
        for(var node:nodes.values()) for(var method:node.methods) if((method.access&ACC_ABSTRACT)==0){
            var label=new LabelNode();method.instructions.insert(label);method.instructions.insert(label,new LineNumberNode(7,label));
        }
        assertEquals(proof,OwnedInterfaceDefaults.inspect(nodes));
        method(nodes.get(HELPER),"bias").instructions.clear();
        method(nodes.get(HELPER),"bias").instructions.add(new InsnNode(RETURN));
        assertNotNull(OwnedInterfaceDefaults.inspect(nodes));
        assertThrows(TransformationException.class,()->new OfflineLinkageVerifier().verifyOwnedInterfaceDefaults(bytes(nodes),ApiIndex.empty()));
    }
    @Test void specialReceiverMustBeProvenThisNotMerelySameType() {
        var nodes=nodes(); var foreign=new MethodNode(ACC_PUBLIC,"foreign","(L"+HELPER+";I)I",null,null);
        foreign.instructions.add(new VarInsnNode(ALOAD,1));foreign.instructions.add(new VarInsnNode(ILOAD,2));
        foreign.instructions.add(new MethodInsnNode(INVOKESPECIAL,INTERFACE,"score","(I)I",true));foreign.instructions.add(new InsnNode(IRETURN));
        foreign.maxStack=2;foreign.maxLocals=3;nodes.get(HELPER).methods.add(foreign);
        assertNotNull(OwnedInterfaceDefaults.inspect(nodes));
        var failure=assertThrows(TransformationException.class,()->new OfflineLinkageVerifier().verifyOwnedInterfaceDefaults(bytes(nodes),ApiIndex.empty()));
        assertTrue(failure.getMessage().contains("INTERFACE_SUPER_RECEIVER_UNPROVEN"),failure.toString());
    }
    @ParameterizedTest @ValueSource(strings={"SUPERINTERFACE","STATE","INITIALIZER","DEFAULT_ABSTRACT","DEFAULT_PRIVATE","DEFAULT_STATIC",
            "MISSING_OVERRIDE","ABSTRACT_OVERRIDE","PRIVATE_OVERRIDE","OPEN_HELPER","HELPER_SUPER","HELPER_CTOR","NO_HOOK","NO_VIRTUAL","NO_SUPER",
            "WRONG_OPCODE","OWNER_KIND","INDIRECT_SUPER","EXTRA_INTERFACE","CODE_BOUND","METHOD_BOUND"})
    void unprovedShapeCannotRequestDefaultPermission(String defect) {
        var nodes=nodes();var contract=nodes.get(INTERFACE);var helper=nodes.get(HELPER);
        var defaultMethod=method(contract,"score");var override=method(helper,"score");
        var special=(MethodInsnNode)override.instructions.get(2);
        switch(defect){
            case "SUPERINTERFACE"->contract.interfaces.add("unknown/Parent");
            case "STATE"->contract.fields.add(new FieldNode(ACC_PUBLIC|ACC_STATIC|ACC_FINAL,"state","I",null,1));
            case "INITIALIZER"->helper.methods.add(new MethodNode(ACC_STATIC,"<clinit>","()V",null,null));
            case "DEFAULT_ABSTRACT"->defaultMethod.access|=ACC_ABSTRACT;
            case "DEFAULT_PRIVATE"->defaultMethod.access=ACC_PRIVATE;
            case "DEFAULT_STATIC"->defaultMethod.access|=ACC_STATIC;
            case "MISSING_OVERRIDE"->helper.methods.remove(override);
            case "ABSTRACT_OVERRIDE"->override.access|=ACC_ABSTRACT;
            case "PRIVATE_OVERRIDE"->override.access=ACC_PRIVATE;
            case "OPEN_HELPER"->helper.access&=~ACC_FINAL;
            case "HELPER_SUPER"->helper.superName="unknown/Base";
            case "HELPER_CTOR"->method(helper,"<init>").access=ACC_PRIVATE;
            case "NO_HOOK"->defaultMethod.instructions.remove(defaultMethod.instructions.get(4));
            case "NO_VIRTUAL"->nodes.remove(CALLER);
            case "NO_SUPER"->override.instructions.remove(special);
            case "WRONG_OPCODE"->special.setOpcode(INVOKEVIRTUAL);
            case "OWNER_KIND"->special.itf=false;
            case "INDIRECT_SUPER"->{method(nodes.get(CALLER),"use").instructions.insertBefore(method(nodes.get(CALLER),"use").instructions.getLast(),new MethodInsnNode(INVOKESPECIAL,INTERFACE,"score","(I)I",true));}
            case "EXTRA_INTERFACE"->{var extra=node("fixture/Other","java/lang/Object",ACC_PUBLIC|ACC_INTERFACE|ACC_ABSTRACT);nodes.put(extra.name,extra);}
            case "CODE_BOUND"->{for(int i=0;i<4097;i++)override.instructions.add(new InsnNode(NOP));}
            case "METHOD_BOUND"->{for(int i=0;i<129;i++)helper.methods.add(new MethodNode(ACC_PUBLIC,"extra"+i,"()V",null,null));}
            default->throw new AssertionError(defect);
        }
        assertThrows(TransformationException.class,()->OwnedInterfaceDefaults.inspect(nodes),defect);
    }
    private static Map<String,ClassNode> nodes(){
        var contract=node(INTERFACE,"java/lang/Object",ACC_PUBLIC|ACC_INTERFACE|ACC_ABSTRACT);
        contract.methods.add(new MethodNode(ACC_PUBLIC|ACC_ABSTRACT,"bias","()I",null,null));
        var score=new MethodNode(ACC_PUBLIC,"score","(I)I",null,null);
        score.instructions.add(new VarInsnNode(ILOAD,1));score.instructions.add(new InsnNode(ICONST_3));score.instructions.add(new InsnNode(IMUL));
        score.instructions.add(new VarInsnNode(ALOAD,0));score.instructions.add(new MethodInsnNode(INVOKEINTERFACE,INTERFACE,"bias","()I",true));
        score.instructions.add(new InsnNode(IADD));score.instructions.add(new InsnNode(IRETURN));score.maxStack=2;score.maxLocals=2;contract.methods.add(score);
        var helper=node(HELPER,"java/lang/Object",ACC_PUBLIC|ACC_FINAL|ACC_SUPER);helper.interfaces.add(INTERFACE);
        var ctor=new MethodNode(ACC_PUBLIC,"<init>","()V",null,null);ctor.instructions.add(new VarInsnNode(ALOAD,0));
        ctor.instructions.add(new MethodInsnNode(INVOKESPECIAL,"java/lang/Object","<init>","()V",false));ctor.instructions.add(new InsnNode(RETURN));
        ctor.maxStack=1;ctor.maxLocals=1;helper.methods.add(ctor);
        var bias=new MethodNode(ACC_PUBLIC,"bias","()I",null,null);bias.instructions.add(new InsnNode(ICONST_1));bias.instructions.add(new InsnNode(IRETURN));bias.maxStack=1;bias.maxLocals=1;helper.methods.add(bias);
        var override=new MethodNode(ACC_PUBLIC,"score","(I)I",null,null);override.instructions.add(new VarInsnNode(ALOAD,0));override.instructions.add(new VarInsnNode(ILOAD,1));
        override.instructions.add(new MethodInsnNode(INVOKESPECIAL,INTERFACE,"score","(I)I",true));override.instructions.add(new InsnNode(ICONST_2));override.instructions.add(new InsnNode(IADD));override.instructions.add(new InsnNode(IRETURN));override.maxStack=2;override.maxLocals=2;helper.methods.add(override);
        var caller=node(CALLER,"java/lang/Object",ACC_PUBLIC|ACC_SUPER);var use=new MethodNode(ACC_PUBLIC|ACC_STATIC,"use","(L"+INTERFACE+";I)I",null,null);
        use.instructions.add(new VarInsnNode(ALOAD,0));use.instructions.add(new VarInsnNode(ILOAD,1));use.instructions.add(new MethodInsnNode(INVOKEINTERFACE,INTERFACE,"score","(I)I",true));use.instructions.add(new InsnNode(IRETURN));use.maxStack=2;use.maxLocals=2;caller.methods.add(use);
        var nodes=new TreeMap<String,ClassNode>();for(var node:List.of(contract,helper,caller))nodes.put(node.name,node);return nodes;
    }
    private static ClassNode node(String name,String parent,int access){var n=new ClassNode();n.name=name;n.superName=parent;n.version=V1_8;n.access=access;return n;}
    private static MethodNode method(ClassNode node,String name){return node.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();}
    private static Map<String,byte[]> bytes(Map<String,ClassNode> nodes){var result=new TreeMap<String,byte[]>();nodes.forEach((name,node)->{var writer=new ClassWriter(0);node.accept(writer);result.put(name,writer.toByteArray());});return result;}
}
