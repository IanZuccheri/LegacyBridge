package dev.legacybridge.converter.conversion.logic;

import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/**
 * Native API bytecode read as data. Straight-line construction/cycle/delegation bodies are exact;
 * a bounded symbolic interpreter follows only collection selection and enum predicates.
 * The modeled collections contain the two booleans requested by the real property constructor.
 * No Guava state table, original mod, World/Level, native initializer or game callback executes.
 * This establishes local API contracts, NOT native dispatch, write success, thread, reach,
 * permission, state-table construction, network delivery or neighbor-effect qualification.
 */
@EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment",matches="true")
class BooleanToggleNativeApiAuditTest {
    private static final Map<Boolean,Api> APIS=new HashMap<>();
    @AfterAll static void close()throws Exception{for(var api:APIS.values())api.close();APIS.clear();}

    @ParameterizedTest @ValueSource(booleans={true,false})
    void nativePropertyFactoryConstructsOnlyTwoBoxedBooleanValues(boolean old)throws Exception{
        var a=api(old);
        assertEquals(List.of("NEW "+a.bool,"DUP","ALOAD 0",call(INVOKESPECIAL,a.bool,"<init>","(Ljava/lang/String;)V"),"ARETURN"),
                tokens(a.method(a.bool,"create","(Ljava/lang/String;)L"+a.bool+";")));
        assertEquals(List.of("ALOAD 0","ALOAD 1","LDC Ljava/lang/Boolean;",call(INVOKESPECIAL,a.property,"<init>","(Ljava/lang/String;Ljava/lang/Class;)V"),
                "ALOAD 0","ICONST_1",call(INVOKESTATIC,"java/lang/Boolean","valueOf","(Z)Ljava/lang/Boolean;"),"ICONST_0",
                call(INVOKESTATIC,"java/lang/Boolean","valueOf","(Z)Ljava/lang/Boolean;"),
                call(INVOKESTATIC,"com/google/common/collect/ImmutableSet","of","(Ljava/lang/Object;Ljava/lang/Object;)Lcom/google/common/collect/ImmutableSet;"),
                "PUTFIELD "+a.bool+".values:Lcom/google/common/collect/ImmutableSet;","RETURN"),tokens(a.method(a.bool,"<init>","(Ljava/lang/String;)V")));
        assertEquals(List.of("ALOAD 0","GETFIELD "+a.bool+".values:Lcom/google/common/collect/ImmutableSet;","ARETURN"),tokens(a.method(a.bool,"getPossibleValues","()Ljava/util/Collection;")));
    }

    @ParameterizedTest @ValueSource(booleans={true,false})
    void nativeCycleRetainsReceiverAndPropertyAndUsesErasedSetValueResult(boolean old)throws Exception{
        var a=api(old);String property="L"+a.property+";";
        assertEquals(List.of("ALOAD 0","ALOAD 1","ALOAD 1",call(INVOKEVIRTUAL,a.property,"getPossibleValues","()Ljava/util/Collection;"),
                "ALOAD 0","ALOAD 1",call(INVOKEVIRTUAL,a.holder,"getValue","("+property+")Ljava/lang/Comparable;"),
                call(INVOKESTATIC,a.holder,"findNextInCollection","(Ljava/util/Collection;Ljava/lang/Object;)Ljava/lang/Object;"),
                "CHECKCAST java/lang/Comparable",call(INVOKEVIRTUAL,a.holder,"setValue","("+property+"Ljava/lang/Comparable;)Ljava/lang/Object;"),"ARETURN"),
                tokens(a.method(a.holder,"cycle","("+property+")Ljava/lang/Object;")));
    }

    @ParameterizedTest @ValueSource(booleans={true,false})
    void realCollectionSelectionPathTogglesBothValuesIndependentlyOfIterationOrder(boolean old)throws Exception{
        var a=api(old);var method=a.method(a.holder,"findNextInCollection","(Ljava/util/Collection;Ljava/lang/Object;)Ljava/lang/Object;");
        for(var values:List.of(List.of(Boolean.TRUE,Boolean.FALSE),List.of(Boolean.FALSE,Boolean.TRUE)))
            for(boolean initial:List.of(false,true)){
                Object next=a.follow(method,List.of(values,initial));assertEquals(!initial,next);
                assertEquals(initial,a.follow(method,List.of(values,next)));
            }
    }

