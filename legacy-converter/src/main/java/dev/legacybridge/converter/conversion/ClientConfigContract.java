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
 * Legacy API: Forge 36 CLIENT spec, guarded registration and queued client setup read.
 * Target API: Forge 47 CLIENT spec, same physical-side and config-load phase boundaries.
 * Transformation: retain bodies; prove every guard, effect, native default and validator.
 * Behavior differences: pre-load get semantics differ; no pre-load read is admitted.
 * Known limitations: a single boolean/int startup snapshot, not reload, SERVER, sync,
 * arbitrary config hooks, client game classes, or native behavioral qualification.
 */
public final class ClientConfigContract {
    public static final String SETUP = "net/minecraftforge/fml/event/lifecycle/FMLClientSetupEvent";
    public static final String ENV = "net/minecraftforge/fml/loading/FMLEnvironment";
    public static final String DIST = "net/minecraftforge/api/distmarker/Dist";
    private static final String SPEC = "net/minecraftforge/common/ForgeConfigSpec";
    private static final String CONFIG = "net/minecraftforge/fml/config/ModConfig$Type";
    private static final String LOADING = "net/minecraftforge/fml/ModLoadingContext";
    private static final String CONTEXT = "net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext";
    private static final String BUS = "net/minecraftforge/eventbus/api/IEventBus";
    private static final String PRIORITY = "net/minecraftforge/eventbus/api/EventPriority";
    private static final String MOD = "Lnet/minecraftforge/fml/common/Mod;";
    private static final String BSM = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;";
    private ClientConfigContract() {}

