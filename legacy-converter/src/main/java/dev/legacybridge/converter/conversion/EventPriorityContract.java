package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.conversion.extract.ContentPlan;
import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.transformation.LegacyReferenceScanner;
import dev.legacybridge.converter.transformation.TransformationException;
import java.util.*;
import java.util.function.Predicate;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.objectweb.asm.Opcodes.*;

/**
 * Legacy API: EventBus 4 explicit listeners and an owned @Cancelable Event.
 * Target API: EventBus 6.2.33, same HIGH/LOW listeners and common-setup queue.
 * Transformation: prove the finite effect graph; retain all bodies via exact remapping.
 * Behavior differences: target MONITOR and queue exception aggregation are NOT generalized.
 * Known limitations: four owned final classes, no payload/overrides, third-party listeners,
 * results, unregister, registry reads, client access, arbitrary callbacks or runtime evidence.
 */
public final class EventPriorityContract {
    public static final String EVENT = "net/minecraftforge/eventbus/api/Event";
    public static final String CANCELABLE = "net/minecraftforge/eventbus/api/Cancelable";
    public static final String PRIORITY = "net/minecraftforge/eventbus/api/EventPriority";
    public static final String BUS = "net/minecraftforge/eventbus/api/IEventBus";
    public static final String FORGE = "net/minecraftforge/common/MinecraftForge";
    public static final String SETUP = "net/minecraftforge/fml/event/lifecycle/FMLCommonSetupEvent";
    private static final String CONTEXT = "net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext";
    private static final String MOD = "Lnet/minecraftforge/fml/common/Mod;";
    private static final String ADD = "(L" + PRIORITY + ";ZLjava/lang/Class;Ljava/util/function/Consumer;)V";
    private static final String BSM = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;";
    private EventPriorityContract() { }

    public record Proof(String entry, String item, String event, String flow, String setup,
                        String install, String run, String high, String low, String trace,
                        String cancelled, boolean receiveCancelled) {
        public Map<String,Object> toTree() {
            var t = new TreeMap<String,Object>();
            t.put("schemaVersion", 1); t.put("contract", "event-priority-cancellation-v1");
            t.put("roles", Map.of("entry",entry,"item",item,"event",event,"flow",flow));
            t.put("setup", Map.of("bus","MOD","phase","FMLCommonSetupEvent","priority","NORMAL",
                    "receiveCancelled",false,"callback",setup,"work",run,"dispatch","enqueueWork"));
            t.put("listeners", List.of(Map.of("bus","FORGE","priority","HIGH","receiveCancelled",false,"method",high),
                    Map.of("bus","FORGE","priority","LOW","receiveCancelled",receiveCancelled,"method",low)));
            t.put("installation",install); t.put("state",Map.of("trace",trace,"cancelled",cancelled));
            t.put("isolatedOracle", Map.of("beforeWorkTrace",0,"afterWorkTrace",receiveCancelled ? 12 : 1,
                    "postReturnsCancelled",true,"qualification","EXPECTED_NOT_EXECUTED"));
            t.put("runtimeValidation","NOT_RUN"); t.put("classesExecuted",false);
            return Collections.unmodifiableMap(t);
        }
    }

