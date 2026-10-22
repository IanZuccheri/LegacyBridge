package dev.legacybridge.converter.conversion.logic;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/**
 * Static, bounded path analysis of trusted native API bytecode, NOT a native runtime test.
 * No ClassLoader, class initialization, mod class execution, world or client is used.
 * The small symbolic evaluator refuses every opcode outside the reviewed enum paths.
 * Direction axis/opposite metadata is read from native enum-construction instructions;
 * the indexed direction lookup is the independently audited data3d/native table contract.
 */
@EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment",matches="true")
class HorizontalOrientationApiAuditTest {
    private static final String[] DIRECTIONS={"NORTH","EAST","SOUTH","WEST"};
    private static final Map<Boolean,NativeApi> APIS=new HashMap<>();
    @AfterAll static void close()throws Exception{for(var api:APIS.values())api.close();APIS.clear();}

    static Stream<Arguments> cases(){
        var rows=new LinkedHashMap<String,List<String>>();
        rows.put("Rotation/NONE",List.of("NORTH","EAST","SOUTH","WEST"));
        rows.put("Rotation/CLOCKWISE_90",List.of("EAST","SOUTH","WEST","NORTH"));
        rows.put("Rotation/CLOCKWISE_180",List.of("SOUTH","WEST","NORTH","EAST"));
        rows.put("Rotation/COUNTERCLOCKWISE_90",List.of("WEST","NORTH","EAST","SOUTH"));
        rows.put("Mirror/NONE",List.of("NORTH","EAST","SOUTH","WEST"));
        rows.put("Mirror/LEFT_RIGHT",List.of("SOUTH","EAST","NORTH","WEST"));
        rows.put("Mirror/FRONT_BACK",List.of("NORTH","WEST","SOUTH","EAST"));
        return rows.entrySet().stream().flatMap(e->java.util.stream.IntStream.range(0,4)
                .mapToObj(i->Arguments.of(e.getKey(),DIRECTIONS[i],e.getValue().get(i))));
    }
    @ParameterizedTest @MethodSource("cases")
    void bothPinnedNativeBytecodePathsMatchTheReviewedFiniteMatrix(String operation,String input,String expected)throws Exception{
        var parts=operation.split("/");
        assertEquals(expected,api(true).orientation(parts[0],parts[1],input));
        assertEquals(expected,api(false).orientation(parts[0],parts[1],input));
    }

    private static NativeApi api(boolean legacy)throws Exception{
        if(!APIS.containsKey(legacy)){
            var props=new Properties();try(var stream=Files.newInputStream(Path.of(System.getProperty("legacybridge.logicMappingProfile")))){props.load(stream);}
            String jars=Objects.requireNonNull(props.getProperty(legacy?"legacy.apiJars":"target.apiJars"));
            APIS.put(legacy,new NativeApi(Path.of(jars.split(";")[0]),legacy));
        }
        return APIS.get(legacy);
    }

    private record EnumValue(String owner,String name){}
    private record SwitchTable(Map<String,Integer> labels){}
    private record DirectionData(String name,int id,int opposite,String axis){}

