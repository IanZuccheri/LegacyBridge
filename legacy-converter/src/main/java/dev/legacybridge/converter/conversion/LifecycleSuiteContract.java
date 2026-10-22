package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.conversion.extract.ContentPlan;
import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.transformation.LegacyReferenceScanner;
import dev.legacybridge.converter.transformation.TransformationException;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.objectweb.asm.Opcodes.*;
import static dev.legacybridge.converter.conversion.ClientConfigContract.*;

/**
 * Legacy API: finite COMMON Loading/Reloading, queued late registry read, owned events and server commands.
 * Target API: native Forge 47 configuration events, EventBus 6 and Brigadier 1.1.8.
 * Transformation: prove the complete composition, preserve all seven original class bodies.
 * Behavior differences: getSpec returns IConfigSpec; only identity comparison with the owned spec is admitted.
 * Known limitations: no world effects, custom config sync, unloading, arbitrary subscriptions or runtime qualification.
 */
public final class LifecycleSuiteContract {
    public static final String CONFIG="net/minecraftforge/fml/config/ModConfig";
    public static final String OLD_EVENT=CONFIG+"$ModConfigEvent";
    public static final String NEW_EVENT="net/minecraftforge/fml/event/config/ModConfigEvent";
    public static final Map<String,String> EVENT_TYPES=Map.of(OLD_EVENT,NEW_EVENT,
        CONFIG+"$Loading",NEW_EVENT+"$Loading",CONFIG+"$Reloading",NEW_EVENT+"$Reloading");
    private static final String SPEC="net/minecraftforge/common/ForgeConfigSpec";
    private static final String CONTEXT="net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext";
    private static final String LOADING="net/minecraftforge/fml/ModLoadingContext";
    private static final String BUS=EventPriorityContract.BUS, PRIORITY=EventPriorityContract.PRIORITY;
    private static final String SUBSCRIBER="Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber;";
    private static final String SUBSCRIBE="Lnet/minecraftforge/eventbus/api/SubscribeEvent;";
    private static final String B="com/mojang/brigadier/", LIT=B+"builder/LiteralArgumentBuilder",
        ARG=B+"builder/ArgumentBuilder", REQ=B+"builder/RequiredArgumentBuilder", CTX=B+"context/CommandContext";
    private LifecycleSuiteContract() {}

