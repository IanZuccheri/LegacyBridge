package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.transformation.TransformationException;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import static org.junit.jupiter.api.Assertions.*;

/** Original inert target trees. These tests prove shape/phase gates, not game behavior or API linkage. */
class LifecycleProfileShapeTest {
    private static final String E="testlife/Entry", I="testlife/Token", C="testlife/Config", H="testlife/Commands";
    private static final String MOD="test_lifecycle", F="net/minecraftforge/", B="com/mojang/brigadier/";
    private static final String ITEM="net/minecraft/world/item/Item", PROP=ITEM+"$Properties", SPEC=F+"common/ForgeConfigSpec";
    private static final String REG=F+"registries/RegistryObject", DEFERRED=F+"registries/DeferredRegister", REGISTRY=F+"registries/IForgeRegistry";
    private static final String BUS=F+"eventbus/api/IEventBus", CTX=F+"fml/javafmlmod/FMLJavaModLoadingContext", SETUP=F+"fml/event/lifecycle/FMLCommonSetupEvent";
    private static final String CMD_EVENT=F+"event/RegisterCommandsEvent", CMD_DESC="(L"+B+"context/CommandContext;)I", PERM_DESC="(Lnet/minecraft/commands/CommandSourceStack;)Z";
    private static final Handle META=new Handle(Opcodes.H_INVOKESTATIC,"java/lang/invoke/LambdaMetafactory","metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",false);

