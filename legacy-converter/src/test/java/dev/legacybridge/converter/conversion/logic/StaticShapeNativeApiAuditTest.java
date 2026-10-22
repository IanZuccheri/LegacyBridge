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
 * Bounded STATIC path oracle over trusted native bytecode, never a game/class-loader test.
 * It reads actual box normalization, Shapes.create/findBits and the requested discrete
 * grid/fill operations. BitSet allocation/filling is a recorded boundary, not a BitSet
 * implementation. calculateSolid reads actual bytecode given explicitly supplied cache,
 * isEmpty and bounds facts; those facts are NOT claimed to have been obtained in a game.
 * Unknown reached calls/opcodes, unexpected native signatures and unbounded loops fail.
 * Neither native initialization nor original fixture classes are executed.
 */
@EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment",matches="true")
class StaticShapeNativeApiAuditTest {
    private static final Map<Boolean,NativeApi> APIS=new HashMap<>();
    @AfterAll static void close()throws Exception{for(var a:APIS.values())a.close();APIS.clear();}

    @ParameterizedTest @ValueSource(booleans={true,false})
    void nativeBlockBoxDividesEveryCoordinateBySixteenBeforeCallingNativeShapeFactory(boolean old)throws Exception{
        var api=api(old);api.auditBox();
    }

    @ParameterizedTest @ValueSource(booleans={true,false})
    void actualNativeFactoryPathRequestsExactlyTheLowerCellOfAOneByTwoByOneGrid(boolean old)throws Exception{
        var api=api(old);api.auditBox();
        var shape=(Value)api.evaluate(api.block,"box","(DDDDDD)L"+api.shape+";",null,List.of(0D,0D,0D,16D,8D,16D),0);
        assertEquals(api.cube,shape.type);
        var grid=(Value)shape.fields.get("shape");assertNotNull(grid);
        assertEquals(List.of(1,2,1),grid.fields.get("dimensions"));
        assertEquals(List.of(List.of(0,0,0)),grid.fills);
        assertEquals(List.of(0,0,0,1,1,1),List.of(grid.fields.get("xMin"),grid.fields.get("yMin"),grid.fields.get("zMin"),
                grid.fields.get("xMax"),grid.fields.get("yMax"),grid.fields.get("zMax")));
    }

    @ParameterizedTest @ValueSource(booleans={true,false})
    void nativeFindBitsAgreesOnlyForTheExactAdmittedCoordinatePairs(boolean old)throws Exception{
        var api=api(old);
        assertEquals(0,api.evaluate(api.shapes,"findBits","(DD)I",null,List.of(0D,1D),0));
        assertEquals(1,api.evaluate(api.shapes,"findBits","(DD)I",null,List.of(0D,.5D),0));
        var names=code(api.method(api.shapes,"findBits","(DD)I")).stream().filter(i->i instanceof MethodInsnNode)
                .map(i->((MethodInsnNode)i).name).toList();
        assertTrue(names.contains(old?"floor":"round"),"Versions do not have interchangeable general rounding contracts");
        assertFalse(names.contains(old?"round":"floor"));
    }

