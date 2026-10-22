package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.conversion.extract.ContentPlan;
import dev.legacybridge.converter.transformation.TransformationException;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Closed effect and phase envelope, checked after the independent external-symbol compiler.
 * No source code is executed. The resource plan must not replace any owned method body.
 */
final class LifecycleProfileShape {
    private static final String F = "net/minecraftforge/";
    private static final String SETUP = F + "fml/event/lifecycle/FMLCommonSetupEvent";
    private static final String COMMANDS = F + "event/RegisterCommandsEvent";
    private static final String SUB = "L" + F + "fml/common/Mod$EventBusSubscriber;";
    private static final String SUBSCRIBE = "L" + F + "eventbus/api/SubscribeEvent;";
    private static final String MOD = "L" + F + "fml/common/Mod;";
    private static final String ITEM = "net/minecraft/world/item/Item";
    private static final String SPEC = F + "common/ForgeConfigSpec";
    private static final String REG = F + "registries/RegistryObject";
    private static final String BUS = F + "eventbus/api/IEventBus";
    private static final String CONTEXT = F + "fml/javafmlmod/FMLJavaModLoadingContext";
    private static final String B = "com/mojang/brigadier/";
    private static final String COMMAND_CONTEXT = "(L" + B + "context/CommandContext;)I";
    private static final String PERMISSION = "(Lnet/minecraft/commands/CommandSourceStack;)Z";
    private static final String METAFACTORY = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;";
    record ConfigShape(FieldNode spec, MethodNode enabled, MethodNode factor) { }
    private LifecycleProfileShape() { }

