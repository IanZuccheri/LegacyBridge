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
 * Legacy API: Forge 36 SERVER spec and FMLServerStarted/Stopped on the Forge bus.
 * Target API: Forge 47 SERVER spec and ServerStarted/Stopped on the Forge bus.
 * Transformation: retain logical-server callbacks, native schema, reads and cleanup.
 * Behavior differences: target config Unloading has no source equivalent; not used.
 * Known limitations: finite per-server snapshot only, no reload/sync handlers, world
 * mutation, failed-start equivalence, arbitrary lifecycle or runtime qualification.
 */
public final class ServerConfigContract {
    public static final String START = "net/minecraftforge/fml/event/server/FMLServerStartedEvent";
    public static final String STOP = "net/minecraftforge/fml/event/server/FMLServerStoppedEvent";
    public static final String TARGET_START = "net/minecraftforge/event/server/ServerStartedEvent";
    public static final String TARGET_STOP = "net/minecraftforge/event/server/ServerStoppedEvent";
    public static final Map<String,String> EVENT_TYPES = Map.of(START,TARGET_START,STOP,TARGET_STOP);
    private static final String SPEC = "net/minecraftforge/common/ForgeConfigSpec";
    private static final String CONFIG = "net/minecraftforge/fml/config/ModConfig$Type";
    private static final String LOADING = "net/minecraftforge/fml/ModLoadingContext";
    private static final String BUS = "net/minecraftforge/eventbus/api/IEventBus";
    private static final String FORGE = "net/minecraftforge/common/MinecraftForge";
    private static final String PRIORITY = "net/minecraftforge/eventbus/api/EventPriority";
    private static final String MOD = "Lnet/minecraftforge/fml/common/Mod;";
    private ServerConfigContract() {}

    public record Proof(String entry, String item, String config, String hooks,
                        String install, String started, String stopped, String active, String enabled,
                        String factor, String starts, String stops, String booleanKey, boolean booleanDefault,
                        String integerKey, int integerDefault, int minimum, int maximum) {
        public Map<String,Object> toTree() {
            return Map.of("schemaVersion",1,"contract","server-config-scope-v1",
                "roles",Map.of("entry",entry,"item",item,"config",config,"hooks",hooks),
                "registration",Map.of("method",install,"scope","SERVER","physicalSides","BOTH",
                    "path","<world>/serverconfig/<modId>-server.toml","sync","NATIVE_CONNECTION_CONFIG_ONLY_NOT_VALIDATED"),
                "lifecycle",Map.of("bus","FORGE","priority","NORMAL","receiveCancelled",false,
                    "readPhase","SERVER_STARTED_AFTER_CONFIG_LOAD","clearPhase","SERVER_STOPPED_BEFORE_CONFIG_UNLOAD",
                    "thread","NATIVE_SERVER_THREAD_NO_RESCHEDULING","started",started,"stopped",stopped),
                "state",Map.of("active",active,"enabled",enabled,"factor",factor,"starts",starts,"stops",stops),
                "schema",Map.of("boolean",Map.of("key",booleanKey,"default",booleanDefault),
                    "integer",Map.of("key",integerKey,"default",integerDefault,"min",minimum,"max",maximum)),
                "isolatedOracle",Map.of("freshServerStarted",Map.of("active",true,"enabled",booleanDefault,"factor",integerDefault,"starts",1,"stops",0),
                    "afterNormalStop",Map.of("active",false,"enabled",false,"factor",0,"starts",1,"stops",1),
                    "nextWorld","READ_NEW_LOADED_VALUES_NO_REUSE_OF_SNAPSHOT","qualification","EXPECTED_NOT_EXECUTED"),
                "excluded",List.of("CONFIG_LOADING_RELOADING_UNLOADING_CALLBACKS","CLIENT_READS","CUSTOM_SYNC","GAMEPLAY","FAILED_START_EQUIVALENCE"),
                "validation",Map.of("runtime","NOT_RUN","classesExecuted",false));
        }
    }