    @Test void acceptsNativeLateReadConfigAndActuallyLinkedCommandsWithoutChangingTrees() {
        var nodes=fixture(); Map<String,byte[]> before=bytes(nodes);
        var result=LifecycleProfileShape.validate(nodes,MOD,"1.0.0");
        assertEquals(MOD,result.modId()); assertEquals("lifecycle_token",result.items().get(0).name());
        assertEquals(16,result.items().get(0).properties().stackSize());
        var after=bytes(nodes); before.forEach((n,b)->assertArrayEquals(b,after.get(n),n));
    }
    @Test void acceptsExactInferredNativeConsumerWithoutRewritingToExplicitEventClass() {
        var nodes=fixture();inferredConstructor(nodes);var before=bytes(nodes);
        assertEquals(MOD,LifecycleProfileShape.validate(nodes,MOD,"1.0.0").modId());
        var after=bytes(nodes);before.forEach((n,b)->assertArrayEquals(b,after.get(n),n));
        assertEquals("(Ljava/util/function/Consumer;)V",((MethodInsnNode)code(named(nodes,E,"<init>")).get(13)).desc);
    }
    @Test void refusesWrongEventTypeInInferredConsumerMetadata() {
        refused(n->{inferredConstructor(n);((InvokeDynamicInsnNode)code(named(n,E,"<init>")).get(12)).bsmArgs[2]=Type.getMethodType("(L"+F+"fml/event/lifecycle/FMLClientSetupEvent;)V");});
    }
    @Test void refusesEarlyRegistryReadInEntrypointConstructor() {
        refused(n->insertBeforeReturn(named(n,E,"<init>"),field(Opcodes.GETSTATIC,E,"TOKEN","L"+REG+";"),
                call(Opcodes.INVOKEVIRTUAL,REG,"get","()Ljava/lang/Object;"),new InsnNode(Opcodes.POP)));
    }
    @Test void refusesDirectEarlyCallToWorkFromConstructor() {
        refused(n->insertBeforeReturn(named(n,E,"<init>"),call(Opcodes.INVOKESTATIC,E,"work","()V")));
    }
    @Test void refusesOpaqueHelperThatInvokesQueuedWorkEvenIfCurrentlyUnused() {
        refused(n->{var m=method(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"earlyHelper","()V",call(Opcodes.INVOKESTATIC,E,"work","()V"),new InsnNode(Opcodes.RETURN));n.get(E).methods.add(m);});
    }
    @Test void refusesEarlyConfigReadThroughItemAndConfigConstructors() {
        refused(n->{var ctor=named(n,C,"<init>");ctor.access=Opcodes.ACC_PUBLIC;
            insertBeforeReturn(ctor,field(Opcodes.GETSTATIC,C,"ENABLED","L"+SPEC+"$BooleanValue;"),call(Opcodes.INVOKEVIRTUAL,SPEC+"$BooleanValue","get","()Ljava/lang/Object;"),new InsnNode(Opcodes.POP));
            insertBeforeReturn(named(n,I,"<init>"),new TypeInsnNode(Opcodes.NEW,C),new InsnNode(Opcodes.DUP),call(Opcodes.INVOKESPECIAL,C,"<init>","()V"),new InsnNode(Opcodes.POP));});
    }
    @Test void refusesConfigConstructorNativeReadsEvenWithoutAnObviousCaller() {
        refused(n->insertBeforeReturn(named(n,C,"<init>"),field(Opcodes.GETSTATIC,C,"ENABLED","L"+SPEC+"$BooleanValue;"),call(Opcodes.INVOKEVIRTUAL,SPEC+"$BooleanValue","get","()Ljava/lang/Object;"),new InsnNode(Opcodes.POP)));
    }
    @Test void refusesDirectConfigReadsInParallelSetup() {
        refused(n->insertBeforeReturn(named(n,E,"setup"),call(Opcodes.INVOKESTATIC,C,"factor","()I"),new InsnNode(Opcodes.POP)));
    }
    @Test void refusesWrongLifecycleSideToken() {
        refused(n->replace(named(n,E,"<init>"),14,new LdcInsnNode(Type.getObjectType(F+"fml/event/lifecycle/FMLClientSetupEvent"))));
    }
    @Test void refusesForgeBusAsTheCommonSetupListenerReceiver() {
        refused(n->{replace(named(n,E,"<init>"),10,field(Opcodes.GETSTATIC,F+"common/MinecraftForge","EVENT_BUS","L"+BUS+";"));replace(named(n,E,"<init>"),11,new InsnNode(Opcodes.NOP));});
    }
    @Test void refusesModBusForCommandSubscriber() {
        refused(n->n.get(H).visibleAnnotations.get(0).values.set(3,new String[]{"L"+F+"fml/common/Mod$EventBusSubscriber$Bus;","MOD"}));
    }
    @Test void refusesSideSpecificSubscriberAndUnknownAnnotationElements() {
        refused(n->n.get(H).visibleAnnotations.get(0).values.addAll(List.of("value",List.of(new String[]{"Lnet/minecraftforge/api/distmarker/Dist;","CLIENT"}))));
    }
    @Test void refusesObservedOrEscapingEnqueueFuture() {
        refused(n->{n.get(E).fields.add(new FieldNode(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC,"future","Ljava/util/concurrent/CompletableFuture;",null,null));
            replace(named(n,E,"setup"),7,field(Opcodes.PUTSTATIC,E,"future","Ljava/util/concurrent/CompletableFuture;"));});
    }
    @Test void refusesExplicitThrowInQueuedWork() {
        refused(n->replace(named(n,E,"work"),12,new InsnNode(Opcodes.ATHROW)));
    }
    @Test void refusesImplicitDivideByZeroWithoutAnyAthrowInstruction() {
        refused(n->{replace(named(n,E,"work"),9,new InsnNode(Opcodes.ICONST_0));replace(named(n,E,"work"),10,new InsnNode(Opcodes.IDIV));});
    }
    @Test void refusesExtraNewOrArrayEffectsInQueuedWork() {
        refused(n->insertBeforeReturn(named(n,E,"work"),new InsnNode(Opcodes.ICONST_1),new IntInsnNode(Opcodes.NEWARRAY,Opcodes.T_INT),new InsnNode(Opcodes.POP)));
    }
    @Test void refusesReadFromAHandleOtherThanTheOneProvenRegistered() {
        refused(n->replace(named(n,E,"work"),0,field(Opcodes.GETSTATIC,E,"NOT_TOKEN","L"+REG+";")));
    }
    @Test void refusesRegistryResultWithoutTheRequiredImmediateItemCast() {
        refused(n->replace(named(n,E,"work"),2,new InsnNode(Opcodes.NOP)));
    }
    @Test void refusesConfigGetterReplacedByCachedDefault() {
        refused(n->{MethodNode m=named(n,C,"enabled");m.instructions.clear();m.instructions.add(new InsnNode(Opcodes.ICONST_1));m.instructions.add(new InsnNode(Opcodes.IRETURN));});
    }
    @Test void refusesDuplicateConfigKeysAndInvalidRange() {
        refused(n->replace(named(n,C,"<clinit>"),10,new LdcInsnNode("enabled")));
        refused(n->replace(named(n,C,"<clinit>"),13,new InsnNode(Opcodes.ICONST_0)));
    }
    @Test void refusesConfigRegistrationThatHasNotReceivedItsExactTargetDescriptor() {
        refused(n->((MethodInsnNode)code(named(n,E,"<init>")).get(9)).desc="(L"+F+"fml/config/ModConfig$Type;L"+SPEC+";)V");
    }
    @Test void refusesUnlinkedCommandSignatureHelperThatReadsLiveConfig() {
        refused(n->n.get(H).methods.add(method(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"notActuallyLinked",CMD_DESC,
                call(Opcodes.INVOKESTATIC,C,"factor","()I"),new InsnNode(Opcodes.IRETURN))));
    }
    @Test void refusesEarlyDirectInvocationOfCommandHandlerDuringRegistration() {
        refused(n->insertBeforeReturn(named(n,H,"register"),new InsnNode(Opcodes.ACONST_NULL),call(Opcodes.INVOKESTATIC,H,"execute",CMD_DESC),new InsnNode(Opcodes.POP)));
    }
    @Test void refusesUnauthorizedApiEvenInsideAnActuallyLinkedCommandHandler() {
        refused(n->{MethodNode m=named(n,H,"execute");m.instructions.insertBefore(m.instructions.getFirst(),call(Opcodes.INVOKESTATIC,"java/lang/System","nanoTime","()J"));m.instructions.insert(m.instructions.getFirst(),new InsnNode(Opcodes.POP2));});
    }
    @Test void refusesLifecycleCallbackOwnedByAnotherRole() {
        refused(n->((InvokeDynamicInsnNode)code(named(n,E,"setup")).get(5)).bsmArgs[1]=new Handle(Opcodes.H_INVOKESTATIC,C,"work","()V",false));
    }
    @Test void refusesExtraSubscriberOnPassiveGetter() {
        refused(n->{MethodNode m=named(n,H,"executionCount");m.visibleAnnotations=new ArrayList<>(List.of(new AnnotationNode("L"+F+"eventbus/api/SubscribeEvent;")));});
    }
    @Test void refusesProtectedRoleDefinitionsAndBrokenOwnedMapIdentity() {
        refused(n->{ClassNode c=n.get(C);c.name="net/minecraft/InjectedConfig";});
    }