    /** Re-derive roles and every event effect from the actual trees, never a caller's certificate. */
    public static Proof inspect(Map<String,ClassNode> nodes) {
        require(nodes != null && nodes.size() == 4, "Exactly four owned classes");
        for (var e : nodes.entrySet()) {
            OperationControl.checkpoint();
            var n=e.getValue();
            require(n != null && e.getKey().equals(n.name) && !LegacyReferenceScanner.isProtectedDefinition(n.name),
                    "Owned class identity");
            require(n.version == V1_8 && n.access == (ACC_PUBLIC|ACC_FINAL|ACC_SUPER) && n.interfaces.isEmpty(),
                    "Only final public Java 8 concrete classes");
            require(n.methods.size() <= 8 && n.fields.size() <= 3, "Member bound");
            var members=new HashSet<String>();
            for (var m:n.methods) {
                require(members.add(m.name+m.desc) && m.instructions.size() <= 128, "Method/instruction bound");
                require(m.tryCatchBlocks.isEmpty() && m.exceptions.isEmpty(), "No event exceptions/handlers");
                require(empty(m.visibleAnnotations) && empty(m.invisibleAnnotations), "No implicit subscribers");
                for(var i:m.instructions) require(!(i instanceof JumpInsnNode || i instanceof TableSwitchInsnNode
                        || i instanceof LookupSwitchInsnNode), "No hidden event control flow");
            }
            members.clear();
            for(var f:n.fields) require(members.add(f.name+f.desc), "Duplicate field");
        }
        var entry=one(nodes,n->annotations(n).stream().anyMatch(a->MOD.equals(a.desc)),"entry");
        var event=one(nodes,n->EVENT.equals(n.superName),"event");
        var item=one(nodes,n->Set.of("net/minecraft/item/Item","net/minecraft/world/item/Item").contains(n.superName),"item");
        var flow=one(nodes,n->n!=entry && n!=event && n!=item,"flow");
        require("java/lang/Object".equals(entry.superName) && "java/lang/Object".equals(flow.superName), "Owned Object roles");
        require(annotations(entry).size()==1 && entry.visibleAnnotations != null && entry.visibleAnnotations.size()==1
                && empty(entry.invisibleAnnotations) && annotations(item).isEmpty() && annotations(flow).isEmpty(), "Runtime-visible Mod and empty helper annotations");
        require(annotations(event).size()==1 && event.visibleAnnotations != null && event.visibleAnnotations.size()==1
                && empty(event.invisibleAnnotations) && annotations(event).get(0).desc.equals("L"+CANCELABLE+";")
                && empty(annotations(event).get(0).values), "Owned event must have only @Cancelable");
        require(event.fields.isEmpty() && event.methods.size()==1, "Event payload/override/initializer unsupported");
        var eventCtor=method(event,"<init>","()V",ACC_PUBLIC);
        exact(eventCtor, v(ALOAD,0), m(INVOKESPECIAL,EVENT,"<init>","()V"), op(RETURN));
        require(item.fields.isEmpty() && item.methods.size()==1 && code(method(item,"<init>","()V",ACC_PUBLIC)).size()==8,
                "Plain native item constructor only");
        require(flow.fields.size()==2 && flow.methods.size()==7, "Finite event helper only");
        var trace=fieldOf(flow,"I"); var cancelled=fieldOf(flow,"Z");
        for(var f:flow.fields) require(f.access==(ACC_PRIVATE|ACC_STATIC) && f.value==null, "Zero-initialized private primitive state");
        exact(method(flow,"<init>","()V",ACC_PRIVATE),v(ALOAD,0),m(INVOKESPECIAL,"java/lang/Object","<init>","()V"),op(RETURN));
        require(entry.fields.size()==3 && entry.methods.size()==3, "Registration, constructor and one setup callback only");
        var ctor=method(entry,"<init>","()V",ACC_PUBLIC);
        var c=code(ctor); require(c.size()==15, "Constructor registration/listener/install envelope");
        same(c.get(6),m(INVOKESTATIC,CONTEXT,"get","()L"+CONTEXT+";"));
        same(c.get(7),m(INVOKEVIRTUAL,CONTEXT,"getModEventBus","()L"+BUS+";"));
        same(c.get(8),f(GETSTATIC,PRIORITY,"NORMAL","L"+PRIORITY+";")); same(c.get(9),op(ICONST_0));
        same(c.get(10),ldc(Type.getObjectType(SETUP)));
        Handle setup=callback(c.get(11),"java/util/function/Consumer","accept","(Ljava/lang/Object;)V","(L"+SETUP+";)V",entry.name);
        same(c.get(12),m(INVOKEINTERFACE,BUS,"addListener",ADD));
        require(c.get(13) instanceof MethodInsnNode,"Explicit helper installation required");
        var installCall=(MethodInsnNode)c.get(13);
        same(installCall,m(INVOKESTATIC,flow.name,installCall.name,"()V")); same(c.get(14),op(RETURN));
        var setupMethod=method(entry,setup.getName(),setup.getDesc(),ACC_PRIVATE|ACC_STATIC);
        var s=code(setupMethod); require(s.size()==5,"Only enqueued work in common setup");
        same(s.get(0),v(ALOAD,0));
        Handle run=callback(s.get(1),"java/lang/Runnable","run","()V","()V",flow.name);
        same(s.get(2),m(INVOKEVIRTUAL,SETUP,"enqueueWork","(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;"));
        same(s.get(3),op(POP)); same(s.get(4),op(RETURN));
        var install=method(flow,installCall.name,"()V",ACC_PUBLIC|ACC_STATIC);
        var registrations=code(install); require(registrations.size()==13,"Exactly two listener registrations");
        Handle high=listener(registrations,0,event.name,flow.name,"HIGH",false);
        int flag=registrations.get(8).getOpcode(); require(flag==ICONST_0 || flag==ICONST_1,"Literal receiveCancelled");
        boolean receive=flag==ICONST_1;
        Handle low=listener(registrations,6,event.name,flow.name,"LOW",receive);
        same(registrations.get(12),op(RETURN));
        require(!high.getName().equals(low.getName()) && !run.getName().equals(install.name), "Distinct callback roles");
        var h=method(flow,high.getName(),high.getDesc(),ACC_PRIVATE|ACC_STATIC);
        var l=method(flow,low.getName(),low.getDesc(),ACC_PRIVATE|ACC_STATIC);
        exact(h,f(GETSTATIC,flow.name,trace.name,"I"),integer(10),op(IMUL),op(ICONST_1),op(IADD),
                f(PUTSTATIC,flow.name,trace.name,"I"),v(ALOAD,0),op(ICONST_1),m(INVOKEVIRTUAL,EVENT,"setCanceled","(Z)V"),op(RETURN));
        exact(l,f(GETSTATIC,flow.name,trace.name,"I"),integer(10),op(IMUL),op(ICONST_2),op(IADD),
                f(PUTSTATIC,flow.name,trace.name,"I"),op(RETURN));
        var work=method(flow,run.getName(),"()V",ACC_PUBLIC|ACC_STATIC);
        exact(work,f(GETSTATIC,FORGE,"EVENT_BUS","L"+BUS+";"),type(NEW,event.name),op(DUP),
                m(INVOKESPECIAL,event.name,"<init>","()V"),m(INVOKEINTERFACE,BUS,"post","(L"+EVENT+";)Z"),
                f(PUTSTATIC,flow.name,cancelled.name,"Z"),op(RETURN));
        var getters=flow.methods.stream().filter(x->x.desc.equals("()I")||x.desc.equals("()Z")).toList();
        require(getters.size()==2 && getters.stream().map(g->g.desc).collect(java.util.stream.Collectors.toSet()).equals(Set.of("()I","()Z")),"Exactly two read-only observations");
        for(var g:getters) {
            require(g.access==(ACC_PUBLIC|ACC_STATIC),"Public static getter");
            var f=g.desc.equals("()I") ? trace : cancelled;
            exact(g,f(GETSTATIC,flow.name,f.name,f.desc),op(IRETURN));
        }
        return new Proof(entry.name,item.name,event.name,flow.name,setup.getName(),install.name,
                run.getName(),high.getName(),low.getName(),trace.name,cancelled.name,receive);
    }