    @ParameterizedTest @ValueSource(booleans={true,false})
    void threeArgumentWorldWriteOnlyDelegatesWithTheSameArgumentsAndUpdateLimit512(boolean old)throws Exception{
        var a=api(old);String args="L"+a.position+";L"+a.state+";";
        assertEquals(List.of("ALOAD 0","ALOAD 1","ALOAD 2","ILOAD 3","SIPUSH 512",
                call(INVOKEVIRTUAL,a.world,"setBlock","("+args+"II)Z"),"IRETURN"),tokens(a.method(a.world,"setBlock","("+args+"I)Z")));
        // The four-argument native operation is deliberately NOT symbolically executed.
    }

    @ParameterizedTest @ValueSource(booleans={true,false})
    void onlySuccessAndConsumeHaveTheReviewedNativeConsumptionAndSwingResults(boolean old)throws Exception{
        var a=api(old);
        for(String result:List.of("SUCCESS","CONSUME")){
            assertEquals(1,a.follow(a.method(a.result,"consumesAction","()Z"),List.of(result)));
            assertEquals(result.equals("SUCCESS")?1:0,a.follow(a.method(a.result,"shouldSwing","()Z"),List.of(result)));
        }
        assertEquals("SUCCESS",a.follow(a.method(a.result,"sidedSuccess","(Z)L"+a.result+";"),List.of(1)));
        assertEquals("CONSUME",a.follow(a.method(a.result,"sidedSuccess","(Z)L"+a.result+";"),List.of(0)));
    }

    @Test void targetStatPredicateIsAnExplicitAdditionalApiNotAnInventedLegacyEquivalence()throws Exception{
        var old=api(true);var target=api(false);
        assertTrue(old.node(old.result).methods.stream().noneMatch(m->m.name.equals("shouldAwardStats")));
        for(String value:List.of("SUCCESS","CONSUME"))
            assertEquals(1,target.follow(target.method(target.result,"shouldAwardStats","()Z"),List.of(value)));
    }

    @Test void unknownCallOnAnOtherwiseReviewedNativePathIsRejected()throws Exception{
        var a=api(false);var method=a.method(a.holder,"findNextInCollection","(Ljava/util/Collection;Ljava/lang/Object;)Ljava/lang/Object;");
        var extra=new MethodInsnNode(INVOKESTATIC,"java/lang/System","nanoTime","()J",false);method.instructions.insert(extra);
        try{assertThrows(AssertionError.class,()->a.follow(method,List.of(List.of(true,false),false)));}
        finally{method.instructions.remove(extra);}
    }