    static ContentPlan validate(Map<String, ClassNode> nodes, String modId, String version) {
        require(nodes != null && nodes.size() == 4, "Exactly entry, item, config and command classes are required");
        for (var pair : nodes.entrySet()) {
            ClassNode node = pair.getValue();
            require(node != null && pair.getKey().equals(node.name)
                    && !dev.legacybridge.converter.transformation.LegacyReferenceScanner.isProtectedDefinition(node.name),
                    "Owned map identity/protected namespace");
            require(node.version == Opcodes.V1_8 && node.interfaces.isEmpty()
                    && (node.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT))
                    == (Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL), "Only four final public Java 8 classes are admitted");
            require(node.methods.size() <= 32 && node.fields.size() <= 32, "Member limit");
            Set<String> members = new HashSet<>();
            for (MethodNode method : node.methods) require(method.instructions.size() <= 1024
                    && members.add(method.name + method.desc), "Instruction or duplicate-method limit");
            members.clear();
            for (FieldNode field : node.fields) require(members.add(field.name + field.desc), "Duplicate field");
        }
        ClassNode entry = one(nodes, n -> annotations(n).stream().anyMatch(a -> a.desc.equals(MOD)), "entrypoint");
        ClassNode item = one(nodes, n -> ITEM.equals(n.superName), "item");
        ClassNode config = one(nodes, n -> n.fields.stream().anyMatch(f -> f.desc.equals("L" + SPEC + ";")), "config");
        ClassNode commands = one(nodes, n -> annotations(n).stream().anyMatch(a -> a.desc.equals(SUB)), "command subscriber");
        require(new HashSet<>(List.of(entry.name, item.name, config.name, commands.name)).size() == 4, "Distinct owned roles required");
        for (ClassNode node : List.of(entry, config, commands)) require("java/lang/Object".equals(node.superName), "Unknown base");
        require(item.methods.size() == 1 && item.fields.isEmpty() && code(method(item, "<init>", "()V")).size() == 8,
                "LIFECYCLE_CONSTRUCTOR_ESCAPE: only the exact plain Item constructor");
        require(annotations(item).isEmpty() && annotations(config).isEmpty() && annotations(entry).size() == 1, "Unexpected role annotations");
        validateSubscriber(commands, modId);
        MethodNode ctor = method(entry, "<init>", "()V"), init = method(entry, "<clinit>", "()V");
        List<AbstractInsnNode> c = code(ctor);
        require(c.size() == 18 || c.size() == 15, "Entrypoint effects/order differ from attachment/config/listener envelope");
        ClassNode projected = new ClassNode(); entry.accept(projected);
        projected.fields.removeIf(f -> !Set.of("Ljava/lang/String;", "L" + F + "registries/DeferredRegister;", "L" + REG + ";").contains(f.desc));
        projected.methods.removeIf(m -> !m.name.equals("<init>") && !m.name.equals("<clinit>"));
        MethodNode projectedCtor = method(projected, "<init>", "()V");
        while (code(projectedCtor).size() > 6) projectedCtor.instructions.remove(projectedCtor.instructions.getLast());
        projectedCtor.instructions.add(new InsnNode(Opcodes.RETURN));
        ContentPlan resourcePlan = LogicProfileShape.validate(Map.of(entry.name, projected, item.name, item), modId, version);
        noFlow(ctor); noFlow(init); noFlow(method(item, "<init>", "()V"));
        opcode(code(method(item, "<init>", "()V")).get(7), Opcodes.RETURN);
        ConfigShape cfg = validateConfig(config);
        call(c.get(6), Opcodes.INVOKESTATIC, F + "fml/ModLoadingContext", "get", "()L" + F + "fml/ModLoadingContext;");
        field(c.get(7), Opcodes.GETSTATIC, F + "fml/config/ModConfig$Type", "COMMON", "L" + F + "fml/config/ModConfig$Type;");
        field(c.get(8), Opcodes.GETSTATIC, config.name, cfg.spec.name, cfg.spec.desc);
        call(c.get(9), Opcodes.INVOKEVIRTUAL, F + "fml/ModLoadingContext", "registerConfig",
                "(L" + F + "fml/config/ModConfig$Type;L" + F + "fml/config/IConfigSpec;)V");
        call(c.get(10), Opcodes.INVOKESTATIC, CONTEXT, "get", "()L" + CONTEXT + ";");
        call(c.get(11), Opcodes.INVOKEVIRTUAL, CONTEXT, "getModEventBus", "()L" + BUS + ";");
        boolean explicitEvent = c.size() == 18;
        if (explicitEvent) {
            field(c.get(12), Opcodes.GETSTATIC, F + "eventbus/api/EventPriority", "NORMAL", "L" + F + "eventbus/api/EventPriority;");
            opcode(c.get(13), Opcodes.ICONST_0);
            require(c.get(14) instanceof LdcInsnNode type && Type.getObjectType(SETUP).equals(type.cst),
                    "LIFECYCLE_WRONG_SIDE_OR_EVENT: expected common setup class token");
        }
        Handle setup = callback(c.get(explicitEvent ? 15 : 12), "java/util/function/Consumer", "accept", "(Ljava/lang/Object;)V", "(L" + SETUP + ";)V", entry.name);
        // The inferred native overload is preserved, never converted into a guessed Class overload.
        call(c.get(explicitEvent ? 16 : 13), Opcodes.INVOKEINTERFACE, BUS, "addListener", explicitEvent
                ? "(L" + F + "eventbus/api/EventPriority;ZLjava/lang/Class;Ljava/util/function/Consumer;)V" : "(Ljava/util/function/Consumer;)V");
        opcode(c.get(explicitEvent ? 17 : 14), Opcodes.RETURN);
        MethodNode setupMethod = method(entry, setup.getName(), setup.getDesc()); staticConcrete(setupMethod);
        List<AbstractInsnNode> s = code(setupMethod); noFlow(setupMethod);
        require(s.size() == 9, "LIFECYCLE_QUEUE_ENVELOPE: only increment, enqueue, discard future and return");
        FieldNode setupCounter = increment(s, 0, entry);
        variable(s.get(4), Opcodes.ALOAD, 0);
        Handle work = callback(s.get(5), "java/lang/Runnable", "run", "()V", "()V", entry.name);
        call(s.get(6), Opcodes.INVOKEVIRTUAL, SETUP, "enqueueWork", "(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;");
        opcode(s.get(7), Opcodes.POP); opcode(s.get(8), Opcodes.RETURN);
        MethodNode workMethod = method(entry, work.getName(), work.getDesc()); staticConcrete(workMethod);
        require(setupMethod != workMethod && setupMethod != init && workMethod != init, "Distinct lifecycle callbacks");
        List<AbstractInsnNode> w = code(workMethod); noFlow(workMethod);
        require(w.size() == 13, "LIFECYCLE_WORK_ENVELOPE: only proven late reads/state writes; no opaque effects or implicit-throw operators");
        FieldInsnNode registry = (FieldInsnNode) code(init).get(8); // original exact registration, already proven above
        field(w.get(0), Opcodes.GETSTATIC, entry.name, registry.name, "L" + REG + ";");
        call(w.get(1), Opcodes.INVOKEVIRTUAL, REG, "get", "()Ljava/lang/Object;");
        cast(w.get(2), ITEM);
        FieldNode itemState = stateField(w.get(3), Opcodes.PUTSTATIC, entry, "L" + ITEM + ";");
        call(w.get(4), Opcodes.INVOKESTATIC, config.name, cfg.enabled.name, "()Z");
        FieldNode enabledState = stateField(w.get(5), Opcodes.PUTSTATIC, entry, "Z");
        call(w.get(6), Opcodes.INVOKESTATIC, config.name, cfg.factor.name, "()I");
        FieldNode factorState = stateField(w.get(7), Opcodes.PUTSTATIC, entry, "I");
        FieldNode workCounter = increment(w, 8, entry);
        opcode(w.get(12), Opcodes.RETURN);
        Set<String> lifecycleState = new HashSet<>(List.of(setupCounter.name, workCounter.name, itemState.name, enabledState.name, factorState.name));
        require(lifecycleState.size() == 5, "Lifecycle state roles must be distinct");
        require(entry.fields.size() == 8, "LIFECYCLE_STATE_ESCAPE: only original registration and five primitive/item state fields");
        for (FieldNode f : entry.fields) require(projected.fields.stream().anyMatch(p -> p.name.equals(f.name) && p.desc.equals(f.desc))
                || lifecycleState.contains(f.name), "Unknown lifecycle state");
        for (MethodNode m : entry.methods) if (m != ctor && m != init && m != setupMethod && m != workMethod) pureGetter(m, entry);
        validateCommands(commands, config, cfg);
        for (ClassNode node : nodes.values()) for (MethodNode m : node.methods) {
            if (!m.name.equals("<init>")) staticConcrete(m);
            List<AnnotationNode> methodAnnotations = annotations(m);
            require(methodAnnotations.isEmpty() || node == commands && m.desc.equals("(L" + COMMANDS + ";)V")
                    && methodAnnotations.size() == 1 && methodAnnotations.get(0).desc.equals(SUBSCRIBE),
                    "Unexpected method subscriber placement");
        }
        return resourcePlan;
    }

    static ConfigShape validateConfig(ClassNode config) {
        require(config.fields.size() == 3 && config.methods.size() == 4, "One COMMON spec, boolean/int values and two direct getters only");
        FieldNode spec = onlyField(config, "L" + SPEC + ";"), enabled = onlyField(config, "L" + SPEC + "$BooleanValue;"), factor = onlyField(config, "L" + SPEC + "$IntValue;");
        for (FieldNode f : config.fields) require((f.access & (Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)) == (Opcodes.ACC_STATIC | Opcodes.ACC_FINAL) && f.value == null, "Native schema fields must be final, not fake constant values");
        objectConstructor(config);
        MethodNode initializer = method(config, "<clinit>", "()V"); noFlow(initializer); staticConcrete(initializer);
        List<AbstractInsnNode> c = code(initializer);
        require(c.size() == 20, "LIFECYCLE_CONFIG_INIT: exact single builder and two literal definitions only");
        require(c.get(0) instanceof TypeInsnNode n && n.getOpcode() == Opcodes.NEW && n.desc.equals(SPEC + "$Builder"), "Native config builder required");
        opcode(c.get(1), Opcodes.DUP); call(c.get(2), Opcodes.INVOKESPECIAL, SPEC + "$Builder", "<init>", "()V");
        variable(c.get(3), Opcodes.ASTORE, 0); variable(c.get(4), Opcodes.ALOAD, 0);
        String booleanKey = key(c.get(5)); require(c.get(6).getOpcode() == Opcodes.ICONST_0 || c.get(6).getOpcode() == Opcodes.ICONST_1, "Literal boolean default");
        call(c.get(7), Opcodes.INVOKEVIRTUAL, SPEC + "$Builder", "define", "(Ljava/lang/String;Z)L" + SPEC + "$BooleanValue;");
        field(c.get(8), Opcodes.PUTSTATIC, config.name, enabled.name, enabled.desc);
        variable(c.get(9), Opcodes.ALOAD, 0); String integerKey = key(c.get(10));
        require(!booleanKey.equals(integerKey), "Config keys must be distinct");
        int def = integer(c.get(11)), min = integer(c.get(12)), max = integer(c.get(13));
        require(min <= def && def <= max, "Config default excludes its range");
        call(c.get(14), Opcodes.INVOKEVIRTUAL, SPEC + "$Builder", "defineInRange", "(Ljava/lang/String;III)L" + SPEC + "$IntValue;");
        field(c.get(15), Opcodes.PUTSTATIC, config.name, factor.name, factor.desc);
        variable(c.get(16), Opcodes.ALOAD, 0); call(c.get(17), Opcodes.INVOKEVIRTUAL, SPEC + "$Builder", "build", "()L" + SPEC + ";");
        field(c.get(18), Opcodes.PUTSTATIC, config.name, spec.name, spec.desc); opcode(c.get(19), Opcodes.RETURN);
        MethodNode boolGetter = onlyMethod(config, "()Z"), intGetter = onlyMethod(config, "()I");
        configGetter(config, boolGetter, enabled, "java/lang/Boolean", "booleanValue", "()Z");
        configGetter(config, intGetter, factor, "java/lang/Integer", "intValue", "()I");
        return new ConfigShape(spec, boolGetter, intGetter);
    }

    private static void configGetter(ClassNode owner, MethodNode getter, FieldNode value, String boxed, String name, String desc) {
        noFlow(getter); staticConcrete(getter); List<AbstractInsnNode> c = code(getter);
        require(c.size() == 5, "Direct native config getter required");
        field(c.get(0), Opcodes.GETSTATIC, owner.name, value.name, value.desc);
        call(c.get(1), Opcodes.INVOKEVIRTUAL, Type.getType(value.desc).getInternalName(), "get", "()Ljava/lang/Object;");
        cast(c.get(2), boxed); call(c.get(3), Opcodes.INVOKEVIRTUAL, boxed, name, desc); opcode(c.get(4), Opcodes.IRETURN);
    }

    private static void validateCommands(ClassNode commands, ClassNode config, ConfigShape cfg) {
        objectConstructor(commands);
        for (FieldNode f : commands.fields) require(f.desc.equals("I") && (f.access & Opcodes.ACC_STATIC) != 0
                && (f.access & Opcodes.ACC_FINAL) == 0 && f.value == null, "Only private command observation counters/state are reviewed");
        List<MethodNode> registrations = commands.methods.stream().filter(m -> annotations(m).stream().anyMatch(a -> a.desc.equals(SUBSCRIBE))).toList();
        require(registrations.size() == 1, "Exactly one command subscriber");
        MethodNode registration = registrations.get(0); staticConcrete(registration);
        require(registration.desc.equals("(L" + COMMANDS + ";)V") && (registration.access & Opcodes.ACC_PUBLIC) != 0,
                "LIFECYCLE_WRONG_SIDE_OR_EVENT: only native server command registration");
        require(annotations(registration).size() == 1 && (annotations(registration).get(0).values == null || annotations(registration).get(0).values.isEmpty()), "Only default NORMAL/receiveCanceled=false annotation");
        Set<MethodNode> permissionCallbacks = new HashSet<>(), executionCallbacks = new HashSet<>();
        for (AbstractInsnNode n : registration.instructions) if (n instanceof InvokeDynamicInsnNode d) {
            require(Type.getReturnType(d.desc).getSort() == Type.OBJECT, "Unknown command callback result");
            String result = Type.getReturnType(d.desc).getInternalName();
            Handle h;
            if (result.equals("java/util/function/Predicate")) {
                h = callback(d, result, "test", "(Ljava/lang/Object;)Z", PERMISSION, commands.name);
                permissionCallbacks.add(method(commands, h.getName(), h.getDesc()));
            } else {
                require(result.equals(B + "Command"), "Unknown command callback type");
                h = callback(d, result, "run", COMMAND_CONTEXT, COMMAND_CONTEXT, commands.name);
                executionCallbacks.add(method(commands, h.getName(), h.getDesc()));
            }
        }
        require(permissionCallbacks.size() == 1 && executionCallbacks.size() == 1, "Exactly one linked permission and execution callback");
        for (MethodNode m : commands.methods) {
            if (m.name.equals("<init>")) continue;
            staticConcrete(m);
            boolean permission = permissionCallbacks.contains(m), execute = executionCallbacks.contains(m), registering = m == registration;
            if (!permission && !execute && !registering) { pureGetter(m, commands); continue; }
            for (AbstractInsnNode n : m.instructions) {
                if (n instanceof MethodInsnNode c) {
                    boolean allowed;
                    if (c.owner.equals(config.name)) {
                        allowed = execute && c.getOpcode() == Opcodes.INVOKESTATIC && !c.itf
                                && (c.name.equals(cfg.enabled.name) && c.desc.equals("()Z") || c.name.equals(cfg.factor.name) && c.desc.equals("()I"));
                    } else if (registering) allowed = registrationCall(c);
                    else if (permission) allowed = c.getOpcode() == Opcodes.INVOKEVIRTUAL && !c.itf && c.owner.equals("net/minecraft/commands/CommandSourceStack") && c.desc.equals("(I)Z");
                    else allowed = c.getOpcode() == Opcodes.INVOKESTATIC && !c.itf && c.owner.equals(B + "arguments/IntegerArgumentType")
                                && c.name.equals("getInteger") && c.desc.equals("(L" + B + "context/CommandContext;Ljava/lang/String;)I");
                    require(allowed, "LIFECYCLE_PHASE_ESCAPE: command calls must belong to the actually linked callback and its reviewed phase");
                } else if (n instanceof FieldInsnNode f) stateField(f, f.getOpcode(), commands, "I");
                else if (n instanceof InvokeDynamicInsnNode) require(registering, "LIFECYCLE_PHASE_ESCAPE: callbacks cannot manufacture further callbacks");
                else if (n instanceof TypeInsnNode t) require(t.getOpcode() == Opcodes.CHECKCAST
                        && registering && Set.of(B + "builder/LiteralArgumentBuilder", B + "builder/RequiredArgumentBuilder").contains(t.desc),
                        "LIFECYCLE_CONSTRUCTOR_ESCAPE: no allocation/opaque casts in command callbacks");
                else if (n instanceof LdcInsnNode l) require(l.cst instanceof String || l.cst instanceof Integer, "Unknown command constant/handle");
                else require(n.getOpcode() < 0 || safeCommandOpcode(n.getOpcode()), "Unsupported command opcode");
            }
        }
    }

    private static boolean registrationCall(MethodInsnNode c) {
        if (c.itf) return false;
        return c.getOpcode() == Opcodes.INVOKEVIRTUAL && (
                c.owner.equals(COMMANDS) && c.name.equals("getDispatcher") && c.desc.equals("()L" + B + "CommandDispatcher;")
                || c.owner.equals(B + "CommandDispatcher") && c.name.equals("register") && c.desc.equals("(L" + B + "builder/LiteralArgumentBuilder;)L" + B + "tree/LiteralCommandNode;")
                || c.owner.equals(B + "builder/LiteralArgumentBuilder") && c.name.equals("requires") && c.desc.equals("(Ljava/util/function/Predicate;)L" + B + "builder/ArgumentBuilder;")
                || c.owner.equals(B + "builder/LiteralArgumentBuilder") && c.name.equals("then") && c.desc.equals("(L" + B + "builder/ArgumentBuilder;)L" + B + "builder/ArgumentBuilder;")
                || c.owner.equals(B + "builder/RequiredArgumentBuilder") && c.name.equals("executes") && c.desc.equals("(L" + B + "Command;)L" + B + "builder/ArgumentBuilder;"))
                || c.getOpcode() == Opcodes.INVOKESTATIC && (
                c.owner.equals("net/minecraft/commands/Commands") && Set.of("(Ljava/lang/String;)L" + B + "builder/LiteralArgumentBuilder;", "(Ljava/lang/String;L" + B + "arguments/ArgumentType;)L" + B + "builder/RequiredArgumentBuilder;").contains(c.desc)
                || c.owner.equals(B + "arguments/IntegerArgumentType") && c.name.equals("integer") && c.desc.equals("(II)L" + B + "arguments/IntegerArgumentType;"));
    }

    private static boolean safeCommandOpcode(int op) {
        return Set.of(Opcodes.NOP, Opcodes.ICONST_M1, Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2, Opcodes.ICONST_3, Opcodes.ICONST_4, Opcodes.ICONST_5,
                Opcodes.BIPUSH, Opcodes.SIPUSH, Opcodes.ILOAD, Opcodes.ISTORE, Opcodes.ALOAD, Opcodes.ASTORE,
                Opcodes.IADD, Opcodes.ISUB, Opcodes.IMUL, Opcodes.IDIV, Opcodes.IREM, Opcodes.INEG, Opcodes.IAND, Opcodes.IOR, Opcodes.IXOR,
                Opcodes.ISHL, Opcodes.ISHR, Opcodes.IUSHR, Opcodes.IINC, Opcodes.POP, Opcodes.DUP,
                Opcodes.IFEQ, Opcodes.IFNE, Opcodes.IFLT, Opcodes.IFGE, Opcodes.IFGT, Opcodes.IFLE,
                Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE, Opcodes.IF_ICMPLT, Opcodes.IF_ICMPGE, Opcodes.IF_ICMPGT, Opcodes.IF_ICMPLE,
                Opcodes.GOTO, Opcodes.TABLESWITCH, Opcodes.LOOKUPSWITCH, Opcodes.IRETURN, Opcodes.RETURN).contains(op);
    }

    private static void validateSubscriber(ClassNode node, String modId) {
        require(annotations(node).size() == 1, "Unexpected subscriber annotations");
        AnnotationNode a = annotations(node).get(0);
        require(a.values != null && a.values.size() == 4, "Only explicit modid and FORGE bus; no side-specific subscriber");
        Map<String,Object> values = new HashMap<>();
        for (int i=0;i<a.values.size();i+=2) require(a.values.get(i) instanceof String && values.put((String)a.values.get(i),a.values.get(i+1)) == null, "Duplicate annotation value");
        require(modId.equals(values.get("modid")) && values.get("bus") instanceof String[] bus
                && Arrays.equals(bus, new String[]{"L"+F+"fml/common/Mod$EventBusSubscriber$Bus;", "FORGE"}),
                "LIFECYCLE_WRONG_SIDE_OR_BUS: command events require Forge bus and matching modid");
    }
    private static void pureGetter(MethodNode m, ClassNode owner) {
        staticConcrete(m); noFlow(m); List<AbstractInsnNode> c = code(m);
        require(c.size() == 2 && m.desc.startsWith("()"), "Only passive observation getters outside linked callbacks");
        String returned = Type.getReturnType(m.desc).getDescriptor();
        require(Set.of("I", "Z", "L" + ITEM + ";").contains(returned), "LIFECYCLE_STATE_ESCAPE: opaque observation value");
        stateField(c.get(0), Opcodes.GETSTATIC, owner, returned);
        opcode(c.get(1), returned.startsWith("L") ? Opcodes.ARETURN : Opcodes.IRETURN);
    }
    private static void objectConstructor(ClassNode node) {
        require(node.methods.stream().filter(m -> m.name.equals("<init>")).count() == 1, "One inert helper constructor only");
        MethodNode ctor = method(node, "<init>", "()V"); noFlow(ctor); List<AbstractInsnNode> c = code(ctor);
        require(c.size() == 3 && (ctor.access & Opcodes.ACC_STATIC) == 0, "LIFECYCLE_CONSTRUCTOR_ESCAPE: helper constructors must only initialize Object");
        variable(c.get(0), Opcodes.ALOAD, 0); call(c.get(1), Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V"); opcode(c.get(2), Opcodes.RETURN);
    }
    private static FieldNode increment(List<AbstractInsnNode> c, int at, ClassNode owner) {
        FieldNode field = stateField(c.get(at), Opcodes.GETSTATIC, owner, "I");
        opcode(c.get(at+1), Opcodes.ICONST_1); opcode(c.get(at+2), Opcodes.IADD);
        field(c.get(at+3), Opcodes.PUTSTATIC, owner.name, field.name, field.desc);
        return field;
    }
    private static FieldNode stateField(AbstractInsnNode n, int op, ClassNode owner, String desc) {
        require(op == Opcodes.GETSTATIC || op == Opcodes.PUTSTATIC, "Only native owned static state");
        require(n instanceof FieldInsnNode f && f.getOpcode() == op && f.owner.equals(owner.name) && f.desc.equals(desc), "LIFECYCLE_STATE_ESCAPE: wrong state owner/type");
        FieldInsnNode f = (FieldInsnNode)n;
        FieldNode declared = owner.fields.stream().filter(v -> v.name.equals(f.name) && v.desc.equals(desc)).findFirst().orElseThrow(() -> failure("Unknown owned state field"));
        require((declared.access & Opcodes.ACC_STATIC) != 0 && (declared.access & Opcodes.ACC_FINAL) == 0 && declared.value == null, "Mutable nonconstant owned state required");
        return declared;
    }
    private static Handle callback(AbstractInsnNode n, String contract, String name, String sam, String instantiated, String owner) {
        require(n instanceof InvokeDynamicInsnNode, "Exact owned static callback required");
        InvokeDynamicInsnNode d = (InvokeDynamicInsnNode)n;
        require(d.desc.equals("()L"+contract+";") && d.name.equals(name) && d.bsm.getTag() == Opcodes.H_INVOKESTATIC && !d.bsm.isInterface()
                && d.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory") && d.bsm.getName().equals("metafactory") && d.bsm.getDesc().equals(METAFACTORY)
                && d.bsmArgs.length == 3 && Type.getMethodType(sam).equals(d.bsmArgs[0]) && Type.getMethodType(instantiated).equals(d.bsmArgs[2])
                && d.bsmArgs[1] instanceof Handle h && h.getTag() == Opcodes.H_INVOKESTATIC && !h.isInterface()
                && h.getOwner().equals(owner) && h.getDesc().equals(instantiated) && !h.getName().startsWith("<"), "LIFECYCLE_CALLBACK_ESCAPE: exact zero-capture owned static callback required");
        return (Handle)d.bsmArgs[1];
    }
    private static void noFlow(MethodNode m) {
        require(m.tryCatchBlocks.isEmpty(), "Lifecycle/config cannot swallow exceptions");
        for (AbstractInsnNode n:m.instructions) require(!(n instanceof JumpInsnNode || n instanceof TableSwitchInsnNode || n instanceof LookupSwitchInsnNode) && n.getOpcode()!=Opcodes.ATHROW, "Lifecycle/config cannot branch, loop or throw");
    }
    private static void staticConcrete(MethodNode m) { require((m.access & (Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT | Opcodes.ACC_SYNCHRONIZED)) == Opcodes.ACC_STATIC, "Concrete static owned callback/helper required"); }
    private static int integer(AbstractInsnNode n) {
        if(n.getOpcode()>=Opcodes.ICONST_M1 && n.getOpcode()<=Opcodes.ICONST_5) return n.getOpcode()-Opcodes.ICONST_0;
        if(n instanceof IntInsnNode i && (i.getOpcode()==Opcodes.BIPUSH || i.getOpcode()==Opcodes.SIPUSH)) return i.operand;
        if(n instanceof LdcInsnNode c && c.cst instanceof Integer i) return i;
        throw failure("Expected integer literal");
    }
    private static String key(AbstractInsnNode n) { require(n instanceof LdcInsnNode c && c.cst instanceof String s && s.matches("[A-Za-z][A-Za-z0-9_-]{0,63}"), "Simple literal config key required"); return (String)((LdcInsnNode)n).cst; }
    private static FieldNode onlyField(ClassNode n, String desc) { List<FieldNode> fields=n.fields.stream().filter(f -> f.desc.equals(desc)).toList(); require(fields.size()==1,"Unique schema field "+desc); return fields.get(0); }
    private static MethodNode onlyMethod(ClassNode n, String desc) { List<MethodNode> methods=n.methods.stream().filter(m -> m.desc.equals(desc)).toList(); require(methods.size()==1,"Unique config getter "+desc); return methods.get(0); }
    private static ClassNode one(Map<String,ClassNode> nodes, java.util.function.Predicate<ClassNode> predicate, String role) { List<ClassNode> found=nodes.values().stream().filter(predicate).toList(); require(found.size()==1,"Expected one "+role); return found.get(0); }
    private static MethodNode method(ClassNode node,String name,String desc) { return node.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).findFirst().orElseThrow(()->failure("Missing method "+node.name+"."+name+desc)); }
    private static List<AbstractInsnNode> code(MethodNode m) { List<AbstractInsnNode> c=new ArrayList<>(); for(AbstractInsnNode n:m.instructions) if(n.getOpcode()>=0)c.add(n); return c; }
    private static List<AnnotationNode> annotations(ClassNode n) { List<AnnotationNode> a=new ArrayList<>(); if(n.visibleAnnotations!=null)a.addAll(n.visibleAnnotations); if(n.invisibleAnnotations!=null)a.addAll(n.invisibleAnnotations); return a; }
    private static List<AnnotationNode> annotations(MethodNode n) { List<AnnotationNode> a=new ArrayList<>(); if(n.visibleAnnotations!=null)a.addAll(n.visibleAnnotations); if(n.invisibleAnnotations!=null)a.addAll(n.invisibleAnnotations); return a; }
    private static void call(AbstractInsnNode n,int opcode,String owner,String name,String desc) { require(n instanceof MethodInsnNode c && c.getOpcode()==opcode && c.itf==(opcode==Opcodes.INVOKEINTERFACE) && c.owner.equals(owner)&&c.name.equals(name)&&c.desc.equals(desc),"Unexpected lifecycle call/order: "+owner+"."+name+desc); }
    private static void field(AbstractInsnNode n,int opcode,String owner,String name,String desc) { require(n instanceof FieldInsnNode f&&f.getOpcode()==opcode&&f.owner.equals(owner)&&f.name.equals(name)&&f.desc.equals(desc),"Unexpected lifecycle field/scope/priority"); }
    private static void variable(AbstractInsnNode n,int op,int var) { require(n instanceof VarInsnNode v && v.getOpcode()==op && v.var==var,"Unexpected lifecycle local"); }
    private static void opcode(AbstractInsnNode n,int op) { require(n.getOpcode()==op,"Unexpected lifecycle opcode "+n.getOpcode()+" expected "+op); }
    private static void cast(AbstractInsnNode n,String type) { require(n instanceof TypeInsnNode t && t.getOpcode()==Opcodes.CHECKCAST && t.desc.equals(type),"Exact checked cast required"); }
    private static void require(boolean ok,String message) { if(!ok)throw failure(message); }
    private static TransformationException failure(String message) { return new TransformationException("LIFECYCLE_PROFILE_SHAPE: "+message); }
}