    static ContentPlan validate(Map<String,ClassNode> nodes,String modId,String version) {
        var proof=inspect(nodes);
        ClassNode projected=new ClassNode(); nodes.get(proof.entry()).accept(projected);
        projected.methods.removeIf(m->!m.name.equals("<init>")&&!m.name.equals("<clinit>"));
        var ctor=method(projected,"<init>","()V",ACC_PUBLIC);
        while(code(ctor).size()>6) ctor.instructions.remove(ctor.instructions.getLast());
        ctor.instructions.add(new InsnNode(RETURN));
        // Admission-only view; emitted nodes ALWAYS retain all methods and instructions.
        return LogicProfileShape.validate(Map.of(projected.name,projected,proof.item(),nodes.get(proof.item())),modId,version);
    }

    /** Exact SAM, no captured arguments, after independent event-role proof. */
    public static boolean accepts(String event,String contract,String name,Type sam,Type instantiated,Type factory) {
        return contract.equals("java/util/function/Consumer") && name.equals("accept")
                && factory.getArgumentTypes().length==0 && sam.getDescriptor().equals("(Ljava/lang/Object;)V")
                && instantiated.getDescriptor().equals("(L"+event+";)V");
    }
    private static Handle listener(List<AbstractInsnNode> c,int offset,String event,String flow,String priority,boolean receive) {
        same(c.get(offset),f(GETSTATIC,FORGE,"EVENT_BUS","L"+BUS+";"));
        same(c.get(offset+1),f(GETSTATIC,PRIORITY,priority,"L"+PRIORITY+";"));
        same(c.get(offset+2),op(receive?ICONST_1:ICONST_0));
        same(c.get(offset+3),ldc(Type.getObjectType(event)));
        Handle h=callback(c.get(offset+4),"java/util/function/Consumer","accept","(Ljava/lang/Object;)V","(L"+event+";)V",flow);
        same(c.get(offset+5),m(INVOKEINTERFACE,BUS,"addListener",ADD)); return h;
    }
    private static Handle callback(AbstractInsnNode i,String contract,String name,String sam,String instantiated,String owner) {
        require(i instanceof InvokeDynamicInsnNode,"Explicit lambda required"); var d=(InvokeDynamicInsnNode)i;
        require(d.name.equals(name)&&d.desc.equals("()L"+contract+";")
                && d.bsm.equals(new Handle(H_INVOKESTATIC,"java/lang/invoke/LambdaMetafactory","metafactory",BSM,false))
                && d.bsmArgs.length==3 && Type.getMethodType(sam).equals(d.bsmArgs[0])
                && Type.getMethodType(instantiated).equals(d.bsmArgs[2]) && d.bsmArgs[1] instanceof Handle,"Exact zero-capture metafactory");
        var h=(Handle)d.bsmArgs[1];
        require(h.getTag()==H_INVOKESTATIC && !h.isInterface() && h.getOwner().equals(owner)
                && h.getDesc().equals(instantiated),"Owned static callback"); return h;
    }
    private static ClassNode one(Map<String,ClassNode> nodes,Predicate<ClassNode> match,String role) {
        var values=nodes.values().stream().filter(match).toList(); require(values.size()==1,"Unique "+role); return values.get(0);
    }
    private static FieldNode fieldOf(ClassNode n,String desc) {
        var fields=n.fields.stream().filter(f->f.desc.equals(desc)).toList(); require(fields.size()==1,"Unique state "+desc); return fields.get(0);
    }
    private static MethodNode method(ClassNode n,String name,String desc,int access) {
        var found=n.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).toList();
        require(found.size()==1 && found.get(0).access==access,"Exact method "+n.name+"."+name+desc); return found.get(0);
    }
    private static List<AnnotationNode> annotations(ClassNode n) {
        var result=new ArrayList<AnnotationNode>(); if(n.visibleAnnotations!=null)result.addAll(n.visibleAnnotations);
        if(n.invisibleAnnotations!=null)result.addAll(n.invisibleAnnotations); return result;
    }
    private static boolean empty(List<?> xs) { return xs==null||xs.isEmpty(); }
    private static List<AbstractInsnNode> code(MethodNode m) {
        var out=new ArrayList<AbstractInsnNode>(); for(var i:m.instructions)if(i.getOpcode()>=0)out.add(i); return out;
    }
    private static void exact(MethodNode method,String... expected) {
        var c=code(method); require(c.size()==expected.length,"Unexpected effects in "+method.name);
        for(int i=0;i<expected.length;i++)same(c.get(i),expected[i]);
    }
    private static void same(AbstractInsnNode node,String expected) { require(token(node).equals(expected),"Effect mismatch: "+token(node)+" != "+expected); }
    private static String token(AbstractInsnNode i) {
        if(i instanceof MethodInsnNode n)return m(n.getOpcode(),n.owner,n.name,n.desc)+(n.itf!=(n.getOpcode()==INVOKEINTERFACE)?" INVALID_ITF":"");
        if(i instanceof FieldInsnNode n)return f(n.getOpcode(),n.owner,n.name,n.desc);
        if(i instanceof VarInsnNode n)return v(n.getOpcode(),n.var);
        if(i instanceof LdcInsnNode n)return ldc(n.cst);
        if(i instanceof IntInsnNode n)return n.getOpcode()+":"+n.operand;
        if(i instanceof TypeInsnNode n)return type(n.getOpcode(),n.desc);
        if(i instanceof InsnNode)return op(i.getOpcode());
        return "UNSUPPORTED:"+i.getClass().getSimpleName();
    }
    private static String op(int opcode){return Integer.toString(opcode);}
    private static String m(int opcode,String owner,String name,String desc){return opcode+":"+owner+"."+name+desc;}
    private static String f(int opcode,String owner,String name,String desc){return opcode+":"+owner+"."+name+":"+desc;}
    private static String v(int opcode,int index){return opcode+":"+index;}
    private static String type(int opcode,String name){return opcode+":"+name;}
    private static String integer(int n){return BIPUSH+":"+n;}
    private static String ldc(Object value){return LDC+":"+value;}
    private static void require(boolean ok,String reason) { if(!ok)throw new TransformationException("EVENT_PRIORITY_UNSUPPORTED: "+reason); }
}