    private static void refused(Consumer<Map<String,ClassNode>> mutation) {var nodes=fixture();mutation.accept(nodes);assertThrows(TransformationException.class,()->LifecycleProfileShape.validate(nodes,MOD,"1.0.0"));}
    private static void inferredConstructor(Map<String,ClassNode> nodes){MethodNode ctor=named(nodes,E,"<init>");for(int i=0;i<3;i++)ctor.instructions.remove(code(ctor).get(12));((MethodInsnNode)code(ctor).get(13)).desc="(Ljava/util/function/Consumer;)V";}
    private static Map<String,ClassNode> fixture() {
        var nodes=new TreeMap<String,ClassNode>();ClassNode e=node(E,"java/lang/Object"),item=node(I,ITEM),cfg=node(C,"java/lang/Object"),cmd=node(H,"java/lang/Object");
        for(ClassNode n:List.of(e,item,cfg,cmd))nodes.put(n.name,n);
        AnnotationNode mod=new AnnotationNode("L"+F+"fml/common/Mod;");mod.values=new ArrayList<>(List.of("value",MOD));e.visibleAnnotations=new ArrayList<>(List.of(mod));
        AnnotationNode sub=new AnnotationNode("L"+F+"fml/common/Mod$EventBusSubscriber;");sub.values=new ArrayList<>(List.of("modid",MOD,"bus",new String[]{"L"+F+"fml/common/Mod$EventBusSubscriber$Bus;","FORGE"}));cmd.visibleAnnotations=new ArrayList<>(List.of(sub));
        int constant=Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC|Opcodes.ACC_FINAL;
        e.fields.add(new FieldNode(constant,"MOD_ID","Ljava/lang/String;",null,MOD));e.fields.add(new FieldNode(constant,"ITEMS","L"+DEFERRED+";",null,null));e.fields.add(new FieldNode(constant,"TOKEN","L"+REG+";",null,null));
        for(String name:List.of("setupCount","workCount","factor"))state(e,name,"I");state(e,"item","L"+ITEM+";");state(e,"enabled","Z");
        MethodNode init=method(Opcodes.ACC_STATIC,"<clinit>","()V",field(Opcodes.GETSTATIC,F+"registries/ForgeRegistries","ITEMS","L"+REGISTRY+";"),new LdcInsnNode(MOD),
                call(Opcodes.INVOKESTATIC,DEFERRED,"create","(L"+REGISTRY+";Ljava/lang/String;)L"+DEFERRED+";"),field(Opcodes.PUTSTATIC,E,"ITEMS","L"+DEFERRED+";"),field(Opcodes.GETSTATIC,E,"ITEMS","L"+DEFERRED+";"),new LdcInsnNode("lifecycle_token"),
                new InvokeDynamicInsnNode("get","()Ljava/util/function/Supplier;",META,Type.getMethodType("()Ljava/lang/Object;"),new Handle(Opcodes.H_NEWINVOKESPECIAL,I,"<init>","()V",false),Type.getMethodType("()L"+ITEM+";")),
                call(Opcodes.INVOKEVIRTUAL,DEFERRED,"register","(Ljava/lang/String;Ljava/util/function/Supplier;)L"+REG+";"),field(Opcodes.PUTSTATIC,E,"TOKEN","L"+REG+";"),new InsnNode(Opcodes.RETURN));e.methods.add(init);
        e.methods.add(method(Opcodes.ACC_PUBLIC,"<init>","()V",new VarInsnNode(Opcodes.ALOAD,0),call(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V"),field(Opcodes.GETSTATIC,E,"ITEMS","L"+DEFERRED+";"),
                call(Opcodes.INVOKESTATIC,CTX,"get","()L"+CTX+";"),call(Opcodes.INVOKEVIRTUAL,CTX,"getModEventBus","()L"+BUS+";"),call(Opcodes.INVOKEVIRTUAL,DEFERRED,"register","(L"+BUS+";)V"),
                call(Opcodes.INVOKESTATIC,F+"fml/ModLoadingContext","get","()L"+F+"fml/ModLoadingContext;"),field(Opcodes.GETSTATIC,F+"fml/config/ModConfig$Type","COMMON","L"+F+"fml/config/ModConfig$Type;"),field(Opcodes.GETSTATIC,C,"SPEC","L"+SPEC+";"),
                call(Opcodes.INVOKEVIRTUAL,F+"fml/ModLoadingContext","registerConfig","(L"+F+"fml/config/ModConfig$Type;L"+F+"fml/config/IConfigSpec;)V"),call(Opcodes.INVOKESTATIC,CTX,"get","()L"+CTX+";"),call(Opcodes.INVOKEVIRTUAL,CTX,"getModEventBus","()L"+BUS+";"),
                field(Opcodes.GETSTATIC,F+"eventbus/api/EventPriority","NORMAL","L"+F+"eventbus/api/EventPriority;"),new InsnNode(Opcodes.ICONST_0),new LdcInsnNode(Type.getObjectType(SETUP)),dynamic("java/util/function/Consumer","accept","(Ljava/lang/Object;)V",E,"setup","(L"+SETUP+";)V"),
                call(Opcodes.INVOKEINTERFACE,BUS,"addListener","(L"+F+"eventbus/api/EventPriority;ZLjava/lang/Class;Ljava/util/function/Consumer;)V"),new InsnNode(Opcodes.RETURN)));
        MethodNode setup=method(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC,"setup","(L"+SETUP+";)V");increment(setup,E,"setupCount");add(setup,new VarInsnNode(Opcodes.ALOAD,0),dynamic("java/lang/Runnable","run","()V",E,"work","()V"),call(Opcodes.INVOKEVIRTUAL,SETUP,"enqueueWork","(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;"),new InsnNode(Opcodes.POP),new InsnNode(Opcodes.RETURN));e.methods.add(setup);
        MethodNode work=method(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC,"work","()V",field(Opcodes.GETSTATIC,E,"TOKEN","L"+REG+";"),call(Opcodes.INVOKEVIRTUAL,REG,"get","()Ljava/lang/Object;"),new TypeInsnNode(Opcodes.CHECKCAST,ITEM),field(Opcodes.PUTSTATIC,E,"item","L"+ITEM+";"),call(Opcodes.INVOKESTATIC,C,"enabled","()Z"),field(Opcodes.PUTSTATIC,E,"enabled","Z"),call(Opcodes.INVOKESTATIC,C,"factor","()I"),field(Opcodes.PUTSTATIC,E,"factor","I"));increment(work,E,"workCount");add(work,new InsnNode(Opcodes.RETURN));e.methods.add(work);
        for(String name:List.of("setupCount","workCount","factor"))getter(e,name,name,"I");getter(e,"observedItem","item","L"+ITEM+";");getter(e,"observedEnabled","enabled","Z");
        item.methods.add(method(Opcodes.ACC_PUBLIC,"<init>","()V",new VarInsnNode(Opcodes.ALOAD,0),new TypeInsnNode(Opcodes.NEW,PROP),new InsnNode(Opcodes.DUP),call(Opcodes.INVOKESPECIAL,PROP,"<init>","()V"),new IntInsnNode(Opcodes.BIPUSH,16),call(Opcodes.INVOKEVIRTUAL,PROP,"stacksTo","(I)L"+PROP+";"),call(Opcodes.INVOKESPECIAL,ITEM,"<init>","(L"+PROP+";)V"),new InsnNode(Opcodes.RETURN)));
        cfg.fields.add(new FieldNode(constant,"SPEC","L"+SPEC+";",null,null));cfg.fields.add(new FieldNode(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC|Opcodes.ACC_FINAL,"ENABLED","L"+SPEC+"$BooleanValue;",null,null));cfg.fields.add(new FieldNode(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC|Opcodes.ACC_FINAL,"FACTOR","L"+SPEC+"$IntValue;",null,null));objectCtor(cfg);
        cfg.methods.add(method(Opcodes.ACC_STATIC,"<clinit>","()V",new TypeInsnNode(Opcodes.NEW,SPEC+"$Builder"),new InsnNode(Opcodes.DUP),call(Opcodes.INVOKESPECIAL,SPEC+"$Builder","<init>","()V"),new VarInsnNode(Opcodes.ASTORE,0),new VarInsnNode(Opcodes.ALOAD,0),new LdcInsnNode("enabled"),new InsnNode(Opcodes.ICONST_1),call(Opcodes.INVOKEVIRTUAL,SPEC+"$Builder","define","(Ljava/lang/String;Z)L"+SPEC+"$BooleanValue;"),field(Opcodes.PUTSTATIC,C,"ENABLED","L"+SPEC+"$BooleanValue;"),new VarInsnNode(Opcodes.ALOAD,0),new LdcInsnNode("factor"),new InsnNode(Opcodes.ICONST_3),new InsnNode(Opcodes.ICONST_1),new IntInsnNode(Opcodes.BIPUSH,8),call(Opcodes.INVOKEVIRTUAL,SPEC+"$Builder","defineInRange","(Ljava/lang/String;III)L"+SPEC+"$IntValue;"),field(Opcodes.PUTSTATIC,C,"FACTOR","L"+SPEC+"$IntValue;"),new VarInsnNode(Opcodes.ALOAD,0),call(Opcodes.INVOKEVIRTUAL,SPEC+"$Builder","build","()L"+SPEC+";"),field(Opcodes.PUTSTATIC,C,"SPEC","L"+SPEC+";"),new InsnNode(Opcodes.RETURN)));
        configGetter(cfg,"enabled","ENABLED","BooleanValue","java/lang/Boolean","booleanValue","Z");configGetter(cfg,"factor","FACTOR","IntValue","java/lang/Integer","intValue","I");
        objectCtor(cmd);for(String field:List.of("registrations","executions","lastInput","lastResult"))state(cmd,field,"I");
        MethodNode registration=method(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"register","(L"+CMD_EVENT+";)V");registration.visibleAnnotations=new ArrayList<>(List.of(new AnnotationNode("L"+F+"eventbus/api/SubscribeEvent;")));increment(registration,H,"registrations");
        add(registration,new VarInsnNode(Opcodes.ALOAD,0),call(Opcodes.INVOKEVIRTUAL,CMD_EVENT,"getDispatcher","()L"+B+"CommandDispatcher;"),new LdcInsnNode("bridgeprobe"),call(Opcodes.INVOKESTATIC,"net/minecraft/commands/Commands","literal","(Ljava/lang/String;)L"+B+"builder/LiteralArgumentBuilder;"),dynamic("java/util/function/Predicate","test","(Ljava/lang/Object;)Z",H,"permission",PERM_DESC),call(Opcodes.INVOKEVIRTUAL,B+"builder/LiteralArgumentBuilder","requires","(Ljava/util/function/Predicate;)L"+B+"builder/ArgumentBuilder;"),new TypeInsnNode(Opcodes.CHECKCAST,B+"builder/LiteralArgumentBuilder"),new LdcInsnNode("value"),new InsnNode(Opcodes.ICONST_0),new IntInsnNode(Opcodes.BIPUSH,100),call(Opcodes.INVOKESTATIC,B+"arguments/IntegerArgumentType","integer","(II)L"+B+"arguments/IntegerArgumentType;"),call(Opcodes.INVOKESTATIC,"net/minecraft/commands/Commands","argument","(Ljava/lang/String;L"+B+"arguments/ArgumentType;)L"+B+"builder/RequiredArgumentBuilder;"),dynamic(B+"Command","run",CMD_DESC,H,"execute",CMD_DESC),call(Opcodes.INVOKEVIRTUAL,B+"builder/RequiredArgumentBuilder","executes","(L"+B+"Command;)L"+B+"builder/ArgumentBuilder;"),call(Opcodes.INVOKEVIRTUAL,B+"builder/LiteralArgumentBuilder","then","(L"+B+"builder/ArgumentBuilder;)L"+B+"builder/ArgumentBuilder;"),new TypeInsnNode(Opcodes.CHECKCAST,B+"builder/LiteralArgumentBuilder"),call(Opcodes.INVOKEVIRTUAL,B+"CommandDispatcher","register","(L"+B+"builder/LiteralArgumentBuilder;)L"+B+"tree/LiteralCommandNode;"),new InsnNode(Opcodes.POP),new InsnNode(Opcodes.RETURN));cmd.methods.add(registration);
        cmd.methods.add(method(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC,"permission",PERM_DESC,new VarInsnNode(Opcodes.ALOAD,0),new InsnNode(Opcodes.ICONST_2),call(Opcodes.INVOKEVIRTUAL,"net/minecraft/commands/CommandSourceStack","hasPermission","(I)Z"),new InsnNode(Opcodes.IRETURN)));
        MethodNode execute=method(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC,"execute",CMD_DESC,new VarInsnNode(Opcodes.ALOAD,0),new LdcInsnNode("value"),call(Opcodes.INVOKESTATIC,B+"arguments/IntegerArgumentType","getInteger","(L"+B+"context/CommandContext;Ljava/lang/String;)I"),new VarInsnNode(Opcodes.ISTORE,1));increment(execute,H,"executions");add(execute,new VarInsnNode(Opcodes.ILOAD,1),field(Opcodes.PUTSTATIC,H,"lastInput","I"),call(Opcodes.INVOKESTATIC,C,"enabled","()Z"));LabelNode disabled=new LabelNode(),join=new LabelNode();add(execute,new JumpInsnNode(Opcodes.IFEQ,disabled),call(Opcodes.INVOKESTATIC,C,"factor","()I"),new VarInsnNode(Opcodes.ILOAD,1),new InsnNode(Opcodes.IMUL),new JumpInsnNode(Opcodes.GOTO,join),disabled,new InsnNode(Opcodes.ICONST_0),join,field(Opcodes.PUTSTATIC,H,"lastResult","I"),field(Opcodes.GETSTATIC,H,"lastResult","I"),new InsnNode(Opcodes.IRETURN));cmd.methods.add(execute);
        getter(cmd,"registrationCount","registrations","I");getter(cmd,"executionCount","executions","I");getter(cmd,"lastInput","lastInput","I");getter(cmd,"lastResult","lastResult","I");return nodes;
    }
    private static ClassNode node(String name,String parent){var n=new ClassNode();n.visit(Opcodes.V1_8,Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL|Opcodes.ACC_SUPER,name,null,parent,null);n.sourceFile=name.substring(name.lastIndexOf('/')+1)+".java";return n;}
    private static MethodNode method(int access,String name,String desc,AbstractInsnNode... code){var m=new MethodNode(access,name,desc,null,null);m.maxLocals=4;m.maxStack=8;add(m,code);return m;}
    private static void add(MethodNode m,AbstractInsnNode... code){for(var n:code)m.instructions.add(n);}
    private static MethodInsnNode call(int op,String owner,String name,String desc){return new MethodInsnNode(op,owner,name,desc,op==Opcodes.INVOKEINTERFACE);}
    private static FieldInsnNode field(int op,String owner,String name,String desc){return new FieldInsnNode(op,owner,name,desc);}
    private static void state(ClassNode n,String name,String desc){n.fields.add(new FieldNode(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC,name,desc,null,null));}
    private static void objectCtor(ClassNode n){n.methods.add(method(Opcodes.ACC_PRIVATE,"<init>","()V",new VarInsnNode(Opcodes.ALOAD,0),call(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V"),new InsnNode(Opcodes.RETURN)));}
    private static void increment(MethodNode m,String owner,String name){add(m,field(Opcodes.GETSTATIC,owner,name,"I"),new InsnNode(Opcodes.ICONST_1),new InsnNode(Opcodes.IADD),field(Opcodes.PUTSTATIC,owner,name,"I"));}
    private static void getter(ClassNode n,String name,String field,String type){n.methods.add(method(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,name,"()"+type,field(Opcodes.GETSTATIC,n.name,field,type),new InsnNode(type.startsWith("L")?Opcodes.ARETURN:Opcodes.IRETURN)));}
    private static void configGetter(ClassNode n,String name,String field,String subtype,String boxed,String unbox,String result){n.methods.add(method(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,name,"()"+result,field(Opcodes.GETSTATIC,n.name,field,"L"+SPEC+"$"+subtype+";"),call(Opcodes.INVOKEVIRTUAL,SPEC+"$"+subtype,"get","()Ljava/lang/Object;"),new TypeInsnNode(Opcodes.CHECKCAST,boxed),call(Opcodes.INVOKEVIRTUAL,boxed,unbox,"()"+result),new InsnNode(Opcodes.IRETURN)));}
    private static InvokeDynamicInsnNode dynamic(String contract,String samName,String sam,String owner,String method,String instantiated){return new InvokeDynamicInsnNode(samName,"()L"+contract+";",META,Type.getMethodType(sam),new Handle(Opcodes.H_INVOKESTATIC,owner,method,instantiated,false),Type.getMethodType(instantiated));}
    private static MethodNode named(Map<String,ClassNode> nodes,String owner,String method){return nodes.get(owner).methods.stream().filter(m->m.name.equals(method)).findFirst().orElseThrow();}
    private static List<AbstractInsnNode> code(MethodNode m){var code=new ArrayList<AbstractInsnNode>();for(var i:m.instructions)if(i.getOpcode()>=0)code.add(i);return code;}
    private static void replace(MethodNode m,int index,AbstractInsnNode replacement){m.instructions.set(code(m).get(index),replacement);}
    private static void insertBeforeReturn(MethodNode m,AbstractInsnNode... instructions){for(var i:instructions)m.instructions.insertBefore(m.instructions.getLast(),i);}
    private static Map<String,byte[]> bytes(Map<String,ClassNode> nodes){Map<String,byte[]> values=new TreeMap<>();nodes.forEach((n,c)->{var w=new ClassWriter(0);c.accept(w);values.put(n,w.toByteArray());});return values;}
}