    public record Proof(EventPriorityContract.Proof events, Map<String,Object> contract) {
        public Proof { contract=Collections.unmodifiableMap(new TreeMap<>(contract)); }
        public String entry(){return events.entry();}
        public String item(){return events.item();}
        public Map<String,Object> toTree(){return contract;}
    }
    public static Proof inspect(Map<String,ClassNode> nodes) {
        try {return graph(nodes);}
        catch(TransformationException e) {
            if(e.getMessage()!=null && e.getMessage().startsWith("CLIENT_CONFIG_UNSUPPORTED: "))
                throw new TransformationException("LIFECYCLE_SUITE_UNSUPPORTED: "+e.getMessage().substring(27),e);
            throw e;
        }
    }
    private static Proof graph(Map<String,ClassNode> nodes) {
        require(nodes!=null && nodes.size()==7,"Exactly seven owned classes");
        for(var e:nodes.entrySet()) {
            OperationControl.checkpoint(); var n=e.getValue();
            require(n!=null && e.getKey().equals(n.name) && !LegacyReferenceScanner.isProtectedDefinition(n.name),"Owned identity");
            require(n.version==V1_8 && n.access==(ACC_PUBLIC|ACC_FINAL|ACC_SUPER) && n.interfaces.isEmpty(),"Final Java 8 roles");
            require(n.fields.size()<=4 && n.methods.size()<=8,"Bounded graph");
            var seen=new HashSet<String>();
            for(var m:n.methods) {
                require(seen.add(m.name+m.desc) && m.instructions.size()<=160 && m.tryCatchBlocks.isEmpty() && m.exceptions.isEmpty(),"Finite methods without handlers");
                for(var i:m.instructions)require(!(i instanceof TableSwitchInsnNode || i instanceof LookupSwitchInsnNode),"No switches");
            }
            seen.clear();
            for(var f:n.fields)require(seen.add(f.name+f.desc) && empty(f.visibleAnnotations) && empty(f.invisibleAnnotations),"Unique plain fields");
        }
        var entry=one(nodes,n->annotations(n).stream().anyMatch(a->a.desc.equals("Lnet/minecraftforge/fml/common/Mod;")),"entry");
        var item=one(nodes,n->Set.of("net/minecraft/item/Item","net/minecraft/world/item/Item").contains(n.superName),"item");
        var event=one(nodes,n->EventPriorityContract.EVENT.equals(n.superName),"event");
        var config=one(nodes,n->n.fields.stream().anyMatch(f->f.desc.equals("L"+SPEC+";")),"config");
        var commands=one(nodes,n->annotations(n).stream().anyMatch(a->a.desc.equals(SUBSCRIBER)),"commands");
        var flow=one(nodes,n->n.methods.stream().flatMap(m->code(m).stream()).anyMatch(i->i instanceof MethodInsnNode call && call.owner.equals(BUS) && call.name.equals("post")),"event flow");
        var hooks=one(nodes,n->!List.of(entry,item,event,config,commands,flow).contains(n),"config hooks");
        require(new HashSet<>(List.of(entry,item,event,config,commands,flow,hooks)).size()==7,"Distinct roles");
        for(var n:List.of(config,commands,hooks))require(n.superName.equals("java/lang/Object"),"Object helpers");
        require(annotations(config).isEmpty() && annotations(hooks).isEmpty(),"No implicit config hooks");
        for(var n:nodes.values())for(var m:n.methods)if(n!=commands)require(empty(m.visibleAnnotations)&&empty(m.invisibleAnnotations),"Only explicit command subscriber");

        var cfg=LifecycleProfileShape.validateConfig(config);
        require(cfg.spec().access==(ACC_PUBLIC|ACC_STATIC|ACC_FINAL),"Owned immutable spec identity");
        for(var f:config.fields)if(f!=cfg.spec())require(f.access==(ACC_PRIVATE|ACC_STATIC|ACC_FINAL),"Native private values");
        exact(method(config,"<init>","()V",ACC_PRIVATE),v(ALOAD,0),m(INVOKESPECIAL,"java/lang/Object","<init>","()V"),op(RETURN));
        method(config,cfg.enabled().name,"()Z",ACC_PUBLIC|ACC_STATIC);
        method(config,cfg.factor().name,"()I",ACC_PUBLIC|ACC_STATIC);
        boolean legacy=item.superName.equals("net/minecraft/item/Item");
        var ctor=method(entry,"<init>","()V",ACC_PUBLIC);var c=code(ctor);
        require(entry.fields.size()==4 && entry.methods.size()==5 && c.size()==20,"Integrated constructor shape");
        same(c.get(6),m(INVOKESTATIC,LOADING,"get","()L"+LOADING+";"));
        same(c.get(7),f(GETSTATIC,CONFIG+"$Type","COMMON","L"+CONFIG+"$Type;"));
        same(c.get(8),f(GETSTATIC,config.name,cfg.spec().name,cfg.spec().desc));
        same(c.get(9),m(INVOKEVIRTUAL,LOADING,"registerConfig","(L"+CONFIG+"$Type;L"+(legacy?SPEC:"net/minecraftforge/fml/config/IConfigSpec")+";)V"));
        require(c.get(18) instanceof MethodInsnNode,"Config installation");
        var install=(MethodInsnNode)c.get(18);same(install,m(INVOKESTATIC,hooks.name,install.name,"()V"));
        var setupHandle=callback(c.get(15),"java/util/function/Consumer","accept","(Ljava/lang/Object;)V",
            "(L"+EventPriorityContract.SETUP+";)V",entry.name);
        var setup=method(entry,setupHandle.getName(),setupHandle.getDesc(),ACC_PRIVATE|ACC_STATIC);
        var setupCode=code(setup);require(setupCode.size()==5,"Queued setup");
        var workHandle=callback(setupCode.get(1),"java/lang/Runnable","run","()V","()V",entry.name);
        var work=method(entry,workHandle.getName(),"()V",ACC_PRIVATE|ACC_STATIC);var w=code(work);
        require(w.size()==6 && w.get(3) instanceof FieldInsnNode && w.get(4) instanceof MethodInsnNode,"Late registry read then event post");
        String object=legacy?"net/minecraftforge/fml/RegistryObject":"net/minecraftforge/registries/RegistryObject";
        var token=fieldOf(entry,"L"+object+";");var observed=fieldOf(entry,"L"+item.superName+";");
        require(observed.access==(ACC_PRIVATE|ACC_STATIC) && observed.value==null,"Private late item");
        var run=(MethodInsnNode)w.get(4);
        exact(work,f(GETSTATIC,entry.name,token.name,token.desc),m(INVOKEVIRTUAL,object,"get",legacy?"()Lnet/minecraftforge/registries/IForgeRegistryEntry;":"()Ljava/lang/Object;"),
            type(CHECKCAST,item.superName),f(PUTSTATIC,entry.name,observed.name,observed.desc),m(INVOKESTATIC,flow.name,run.name,"()V"),op(RETURN));
        var getters=entry.methods.stream().filter(x->x.desc.equals("()L"+item.superName+";")).toList();
        require(getters.size()==1,"Single late observation");
        var getter=method(entry,getters.get(0).name,getters.get(0).desc,ACC_PUBLIC|ACC_STATIC);
        exact(getter,f(GETSTATIC,entry.name,observed.name,observed.desc),op(ARETURN));

        // Admission-only view: all removed effects are proved above/below; emitted bytes are NEVER this projection.
        var projected=new ClassNode();entry.accept(projected);
        projected.fields.removeIf(f->f.name.equals(observed.name));
        projected.methods.removeIf(x->x.name.equals(work.name)&&x.desc.equals(work.desc) || x.name.equals(getter.name)&&x.desc.equals(getter.desc));
        var pc=method(projected,"<init>","()V",ACC_PUBLIC);var original=code(pc);
        for(int i:new int[]{6,7,8,9,18})pc.instructions.remove(original.get(i));
        var ps=method(projected,setup.name,setup.desc,setup.access);
        var projectedDynamic=(InvokeDynamicInsnNode)code(ps).get(1);
        // ASM tree accept retains the bootstrap argument array. Never mutate the original graph.
        projectedDynamic.bsmArgs=projectedDynamic.bsmArgs.clone();
        projectedDynamic.bsmArgs[1]=new Handle(H_INVOKESTATIC,flow.name,run.name,"()V",false);
        registrationEnvelope(projected,item,code(pc));
        var ep=EventPriorityContract.inspect(Map.of(projected.name,projected,item.name,item,event.name,event,flow.name,flow));
        var reload=reload(hooks,install.name,config,cfg,legacy);
        var cmd=commands(commands,entry,config,cfg,legacy);
        var init=code(method(config,"<clinit>","()V",ACC_STATIC));
        var t=new TreeMap<String,Object>();
        t.put("schemaVersion",1);t.put("contract","lifecycle-suite-v1");
        t.put("roles",Map.of("entry",entry.name,"item",item.name,"config",config.name,"hooks",hooks.name,"commands",commands.name));
        t.put("eventAdmissionView",Map.of("emitted",false,"proof",ep.toTree(),
            "actualQueuedOwner",entry.name,"actualQueuedWork",work.name,"actualWorkSequence",List.of("LATE_REGISTRY_READ","OWNED_EVENT_POST")));
        t.put("lateRegistry",Map.of("work",work.name,"field",observed.name,"phase","COMMON_SETUP_ENQUEUE_WORK"));
        t.put("configuration",reload);t.put("commands",cmd);
        t.put("schema",Map.of("booleanKey",((LdcInsnNode)init.get(5)).cst,"booleanDefault",literal(init.get(6))!=0,
            "integerKey",((LdcInsnNode)init.get(10)).cst,"integerDefault",literal(init.get(11)),"minimum",literal(init.get(12)),"maximum",literal(init.get(13))));
        t.put("runtimeValidation","NOT_RUN");t.put("classesExecuted",false);
        return new Proof(ep,t);
    }
    private static Map<String,Object> reload(ClassNode hooks,String install,ClassNode config,LifecycleProfileShape.ConfigShape cfg,boolean legacy) {
        require(hooks.fields.size()==4 && hooks.methods.size()==8,"Four volatile observations and two callbacks");
        for(var f:hooks.fields)require(f.access==(ACC_PRIVATE|ACC_STATIC|ACC_VOLATILE)&&f.value==null,"Volatile primitive observations");
        exact(method(hooks,"<init>","()V",ACC_PRIVATE),v(ALOAD,0),m(INVOKESPECIAL,"java/lang/Object","<init>","()V"),op(RETURN));
        var ins=code(method(hooks,install,"()V",ACC_PUBLIC|ACC_STATIC));require(ins.size()==15,"Only Loading and Reloading on MOD bus");
        String loading=legacy?CONFIG+"$Loading":NEW_EVENT+"$Loading",reloading=legacy?CONFIG+"$Reloading":NEW_EVENT+"$Reloading";
        var load=listener(ins,0,loading,hooks.name);var reload=listener(ins,7,reloading,hooks.name);same(ins.get(14),op(RETURN));
        require(!load.getName().equals(reload.getName()),"Distinct callbacks");
        var a=handler(hooks,load,config,cfg,legacy);var b=handler(hooks,reload,config,cfg,legacy);
        require(a.get(0).equals(b.get(0))&&a.get(1).equals(b.get(1))&&!a.get(2).equals(b.get(2)),"Same observations, separate phase counters");
        var names=Set.of(a.get(0),a.get(1),a.get(2),b.get(2));require(names.size()==4,"Distinct config state");
        for(var f:hooks.fields)require(names.contains(f.name)&&f.desc.equals(f.name.equals(a.get(0))?"Z":"I"),"Exact primitive state");
        observations(hooks,names);
        return Map.of("scope","COMMON","install",install,"loading",load.getName(),"reloading",reload.getName(),
            "filter","OWNED_SPEC_IDENTITY_AND_COMMON_TYPE","thread","NATIVE_CALLER_OR_FILE_WATCHER_NO_RESCHEDULING",
            "state",Map.of("enabled",a.get(0),"factor",a.get(1),"loads",a.get(2),"reloads",b.get(2)),
            "sync","NONE","reload","NATIVE_AFTER_RELOAD_CACHE_INVALIDATION_NOT_REINITIALIZATION",
            "atomicSnapshot",false);
    }
    private static Handle listener(List<AbstractInsnNode> c,int o,String event,String owner) {
        same(c.get(o),m(INVOKESTATIC,CONTEXT,"get","()L"+CONTEXT+";"));
        same(c.get(o+1),m(INVOKEVIRTUAL,CONTEXT,"getModEventBus","()L"+BUS+";"));
        same(c.get(o+2),f(GETSTATIC,PRIORITY,"NORMAL","L"+PRIORITY+";"));same(c.get(o+3),op(ICONST_0));
        same(c.get(o+4),ldc(Type.getObjectType(event)));
        var h=callback(c.get(o+5),"java/util/function/Consumer","accept","(Ljava/lang/Object;)V","(L"+event+";)V",owner);
        same(c.get(o+6),m(INVOKEINTERFACE,BUS,"addListener","(L"+PRIORITY+";ZLjava/lang/Class;Ljava/util/function/Consumer;)V"));return h;
    }
    private static List<String> handler(ClassNode hooks,Handle h,ClassNode config,LifecycleProfileShape.ConfigShape cfg,boolean legacy) {
        var method=method(hooks,h.getName(),h.getDesc(),ACC_PRIVATE|ACC_STATIC);var c=code(method);
        require(c.size()==20,"Filter, reads and one phase counter only");
        String event=Type.getArgumentTypes(h.getDesc())[0].getInternalName();
        for(int o:new int[]{0,5}) {same(c.get(o),v(ALOAD,0));same(c.get(o+1),m(INVOKEVIRTUAL,event,"getConfig","()L"+CONFIG+";"));}
        same(c.get(2),m(INVOKEVIRTUAL,CONFIG,"getSpec","()L"+(legacy?SPEC:"net/minecraftforge/fml/config/IConfigSpec")+";"));
        same(c.get(3),f(GETSTATIC,config.name,cfg.spec().name,cfg.spec().desc));jump(c,4,IF_ACMPNE,10);
        same(c.get(7),m(INVOKEVIRTUAL,CONFIG,"getType","()L"+CONFIG+"$Type;"));
        same(c.get(8),f(GETSTATIC,CONFIG+"$Type","COMMON","L"+CONFIG+"$Type;"));jump(c,9,IF_ACMPEQ,11);same(c.get(10),op(RETURN));
        String enabled=state(c.get(12)),factor=state(c.get(14)),count=state(c.get(15));
        String[] tail={m(INVOKESTATIC,config.name,cfg.enabled().name,"()Z"),f(PUTSTATIC,hooks.name,enabled,"Z"),
            m(INVOKESTATIC,config.name,cfg.factor().name,"()I"),f(PUTSTATIC,hooks.name,factor,"I"),
            f(GETSTATIC,hooks.name,count,"I"),op(ICONST_1),op(IADD),f(PUTSTATIC,hooks.name,count,"I"),op(RETURN)};
        for(int i=0;i<tail.length;i++)same(c.get(11+i),tail[i]);
        return List.of(enabled,factor,count);
    }
    private static Map<String,Object> commands(ClassNode n,ClassNode entry,ClassNode config,LifecycleProfileShape.ConfigShape cfg,boolean legacy) {
        require(n.fields.size()==3&&n.methods.size()==8,"Bounded fresh dispatcher command tree");
        var anns=annotations(n);require(anns.size()==1&&n.visibleAnnotations!=null&&n.visibleAnnotations.size()==1&&empty(n.invisibleAnnotations),"Runtime subscriber only");
        var values=anns.get(0).values;require(values!=null&&values.size()==4,"Explicit modId and FORGE bus, both physical sides");
        var attributes=new HashMap<String,Object>();for(int i=0;i<values.size();i+=2)require(attributes.put((String)values.get(i),values.get(i+1))==null,"Unique subscriber attributes");
        require(attributes.keySet().equals(Set.of("modid","bus")) && Objects.equals(attributes.get("modid"),fieldOf(entry,"Ljava/lang/String;").value)
            && attributes.get("bus") instanceof String[] bus && Arrays.equals(bus,new String[]{"Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber$Bus;","FORGE"}),"Own FORGE subscriber");
        for(var f:n.fields)require(f.access==(ACC_PRIVATE|ACC_STATIC)&&f.desc.equals("I")&&f.value==null,"No cached dispatcher or object state");
        exact(method(n,"<init>","()V",ACC_PRIVATE),v(ALOAD,0),m(INVOKESPECIAL,"java/lang/Object","<init>","()V"),op(RETURN));
        var registrations=n.methods.stream().filter(x->x.desc.equals("(Lnet/minecraftforge/event/RegisterCommandsEvent;)V")).toList();
        require(registrations.size()==1,"One command event subscriber");var reg=registrations.get(0);
        method(n,reg.name,reg.desc,ACC_PUBLIC|ACC_STATIC);
        for(var x:n.methods)require(empty(x.invisibleAnnotations) && (x==reg?x.visibleAnnotations!=null&&x.visibleAnnotations.size()==1&&x.visibleAnnotations.get(0).desc.equals(SUBSCRIBE)&&empty(x.visibleAnnotations.get(0).values):empty(x.visibleAnnotations)),"Default NORMAL noncancelled subscriber only");
        var c=code(reg);require(c.size()==36,"Two fresh typed command branches");
        String calls=state(c.get(0)),root=word(c.get(6)),scaleName=word(c.get(11)),integerName=word(c.get(13)),flagName=word(c.get(23)),booleanName=word(c.get(25));
        require(!scaleName.equals(flagName),"Distinct command branches");
        String commands=legacy?"net/minecraft/command/Commands":"net/minecraft/commands/Commands",source=legacy?"net/minecraft/command/CommandSource":"net/minecraft/commands/CommandSourceStack";
        String literal=nativeName(c.get(7)),argument=nativeName(c.get(17));
        String[] prefix={f(GETSTATIC,n.name,calls,"I"),op(ICONST_1),op(IADD),f(PUTSTATIC,n.name,calls,"I"),
            v(ALOAD,0),m(INVOKEVIRTUAL,"net/minecraftforge/event/RegisterCommandsEvent","getDispatcher","()L"+B+"CommandDispatcher;"),ldc(root),
            m(INVOKESTATIC,commands,literal,"(Ljava/lang/String;)L"+LIT+";")};
        for(int i=0;i<prefix.length;i++)same(c.get(i),prefix[i]);
        var permission=callback(c.get(8),"java/util/function/Predicate","test","(Ljava/lang/Object;)Z","(L"+source+";)Z",n.name);
        same(c.get(9),m(INVOKEVIRTUAL,LIT,"requires","(Ljava/util/function/Predicate;)L"+ARG+";"));same(c.get(10),type(CHECKCAST,LIT));
        same(c.get(11),ldc(scaleName));same(c.get(12),m(INVOKESTATIC,commands,literal,"(Ljava/lang/String;)L"+LIT+";"));
        same(c.get(13),ldc(integerName));int minimum=literal(c.get(14)),maximum=literal(c.get(15));
        require(minimum>=0&&maximum>=minimum&&maximum<=1000000,"Finite bounded integer");
        same(c.get(16),m(INVOKESTATIC,B+"arguments/IntegerArgumentType","integer","(II)L"+B+"arguments/IntegerArgumentType;"));
        same(c.get(17),m(INVOKESTATIC,commands,argument,"(Ljava/lang/String;L"+B+"arguments/ArgumentType;)L"+REQ+";"));
        var scale=callback(c.get(18),B+"Command","run","(L"+CTX+";)I","(L"+CTX+";)I",n.name);
        same(c.get(23),ldc(flagName));same(c.get(24),m(INVOKESTATIC,commands,literal,"(Ljava/lang/String;)L"+LIT+";"));
        same(c.get(25),ldc(booleanName));same(c.get(26),m(INVOKESTATIC,B+"arguments/BoolArgumentType","bool","()L"+B+"arguments/BoolArgumentType;"));
        same(c.get(27),m(INVOKESTATIC,commands,argument,"(Ljava/lang/String;L"+B+"arguments/ArgumentType;)L"+REQ+";"));
        var flag=callback(c.get(28),B+"Command","run","(L"+CTX+";)I","(L"+CTX+";)I",n.name);
        require(!flag.getName().equals(scale.getName()),"Distinct handlers");
        for(int o:new int[]{19,29}) {
            same(c.get(o),m(INVOKEVIRTUAL,REQ,"executes","(L"+B+"Command;)L"+ARG+";"));
            for(int j=1;j<=2;j++)same(c.get(o+j),m(INVOKEVIRTUAL,LIT,"then","(L"+ARG+";)L"+ARG+";"));
            same(c.get(o+3),type(CHECKCAST,LIT));
        }
        same(c.get(33),m(INVOKEVIRTUAL,B+"CommandDispatcher","register","(L"+LIT+";)L"+B+"tree/LiteralCommandNode;"));
        same(c.get(34),op(POP));same(c.get(35),op(RETURN));
        var pm=method(n,permission.getName(),permission.getDesc(),ACC_PRIVATE|ACC_STATIC);var p=code(pm);
        require(p.size()==4,"Native permission predicate");int level=literal(p.get(1));require(level>=1&&level<=4,"Non-public permission level");
        same(p.get(0),v(ALOAD,0));same(p.get(2),m(INVOKEVIRTUAL,source,nativeName(p.get(2)),"(I)Z"));same(p.get(3),op(IRETURN));
        var sm=method(n,scale.getName(),scale.getDesc(),ACC_PRIVATE|ACC_STATIC);var s=code(sm);require(s.size()==18,"Config-sensitive integer execution");
        var fm=method(n,flag.getName(),flag.getDesc(),ACC_PRIVATE|ACC_STATIC);var f=code(fm);require(f.size()==16,"Boolean execution");
        String executions=state(s.get(4)),result=state(s.get(15));
        require(Set.of(calls,executions,result).size()==3,"Distinct command observations");
        for(var code:List.of(s,f)) {
            same(code.get(0),v(ALOAD,0));same(code.get(3),v(ISTORE,1));
            same(code.get(4),f(GETSTATIC,n.name,executions,"I"));same(code.get(5),op(ICONST_1));same(code.get(6),op(IADD));same(code.get(7),f(PUTSTATIC,n.name,executions,"I"));
        }
        same(s.get(1),ldc(integerName));same(s.get(2),m(INVOKESTATIC,B+"arguments/IntegerArgumentType","getInteger","(L"+CTX+";Ljava/lang/String;)I"));
        same(s.get(8),m(INVOKESTATIC,config.name,cfg.enabled().name,"()Z"));jump(s,9,IFEQ,14);
        same(s.get(10),m(INVOKESTATIC,config.name,cfg.factor().name,"()I"));same(s.get(11),v(ILOAD,1));same(s.get(12),op(IMUL));jump(s,13,GOTO,15);same(s.get(14),op(ICONST_0));
        same(f.get(1),ldc(booleanName));same(f.get(2),m(INVOKESTATIC,B+"arguments/BoolArgumentType","getBool","(L"+CTX+";Ljava/lang/String;)Z"));
        same(f.get(8),v(ILOAD,1));jump(f,9,IFEQ,12);same(f.get(10),op(ICONST_1));jump(f,11,GOTO,13);same(f.get(12),op(ICONST_0));
        for(var code:List.of(s,f)) {int o=code.size()-3;same(code.get(o),f(PUTSTATIC,n.name,result,"I"));same(code.get(o+1),f(GETSTATIC,n.name,result,"I"));same(code.get(o+2),op(IRETURN));}
        observations(n,Set.of(calls,executions,result));
        return Map.of("root",root,"integer",Map.of("literal",scaleName,"argument",integerName,"min",minimum,"max",maximum),
            "boolean",Map.of("literal",flagName,"argument",booleanName),"permission",level,"bus","FORGE",
            "dispatcher","FRESH_TREE_PER_REGISTER_COMMANDS_EVENT","execution","NATIVE_SERVER_COMMAND_CALLER",
            "effects","OWNED_COUNTER_AND_RESULT_ONLY","config","READ_CURRENT_NATIVE_VALUES_ON_EACH_INTEGER_EXECUTION");
    }
    private static void observations(ClassNode n,Set<String> names) {
        var getters=n.methods.stream().filter(x->x.desc.equals("()I")||x.desc.equals("()Z")).toList();
        require(getters.size()==names.size(),"Complete observation methods");var seen=new HashSet<String>();
        for(var g:getters) {
            require(g.access==(ACC_PUBLIC|ACC_STATIC)&&code(g).size()==2,"Pure getter");
            String field=state(code(g).get(0));require(names.contains(field)&&seen.add(field),"Distinct owned observations");
            exact(g,f(GETSTATIC,n.name,field,g.desc.substring(2)),op(IRETURN));
        }
    }
    private static void jump(List<AbstractInsnNode> c,int index,int opcode,int destination) {
        require(c.get(index) instanceof JumpInsnNode && c.get(index).getOpcode()==opcode,"Exact branch");
        AbstractInsnNode i=((JumpInsnNode)c.get(index)).label;while(i!=null&&i.getOpcode()<0)i=i.getNext();
        require(i==c.get(destination),"Exact branch destination, no bypassed effects");
    }
    private static String state(AbstractInsnNode i){require(i instanceof FieldInsnNode,"Explicit state field");return ((FieldInsnNode)i).name;}
    private static String nativeName(AbstractInsnNode i){require(i instanceof MethodInsnNode,"Explicit native call");return ((MethodInsnNode)i).name;}
    private static String word(AbstractInsnNode i){require(i instanceof LdcInsnNode l&&l.cst instanceof String s&&s.matches("[a-z][a-z0-9_]{0,31}"),"Bounded command word");return (String)((LdcInsnNode)i).cst;}
    static ContentPlan validate(Map<String,ClassNode> nodes,String modId,String version) {
        var proof=inspect(nodes);var n=new ClassNode();nodes.get(proof.entry()).accept(n);
        n.fields.removeIf(f->f.access!=(ACC_PUBLIC|ACC_STATIC|ACC_FINAL));
        n.methods.removeIf(m->!m.name.equals("<init>")&&!m.name.equals("<clinit>"));
        var ctor=method(n,"<init>","()V",ACC_PUBLIC);
        while(code(ctor).size()>6)ctor.instructions.remove(ctor.instructions.getLast());
        ctor.instructions.add(new InsnNode(RETURN));
        return LogicProfileShape.validate(Map.of(n.name,n,proof.item(),nodes.get(proof.item())),modId,version);
    }
    public static boolean accepts(String contract,String name,Type sam,Type instantiated,Type factory) {
        return java.util.stream.Stream.of(CONFIG+"$Loading",CONFIG+"$Reloading",NEW_EVENT+"$Loading",NEW_EVENT+"$Reloading")
            .anyMatch(e->EventPriorityContract.accepts(e,contract,name,sam,instantiated,factory));
    }
    private static void require(boolean value,String why){if(!value)throw new TransformationException("LIFECYCLE_SUITE_UNSUPPORTED: "+why);}
}