    /** Derive every phase and state effect from bytes, not a filename, prior report or policy flag. */
    public static Proof inspect(Map<String,ClassNode> nodes) {
        try { return inspectGraph(nodes); }
        catch (TransformationException failure) {
            // Shared token/envelope checks retain their original CLIENT diagnostics for that API.
            // Re-contextualize only that known refusal; never swallow cancellation or other failures.
            String prefix = "CLIENT_CONFIG_UNSUPPORTED: ";
            if (failure.getMessage() != null && failure.getMessage().startsWith(prefix))
                throw new TransformationException("SERVER_CONFIG_UNSUPPORTED: " + failure.getMessage().substring(prefix.length()), failure);
            throw failure;
        }
    }
    private static Proof inspectGraph(Map<String,ClassNode> nodes) {
        require(nodes!=null && nodes.size()==4,"Exactly four owned classes");
        for(var e:nodes.entrySet()) {
            OperationControl.checkpoint();var n=e.getValue();
            require(n!=null && e.getKey().equals(n.name) && !LegacyReferenceScanner.isProtectedDefinition(n.name),"Owned identity");
            require(n.version==V1_8 && n.access==(ACC_PUBLIC|ACC_FINAL|ACC_SUPER) && n.interfaces.isEmpty(),"Final public Java 8 classes");
            require(n.fields.size()<=5 && n.methods.size()<=9,"Member bound");
            var seen=new HashSet<String>();
            for(var m:n.methods) {
                require(seen.add(m.name+m.desc) && m.instructions.size()<=128,"Method bound");
                require(m.tryCatchBlocks.isEmpty() && m.exceptions.isEmpty() && empty(m.visibleAnnotations) && empty(m.invisibleAnnotations),"No handlers/subscribers");
                for(var i:m.instructions)require(!(i instanceof JumpInsnNode || i instanceof TableSwitchInsnNode || i instanceof LookupSwitchInsnNode),"No physical-side guard or hidden flow");
            }
            seen.clear();
            for(var f:n.fields)require(seen.add(f.name+f.desc) && empty(f.visibleAnnotations) && empty(f.invisibleAnnotations),"Unique plain fields");
        }
        var entry=one(nodes,n->annotations(n).stream().anyMatch(a->MOD.equals(a.desc)),"entry");
        var item=one(nodes,n->Set.of("net/minecraft/item/Item","net/minecraft/world/item/Item").contains(n.superName),"item");
        var config=one(nodes,n->n.fields.stream().anyMatch(f->f.desc.equals("L"+SPEC+";")),"config");
        var hooks=one(nodes,n->n!=entry && n!=item && n!=config,"hooks");
        require(new HashSet<>(List.of(entry.name,item.name,config.name,hooks.name)).size()==4,"Distinct roles");
        for(var n:List.of(entry,config,hooks))require("java/lang/Object".equals(n.superName),"Object helpers only");
        require(annotations(entry).size()==1 && entry.visibleAnnotations!=null && entry.visibleAnnotations.size()==1
            && empty(entry.invisibleAnnotations) && annotations(item).isEmpty() && annotations(config).isEmpty() && annotations(hooks).isEmpty(),"Only runtime-visible Mod");
        require(entry.fields.size()==3 && entry.methods.size()==2,"Only native registration and unconditional install");
        var ctor=method(entry,"<init>","()V",ACC_PUBLIC);var c=code(ctor);
        require(c.size()==8 && c.get(6) instanceof MethodInsnNode,"Unconditional both-physical-sides registration");
        var installCall=(MethodInsnNode)c.get(6);
        same(installCall,m(INVOKESTATIC,hooks.name,installCall.name,"()V"));same(c.get(7),op(RETURN));
        require(item.fields.isEmpty() && item.methods.size()==1 && code(method(item,"<init>","()V",ACC_PUBLIC)).size()==8,"Plain item only");
        registrationEnvelope(entry,item,c); // Same native register/item envelope, not duplicated rules.
        var cfg=LifecycleProfileShape.validateConfig(config);
        require(cfg.spec().access==(ACC_PUBLIC|ACC_STATIC|ACC_FINAL),"Public final native spec");
        for(var f:config.fields)if(f!=cfg.spec())require(f.access==(ACC_PRIVATE|ACC_STATIC|ACC_FINAL),"Private native values");
        method(config,"<init>","()V",ACC_PRIVATE);method(config,"<clinit>","()V",ACC_STATIC);
        method(config,cfg.enabled().name,"()Z",ACC_PUBLIC|ACC_STATIC);method(config,cfg.factor().name,"()I",ACC_PUBLIC|ACC_STATIC);
        require(hooks.fields.size()==5 && hooks.methods.size()==9,"Finite session snapshot/counters only");
        for(var f:hooks.fields)require(f.access==(ACC_PRIVATE|ACC_STATIC) && f.value==null,"Private zero-initialized observation state");
        exact(method(hooks,"<init>","()V",ACC_PRIVATE),v(ALOAD,0),m(INVOKESPECIAL,"java/lang/Object","<init>","()V"),op(RETURN));
        boolean legacy=item.superName.equals("net/minecraft/item/Item");
        String start=legacy?START:TARGET_START,stop=legacy?STOP:TARGET_STOP,parameter=legacy?SPEC:"net/minecraftforge/fml/config/IConfigSpec";
        var install=method(hooks,installCall.name,"()V",ACC_PUBLIC|ACC_STATIC);var ins=code(install);
        require(ins.size()==17,"SERVER config and exactly two Forge lifecycle listeners");
        same(ins.get(0),m(INVOKESTATIC,LOADING,"get","()L"+LOADING+";"));
        same(ins.get(1),f(GETSTATIC,CONFIG,"SERVER","L"+CONFIG+";"));
        same(ins.get(2),f(GETSTATIC,config.name,cfg.spec().name,cfg.spec().desc));
        same(ins.get(3),m(INVOKEVIRTUAL,LOADING,"registerConfig","(L"+CONFIG+";L"+parameter+";)V"));
        var started=listener(ins,4,start,hooks.name);var stopped=listener(ins,10,stop,hooks.name);
        same(ins.get(16),op(RETURN));require(!started.getName().equals(stopped.getName()),"Distinct lifecycle callbacks");
        var begin=method(hooks,started.getName(),started.getDesc(),ACC_PRIVATE|ACC_STATIC);
        var end=method(hooks,stopped.getName(),stopped.getDesc(),ACC_PRIVATE|ACC_STATIC);
        var b=code(begin);var e=code(end);require(b.size()==11 && e.size()==11,"Exact read/start and clear/stop effects");
        String enabled=state(b.get(1)),factor=state(b.get(3)),active=state(b.get(5)),starts=state(b.get(6)),stops=state(e.get(6));
        require(new HashSet<>(List.of(enabled,factor,active,starts,stops)).size()==5,"Distinct session fields");
        for(var f:hooks.fields)require((Set.of(enabled,active).contains(f.name)&&f.desc.equals("Z"))
            ||(Set.of(factor,starts,stops).contains(f.name)&&f.desc.equals("I")),"Exact state types");
        exact(begin,m(INVOKESTATIC,config.name,cfg.enabled().name,"()Z"),f(PUTSTATIC,hooks.name,enabled,"Z"),
            m(INVOKESTATIC,config.name,cfg.factor().name,"()I"),f(PUTSTATIC,hooks.name,factor,"I"),op(ICONST_1),
            f(PUTSTATIC,hooks.name,active,"Z"),f(GETSTATIC,hooks.name,starts,"I"),op(ICONST_1),op(IADD),f(PUTSTATIC,hooks.name,starts,"I"),op(RETURN));
        exact(end,op(ICONST_0),f(PUTSTATIC,hooks.name,active,"Z"),op(ICONST_0),f(PUTSTATIC,hooks.name,enabled,"Z"),
            op(ICONST_0),f(PUTSTATIC,hooks.name,factor,"I"),f(GETSTATIC,hooks.name,stops,"I"),op(ICONST_1),op(IADD),f(PUTSTATIC,hooks.name,stops,"I"),op(RETURN));
        var getters=hooks.methods.stream().filter(m->m.desc.equals("()Z")||m.desc.equals("()I")).toList();
        require(getters.size()==5,"Five pure observations");var observed=new HashSet<String>();
        for(var g:getters) {
            require(g.access==(ACC_PUBLIC|ACC_STATIC) && code(g).size()==2 && code(g).get(0) instanceof FieldInsnNode,"Pure observation");
            var f=(FieldInsnNode)code(g).get(0);require(observed.add(f.name),"Distinct observations");
            exact(g,f(GETSTATIC,hooks.name,f.name,g.desc.substring(2)),op(IRETURN));
        }
        require(observed.equals(Set.of(enabled,factor,active,starts,stops)),"Complete observation set");
        var init=code(method(config,"<clinit>","()V",ACC_STATIC));
        return new Proof(entry.name,item.name,config.name,hooks.name,install.name,started.getName(),stopped.getName(),
            active,enabled,factor,starts,stops,(String)((LdcInsnNode)init.get(5)).cst,init.get(6).getOpcode()==ICONST_1,
            (String)((LdcInsnNode)init.get(10)).cst,literal(init.get(11)),literal(init.get(12)),literal(init.get(13)));
    }
    private static String state(AbstractInsnNode i) { require(i instanceof FieldInsnNode,"Explicit state field");return ((FieldInsnNode)i).name; }
    private static Handle listener(List<AbstractInsnNode> ins,int offset,String event,String owner) {
        same(ins.get(offset),f(GETSTATIC,FORGE,"EVENT_BUS","L"+BUS+";"));
        same(ins.get(offset+1),f(GETSTATIC,PRIORITY,"NORMAL","L"+PRIORITY+";"));
        same(ins.get(offset+2),op(ICONST_0));same(ins.get(offset+3),ldc(Type.getObjectType(event)));
        var handle=callback(ins.get(offset+4),"java/util/function/Consumer","accept","(Ljava/lang/Object;)V","(L"+event+";)V",owner);
        same(ins.get(offset+5),m(INVOKEINTERFACE,BUS,"addListener","(L"+PRIORITY+";ZLjava/lang/Class;Ljava/util/function/Consumer;)V"));
        return handle;
    }
    static ContentPlan validate(Map<String,ClassNode> nodes,String modId,String version) {
        var proof=inspect(nodes);var projected=new ClassNode();nodes.get(proof.entry()).accept(projected);
        var ctor=method(projected,"<init>","()V",ACC_PUBLIC);
        while(code(ctor).size()>6)ctor.instructions.remove(ctor.instructions.getLast());
        ctor.instructions.add(new InsnNode(RETURN));
        return LogicProfileShape.validate(Map.of(projected.name,projected,proof.item(),nodes.get(proof.item())),modId,version);
    }
    public static boolean accepts(String contract,String name,Type sam,Type instantiated,Type factory) {
        return java.util.stream.Stream.of(START,STOP,TARGET_START,TARGET_STOP)
            .anyMatch(event->EventPriorityContract.accepts(event,contract,name,sam,instantiated,factory));
    }
    private static void require(boolean valid,String reason) {if(!valid)throw new TransformationException("SERVER_CONFIG_UNSUPPORTED: "+reason);}
}
