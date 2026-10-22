package dev.legacybridge.converter.transformation.linkage;

import dev.legacybridge.converter.mapping.source.ApiIndex;
import dev.legacybridge.converter.mapping.source.ApiIndex.ApiClass;
import dev.legacybridge.converter.mapping.source.ApiIndex.ApiMember;
import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.operation.OperationStoppedException;
import dev.legacybridge.converter.transformation.BytecodeValidator;
import dev.legacybridge.converter.transformation.TransformationException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

/**
 * Additional fail-closed gate for logic-preserving conversion. Never defines, links or initializes
 * classes. Resolution/access follow the declaration subset of JVMS 5.4.3-5.4.6; constructor-state
 * checking is a stricter supported language derived from JVMS 4.10.1.9, not a general JVM verifier.
 * The caller must supply external declarations already normalized to the actual production namespace.
 */
public final class OfflineLinkageVerifier {
    /** Independent callback admission policies; networking must never widen a legacy profile implicitly. */
    public enum CallbackPolicy { LOGIC, LIFECYCLE, NETWORK_SNAPSHOT, OWNED_EVENT_PRIORITY, CLIENT_CONFIG, SERVER_CONFIG, LIFECYCLE_SUITE, HORIZONTAL_BLOCK, STATIC_SHAPE_BLOCK, BOOLEAN_TOGGLE_BLOCK, ITEM_ATTRIBUTES, ITEM_ATTRIBUTE_DURABILITY, OWNED_STACK_DAMAGE, OWNED_STACK_REPAIR_USE }

    public record Summary(String status, int ownedClassCount, int resolvedClassCount, int checkedMethodCount,
                          int methodReferenceCount, int fieldReferenceCount, int lambdaSiteCount, List<String> limitations) {
        public Summary { limitations = List.copyOf(limitations); }
        public Map<String, Object> toTree() {
            return Map.of("schemaVersion", 1, "status", status, "ownedClassCount", ownedClassCount,
                    "resolvedClassCount", resolvedClassCount, "checkedMethodCount", checkedMethodCount,
                    "methodReferenceCount", methodReferenceCount, "fieldReferenceCount", fieldReferenceCount,
                    "lambdaSiteCount", lambdaSiteCount, "limitations", limitations, "classesExecuted", false);
        }
    }

    private static final String METAFACTORY = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;";
    private static final List<String> LIMITATIONS = List.of(
            "Static declaration/access/dataflow checks only; no JVM class loading, Forge discovery or runtime behavior was tested.",
            "Caller supplies complete production-namespace external declarations and separately validates external module/classloader wiring; ApiIndex has no module/nest/sealed metadata.",
            "Cross-owner package access, nestmates, owned sealed/record/annotation classes, default-interface dispatch, native code, jsr/ret and reflective linkage are unsupported.",
            "Constructor delegation through this(), uninitialized local aliases, branches or exception handlers with live uninitialized values are unsupported.",
            "Only LambdaMetafactory.metafactory Supplier and IntUnaryOperator with owned static implementations/exact captures, or zero-capture Supplier of an owned concrete Item subclass through its declared ()V constructor, are admitted; no boxing adaptation, altMetafactory, standalone handles or condy.");

    public Summary verify(Map<String, byte[]> ownedClasses, ApiIndex externalProductionNamespace) {
        return verify(ownedClasses, externalProductionNamespace, false);
    }

    public Summary verify(Map<String, byte[]> ownedClasses, ApiIndex externalProductionNamespace, boolean lifecycleCallbacks) {
        return verify(ownedClasses, externalProductionNamespace, lifecycleCallbacks ? CallbackPolicy.LIFECYCLE : CallbackPolicy.LOGIC);
    }

    public Summary verify(Map<String, byte[]> ownedClasses, ApiIndex externalProductionNamespace, CallbackPolicy policy) {
        return verify(ownedClasses, externalProductionNamespace, policy, null);
    }
    /** Separate private-candidate gate. The owner is re-read and its finite graph independently proved. */
    public Summary verifyOwnedConstructors(Map<String, byte[]> ownedClasses, ApiIndex externalProductionNamespace, String owner) {
        if (owner == null) throw new TransformationException("CONSTRUCTOR_OWNER_REQUIRED");
        return verify(ownedClasses, externalProductionNamespace, CallbackPolicy.LOGIC, owner);
    }
    private Summary verify(Map<String, byte[]> ownedClasses, ApiIndex externalProductionNamespace, CallbackPolicy policy, String delegationOwner) {
        return verify(ownedClasses, externalProductionNamespace, policy, delegationOwner, false);
    }
    /** Private gate: independently re-derive the finite interface proof from these exact bytes. */
    public Summary verifyOwnedInterfaceDefaults(Map<String, byte[]> ownedClasses, ApiIndex externalProductionNamespace) {
        return verify(ownedClasses, externalProductionNamespace, CallbackPolicy.LOGIC, null, true);
    }
    private Summary verify(Map<String, byte[]> ownedClasses, ApiIndex externalProductionNamespace, CallbackPolicy policy,
                           String delegationOwner, boolean interfaceDefaults) {
        return verify(ownedClasses, externalProductionNamespace, policy, delegationOwner, interfaceDefaults, false);
    }
    /** Private receiver candidate gate; dataflow must prove the actual captured value is initialized this. */
    public Summary verifyOwnedReceiverHandles(Map<String,byte[]> ownedClasses, ApiIndex externalProductionNamespace) {
        return verify(ownedClasses, externalProductionNamespace, CallbackPolicy.LOGIC, null, false, true);
    }
    private Summary verify(Map<String,byte[]> ownedClasses, ApiIndex externalProductionNamespace, CallbackPolicy policy,
                           String delegationOwner, boolean interfaceDefaults, boolean receiverHandles) {
        try { return new Check(ownedClasses, externalProductionNamespace, policy, delegationOwner, interfaceDefaults, receiverHandles).verify(); }
        catch (OperationStoppedException stopped) { throw stopped; }
        catch (TransformationException rejected) { throw rejected; }
        catch (RuntimeException | AnalyzerException malformed) {
            OperationStoppedException.rethrowIfPresent(malformed);
            throw new TransformationException("OFFLINE_LINKAGE_REJECTED: " + malformed.getMessage(), malformed);
        }
    }

    /** Header-only bytes for OfflineClassWriter hierarchy queries. These are metadata, never runnable adapters. */
    public static Map<String, byte[]> declarationHierarchy(ApiIndex externalProductionNamespace) {
        return Map.copyOf(new LinkageHierarchy(Map.of(), externalProductionNamespace).declarationBytes(true));
    }

    private static final class Check {
        private final Map<String, ClassNode> classes = new TreeMap<>();
        private final Map<String, byte[]> bytes = new TreeMap<>();
        private final LinkageHierarchy hierarchy;
        private final CallbackPolicy callbackPolicy;
        private final String delegationOwner;
        private final OwnedInterfaceDefaults.Proof interfaceProof;
        private final OwnedReceiverHandles.Proof receiverProof;
        private final dev.legacybridge.converter.conversion.EventPriorityContract.Proof eventProof;
        private final dev.legacybridge.converter.conversion.ClientConfigContract.Proof clientConfigProof;
        private final dev.legacybridge.converter.conversion.ServerConfigContract.Proof serverConfigProof;
        private final dev.legacybridge.converter.conversion.LifecycleSuiteContract.Proof suiteProof;
        private final dev.legacybridge.converter.conversion.HorizontalBlockContract.Proof horizontalProof;
        private final dev.legacybridge.converter.conversion.StaticShapeBlockContract.Proof staticShapeProof;
        private final dev.legacybridge.converter.conversion.BooleanToggleBlockContract.Proof booleanToggleProof;
        private final dev.legacybridge.converter.conversion.ItemAttributeContract.Proof itemAttributeProof;
        private final dev.legacybridge.converter.conversion.ItemAttributeDurabilityContract.Proof itemAttributeDurabilityProof;
        private final dev.legacybridge.converter.conversion.OwnedStackDamageContract.Proof ownedStackDamageProof;
        private final dev.legacybridge.converter.conversion.OwnedStackRepairUseContract.Proof ownedStackRepairUseProof;
        private final Map<String, Set<String>> opaqueRegistrationReturns = new TreeMap<>();
        private int methods, methodReferences, fieldReferences, lambdas, instructions;

        Check(Map<String, byte[]> inputs, ApiIndex external, CallbackPolicy callbackPolicy, String delegationOwner, boolean interfaceDefaults, boolean receiverHandles) {
            require(callbackPolicy != null, "CALLBACK_POLICY_REQUIRED", "Explicit callback policy must not be null");
            this.callbackPolicy = callbackPolicy;
            this.delegationOwner = delegationOwner;
            require(inputs != null && !inputs.isEmpty() && inputs.size() <= 256, "LINKAGE_LIMIT", "Expected 1..256 owned classes");
            require(external != null, "MISSING_API_INDEX", "External declarations are mandatory");
            long total = 0;
            for (var entry : inputs.entrySet()) {
                OperationControl.checkpoint();
                byte[] input = entry.getValue();
                require(input != null && input.length <= 2 * 1024 * 1024 && (total += input.length) <= 32L * 1024 * 1024,
                        "LINKAGE_LIMIT", "Owned bytecode exceeds per-class/total bounds");
                ClassReader reader = new ClassReader(input);
                String name = reader.getClassName();
                require(entry.getKey().equals(name) || entry.getKey().equals(name + ".class"), "OWNERSHIP_PATH_MISMATCH", entry.getKey());
                require(!forbiddenOwner(name) && !external.hasClass(name), "PLATFORM_OR_EXTERNAL_CLASS_OWNERSHIP", name);
                ClassNode node = new ClassNode(Opcodes.ASM9); reader.accept(node, ClassReader.EXPAND_FRAMES);
                require(classes.put(name, node) == null, "DUPLICATE_OWNED_CLASS", name);
                bytes.put(name, input.clone());
            }
            hierarchy = new LinkageHierarchy(classes, external);
            interfaceProof = interfaceDefaults ? OwnedInterfaceDefaults.inspect(classes) : null;
            receiverProof = receiverHandles ? OwnedReceiverHandles.inspect(classes) : null;
            suiteProof = callbackPolicy == CallbackPolicy.LIFECYCLE_SUITE ? dev.legacybridge.converter.conversion.LifecycleSuiteContract.inspect(classes) : null;
            serverConfigProof = callbackPolicy == CallbackPolicy.SERVER_CONFIG ? dev.legacybridge.converter.conversion.ServerConfigContract.inspect(classes) : null;
            clientConfigProof = callbackPolicy == CallbackPolicy.CLIENT_CONFIG ? dev.legacybridge.converter.conversion.ClientConfigContract.inspect(classes) : null;
            eventProof = suiteProof != null ? suiteProof.events() : callbackPolicy == CallbackPolicy.OWNED_EVENT_PRIORITY ? dev.legacybridge.converter.conversion.EventPriorityContract.inspect(classes) : null;
            horizontalProof = callbackPolicy == CallbackPolicy.HORIZONTAL_BLOCK
                    ? dev.legacybridge.converter.conversion.HorizontalBlockContract.inspect(classes) : null;
            staticShapeProof = callbackPolicy == CallbackPolicy.STATIC_SHAPE_BLOCK
                    ? dev.legacybridge.converter.conversion.StaticShapeBlockContract.inspect(classes) : null;
            booleanToggleProof = callbackPolicy == CallbackPolicy.BOOLEAN_TOGGLE_BLOCK
                    ? dev.legacybridge.converter.conversion.BooleanToggleBlockContract.inspect(classes) : null;
            itemAttributeProof = callbackPolicy == CallbackPolicy.ITEM_ATTRIBUTES
                    ? dev.legacybridge.converter.conversion.ItemAttributeContract.inspect(classes) : null;
            itemAttributeDurabilityProof = callbackPolicy == CallbackPolicy.ITEM_ATTRIBUTE_DURABILITY
                    ? dev.legacybridge.converter.conversion.ItemAttributeDurabilityContract.inspect(classes) : null;
            ownedStackDamageProof = callbackPolicy == CallbackPolicy.OWNED_STACK_DAMAGE
                    ? dev.legacybridge.converter.conversion.OwnedStackDamageContract.inspect(classes) : null;
            ownedStackRepairUseProof = callbackPolicy == CallbackPolicy.OWNED_STACK_REPAIR_USE
                    ? dev.legacybridge.converter.conversion.OwnedStackRepairUseContract.inspect(classes) : null;
            if (delegationOwner != null) {
                require(classes.containsKey(delegationOwner), "CONSTRUCTOR_OWNER_NOT_OWNED", delegationOwner);
                OwnedConstructorGraph.inspect(classes.get(delegationOwner));
            }
        }

