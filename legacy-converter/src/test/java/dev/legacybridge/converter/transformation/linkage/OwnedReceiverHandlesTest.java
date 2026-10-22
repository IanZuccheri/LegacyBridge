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

/** Inert owned trees; no original classes defined, initialized or executed. */
class OwnedReceiverHandlesTest {
    private static final String OWNER="fixture/Counter";
    private static final Handle BOOT=new Handle(H_INVOKESTATIC,"java/lang/invoke/LambdaMetafactory","metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",false);
    @Test void independentlyProvesCaptureAndKeepsOtherPoliciesClosed() {
        var node=node();var proof=OwnedReceiverHandles.inspect(Map.of(OWNER,node));
        assertEquals("score",proof.implementation().name());assertEquals(1,proof.instruction());
        assertEquals("PROVEN_NON_NULL_THIS",proof.toTree().get("receiverRequirement"));
        assertEquals(false,proof.toTree().get("classesExecuted"));
        assertEquals(false,proof.toTree().get("publicationAllowed"));
        assertThrows(UnsupportedOperationException.class,()->proof.toTree().clear());
        var verifier=new OfflineLinkageVerifier();var bytes=bytes(node);
        assertEquals("PASSED_SCOPED",verifier.verifyOwnedReceiverHandles(bytes,ApiIndex.empty()).status());
        for(var policy:OfflineLinkageVerifier.CallbackPolicy.values())
            assertThrows(TransformationException.class,()->verifier.verify(bytes,ApiIndex.empty(),policy),policy.name());
        assertThrows(TransformationException.class,()->verifier.verifyOwnedInterfaceDefaults(bytes,ApiIndex.empty()));
    }
    @Test void initializedThisLocalAliasRetainsProvenanceWithoutLiteralLayoutGuard() {
        var node=node();var factory=method(node,"operation");
        factory.instructions.insert(factory.instructions.getFirst(),new VarInsnNode(ASTORE,1));
        factory.instructions.insert(factory.instructions.get(1),new VarInsnNode(ALOAD,1));
        factory.maxLocals=2;
        assertEquals("PASSED_SCOPED",new OfflineLinkageVerifier().verifyOwnedReceiverHandles(bytes(node),ApiIndex.empty()).status());
    }
    @Test void debugNodesDoNotChangeStructuralProof() {
        var node=node();var before=OwnedReceiverHandles.inspect(Map.of(OWNER,node));
        var label=new LabelNode();var factory=method(node,"operation");factory.instructions.insert(label);
        factory.instructions.insert(label,new LineNumberNode(21,label));
        assertEquals(before,OwnedReceiverHandles.inspect(Map.of(OWNER,node)));
    }
    @ParameterizedTest @ValueSource(strings={"NULL","OTHER_INSTANCE","FIELD","OVERWRITTEN_THIS","MERGED_NULL"})
    void sameTypeDoesNotProveCapturedThis(String defect) {
        var node=node();var factory=method(node,"operation");
        switch(defect) {
            case "NULL" -> factory.instructions.set(factory.instructions.getFirst(),new InsnNode(ACONST_NULL));
            case "OTHER_INSTANCE" -> {
                factory.instructions.set(factory.instructions.getFirst(),new TypeInsnNode(NEW,OWNER));
                factory.instructions.insert(factory.instructions.getFirst(),new InsnNode(DUP));
                factory.instructions.insert(factory.instructions.get(1),new MethodInsnNode(INVOKESPECIAL,OWNER,"<init>","()V",false));
                factory.maxStack=2;
            }
            case "FIELD" -> {
                node.fields.add(new FieldNode(ACC_PRIVATE,"other","L"+OWNER+";",null,null));
                factory.instructions.insert(factory.instructions.getFirst(),new FieldInsnNode(GETFIELD,OWNER,"other","L"+OWNER+";"));
            }
            case "OVERWRITTEN_THIS" -> {
                factory.instructions.insert(new VarInsnNode(ASTORE,0));
                factory.instructions.insert(new InsnNode(ACONST_NULL));
            }
            case "MERGED_NULL" -> {
                var other=new LabelNode();var merge=new LabelNode();var prefix=new InsnList();
                prefix.add(new InsnNode(ICONST_0));prefix.add(new JumpInsnNode(IFEQ,other));
                prefix.add(new VarInsnNode(ALOAD,0));prefix.add(new JumpInsnNode(GOTO,merge));
                prefix.add(other);prefix.add(new InsnNode(ACONST_NULL));prefix.add(merge);
                factory.instructions.remove(factory.instructions.getFirst());factory.instructions.insert(prefix);
            }
            default -> throw new AssertionError(defect);
        }
        assertNotNull(OwnedReceiverHandles.inspect(Map.of(OWNER,node)));
        var error=assertThrows(TransformationException.class,()->new OfflineLinkageVerifier().verifyOwnedReceiverHandles(bytes(node),ApiIndex.empty()));
        assertTrue(error.getMessage().contains("LAMBDA_RECEIVER_UNPROVEN"),error.toString());
    }
    @ParameterizedTest @ValueSource(strings={"OPEN","INTERFACE","SUPER","PRIVATE","STATIC","ABSTRACT","NATIVE",
            "MISSING","SPECIAL","INTERFACE_HANDLE","HANDLE_OWNER_KIND","FOREIGN_OWNER","CAPTURE","UNBOUND","SAM","BOXING",
            "RETURN","FACTORY_STATIC","FACTORY_ARGS","CTOR_PRIVATE","CLINIT","SECOND_SITE","ALT_META","BOOT_KIND",
            "STANDALONE","BAD_BODY","UNKNOWN_EXTERNAL","CODE_BOUND","METHOD_BOUND","PROTECTED_OWNER"})
    void malformedOrUnreviewedHandlesCannotBeAdmitted(String defect) {
        var node=node();var factory=method(node,"operation");var target=method(node,"score");var dynamic=dynamic(node);
        switch(defect) {
            case "OPEN" -> node.access &= ~ACC_FINAL;
            case "INTERFACE" -> node.interfaces.add("java/io/Serializable");
            case "SUPER" -> node.superName="unknown/Base";
            case "PRIVATE" -> target.access=ACC_PRIVATE;
            case "STATIC" -> target.access|=ACC_STATIC;
            case "ABSTRACT" -> target.access|=ACC_ABSTRACT;
            case "NATIVE" -> target.access|=ACC_NATIVE;
            case "MISSING" -> node.methods.remove(target);
            case "SPECIAL" -> dynamic.bsmArgs[1]=new Handle(H_INVOKESPECIAL,OWNER,"score","(I)I",false);
            case "INTERFACE_HANDLE" -> dynamic.bsmArgs[1]=new Handle(H_INVOKEINTERFACE,OWNER,"score","(I)I",true);
            case "HANDLE_OWNER_KIND" -> dynamic.bsmArgs[1]=new Handle(H_INVOKEVIRTUAL,OWNER,"score","(I)I",true);
            case "FOREIGN_OWNER" -> dynamic.bsmArgs[1]=new Handle(H_INVOKEVIRTUAL,"unknown/Api","score","(I)I",false);
            case "CAPTURE" -> dynamic.desc="(Ljava/lang/Object;)Ljava/util/function/IntUnaryOperator;";
            case "UNBOUND" -> dynamic.desc="()Ljava/util/function/IntUnaryOperator;";
            case "SAM" -> dynamic.name="get";
            case "BOXING" -> dynamic.bsmArgs[2]=Type.getMethodType("(Ljava/lang/Integer;)Ljava/lang/Integer;");
            case "RETURN" -> dynamic.bsmArgs[1]=new Handle(H_INVOKEVIRTUAL,OWNER,"score","(I)J",false);
            case "FACTORY_STATIC" -> factory.access|=ACC_STATIC;
            case "FACTORY_ARGS" -> factory.desc="(L"+OWNER+";)Ljava/util/function/IntUnaryOperator;";
            case "CTOR_PRIVATE" -> method(node,"<init>").access=ACC_PRIVATE;
            case "CLINIT" -> node.methods.add(new MethodNode(ACC_STATIC,"<clinit>","()V",null,null));
            case "SECOND_SITE" -> factory.instructions.insertBefore(dynamic,dynamic.clone(new HashMap<>()));
            case "ALT_META" -> dynamic.bsm=new Handle(H_INVOKESTATIC,BOOT.getOwner(),"altMetafactory",BOOT.getDesc(),false);
            case "BOOT_KIND" -> dynamic.bsm=new Handle(H_INVOKEVIRTUAL,BOOT.getOwner(),BOOT.getName(),BOOT.getDesc(),false);
            case "STANDALONE" -> factory.instructions.insert(new LdcInsnNode((Handle)dynamic.bsmArgs[1]));
            case "BAD_BODY" -> target.instructions.set(target.instructions.getLast(),new InsnNode(RETURN));
            case "UNKNOWN_EXTERNAL" -> target.instructions.insert(new MethodInsnNode(INVOKESTATIC,"unknown/Api","run","()V",false));
            case "CODE_BOUND" -> {for(int i=0;i<4097;i++)target.instructions.add(new InsnNode(NOP));}
            case "METHOD_BOUND" -> {for(int i=0;i<129;i++)node.methods.add(new MethodNode(ACC_PUBLIC,"m"+i,"()V",null,null));}
            case "PROTECTED_OWNER" -> node.name="java/lang/Forbidden";
            default -> throw new AssertionError(defect);
        }
        assertThrows(TransformationException.class,()->new OfflineLinkageVerifier().verifyOwnedReceiverHandles(bytes(node),ApiIndex.empty()),defect);
    }
    private static ClassNode node() {
        var n=new ClassNode();n.version=V1_8;n.access=ACC_PUBLIC|ACC_FINAL|ACC_SUPER;n.name=OWNER;n.superName="java/lang/Object";
        var ctor=new MethodNode(ACC_PUBLIC,"<init>","()V",null,null);
        ctor.instructions.add(new VarInsnNode(ALOAD,0));ctor.instructions.add(new MethodInsnNode(INVOKESPECIAL,"java/lang/Object","<init>","()V",false));
        ctor.instructions.add(new InsnNode(RETURN));ctor.maxStack=1;ctor.maxLocals=1;n.methods.add(ctor);
        var score=new MethodNode(ACC_PUBLIC,"score","(I)I",null,null);score.instructions.add(new VarInsnNode(ILOAD,1));
        score.instructions.add(new InsnNode(IRETURN));score.maxStack=1;score.maxLocals=2;n.methods.add(score);
        var factory=new MethodNode(ACC_PUBLIC,"operation","()Ljava/util/function/IntUnaryOperator;",null,null);
        factory.instructions.add(new VarInsnNode(ALOAD,0));
        factory.instructions.add(new InvokeDynamicInsnNode("applyAsInt","(L"+OWNER+";)Ljava/util/function/IntUnaryOperator;",BOOT,
                Type.getMethodType("(I)I"),new Handle(H_INVOKEVIRTUAL,OWNER,"score","(I)I",false),Type.getMethodType("(I)I")));
        factory.instructions.add(new InsnNode(ARETURN));factory.maxStack=1;factory.maxLocals=1;n.methods.add(factory);return n;
    }
    private static MethodNode method(ClassNode node,String name){return node.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();}
    private static InvokeDynamicInsnNode dynamic(ClassNode node){for(var i:method(node,"operation").instructions)if(i instanceof InvokeDynamicInsnNode d)return d;throw new AssertionError();}
    private static Map<String,byte[]> bytes(ClassNode node){var writer=new ClassWriter(0);node.accept(writer);return Map.of(node.name,writer.toByteArray());}
}