    @Test void targetAverageExtentIsFiveSixthsNotVolumeAndSolidDependsOnCacheFacts()throws Exception{
        var api=api(false);var bounds=api.bounds();
        assertEquals(5D/6D,api.evaluate(api.aabb,"getSize","()D",bounds,List.of(),0));
        var method=api.method(api.state,"calculateSolid","()Z");
        var thresholds=code(method).stream().filter(i->i instanceof LdcInsnNode).map(i->((LdcInsnNode)i).cst).toList();
        assertEquals(List.of(0.7291666666666666D),thresholds);
        assertEquals(0,api.solid(false,false,false,false));
        assertEquals(1,api.solid(false,false,true,false));
        assertEquals(0,api.solid(false,false,true,true));
        assertEquals(1,api.solid(true,false,false,false));
        assertEquals(0,api.solid(false,true,true,false));
        // Native cache creation ordering is checked, not executed or treated as a runtime pass.
        var init=code(api.method(api.state,"initCache","()V"));
        int cache=-1,calculate=-1,solid=-1;
        for(int n=0;n<init.size();n++){
            var i=init.get(n);
            if(i instanceof FieldInsnNode f&&f.getOpcode()==PUTFIELD&&f.name.equals("cache"))cache=n;
            if(i instanceof MethodInsnNode m&&m.name.equals("calculateSolid")){assertEquals(api.state,m.owner);assertEquals("()Z",m.desc);calculate=n;}
            if(i instanceof FieldInsnNode f&&f.getOpcode()==PUTFIELD&&f.name.equals("legacySolid"))solid=n;
        }
        assertTrue(cache>=0&&cache<calculate&&calculate<solid);
        assertTrue(init.stream().anyMatch(i->i instanceof MethodInsnNode m&&m.name.equals("hasDynamicShape")&&m.desc.equals("()Z")));
    }

    @Test void unexpectedNormalizationAndReachedNativeCallsFailInsteadOfReceivingMadeUpSemantics()throws Exception{
        var api=api(false);var box=api.method(api.block,"box","(DDDDDD)L"+api.shape+";");
        var literal=(LdcInsnNode)code(box).get(1);Object saved=literal.cst;
        try{literal.cst=8D;assertThrows(AssertionError.class,api::auditBox);}finally{literal.cst=saved;}
        var find=api.method(api.shapes,"findBits","(DD)I");
        var unknown=new MethodInsnNode(INVOKESTATIC,"java/lang/System","nanoTime","()J",false);
        find.instructions.insert(unknown);
        try{assertThrows(AssertionError.class,()->api.evaluate(api.shapes,"findBits","(DD)I",null,List.of(0D,.5D),0));}
        finally{find.instructions.remove(unknown);}
    }