        Summary verify() throws AnalyzerException {
            if (callbackPolicy == CallbackPolicy.NETWORK_SNAPSHOT)
                for (ClassNode node : classes.values()) reviewedOpaqueReturns(node);
            for (ClassNode node : classes.values()) {
                OperationControl.checkpoint(); hierarchy.graph(node.name); declaration(node); constantPool(node);
            }
            if (booleanToggleProof != null) booleanToggleNativeContracts(classes.get(booleanToggleProof.block()));
            if (itemAttributeProof != null) itemAttributeNativeContracts(classes.get(itemAttributeProof.item()));
            if (itemAttributeDurabilityProof != null) itemAttributeDurabilityNativeContracts();
            if (ownedStackDamageProof != null) ownedStackDamageNativeContracts();
            if (ownedStackRepairUseProof != null) ownedStackRepairUseNativeContracts();
            for (ClassNode node : classes.values()) {
                overridesAndAbstractRequirements(node);
                for (MethodNode method : node.methods) method(node, method);
            }
            Map<String, byte[]> declarationHierarchy = hierarchy.declarationBytes(false);
            declarationHierarchy.putAll(bytes);
            for (byte[] own : bytes.values()) BytecodeValidator.validate(own, declarationHierarchy);
            return new Summary("PASSED_SCOPED", classes.size(), hierarchy.resolvedCount(), methods,
                    methodReferences, fieldReferences, lambdas, callbackLimitations());
        }

        private List<String> callbackLimitations() {
            if (receiverProof != null) {
                var limitations = new ArrayList<>(LIMITATIONS);
                limitations.set(4, "Existing exact static/constructor lambdas plus one independently proved final owned helper this::publicMethod IntUnaryOperator; initialized non-null this capture, exact (I)I, no adaptation, boxing, alternate bootstrap or other instance handles. Static checks do not certify native execution.");
                return List.copyOf(limitations);
            }
            if (interfaceProof != null) {
                var limitations = new ArrayList<>(LIMITATIONS);
                limitations.set(2, "Only the independently re-read flat owned interface " + interfaceProof.contract()
                        + " and final helper " + interfaceProof.helper() + " admit the proved integer default/override/direct-super path."
                        + " General defaults, diamonds, inherited defaults, instance handles, cross-owner package access, nests, sealed/record/annotation classes, native code and reflection remain unsupported.");
                return List.copyOf(limitations);
            }
            if (delegationOwner != null) {
                List<String> limitations = new ArrayList<>(LIMITATIONS);
                limitations.removeIf(s -> s.startsWith("Constructor delegation through this()"));
                limitations.add("Only the independently re-read finite integer chain of " + delegationOwner
                        + " permits this() delegation; uninitialized aliases/branches/handlers remain unsupported.");
                return List.copyOf(limitations);
            }
            if (callbackPolicy == CallbackPolicy.LOGIC) return LIMITATIONS;
            List<String> result = new ArrayList<>(LIMITATIONS.subList(0, LIMITATIONS.size()-1));
            if (ownedStackRepairUseProof != null)
                result.add("Only the independently re-read two-class/six-method owned stack repair-use program: final owned Item, capacity 128, the separately checked guarded damage-1 helper on the received stack, one received-hand lookup declared on LivingEntity, logical-client SUCCESS without mutation, and one server helper call followed by same-stack CONSUME/PASS. Exact native declarations, owners/access/opcodes, unique interfaces, final ItemStack and inherited IForgeItem default-hook dispatch are independently checked. Client prediction may swing even when the server refuses repair; native creative use restores damage. No proof of callback delivery, per-click count, thread/player authority, cooldown/cancellation/spectator behavior, inventory synchronization, native game execution or multiplayer qualification; no material cost, wear/break or arbitrary item-use compatibility.");
            else if (ownedStackDamageProof != null)
                result.add("Only the independently re-read two-class/five-method owned stack-damage program: final owned Item, capacity 128, helper guards null/item identity/native damaged state/positive damage bounded by maximum, then exactly one damage-1 write to the received stack. Native final ItemStack, direct method declarations and inherited IForgeItem default-hook dispatch are independently checked. No automatic gameplay trigger, wear/break, material consumption, arbitrary NBT validation, concurrent access, player/world authority, inventory synchronization, native game execution or multiplayer qualification.");
            else if (itemAttributeDurabilityProof != null)
                result.add("Only the independently re-read two-class/seven-method Item attribute-durability program: immutable MAINHAND ADDITION map, inherited native UUID identities, literal durability 128 and the second-argument ordinary-stack iron-ingot repair predicate. Both native public virtual callbacks, exact declaring owners/access, final ItemStack, property/getter declarations, iron field and Guava ABI/opcodes are independently checked. Capacity does not implement wear: no damage consumption, hit/mining/break behavior, anvil cost/material consumption, NBT/event overrides, native game execution or multiplayer qualification. Separate attribute-only and durable-only policies are not widened.");
            else if (itemAttributeProof != null)
                result.add("Only the independently re-read two-class Item attribute program: one immutable map with fixed MAINHAND ADDITION modifiers and inherited native UUID identities, empty result for other slots, constructor super only. The native public virtual callback, protected/static/final UUID declaring owner, attribute/enum fields, exact Guava builder declarations and invocation kinds are independently checked. No tool actions, stack-sensitive/NBT/event overrides, arbitrary Guava operations, combat/equipment/tooltip qualification, native game execution or implicit combination with other policies.");
            else if (booleanToggleProof != null)
                result.add("Only the independently re-read false-default active BooleanProperty program: logical-client SUCCESS without a write; server cycle of the actual callback state and exactly one setBlock at the callback position with flags 3, ignored boolean result, then CONSUME. Required native callbacks, inherited declarations, finality, access and opcodes are independently checked. No arbitrary interaction, other world access, write-success guarantee, main-thread/reach/protection qualification, synchronization or native gameplay execution.");
            else if (staticShapeProof != null)
                result.add("Only the independently re-read two-class bottom-half Block/BlockItem program: one static final native Block.box(0,0,0,16,8,16), constructor super only, and two public outline/collision overrides returning that same owned field without consulting state/world/position/context. No general geometry, dynamic shape, horizontal composition, shape mutation, native cache/default/lighting qualification or game execution.");
            else if (horizontalProof != null)
                result.add("Only the independently re-read two-class horizontal Block/BlockItem program: static facing property, protected definition override, post-super NORTH default, and opposite horizontal placement. "
                        + (horizontalProof.orientationTransforms()
                        ? "The complete rotate/mirror override pair additionally preserves the supplied state and transforms its existing facing through the exact native enum operations. "
                        : "Rotation/mirror overrides are absent and no such behavior is added. ")
                        + "Protected access is still verified on real receiver dataflow. No arbitrary block callback, additional state, shape change, game execution or behavior qualification.");
            else if (suiteProof != null)
                result.add("Only the independently re-read seven-class lifecycle composition: native COMMON spec identity/type filtered Loading/Reloading, queued late registry lookup, owned cancelable event and fresh typed integer/boolean command tree. No game execution, general event/config composition, remote synchronization, atomic snapshot or arbitrary command effects.");
            else if (serverConfigProof != null)
                result.add("Only independently re-read SERVER spec, both-physical-sides registration and Forge started/stopped snapshots. No config-event callbacks, reload, client reads, custom synchronization or native behavior qualification.");
            else if (clientConfigProof != null)
                result.add("Only independently re-read physical CLIENT guard, boolean/int native spec, MOD client-setup Consumer and queued startup Runnable. No pre-load reads, arbitrary config hooks, reload, synchronization or native classloader/behavior qualification.");
            else if (eventProof != null)
                result.add("Only the independently re-read four-class owned cancelable event, two explicit HIGH/LOW Consumer callbacks and common-setup Runnable are admitted. No arbitrary events, MONITOR, payload, runtime execution or third-party listener equivalence.");
            else if (callbackPolicy == CallbackPolicy.LIFECYCLE)
                result.add("Exact Supplier/IntUnaryOperator contracts plus opt-in zero-capture owned static common-setup Consumer, Runnable, command-source Predicate and Brigadier Command callbacks only; no general boxing, receiver handles, altMetafactory or condy.");
            else result.add("Networking-only exact owned static Supplier<String>, Predicate<String>, single-capture same-ServerPlayer Supplier, fixed message/buffer Function and BiConsumer, message/context-Supplier BiConsumer and two-capture message/context Runnable SAMs. No receiver handles, boxing, alternate metafactory, field effects, protocol safety, queue bounds or session correctness are certified by linkage; the independent profile must prove those contracts.");
            if (callbackPolicy == CallbackPolicy.NETWORK_SNAPSHOT)
                result.add("The package-private native MessageHandler return is admitted only on the exact public six-argument SimpleChannel.registerMessage invocation immediately discarded with POP, plus its inert constant-pool/InnerClasses metadata. Its real access flags are retained; no construction, cast, storage, signature, handle or escaped return is authorized.");
            return List.copyOf(result);
        }