    private static final class NativeApi implements AutoCloseable {
        final ZipFile archive;final String direction,rotation,mirror;
        final Map<String,ClassNode> nodes=new HashMap<>();final Map<String,DirectionData> directions=new HashMap<>();
        NativeApi(Path jar,boolean old)throws Exception{
            archive=new ZipFile(jar.toFile());direction=old?"net/minecraft/util/Direction":"net/minecraft/core/Direction";
            rotation=old?"net/minecraft/util/Rotation":"net/minecraft/world/level/block/Rotation";
            mirror=old?"net/minecraft/util/Mirror":"net/minecraft/world/level/block/Mirror";
            var init=code(method(direction,"<clinit>","()V"));
            for(int i=0;i<init.size();i++)if(init.get(i) instanceof TypeInsnNode n&&n.getOpcode()==NEW&&n.desc.equals(direction)){
                assertEquals(DUP,init.get(i+1).getOpcode());
                String name=(String)((LdcInsnNode)init.get(i+2)).cst;
                int ordinal=number(init.get(i+3)),id=number(init.get(i+4)),opposite=number(init.get(i+5));
                assertEquals(id,ordinal,"Exact pinned enum identity order");
                String axis=null;int end=i+6;
                for(;end<init.size();end++){
                    if(init.get(end) instanceof FieldInsnNode f&&f.owner.equals(direction+"$Axis")&&f.getOpcode()==GETSTATIC)axis=f.name;
                    if(init.get(end) instanceof FieldInsnNode f&&f.owner.equals(direction)&&f.getOpcode()==PUTSTATIC){assertEquals(name,f.name);break;}
                }
                assertNotNull(axis);assertTrue(end<init.size());
                assertNull(directions.put(name,new DirectionData(name,id,opposite,axis)));
            }
            assertEquals(Set.of("DOWN","UP","NORTH","SOUTH","WEST","EAST"),directions.keySet());
            assertEquals(Set.of(0,1,2,3,4,5),directions.values().stream().map(DirectionData::id).collect(java.util.stream.Collectors.toSet()));
            var axis=code(method(direction,"getAxis","()L"+direction+"$Axis;"));
            assertEquals(3,axis.size());assertEquals(ALOAD,axis.get(0).getOpcode());
            assertEquals("axis",((FieldInsnNode)axis.get(1)).name);assertEquals(ARETURN,axis.get(2).getOpcode());
            var opposite=code(method(direction,"getOpposite","()L"+direction+";"));
            assertEquals(4,opposite.size());assertEquals(ALOAD,opposite.get(0).getOpcode());
            assertEquals("oppositeIndex",((FieldInsnNode)opposite.get(1)).name);
            var lookup=(MethodInsnNode)opposite.get(2);assertEquals(INVOKESTATIC,lookup.getOpcode());
            assertEquals(direction,lookup.owner);assertEquals("from3DDataValue",lookup.name);assertEquals("(I)L"+direction+";",lookup.desc);
            assertEquals(ARETURN,opposite.get(3).getOpcode());
        }
        String orientation(String type,String value,String input)throws Exception{
            String owner=type.equals("Rotation")?rotation:mirror;
            return ((EnumValue)evaluate(method(owner,type.equals("Rotation")?"rotate":"mirror","(L"+direction+";)L"+direction+";"),
                    List.of(new EnumValue(owner,value),new EnumValue(direction,input)),0)).name;
        }
        Object evaluate(MethodNode method,List<Object> locals,int depth)throws Exception{
            assertTrue(depth<4,"Bounded native API call graph");assertTrue(method.tryCatchBlocks.isEmpty());
            var stack=new ArrayList<Object>();var insns=method.instructions;
            for(int pc=0,steps=0;pc<insns.size()&&steps++<128;pc++){
                var i=insns.get(pc);int op=i.getOpcode();if(op<0)continue;
                if(op==ALOAD)stack.add(locals.get(((VarInsnNode)i).var));
                else if(op==GETSTATIC){
                    var f=(FieldInsnNode)i;
                    if(f.desc.equals("[I"))stack.add(switchTable(f));
                    else {assertTrue(Set.of(direction,direction+"$Axis",rotation,mirror).contains(f.owner),f.owner);stack.add(new EnumValue(f.owner,f.name));}
                }else if(op==INVOKEVIRTUAL){
                    var call=(MethodInsnNode)i;assertFalse(call.itf);
                    EnumValue receiver=(EnumValue)pop(stack);
                    assertEquals(receiver.owner,call.owner);
                    if(call.name.equals("ordinal")){assertEquals("()I",call.desc);stack.add(receiver);}
                    else if(call.owner.equals(direction)&&call.name.equals("getAxis")){
                        assertEquals("()L"+direction+"$Axis;",call.desc);stack.add(new EnumValue(direction+"$Axis",directions.get(receiver.name).axis));
                    }else if(call.owner.equals(direction)&&call.name.equals("getOpposite")){
                        assertEquals("()L"+direction+";",call.desc);int target=directions.get(receiver.name).opposite;
                        String name=directions.values().stream().filter(v->v.id==target).map(DirectionData::name).findFirst().orElseThrow();
                        stack.add(new EnumValue(direction,name));
                    }else if(call.owner.equals(direction)&&Set.of("getClockWise","getCounterClockWise").contains(call.name))
                        stack.add(evaluate(method(direction,call.name,call.desc),List.of(receiver),depth+1));
                    else throw new AssertionError("Unreviewed symbolic native call: "+call.owner+"."+call.name+call.desc);
                }else if(op==IALOAD){EnumValue index=(EnumValue)pop(stack);var table=(SwitchTable)pop(stack);stack.add(Objects.requireNonNull(table.labels.get(index.owner+"."+index.name)));}
                else if(op==IF_ACMPNE||op==IF_ACMPEQ){Object right=pop(stack),left=pop(stack);boolean equal=left.equals(right);
                    if(op==IF_ACMPEQ?equal:!equal)pc=insns.indexOf(((JumpInsnNode)i).label)-1;
                }else if(op==GOTO)pc=insns.indexOf(((JumpInsnNode)i).label)-1;
                else if(op==TABLESWITCH){int key=(Integer)pop(stack);var t=(TableSwitchInsnNode)i;pc=insns.indexOf(key>=t.min&&key<=t.max?t.labels.get(key-t.min):t.dflt)-1;}
                else if(op==ARETURN){Object result=pop(stack);assertTrue(stack.isEmpty());return result;}
                else throw new AssertionError("Unreviewed reachable opcode "+op+" in "+method.name);
            }
            throw new AssertionError("Native symbolic path did not return within its budget");
        }
        SwitchTable switchTable(FieldInsnNode field)throws Exception{
            var body=code(method(field.owner,"<clinit>","()V"));var result=new HashMap<String,Integer>();
            for(int n=0;n+4<body.size();n++)if(body.get(n) instanceof FieldInsnNode array&&array.getOpcode()==GETSTATIC
                    &&array.owner.equals(field.owner)&&array.name.equals(field.name)&&array.desc.equals("[I")){
                var value=(FieldInsnNode)body.get(n+1);var ordinal=(MethodInsnNode)body.get(n+2);
                assertEquals(GETSTATIC,value.getOpcode());assertEquals(INVOKEVIRTUAL,ordinal.getOpcode());
                assertEquals(value.owner,ordinal.owner);assertEquals("ordinal",ordinal.name);assertEquals("()I",ordinal.desc);
                assertEquals(IASTORE,body.get(n+4).getOpcode());assertNull(result.put(value.owner+"."+value.name,number(body.get(n+3))));
            }
            assertFalse(result.isEmpty());return new SwitchTable(Map.copyOf(result));
        }
        MethodNode method(String owner,String name,String desc)throws Exception{
            if(!nodes.containsKey(owner)){
                var entry=Objects.requireNonNull(archive.getEntry(owner+".class"),owner);
                try(var stream=archive.getInputStream(entry)){var node=new ClassNode();new ClassReader(stream.readAllBytes()).accept(node,0);nodes.put(owner,node);}
            }
            return nodes.get(owner).methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).findFirst().orElseThrow();
        }
        @Override public void close()throws Exception{archive.close();}
    }
    private static List<AbstractInsnNode> code(MethodNode method){return Arrays.stream(method.instructions.toArray()).filter(i->i.getOpcode()>=0).toList();}
    private static Object pop(List<Object> values){return values.remove(values.size()-1);}
    private static int number(AbstractInsnNode i){
        if(i.getOpcode()>=ICONST_M1&&i.getOpcode()<=ICONST_5)return i.getOpcode()-ICONST_0;
        if(i instanceof IntInsnNode n)return n.operand;
        if(i instanceof LdcInsnNode n&&n.cst instanceof Integer v)return v;
        throw new AssertionError("Expected exact small enum literal");
    }
}