    private static NativeApi api(boolean old)throws Exception{
        if(!APIS.containsKey(old)){
            var p=new Properties();try(var in=Files.newInputStream(Path.of(System.getProperty("legacybridge.logicMappingProfile")))){p.load(in);}
            APIS.put(old,new NativeApi(Path.of(Objects.requireNonNull(p.getProperty(old?"legacy.apiJars":"target.apiJars")).split(";")[0]),old));
        }return APIS.get(old);
    }
    private static final class Value {
        final String type;final Map<String,Object> fields=new HashMap<>();final List<List<Integer>> fills=new ArrayList<>();
        Value(String type){this.type=type;}
    }
    private static final class NativeApi implements AutoCloseable {
        final ZipFile zip;final boolean old;final String block,shapes,shape,aabb,grid,cube,state,properties;
        final Map<String,ClassNode> nodes=new HashMap<>();
        NativeApi(Path jar,boolean old)throws Exception{
            this.old=old;zip=new ZipFile(jar.toFile());
            block=old?"net/minecraft/block/Block":"net/minecraft/world/level/block/Block";
            shapes=old?"net/minecraft/util/math/shapes/VoxelShapes":"net/minecraft/world/phys/shapes/Shapes";
            shape=old?"net/minecraft/util/math/shapes/VoxelShape":"net/minecraft/world/phys/shapes/VoxelShape";
            aabb=old?"net/minecraft/util/math/AxisAlignedBB":"net/minecraft/world/phys/AABB";
            grid=old?"net/minecraft/util/math/shapes/BitSetVoxelShapePart":"net/minecraft/world/phys/shapes/BitSetDiscreteVoxelShape";
            cube=old?"net/minecraft/util/math/shapes/VoxelShapeCube":"net/minecraft/world/phys/shapes/CubeVoxelShape";
            state="net/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase";
            properties="net/minecraft/world/level/block/state/BlockBehaviour$Properties";
        }
        void auditBox()throws Exception{
            var body=code(method(block,"box","(DDDDDD)L"+shape+";"));assertEquals(20,body.size());
            for(int n=0;n<6;n++){
                assertEquals(DLOAD,body.get(n*3).getOpcode());assertEquals(n*2,((VarInsnNode)body.get(n*3)).var);
                assertEquals(16D,((LdcInsnNode)body.get(n*3+1)).cst);assertEquals(DDIV,body.get(n*3+2).getOpcode());
            }
            var call=(MethodInsnNode)body.get(18);assertEquals(INVOKESTATIC,call.getOpcode());assertEquals(shapes,call.owner);
            assertEquals("box",call.name);assertEquals("(DDDDDD)L"+shape+";",call.desc);assertFalse(call.itf);assertEquals(ARETURN,body.get(19).getOpcode());
        }
        Value bounds(){var b=new Value(aabb);b.fields.putAll(Map.of("minX",0D,"minY",0D,"minZ",0D,"maxX",1D,"maxY",.5D,"maxZ",1D));return b;}
        Object solid(boolean forceOn,boolean forceOff,boolean hasCache,boolean empty)throws Exception{
            var props=new Value(properties);props.fields.put("forceSolidOn",forceOn?1:0);props.fields.put("forceSolidOff",forceOff?1:0);
            var owner=new Value(block);owner.fields.put("properties",props);var instance=new Value(state);instance.fields.put("owner",owner);
            if(hasCache){var cache=new Value(state+"$Cache");var s=new Value(shape);s.fields.put("assumedEmpty",empty?1:0);s.fields.put("assumedBounds",bounds());
                cache.fields.put("collisionShape",s);instance.fields.put("cache",cache);}else instance.fields.put("cache",null);
            return evaluate(state,"calculateSolid","()Z",instance,List.of(),0);
        }
        Object evaluate(String owner,String name,String descriptor,Value self,List<Object> arguments,int depth)throws Exception{
            assertTrue(depth<12,"Bounded native symbolic call graph");
            var method=method(owner,name,descriptor);assertTrue(method.tryCatchBlocks.isEmpty());
            assertEquals(self==null,(method.access&ACC_STATIC)!=0);
            var local=new Object[method.maxLocals];int slot=0;if(self!=null)local[slot++]=self;
            var types=Type.getArgumentTypes(descriptor);assertEquals(types.length,arguments.size());
            for(int n=0;n<types.length;n++){local[slot]=arguments.get(n);slot+=types[n].getSize();}
            var stack=new ArrayList<Object>();var insns=method.instructions;
            for(int pc=0,steps=0;pc<insns.size()&&steps++<2048;pc++){
                var i=insns.get(pc);int op=i.getOpcode();if(op<0)continue;
                if(op>=ICONST_M1&&op<=ICONST_5)stack.add(op-ICONST_0);
                else if(op==DCONST_0||op==DCONST_1)stack.add((double)(op-DCONST_0));
                else if(op==LCONST_0||op==LCONST_1)stack.add((long)(op-LCONST_0));
                else if(op==LDC)stack.add(((LdcInsnNode)i).cst);
                else if(op==ALOAD||op==ILOAD||op==DLOAD||op==LLOAD)stack.add(local[((VarInsnNode)i).var]);
                else if(op==ASTORE||op==ISTORE||op==DSTORE||op==LSTORE)local[((VarInsnNode)i).var]=pop(stack);
                else if(op==DUP)stack.add(stack.get(stack.size()-1));
                else if(op==NEW){String type=((TypeInsnNode)i).desc;assertTrue(Set.of(aabb,grid,cube).contains(type),type);stack.add(new Value(type));}
                else if(op==CHECKCAST){var v=(Value)stack.get(stack.size()-1);assertEquals(((TypeInsnNode)i).desc,v.type);}
                else if(op==GETFIELD){var f=(FieldInsnNode)i;var v=(Value)pop(stack);assertTrue(v.fields.containsKey(f.name),"Unknown symbolic field "+f.owner+"."+f.name);stack.add(v.fields.get(f.name));}
                else if(op==PUTFIELD){var f=(FieldInsnNode)i;Object value=pop(stack);((Value)pop(stack)).fields.put(f.name,value);}
                else if(op==I2D||op==L2D)stack.add(((Number)pop(stack)).doubleValue());
                else if(op==L2I)stack.add(((Number)pop(stack)).intValue());
                else if(op==I2L)stack.add(((Number)pop(stack)).longValue());
                else if(op==DDIV||op==DMUL||op==DSUB||op==DADD){double right=((Number)pop(stack)).doubleValue(),left=((Number)pop(stack)).doubleValue();
                    stack.add(op==DDIV?left/right:op==DMUL?left*right:op==DSUB?left-right:left+right);}
                else if(op==ISHL){int right=((Number)pop(stack)).intValue(),left=((Number)pop(stack)).intValue();stack.add(left<<right);}
                else if(op==LADD){long right=((Number)pop(stack)).longValue(),left=((Number)pop(stack)).longValue();stack.add(left+right);}
                else if(op==DCMPG||op==DCMPL||op==LCMP){var right=(Number)pop(stack);var left=(Number)pop(stack);
                    assertTrue(Double.isFinite(left.doubleValue())&&Double.isFinite(right.doubleValue()));stack.add(Double.compare(left.doubleValue(),right.doubleValue()));}
                else if(op==IINC){var inc=(IincInsnNode)i;local[inc.var]=((Number)local[inc.var]).intValue()+inc.incr;}
                else if(op==GOTO)pc=insns.indexOf(((JumpInsnNode)i).label)-1;
                else if(op>=IFEQ&&op<=IFLE){int value=((Number)pop(stack)).intValue();boolean jump=switch(op){case IFEQ->value==0;case IFNE->value!=0;case IFLT->value<0;case IFGE->value>=0;case IFGT->value>0;default->value<=0;};
                    if(jump)pc=insns.indexOf(((JumpInsnNode)i).label)-1;}
                else if(op>=IF_ICMPEQ&&op<=IF_ICMPLE){int right=((Number)pop(stack)).intValue(),left=((Number)pop(stack)).intValue();boolean jump=switch(op){case IF_ICMPEQ->left==right;case IF_ICMPNE->left!=right;case IF_ICMPLT->left<right;case IF_ICMPGE->left>=right;case IF_ICMPGT->left>right;default->left<=right;};
                    if(jump)pc=insns.indexOf(((JumpInsnNode)i).label)-1;}
                else if(op==IFNULL||op==IFNONNULL){boolean nil=pop(stack)==null;if(op==IFNULL?nil:!nil)pc=insns.indexOf(((JumpInsnNode)i).label)-1;}
                else if(op==INVOKESTATIC||op==INVOKESPECIAL||op==INVOKEVIRTUAL){
                    var call=(MethodInsnNode)i;assertFalse(call.itf);var args=new ArrayList<Object>();
                    for(int n=Type.getArgumentTypes(call.desc).length;n>0;n--)args.add(0,pop(stack));
                    Value receiver=op==INVOKESTATIC?null:(Value)pop(stack);
                    Object result=call(call,receiver,args,depth+1);
                    if(Type.getReturnType(call.desc).getSort()!=Type.VOID)stack.add(result);
                }else if(op==ARETURN||op==IRETURN||op==DRETURN){Object result=pop(stack);assertTrue(stack.isEmpty());return result;}
                else if(op==RETURN){assertTrue(stack.isEmpty());return null;}
                else throw new AssertionError("Unreviewed reached opcode "+op+" in "+owner+"."+name);
            }throw new AssertionError("Native symbolic path did not return within budget: "+owner+"."+name);
        }
        Object call(MethodInsnNode m,Value receiver,List<Object> args,int depth)throws Exception{
            if(m.owner.equals("java/lang/Object")&&m.name.equals("<init>")&&m.desc.equals("()V"))return null;
            if(m.owner.equals("java/lang/Math")){
                double a=((Number)args.get(0)).doubleValue();
                return switch(m.name+m.desc){case "round(D)J"->Math.round(a);case "floor(D)D"->Math.floor(a);case "abs(D)D"->Math.abs(a);
                    case "min(DD)D"->Math.min(a,((Number)args.get(1)).doubleValue());case "max(DD)D"->Math.max(a,((Number)args.get(1)).doubleValue());
                    default->throw new AssertionError("Unknown Math intrinsic "+m.name+m.desc);};
            }
            if(m.owner.equals(grid)&&m.name.equals("<init>")){
                assertEquals(old?"(IIIIIIIII)V":"(III)V",m.desc);assertEquals(grid,receiver.type);
                receiver.fields.put("dimensions",List.copyOf(args.subList(0,3)));
                if(old){String[] names={"xMin","yMin","zMin","xMax","yMax","zMax"};for(int n=0;n<6;n++)receiver.fields.put(names[n],args.get(n+3));}
                return null; // Explicit recording boundary: the native grid constructor is NOT executed.
            }
            if(m.owner.equals(grid)&&m.name.equals(old?"setFull":"fillUpdateBounds")){
                assertEquals(old?"(IIIZZ)V":"(IIIZ)V",m.desc);assertEquals(0,args.get(3));if(old)assertEquals(1,args.get(4));
                var cell=List.of((Integer)args.get(0),(Integer)args.get(1),(Integer)args.get(2));assertFalse(receiver.fills.contains(cell));receiver.fills.add(cell);
                return null; // Record exact fill request, not native bitset/collision behavior.
            }
            if(m.owner.equals(shape)&&m.name.equals("isEmpty")&&m.desc.equals("()Z"))return Objects.requireNonNull(receiver.fields.get("assumedEmpty"));
            if(m.owner.equals(shape)&&m.name.equals("bounds")&&m.desc.equals("()L"+aabb+";"))return Objects.requireNonNull(receiver.fields.get("assumedBounds"));
            boolean allowed=m.owner.equals(block)&&m.name.equals("box")||m.owner.equals(shapes)&&Set.of("box","create","findBits").contains(m.name)
                    ||m.owner.equals(aabb)&&Set.of("<init>","getSize","getXsize","getYsize","getZsize").contains(m.name)
                    ||m.owner.equals(cube)&&m.name.equals("<init>")||m.owner.equals(shape)&&m.name.equals("<init>")
                    ||m.owner.equals(grid)&&m.name.equals("withFilledBounds");
            assertTrue(allowed,"Unreviewed native call "+m.owner+"."+m.name+m.desc);
            return evaluate(m.owner,m.name,m.desc,receiver,args,depth);
        }
        MethodNode method(String owner,String name,String desc)throws Exception{
            if(!nodes.containsKey(owner)){
                var entry=Objects.requireNonNull(zip.getEntry(owner+".class"),owner);assertTrue(entry.getSize()>0&&entry.getSize()<=2*1024*1024);
                try(var in=zip.getInputStream(entry)){byte[] bytes=in.readNBytes(2*1024*1024+1);assertTrue(bytes.length<=2*1024*1024);
                    var node=new ClassNode();new ClassReader(bytes).accept(node,0);nodes.put(owner,node);}
            }
            return nodes.get(owner).methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).findFirst().orElseThrow();
        }
        @Override public void close()throws Exception{zip.close();}
    }
    private static List<AbstractInsnNode> code(MethodNode m){return Arrays.stream(m.instructions.toArray()).filter(i->i.getOpcode()>=0).toList();}
    private static Object pop(List<Object> values){return values.remove(values.size()-1);}
}