        /**
         * Both pinned Forge releases legally expose a package-private return from a
         * public method. Java callers may discard that value. This is not permission
         * to access that class: only the audited invocation/POP and the metadata the
         * Java compiler emits for that invocation receive this contextual exception.
         */
        private void reviewedOpaqueReturns(ClassNode node) {
            for (MethodNode method : node.methods) for (AbstractInsnNode instruction : method.instructions) {
                OperationControl.checkpoint();
                if (!(instruction instanceof MethodInsnNode call)) continue;
                String opaque = registrationReturn(call.owner, call.name, call.desc);
                if (opaque == null) continue;
                AbstractInsnNode next = call.getNext();
                while (next != null && next.getOpcode() < 0) next = next.getNext();
                require(call.getOpcode() == Opcodes.INVOKEVIRTUAL && !call.itf && next != null && next.getOpcode() == Opcodes.POP,
                        "NETWORK_OPAQUE_RETURN_ESCAPE", node.name + "." + method.name);
                hierarchy.accessibleClass(node.name, call.owner);
                LinkageHierarchy.Member member = hierarchy.method(call.owner, call.name, call.desc);
                require(member.owner().name().equals(call.owner)
                                && (member.member().access() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT)) == Opcodes.ACC_PUBLIC,
                        "NETWORK_OPAQUE_RETURN_DECLARATION", call.owner);
                ApiClass declaration = hierarchy.type(opaque);
                require(!hierarchy.owned(opaque) && (declaration.access() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED
                                | Opcodes.ACC_PRIVATE | Opcodes.ACC_INTERFACE | Opcodes.ACC_ANNOTATION | Opcodes.ACC_ENUM | Opcodes.ACC_ABSTRACT)) == 0,
                        "NETWORK_OPAQUE_RETURN_DECLARATION", opaque);
                hierarchy.graph(opaque);
                opaqueRegistrationReturns.computeIfAbsent(node.name, ignored -> new HashSet<>()).add(opaque);
            }
        }

        private static String registrationReturn(String owner, String name, String descriptor) {
            if (!name.equals("registerMessage")) return null;
            for (String prefix : List.of("net/minecraftforge/fml/network/", "net/minecraftforge/network/")) {
                String opaque = prefix + "simple/IndexedMessageCodec$MessageHandler";
                if (owner.equals(prefix + "simple/SimpleChannel") && descriptor.equals("(ILjava/lang/Class;Ljava/util/function/BiConsumer;"
                        + "Ljava/util/function/Function;Ljava/util/function/BiConsumer;Ljava/util/Optional;)L" + opaque + ";")) return opaque;
            }
            return null;
        }

        private boolean reviewedOpaqueMetadata(String caller, String name) {
            return opaqueRegistrationReturns.getOrDefault(caller, Set.of()).contains(name);
        }

        private void invocationDescriptor(String caller, String owner, String name, String descriptor) {
            String opaque = registrationReturn(owner, name, descriptor);
            if (opaque != null && reviewedOpaqueMetadata(caller, opaque)) {
                for (Type argument : Type.getArgumentTypes(descriptor)) hierarchy.accessibleType(caller, argument);
                hierarchy.graph(opaque); // Preserve real non-public declaration; this does not make it accessible.
            } else descriptor(caller, descriptor);
        }

        private void declaration(ClassNode node) {
            require((node.version & 0xFFFF) >= Opcodes.V1_8 && (node.version & 0xFFFF) <= Opcodes.V17 && node.version >>> 16 == 0,
                    "CLASS_VERSION_UNSUPPORTED", node.name);
            require(node.module == null && node.nestHostClass == null && empty(node.nestMembers) && empty(node.permittedSubclasses)
                            && empty(node.recordComponents) && (node.access & (Opcodes.ACC_RECORD | Opcodes.ACC_ANNOTATION | Opcodes.ACC_ENUM)) == 0,
                    "CLASS_FEATURE_UNSUPPORTED", node.name);
            if (node.superName != null) hierarchy.accessibleClass(node.name, node.superName);
            for (String parent : node.interfaces) hierarchy.accessibleClass(node.name, parent);
            signature(node.name, node.signature, false);
            annotations(node.name, node.visibleAnnotations, 0); annotations(node.name, node.invisibleAnnotations, 0);
            annotations(node.name, node.visibleTypeAnnotations, 0); annotations(node.name, node.invisibleTypeAnnotations, 0);
            if (node.outerClass != null) {
                hierarchy.accessibleClass(node.name, node.outerClass);
                if (node.outerMethod != null) { descriptor(node.name, node.outerMethodDesc); hierarchy.method(node.outerClass, node.outerMethod, node.outerMethodDesc); }
            }
            for (InnerClassNode inner : node.innerClasses) {
                if (reviewedOpaqueMetadata(node.name, inner.name)) {
                    require(inner.name.substring(0, inner.name.indexOf('$')).equals(inner.outerName)
                                    && "MessageHandler".equals(inner.innerName) && inner.access == 0,
                            "NETWORK_OPAQUE_INNER_METADATA", inner.name);
                } else hierarchy.accessibleClass(node.name, inner.name);
                if (inner.outerName != null) hierarchy.accessibleClass(node.name, inner.outerName);
            }
            for (FieldNode field : node.fields) {
                descriptor(node.name, field.desc); signature(node.name, field.signature, true);
                annotations(node.name, field.visibleAnnotations, 0); annotations(node.name, field.invisibleAnnotations, 0);
                annotations(node.name, field.visibleTypeAnnotations, 0); annotations(node.name, field.invisibleTypeAnnotations, 0);
                constant(node.name, field.value);
            }
        }

        private void method(ClassNode node, MethodNode method) throws AnalyzerException {
            OperationControl.checkpoint();
            require(++methods <= 8192 && (instructions += method.instructions.size()) <= 200_000, "LINKAGE_LIMIT", "Method/instruction count");
            require((method.access & Opcodes.ACC_NATIVE) == 0, "NATIVE_METHOD_UNSUPPORTED", node.name + "." + method.name);
            descriptor(node.name, method.desc); signature(node.name, method.signature, false);
            for (String exception : method.exceptions) throwable(node.name, exception);
            annotations(node.name, method.visibleAnnotations, 0); annotations(node.name, method.invisibleAnnotations, 0);
            annotations(node.name, method.visibleTypeAnnotations, 0); annotations(node.name, method.invisibleTypeAnnotations, 0);
            annotations(node.name, method.visibleLocalVariableAnnotations, 0); annotations(node.name, method.invisibleLocalVariableAnnotations, 0);
            parameterAnnotations(node.name, method.visibleParameterAnnotations);
            parameterAnnotations(node.name, method.invisibleParameterAnnotations);
            require(method.annotationDefault == null, "ANNOTATION_DEFAULT_UNSUPPORTED", node.name + "." + method.name);
            if (method.localVariables != null) for (LocalVariableNode local : method.localVariables) { descriptor(node.name, local.desc); signature(node.name, local.signature, true); }
            for (TryCatchBlockNode block : method.tryCatchBlocks) {
                if (block.type != null) throwable(node.name, block.type);
                annotations(node.name, block.visibleTypeAnnotations, 0); annotations(node.name, block.invisibleTypeAnnotations, 0);
            }
            if ((method.access & Opcodes.ACC_ABSTRACT) != 0) return;
            if (hierarchy.isInterface(node.name) && (method.access & Opcodes.ACC_STATIC) == 0
                    && (interfaceProof == null || !interfaceProof.permitsDefault(node.name, method.name, method.desc)))
                LinkageHierarchy.fail("DEFAULT_INTERFACE_DISPATCH_UNSUPPORTED", node.name + "." + method.name);
            // Resolve references even in unreachable instructions; dead legacy calls are not retained as compatibility.
            for (AbstractInsnNode instruction : method.instructions) scanInstruction(node, method, instruction);
            Frame<BasicValue>[] frames = InitializationVerifier.analyze(node, method, hierarchy, node.name.equals(delegationOwner));
            for (int index = 0; index < method.instructions.size(); index++) {
                AbstractInsnNode instruction = method.instructions.get(index);
                require(instruction.getOpcode() < 0 || frames[index] != null, "UNREACHABLE_CODE_UNSUPPORTED", node.name + "." + method.name);
                if (instruction instanceof MethodInsnNode call) methodCall(node, method, call, frames[index]);
                if (instruction instanceof FieldInsnNode field) fieldCall(node, method, field, frames[index]);
                if (instruction instanceof InvokeDynamicInsnNode dynamic && receiverProof != null
                        && receiverProof.permits(node.name, dynamic)) {
                    require(InitializationVerifier.provenThis(receiver(frames[index], 0)),
                            "LAMBDA_RECEIVER_UNPROVEN", node.name + "." + method.name);
                }
            }
        }

        private void scanInstruction(ClassNode node, MethodNode method, AbstractInsnNode instruction) {
            OperationControl.checkpoint();
            require(instruction.getOpcode() != Opcodes.JSR && instruction.getOpcode() != Opcodes.RET, "SUBROUTINE_UNSUPPORTED", node.name);
            annotations(node.name, instruction.visibleTypeAnnotations, 0); annotations(node.name, instruction.invisibleTypeAnnotations, 0);
            if (instruction instanceof TypeInsnNode type) {
                hierarchy.accessibleClass(node.name, type.desc);
                if (instruction.getOpcode() == Opcodes.NEW) require(!hierarchy.isInterface(type.desc)
                                && (hierarchy.type(type.desc).access() & Opcodes.ACC_ABSTRACT) == 0,
                        "NONINSTANTIABLE_NEW", type.desc);
            } else if (instruction instanceof MethodInsnNode call) {
                invocationDescriptor(node.name, call.owner, call.name, call.desc); hierarchy.accessibleClass(node.name, call.owner);
                hierarchy.method(call.owner, call.name, call.desc);
            } else if (instruction instanceof FieldInsnNode field) {
                descriptor(node.name, field.desc); hierarchy.accessibleClass(node.name, field.owner);
                hierarchy.field(field.owner, field.name, field.desc);
            } else if (instruction instanceof LdcInsnNode constant) constant(node.name, constant.cst);
            else if (instruction instanceof MultiANewArrayInsnNode array) descriptor(node.name, array.desc);
            else if (instruction instanceof InvokeDynamicInsnNode dynamic) lambda(node, dynamic);
            else if (instruction instanceof FrameNode frame) {
                frameTypes(node.name, frame.local); frameTypes(node.name, frame.stack);
            }
        }

        private void methodCall(ClassNode node, MethodNode method, MethodInsnNode call, Frame<BasicValue> frame) {
            require(++methodReferences <= 200_000, "LINKAGE_LIMIT", "Method reference count");
            boolean ownerInterface = hierarchy.isInterface(call.owner);
            require(ownerInterface == call.itf, "METHOD_OWNER_KIND", call.owner + "." + call.name);
            require(!call.name.equals("<clinit>"), "EXPLICIT_CLINIT_CALL", call.owner);
            if (call.owner.equals("java/lang/invoke/MethodHandle") || call.owner.equals("java/lang/invoke/VarHandle")
                    || call.owner.startsWith("java/lang/reflect/") || call.owner.equals("java/lang/invoke/MethodHandles$Lookup")
                    || call.owner.equals("java/lang/Class") && Set.of("forName", "newInstance", "getDeclaredMethod", "getMethod", "getDeclaredField", "getField").contains(call.name)
                    || call.owner.equals("java/lang/ClassLoader") && Set.of("loadClass", "defineClass").contains(call.name))
                LinkageHierarchy.fail("REFLECTIVE_LINKAGE_UNSUPPORTED", call.owner + "." + call.name);
            LinkageHierarchy.Member resolved = hierarchy.method(call.owner, call.name, call.desc);
            int access = resolved.member().access();
            boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
            require((call.getOpcode() == Opcodes.INVOKESTATIC) == isStatic, "METHOD_STATICNESS", call.owner + "." + call.name);
            if (call.getOpcode() == Opcodes.INVOKEINTERFACE) require(ownerInterface && (access & Opcodes.ACC_PRIVATE) == 0, "INVALID_INVOKEINTERFACE", call.owner);
            if (call.getOpcode() == Opcodes.INVOKEVIRTUAL) require(!ownerInterface && (access & Opcodes.ACC_PRIVATE) == 0, "INVALID_INVOKEVIRTUAL", call.owner);
            if (ownerInterface && isStatic) require(resolved.owner().name().equals(call.owner), "INHERITED_INTERFACE_STATIC_UNSUPPORTED", call.owner);
            if (hierarchy.isInterface(resolved.owner().name()) && !isStatic && (access & Opcodes.ACC_ABSTRACT) == 0
                    && (interfaceProof == null || !interfaceProof.permitsDefault(resolved.owner().name(), call.name, call.desc)))
                LinkageHierarchy.fail("DEFAULT_INTERFACE_DISPATCH_UNSUPPORTED", call.owner + "." + call.name);
            BasicValue receiver = isStatic ? null : receiver(frame, Type.getArgumentTypes(call.desc).length);
            if (call.name.equals("<init>")) {
                require(call.getOpcode() == Opcodes.INVOKESPECIAL && !ownerInterface && resolved.owner().name().equals(call.owner)
                                && Type.getReturnType(call.desc).equals(Type.VOID_TYPE), "INVALID_CONSTRUCTOR_LINKAGE", call.owner);
            } else if (call.getOpcode() == Opcodes.INVOKESPECIAL) {
                if (ownerInterface) {
                    require(interfaceProof != null && interfaceProof.permitsSpecial(node.name, call)
                                    && resolved.owner().name().equals(call.owner) && (access & Opcodes.ACC_ABSTRACT) == 0
                                    && node.interfaces.equals(List.of(call.owner)), "INVALID_INTERFACE_SUPER", call.owner);
                    require(InitializationVerifier.provenThis(receiver), "INTERFACE_SUPER_RECEIVER_UNPROVEN", call.owner);
                } else {
                    require((access & Opcodes.ACC_ABSTRACT) == 0, "INVALID_INVOKESPECIAL", call.owner);
                    require(call.owner.equals(node.superName) || call.owner.equals(node.name) && resolved.owner().name().equals(node.name)
                                && (access & Opcodes.ACC_PRIVATE) != 0,
                        "SPECIAL_DISPATCH_UNSUPPORTED", call.owner + "." + call.name);
                }
                require(receiver != null && hierarchy.assignable(Type.getObjectType(node.name), receiver.getType()),
                        "SPECIAL_RECEIVER_UNPROVEN", call.owner + "." + call.name);
            }
            hierarchy.accessibleMember(node.name, resolved, receiver == null ? null : receiver.getType(), !isStatic);
        }

        private void fieldCall(ClassNode node, MethodNode method, FieldInsnNode field, Frame<BasicValue> frame) {
            require(++fieldReferences <= 200_000, "LINKAGE_LIMIT", "Field reference count");
            LinkageHierarchy.Member resolved = hierarchy.field(field.owner, field.name, field.desc);
            boolean isStatic = (resolved.member().access() & Opcodes.ACC_STATIC) != 0;
            boolean staticOpcode = field.getOpcode() == Opcodes.GETSTATIC || field.getOpcode() == Opcodes.PUTSTATIC;
            require(isStatic == staticOpcode, "FIELD_STATICNESS", field.owner + "." + field.name);
            boolean write = field.getOpcode() == Opcodes.PUTFIELD || field.getOpcode() == Opcodes.PUTSTATIC;
            BasicValue receiver = isStatic ? null : receiver(frame, write ? 1 : 0);
            hierarchy.accessibleMember(node.name, resolved, receiver == null ? null : receiver.getType(), !isStatic);
            if (write && (resolved.member().access() & Opcodes.ACC_FINAL) != 0) {
                require(resolved.owner().name().equals(node.name) && method.name.equals(isStatic ? "<clinit>" : "<init>")
                                && (isStatic || InitializationVerifier.provenThis(receiver)),
                        "ILLEGAL_FINAL_FIELD_WRITE", field.owner + "." + field.name);
            }
        }

        private void lambda(ClassNode node, InvokeDynamicInsnNode dynamic) {
            require(++lambdas <= 2048, "LINKAGE_LIMIT", "Lambda count");
            Handle bootstrap = dynamic.bsm;
            require(bootstrap.getTag() == Opcodes.H_INVOKESTATIC && !bootstrap.isInterface()
                            && bootstrap.getOwner().equals("java/lang/invoke/LambdaMetafactory") && bootstrap.getName().equals("metafactory")
                            && bootstrap.getDesc().equals(METAFACTORY) && dynamic.bsmArgs.length == 3,
                    "BOOTSTRAP_UNSUPPORTED", dynamic.name);
            descriptor(node.name, dynamic.desc); descriptor(node.name, bootstrap.getDesc());
            hierarchy.accessibleClass(node.name, bootstrap.getOwner());
            LinkageHierarchy.Member boot = hierarchy.method(bootstrap.getOwner(), bootstrap.getName(), bootstrap.getDesc());
            require((boot.member().access() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) == (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC), "INVALID_BOOTSTRAP_LINKAGE", dynamic.name);
            require(dynamic.bsmArgs[0] instanceof Type && dynamic.bsmArgs[1] instanceof Handle && dynamic.bsmArgs[2] instanceof Type,
                    "LAMBDA_ARGUMENTS_UNSUPPORTED", dynamic.name);
            Type sam = (Type) dynamic.bsmArgs[0], instantiated = (Type) dynamic.bsmArgs[2]; Handle implementation = (Handle) dynamic.bsmArgs[1];
            require(sam.getSort() == Type.METHOD && instantiated.getSort() == Type.METHOD, "LAMBDA_METHOD_TYPE", dynamic.name);
            Type factory = Type.getMethodType(dynamic.desc); Type functional = factory.getReturnType();
            require(functional.getSort() == Type.OBJECT, "LAMBDA_RETURN_INTERFACE", dynamic.desc);
            if (callbackPolicy == CallbackPolicy.NETWORK_SNAPSHOT) {
                networkLambda(node, dynamic, sam, instantiated, implementation, factory, functional);
                return;
            }
            boolean supplier = functional.getInternalName().equals("java/util/function/Supplier");
            boolean operator = functional.getInternalName().equals("java/util/function/IntUnaryOperator");
            boolean lifecycle = (callbackPolicy == CallbackPolicy.LIFECYCLE || eventProof != null || clientConfigProof != null) && dev.legacybridge.converter.transformation.ReviewedLifecycleLambda
                    .accepts(functional.getInternalName(), dynamic.name, sam, instantiated, factory);
            boolean events = (eventProof != null && dev.legacybridge.converter.conversion.EventPriorityContract.accepts(eventProof.event(), functional.getInternalName(), dynamic.name, sam, instantiated, factory))
                    || (clientConfigProof != null && dev.legacybridge.converter.conversion.ClientConfigContract.accepts(functional.getInternalName(), dynamic.name, sam, instantiated, factory))
                    || (serverConfigProof != null && dev.legacybridge.converter.conversion.ServerConfigContract.accepts(functional.getInternalName(), dynamic.name, sam, instantiated, factory))
                    || (suiteProof != null && dev.legacybridge.converter.conversion.LifecycleSuiteContract.accepts(functional.getInternalName(), dynamic.name, sam, instantiated, factory));
            require(supplier && dynamic.name.equals("get") && sam.getDescriptor().equals("()Ljava/lang/Object;")
                            || operator && dynamic.name.equals("applyAsInt") && sam.getDescriptor().equals("(I)I") || lifecycle || events,
                    "FUNCTIONAL_INTERFACE_UNSUPPORTED", dynamic.name + dynamic.desc);
            descriptor(node.name, sam.getDescriptor()); descriptor(node.name, instantiated.getDescriptor()); descriptor(node.name, implementation.getDesc());
            if (implementation.getTag() == Opcodes.H_NEWINVOKESPECIAL) {
                constructorSupplier(node, factory, supplier, instantiated, implementation);
                return;
            }
            if (receiverProof != null && receiverProof.permits(node.name, dynamic)) {
                require(operator && sam.equals(Type.getMethodType("(I)I")) && instantiated.equals(sam)
                                && factory.getDescriptor().equals("(" + receiverProof.captureDescriptor() + ")Ljava/util/function/IntUnaryOperator;"),
                        "RECEIVER_ADAPTATION_UNSUPPORTED", dynamic.desc);
                var impl = hierarchy.method(implementation.getOwner(), implementation.getName(), implementation.getDesc());
                require(impl.owner().name().equals(implementation.getOwner()) && impl.member().access() == Opcodes.ACC_PUBLIC,
                        "RECEIVER_IMPLEMENTATION_LINKAGE", implementation.toString());
                hierarchy.accessibleMember(node.name, impl, Type.getObjectType(node.name), true);
                return; // The frame pass additionally checks the captured value's this provenance.
            }
            require(implementation.getTag() == Opcodes.H_INVOKESTATIC && classes.containsKey(implementation.getOwner())
                            && implementation.isInterface() == hierarchy.isInterface(implementation.getOwner()),
                    "LAMBDA_IMPLEMENTATION_UNSUPPORTED", implementation.toString());
            LinkageHierarchy.Member impl = hierarchy.method(implementation.getOwner(), implementation.getName(), implementation.getDesc());
            require(impl.owner().name().equals(implementation.getOwner()) && !implementation.getName().startsWith("<")
                            && (impl.member().access() & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == Opcodes.ACC_STATIC,
                    "LAMBDA_IMPLEMENTATION_UNSUPPORTED", implementation.toString());
            hierarchy.accessibleMember(node.name, impl, null, false);
            Type[] captured = factory.getArgumentTypes(), arguments = Type.getArgumentTypes(implementation.getDesc());
            Type[] dynamicArguments = instantiated.getArgumentTypes();
            require(captured.length <= 16 && arguments.length == captured.length + dynamicArguments.length,
                    "LAMBDA_CAPTURE_ARITY", dynamic.name);
            for (int index = 0; index < captured.length; index++) require(captured[index].equals(arguments[index]), "LAMBDA_CAPTURE_TYPE", dynamic.name);
            if (supplier) {
                require(dynamicArguments.length == 0 && LinkageHierarchy.reference(instantiated.getReturnType())
                                && LinkageHierarchy.reference(Type.getReturnType(implementation.getDesc()))
                                && hierarchy.assignable(instantiated.getReturnType(), Type.getReturnType(implementation.getDesc())),
                        "SUPPLIER_ADAPTATION_UNSUPPORTED", implementation.toString());
            } else if (lifecycle || events) {
                require(captured.length == 0 && java.util.Arrays.equals(arguments, instantiated.getArgumentTypes())
                                && Type.getReturnType(implementation.getDesc()).equals(instantiated.getReturnType()),
                        "LIFECYCLE_CALLBACK_ADAPTATION_UNSUPPORTED", implementation.toString());
            } else {
                require(instantiated.equals(Type.getMethodType("(I)I")) && arguments[captured.length].equals(Type.INT_TYPE)
                                && Type.getReturnType(implementation.getDesc()).equals(Type.INT_TYPE),
                        "INT_UNARY_ADAPTATION_UNSUPPORTED", implementation.toString());
            }
        }

        private void networkLambda(ClassNode node, InvokeDynamicInsnNode dynamic, Type sam, Type instantiated,
                                   Handle implementation, Type factory, Type functional) {
            require(dev.legacybridge.converter.transformation.ReviewedNetworkLambda.accepts(
                            functional.getInternalName(), dynamic.name, sam, instantiated, factory, classes.keySet()),
                    "NETWORK_SAM_UNSUPPORTED", dynamic.name + dynamic.desc + " / " + instantiated);
            descriptor(node.name, sam.getDescriptor());
            descriptor(node.name, instantiated.getDescriptor());
            descriptor(node.name, implementation.getDesc());
            require(implementation.getTag() == Opcodes.H_INVOKESTATIC && classes.containsKey(implementation.getOwner())
                            && !implementation.isInterface() && !hierarchy.isInterface(implementation.getOwner()),
                    "NETWORK_IMPLEMENTATION_UNSUPPORTED", implementation.toString());
            LinkageHierarchy.Member impl = hierarchy.method(implementation.getOwner(), implementation.getName(), implementation.getDesc());
            require(impl.owner().name().equals(implementation.getOwner()) && !implementation.getName().startsWith("<")
                            && (impl.member().access() & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == Opcodes.ACC_STATIC,
                    "NETWORK_IMPLEMENTATION_UNSUPPORTED", implementation.toString());
            hierarchy.accessibleMember(node.name, impl, null, false);
            Type[] captured = factory.getArgumentTypes();
            Type[] dynamicArguments = instantiated.getArgumentTypes();
            Type[] actual = Type.getArgumentTypes(implementation.getDesc());
            require(actual.length == captured.length + dynamicArguments.length
                            && Type.getReturnType(implementation.getDesc()).equals(instantiated.getReturnType()),
                    "NETWORK_CALLBACK_ADAPTATION_UNSUPPORTED", implementation.toString());
            for (int index = 0; index < captured.length; index++)
                require(captured[index].equals(actual[index]), "NETWORK_CAPTURE_TYPE", implementation.toString());
            for (int index = 0; index < dynamicArguments.length; index++)
                require(dynamicArguments[index].equals(actual[index + captured.length]),
                        "NETWORK_ARGUMENT_TYPE", implementation.toString());
            // Owned argument/result names are only message-role candidates. Do not accept an
            // interface/array/supertype as a packet DTO merely because the SAM's erasure fits.
            List<Type> candidates = new ArrayList<>(List.of(captured));
            candidates.addAll(List.of(dynamicArguments));
            candidates.add(instantiated.getReturnType());
            for (Type candidate : candidates) if (candidate.getSort() == Type.OBJECT && classes.containsKey(candidate.getInternalName())) {
                ApiClass declaration = hierarchy.type(candidate.getInternalName());
                require((declaration.access() & (Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT)) == 0,
                        "NETWORK_MESSAGE_KIND_UNSUPPORTED", candidate.getInternalName());
            }
        }

        private void constructorSupplier(ClassNode node, Type factory, boolean supplier, Type instantiated, Handle implementation) {
            require(supplier && factory.getArgumentTypes().length == 0 && instantiated.getArgumentTypes().length == 0
                            && implementation.getName().equals("<init>") && implementation.getDesc().equals("()V")
                            && !implementation.isInterface() && classes.containsKey(implementation.getOwner()),
                    "CONSTRUCTOR_SUPPLIER_UNSUPPORTED", implementation.toString());
            ApiClass owner = hierarchy.type(implementation.getOwner());
            require((owner.access() & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE)) == 0,
                    "CONSTRUCTOR_SUPPLIER_NONINSTANTIABLE", owner.name());
            hierarchy.accessibleClass(node.name, owner.name());
            LinkageHierarchy.Member constructor = hierarchy.method(owner.name(), "<init>", "()V");
            require(constructor.owner().name().equals(owner.name()) && (constructor.member().access() & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0,
                    "CONSTRUCTOR_SUPPLIER_UNSUPPORTED", implementation.toString());
            // Constructor references allocate the declared owner, not a receiver derived from the caller.
            hierarchy.accessibleMember(node.name, constructor, Type.getObjectType(owner.name()), true);
            List<String> itemParents = hierarchy.ancestors(owner.name()).stream().map(ApiClass::name)
                    .filter(name -> name.equals("net/minecraft/item/Item") || name.equals("net/minecraft/world/item/Item")).toList();
            require(itemParents.size() == 1, "CONSTRUCTOR_SUPPLIER_ITEM_PARENT", owner.name());
            Type result = instantiated.getReturnType();
            require(result.getSort() == Type.OBJECT && (result.getInternalName().equals(owner.name()) || result.getInternalName().equals(itemParents.get(0)))
                            && hierarchy.assignable(result, Type.getObjectType(owner.name())),
                    "CONSTRUCTOR_SUPPLIER_RETURN", implementation.toString());
        }

        /** The five-method owned endpoint is independent of attributes, repair ingredients and game callbacks. */
        private void ownedStackDamageNativeContracts() {
            var proof = ownedStackDamageProof;
            stackDamageNativeContracts(classes.get(proof.item()), proof.helper(), proof.durability(), null);
        }

        /** Shared helper endpoints only; the new callback is omitted only after its complete six-method proof. */
        private void stackDamageNativeContracts(ClassNode item, String helper, int durability, MethodNode admittedUse) {
            boolean old = item.superName.equals("net/minecraft/item/Item");
            String nativeItem = item.superName, properties = nativeItem + "$Properties";
            String stack = old ? "net/minecraft/item/ItemStack" : "net/minecraft/world/item/ItemStack";
            String forge = "net/minecraftforge/common/extensions/IForgeItem";
            stackDamageNativeType(nativeItem, Opcodes.ACC_PUBLIC,
                    old ? "net/minecraftforge/registries/ForgeRegistryEntry" : "java/lang/Object",
                    old ? Set.of("net/minecraft/util/IItemProvider", forge)
                            : Set.of("net/minecraft/world/flag/FeatureElement", "net/minecraft/world/level/ItemLike", forge));
            stackDamageNativeType(properties, Opcodes.ACC_PUBLIC, "java/lang/Object", Set.of());
            stackDamageNativeType(stack, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                    "net/minecraftforge/common/capabilities/CapabilityProvider",
                    Set.of("net/minecraftforge/common/extensions/IForgeItemStack"));
            stackDamageNativeType(forge, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
                    "java/lang/Object", Set.of());
            require(durability == 128, "STACK_DAMAGE_CAPACITY", item.name);
            for (var parent : hierarchy.ancestors(nativeItem))
                require(LinkageHierarchy.declared(parent.methods(), helper, "(L" + stack + ";)Z") == null,
                        "STACK_DAMAGE_ACCIDENTAL_NATIVE_CALLBACK", parent.name() + "." + helper);
            for (MethodNode method : item.methods) if (method != admittedUse) for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    require(!call.itf && !hierarchy.isInterface(call.owner)
                                    && (call.name.equals("<init>") ? call.getOpcode() == Opcodes.INVOKESPECIAL : call.getOpcode() == Opcodes.INVOKEVIRTUAL),
                            "STACK_DAMAGE_INVOCATION_KIND", call.owner + "." + call.name + call.desc);
                    stackDamageDeclaration(call.owner, call.name, call.desc, Opcodes.ACC_PUBLIC);
                }
            }
            stackDamageDeclaration(stack, old ? "func_77984_f" : "m_41763_", "()Z", Opcodes.ACC_PUBLIC);
            stackDamageDeclaration(nativeItem, old ? "func_77612_l" : "m_41462_", "()I", Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL);
            stackDamageDeclaration(nativeItem, old ? "func_77645_m" : "m_41465_", "()Z", Opcodes.ACC_PUBLIC);
            for (String name : List.of("getDamage", "setDamage", "getMaxDamage", "isDamaged", "isDamageable")) {
                String descriptor = "(L" + stack + ";" + (name.equals("setDamage") ? "I)V" : ")" + (name.startsWith("is") ? "Z" : "I"));
                stackDamageDeclaration(forge, name, descriptor, Opcodes.ACC_PUBLIC);
                stackDamageDefaultOwner(item.name, forge, name, descriptor);
            }
            // Source's public default getItem and target's private self helper are platform internals,
            // not newly admitted mod calls. The platform itself is never rewritten.
            stackDamageDeclaration(forge, old ? "getItem" : "self", "()L" + nativeItem + ";",
                    old ? Opcodes.ACC_PUBLIC : Opcodes.ACC_PRIVATE);
            if (old) stackDamageDefaultOwner(item.name, forge, "getItem", "()L" + nativeItem + ";");
        }

        /** Independent declaration/access/dispatch gate; successful mappings are not trusted as proof. */
        private void ownedStackRepairUseNativeContracts() {
            var proof = ownedStackRepairUseProof;
            ClassNode item = classes.get(proof.item());
            boolean old = item.superName.equals("net/minecraft/item/Item");
            String stack = old ? "net/minecraft/item/ItemStack" : "net/minecraft/world/item/ItemStack";
            String world = old ? "net/minecraft/world/World" : "net/minecraft/world/level/Level";
            String player = old ? "net/minecraft/entity/player/PlayerEntity" : "net/minecraft/world/entity/player/Player";
            String living = old ? "net/minecraft/entity/LivingEntity" : "net/minecraft/world/entity/LivingEntity";
            String hand = old ? "net/minecraft/util/Hand" : "net/minecraft/world/InteractionHand";
            String holder = old ? "net/minecraft/util/ActionResult" : "net/minecraft/world/InteractionResultHolder";
            String useName = old ? "func_77659_a" : "m_7203_";
            String useDesc = "(L" + world + ";L" + player + ";L" + hand + ";)L" + holder + ";";
            MethodNode use = item.methods.stream().filter(m -> m.name.equals(useName) && m.desc.equals(useDesc))
                    .findFirst().orElseThrow(() -> new TransformationException("STACK_REPAIR_USE_REQUIRED_OVERRIDE"));
            stackDamageNativeContracts(item, proof.helper(), proof.durability(), use);
            stackDamageNativeType(world, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                    "net/minecraftforge/common/capabilities/CapabilityProvider",
                    old ? Set.of("net/minecraft/world/IWorld", "java/lang/AutoCloseable", "net/minecraftforge/common/extensions/IForgeWorld")
                            : Set.of("net/minecraft/world/level/LevelAccessor", "java/lang/AutoCloseable", "net/minecraftforge/common/extensions/IForgeLevel"));
            stackDamageNativeType(player, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, living,
                    old ? Set.of() : Set.of("net/minecraftforge/common/extensions/IForgePlayer"));
            stackDamageNativeType(living, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                    old ? "net/minecraft/entity/Entity" : "net/minecraft/world/entity/Entity",
                    old ? Set.of() : Set.of("net/minecraft/world/entity/Attackable", "net/minecraftforge/common/extensions/IForgeLivingEntity"));
            stackDamageNativeType(hand, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM, "java/lang/Enum", Set.of());
            stackDamageNativeType(holder, Opcodes.ACC_PUBLIC, "java/lang/Object", Set.of());
            stackDamageDeclaration(item.superName, useName, useDesc, Opcodes.ACC_PUBLIC);
            String getterName = old ? "func_184586_b" : "m_21120_", getterDesc = "(L" + hand + ";)L" + stack + ";";
            stackDamageDeclaration(living, getterName, getterDesc, Opcodes.ACC_PUBLIC);
            require(hierarchy.method(player, getterName, getterDesc).owner().name().equals(living),
                    "STACK_REPAIR_USE_HAND_LOOKUP_OWNER", player);
            // Reject even private/static/competing-interface lookalikes: the pinned alias has one declaration.
            for (var ancestor : hierarchy.ancestors(player)) if (!ancestor.name().equals(living))
                require(LinkageHierarchy.declared(ancestor.methods(), getterName, getterDesc) == null,
                        "STACK_REPAIR_USE_HAND_LOOKUP_SHADOW", ancestor.name());
            Set<String> factories = old ? Set.of("func_226248_a_", "func_226249_b_", "func_226250_c_")
                    : Set.of("m_19090_", "m_19096_", "m_19098_");
            String holderDesc = "(Ljava/lang/Object;)L" + holder + ";";
            for (String factory : factories) stackDamageDeclaration(holder, factory, holderDesc, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
            String sideName = old ? "field_72995_K" : "f_46443_";
            var fields = hierarchy.type(world).fields().stream().filter(f -> f.name().equals(sideName) && f.descriptor().equals("Z")).toList();
            int fieldMask = Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_STATIC
                    | Opcodes.ACC_FINAL | Opcodes.ACC_VOLATILE | Opcodes.ACC_TRANSIENT | Opcodes.ACC_ENUM | Opcodes.ACC_SYNTHETIC;
            require(fields.size() == 1 && (fields.get(0).access() & fieldMask) == (Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL)
                            && hierarchy.field(world, sideName, "Z").owner().name().equals(world),
                    "STACK_REPAIR_USE_SIDE_FIELD_DECLARATION", world + "." + sideName);
            int getters = 0, helpers = 0, sides = 0;
            Set<String> seenFactories = new HashSet<>();
            for (AbstractInsnNode instruction : use.instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    require(!call.itf && !hierarchy.isInterface(call.owner), "STACK_REPAIR_USE_INVOCATION_KIND", call.owner);
                    if (call.owner.equals(player) && call.name.equals(getterName) && call.desc.equals(getterDesc)) {
                        require(call.getOpcode() == Opcodes.INVOKEVIRTUAL, "STACK_REPAIR_USE_GETTER_OPCODE", call.name); getters++;
                    } else if (call.owner.equals(item.name) && call.name.equals(proof.helper()) && call.desc.equals("(L" + stack + ";)Z")) {
                        require(call.getOpcode() == Opcodes.INVOKEVIRTUAL
                                        && hierarchy.method(call.owner, call.name, call.desc).owner().name().equals(item.name),
                                "STACK_REPAIR_USE_HELPER_DISPATCH", call.name); helpers++;
                    } else {
                        require(call.owner.equals(holder) && factories.contains(call.name) && call.desc.equals(holderDesc)
                                        && call.getOpcode() == Opcodes.INVOKESTATIC && seenFactories.add(call.name),
                                "STACK_REPAIR_USE_RESULT_FACTORY", call.owner + "." + call.name);
                    }
                } else if (instruction instanceof FieldInsnNode field) {
                    require(field.owner.equals(world) && field.name.equals(sideName) && field.desc.equals("Z")
                                    && field.getOpcode() == Opcodes.GETFIELD,
                            "STACK_REPAIR_USE_SIDE_FIELD_OPCODE", field.owner + "." + field.name); sides++;
                }
            }
            require(getters == 1 && helpers == 1 && sides == 1 && seenFactories.equals(factories),
                    "STACK_REPAIR_USE_REFERENCE_COUNTS", item.name);
        }

        private void stackDamageNativeType(String owner, int expected, String parent, Set<String> interfaces) {
            var type = hierarchy.type(owner);
            int mask = Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_FINAL
                    | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE | Opcodes.ACC_ENUM | Opcodes.ACC_ANNOTATION;
            require(!hierarchy.owned(owner) && (type.access() & mask) == expected
                            && java.util.Objects.equals(type.superName(), parent)
                            && type.interfaces().size() == interfaces.size()
                            && new HashSet<>(type.interfaces()).equals(interfaces),
                    "STACK_DAMAGE_NATIVE_TYPE_CONTRACT", owner);
        }

        private void stackDamageDefaultOwner(String item, String forge, String name, String descriptor) {
            // This is an assertion about the pinned native hook route, NOT permission for mod code
            // to use the verifier's deliberately unsupported generic default-interface dispatch.
            var ancestors = hierarchy.ancestors(item);
            require(ancestors.stream().anyMatch(type -> type.name().equals(forge)), "STACK_DAMAGE_MISSING_NATIVE_INTERFACE", item);
            for (var type : ancestors) if (!type.name().equals(forge))
                require(LinkageHierarchy.declared(type.methods(), name, descriptor) == null,
                        "STACK_DAMAGE_NATIVE_HOOK_SHADOW", type.name() + "." + name + descriptor);
        }

        private void stackDamageDeclaration(String owner, String name, String descriptor, int expected) {
            int mask = Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_STATIC
                    | Opcodes.ACC_FINAL | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_SYNCHRONIZED
                    | Opcodes.ACC_BRIDGE | Opcodes.ACC_VARARGS | Opcodes.ACC_SYNTHETIC;
            var declarations = hierarchy.type(owner).methods().stream()
                    .filter(member -> member.name().equals(name) && member.descriptor().equals(descriptor)).toList();
            require(declarations.size() == 1, "STACK_DAMAGE_NATIVE_DECLARATION_COUNT", owner + "." + name + descriptor);
            var declaration = declarations.get(0);
            require(!hierarchy.owned(owner) && (declaration.access() & mask) == expected
                            && hierarchy.method(owner, name, descriptor).owner().name().equals(owner),
                    "STACK_DAMAGE_NATIVE_DECLARATION", owner + "." + name + descriptor);
        }

        /** Separate finite Item contract: native declarations cannot drift merely because bytecode still links. */
        private void itemAttributeNativeContracts(ClassNode node) {
            itemAttributeNativeContracts(node, false);
        }

        /** The combined policy has its own re-read proof; only its separately checked repair body is omitted here. */
        private void itemAttributeNativeContracts(ClassNode node, boolean attributeDurability) {
            boolean old = node.superName.equals("net/minecraft/item/Item");
            String nativeItem = node.superName;
            String attribute = old ? "net/minecraft/entity/ai/attributes/Attribute" : "net/minecraft/world/entity/ai/attributes/Attribute";
            String attributes = old ? "net/minecraft/entity/ai/attributes/Attributes" : "net/minecraft/world/entity/ai/attributes/Attributes";
            String modifier = old ? "net/minecraft/entity/ai/attributes/AttributeModifier" : "net/minecraft/world/entity/ai/attributes/AttributeModifier";
            String slot = old ? "net/minecraft/inventory/EquipmentSlotType" : "net/minecraft/world/entity/EquipmentSlot";
            String multimap = "com/google/common/collect/Multimap", immutable = "com/google/common/collect/ImmutableMultimap";
            for (String owner : List.of(nativeItem, attribute, attributes, modifier, immutable + "$Builder"))
                itemAttributeClassKind(owner, Opcodes.ACC_PUBLIC);
            itemAttributeClassKind(slot, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM);
            itemAttributeClassKind(modifier + "$Operation", Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM);
            itemAttributeClassKind(multimap, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT);
            itemAttributeClassKind(immutable, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT);
            int methodMask = Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_STATIC
                    | Opcodes.ACC_FINAL | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_SYNCHRONIZED
                    | Opcodes.ACC_BRIDGE | Opcodes.ACC_VARARGS | Opcodes.ACC_SYNTHETIC;
            int fieldMask = Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_STATIC
                    | Opcodes.ACC_FINAL | Opcodes.ACC_VOLATILE | Opcodes.ACC_TRANSIENT | Opcodes.ACC_ENUM | Opcodes.ACC_SYNTHETIC;
            for (MethodNode method : node.methods) {
                if (attributeDurability && method.name.equals(old ? "func_82789_a" : "m_6832_")) continue;
                if (!method.name.startsWith("<")) {
                    require(method.name.equals(old ? "func_111205_h" : "m_7167_")
                                    && method.desc.equals("(L" + slot + ";)L" + multimap + ";"),
                            "ITEM_ATTRIBUTES_CALLBACK_SHAPE", node.name + "." + method.name + method.desc);
                    var declaration = LinkageHierarchy.declared(hierarchy.type(nativeItem).methods(), method.name, method.desc);
                    require(declaration != null && (declaration.access() & methodMask) == Opcodes.ACC_PUBLIC,
                            "ITEM_ATTRIBUTES_REQUIRED_OVERRIDE", node.name + "." + method.name);
                    require(hierarchy.method(nativeItem, method.name, method.desc).owner().name().equals(nativeItem),
                            "ITEM_ATTRIBUTES_OVERRIDE_OWNER_CHANGED", node.name + "." + method.name);
                }
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call) {
                        var resolved = hierarchy.method(call.owner, call.name, call.desc);
                        require(!hierarchy.owned(resolved.owner().name()), "ITEM_ATTRIBUTES_UNEXPECTED_OWNED_CALL", call.owner);
                        int expected = Opcodes.ACC_PUBLIC | (call.getOpcode() == Opcodes.INVOKESTATIC ? Opcodes.ACC_STATIC : 0);
                        require(resolved.owner().name().equals(call.owner) && (resolved.member().access() & methodMask) == expected,
                                "ITEM_ATTRIBUTES_NATIVE_METHOD_CONTRACT", call.owner + "." + call.name + call.desc);
                        require(!call.itf && !hierarchy.isInterface(call.owner)
                                        && (call.name.equals("<init>") ? call.getOpcode() == Opcodes.INVOKESPECIAL
                                        : call.owner.equals(immutable) ? call.getOpcode() == Opcodes.INVOKESTATIC
                                        : call.owner.equals(immutable + "$Builder") && call.getOpcode() == Opcodes.INVOKEVIRTUAL),
                                "ITEM_ATTRIBUTES_INVOCATION_KIND", call.owner + "." + call.name + call.desc);
                    } else if (instruction instanceof FieldInsnNode field) {
                        var resolved = hierarchy.field(field.owner, field.name, field.desc);
                        if (hierarchy.owned(resolved.owner().name())) continue; // Owned constant already re-proved; normal access checks still run.
                        boolean inheritedUuid = field.owner.equals(node.name);
                        String expectedOwner = inheritedUuid ? nativeItem : field.owner;
                        int expected = Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | (inheritedUuid ? Opcodes.ACC_PROTECTED : Opcodes.ACC_PUBLIC);
                        if (expectedOwner.equals(slot) || expectedOwner.equals(modifier + "$Operation")) expected |= Opcodes.ACC_ENUM;
                        require(field.getOpcode() == Opcodes.GETSTATIC && resolved.owner().name().equals(expectedOwner)
                                        && (resolved.member().access() & fieldMask) == expected
                                        && (!inheritedUuid || field.desc.equals("Ljava/util/UUID;")),
                                "ITEM_ATTRIBUTES_NATIVE_FIELD_CONTRACT", field.owner + "." + field.name + field.desc);
                    }
                }
            }
        }

        /** No inference from successful mappings: recheck the combined native repair/property endpoint independently. */
        private void itemAttributeDurabilityNativeContracts() {
            var proof = itemAttributeDurabilityProof.attributeProof();
            ClassNode item = classes.get(proof.item()), entry = classes.get(proof.entry());
            itemAttributeNativeContracts(item, true);
            boolean old = item.superName.equals("net/minecraft/item/Item");
            String nativeItem = item.superName, properties = nativeItem + "$Properties";
            String stack = old ? "net/minecraft/item/ItemStack" : "net/minecraft/world/item/ItemStack";
            String items = old ? "net/minecraft/item/Items" : "net/minecraft/world/item/Items";
            String repairName = old ? "func_82789_a" : "m_6832_";
            String repairDesc = "(L" + stack + ";L" + stack + ";)Z";
            itemAttributeClassKind(properties, Opcodes.ACC_PUBLIC);
            itemAttributeClassKind(items, Opcodes.ACC_PUBLIC);
            itemAttributeClassKind(stack, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL);
            require(itemAttributeDurabilityProof.durability() == 128, "ITEM_ATTRIBUTE_DURABILITY_LITERAL", proof.item());
            itemAttributeDurabilityMethod(nativeItem, repairName, repairDesc);
            var repair = item.methods.stream().filter(m -> m.name.equals(repairName) && m.desc.equals(repairDesc))
                    .findFirst().orElseThrow(() -> new TransformationException("ITEM_ATTRIBUTE_DURABILITY_REQUIRED_REPAIR_OVERRIDE"));
            int getterCalls = 0, ironFields = 0;
            for (AbstractInsnNode instruction : repair.instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    require(call.owner.equals(stack) && call.name.equals(old ? "func_77973_b" : "m_41720_")
                                    && call.desc.equals("()L" + nativeItem + ";") && !call.itf && call.getOpcode() == Opcodes.INVOKEVIRTUAL,
                            "ITEM_ATTRIBUTE_DURABILITY_GETTER_OPCODE", call.owner + "." + call.name);
                    itemAttributeDurabilityMethod(call.owner, call.name, call.desc); getterCalls++;
                } else if (instruction instanceof FieldInsnNode field) {
                    require(field.owner.equals(items) && field.name.equals(old ? "field_151042_j" : "f_42416_")
                                    && field.desc.equals("L" + nativeItem + ";") && field.getOpcode() == Opcodes.GETSTATIC,
                            "ITEM_ATTRIBUTE_DURABILITY_IRON_REFERENCE", field.owner + "." + field.name);
                    var resolved = hierarchy.field(field.owner, field.name, field.desc);
                    int mask = Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_STATIC
                            | Opcodes.ACC_FINAL | Opcodes.ACC_VOLATILE | Opcodes.ACC_TRANSIENT | Opcodes.ACC_ENUM | Opcodes.ACC_SYNTHETIC;
                    require(resolved.owner().name().equals(items)
                                    && (resolved.member().access() & mask) == (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL),
                            "ITEM_ATTRIBUTE_DURABILITY_IRON_DECLARATION", field.owner + "." + field.name);
                    ironFields++;
                }
            }
            require(getterCalls == 1 && ironFields == 1, "ITEM_ATTRIBUTE_DURABILITY_REPAIR_REFERENCES", item.name);
            var factory = entry.methods.stream().filter(m -> m.name.equals(proof.factory())).findFirst().orElseThrow();
            int propertyCalls = 0;
            for (AbstractInsnNode instruction : factory.instructions) if (instruction instanceof MethodInsnNode call && call.owner.equals(properties)) {
                boolean constructor = call.name.equals("<init>") && call.desc.equals("()V") && call.getOpcode() == Opcodes.INVOKESPECIAL;
                boolean durability = call.name.equals(old ? "func_200918_c" : "m_41503_")
                        && call.desc.equals("(I)L" + properties + ";") && call.getOpcode() == Opcodes.INVOKEVIRTUAL;
                require(!call.itf && (constructor || durability), "ITEM_ATTRIBUTE_DURABILITY_PROPERTY_OPCODE", call.owner + "." + call.name);
                itemAttributeDurabilityMethod(call.owner, call.name, call.desc); propertyCalls++;
            }
            require(propertyCalls == 2, "ITEM_ATTRIBUTE_DURABILITY_PROPERTY_REFERENCES", entry.name);
        }

        private void itemAttributeDurabilityMethod(String owner, String name, String descriptor) {
            int mask = Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_STATIC
                    | Opcodes.ACC_FINAL | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_SYNCHRONIZED
                    | Opcodes.ACC_BRIDGE | Opcodes.ACC_VARARGS | Opcodes.ACC_SYNTHETIC;
            var declaration = LinkageHierarchy.declared(hierarchy.type(owner).methods(), name, descriptor);
            require(declaration != null && (declaration.access() & mask) == Opcodes.ACC_PUBLIC
                            && !hierarchy.owned(owner) && !hierarchy.isInterface(owner)
                            && hierarchy.method(owner, name, descriptor).owner().name().equals(owner),
                    "ITEM_ATTRIBUTE_DURABILITY_NATIVE_METHOD", owner + "." + name + descriptor);
        }

        private void itemAttributeClassKind(String owner, int expected) {
            int mask = Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_FINAL
                    | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE | Opcodes.ACC_ENUM | Opcodes.ACC_ANNOTATION;
            require(!hierarchy.owned(owner) && (hierarchy.type(owner).access() & mask) == expected,
                    "ITEM_ATTRIBUTES_NATIVE_CLASS_KIND", owner);
        }

        /** Semantic declaration checks are stricter than JVM legality and confined to the re-proved graph. */
        private void booleanToggleNativeContracts(ClassNode node) {
            boolean old=node.superName.equals("net/minecraft/block/Block");
            String nativeBlock=node.superName, behaviour=old?"net/minecraft/block/AbstractBlock":"net/minecraft/world/level/block/state/BlockBehaviour";
            String state=old?"net/minecraft/block/BlockState":"net/minecraft/world/level/block/state/BlockState";
            String holder=old?"net/minecraft/state/StateHolder":"net/minecraft/world/level/block/state/StateHolder";
            String registerDefault=old?"func_180632_j":"m_49959_";
            int mask=Opcodes.ACC_PUBLIC|Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED|Opcodes.ACC_STATIC
                    |Opcodes.ACC_FINAL|Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE;
            for(MethodNode method:node.methods) {
                if(!method.name.startsWith("<")) {
                    boolean definition=(method.access&Opcodes.ACC_PROTECTED)!=0;
                    String owner=definition?nativeBlock:behaviour;
                    var declaration=LinkageHierarchy.declared(hierarchy.type(owner).methods(),method.name,method.desc);
                    require(declaration!=null&&(declaration.access()&mask)==(definition?Opcodes.ACC_PROTECTED:Opcodes.ACC_PUBLIC),
                            "BOOLEAN_TOGGLE_REQUIRED_OVERRIDE",node.name+"."+method.name);
                    require(hierarchy.method(nativeBlock,method.name,method.desc).owner().name().equals(owner),
                            "BOOLEAN_TOGGLE_OVERRIDE_OWNER_CHANGED",node.name+"."+method.name);
                }
                for(AbstractInsnNode instruction:method.instructions) {
                    if(instruction instanceof MethodInsnNode call) {
                        var member=hierarchy.method(call.owner,call.name,call.desc);
                        if(hierarchy.owned(member.owner().name()))continue;
                        String expectedOwner=call.owner.equals(node.name)?nativeBlock:call.owner.equals(state)?holder:call.owner;
                        boolean protectedDefault=call.name.equals(registerDefault)&&expectedOwner.equals(nativeBlock);
                        int expected=protectedDefault?Opcodes.ACC_PROTECTED|Opcodes.ACC_FINAL:Opcodes.ACC_PUBLIC;
                        if(call.getOpcode()==Opcodes.INVOKESTATIC)expected|=Opcodes.ACC_STATIC;
                        require(member.owner().name().equals(expectedOwner)&&(member.member().access()&mask)==expected,
                                "BOOLEAN_TOGGLE_NATIVE_METHOD_CONTRACT",call.owner+"."+call.name+call.desc);
                    } else if(instruction instanceof FieldInsnNode field) {
                        var member=hierarchy.field(field.owner,field.name,field.desc);
                        if(hierarchy.owned(member.owner().name()))continue;
                        String expectedOwner=field.owner.equals(node.name)?nativeBlock:field.owner;
                        int expected=Opcodes.ACC_FINAL|(expectedOwner.equals(nativeBlock)?Opcodes.ACC_PROTECTED:Opcodes.ACC_PUBLIC);
                        if(field.getOpcode()==Opcodes.GETSTATIC)expected|=Opcodes.ACC_STATIC;
                        require(member.owner().name().equals(expectedOwner)&&(member.member().access()&mask)==expected,
                                "BOOLEAN_TOGGLE_NATIVE_FIELD_CONTRACT",field.owner+"."+field.name+field.desc);
                    }
                }
            }
        }

        private void overridesAndAbstractRequirements(ClassNode node) {
            List<ApiClass> ancestors = hierarchy.ancestors(node.name);
            if (staticShapeProof != null && node.name.equals(staticShapeProof.block())) {
                String declaringOwner = node.superName.equals("net/minecraft/block/Block")
                        ? "net/minecraft/block/AbstractBlock" : "net/minecraft/world/level/block/state/BlockBehaviour";
                ApiClass nativeOwner = hierarchy.type(declaringOwner);
                hierarchy.accessibleClass(node.name, declaringOwner);
                for (MethodNode callback : node.methods) if (!callback.name.startsWith("<")) {
                    ApiMember nativeMethod = LinkageHierarchy.declared(nativeOwner.methods(), callback.name, callback.desc);
                    int mask = Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_STATIC
                            | Opcodes.ACC_FINAL | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE;
                    require(nativeMethod != null && (nativeMethod.access() & mask) == Opcodes.ACC_PUBLIC,
                            "STATIC_SHAPE_REQUIRED_OVERRIDE", node.name + "." + callback.name + " requires " + declaringOwner);
                    // A new nearer declaration changes dispatch even if the old ancestor still exists.
                    require(hierarchy.method(node.superName, callback.name, callback.desc).owner().name().equals(declaringOwner),
                            "STATIC_SHAPE_OVERRIDE_OWNER_CHANGED", node.name + "." + callback.name);
                }
            }
            for (MethodNode method : node.methods) {
                if (method.name.startsWith("<")) continue;
                for (ApiClass parent : ancestors) {
                    if (parent.name().equals(node.name)) continue;
                    ApiMember inherited = LinkageHierarchy.declared(parent.methods(), method.name, method.desc);
                    if (inherited == null || (inherited.access() & Opcodes.ACC_PRIVATE) != 0) continue;
                    if ((parent.access() & Opcodes.ACC_INTERFACE) != 0 && (inherited.access() & Opcodes.ACC_STATIC) != 0) continue;
                    if ((inherited.access() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) == 0 && !hierarchy.sameRuntimePackage(node.name, parent.name())) continue;
                    require((inherited.access() & Opcodes.ACC_FINAL) == 0, "FINAL_METHOD_OVERRIDE", node.name + "." + method.name);
                    require(((method.access ^ inherited.access()) & Opcodes.ACC_STATIC) == 0, "OVERRIDE_STATICNESS", node.name + "." + method.name);
                    require(visibility(method.access) >= visibility(inherited.access()), "WEAKER_OVERRIDE_ACCESS", node.name + "." + method.name);
                }
            }
            if ((node.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE)) != 0) return;
            for (ApiClass parent : ancestors) for (ApiMember requirement : parent.methods()) {
                if ((requirement.access() & Opcodes.ACC_ABSTRACT) == 0) continue;
                LinkageHierarchy.Member implementation = hierarchy.method(node.name, requirement.name(), requirement.descriptor());
                require((implementation.member().access() & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0,
                        "ABSTRACT_METHOD_UNIMPLEMENTED", node.name + "." + requirement.name() + requirement.descriptor());
                if ((parent.access() & Opcodes.ACC_INTERFACE) != 0)
                    require((implementation.member().access() & Opcodes.ACC_PUBLIC) != 0, "INTERFACE_IMPLEMENTATION_ACCESS", node.name + "." + requirement.name());
                else if ((requirement.access() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) == 0)
                    require(hierarchy.sameRuntimePackage(parent.name(), implementation.owner().name()), "PACKAGE_ABSTRACT_IMPLEMENTATION_UNSUPPORTED", node.name);
            }
        }

        private void constantPool(ClassNode node) {
            ClassReader reader = new ClassReader(bytes.get(node.name)); char[] buffer = new char[reader.getMaxStringLength()];
            for (int index = 1; index < reader.getItemCount(); index++) {
                OperationControl.checkpoint(); int offset = reader.getItem(index); if (offset == 0) continue;
                int tag = reader.readByte(offset - 1);
                if (tag == 7) {
                    String name = reader.readUTF8(offset, buffer);
                    if (!reviewedOpaqueMetadata(node.name, name)) hierarchy.accessibleClass(node.name, name);
                }
                if (tag == 16) descriptor(node.name, reader.readUTF8(offset, buffer));
                if (tag == 17) LinkageHierarchy.fail("CONSTANT_DYNAMIC_UNSUPPORTED", node.name);
                if (tag == 9 || tag == 10 || tag == 11) {
                    String owner = reader.readClass(offset, buffer);
                    int signature = reader.getItem(reader.readUnsignedShort(offset + 2));
                    String name = reader.readUTF8(signature, buffer), descriptor = reader.readUTF8(signature + 2, buffer);
                    hierarchy.accessibleClass(node.name, owner);
                    if (tag == 10) invocationDescriptor(node.name, owner, name, descriptor);
                    else descriptor(node.name, descriptor);
                    LinkageHierarchy.Member member;
                    if (tag == 9) member = hierarchy.field(owner, name, descriptor);
                    else {
                        require(hierarchy.isInterface(owner) == (tag == 11), "CONSTANT_POOL_OWNER_KIND", owner);
                        member = hierarchy.method(owner, name, descriptor);
                    }
                    // Receiver-specific protected checks require instruction dataflow and occur separately.
                    hierarchy.accessibleMember(node.name, member, null, false);
                }
            }
        }

        private void descriptor(String caller, String descriptor) {
            require(descriptor != null && descriptor.length() <= 16_384, "INVALID_DESCRIPTOR", caller);
            hierarchy.accessibleType(caller, Type.getType(descriptor));
        }
        private void throwable(String caller, String type) {
            hierarchy.accessibleClass(caller, type);
            require(hierarchy.subtype(type, "java/lang/Throwable"), "NON_THROWABLE_EXCEPTION_TYPE", type);
        }
        private void constant(String caller, Object value) {
            if (value instanceof Type type) hierarchy.accessibleType(caller, type);
            else if (value instanceof ConstantDynamic) LinkageHierarchy.fail("CONSTANT_DYNAMIC_UNSUPPORTED", caller);
            else if (value instanceof Handle) LinkageHierarchy.fail("STANDALONE_METHOD_HANDLE_UNSUPPORTED", caller);
            else require(value == null || value instanceof String || value instanceof Number, "CONSTANT_KIND_UNSUPPORTED", caller);
        }

        private void annotations(String caller, List<? extends AnnotationNode> annotations, int depth) {
            if (annotations == null) return;
            require(depth < 32 && annotations.size() <= 256, "LINKAGE_LIMIT", "Annotation depth/count");
            for (AnnotationNode annotation : annotations) {
                Type type = Type.getType(annotation.desc); hierarchy.accessibleType(caller, type);
                ApiClass declaration = hierarchy.type(type.getInternalName());
                require((declaration.access() & Opcodes.ACC_ANNOTATION) != 0, "ANNOTATION_OWNER_KIND", declaration.name());
                if (annotation.values == null) continue;
                require(annotation.values.size() <= 1024 && annotation.values.size() % 2 == 0, "INVALID_ANNOTATION_VALUES", caller);
                Set<String> names = new HashSet<>();
                for (int index = 0; index < annotation.values.size(); index += 2) {
                    require(annotation.values.get(index) instanceof String, "INVALID_ANNOTATION_ELEMENT", caller);
                    String name = (String) annotation.values.get(index); require(names.add(name), "DUPLICATE_ANNOTATION_ELEMENT", name);
                    List<ApiMember> members = declaration.methods().stream().filter(method -> method.name().equals(name) && Type.getArgumentTypes(method.descriptor()).length == 0).toList();
                    require(members.size() == 1, "MISSING_ANNOTATION_ELEMENT", declaration.name() + "." + name);
                    annotationValue(caller, annotation.values.get(index + 1), Type.getReturnType(members.get(0).descriptor()), depth + 1);
                }
            }
        }
        private void annotationValue(String caller, Object value, Type expected, int depth) {
            require(depth < 32, "LINKAGE_LIMIT", "Annotation value nesting");
            hierarchy.accessibleType(caller, expected);
            if (value instanceof List<?> values) {
                require(expected.getSort() == Type.ARRAY && values.size() <= 1024, "ANNOTATION_VALUE_TYPE", caller);
                for (Object element : values) annotationValue(caller, element, Type.getType(expected.getDescriptor().substring(1)), depth + 1);
            } else if (value instanceof AnnotationNode nested) {
                require(expected.getDescriptor().equals(nested.desc), "ANNOTATION_VALUE_TYPE", caller); annotations(caller, List.of(nested), depth + 1);
            } else if (value instanceof String[] enumeration) {
                require(enumeration.length == 2 && expected.getDescriptor().equals(enumeration[0]), "ANNOTATION_ENUM_TYPE", caller);
                String owner = Type.getType(enumeration[0]).getInternalName(); hierarchy.accessibleClass(caller, owner);
                LinkageHierarchy.Member field = hierarchy.field(owner, enumeration[1], enumeration[0]);
                require((field.member().access() & (Opcodes.ACC_STATIC | Opcodes.ACC_ENUM)) == (Opcodes.ACC_STATIC | Opcodes.ACC_ENUM), "ANNOTATION_ENUM_CONSTANT", owner);
                hierarchy.accessibleMember(caller, field, null, false);
            } else if (value instanceof Type literal) {
                require(expected.equals(Type.getType("Ljava/lang/Class;")), "ANNOTATION_CLASS_LITERAL_TYPE", caller); hierarchy.accessibleType(caller, literal);
            } else {
                boolean valid = value instanceof String && expected.equals(Type.getType("Ljava/lang/String;"))
                        || value instanceof Boolean && expected.equals(Type.BOOLEAN_TYPE) || value instanceof Byte && expected.equals(Type.BYTE_TYPE)
                        || value instanceof Character && expected.equals(Type.CHAR_TYPE) || value instanceof Short && expected.equals(Type.SHORT_TYPE)
                        || value instanceof Integer && expected.equals(Type.INT_TYPE) || value instanceof Long && expected.equals(Type.LONG_TYPE)
                        || value instanceof Float && expected.equals(Type.FLOAT_TYPE) || value instanceof Double && expected.equals(Type.DOUBLE_TYPE);
                require(valid, "ANNOTATION_VALUE_TYPE", caller);
            }
        }

        private void signature(String caller, String signature, boolean field) {
            if (signature == null) return;
            require(signature.length() <= 16_384, "LINKAGE_LIMIT", "Signature length");
            SignatureVisitor visitor = new SignatureReferences(caller);
            if (field) new SignatureReader(signature).acceptType(visitor); else new SignatureReader(signature).accept(visitor);
        }
        private void parameterAnnotations(String caller, List<AnnotationNode>[] parameters) {
            if (parameters != null) for (List<AnnotationNode> parameter : parameters) annotations(caller, parameter, 0);
        }
        private void frameTypes(String caller, List<Object> values) {
            if (values != null) for (Object value : values) if (value instanceof String name) hierarchy.accessibleClass(caller, name);
        }
        private final class SignatureReferences extends SignatureVisitor {
            private final String caller; private String current;
            SignatureReferences(String caller) { super(Opcodes.ASM9); this.caller = caller; }
            @Override public void visitClassType(String name) { current = name; hierarchy.accessibleClass(caller, name); }
            @Override public void visitInnerClassType(String name) { current += "$" + name; hierarchy.accessibleClass(caller, current); }
            private SignatureVisitor child() { return new SignatureReferences(caller); }
            @Override public SignatureVisitor visitClassBound() { return child(); }
            @Override public SignatureVisitor visitInterfaceBound() { return child(); }
            @Override public SignatureVisitor visitSuperclass() { return child(); }
            @Override public SignatureVisitor visitInterface() { return child(); }
            @Override public SignatureVisitor visitParameterType() { return child(); }
            @Override public SignatureVisitor visitReturnType() { return child(); }
            @Override public SignatureVisitor visitExceptionType() { return child(); }
            @Override public SignatureVisitor visitArrayType() { return child(); }
            @Override public SignatureVisitor visitTypeArgument(char wildcard) { return child(); }
        }
        private static BasicValue receiver(Frame<BasicValue> frame, int arguments) {
            if (frame == null || frame.getStackSize() <= arguments) return null;
            return frame.getStack(frame.getStackSize() - arguments - 1);
        }
    }

    private static int visibility(int access) { return (access & Opcodes.ACC_PUBLIC) != 0 ? 3 : (access & Opcodes.ACC_PROTECTED) != 0 ? 2 : (access & Opcodes.ACC_PRIVATE) != 0 ? 0 : 1; }
    private static boolean forbiddenOwner(String name) { return LinkageHierarchy.platform(name) || name.startsWith("net/minecraft/") || name.startsWith("net/minecraftforge/") || name.startsWith("cpw/mods/") || name.startsWith("com/mojang/"); }
    private static boolean empty(List<?> value) { return value == null || value.isEmpty(); }
    private static void require(boolean condition, String code, String detail) { if (!condition) LinkageHierarchy.fail(code, detail); }
}