    public record Proof(String entry, String item, String config, String hooks,
                        String install, String setup, String read, String enabled, String factor, String loads,
                        String booleanKey, boolean booleanDefault, String integerKey, int integerDefault, int minimum, int maximum) {
        public Map<String,Object> toTree() {
            return Map.of("schemaVersion",1,"contract","client-config-scope-v1",
                "roles",Map.of("entry",entry,"item",item,"config",config,"hooks",hooks),
                "guard",Map.of("physicalDist","CLIENT","nonClient","SKIP_CONFIG_AND_HOOKS"),
                "registration",Map.of("method",install,"scope","CLIENT","sync",false,"path","config/<modId>-client.toml"),
                "read",Map.of("setup",setup,"work",read,"bus","MOD","priority","NORMAL","receiveCancelled",false,
                    "phase","FMLClientSetupEvent","thread","enqueueWork","reload","NOT_HANDLED_STARTUP_SNAPSHOT"),
                "schema",Map.of("boolean",Map.of("key",booleanKey,"default",booleanDefault),
                    "integer",Map.of("key",integerKey,"default",integerDefault,"min",minimum,"max",maximum)),
                "isolatedOracle",Map.of("enabledField",enabled,"factorField",factor,"loadsField",loads,
                    "freshClient",Map.of("enabled",booleanDefault,"factor",integerDefault,"loads",1),
                    "dedicatedServer","NO_CLIENT_REGISTRATION_OR_READ","qualification","EXPECTED_NOT_EXECUTED"),
                "runtimeValidation","NOT_RUN","classesExecuted",false);
        }
    }
    public static Proof inspect(Map<String,ClassNode> nodes) {
        require(nodes!=null && nodes.size()==4,"Exactly four owned classes");
        for(var e:nodes.entrySet()) {
            OperationControl.checkpoint(); var n=e.getValue();
            require(n!=null && e.getKey().equals(n.name) && !LegacyReferenceScanner.isProtectedDefinition(n.name),"Owned identity");
            require(n.version==V1_8 && n.access==(ACC_PUBLIC|ACC_FINAL|ACC_SUPER) && n.interfaces.isEmpty(),"Final public Java 8 classes");
            require(n.fields.size()<=3 && n.methods.size()<=7,"Member bound");
            var seen=new HashSet<String>();
            for(var m:n.methods) {
                require(seen.add(m.name+m.desc) && m.instructions.size()<=128,"Method bound");
                require(m.tryCatchBlocks.isEmpty() && m.exceptions.isEmpty()
                    && empty(m.visibleAnnotations) && empty(m.invisibleAnnotations),"No handlers or subscriber annotations");
                for(var i:m.instructions) require(!(i instanceof TableSwitchInsnNode || i instanceof LookupSwitchInsnNode),"No switches");
            }
            seen.clear();
            for(var f:n.fields) require(seen.add(f.name+f.desc) && empty(f.visibleAnnotations) && empty(f.invisibleAnnotations),"Plain unique fields");
        }
        var entry=one(nodes,n->annotations(n).stream().anyMatch(a->MOD.equals(a.desc)),"entry");
        var item=one(nodes,n->Set.of("net/minecraft/item/Item","net/minecraft/world/item/Item").contains(n.superName),"item");
        var config=one(nodes,n->n.fields.stream().anyMatch(f->f.desc.equals("L"+SPEC+";")),"config");
        var hooks=one(nodes,n->n!=entry && n!=item && n!=config,"hooks");
        require(new HashSet<>(List.of(entry.name,item.name,config.name,hooks.name)).size()==4,"Distinct roles");
        for(var n:List.of(entry,config,hooks)) require("java/lang/Object".equals(n.superName),"Only Object helpers");
        require(annotations(entry).size()==1 && entry.visibleAnnotations!=null && entry.visibleAnnotations.size()==1
            && empty(entry.invisibleAnnotations) && annotations(item).isEmpty() && annotations(config).isEmpty() && annotations(hooks).isEmpty(),"Only runtime-visible Mod");
        require(entry.fields.size()==3 && entry.methods.size()==2,"Only registration and guarded constructor");
        var ctor=method(entry,"<init>","()V",ACC_PUBLIC);
        var c=code(ctor); require(c.size()==11,"Single guarded installation envelope");
        same(c.get(6),f(GETSTATIC,ENV,"dist","L"+DIST+";"));
        same(c.get(7),f(GETSTATIC,DIST,"CLIENT","L"+DIST+";"));
        require(c.get(8) instanceof JumpInsnNode && c.get(8).getOpcode()==IF_ACMPNE,"Exact physical CLIENT guard");
        var jump=(JumpInsnNode)c.get(8); var target=(AbstractInsnNode)jump.label;
        require(ctor.instructions.indexOf(target)>ctor.instructions.indexOf(c.get(9)),"Forward guard");
        while(target!=null && target.getOpcode()<0)target=target.getNext();
        require(target==c.get(10),"Non-client branch must bypass all config effects");
        require(c.get(9) instanceof MethodInsnNode,"Owned install call"); var installCall=(MethodInsnNode)c.get(9);
        same(installCall,m(INVOKESTATIC,hooks.name,installCall.name,"()V")); same(c.get(10),op(RETURN));
        for(var n:nodes.values())for(var method:n.methods)for(var i:method.instructions)
            require(!(i instanceof JumpInsnNode) || method==ctor && i==jump,"No other hidden control flow");
        require(item.fields.isEmpty() && item.methods.size()==1 && code(method(item,"<init>","()V",ACC_PUBLIC)).size()==8,"Plain item only");
        registrationEnvelope(entry,item,c);
        var cfg=LifecycleProfileShape.validateConfig(config);
        require(cfg.spec().access==(ACC_PUBLIC|ACC_STATIC|ACC_FINAL),"Public final spec");
        for(var f:config.fields)if(f!=cfg.spec()) require(f.access==(ACC_PRIVATE|ACC_STATIC|ACC_FINAL),"Private native config values");
        method(config,"<init>","()V",ACC_PRIVATE); method(config,"<clinit>","()V",ACC_STATIC);
        method(config,cfg.enabled().name,"()Z",ACC_PUBLIC|ACC_STATIC);
        method(config,cfg.factor().name,"()I",ACC_PUBLIC|ACC_STATIC);
        require(hooks.fields.size()==3 && hooks.methods.size()==7,"Finite startup observations");
        for(var f:hooks.fields)require(f.access==(ACC_PRIVATE|ACC_STATIC) && f.value==null,"Private per-class startup observations");
        exact(method(hooks,"<init>","()V",ACC_PRIVATE),v(ALOAD,0),m(INVOKESPECIAL,"java/lang/Object","<init>","()V"),op(RETURN));
        var install=method(hooks,installCall.name,"()V",ACC_PUBLIC|ACC_STATIC);
        var ins=code(install); require(ins.size()==12,"Only CLIENT registration and explicit MOD listener");
        same(ins.get(0),m(INVOKESTATIC,LOADING,"get","()L"+LOADING+";"));
        same(ins.get(1),f(GETSTATIC,CONFIG,"CLIENT","L"+CONFIG+";"));
        same(ins.get(2),f(GETSTATIC,config.name,cfg.spec().name,cfg.spec().desc));
        String parameter=item.superName.equals("net/minecraft/item/Item")?SPEC:"net/minecraftforge/fml/config/IConfigSpec";
        same(ins.get(3),m(INVOKEVIRTUAL,LOADING,"registerConfig","(L"+CONFIG+";L"+parameter+";)V"));
        same(ins.get(4),m(INVOKESTATIC,CONTEXT,"get","()L"+CONTEXT+";"));
        same(ins.get(5),m(INVOKEVIRTUAL,CONTEXT,"getModEventBus","()L"+BUS+";"));
        same(ins.get(6),f(GETSTATIC,PRIORITY,"NORMAL","L"+PRIORITY+";")); same(ins.get(7),op(ICONST_0));
        same(ins.get(8),ldc(Type.getObjectType(SETUP)));
        var setup=callback(ins.get(9),"java/util/function/Consumer","accept","(Ljava/lang/Object;)V","(L"+SETUP+";)V",hooks.name);
        same(ins.get(10),m(INVOKEINTERFACE,BUS,"addListener","(L"+PRIORITY+";ZLjava/lang/Class;Ljava/util/function/Consumer;)V")); same(ins.get(11),op(RETURN));
        var s=code(method(hooks,setup.getName(),setup.getDesc(),ACC_PRIVATE|ACC_STATIC)); require(s.size()==5,"Enqueued read only");
        same(s.get(0),v(ALOAD,0));
        var read=callback(s.get(1),"java/lang/Runnable","run","()V","()V",hooks.name);
        same(s.get(2),m(INVOKEVIRTUAL,SETUP,"enqueueWork","(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;"));
        same(s.get(3),op(POP)); same(s.get(4),op(RETURN));
        var r=code(method(hooks,read.getName(),"()V",ACC_PRIVATE|ACC_STATIC)); require(r.size()==9,"Only config read and count");
        require(r.get(1) instanceof FieldInsnNode && r.get(3) instanceof FieldInsnNode && r.get(4) instanceof FieldInsnNode,"Owned state");
        String enabled=((FieldInsnNode)r.get(1)).name, factor=((FieldInsnNode)r.get(3)).name, loads=((FieldInsnNode)r.get(4)).name;
        require(new HashSet<>(List.of(enabled,factor,loads)).size()==3,"Distinct observation fields");
        require(hooks.fields.stream().allMatch(f->(f.name.equals(enabled)&&f.desc.equals("Z"))
            ||(Set.of(factor,loads).contains(f.name)&&f.desc.equals("I"))),"Exactly typed state");
        exact(method(hooks,read.getName(),"()V",ACC_PRIVATE|ACC_STATIC),
            m(INVOKESTATIC,config.name,cfg.enabled().name,"()Z"),f(PUTSTATIC,hooks.name,enabled,"Z"),
            m(INVOKESTATIC,config.name,cfg.factor().name,"()I"),f(PUTSTATIC,hooks.name,factor,"I"),
            f(GETSTATIC,hooks.name,loads,"I"),op(ICONST_1),op(IADD),f(PUTSTATIC,hooks.name,loads,"I"),op(RETURN));
        var getters=hooks.methods.stream().filter(m->m.desc.equals("()Z")||m.desc.equals("()I")).toList();
        require(getters.size()==3,"Three read-only observations"); var observed=new HashSet<String>();
        for(var g:getters) {
            require(g.access==(ACC_PUBLIC|ACC_STATIC) && code(g).size()==2 && code(g).get(0) instanceof FieldInsnNode,"Pure getter");
            var f=(FieldInsnNode)code(g).get(0); require(observed.add(f.name),"No duplicate observation");
            exact(g,f(GETSTATIC,hooks.name,f.name,g.desc.substring(2)),op(IRETURN));
        }
        require(observed.equals(Set.of(enabled,factor,loads)),"Complete observations");
        var init=code(method(config,"<clinit>","()V",ACC_STATIC));
        return new Proof(entry.name,item.name,config.name,hooks.name,install.name,setup.getName(),read.getName(),
            enabled,factor,loads,(String)((LdcInsnNode)init.get(5)).cst,init.get(6).getOpcode()==ICONST_1,
            (String)((LdcInsnNode)init.get(10)).cst,literal(init.get(11)),literal(init.get(12)),literal(init.get(13)));
    }
    static void registrationEnvelope(ClassNode entry,ClassNode item,List<AbstractInsnNode> ctor) {
        String deferred="net/minecraftforge/registries/DeferredRegister", registry="net/minecraftforge/registries/IForgeRegistry";
        String object=item.superName.equals("net/minecraft/item/Item") ? "net/minecraftforge/fml/RegistryObject" : "net/minecraftforge/registries/RegistryObject";
        var register=fieldOf(entry,"L"+deferred+";");var token=fieldOf(entry,"L"+object+";");var identity=fieldOf(entry,"Ljava/lang/String;");
        for(var f:entry.fields)require(f.access==(ACC_PUBLIC|ACC_STATIC|ACC_FINAL),"Final public registration fields");
        require(register.value==null && token.value==null && identity.value instanceof String,"Literal mod identity, native registry fields");
        var init=code(method(entry,"<clinit>","()V",ACC_STATIC)); require(init.size()==10,"No early initialization effects");
        same(init.get(0),f(GETSTATIC,"net/minecraftforge/registries/ForgeRegistries","ITEMS","L"+registry+";"));
        same(init.get(1),ldc(identity.value));
        same(init.get(2),m(INVOKESTATIC,deferred,"create","(L"+registry+";Ljava/lang/String;)L"+deferred+";"));
        same(init.get(3),f(PUTSTATIC,entry.name,register.name,register.desc));
        same(init.get(4),f(GETSTATIC,entry.name,register.name,register.desc));
        require(init.get(5) instanceof LdcInsnNode l && l.cst instanceof String,"Literal registry name");
        require(init.get(6) instanceof InvokeDynamicInsnNode,"Exact item supplier");var d=(InvokeDynamicInsnNode)init.get(6);
        require(d.name.equals("get") && d.desc.equals("()Ljava/util/function/Supplier;")
            && d.bsm.equals(new Handle(H_INVOKESTATIC,"java/lang/invoke/LambdaMetafactory","metafactory",BSM,false))
            && Arrays.equals(d.bsmArgs,new Object[]{Type.getMethodType("()Ljava/lang/Object;"),
                new Handle(H_NEWINVOKESPECIAL,item.name,"<init>","()V",false),Type.getMethodType("()L"+item.superName+";")}),"Native item-only supplier");
        same(init.get(7),m(INVOKEVIRTUAL,deferred,"register","(Ljava/lang/String;Ljava/util/function/Supplier;)L"+object+";"));
        same(init.get(8),f(PUTSTATIC,entry.name,token.name,token.desc));same(init.get(9),op(RETURN));
        String[] prefix={v(ALOAD,0),m(INVOKESPECIAL,"java/lang/Object","<init>","()V"),
            f(GETSTATIC,entry.name,register.name,register.desc),m(INVOKESTATIC,CONTEXT,"get","()L"+CONTEXT+";"),
            m(INVOKEVIRTUAL,CONTEXT,"getModEventBus","()L"+BUS+";"),m(INVOKEVIRTUAL,deferred,"register","(L"+BUS+";)V")};
        for(int i=0;i<prefix.length;i++)same(ctor.get(i),prefix[i]);
        var c=code(method(item,"<init>","()V",ACC_PUBLIC));String properties=item.superName+"$Properties";
        same(c.get(0),v(ALOAD,0));same(c.get(1),type(NEW,properties));same(c.get(2),op(DUP));
        same(c.get(3),m(INVOKESPECIAL,properties,"<init>","()V"));
        int stack=literal(c.get(4));require(stack>=1 && stack<=64,"Finite stack size");
        require(c.get(5) instanceof MethodInsnNode,"Native item property, not owned config effects");
        var property=(MethodInsnNode)c.get(5);
        // The exact production member name is independently resolved from both real API indexes.
        same(property,m(INVOKEVIRTUAL,properties,property.name,"(I)L"+properties+";"));
        same(c.get(6),m(INVOKESPECIAL,item.superName,"<init>","(L"+properties+";)V"));same(c.get(7),op(RETURN));
    }
    static ContentPlan validate(Map<String,ClassNode> nodes,String modId,String version) {
        var proof=inspect(nodes);
        var projected=new ClassNode(); nodes.get(proof.entry()).accept(projected);
        var ctor=method(projected,"<init>","()V",ACC_PUBLIC);
        while(code(ctor).size()>6)ctor.instructions.remove(ctor.instructions.getLast());
        ctor.instructions.add(new InsnNode(RETURN));
        // Admission view only; transformation always retains original guarded bodies.
        return LogicProfileShape.validate(Map.of(projected.name,projected,proof.item(),nodes.get(proof.item())),modId,version);
    }
    public static boolean accepts(String contract,String name,Type sam,Type instantiated,Type factory) {
        return EventPriorityContract.accepts(SETUP,contract,name,sam,instantiated,factory);
    }
    static int literal(AbstractInsnNode i) {
        int opcode=i.getOpcode(); if(opcode>=ICONST_M1 && opcode<=ICONST_5)return opcode-ICONST_0;
        if(i instanceof IntInsnNode n && (opcode==BIPUSH || opcode==SIPUSH))return n.operand;
        if(i instanceof LdcInsnNode n && n.cst instanceof Integer v)return v;
        throw new TransformationException("CLIENT_CONFIG_UNSUPPORTED: integer literal required");
    }
    static Handle callback(AbstractInsnNode i,String contract,String name,String sam,String instantiated,String owner) {
        require(i instanceof InvokeDynamicInsnNode,"Explicit lambda required"); var d=(InvokeDynamicInsnNode)i;
        require(d.name.equals(name)&&d.desc.equals("()L"+contract+";")
                && d.bsm.equals(new Handle(H_INVOKESTATIC,"java/lang/invoke/LambdaMetafactory","metafactory",BSM,false))
                && d.bsmArgs.length==3 && Type.getMethodType(sam).equals(d.bsmArgs[0])
                && Type.getMethodType(instantiated).equals(d.bsmArgs[2]) && d.bsmArgs[1] instanceof Handle,"Exact zero-capture metafactory");
        var h=(Handle)d.bsmArgs[1];
        require(h.getTag()==H_INVOKESTATIC && !h.isInterface() && h.getOwner().equals(owner)
                && h.getDesc().equals(instantiated),"Owned static callback"); return h;
    }
    static ClassNode one(Map<String,ClassNode> nodes,Predicate<ClassNode> match,String role) {
        var values=nodes.values().stream().filter(match).toList(); require(values.size()==1,"Unique "+role); return values.get(0);
    }
    static FieldNode fieldOf(ClassNode n,String desc) {
        var fields=n.fields.stream().filter(f->f.desc.equals(desc)).toList(); require(fields.size()==1,"Unique state "+desc); return fields.get(0);
    }
    static MethodNode method(ClassNode n,String name,String desc,int access) {
        var found=n.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).toList();
        require(found.size()==1 && found.get(0).access==access,"Exact method "+n.name+"."+name+desc); return found.get(0);
    }
    static List<AnnotationNode> annotations(ClassNode n) {
        var result=new ArrayList<AnnotationNode>(); if(n.visibleAnnotations!=null)result.addAll(n.visibleAnnotations);
        if(n.invisibleAnnotations!=null)result.addAll(n.invisibleAnnotations); return result;
    }
    static boolean empty(List<?> xs) { return xs==null||xs.isEmpty(); }
    static List<AbstractInsnNode> code(MethodNode m) {
        var out=new ArrayList<AbstractInsnNode>(); for(var i:m.instructions)if(i.getOpcode()>=0)out.add(i); return out;
    }
    static void exact(MethodNode method,String... expected) {
        var c=code(method); require(c.size()==expected.length,"Unexpected effects in "+method.name);
        for(int i=0;i<expected.length;i++)same(c.get(i),expected[i]);
    }
    static void same(AbstractInsnNode node,String expected) { require(token(node).equals(expected),"Effect mismatch: "+token(node)+" != "+expected); }
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
    static String op(int opcode){return Integer.toString(opcode);}
    static String m(int opcode,String owner,String name,String desc){return opcode+":"+owner+"."+name+desc;}
    static String f(int opcode,String owner,String name,String desc){return opcode+":"+owner+"."+name+":"+desc;}
    static String v(int opcode,int index){return opcode+":"+index;}
    static String type(int opcode,String name){return opcode+":"+name;}
    private static String integer(int n){return BIPUSH+":"+n;}
    static String ldc(Object value){return LDC+":"+value;}
    private static void require(boolean ok,String reason) { if(!ok)throw new TransformationException("CLIENT_CONFIG_UNSUPPORTED: "+reason); }
}