    private static Api api(boolean old)throws Exception{
        if(!APIS.containsKey(old)){var p=new Properties();try(var in=Files.newInputStream(Path.of(System.getProperty("legacybridge.logicMappingProfile")))){p.load(in);}
            APIS.put(old,new Api(Path.of(p.getProperty(old?"legacy.apiJars":"target.apiJars").split(";")[0]),old));}return APIS.get(old);
    }
    private static final class Api implements AutoCloseable{
        final ZipFile zip;final String bool,property,holder,world,position,state,result;
        final Map<String,ClassNode> nodes=new HashMap<>();
        Api(Path path,boolean old)throws Exception{
            zip=new ZipFile(path.toFile());bool=old?"net/minecraft/state/BooleanProperty":"net/minecraft/world/level/block/state/properties/BooleanProperty";
            property=old?"net/minecraft/state/Property":"net/minecraft/world/level/block/state/properties/Property";
            holder=old?"net/minecraft/state/StateHolder":"net/minecraft/world/level/block/state/StateHolder";
            world=old?"net/minecraft/world/World":"net/minecraft/world/level/Level";
            position=old?"net/minecraft/util/math/BlockPos":"net/minecraft/core/BlockPos";
            state=old?"net/minecraft/block/BlockState":"net/minecraft/world/level/block/state/BlockState";
            result=old?"net/minecraft/util/ActionResultType":"net/minecraft/world/InteractionResult";
        }
        ClassNode node(String owner)throws Exception{
            if(!nodes.containsKey(owner)){var e=Objects.requireNonNull(zip.getEntry(owner+".class"));assertTrue(e.getSize()>0&&e.getSize()<=2*1024*1024);
                try(var in=zip.getInputStream(e)){byte[] bytes=in.readNBytes(2*1024*1024+1);assertTrue(bytes.length<=2*1024*1024);var node=new ClassNode();new ClassReader(bytes).accept(node,0);nodes.put(owner,node);}}
            return nodes.get(owner);
        }
        MethodNode method(String owner,String name,String desc)throws Exception{return node(owner).methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).findFirst().orElseThrow();}
        Object follow(MethodNode method,List<?> arguments){
            assertTrue(method.tryCatchBlocks.isEmpty());Object[] local=new Object[method.maxLocals];for(int n=0;n<arguments.size();n++)local[n]=arguments.get(n);
            var stack=new ArrayList<Object>();var insns=method.instructions;
            for(int pc=0,steps=0;pc<insns.size()&&steps++<128;pc++){
                var i=insns.get(pc);int op=i.getOpcode();if(op<0)continue;
                if(op==ALOAD||op==ILOAD)stack.add(local[((VarInsnNode)i).var]);
                else if(op==ASTORE)local[((VarInsnNode)i).var]=pop(stack);
                else if(op==ICONST_0||op==ICONST_1)stack.add(op-ICONST_0);
                else if(op==GETSTATIC){var f=(FieldInsnNode)i;assertEquals(result,f.owner);assertEquals("L"+result+";",f.desc);
                    assertTrue(Set.of("SUCCESS","CONSUME","CONSUME_PARTIAL","PASS","FAIL").contains(f.name));stack.add(f.name);}
                else if(op==INVOKEINTERFACE||op==INVOKEVIRTUAL){var m=(MethodInsnNode)i;
                    if(m.owner.equals("java/lang/Object")&&m.name.equals("equals")&&m.desc.equals("(Ljava/lang/Object;)Z")&&op==INVOKEVIRTUAL){Object right=pop(stack),left=pop(stack);assertTrue(left instanceof Boolean);stack.add(left.equals(right)?1:0);}
                    else if(op==INVOKEINTERFACE&&m.owner.equals("java/util/Collection")&&m.name.equals("iterator")&&m.desc.equals("()Ljava/util/Iterator;"))stack.add(((List<?>)pop(stack)).iterator());
                    else if(op==INVOKEINTERFACE&&m.owner.equals("java/util/Iterator")&&m.name.equals("hasNext")&&m.desc.equals("()Z"))stack.add(((Iterator<?>)pop(stack)).hasNext()?1:0);
                    else if(op==INVOKEINTERFACE&&m.owner.equals("java/util/Iterator")&&m.name.equals("next")&&m.desc.equals("()Ljava/lang/Object;"))stack.add(((Iterator<?>)pop(stack)).next());
                    else throw new AssertionError("Unreviewed native path call: "+m.owner+"."+m.name+m.desc);
                }else if(op==IF_ACMPEQ||op==IF_ACMPNE){Object right=pop(stack),left=pop(stack);boolean equal=Objects.equals(left,right);
                    if(op==IF_ACMPEQ?equal:!equal)pc=insns.indexOf(((JumpInsnNode)i).label)-1;
                }else if(op==IFEQ||op==IFNE){int value=(Integer)pop(stack);if(op==IFEQ?value==0:value!=0)pc=insns.indexOf(((JumpInsnNode)i).label)-1;}
                else if(op==GOTO)pc=insns.indexOf(((JumpInsnNode)i).label)-1;
                else if(op==IRETURN||op==ARETURN){Object value=pop(stack);assertTrue(stack.isEmpty());return value;}
                else throw new AssertionError("Unreviewed reached native opcode "+op);
            }throw new AssertionError("Native symbolic path did not return within budget");
        }
        @Override public void close()throws Exception{zip.close();}
    }
    private static Object pop(List<Object> stack){return stack.remove(stack.size()-1);}
    private static String call(int opcode,String owner,String name,String desc){return org.objectweb.asm.util.Printer.OPCODES[opcode]+" "+owner+"."+name+desc;}
    private static List<String> tokens(MethodNode method){assertTrue(method.tryCatchBlocks.isEmpty());var result=new ArrayList<String>();
        for(var i:method.instructions){int op=i.getOpcode();if(op<0)continue;String text=org.objectweb.asm.util.Printer.OPCODES[op];
            if(i instanceof VarInsnNode v)text+=" "+v.var;
            else if(i instanceof TypeInsnNode t)text+=" "+t.desc;
            else if(i instanceof LdcInsnNode l)text+=" "+l.cst;
            else if(i instanceof IntInsnNode n)text+=" "+n.operand;
            else if(i instanceof FieldInsnNode f)text+=" "+f.owner+"."+f.name+":"+f.desc;
            else if(i instanceof MethodInsnNode m){assertFalse(m.itf);text=call(op,m.owner,m.name,m.desc);}
            else if(!(i instanceof InsnNode))throw new AssertionError("Unexpected straight-line instruction "+i);
            result.add(text);
        }return List.copyOf(result);
    }
}
