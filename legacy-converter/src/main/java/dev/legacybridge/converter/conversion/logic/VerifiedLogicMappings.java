package dev.legacybridge.converter.conversion.logic;

import dev.legacybridge.converter.mapping.*;
import dev.legacybridge.converter.mapping.source.ApiIndex;
import dev.legacybridge.converter.mapping.source.MappingInputs;
import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.transformation.LegacyReferenceScanner;
import dev.legacybridge.converter.transformation.TransformationException;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import dev.legacybridge.runtime.compat.logic.PreservedLogicAdapter;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Compiles independently reviewed symbol contracts for a finite own-logic profile.
 * It is NOT a generic HIGH-to-VERIFIED promotion and never executes an input class.
 * All original methods stay in the caller's trees; this class only builds immutable rules.
 */
public final class VerifiedLogicMappings {
    private static final String ITEM = "net/minecraft/world/item/Item";
    private static final String STACK = "net/minecraft/world/item/ItemStack";
    private static final String PROPERTIES = ITEM + "$Properties";
    private static final String DEFERRED = "net/minecraftforge/registries/DeferredRegister";
    private static final String REGISTRIES = "net/minecraftforge/registries/ForgeRegistries";
    private static final String REGISTRY = "net/minecraftforge/registries/IForgeRegistry";
    private static final String OLD_OBJECT = "net/minecraftforge/fml/RegistryObject";
    private static final String NEW_OBJECT = "net/minecraftforge/registries/RegistryObject";
    private static final String BUS = "net/minecraftforge/eventbus/api/IEventBus";
    private static final String CONTEXT = "net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext";
    private static final String MOD = "net/minecraftforge/fml/common/Mod";
    private static final String SUPPLIER = "java/util/function/Supplier";
    private static final String OPERATOR = "java/util/function/IntUnaryOperator";
    private static final String METAFACTORY = "java/lang/invoke/LambdaMetafactory";
    private static final String METAFACTORY_DESC = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;";
    private static final Set<String> BOOTSTRAP_TYPES = Set.of(METAFACTORY, "java/lang/invoke/MethodHandles",
            "java/lang/invoke/MethodHandles$Lookup", "java/lang/invoke/MethodHandle", "java/lang/invoke/MethodType", "java/lang/invoke/CallSite");
    private static final Set<String> JDK_TYPES = Set.of("java/lang/Object", "java/lang/String", "java/lang/IllegalArgumentException",
            "java/lang/RuntimeException", "java/lang/Exception", "java/lang/Throwable", "java/lang/FunctionalInterface", SUPPLIER, OPERATOR);

    public record Result(MappingDatabase database, Map<LegacySymbol, String> adapterBindings,
                         ApiIndex sourceApi, ApiIndex targetApi, List<Map<String, Object>> rules,
                         PlainBlockApiRecipe plainBlockRecipe) {
        public Result(MappingDatabase database, Map<LegacySymbol, String> adapterBindings,
                      ApiIndex sourceApi, ApiIndex targetApi, List<Map<String, Object>> rules) {
            this(database, adapterBindings, sourceApi, targetApi, rules, null);
        }
        public Result {
            adapterBindings = Collections.unmodifiableMap(new TreeMap<>(adapterBindings));
            rules = rules.stream().map(Map::copyOf).toList();
        }
    }

    private record Recipe(LegacySymbol source, TargetSymbol target, String contract, int opcode) { }
    private record Declaration(String owner, ApiIndex.ApiMember member) { }

    private final Map<String, ClassNode> own;
    private final LegacyReferenceScanner.ScanResult scan;
    private final MappingInputs inputs;
    private final LogicApiNamespace sourceNamespace;
    private final LogicApiNamespace targetNamespace;
    private final ApiIndex sourceApi;
    private final ApiIndex targetApi;
    private final Map<String, String> classes = new TreeMap<>();
    private final Map<LegacySymbol, Recipe> recipes = new TreeMap<>();
    /** Exact state call-site aliases admitted only by independent finite state-profile proofs. */
    private final Map<LegacySymbol, LegacySymbol> horizontalAliases = new TreeMap<>();
    private final Map<LegacySymbol, MappingRule> approved = new TreeMap<>();
    private final Map<LegacySymbol, String> bindings = new TreeMap<>();
    private final Map<LegacySymbol, Map<String, Object>> review = new TreeMap<>();
    private final Map<String, ApiIndex.ApiClass> jdk = new HashMap<>();
    private final Set<String> lambdaClasses = new HashSet<>();
    private MappingDatabase generated;
    private final boolean lifecycle;
    private final boolean food;
    private final boolean durable;
    private final boolean plainBlock;
    private final boolean horizontalBlock;
    private final boolean orientationTransforms;
    private final boolean staticShapeBlock;
    private final boolean booleanToggleBlock;
    private final boolean itemAttributes;
    private final boolean itemAttributeDurability;
    private final dev.legacybridge.converter.conversion.OwnedStackDamageContract.Proof ownedStackDamageProof;
    private final dev.legacybridge.converter.conversion.OwnedStackRepairUseContract.Proof ownedStackRepairUseProof;
    private final PlainBlockApiRecipe plainBlockRecipe;
    private final boolean network;
    private final dev.legacybridge.converter.conversion.EventPriorityContract.Proof eventProof;
    private final dev.legacybridge.converter.conversion.ClientConfigContract.Proof clientConfigProof;
    private final dev.legacybridge.converter.conversion.ServerConfigContract.Proof serverConfigProof;
    private final dev.legacybridge.converter.conversion.LifecycleSuiteContract.Proof suiteProof;
    private static final String FOOD = "net/minecraft/world/food/FoodProperties";
    private static final String ERASED_ENTRY = "net/minecraftforge/registries/IForgeRegistryEntry";
    private static final String SETUP = "net/minecraftforge/fml/event/lifecycle/FMLCommonSetupEvent";
    private static final String SUBSCRIBER = MOD + "$EventBusSubscriber";
    private static final String SUBSCRIBE = "net/minecraftforge/eventbus/api/SubscribeEvent";
    private static final String SPEC = "net/minecraftforge/common/ForgeConfigSpec";
    private static final String BLOCK = "net/minecraft/world/level/block/Block";
    private static final String BLOCK_STATE = "net/minecraft/world/level/block/state/BlockState";
    private static final String STATE = "net/minecraft/world/level/block/state/StateHolder";
    private static final String DEFINITION = "net/minecraft/world/level/block/state/StateDefinition";
    private static final String STATE_PROPERTIES = "net/minecraft/world/level/block/state/properties/BlockStateProperties";
    private static final String PROPERTY = "net/minecraft/world/level/block/state/properties/Property";
    private static final String DIRECTION_PROPERTY = "net/minecraft/world/level/block/state/properties/DirectionProperty";
    private static final String DIRECTION = "net/minecraft/core/Direction";
    private static final String PLACEMENT = "net/minecraft/world/item/context/BlockPlaceContext";
    private static final String USE_CONTEXT = "net/minecraft/world/item/context/UseOnContext";
    private static final String ROTATION = "net/minecraft/world/level/block/Rotation";
    private static final String MIRROR = "net/minecraft/world/level/block/Mirror";
    private static final String VOXEL_SHAPE = "net/minecraft/world/phys/shapes/VoxelShape";
    private static final String COLLISION_CONTEXT = "net/minecraft/world/phys/shapes/CollisionContext";
    private static final String BLOCK_GETTER = "net/minecraft/world/level/BlockGetter";
    private static final String BLOCK_POS = "net/minecraft/core/BlockPos";
    private static final String BOOLEAN_PROPERTY = "net/minecraft/world/level/block/state/properties/BooleanProperty";
    private static final String LEVEL = "net/minecraft/world/level/Level";
    private static final String PLAYER = "net/minecraft/world/entity/player/Player";
    private static final String LIVING = "net/minecraft/world/entity/LivingEntity";
    private static final String RESULT_HOLDER = "net/minecraft/world/InteractionResultHolder";
    private static final String HAND = "net/minecraft/world/InteractionHand";
    private static final String BLOCK_HIT = "net/minecraft/world/phys/BlockHitResult";
    private static final String INTERACTION_RESULT = "net/minecraft/world/InteractionResult";
    private static final String ATTRIBUTE = "net/minecraft/world/entity/ai/attributes/Attribute";
    private static final String ATTRIBUTES = "net/minecraft/world/entity/ai/attributes/Attributes";
    private static final String MODIFIER = "net/minecraft/world/entity/ai/attributes/AttributeModifier";
    private static final String MODIFIER_OPERATION = MODIFIER + "$Operation";
    private static final String EQUIPMENT_SLOT = "net/minecraft/world/entity/EquipmentSlot";
    private static final String MULTIMAP = "com/google/common/collect/Multimap";
    private static final String IMMUTABLE_MULTIMAP = "com/google/common/collect/ImmutableMultimap";
    private static final String MULTIMAP_BUILDER = IMMUTABLE_MULTIMAP + "$Builder";

    private VerifiedLogicMappings(Map<String, ClassNode> sourceNodes, LegacyReferenceScanner.ScanResult scan, MappingInputs inputs, boolean lifecycle, boolean food) {
        this(sourceNodes, scan, inputs, lifecycle, food, false);
    }
    private VerifiedLogicMappings(Map<String, ClassNode> sourceNodes, LegacyReferenceScanner.ScanResult scan, MappingInputs inputs, boolean lifecycle, boolean food, boolean network) {
        this(sourceNodes, scan, inputs, lifecycle, food, network, false);
    }
    private VerifiedLogicMappings(Map<String, ClassNode> sourceNodes, LegacyReferenceScanner.ScanResult scan, MappingInputs inputs, boolean lifecycle, boolean food, boolean network, boolean events) {
        this(sourceNodes, scan, inputs, lifecycle, food, network, events, false);
    }
    private VerifiedLogicMappings(Map<String, ClassNode> sourceNodes, LegacyReferenceScanner.ScanResult scan, MappingInputs inputs, boolean lifecycle, boolean food, boolean network, boolean events, boolean clientConfig) {
        this(sourceNodes, scan, inputs, lifecycle, food, network, events, clientConfig, false);
    }
    private VerifiedLogicMappings(Map<String, ClassNode> sourceNodes, LegacyReferenceScanner.ScanResult scan, MappingInputs inputs, boolean lifecycle, boolean food, boolean network, boolean events, boolean clientConfig, boolean serverConfig) {
        this(sourceNodes, scan, inputs, lifecycle, food, network, events, clientConfig, serverConfig, false);
    }
    private VerifiedLogicMappings(Map<String, ClassNode> sourceNodes, LegacyReferenceScanner.ScanResult scan, MappingInputs inputs, boolean lifecycle, boolean food, boolean network, boolean events, boolean clientConfig, boolean serverConfig, boolean suite) {
        this(sourceNodes, scan, inputs, lifecycle, food, network, events, clientConfig, serverConfig, suite, false);
    }
    private VerifiedLogicMappings(Map<String, ClassNode> sourceNodes, LegacyReferenceScanner.ScanResult scan, MappingInputs inputs,
            boolean lifecycle, boolean food, boolean network, boolean events, boolean clientConfig, boolean serverConfig, boolean suite, boolean durable) {
        this(sourceNodes, scan, inputs, lifecycle, food, network, events, clientConfig, serverConfig, suite, durable, false);
    }
    private VerifiedLogicMappings(Map<String, ClassNode> sourceNodes, LegacyReferenceScanner.ScanResult scan, MappingInputs inputs,
            boolean lifecycle, boolean food, boolean network, boolean events, boolean clientConfig, boolean serverConfig,
            boolean suite, boolean durable, boolean plainBlock) {
        this(sourceNodes, scan, inputs, lifecycle, food, network, events, clientConfig, serverConfig,
                suite, durable, plainBlock, false);
    }
    private VerifiedLogicMappings(Map<String, ClassNode> sourceNodes, LegacyReferenceScanner.ScanResult scan, MappingInputs inputs,
            boolean lifecycle, boolean food, boolean network, boolean events, boolean clientConfig, boolean serverConfig,
            boolean suite, boolean durable, boolean plainBlock, boolean horizontalBlock) {
        this(sourceNodes, scan, inputs, lifecycle, food, network, events, clientConfig, serverConfig,
                suite, durable, plainBlock, horizontalBlock, false);
    }
    private VerifiedLogicMappings(Map<String, ClassNode> sourceNodes, LegacyReferenceScanner.ScanResult scan, MappingInputs inputs,
            boolean lifecycle, boolean food, boolean network, boolean events, boolean clientConfig, boolean serverConfig,
            boolean suite, boolean durable, boolean plainBlock, boolean horizontalBlock, boolean staticShapeBlock) {
        this(sourceNodes, scan, inputs, lifecycle, food, network, events, clientConfig, serverConfig,
                suite, durable, plainBlock, horizontalBlock, staticShapeBlock, false);
    }
    private VerifiedLogicMappings(Map<String, ClassNode> sourceNodes, LegacyReferenceScanner.ScanResult scan, MappingInputs inputs,
            boolean lifecycle, boolean food, boolean network, boolean events, boolean clientConfig, boolean serverConfig,
            boolean suite, boolean durable, boolean plainBlock, boolean horizontalBlock, boolean staticShapeBlock, boolean booleanToggleBlock) {
        this(sourceNodes,scan,inputs,lifecycle,food,network,events,clientConfig,serverConfig,suite,durable,plainBlock,horizontalBlock,staticShapeBlock,booleanToggleBlock,false);
    }
    private VerifiedLogicMappings(Map<String, ClassNode> sourceNodes, LegacyReferenceScanner.ScanResult scan, MappingInputs inputs,
            boolean lifecycle, boolean food, boolean network, boolean events, boolean clientConfig, boolean serverConfig,
            boolean suite, boolean durable, boolean plainBlock, boolean horizontalBlock, boolean staticShapeBlock, boolean booleanToggleBlock,
            boolean itemAttributes) {
        this(sourceNodes,scan,inputs,lifecycle,food,network,events,clientConfig,serverConfig,suite,durable,plainBlock,
                horizontalBlock,staticShapeBlock,booleanToggleBlock,itemAttributes,false);
    }
    private VerifiedLogicMappings(Map<String, ClassNode> sourceNodes, LegacyReferenceScanner.ScanResult scan, MappingInputs inputs,
            boolean lifecycle, boolean food, boolean network, boolean events, boolean clientConfig, boolean serverConfig,
            boolean suite, boolean durable, boolean plainBlock, boolean horizontalBlock, boolean staticShapeBlock, boolean booleanToggleBlock,
            boolean itemAttributes, boolean itemAttributeDurability) {
        this(sourceNodes,scan,inputs,lifecycle,food,network,events,clientConfig,serverConfig,suite,durable,plainBlock,
                horizontalBlock,staticShapeBlock,booleanToggleBlock,itemAttributes,itemAttributeDurability,false);
    }
    private VerifiedLogicMappings(Map<String, ClassNode> sourceNodes, LegacyReferenceScanner.ScanResult scan, MappingInputs inputs,
            boolean lifecycle, boolean food, boolean network, boolean events, boolean clientConfig, boolean serverConfig,
            boolean suite, boolean durable, boolean plainBlock, boolean horizontalBlock, boolean staticShapeBlock, boolean booleanToggleBlock,
            boolean itemAttributes, boolean itemAttributeDurability, boolean ownedStackDamage) {
        this(sourceNodes,scan,inputs,lifecycle,food,network,events,clientConfig,serverConfig,suite,durable,plainBlock,
                horizontalBlock,staticShapeBlock,booleanToggleBlock,itemAttributes,itemAttributeDurability,ownedStackDamage,false);
    }
    private VerifiedLogicMappings(Map<String, ClassNode> sourceNodes, LegacyReferenceScanner.ScanResult scan, MappingInputs inputs,
            boolean lifecycle, boolean food, boolean network, boolean events, boolean clientConfig, boolean serverConfig,
            boolean suite, boolean durable, boolean plainBlock, boolean horizontalBlock, boolean staticShapeBlock, boolean booleanToggleBlock,
            boolean itemAttributes, boolean itemAttributeDurability, boolean ownedStackDamage, boolean ownedStackRepairUse) {
        if(ownedStackDamage && ownedStackRepairUse) throw failure("OWNED_STACK_REPAIR_USE_COMPOSITION", "Distinct private graphs cannot be combined through flags");
        this.ownedStackRepairUseProof=ownedStackRepairUse?dev.legacybridge.converter.conversion.OwnedStackRepairUseContract.inspect(sourceNodes):null;
        this.ownedStackDamageProof=ownedStackRepairUseProof!=null?ownedStackRepairUseProof.stackProof()
                :ownedStackDamage?dev.legacybridge.converter.conversion.OwnedStackDamageContract.inspect(sourceNodes):null;
        this.itemAttributes=itemAttributes;
        if(itemAttributes) dev.legacybridge.converter.conversion.ItemAttributeContract.inspect(sourceNodes);
        this.itemAttributeDurability=itemAttributeDurability;
        // This combined graph has its own proof. Neither single-feature inspector is
        // allowed to admit it by silently projecting away the other callback/factory call.
        if(itemAttributeDurability) dev.legacybridge.converter.conversion.ItemAttributeDurabilityContract.inspect(sourceNodes);
        this.plainBlock=plainBlock;
        this.horizontalBlock=horizontalBlock;
        this.staticShapeBlock=staticShapeBlock;
        this.booleanToggleBlock=booleanToggleBlock;
        if (booleanToggleBlock) dev.legacybridge.converter.conversion.BooleanToggleBlockContract.inspect(sourceNodes);
        if (staticShapeBlock) dev.legacybridge.converter.conversion.StaticShapeBlockContract.inspect(sourceNodes);
        this.orientationTransforms = horizontalBlock
                && dev.legacybridge.converter.conversion.HorizontalBlockContract.inspect(sourceNodes).orientationTransforms();
        if (plainBlock && !horizontalBlock && !staticShapeBlock && !booleanToggleBlock) dev.legacybridge.converter.conversion.PlainBlockContract.inspect(sourceNodes);
        this.durable=durable;
        if(durable) dev.legacybridge.converter.conversion.DurableItemContract.inspect(sourceNodes);
        this.suiteProof = suite ? dev.legacybridge.converter.conversion.LifecycleSuiteContract.inspect(sourceNodes) : null;
        this.serverConfigProof = serverConfig ? dev.legacybridge.converter.conversion.ServerConfigContract.inspect(sourceNodes) : null;
        this.clientConfigProof = clientConfig ? dev.legacybridge.converter.conversion.ClientConfigContract.inspect(sourceNodes) : null;
        this.eventProof = suiteProof != null ? suiteProof.events() : events ? dev.legacybridge.converter.conversion.EventPriorityContract.inspect(sourceNodes) : null;
        this.lifecycle = lifecycle;
        this.food = food;
        this.network = network;
        this.own = Collections.unmodifiableMap(new TreeMap<>(Objects.requireNonNull(sourceNodes)));
        this.scan = Objects.requireNonNull(scan);
        this.inputs = Objects.requireNonNull(inputs);
        if (!inputs.complete()) throw failure("LOGIC_MAPPING_DATA_REQUIRED", "Explicit complete mapping/API data is required");
        if (own.isEmpty() || own.size() > LegacyReferenceScanner.MAX_CLASSES || !own.keySet().equals(scan.classes().keySet())) {
            throw failure("LOGIC_SOURCE_MISMATCH", "Source trees must match the complete owned scan");
        }
        for (var entry : own.entrySet()) {
            if (entry.getValue() == null || !entry.getKey().equals(entry.getValue().name)
                    || LegacyReferenceScanner.isProtectedDefinition(entry.getKey())
                    || !Arrays.equals(bytes(entry.getValue()), bytes(scan.classes().get(entry.getKey()).readNode()))) {
                throw failure("LOGIC_SOURCE_MISMATCH", "Mutable/protected/unscanned class: " + entry.getKey());
            }
        }
        sourceNamespace = new LogicApiNamespace(inputs.legacyOfficial(), inputs.legacySrg(), inputs.legacyApi(), true);
        targetNamespace = new LogicApiNamespace(inputs.targetOfficial(), inputs.targetSrg(), inputs.targetApi(), false);
        initializeReviewedTypes();
        Set<String> sourceRoots = new TreeSet<>(), targetRoots = new TreeSet<>();
        for (var reference : scan.references()) {
            if (classes.containsKey(reference.owner()) && !reference.owner().startsWith("java/") && !own.containsKey(reference.owner())) {
                sourceRoots.add(reference.owner()); targetRoots.add(classes.get(reference.owner()));
            }
        }
        if (lifecycle || plainBlock) { sourceRoots.add(ERASED_ENTRY); targetRoots.remove(ERASED_ENTRY); }
        if (plainBlock) {
            sourceRoots.add(sourceNamespace.productionClass(PlainBlockApiRecipe.MATERIAL));
            sourceRoots.add(sourceNamespace.productionClass(PlainBlockApiRecipe.PROPERTIES));
            for (String canonical : List.of(PlainBlockApiRecipe.PROPERTIES, PlainBlockApiRecipe.COLOR, PlainBlockApiRecipe.INSTRUMENT))
                targetRoots.add(targetNamespace.productionClass(canonical));
        }
        sourceApi = sourceNamespace.normalize(sourceRoots);
        targetApi = targetNamespace.normalize(targetRoots);
        if(itemAttributes || itemAttributeDurability) verifyItemAttributeTypes();
        if(ownedStackDamageProof!=null) verifyOwnedStackDamageTypesAndHooks();
        if(ownedStackRepairUseProof!=null) verifyOwnedStackRepairUseTypesAndMembers();
        plainBlockRecipe = plainBlock ? PlainBlockApiRecipe.resolve(sourceNamespace, targetNamespace, sourceApi, targetApi, staticShapeBlock) : null;
    }

    public static Result compile(Map<String, ClassNode> sourceNodes, LegacyReferenceScanner.ScanResult scan, MappingInputs inputs) {
        return compile(sourceNodes, scan, inputs, false);
    }
    public static Result compile(Map<String, ClassNode> sourceNodes, LegacyReferenceScanner.ScanResult scan, MappingInputs inputs, boolean lifecycle) {
        return new VerifiedLogicMappings(sourceNodes, scan, inputs, lifecycle, false).compile();
    }
    public static Result compileFood(Map<String, ClassNode> sourceNodes, LegacyReferenceScanner.ScanResult scan, MappingInputs inputs) {
        return new VerifiedLogicMappings(sourceNodes, scan, inputs, false, true).compile();
    }
    public static Result compileNetwork(Map<String, ClassNode> sourceNodes, LegacyReferenceScanner.ScanResult scan, MappingInputs inputs) {
        return new VerifiedLogicMappings(sourceNodes, scan, inputs, false, false, true).compile();
    }

    public static Result compileDurableItem(Map<String, ClassNode> nodes, LegacyReferenceScanner.ScanResult scan, MappingInputs inputs) {
        return new VerifiedLogicMappings(nodes, scan, inputs, false, false, false, false, false, false, false, true).compile();
    }
    public static Result compilePlainBlock(Map<String, ClassNode> nodes, LegacyReferenceScanner.ScanResult scan, MappingInputs inputs) {
        return new VerifiedLogicMappings(nodes, scan, inputs, false, false, false, false, false, false, false, false, true).compile();
    }
    /** Independent opt-in; the complete two-class state/placement graph must pass its finite proof. */
    public static Result compileHorizontalBlock(Map<String, ClassNode> nodes, LegacyReferenceScanner.ScanResult scan, MappingInputs inputs) {
        return new VerifiedLogicMappings(nodes, scan, inputs, false, false, false, false, false, false, false, false, true, true).compile();
    }
    /** Independent finite geometry gate: exactly the literal bottom half, never arbitrary Block.box. */
    public static Result compileStaticShapeBlock(Map<String, ClassNode> nodes, LegacyReferenceScanner.ScanResult scan, MappingInputs inputs) {
        return new VerifiedLogicMappings(nodes, scan, inputs, false, false, false, false, false, false, false, false, true, false, true).compile();
    }
    /** Independent logical-side/argument-provenance proof, not generic permission to mutate worlds. */
    public static Result compileBooleanToggleBlock(Map<String, ClassNode> nodes, LegacyReferenceScanner.ScanResult scan, MappingInputs inputs) {
        return new VerifiedLogicMappings(nodes, scan, inputs, false, false, false, false, false, false, false, false, true, false, false, true).compile();
    }
    /** Independent immutable two-modifier MAINHAND contract; not sword, durability or arbitrary Guava support. */
    public static Result compileItemAttributes(Map<String, ClassNode> nodes, LegacyReferenceScanner.ScanResult scan, MappingInputs inputs) {
        return new VerifiedLogicMappings(nodes,scan,inputs,false,false,false,false,false,false,false,false,false,false,false,false,true).compile();
    }
    /** Independent finite composition: immutable MAINHAND modifiers, capacity 128 and iron-ingredient repair.
     * This does not add attack/mining wear, tool or sword semantics, or widen either older compiler. */
    public static Result compileItemAttributeDurability(Map<String, ClassNode> nodes, LegacyReferenceScanner.ScanResult scan, MappingInputs inputs) {
        return new VerifiedLogicMappings(nodes,scan,inputs,false,false,false,false,false,false,false,false,false,false,false,false,false,true).compile();
    }
    /** One owned helper, not a callback or generic stack API: same-stack guarded damage decrement. */
    public static Result compileOwnedStackDamage(Map<String, ClassNode> nodes, LegacyReferenceScanner.ScanResult scan, MappingInputs inputs) {
        return new VerifiedLogicMappings(nodes,scan,inputs,false,false,false,false,false,false,false,false,false,false,false,false,false,false,true).compile();
    }
    /** Private six-method graph: native use observes the received hand once, predicts client SUCCESS,
     * and invokes the same-stack helper only on the logical server. Not arbitrary Item.use support. */
    public static Result compileOwnedStackRepairUse(Map<String, ClassNode> nodes, LegacyReferenceScanner.ScanResult scan, MappingInputs inputs) {
        return new VerifiedLogicMappings(nodes,scan,inputs,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,true).compile();
    }
    public static Result compileLifecycleSuite(Map<String, ClassNode> nodes, LegacyReferenceScanner.ScanResult scan, MappingInputs inputs) {
        return new VerifiedLogicMappings(nodes, scan, inputs, true, false, false, false, false, false, true).compile();
    }
    public static Result compileServerConfig(Map<String, ClassNode> nodes, LegacyReferenceScanner.ScanResult scan, MappingInputs inputs) {
        return new VerifiedLogicMappings(nodes, scan, inputs, true, false, false, false, false, true).compile();
    }

    public static Result compileClientConfig(Map<String, ClassNode> nodes, LegacyReferenceScanner.ScanResult scan, MappingInputs inputs) {
        return new VerifiedLogicMappings(nodes, scan, inputs, true, false, false, false, true).compile();
    }

    public static Result compileEvents(Map<String, ClassNode> nodes, LegacyReferenceScanner.ScanResult scan, MappingInputs inputs) {
        return new VerifiedLogicMappings(nodes, scan, inputs, true, false, false, true).compile();
    }

    private Result compile() {
        if (AdapterCatalog.find(AdapterCatalog.LOGIC).isEmpty()) throw failure("LOGIC_ADAPTER_MISSING", "Implemented descriptor-operation catalog entry is required");
        buildRecipes();
        for (ClassNode node : own.values()) inspectDynamicSites(node);
        Set<LegacySymbol> requested = new TreeSet<>();
        for (var ref : scan.references()) {
            OperationControl.checkpoint();
            if (plainBlockRecipe != null && plainBlockRecipe.consumesReference(ref, own)) continue;
            requireType(ref.owner());
            if (BOOTSTRAP_TYPES.contains(ref.owner())) requireBootstrapContext(ref);
            if (ref.kind() == LegacyReferenceScanner.ReferenceKind.ANNOTATION
                    && !ref.owner().equals(MOD) && !ref.owner().equals("java/lang/FunctionalInterface")
                    && !(lifecycle && Set.of(SUBSCRIBER, SUBSCRIBE).contains(ref.owner()))
                    && !(eventProof != null && ref.owner().equals(dev.legacybridge.converter.conversion.EventPriorityContract.CANCELABLE))) {
                throw failure("LOGIC_UNSUPPORTED_ANNOTATION", ref.owner());
            }
            requested.add(LegacySymbol.classSymbol(ref.owner()));
            if (ref.kind() == LegacyReferenceScanner.ReferenceKind.METHOD || ref.kind() == LegacyReferenceScanner.ReferenceKind.FIELD) {
                requested.add(symbol(ref.owner(), ref.name(), ref.descriptor(), ref.kind() == LegacyReferenceScanner.ReferenceKind.FIELD));
            }
        }
        generated = new MappingDatabaseGenerator(inputs).generate(requested);
        for (String owner : own.keySet()) add(LegacySymbol.classSymbol(owner), TargetSymbol.classSymbol(owner), "logic.owned-class-identity.v1", "OWNED", "NONE");
        for (LegacySymbol symbol : requested) {
            if (symbol.kind() == SymbolKind.CLASS) approveClass(symbol.owner());
            else approveExternal(symbol, null);
        }
        // Overrides have no call-site reference. Missing/private source declarations must not
        // demote these required callbacks to ordinary owned methods retaining obsolete SRG names.
        if (staticShapeBlock) for (Recipe recipe : recipes.values()) {
            if (Set.of("static-shape.native-outline-override.v1", "static-shape.native-collision-override.v1").contains(recipe.contract()))
                approveExternal(recipe.source(), null);
        }
        if (booleanToggleBlock) for (Recipe recipe : recipes.values()) {
            if (!Set.of("boolean-toggle.protected-definition-override.v1", "boolean-toggle.native-use-override.v1").contains(recipe.contract())) continue;
            approveExternal(recipe.source(), null);
            var from = externalDeclaration(sourceApi, sourceNamespace.productionClass(BLOCK), recipe.source().name(), recipe.source().descriptor(), false, new HashSet<>());
            var to = externalDeclaration(targetApi, targetNamespace.productionClass(BLOCK), recipe.target().name(), recipe.target().descriptor(), false, new HashSet<>());
            if (from == null || to == null || !from.owner().equals(recipe.source().owner()) || !to.owner().equals(recipe.target().owner()))
                throw failure("BOOLEAN_TOGGLE_OVERRIDE_OWNER_CHANGED", readable(recipe.source()));
        }
        if(itemAttributes || itemAttributeDurability) for(Recipe recipe:recipes.values())
            if(recipe.contract().startsWith("item-attributes.")
                    || itemAttributeDurability && recipe.contract().startsWith("item-attribute-durability."))
                approveExternal(recipe.source(),null);
        if(ownedStackDamageProof!=null) for(Recipe recipe:recipes.values())
            if(recipe.contract().startsWith("owned-stack-damage."))approveExternal(recipe.source(),null);
        // The native override has no external call site; missing data cannot demote it to an
        // unchanged owned SRG method. The inherited alias is also checked independently.
        if(ownedStackRepairUseProof!=null) for(Recipe recipe:recipes.values())
            if(recipe.contract().startsWith("owned-stack-repair-use."))approveExternal(recipe.source(),null);
        for (ClassNode node : own.values()) inspectDeclarations(node);
        for (ClassNode node : own.values()) inspectInstructions(node);
        var database = MappingDatabase.builder();
        approved.values().forEach(database::add);
        var rules = new ArrayList<>(review.values());
        if (plainBlockRecipe != null) rules.add(plainBlockRecipe.review());
        return new Result(database.build(), bindings, sourceApi, targetApi, rules, plainBlockRecipe);
    }

    private void initializeReviewedTypes() {
        classes.put(sourceNamespace.productionClass(ITEM), targetNamespace.productionClass(ITEM));
        classes.put(sourceNamespace.productionClass(STACK), targetNamespace.productionClass(STACK));
        classes.put(sourceNamespace.productionClass(PROPERTIES), targetNamespace.productionClass(PROPERTIES));
        for (String owner : List.of(DEFERRED, REGISTRIES, REGISTRY, BUS, CONTEXT, MOD)) classes.put(owner, owner);
        classes.put(OLD_OBJECT, NEW_OBJECT);
        for (String owner : JDK_TYPES) classes.put(owner, owner);
        for (String owner : BOOTSTRAP_TYPES) classes.put(owner, owner);
        own.keySet().forEach(owner -> classes.put(owner, owner));
        if (plainBlock) {
            for (String canonical : List.of(PlainBlockApiRecipe.BLOCK, PlainBlockApiRecipe.BLOCK_ITEM,
                    PlainBlockApiRecipe.BEHAVIOUR, PlainBlockApiRecipe.PROPERTIES))
                classes.put(sourceNamespace.productionClass(canonical), targetNamespace.productionClass(canonical));
            classes.put(ERASED_ENTRY, ERASED_ENTRY); // Source-only erased return; never a target class rule.
        }
        if (horizontalBlock) {
            for (String canonical : List.of(BLOCK_STATE, STATE, DEFINITION, DEFINITION + "$Builder",
                    STATE_PROPERTIES, PROPERTY, DIRECTION_PROPERTY, DIRECTION, PLACEMENT, USE_CONTEXT))
                classes.put(sourceNamespace.productionClass(canonical), targetNamespace.productionClass(canonical));
            classes.put("java/lang/Comparable", "java/lang/Comparable");
            classes.put("java/lang/Enum", "java/lang/Enum");
        }
        if (orientationTransforms) for (String canonical : List.of(ROTATION, MIRROR))
            classes.put(sourceNamespace.productionClass(canonical), targetNamespace.productionClass(canonical));
        if (staticShapeBlock) for (String canonical : List.of(BLOCK_STATE, VOXEL_SHAPE, COLLISION_CONTEXT, BLOCK_GETTER, BLOCK_POS))
            classes.put(sourceNamespace.productionClass(canonical), targetNamespace.productionClass(canonical));
        if (booleanToggleBlock) {
            for (String canonical : List.of(BLOCK_STATE, STATE, DEFINITION, DEFINITION+"$Builder", PROPERTY,
                    BOOLEAN_PROPERTY, LEVEL, BLOCK_POS, PLAYER, HAND, BLOCK_HIT, INTERACTION_RESULT))
                classes.put(sourceNamespace.productionClass(canonical), targetNamespace.productionClass(canonical));
            for (String type : List.of("java/lang/Comparable", "java/lang/Enum", "java/lang/Boolean")) classes.put(type,type);
        }
        if(ownedStackRepairUseProof!=null) for(String canonical:List.of(LEVEL,PLAYER,LIVING,HAND,RESULT_HOLDER))
            classes.put(sourceNamespace.productionClass(canonical),targetNamespace.productionClass(canonical));
        if(itemAttributes || itemAttributeDurability) {
            for(String canonical:List.of(ATTRIBUTE,ATTRIBUTES,MODIFIER,MODIFIER_OPERATION,EQUIPMENT_SLOT))
                classes.put(sourceNamespace.productionClass(canonical),targetNamespace.productionClass(canonical));
            for(String type:List.of(MULTIMAP,IMMUTABLE_MULTIMAP,MULTIMAP_BUILDER,"java/util/UUID","java/lang/Enum"))classes.put(type,type);
        }
        if (network) {
            for (String canonical : List.of("net/minecraft/resources/ResourceLocation", "net/minecraft/network/FriendlyByteBuf",
                    "net/minecraft/network/Connection", "net/minecraft/server/level/ServerPlayer"))
                classes.put(sourceNamespace.productionClass(canonical), targetNamespace.productionClass(canonical));
            for (String relative : List.of("NetworkRegistry", "NetworkDirection", "NetworkEvent$Context",
                    "NetworkEvent", "simple/SimpleChannel", "simple/IndexedMessageCodec", "simple/IndexedMessageCodec$MessageHandler",
                    "PacketDistributor", "PacketDistributor$PacketTarget"))
                classes.put("net/minecraftforge/fml/network/" + relative, "net/minecraftforge/network/" + relative);
            for (String identity : List.of("java/lang/Class", "java/lang/Enum", "java/lang/Integer", "java/lang/System",
                    "java/io/PrintStream", "java/lang/Thread", "java/lang/Runnable", "java/util/Optional",
                    "java/util/function/Predicate", "java/util/function/BiConsumer", "java/util/function/Function",
                    "java/util/concurrent/CompletableFuture", "io/netty/buffer/ByteBuf")) classes.put(identity, identity);
        }
        if (suiteProof != null) {
            classes.putAll(dev.legacybridge.converter.conversion.LifecycleSuiteContract.EVENT_TYPES);
            classes.put("net/minecraftforge/fml/config/ModConfig","net/minecraftforge/fml/config/ModConfig");
            classes.put("com/mojang/brigadier/arguments/BoolArgumentType","com/mojang/brigadier/arguments/BoolArgumentType");
        }
        if (serverConfigProof != null) {
            classes.putAll(dev.legacybridge.converter.conversion.ServerConfigContract.EVENT_TYPES);
            classes.put("net/minecraftforge/common/MinecraftForge", "net/minecraftforge/common/MinecraftForge");
        }
        if (clientConfigProof != null) for (String name : List.of(
                dev.legacybridge.converter.conversion.ClientConfigContract.SETUP,
                dev.legacybridge.converter.conversion.ClientConfigContract.ENV,
                dev.legacybridge.converter.conversion.ClientConfigContract.DIST)) classes.put(name, name);
        if (eventProof != null) for (String name : List.of(
                "net/minecraftforge/common/MinecraftForge", "net/minecraftforge/eventbus/api/Event",
                "net/minecraftforge/eventbus/api/Cancelable")) classes.put(name, name);
        if (durable || itemAttributeDurability) classes.put(sourceNamespace.productionClass("net/minecraft/world/item/Items"),targetNamespace.productionClass("net/minecraft/world/item/Items"));
        if (food) for (String canonical : List.of(FOOD, FOOD + "$Builder"))
            classes.put(sourceNamespace.productionClass(canonical), targetNamespace.productionClass(canonical));
        if (lifecycle) {
            for (String owner : List.of(ERASED_ENTRY, SETUP, "net/minecraftforge/fml/event/lifecycle/ParallelDispatchEvent",
                    SUBSCRIBER, SUBSCRIBER + "$Bus", SUBSCRIBE, "net/minecraftforge/eventbus/api/EventPriority",
                    "net/minecraftforge/event/RegisterCommandsEvent", "net/minecraftforge/fml/ModLoadingContext",
                    "net/minecraftforge/fml/config/ModConfig", "net/minecraftforge/fml/config/ModConfig$Type", SPEC, SPEC + "$Builder", SPEC + "$BooleanValue",
                    SPEC + "$IntValue", SPEC + "$ConfigValue", "java/lang/Class", "java/lang/Enum", "java/lang/Number",
                    "java/lang/Boolean", "java/lang/Integer", "java/lang/Runnable", "java/util/function/Consumer",
                    "java/util/function/Predicate", "java/util/concurrent/CompletableFuture",
                    "com/mojang/brigadier/Command", "com/mojang/brigadier/CommandDispatcher",
                    "com/mojang/brigadier/context/CommandContext", "com/mojang/brigadier/arguments/ArgumentType",
                    "com/mojang/brigadier/arguments/IntegerArgumentType", "com/mojang/brigadier/builder/ArgumentBuilder",
                    "com/mojang/brigadier/builder/LiteralArgumentBuilder", "com/mojang/brigadier/builder/RequiredArgumentBuilder",
                    "com/mojang/brigadier/tree/LiteralCommandNode")) classes.put(owner, owner);
            for (String canonical : List.of("net/minecraft/commands/Commands", "net/minecraft/commands/CommandSourceStack"))
                classes.put(sourceNamespace.productionClass(canonical), targetNamespace.productionClass(canonical));
        }
    }

    private void buildRecipes() {
        if (network) { networkRecipes(); return; }
        minecraft(ITEM, "<init>", "(L" + PROPERTIES + ";)V", "logic.item-constructor.v1", Opcodes.INVOKESPECIAL);
        minecraft(PROPERTIES, "<init>", "()V", "logic.item-properties-constructor.v1", Opcodes.INVOKESPECIAL);
        minecraft(PROPERTIES, "stacksTo", "(I)L" + PROPERTIES + ";", "logic.item-stack-size-property.v1", Opcodes.INVOKEVIRTUAL);
        minecraft(ITEM, "isFoil", "(L" + STACK + ";)Z", "logic.item-foil-override.v1", Opcodes.INVOKEVIRTUAL);
        minecraft(STACK, "getCount", "()I", "logic.item-stack-count.v1", Opcodes.INVOKEVIRTUAL);
        nativeRecipe(DEFERRED, "create", "(L" + REGISTRY + ";Ljava/lang/String;)L" + DEFERRED + ";", false, "logic.deferred-items-create.v1", Opcodes.INVOKESTATIC);
        nativeRecipe(DEFERRED, "register", "(Ljava/lang/String;Ljava/util/function/Supplier;)L" + OLD_OBJECT + ";", false, "logic.deferred-item-supplier.v1", Opcodes.INVOKEVIRTUAL);
        nativeRecipe(DEFERRED, "register", "(L" + BUS + ";)V", false, "logic.deferred-mod-bus-attachment.v1", Opcodes.INVOKEVIRTUAL);
        nativeRecipe(REGISTRIES, "ITEMS", "L" + REGISTRY + ";", true, "logic.native-item-registry.v1", Opcodes.GETSTATIC);
        nativeRecipe(CONTEXT, "get", "()L" + CONTEXT + ";", false, "logic.active-mod-loading-context.v1", Opcodes.INVOKESTATIC);
        nativeRecipe(CONTEXT, "getModEventBus", "()L" + BUS + ";", false, "logic.active-mod-event-bus.v1", Opcodes.INVOKEVIRTUAL);
        nativeRecipe("java/lang/Object", "<init>", "()V", false, "logic.jdk-object-constructor.v1", Opcodes.INVOKESPECIAL);
        nativeRecipe("java/lang/IllegalArgumentException", "<init>", "()V", false, "logic.jdk-argument-exception.v1", Opcodes.INVOKESPECIAL);
        nativeRecipe("java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V", false, "logic.jdk-argument-exception.v1", Opcodes.INVOKESPECIAL);
        nativeRecipe(SUPPLIER, "get", "()Ljava/lang/Object;", false, "logic.jdk-supplier-contract.v1", Opcodes.INVOKEINTERFACE);
        nativeRecipe(OPERATOR, "applyAsInt", "(I)I", false, "logic.jdk-int-unary-contract.v1", Opcodes.INVOKEINTERFACE);
        nativeRecipe(METAFACTORY, "metafactory", METAFACTORY_DESC, false, "logic.owned-lambda-metafactory.v1", Opcodes.INVOKESTATIC);
        if (plainBlock) {
            minecraft(PlainBlockApiRecipe.BLOCK, "<init>", "(L" + PlainBlockApiRecipe.PROPERTIES + ";)V",
                    booleanToggleBlock?"boolean-toggle.native-block-super-constructor.v1":staticShapeBlock?"static-shape.native-block-super-constructor.v1":"plain-block.native-cube-constructor.v1", Opcodes.INVOKESPECIAL);
            minecraft(PlainBlockApiRecipe.BLOCK_ITEM, "<init>", "(L" + PlainBlockApiRecipe.BLOCK + ";L" + PROPERTIES + ";)V",
                    "plain-block.native-block-item-constructor.v1", Opcodes.INVOKESPECIAL);
            minecraft(PlainBlockApiRecipe.PROPERTIES, "strength", "(FF)L" + PlainBlockApiRecipe.PROPERTIES + ";",
                    "plain-block.finite-nonnegative-strength.v1", Opcodes.INVOKEVIRTUAL);
            nativeRecipe(REGISTRIES, "BLOCKS", "L" + REGISTRY + ";", true,
                    "plain-block.native-block-registry.v1", Opcodes.GETSTATIC);
            nativeRecipe(OLD_OBJECT, "get", "()L" + ERASED_ENTRY + ";", false,
                    "plain-block.deferred-block-item-late-get.v1", Opcodes.INVOKEVIRTUAL);
        }
        if (horizontalBlock) horizontalBlockRecipes();
        if (staticShapeBlock) staticShapeBlockRecipes();
        if (booleanToggleBlock) booleanToggleBlockRecipes();
        if (itemAttributes || itemAttributeDurability) itemAttributeRecipes();
        if (itemAttributeDurability) itemAttributeDurabilityRecipes();
        if (ownedStackDamageProof!=null) ownedStackDamageRecipes();
        if (ownedStackRepairUseProof!=null) ownedStackRepairUseRecipes();
        if (lifecycle) lifecycleRecipes();
        if (serverConfigProof != null) {
            String type="net/minecraftforge/fml/config/ModConfig$Type";
            nativeRecipe(type, "SERVER", "L"+type+";", true, "lifecycle.server-config-scope.v1", Opcodes.GETSTATIC);
            nativeRecipe("net/minecraftforge/common/MinecraftForge", "EVENT_BUS", "L"+BUS+";", true,
                    "lifecycle.server-config-forge-bus.v1", Opcodes.GETSTATIC);
        }
        if (clientConfigProof != null) {
            String dist=dev.legacybridge.converter.conversion.ClientConfigContract.DIST;
            nativeRecipe(dev.legacybridge.converter.conversion.ClientConfigContract.ENV, "dist", "L"+dist+";", true,
                    "lifecycle.client-physical-guard.v1", Opcodes.GETSTATIC);
            nativeRecipe(dist, "CLIENT", "L"+dist+";", true, "lifecycle.client-dist-constant.v1", Opcodes.GETSTATIC);
            String type="net/minecraftforge/fml/config/ModConfig$Type";
            nativeRecipe(type, "CLIENT", "L"+type+";", true, "lifecycle.client-config-scope.v1", Opcodes.GETSTATIC);
            nativeRecipe(dev.legacybridge.converter.conversion.ClientConfigContract.SETUP, "enqueueWork",
                    "(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;", false,
                    "lifecycle.client-loaded-config-queued-read.v1", Opcodes.INVOKEVIRTUAL);
        }
        if (eventProof != null) {
            String event = "net/minecraftforge/eventbus/api/Event", priority = "net/minecraftforge/eventbus/api/EventPriority";
            nativeRecipe("net/minecraftforge/common/MinecraftForge", "EVENT_BUS", "L" + BUS + ";", true,
                    "events.owned-forge-bus.v1", Opcodes.GETSTATIC);
            nativeRecipe(event, "<init>", "()V", false, "events.pure-owned-cancelable-constructor.v1", Opcodes.INVOKESPECIAL);
            nativeRecipe(event, "setCanceled", "(Z)V", false, "events.high-priority-owned-cancellation.v1", Opcodes.INVOKEVIRTUAL);
            nativeRecipe(BUS, "post", "(L" + event + ";)Z", false, "events.fresh-owned-synchronous-post.v1", Opcodes.INVOKEINTERFACE);
            for (String name : List.of("HIGH", "LOW")) nativeRecipe(priority, name, "L" + priority + ";", true,
                    "events.distinct-legacy-priorities.v1", Opcodes.GETSTATIC);
            nativeRecipe(BUS, "addListener", "(L" + priority + ";ZLjava/lang/Class;Ljava/util/function/Consumer;)V", false,
                    "events.exact-bus-type-priority-cancellation.v1", Opcodes.INVOKEINTERFACE);
        }
        if (durable) {
            minecraft(PROPERTIES,"durability","(I)L"+PROPERTIES+";","durable.native-positive-durability-stack-one.v1",Opcodes.INVOKEVIRTUAL);
            minecraft(ITEM,"isValidRepairItem","(L"+STACK+";L"+STACK+";)Z","durable.iron-ingredient-virtual-override.v1",Opcodes.INVOKEVIRTUAL);
            minecraft(STACK,"getItem","()L"+ITEM+";","durable.second-argument-item-identity.v1",Opcodes.INVOKEVIRTUAL);
            String owner="net/minecraft/world/item/Items", desc="L"+ITEM+";";
            var from=LegacySymbol.field(sourceNamespace.productionClass(owner),sourceNamespace.productionField(owner,"IRON_INGOT",desc),sourceNamespace.productionDescriptor(desc));
            var to=TargetSymbol.field(targetNamespace.productionClass(owner),targetNamespace.productionField(owner,"IRON_INGOT",desc),targetNamespace.productionDescriptor(desc));
            recipes.put(from,new Recipe(from,to,"durable.native-iron-ingredient.v1",Opcodes.GETSTATIC));
        }
        if (food) {
            minecraft(FOOD + "$Builder", "<init>", "()V", "food.literal-builder.v1", Opcodes.INVOKESPECIAL);
            minecraft(FOOD + "$Builder", "nutrition", "(I)L" + FOOD + "$Builder;", "food.literal-nutrition.v1", Opcodes.INVOKEVIRTUAL);
            minecraft(FOOD + "$Builder", "saturationMod", "(F)L" + FOOD + "$Builder;", "food.literal-saturation.v1", Opcodes.INVOKEVIRTUAL);
            minecraft(FOOD + "$Builder", "build", "()L" + FOOD + ";", "food.build-with-default-flags-and-empty-effects.v1", Opcodes.INVOKEVIRTUAL);
            minecraft(PROPERTIES, "food", "(L" + FOOD + ";)L" + PROPERTIES + ";", "food.native-property-no-overrides-gameevent-migration.v1", Opcodes.INVOKEVIRTUAL);
        }
    }

    /** Pinned source/target contracts in docs/NETWORK_API_BASELINES.md. No messageBuilder/main-thread substitution. */
    private void networkRecipes() {
        String old = "net/minecraftforge/fml/network/";
        String channel = old + "simple/SimpleChannel", context = old + "NetworkEvent$Context";
        String distributor = old + "PacketDistributor", direction = old + "NetworkDirection";
        String location = sourceNamespace.productionClass("net/minecraft/resources/ResourceLocation");
        String buffer = sourceNamespace.productionClass("net/minecraft/network/FriendlyByteBuf");
        String connection = sourceNamespace.productionClass("net/minecraft/network/Connection");
        minecraft("net/minecraft/resources/ResourceLocation", "<init>", "(Ljava/lang/String;Ljava/lang/String;)V", "network.literal-channel-id.v1", Opcodes.INVOKESPECIAL);
        minecraft("net/minecraft/network/Connection", "isConnected", "()Z", "network.connection-liveness.v1", Opcodes.INVOKEVIRTUAL);
        nativeRecipe(old + "NetworkRegistry", "newSimpleChannel", "(L" + location + ";Ljava/util/function/Supplier;Ljava/util/function/Predicate;Ljava/util/function/Predicate;)L" + channel + ";",
                false, "network.exact-protocol-channel.v1", Opcodes.INVOKESTATIC);
        nativeRecipe(channel, "registerMessage", "(ILjava/lang/Class;Ljava/util/function/BiConsumer;Ljava/util/function/Function;Ljava/util/function/BiConsumer;Ljava/util/Optional;)L" + old + "simple/IndexedMessageCodec$MessageHandler;",
                false, "network.unique-byte-id-explicit-direction.v1", Opcodes.INVOKEVIRTUAL);
        nativeRecipe(channel, "send", "(L" + distributor + "$PacketTarget;Ljava/lang/Object;)V", false, "network.one-selected-player.v1", Opcodes.INVOKEVIRTUAL);
        nativeRecipe(distributor, "PLAYER", "L" + distributor + ";", true, "network.player-distributor.v1", Opcodes.GETSTATIC);
        nativeRecipe(distributor, "with", "(Ljava/util/function/Supplier;)L" + distributor + "$PacketTarget;", false, "network.player-supplier.v1", Opcodes.INVOKEVIRTUAL);
        nativeRecipe(direction, "PLAY_TO_CLIENT", "L" + direction + ";", true, "network.explicit-s2c-direction.v1", Opcodes.GETSTATIC);
        nativeRecipe(context, "enqueueWork", "(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;", false, "network.retained-game-thread-enqueue.v1", Opcodes.INVOKEVIRTUAL);
        nativeRecipe(context, "setPacketHandled", "(Z)V", false, "network.explicit-handled-state.v1", Opcodes.INVOKEVIRTUAL);
        nativeRecipe(context, "getNetworkManager", "()L" + connection + ";", false, "network.context-connection-identity.v1", Opcodes.INVOKEVIRTUAL);
        nativeRecipe(buffer, "readableBytes", "()I", false, "network.exact-buffer-size.v1", Opcodes.INVOKEVIRTUAL);
        nativeRecipe(buffer, "readInt", "()I", false, "network.fixed-big-endian-int.v1", Opcodes.INVOKEVIRTUAL);
        nativeRecipe(buffer, "writeInt", "(I)Lio/netty/buffer/ByteBuf;", false, "network.fixed-big-endian-int.v1", Opcodes.INVOKEVIRTUAL);
        nativeRecipe("java/lang/Object", "<init>", "()V", false, "network.object-constructor.v1", Opcodes.INVOKESPECIAL);
        nativeRecipe("java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V", false, "network.send-preconditions.v1", Opcodes.INVOKESPECIAL);
        nativeRecipe("java/lang/String", "equals", "(Ljava/lang/Object;)Z", false, "network.exact-protocol-equality.v1", Opcodes.INVOKEVIRTUAL);
        nativeRecipe("java/util/Optional", "of", "(Ljava/lang/Object;)Ljava/util/Optional;", false, "network.nonempty-direction.v1", Opcodes.INVOKESTATIC);
        nativeRecipe(SUPPLIER, "get", "()Ljava/lang/Object;", false, "network.context-supplier.v1", Opcodes.INVOKEINTERFACE);
        nativeRecipe("java/lang/System", "err", "Ljava/io/PrintStream;", true, "network.visible-rejection-stream.v1", Opcodes.GETSTATIC);
        nativeRecipe("java/io/PrintStream", "println", "(Ljava/lang/String;)V", false, "network.bounded-constant-diagnostic.v1", Opcodes.INVOKEVIRTUAL);
        nativeRecipe("java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false, "network.observable-handler-thread.v1", Opcodes.INVOKESTATIC);
        nativeRecipe("java/lang/Thread", "getId", "()J", false, "network.observable-handler-thread.v1", Opcodes.INVOKEVIRTUAL);
        nativeRecipe(METAFACTORY, "metafactory", METAFACTORY_DESC, false, "network.reviewed-owned-callback.v1", Opcodes.INVOKESTATIC);
    }

    private void lifecycleRecipes() {
        if (suiteProof != null) {
            String config = "net/minecraftforge/fml/config/ModConfig";
            for (String phase : List.of("$Loading", "$Reloading"))
                nativeRecipe(config + phase, "getConfig", "()L" + config + ";", false, "lifecycle.config-event-owned-filter.v1", Opcodes.INVOKEVIRTUAL);
            nativeRecipe(config, "getSpec", "()L" + SPEC + ";", false, "lifecycle.config-spec-identity.v1", Opcodes.INVOKEVIRTUAL);
            nativeRecipe(config, "getType", "()L" + config + "$Type;", false, "lifecycle.config-common-filter.v1", Opcodes.INVOKEVIRTUAL);
            String bool = "com/mojang/brigadier/arguments/BoolArgumentType";
            nativeRecipe(bool, "bool", "()L" + bool + ";", false, "lifecycle.native-boolean-argument.v1", Opcodes.INVOKESTATIC);
            nativeRecipe(bool, "getBool", "(Lcom/mojang/brigadier/context/CommandContext;Ljava/lang/String;)Z", false, "lifecycle.parsed-boolean-value.v1", Opcodes.INVOKESTATIC);
        }
        nativeRecipe(OLD_OBJECT, "get", "()L" + ERASED_ENTRY + ";", false, "lifecycle.late-registry-erasure.v1", Opcodes.INVOKEVIRTUAL);
        nativeRecipe(BUS, "addListener", "(Lnet/minecraftforge/eventbus/api/EventPriority;ZLjava/lang/Class;Ljava/util/function/Consumer;)V", false,
                "lifecycle.explicit-common-setup-listener.v1", Opcodes.INVOKEINTERFACE);
        nativeRecipe(BUS, "addListener", "(Ljava/util/function/Consumer;)V", false,
                "lifecycle.native-inferred-common-setup-listener.v1", Opcodes.INVOKEINTERFACE);
        nativeRecipe("net/minecraftforge/eventbus/api/EventPriority", "NORMAL", "Lnet/minecraftforge/eventbus/api/EventPriority;", true, "lifecycle.normal-priority.v1", Opcodes.GETSTATIC);
        nativeRecipe(SUBSCRIBER + "$Bus", "FORGE", "L" + SUBSCRIBER + "$Bus;", true, "lifecycle.forge-command-bus.v1", Opcodes.GETSTATIC);
        nativeRecipe(SETUP, "enqueueWork", "(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;", false, "lifecycle.queued-owned-work.v1", Opcodes.INVOKEVIRTUAL);
        nativeRecipe("net/minecraftforge/fml/ModLoadingContext", "get", "()Lnet/minecraftforge/fml/ModLoadingContext;", false, "lifecycle.mod-context.v1", Opcodes.INVOKESTATIC);
        nativeRecipe("net/minecraftforge/fml/ModLoadingContext", "registerConfig", "(Lnet/minecraftforge/fml/config/ModConfig$Type;L" + SPEC + ";)V", false, serverConfigProof != null ? "lifecycle.server-config-registration.v1" : clientConfigProof != null ? "lifecycle.client-config-registration.v1" : "lifecycle.common-config-registration.v1", Opcodes.INVOKEVIRTUAL);
        nativeRecipe("net/minecraftforge/fml/config/ModConfig$Type", "COMMON", "Lnet/minecraftforge/fml/config/ModConfig$Type;", true, "lifecycle.common-config-scope.v1", Opcodes.GETSTATIC);
        nativeRecipe(SPEC + "$Builder", "<init>", "()V", false, "lifecycle.config-builder.v1", Opcodes.INVOKESPECIAL);
        nativeRecipe(SPEC + "$Builder", "define", "(Ljava/lang/String;Z)L" + SPEC + "$BooleanValue;", false, "lifecycle.config-boolean-default.v1", Opcodes.INVOKEVIRTUAL);
        nativeRecipe(SPEC + "$Builder", "defineInRange", "(Ljava/lang/String;III)L" + SPEC + "$IntValue;", false, "lifecycle.config-integer-range.v1", Opcodes.INVOKEVIRTUAL);
        nativeRecipe(SPEC + "$Builder", "build", "()L" + SPEC + ";", false, "lifecycle.config-build.v1", Opcodes.INVOKEVIRTUAL);
        for (String value : List.of("$BooleanValue", "$IntValue")) nativeRecipe(SPEC + value, "get", "()Ljava/lang/Object;", false, "lifecycle.loaded-config-read.v1", Opcodes.INVOKEVIRTUAL);
        nativeRecipe("java/lang/Boolean", "booleanValue", "()Z", false, "lifecycle.boolean-unbox.v1", Opcodes.INVOKEVIRTUAL);
        nativeRecipe("java/lang/Integer", "intValue", "()I", false, "lifecycle.integer-unbox.v1", Opcodes.INVOKEVIRTUAL);
        String b = "com/mojang/brigadier/";
        nativeRecipe("net/minecraftforge/event/RegisterCommandsEvent", "getDispatcher", "()L" + b + "CommandDispatcher;", false, "lifecycle.server-command-dispatcher.v1", Opcodes.INVOKEVIRTUAL);
        nativeRecipe(b + "CommandDispatcher", "register", "(L" + b + "builder/LiteralArgumentBuilder;)L" + b + "tree/LiteralCommandNode;", false, "lifecycle.register-command-tree.v1", Opcodes.INVOKEVIRTUAL);
        minecraft("net/minecraft/commands/Commands", "literal", "(Ljava/lang/String;)L" + b + "builder/LiteralArgumentBuilder;", "lifecycle.literal-command.v1", Opcodes.INVOKESTATIC);
        minecraft("net/minecraft/commands/Commands", "argument", "(Ljava/lang/String;L" + b + "arguments/ArgumentType;)L" + b + "builder/RequiredArgumentBuilder;", "lifecycle.command-argument.v1", Opcodes.INVOKESTATIC);
        minecraft("net/minecraft/commands/CommandSourceStack", "hasPermission", "(I)Z", "lifecycle.command-permission-level.v1", Opcodes.INVOKEVIRTUAL);
        nativeRecipe(b + "arguments/IntegerArgumentType", "integer", "(II)L" + b + "arguments/IntegerArgumentType;", false, "lifecycle.bounded-integer-argument.v1", Opcodes.INVOKESTATIC);
        nativeRecipe(b + "arguments/IntegerArgumentType", "getInteger", "(L" + b + "context/CommandContext;Ljava/lang/String;)I", false, "lifecycle.parsed-integer-value.v1", Opcodes.INVOKESTATIC);
        nativeRecipe(b + "builder/LiteralArgumentBuilder", "requires", "(Ljava/util/function/Predicate;)L" + b + "builder/ArgumentBuilder;", false, "lifecycle.command-permission-predicate.v1", Opcodes.INVOKEVIRTUAL);
        nativeRecipe(b + "builder/LiteralArgumentBuilder", "then", "(L" + b + "builder/ArgumentBuilder;)L" + b + "builder/ArgumentBuilder;", false, "lifecycle.command-child.v1", Opcodes.INVOKEVIRTUAL);
        nativeRecipe(b + "builder/RequiredArgumentBuilder", "executes", "(L" + b + "Command;)L" + b + "builder/ArgumentBuilder;", false, "lifecycle.command-handler.v1", Opcodes.INVOKEVIRTUAL);
    }

    /**
     * Only the independently proved attribute (or explicit attribute/durability composition)
     * graph's two fixed ADDITION entries, inherited native UUID identities,
     * exact doubles and MAINHAND branch. Guava's unchanged package is not an exemption: only
     * these four public erased calls exist in this recipe, and both selected inventories must
     * supply their real declarations and the reviewed 21.0/31.1-jre provenance.
     */
    private void itemAttributeRecipes() {
        minecraft(ITEM,"<init>","(L"+PROPERTIES+";)V","item-attributes.native-item-constructor.v1",Opcodes.INVOKESPECIAL);
        minecraft(PROPERTIES,"<init>","()V","item-attributes.fresh-properties-constructor.v1",Opcodes.INVOKESPECIAL);
        minecraft(ITEM,"getDefaultAttributeModifiers","(L"+EQUIPMENT_SLOT+";)L"+MULTIMAP+";",
                "item-attributes.mainhand-override.v1",Opcodes.INVOKEVIRTUAL);
        for(String field:List.of("BASE_ATTACK_DAMAGE_UUID","BASE_ATTACK_SPEED_UUID"))
            minecraftField(ITEM,field,"Ljava/util/UUID;","item-attributes.inherited-native-uuid.v1",Opcodes.GETSTATIC);
        for(String field:List.of("ATTACK_DAMAGE","ATTACK_SPEED"))
            minecraftField(ATTRIBUTES,field,"L"+ATTRIBUTE+";","item-attributes.native-attribute-identity.v1",Opcodes.GETSTATIC);
        minecraftField(MODIFIER_OPERATION,"ADDITION","L"+MODIFIER_OPERATION+";","item-attributes.addition-operation.v1",Opcodes.GETSTATIC);
        minecraftField(EQUIPMENT_SLOT,"MAINHAND","L"+EQUIPMENT_SLOT+";","item-attributes.mainhand-singleton.v1",Opcodes.GETSTATIC);
        minecraft(MODIFIER,"<init>","(Ljava/util/UUID;Ljava/lang/String;DL"+MODIFIER_OPERATION+";)V",
                "item-attributes.fixed-native-modifier-constructor.v1",Opcodes.INVOKESPECIAL);
        nativeRecipe(IMMUTABLE_MULTIMAP,"builder","()L"+MULTIMAP_BUILDER+";",false,"item-attributes.immutable-builder.v1",Opcodes.INVOKESTATIC);
        nativeRecipe(MULTIMAP_BUILDER,"put","(Ljava/lang/Object;Ljava/lang/Object;)L"+MULTIMAP_BUILDER+";",false,"item-attributes.two-immutable-entries.v1",Opcodes.INVOKEVIRTUAL);
        nativeRecipe(MULTIMAP_BUILDER,"build","()L"+IMMUTABLE_MULTIMAP+";",false,"item-attributes.immutable-snapshot.v1",Opcodes.INVOKEVIRTUAL);
        nativeRecipe(IMMUTABLE_MULTIMAP,"of","()L"+IMMUTABLE_MULTIMAP+";",false,"item-attributes.empty-other-slots.v1",Opcodes.INVOKESTATIC);
    }

    /** Capacity and repair eligibility only. The literal 128, fresh Properties, second-argument
     * identity and both callbacks are independently checked by ItemAttributeDurabilityContract;
     * this is not a generic composition switch or a damage-consumption implementation. */
    private void itemAttributeDurabilityRecipes() {
        minecraft(PROPERTIES,"durability","(I)L"+PROPERTIES+";",
                "item-attribute-durability.native-capacity-128-stack-one.v1",Opcodes.INVOKEVIRTUAL);
        minecraft(ITEM,"isValidRepairItem","(L"+STACK+";L"+STACK+";)Z",
                "item-attribute-durability.iron-ingredient-virtual-override.v1",Opcodes.INVOKEVIRTUAL);
        minecraft(STACK,"getItem","()L"+ITEM+";",
                "item-attribute-durability.second-argument-item-identity.v1",Opcodes.INVOKEVIRTUAL);
        minecraftField("net/minecraft/world/item/Items","IRON_INGOT","L"+ITEM+";",
                "item-attribute-durability.native-iron-ingredient.v1",Opcodes.GETSTATIC);
    }

    /** These recipes require the complete guarded helper, not merely the presence of stack methods.
     * Native Forge hooks retain their reviewed defaults; they are verified below but are deliberately
     * not admitted as calls made by arbitrary converted code. */
    private void ownedStackDamageRecipes() {
        minecraft(ITEM,"<init>","(L"+PROPERTIES+";)V","owned-stack-damage.native-item-super.v1",Opcodes.INVOKESPECIAL);
        minecraft(PROPERTIES,"<init>","()V","owned-stack-damage.fresh-properties.v1",Opcodes.INVOKESPECIAL);
        minecraft(PROPERTIES,"durability","(I)L"+PROPERTIES+";","owned-stack-damage.capacity-128-stack-one.v1",Opcodes.INVOKEVIRTUAL);
        minecraft(STACK,"getItem","()L"+ITEM+";","owned-stack-damage.received-item-identity-guard.v1",Opcodes.INVOKEVIRTUAL);
        minecraft(STACK,"isDamaged","()Z","owned-stack-damage.native-damaged-guard.v1",Opcodes.INVOKEVIRTUAL);
        minecraft(STACK,"getDamageValue","()I","owned-stack-damage.observed-positive-damage.v1",Opcodes.INVOKEVIRTUAL);
        minecraft(STACK,"getMaxDamage","()I","owned-stack-damage.observed-maximum-guard.v1",Opcodes.INVOKEVIRTUAL);
        minecraft(STACK,"setDamageValue","(I)V","owned-stack-damage.single-guarded-decrement.v1",Opcodes.INVOKEVIRTUAL);
    }

    private void verifyOwnedStackDamageTypesAndHooks() {
        String forge="net/minecraftforge/common/extensions/IForgeItem";
        for(boolean source:List.of(true,false)) {
            ApiIndex api=source?sourceApi:targetApi;LogicApiNamespace ns=source?sourceNamespace:targetNamespace;
            String item=ns.productionClass(ITEM),stack=ns.productionClass(STACK),properties=ns.productionClass(PROPERTIES);
            stackDamageType(api,item,Opcodes.ACC_PUBLIC,source?"net/minecraftforge/registries/ForgeRegistryEntry":"java/lang/Object",
                    source?Set.of("net/minecraft/util/IItemProvider",forge):Set.of("net/minecraft/world/flag/FeatureElement","net/minecraft/world/level/ItemLike",forge));
            stackDamageType(api,properties,Opcodes.ACC_PUBLIC,"java/lang/Object",Set.of());
            stackDamageType(api,stack,Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL,"net/minecraftforge/common/capabilities/CapabilityProvider",
                    Set.of("net/minecraftforge/common/extensions/IForgeItemStack"));
            stackDamageType(api,forge,Opcodes.ACC_PUBLIC|Opcodes.ACC_INTERFACE|Opcodes.ACC_ABSTRACT,"java/lang/Object",Set.of());
            stackDamageMethod(api,item,"<init>","(L"+properties+";)V",Opcodes.ACC_PUBLIC);
            stackDamageMethod(api,properties,"<init>","()V",Opcodes.ACC_PUBLIC);
            stackDamageMethod(api,properties,ns.productionMethod(PROPERTIES,"durability","(I)L"+PROPERTIES+";"),"(I)L"+properties+";",Opcodes.ACC_PUBLIC);
            for(var method:Map.of("getItem","()L"+ITEM+";","isDamaged","()Z","getDamageValue","()I",
                    "getMaxDamage","()I","setDamageValue","(I)V","isDamageableItem","()Z").entrySet()) {
                stackDamageMethod(api,stack,ns.productionMethod(STACK,method.getKey(),method.getValue()),
                        ns.productionDescriptor(method.getValue()),Opcodes.ACC_PUBLIC);
            }
            stackDamageMethod(api,item,ns.productionMethod(ITEM,"getMaxDamage","()I"),"()I",Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL);
            stackDamageMethod(api,item,ns.productionMethod(ITEM,"canBeDepleted","()Z"),"()Z",Opcodes.ACC_PUBLIC);
            for(var hook:Map.of("getDamage","(L"+stack+";)I","getMaxDamage","(L"+stack+";)I",
                    "isDamaged","(L"+stack+";)Z","setDamage","(L"+stack+";I)V","isDamageable","(L"+stack+";)Z").entrySet()) {
                stackDamageMethod(api,forge,hook.getKey(),hook.getValue(),Opcodes.ACC_PUBLIC);
                Declaration inherited=externalDeclaration(api,item,hook.getKey(),hook.getValue(),false,new HashSet<>());
                if(inherited==null||!forge.equals(inherited.owner()))
                    throw failure("OWNED_STACK_DAMAGE_HOOK_DISPATCH_CHANGED",item+"."+hook.getKey()+hook.getValue());
            }
            // The source default casts this through getItem(); target uses its private self().
            // Neither difference gives the owned helper permission to call either implementation.
            stackDamageMethod(api,forge,source?"getItem":"self","()L"+item+";",source?Opcodes.ACC_PUBLIC:Opcodes.ACC_PRIVATE);
            if(source) {
                Declaration inherited=externalDeclaration(api,item,"getItem","()L"+item+";",false,new HashSet<>());
                if(inherited==null||!forge.equals(inherited.owner()))
                    throw failure("OWNED_STACK_DAMAGE_HOOK_DISPATCH_CHANGED",item+".getItem");
            }
        }
    }
    private static void stackDamageType(ApiIndex api,String owner,int expected,String parent,Set<String> interfaces) {
        var type=requireApiClass(api,owner);
        int mask=Opcodes.ACC_PUBLIC|Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED|Opcodes.ACC_FINAL|Opcodes.ACC_ABSTRACT
                |Opcodes.ACC_INTERFACE|Opcodes.ACC_ENUM|Opcodes.ACC_ANNOTATION;
        if((type.access()&mask)!=expected||!Objects.equals(type.superName(),parent)
                ||type.interfaces().size()!=interfaces.size()||!new HashSet<>(type.interfaces()).equals(interfaces))
            throw failure("OWNED_STACK_DAMAGE_TYPE_CONTRACT_CHANGED",owner);
    }
    private void stackDamageMethod(ApiIndex api,String owner,String name,String descriptor,int expected) {
        Declaration exact=declared(api,owner,name,descriptor,false);
        int mask=Opcodes.ACC_PUBLIC|Opcodes.ACC_PROTECTED|Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC|Opcodes.ACC_FINAL
                |Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE|Opcodes.ACC_SYNCHRONIZED|Opcodes.ACC_BRIDGE|Opcodes.ACC_VARARGS|Opcodes.ACC_SYNTHETIC;
        if(exact==null||(exact.member().access()&mask)!=expected)
            throw failure("OWNED_STACK_DAMAGE_MEMBER_CONTRACT_CHANGED",owner+"."+name+descriptor);
    }

    /** Exact native contracts observed in the pinned sources/binaries; the owned proof gates all
     * recipes. Prediction, creative restoration, cancellation and native sync are not emulated. */
    private void ownedStackRepairUseRecipes() {
        minecraft(ITEM,"use",repairUseDescriptor(),"owned-stack-repair-use.native-use-override.v1",Opcodes.INVOKEVIRTUAL);
        minecraftInherited(PLAYER,LIVING,"getItemInHand","(L"+HAND+";)L"+STACK+";",
                "owned-stack-repair-use.received-player-hand.v1");
        minecraftField(LEVEL,"isClientSide","Z","owned-stack-repair-use.logical-side-field.v1",Opcodes.GETFIELD);
        for(String name:List.of("success","consume","pass"))
            minecraft(RESULT_HOLDER,name,"(Ljava/lang/Object;)L"+RESULT_HOLDER+";",
                    "owned-stack-repair-use.same-stack-"+name+".v1",Opcodes.INVOKESTATIC);
    }
    private static String repairUseDescriptor() {
        return "(L"+LEVEL+";L"+PLAYER+";L"+HAND+";)L"+RESULT_HOLDER+";";
    }
    private void verifyOwnedStackRepairUseTypesAndMembers() {
        for(boolean source:List.of(true,false)) {
            ApiIndex api=source?sourceApi:targetApi;LogicApiNamespace ns=source?sourceNamespace:targetNamespace;
            String level=ns.productionClass(LEVEL),player=ns.productionClass(PLAYER),living=ns.productionClass(LIVING);
            stackDamageType(api,level,Opcodes.ACC_PUBLIC|Opcodes.ACC_ABSTRACT,
                    "net/minecraftforge/common/capabilities/CapabilityProvider",
                    source?Set.of("net/minecraft/world/IWorld","java/lang/AutoCloseable","net/minecraftforge/common/extensions/IForgeWorld")
                            :Set.of("net/minecraft/world/level/LevelAccessor","java/lang/AutoCloseable","net/minecraftforge/common/extensions/IForgeLevel"));
            stackDamageType(api,player,Opcodes.ACC_PUBLIC|Opcodes.ACC_ABSTRACT,living,
                    source?Set.of():Set.of("net/minecraftforge/common/extensions/IForgePlayer"));
            stackDamageType(api,living,Opcodes.ACC_PUBLIC|Opcodes.ACC_ABSTRACT,ns.productionClass("net/minecraft/world/entity/Entity"),
                    source?Set.of():Set.of("net/minecraft/world/entity/Attackable","net/minecraftforge/common/extensions/IForgeLivingEntity"));
            stackDamageType(api,ns.productionClass(HAND),Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL|Opcodes.ACC_ENUM,"java/lang/Enum",Set.of());
            stackDamageType(api,ns.productionClass(RESULT_HOLDER),Opcodes.ACC_PUBLIC,"java/lang/Object",Set.of());
            stackDamageMethod(api,ns.productionClass(ITEM),ns.productionMethod(ITEM,"use",repairUseDescriptor()),
                    ns.productionDescriptor(repairUseDescriptor()),Opcodes.ACC_PUBLIC);
            String handDescriptor="(L"+HAND+";)L"+STACK+";";
            String handName=ns.productionMethod(LIVING,"getItemInHand",handDescriptor);
            String nativeHandDescriptor=ns.productionDescriptor(handDescriptor);
            stackDamageMethod(api,living,handName,nativeHandDescriptor,Opcodes.ACC_PUBLIC);
            // A nearest-declaration lookup is insufficient: an injected ancestor/interface
            // declaration can be hidden by LivingEntity, and a private official-named method
            // cannot inherit the SRG spelling during normalization. Neither exists in the
            // reviewed native contract. Inspect the complete hierarchy, including Player.
            var handHierarchy=new LinkedHashSet<String>();
            handHierarchy.add(player);
            handHierarchy.addAll(externalAncestors(player,!source,new HashSet<>()));
            for(String ancestor:handHierarchy) {
                if(ancestor.equals(living))continue;
                var type=ancestor.startsWith("java/")?jdkClass(ancestor):requireApiClass(api,ancestor);
                if(type.methods().stream().anyMatch(method->method.descriptor().equals(nativeHandDescriptor)
                        && (method.name().equals(handName)||method.name().equals("getItemInHand"))))
                    throw failure("OWNED_STACK_REPAIR_USE_HAND_DISPATCH_CHANGED",ancestor+" shadows the reviewed LivingEntity hand declaration");
            }
            Declaration hand=externalDeclaration(api,player,handName,nativeHandDescriptor,false,new HashSet<>());
            if(hand==null||!hand.owner().equals(living))
                throw failure("OWNED_STACK_REPAIR_USE_HAND_DISPATCH_CHANGED",player+"."+handName+nativeHandDescriptor);
            for(String name:List.of("success","consume","pass")) {
                String descriptor="(Ljava/lang/Object;)L"+RESULT_HOLDER+";";
                stackDamageMethod(api,ns.productionClass(RESULT_HOLDER),ns.productionMethod(RESULT_HOLDER,name,descriptor),
                        ns.productionDescriptor(descriptor),Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC);
            }
            Declaration side=declared(api,level,ns.productionField(LEVEL,"isClientSide","Z"),"Z",true);
            int mask=Opcodes.ACC_PUBLIC|Opcodes.ACC_PROTECTED|Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC
                    |Opcodes.ACC_FINAL|Opcodes.ACC_VOLATILE|Opcodes.ACC_TRANSIENT|Opcodes.ACC_SYNTHETIC|Opcodes.ACC_ENUM;
            if(side==null||(side.member().access()&mask)!=(Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL))
                throw failure("OWNED_STACK_REPAIR_USE_SIDE_CONTRACT_CHANGED",level+".isClientSide");
        }
    }

    private void verifyItemAttributeTypes() {
        for(boolean source:List.of(true,false)) {
            String prefix=source?"legacy.apiJars[":"target.apiJars[";
            String hash=source?"972139718abc8a4893fa78cba8cf7b2c903f35c97aaf44fa3031b0669948b480":"a42edc9cab792e39fe39bb94f3fca655ed157ff87a8af78e1d6ba5b07c4a00ab";
            if(inputs.sources().stream().noneMatch(s->s.id().startsWith(prefix)&&s.sha256().equals(hash)))
                throw failure("ITEM_ATTRIBUTES_GUAVA_DATA_REQUIRED","Explicit reviewed "+(source?"Guava 21.0 source":"Guava 31.1-jre target")+" API provenance is required; converter classpath is not fallback data");
            ApiIndex api=source?sourceApi:targetApi;LogicApiNamespace ns=source?sourceNamespace:targetNamespace;
            int pub=Opcodes.ACC_PUBLIC,abs=Opcodes.ACC_ABSTRACT;
            for(String canonical:List.of(EQUIPMENT_SLOT,MODIFIER_OPERATION))
                itemAttributeType(api,ns.productionClass(canonical),pub|Opcodes.ACC_FINAL|Opcodes.ACC_ENUM,"java/lang/Enum",Set.of());
            itemAttributeType(api,ns.productionClass(MODIFIER),pub,"java/lang/Object",Set.of());
            itemAttributeType(api,ns.productionClass(ATTRIBUTES),pub,"java/lang/Object",Set.of());
            itemAttributeType(api,ns.productionClass(ATTRIBUTE),pub,source?"net/minecraftforge/registries/ForgeRegistryEntry":"java/lang/Object",Set.of());
            itemAttributeType(api,ns.productionClass(ITEM),pub,source?"net/minecraftforge/registries/ForgeRegistryEntry":"java/lang/Object",
                    source?Set.of("net/minecraft/util/IItemProvider","net/minecraftforge/common/extensions/IForgeItem"):
                            Set.of("net/minecraft/world/flag/FeatureElement","net/minecraft/world/level/ItemLike","net/minecraftforge/common/extensions/IForgeItem"));
            itemAttributeType(api,ns.productionClass(PROPERTIES),pub,"java/lang/Object",Set.of());
            itemAttributeType(api,MULTIMAP,pub|abs|Opcodes.ACC_INTERFACE,"java/lang/Object",Set.of());
            itemAttributeType(api,MULTIMAP_BUILDER,pub,"java/lang/Object",Set.of());
            String abstractMap="com/google/common/collect/AbstractMultimap",baseMap="com/google/common/collect/BaseImmutableMultimap";
            itemAttributeType(api,IMMUTABLE_MULTIMAP,pub|abs,source?abstractMap:baseMap,Set.of("java/io/Serializable"));
            itemAttributeType(api,abstractMap,abs,"java/lang/Object",Set.of(MULTIMAP));
            if(!source)itemAttributeType(api,baseMap,abs,abstractMap,Set.of());
        }
    }
    private static void itemAttributeType(ApiIndex api,String owner,int access,String parent,Set<String> interfaces) {
        var type=requireApiClass(api,owner);
        int mask=Opcodes.ACC_PUBLIC|Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED|Opcodes.ACC_FINAL|Opcodes.ACC_ABSTRACT|Opcodes.ACC_INTERFACE|Opcodes.ACC_ENUM|Opcodes.ACC_ANNOTATION;
        if((type.access()&mask)!=access||!Objects.equals(type.superName(),parent)||!new HashSet<>(type.interfaces()).equals(interfaces))
            throw failure("ITEM_ATTRIBUTES_TYPE_CONTRACT_CHANGED",owner);
    }
    private void itemAttributeDescriptor(Type type,ApiIndex api) {
        if(type.getSort()==Type.METHOD){itemAttributeDescriptor(type.getReturnType(),api);for(Type argument:type.getArgumentTypes())itemAttributeDescriptor(argument,api);return;}
        if(type.getSort()==Type.ARRAY){itemAttributeDescriptor(type.getElementType(),api);return;}
        if(type.getSort()!=Type.OBJECT)return;
        String owner=type.getInternalName();var declaration=owner.startsWith("java/")?jdkClass(owner):requireApiClass(api,owner);
        if((declaration.access()&Opcodes.ACC_PUBLIC)==0)throw failure("ITEM_ATTRIBUTES_DESCRIPTOR_INACCESSIBLE",owner);
    }

    /** Only the proved false-default, two-state, client-guarded single callback-position write. */
    private void booleanToggleBlockRecipes() {
        minecraftField(BLOCK,"stateDefinition","L"+DEFINITION+";","boolean-toggle.protected-state-definition.v1",Opcodes.GETFIELD);
        minecraft(BLOCK,"registerDefaultState","(L"+BLOCK_STATE+";)V","boolean-toggle.protected-default-state.v1",Opcodes.INVOKEVIRTUAL);
        minecraft(BLOCK,"createBlockStateDefinition","(L"+DEFINITION+"$Builder;)V","boolean-toggle.protected-definition-override.v1",Opcodes.INVOKEVIRTUAL);
        minecraft(DEFINITION,"any","()L"+STATE+";","boolean-toggle.two-state-any.v1",Opcodes.INVOKEVIRTUAL);
        minecraft(DEFINITION+"$Builder","add","([L"+PROPERTY+";)L"+DEFINITION+"$Builder;","boolean-toggle.single-property-builder.v1",Opcodes.INVOKEVIRTUAL);
        minecraftInherited(BLOCK_STATE,STATE,"setValue","(L"+PROPERTY+";Ljava/lang/Comparable;)Ljava/lang/Object;","boolean-toggle.false-default-set-value.v1");
        minecraftInherited(BLOCK_STATE,STATE,"cycle","(L"+PROPERTY+";)Ljava/lang/Object;","boolean-toggle.two-state-cycle.v1");
        minecraft(BOOLEAN_PROPERTY,"create","(Ljava/lang/String;)L"+BOOLEAN_PROPERTY+";","boolean-toggle.active-property.v1",Opcodes.INVOKESTATIC);
        nativeRecipe("java/lang/Boolean","FALSE","Ljava/lang/Boolean;",true,"boolean-toggle.jdk-false-singleton.v1",Opcodes.GETSTATIC);
        minecraftField(LEVEL,"isClientSide","Z","boolean-toggle.logical-side-field.v1",Opcodes.GETFIELD);
        minecraft(LEVEL,"setBlock","(L"+BLOCK_POS+";L"+BLOCK_STATE+";I)Z","boolean-toggle.single-local-state-write.v1",Opcodes.INVOKEVIRTUAL);
        for (String result : List.of("SUCCESS","CONSUME"))
            minecraftField(INTERACTION_RESULT,result,"L"+INTERACTION_RESULT+";","boolean-toggle.result-singleton.v1",Opcodes.GETSTATIC);
        minecraft(PlainBlockApiRecipe.BEHAVIOUR,"use","(L"+BLOCK_STATE+";L"+LEVEL+";L"+BLOCK_POS+";L"+PLAYER+";L"+HAND+";L"+BLOCK_HIT+";)L"+INTERACTION_RESULT+";",
                "boolean-toggle.native-use-override.v1",Opcodes.INVOKEVIRTUAL);
    }

    /**
     * Only the pre-proved literal bottom half admits this native box rename. The two APIs differ
     * for reversed/degenerate bounds and native solidity is initialized through different paths;
     * this is not a general geometry equivalence. The native overrides ignore all context inputs
     * and return their own same static final shape, independently re-read by the offline gate.
     */
    private void staticShapeBlockRecipes() {
        minecraft(BLOCK, "box", "(DDDDDD)L" + VOXEL_SHAPE + ";",
                "static-shape.literal-bottom-half-box.v1", Opcodes.INVOKESTATIC);
        String descriptor="(L" + BLOCK_STATE + ";L" + BLOCK_GETTER + ";L" + BLOCK_POS + ";L" + COLLISION_CONTEXT + ";)L" + VOXEL_SHAPE + ";";
        minecraft(PlainBlockApiRecipe.BEHAVIOUR, "getShape", descriptor,
                "static-shape.native-outline-override.v1", Opcodes.INVOKEVIRTUAL);
        minecraft(PlainBlockApiRecipe.BEHAVIOUR, "getCollisionShape", descriptor,
                "static-shape.native-collision-override.v1", Opcodes.INVOKEVIRTUAL);
    }

    /**
     * Exact 36.2.42/47.4.23 state path. The owned constructor registers NORTH only after super;
     * the super constructor invokes the protected property-definition override first. Both native
     * setValue implementations erase S to Object; their ==/equals difference is harmless only for
     * the proved Direction enum singleton values. No arbitrary property, context or callback enters.
     */
    private void horizontalBlockRecipes() {
        minecraftField(BLOCK, "stateDefinition", "L" + DEFINITION + ";",
                "horizontal.protected-state-definition.v1", Opcodes.GETFIELD);
        minecraft(BLOCK, "registerDefaultState", "(L" + BLOCK_STATE + ";)V",
                "horizontal.protected-default-state.v1", Opcodes.INVOKEVIRTUAL);
        minecraft(BLOCK, "defaultBlockState", "()L" + BLOCK_STATE + ";",
                "horizontal.native-default-state.v1", Opcodes.INVOKEVIRTUAL);
        minecraft(BLOCK, "createBlockStateDefinition", "(L" + DEFINITION + "$Builder;)V",
                "horizontal.protected-definition-override.v1", Opcodes.INVOKEVIRTUAL);
        minecraft(BLOCK, "getStateForPlacement", "(L" + PLACEMENT + ";)L" + BLOCK_STATE + ";",
                "horizontal.placement-override.v1", Opcodes.INVOKEVIRTUAL);
        minecraft(DEFINITION, "any", "()L" + STATE + ";",
                "horizontal.finite-state-any.v1", Opcodes.INVOKEVIRTUAL);
        minecraft(DEFINITION + "$Builder", "add", "([L" + PROPERTY + ";)L" + DEFINITION + "$Builder;",
                "horizontal.single-property-builder.v1", Opcodes.INVOKEVIRTUAL);
        minecraftInherited(BLOCK_STATE, STATE, "setValue", "(L" + PROPERTY + ";Ljava/lang/Comparable;)Ljava/lang/Object;",
                "horizontal.direction-enum-set-value.v1");
        minecraftInherited(PLACEMENT, USE_CONTEXT, "getHorizontalDirection", "()L" + DIRECTION + ";",
                "horizontal.context-player-or-north.v1");
        minecraft(DIRECTION, "getOpposite", "()L" + DIRECTION + ";",
                "horizontal.opposite-direction.v1", Opcodes.INVOKEVIRTUAL);
        minecraftField(DIRECTION, "NORTH", "L" + DIRECTION + ";",
                "horizontal.north-enum-singleton.v1", Opcodes.GETSTATIC);
        minecraftField(STATE_PROPERTIES, "HORIZONTAL_FACING", "L" + DIRECTION_PROPERTY + ";",
                "horizontal.four-value-facing-property.v1", Opcodes.GETSTATIC);
        if (orientationTransforms) {
            // These two real overrides are declared on BlockBehaviour, NOT its Block subclass.
            // Contract admission requires the complete pair and the original argument state.
            minecraft(PlainBlockApiRecipe.BEHAVIOUR, "rotate", "(L" + BLOCK_STATE + ";L" + ROTATION + ";)L" + BLOCK_STATE + ";",
                    "horizontal.rotate-facing-override.v1", Opcodes.INVOKEVIRTUAL);
            minecraft(PlainBlockApiRecipe.BEHAVIOUR, "mirror", "(L" + BLOCK_STATE + ";L" + MIRROR + ";)L" + BLOCK_STATE + ";",
                    "horizontal.mirror-facing-override.v1", Opcodes.INVOKEVIRTUAL);
            minecraftInherited(BLOCK_STATE, STATE, "getValue", "(L" + PROPERTY + ";)Ljava/lang/Comparable;",
                    "horizontal.existing-facing-value.v1");
            minecraft(ROTATION, "rotate", "(L" + DIRECTION + ";)L" + DIRECTION + ";",
                    "horizontal.four-direction-rotation.v1", Opcodes.INVOKEVIRTUAL);
            minecraft(MIRROR, "mirror", "(L" + DIRECTION + ";)L" + DIRECTION + ";",
                    "horizontal.four-direction-mirror.v1", Opcodes.INVOKEVIRTUAL);
        }
    }

    /** Java emits the referenced subclass, while exact Mojang rows belong to the declaring ancestor. */
    private void minecraftInherited(String referenceOwner, String declaringOwner, String name, String descriptor, String contract) {
        minecraft(declaringOwner, name, descriptor, contract, Opcodes.INVOKEVIRTUAL);
        LegacySymbol declaration = LegacySymbol.method(sourceNamespace.productionClass(declaringOwner),
                sourceNamespace.productionMethod(declaringOwner, name, descriptor), sourceNamespace.productionDescriptor(descriptor));
        Recipe reviewed = recipes.get(declaration);
        LegacySymbol alias = LegacySymbol.method(sourceNamespace.productionClass(referenceOwner), declaration.name(), declaration.descriptor());
        TargetSymbol target = TargetSymbol.method(targetNamespace.productionClass(referenceOwner), reviewed.target().name(), reviewed.target().descriptor());
        recipes.put(alias, new Recipe(alias, target, contract, Opcodes.INVOKEVIRTUAL));
        horizontalAliases.put(alias, declaration);
    }

    private void minecraftField(String canonical, String name, String descriptor, String contract, int opcode) {
        LegacySymbol source = LegacySymbol.field(sourceNamespace.productionClass(canonical),
                sourceNamespace.productionField(canonical, name, descriptor), sourceNamespace.productionDescriptor(descriptor));
        TargetSymbol target = TargetSymbol.field(targetNamespace.productionClass(canonical),
                targetNamespace.productionField(canonical, name, descriptor), targetNamespace.productionDescriptor(descriptor));
        recipes.put(source, new Recipe(source, target, contract, opcode));
    }

    private void minecraft(String canonical, String method, String descriptor, String contract, int opcode) {
        String sourceOwner = sourceNamespace.productionClass(canonical);
        String targetOwner = targetNamespace.productionClass(canonical);
        String sourceName = sourceNamespace.productionMethod(canonical, method, descriptor);
        String targetName = targetNamespace.productionMethod(canonical, method, descriptor);
        LegacySymbol source = symbol(sourceOwner, sourceName, sourceNamespace.productionDescriptor(descriptor), false);
        TargetSymbol target = new TargetSymbol(source.kind(), targetOwner, targetName, targetNamespace.productionDescriptor(descriptor));
        recipes.put(source, new Recipe(source, target, contract, opcode));
    }

    private void nativeRecipe(String owner, String name, String descriptor, boolean field, String contract, int opcode) {
        LegacySymbol source = symbol(owner, name, descriptor, field);
        String targetDescriptor = remapDescriptor(descriptor);
        if (lifecycle) targetDescriptor = dev.legacybridge.runtime.compat.logic.LifecycleContractAdapter.methodDescriptor(owner, name, descriptor, targetDescriptor);
        if (suiteProof != null) targetDescriptor = dev.legacybridge.runtime.compat.logic.LifecycleSuiteAdapter.methodDescriptor(owner, name, descriptor, targetDescriptor);
        if (plainBlock) targetDescriptor = dev.legacybridge.runtime.compat.logic.PlainBlockArtifactAdapter.registryDescriptor(owner, name, descriptor, targetDescriptor);
        recipes.put(source, new Recipe(source, new TargetSymbol(source.kind(), classes.get(owner), name, targetDescriptor), contract, opcode));
    }

    private String remapDescriptor(String descriptor) {
        // This exact implemented OFFLINE operation is also the catalog binding of each approved rule.
        return network ? dev.legacybridge.runtime.compat.networking.SnapshotContractAdapter.remapDescriptor(descriptor, classes)
                : food ? dev.legacybridge.runtime.compat.logic.FoodContractAdapter.remapDescriptor(descriptor, classes)
                : PreservedLogicAdapter.remapDescriptor(descriptor, classes);
    }

    private void requireType(String owner) {
        if (!classes.containsKey(owner)) throw failure("LOGIC_UNSUPPORTED_TYPE", "No reviewed class contract for " + owner);
    }

    private void approveClass(String owner) {
        requireType(owner);
        if ((lifecycle || plainBlock) && owner.equals(ERASED_ENTRY)) return; // Source-only call return; final offline gate forbids any retained reference.
        if (!owner.startsWith("java/")) {
            var from = requireApiClass(sourceApi, owner);
            var to = requireApiClass(targetApi, classes.get(owner));
            if ((durable || itemAttributeDurability) && owner.equals(sourceNamespace.productionClass(STACK))
                    && ((from.access() & Opcodes.ACC_FINAL)==0 || (to.access() & Opcodes.ACC_FINAL)==0))
                throw failure("DURABLE_STACK_CONTRACT_CHANGED", "Reviewed native stack must remain final on both baselines");
            if ((horizontalBlock && owner.equals(sourceNamespace.productionClass(DIRECTION))
                    || orientationTransforms && (owner.equals(sourceNamespace.productionClass(ROTATION))
                    || owner.equals(sourceNamespace.productionClass(MIRROR)))
                    || booleanToggleBlock && (owner.equals(sourceNamespace.productionClass(INTERACTION_RESULT))
                    || owner.equals(sourceNamespace.productionClass(HAND))))
                    && ((from.access() & (Opcodes.ACC_FINAL | Opcodes.ACC_ENUM)) != (Opcodes.ACC_FINAL | Opcodes.ACC_ENUM)
                    || (to.access() & (Opcodes.ACC_FINAL | Opcodes.ACC_ENUM)) != (Opcodes.ACC_FINAL | Opcodes.ACC_ENUM)
                    || !"java/lang/Enum".equals(from.superName()) || !"java/lang/Enum".equals(to.superName())))
                throw failure(booleanToggleBlock?"BOOLEAN_TOGGLE_ENUM_CONTRACT_CHANGED":"HORIZONTAL_DIRECTION_CONTRACT_CHANGED", "The finite proof requires the actual reviewed final native enums");
            int kind = Opcodes.ACC_INTERFACE | Opcodes.ACC_ANNOTATION | Opcodes.ACC_ENUM;
            boolean opaqueRegistrationResult = network && owner.equals("net/minecraftforge/fml/network/simple/IndexedMessageCodec$MessageHandler")
                    && classes.get(owner).equals("net/minecraftforge/network/simple/IndexedMessageCodec$MessageHandler")
                    && (from.access() & (kind | Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) == 0
                    && (to.access() & (kind | Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) == 0
                    && "java/lang/Object".equals(from.superName()) && "java/lang/Object".equals(to.superName())
                    && from.interfaces().isEmpty() && to.interfaces().isEmpty();
            // Both pinned public registerMessage methods return a package-private class.
            // Its only approved use is a discarded call result (exact original recipe +
            // separate offline opaque-return gate). Do not fabricate a public API class.
            if (!opaqueRegistrationResult && ((from.access() & kind) != (to.access() & kind) || (from.access() & Opcodes.ACC_PUBLIC) == 0
                    || (to.access() & Opcodes.ACC_PUBLIC) == 0)) throw failure("LOGIC_TYPE_ABI_CHANGED", owner);
        } else jdkClass(owner);
        add(LegacySymbol.classSymbol(owner), TargetSymbol.classSymbol(classes.get(owner)),
                serverConfigProof != null && dev.legacybridge.converter.conversion.ServerConfigContract.EVENT_TYPES.containsKey(owner)
                        ? "lifecycle.server-event-phase.v1" : food && (owner.equals(sourceNamespace.productionClass(FOOD)) || owner.equals(sourceNamespace.productionClass(FOOD + "$Builder")))
                        ? "food.reviewed-class-component.v1" : "logic.reviewed-class-component.v1",
                "HIGH", genericStatus(LegacySymbol.classSymbol(owner)));
    }

    private void approveExternal(LegacySymbol source, Integer opcode) {
        Recipe recipe = recipes.get(source);
        if (recipe == null) throw failure("LOGIC_UNSUPPORTED_MEMBER", readable(source));
        LegacySymbol horizontalDeclaration = horizontalAliases.get(source);
        Recipe declarationRecipe = horizontalDeclaration == null ? recipe : recipes.get(horizontalDeclaration);
        if (source.owner().startsWith("net/minecraft/")) {
            // An exact symbol candidate is necessary, but never sufficient: the finite recipe and
            // independent declaration/opcode checks below must also approve the contract.
            LegacySymbol candidateSource = horizontalDeclaration == null ? source : horizontalDeclaration;
            MappingRule candidate = new MappingDatabaseGenerator(inputs).generate(List.of(candidateSource)).resolve(candidateSource);
            if (candidate.status() != MappingStatus.RESOLVED || !declarationRecipe.target().equals(candidate.target())) {
                throw failure("LOGIC_CANDIDATE_DISAGREEMENT", readable(source));
            }
        }
        if (opcode != null && opcode != recipe.opcode()
                && !(opcode == Opcodes.INVOKESPECIAL && recipe.opcode() == Opcodes.INVOKEVIRTUAL
                && recipe.contract().equals("logic.item-foil-override.v1"))) {
            throw failure("LOGIC_INVOCATION_KIND", readable(source));
        }
        Declaration from = externalDeclaration(sourceApi, source.owner(), source.name(), source.descriptor(), source.kind() == SymbolKind.FIELD, new HashSet<>());
        Declaration to = externalDeclaration(targetApi, recipe.target().owner(), recipe.target().name(), recipe.target().descriptor(), source.kind() == SymbolKind.FIELD, new HashSet<>());
        boolean inheritedLifecycle = (lifecycle && recipe.contract().startsWith("lifecycle.") || network && recipe.contract().startsWith("network.")) && from != null && to != null
                && (from.owner().equals(to.owner()) || suiteProof != null && recipe.contract().equals("lifecycle.config-event-owned-filter.v1")
                && from.owner().equals(dev.legacybridge.converter.conversion.LifecycleSuiteContract.OLD_EVENT)
                && to.owner().equals(dev.legacybridge.converter.conversion.LifecycleSuiteContract.NEW_EVENT));
        boolean reviewedHorizontalAlias = horizontalDeclaration != null && from != null && to != null
                && from.owner().equals(horizontalDeclaration.owner()) && to.owner().equals(declarationRecipe.target().owner());
        if(ownedStackRepairUseProof!=null && horizontalDeclaration!=null && !reviewedHorizontalAlias)
            throw failure("OWNED_STACK_REPAIR_USE_HAND_DISPATCH_CHANGED",readable(source));
        if (from == null || to == null || !inheritedLifecycle && !reviewedHorizontalAlias
                && (!from.owner().equals(source.owner()) || !to.owner().equals(recipe.target().owner()))) {
            throw failure("LOGIC_MAPPING_DATA_REQUIRED", "Exact declarations absent for reviewed contract " + readable(source));
        }
        int expectedStatic = recipe.opcode() == Opcodes.INVOKESTATIC || recipe.opcode() == Opcodes.GETSTATIC ? Opcodes.ACC_STATIC : 0;
        boolean protectedHorizontal = horizontalBlock && Set.of("horizontal.protected-state-definition.v1",
                "horizontal.protected-default-state.v1", "horizontal.protected-definition-override.v1").contains(recipe.contract())
                || booleanToggleBlock && Set.of("boolean-toggle.protected-state-definition.v1",
                "boolean-toggle.protected-default-state.v1", "boolean-toggle.protected-definition-override.v1").contains(recipe.contract())
                || (itemAttributes || itemAttributeDurability) && recipe.contract().equals("item-attributes.inherited-native-uuid.v1");
        int visibility = Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE;
        int expectedVisibility = protectedHorizontal ? Opcodes.ACC_PROTECTED : Opcodes.ACC_PUBLIC;
        if ((from.member().access() & Opcodes.ACC_STATIC) != expectedStatic || (to.member().access() & Opcodes.ACC_STATIC) != expectedStatic
                || (from.member().access() & visibility) != expectedVisibility || (to.member().access() & visibility) != expectedVisibility) {
            throw failure("LOGIC_MEMBER_ABI_CHANGED", readable(source));
        }
        if (horizontalBlock && recipe.contract().startsWith("horizontal.")
                || staticShapeBlock && recipe.contract().startsWith("static-shape.")
                || booleanToggleBlock && recipe.contract().startsWith("boolean-toggle.")) {
            boolean expectedFinal = Set.of("horizontal.protected-state-definition.v1", "horizontal.protected-default-state.v1",
                    "horizontal.native-default-state.v1", "horizontal.north-enum-singleton.v1",
                    "horizontal.four-value-facing-property.v1", "boolean-toggle.protected-state-definition.v1",
                    "boolean-toggle.protected-default-state.v1", "boolean-toggle.jdk-false-singleton.v1",
                    "boolean-toggle.logical-side-field.v1", "boolean-toggle.result-singleton.v1").contains(recipe.contract());
            int mask = Opcodes.ACC_FINAL | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE;
            int expected = expectedFinal ? Opcodes.ACC_FINAL : 0;
            if ((from.member().access() & mask) != expected || (to.member().access() & mask) != expected)
                throw failure(booleanToggleBlock ? "BOOLEAN_TOGGLE_MEMBER_CONTRACT_CHANGED" : staticShapeBlock ? "STATIC_SHAPE_MEMBER_CONTRACT_CHANGED" : "HORIZONTAL_MEMBER_CONTRACT_CHANGED", readable(source));
        }
        if (durable && recipe.contract().equals("durable.native-iron-ingredient.v1")
                && ((from.member().access() & Opcodes.ACC_FINAL)==0 || (to.member().access() & Opcodes.ACC_FINAL)==0))
            throw failure("DURABLE_INGREDIENT_CONTRACT_CHANGED", "Reviewed native ingredient field must remain final");
        if((itemAttributes || itemAttributeDurability) && recipe.contract().startsWith("item-attributes.")
                || itemAttributeDurability && recipe.contract().startsWith("item-attribute-durability.")) {
            int mask=Opcodes.ACC_FINAL|Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE;
            int expected=source.kind()==SymbolKind.FIELD?Opcodes.ACC_FINAL:0;
            if((from.member().access()&mask)!=expected||(to.member().access()&mask)!=expected)
                throw failure("ITEM_ATTRIBUTES_MEMBER_CONTRACT_CHANGED",readable(source));
            if((requireApiClass(sourceApi,from.owner()).access()&Opcodes.ACC_INTERFACE)!=0
                    ||(requireApiClass(targetApi,to.owner()).access()&Opcodes.ACC_INTERFACE)!=0)
                throw failure("ITEM_ATTRIBUTES_INVOCATION_OWNER_CHANGED",readable(source));
            itemAttributeDescriptor(Type.getType(source.descriptor()),sourceApi);
            itemAttributeDescriptor(Type.getType(recipe.target().descriptor()),targetApi);
        }
        String descriptor = remapDescriptor(source.descriptor());
        if (lifecycle) descriptor = dev.legacybridge.runtime.compat.logic.LifecycleContractAdapter.methodDescriptor(source.owner(), source.name(), source.descriptor(), descriptor);
        if (suiteProof != null) descriptor = dev.legacybridge.runtime.compat.logic.LifecycleSuiteAdapter.methodDescriptor(source.owner(), source.name(), source.descriptor(), descriptor);
        if (plainBlock) descriptor = dev.legacybridge.runtime.compat.logic.PlainBlockArtifactAdapter.registryDescriptor(source.owner(), source.name(), source.descriptor(), descriptor);
        if (!recipe.target().descriptor().equals(descriptor)) throw failure("LOGIC_DESCRIPTOR_ABI_CHANGED", readable(source));
        add(source, recipe.target(), recipe.contract(), "HIGH", genericStatus(source));
    }

    private String genericStatus(LegacySymbol symbol) {
        if (generated == null) return "NOT_REQUESTED";
        MappingRule rule = generated.resolve(symbol);
        return rule.status() + "/" + rule.confidence();
    }

    private void inspectDeclarations(ClassNode node) {
        OperationControl.checkpoint();
        classAnnotations(node, node.visibleAnnotations);
        classAnnotations(node, node.invisibleAnnotations);
        rejectOwnedAnnotations(node.visibleTypeAnnotations);
        rejectOwnedAnnotations(node.invisibleTypeAnnotations);
        if (node.module != null || node.recordComponents != null && !node.recordComponents.isEmpty()
                || node.permittedSubclasses != null && !node.permittedSubclasses.isEmpty() || node.attrs != null && !node.attrs.isEmpty()) {
            throw failure("LOGIC_UNSUPPORTED_CLASS_METADATA", node.name);
        }
        if (node.superName != null && !own.containsKey(node.superName) && !node.superName.equals("java/lang/Object")
                && !node.superName.equals(sourceNamespace.productionClass(ITEM))
                && !((horizontalBlock || staticShapeBlock || booleanToggleBlock) && node.superName.equals(sourceNamespace.productionClass(BLOCK)))
                && !(eventProof != null && node.name.equals(eventProof.event()) && node.superName.equals("net/minecraftforge/eventbus/api/Event"))) throw failure("LOGIC_UNSUPPORTED_SUPERCLASS", node.superName);
        for (String contract : node.interfaces) if (!own.containsKey(contract) && !Set.of(SUPPLIER, OPERATOR).contains(contract)) {
            throw failure("LOGIC_UNSUPPORTED_INTERFACE", contract);
        }
        // Walk complete trusted ancestry now; unavailable event/platform data cannot silently look like Object.
        externalAncestors(node.name, false, new HashSet<>());
        externalAncestors(node.name, true, new HashSet<>());
        for (FieldNode field : node.fields) {
            rejectOwnedAnnotations(field.visibleAnnotations);
            rejectOwnedAnnotations(field.invisibleAnnotations);
            rejectOwnedAnnotations(field.visibleTypeAnnotations);
            rejectOwnedAnnotations(field.invisibleTypeAnnotations);
            if (field.value instanceof ConstantDynamic || field.attrs != null && !field.attrs.isEmpty()) throw failure("LOGIC_UNSUPPORTED_FIELD_METADATA", node.name + "." + field.name);
            add(LegacySymbol.field(node.name, field.name, field.desc), TargetSymbol.field(node.name, field.name, remapDescriptor(field.desc)), "logic.owned-field.v1", "OWNED", "NONE");
        }
        for (MethodNode method : node.methods) {
            methodAnnotations(method, method.visibleAnnotations);
            methodAnnotations(method, method.invisibleAnnotations);
            rejectOwnedAnnotations(method.visibleTypeAnnotations);
            rejectOwnedAnnotations(method.invisibleTypeAnnotations);
            if (method.annotationDefault != null) throw failure("LOGIC_UNSUPPORTED_ANNOTATION", node.name + "." + method.name);
            if (method.visibleParameterAnnotations != null) for (var values : method.visibleParameterAnnotations) rejectOwnedAnnotations(values);
            if (method.invisibleParameterAnnotations != null) for (var values : method.invisibleParameterAnnotations) rejectOwnedAnnotations(values);
            rejectOwnedAnnotations(method.visibleLocalVariableAnnotations);
            rejectOwnedAnnotations(method.invisibleLocalVariableAnnotations);
            if ((method.access & (Opcodes.ACC_NATIVE | Opcodes.ACC_SYNCHRONIZED)) != 0 || method.attrs != null && !method.attrs.isEmpty()) {
                throw failure("LOGIC_UNSUPPORTED_METHOD", node.name + "." + method.name);
            }
            if (method.name.equals("<clinit>")) continue;
            LegacySymbol symbol = symbol(node.name, method.name, method.desc, false);
            Recipe override = null;
            if (!method.name.equals("<init>")) {
                if(ownedStackDamageProof!=null && node.name.equals(ownedStackDamageProof.item())
                        && (ownedStackRepairUseProof==null || method.name.equals(ownedStackDamageProof.helper()))) {
                    // An owned endpoint is not a native callback. Reject even otherwise-reviewed
                    // recipes (e.g. isFoil) when a renamed helper collides with native dispatch.
                    for(boolean target:List.of(false,true)) for(String ancestor:externalAncestors(node.name,target,new HashSet<>())) {
                        Declaration collision=declared(target?targetApi:sourceApi,ancestor,method.name,
                                target?remapDescriptor(method.desc):method.desc,false);
                        if(collision!=null&&(collision.member().access()&Opcodes.ACC_PRIVATE)==0)
                            throw failure("OWNED_STACK_DAMAGE_HELPER_COLLISION",node.name+"."+method.name+" inherits "+ancestor);
                    }
                }
                for (String ancestor : externalAncestors(node.name, false, new HashSet<>())) {
                    Declaration declaration = declared(sourceApi, ancestor, method.name, method.desc, false);
                    if (declaration == null || (declaration.member().access() & Opcodes.ACC_PRIVATE) != 0) continue;
                    Recipe candidate = recipes.get(LegacySymbol.method(ancestor, method.name, method.desc));
                    boolean protectedDefinition = candidate != null
                            && (horizontalBlock && candidate.contract().equals("horizontal.protected-definition-override.v1")
                            || booleanToggleBlock && candidate.contract().equals("boolean-toggle.protected-definition-override.v1"))
                            && (method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE)) == Opcodes.ACC_PROTECTED;
                    if (candidate == null || (declaration.member().access() & (Opcodes.ACC_FINAL | Opcodes.ACC_STATIC)) != 0
                            || (method.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) != 0
                            || (method.access & Opcodes.ACC_PUBLIC) == 0 && !protectedDefinition) {
                        throw failure("LOGIC_UNREVIEWED_EXTERNAL_OVERRIDE", node.name + "." + method.name + method.desc + " inherits " + ancestor);
                    }
                    if (override != null && !override.target().name().equals(candidate.target().name())) throw failure("LOGIC_AMBIGUOUS_OVERRIDE", readable(symbol));
                    approveExternal(candidate.source(), null);
                    Declaration target = declared(targetApi, candidate.target().owner(), candidate.target().name(), candidate.target().descriptor(), false);
                    if (target == null || (target.member().access() & (Opcodes.ACC_FINAL | Opcodes.ACC_STATIC)) != 0) throw failure("LOGIC_OVERRIDE_TARGET_CHANGED", readable(symbol));
                    override = candidate;
                }
            }
            if (staticShapeBlock && node.superName.equals(sourceNamespace.productionClass(BLOCK))
                    && !method.name.equals("<init>") && override == null)
                throw failure("STATIC_SHAPE_REQUIRED_OVERRIDE_MISSING", readable(symbol));
            if (booleanToggleBlock && node.superName.equals(sourceNamespace.productionClass(BLOCK))
                    && !method.name.equals("<init>") && override == null)
                throw failure("BOOLEAN_TOGGLE_REQUIRED_OVERRIDE_MISSING", readable(symbol));
            if((itemAttributes || itemAttributeDurability)&&node.superName.equals(sourceNamespace.productionClass(ITEM))
                    && !method.name.equals("<init>")&&override==null)
                throw failure("ITEM_ATTRIBUTES_REQUIRED_OVERRIDE_MISSING",readable(symbol));
            if(ownedStackRepairUseProof!=null && node.name.equals(ownedStackRepairUseProof.item())
                    && !method.name.equals("<init>") && !method.name.equals(ownedStackRepairUseProof.helper())
                    && (override==null || !override.contract().equals("owned-stack-repair-use.native-use-override.v1")))
                throw failure("OWNED_STACK_REPAIR_USE_REQUIRED_OVERRIDE_MISSING",readable(symbol));
            String targetName = override == null ? method.name : override.target().name();
            if (override == null && !method.name.equals("<init>")) {
                for (String ancestor : externalAncestors(node.name, true, new HashSet<>())) {
                    Declaration accidental = declared(targetApi, ancestor, targetName, remapDescriptor(method.desc), false);
                    if (accidental != null && (accidental.member().access() & Opcodes.ACC_PRIVATE) == 0) {
                        throw failure("LOGIC_ACCIDENTAL_TARGET_OVERRIDE", readable(symbol) + " collides with " + ancestor);
                    }
                }
            }
            add(symbol, new TargetSymbol(symbol.kind(), node.name, targetName, remapDescriptor(method.desc)),
                    override == null ? "logic.owned-method.v1" : override.contract() + ".owned-override", "OWNED", "NONE");
        }
    }

    private Set<String> externalAncestors(String owner, boolean target, Set<String> visiting) {
        if (!visiting.add(owner)) throw failure("LOGIC_HIERARCHY_CYCLE", owner);
        if (visiting.size() > 128) throw failure("LOGIC_HIERARCHY_LIMIT", owner);
        ClassNode node = own.get(owner);
        var result = new LinkedHashSet<String>();
        if (node != null) {
            var parents = new ArrayList<>(node.interfaces);
            if (node.superName != null) parents.add(node.superName);
            for (String parent : parents) {
                String mapped = target && !own.containsKey(parent) ? classes.getOrDefault(parent, parent) : parent;
                if (!own.containsKey(mapped)) result.add(mapped);
                result.addAll(externalAncestors(mapped, target, new HashSet<>(visiting)));
            }
        } else {
            ApiIndex.ApiClass type = owner.startsWith("java/") ? jdkClass(owner) : requireApiClass(target ? targetApi : sourceApi, owner);
            var parents = new ArrayList<>(type.interfaces());
            if (type.superName() != null) parents.add(type.superName());
            for (String parent : parents) {
                result.add(parent);
                result.addAll(externalAncestors(parent, target, new HashSet<>(visiting)));
            }
        }
        return result;
    }

    private void inspectInstructions(ClassNode node) {
        for (MethodNode method : node.methods) for (AbstractInsnNode instruction : method.instructions) {
            OperationControl.checkpoint();
            rejectOwnedAnnotations(instruction.visibleTypeAnnotations);
            rejectOwnedAnnotations(instruction.invisibleTypeAnnotations);
            if (plainBlockRecipe != null && plainBlockRecipe.consumesInstruction(instruction)) continue;
            if (instruction.getOpcode() == Opcodes.MONITORENTER || instruction.getOpcode() == Opcodes.MONITOREXIT
                    || instruction.getOpcode() == Opcodes.JSR || instruction.getOpcode() == Opcodes.RET) throw failure("LOGIC_UNSUPPORTED_OPCODE", node.name + "." + method.name);
            if (instruction instanceof MethodInsnNode call) {
                if (BOOTSTRAP_TYPES.contains(call.owner)) throw failure("LOGIC_BOOTSTRAP_ONLY_TYPE", call.owner);
                memberCall(call.owner, call.name, call.desc, false, call.getOpcode());
            } else if (instruction instanceof FieldInsnNode field) memberCall(field.owner, field.name, field.desc, true, field.getOpcode());
            else if (instruction instanceof TypeInsnNode type && BOOTSTRAP_TYPES.contains(type.desc)) throw failure("LOGIC_BOOTSTRAP_ONLY_TYPE", type.desc);
            else if (instruction instanceof LdcInsnNode constant && (constant.cst instanceof Handle || constant.cst instanceof ConstantDynamic
                    || constant.cst instanceof Type type && containsBootstrapType(type))) throw failure("LOGIC_UNSUPPORTED_DYNAMIC_CONSTANT", node.name);
            else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                Handle implementation = (Handle) dynamic.bsmArgs[1];
                memberCall(implementation.getOwner(), implementation.getName(), implementation.getDesc(), false, handleOpcode(implementation));
            }
        }
    }

    private void memberCall(String owner, String name, String desc, boolean field, int opcode) {
        LegacySymbol symbol = symbol(owner, name, desc, field);
        if (!own.containsKey(owner)) { approveExternal(symbol, opcode); return; }
        Declaration local = ownDeclaration(owner, name, desc, field, new HashSet<>());
        if (local == null) throw failure("LOGIC_UNRESOLVED_OWN_MEMBER", readable(symbol));
        boolean instructionStatic = opcode == Opcodes.INVOKESTATIC || opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC;
        if (instructionStatic != ((local.member().access() & Opcodes.ACC_STATIC) != 0)) throw failure("LOGIC_INVOCATION_KIND", readable(symbol));
        MappingRule declaration = approved.get(symbol(local.owner(), name, desc, field));
        if (declaration == null && !own.containsKey(local.owner())) {
            LegacySymbol inherited = symbol(local.owner(), name, desc, field);
            approveExternal(inherited, opcode);
            declaration = approved.get(inherited);
        }
        if (declaration == null) throw failure("LOGIC_UNRESOLVED_OWN_MEMBER", readable(symbol));
        add(symbol, new TargetSymbol(symbol.kind(), owner, declaration.target().name(), remapDescriptor(desc)),
                "logic.owned-dispatch-alias.v1", "OWNED", "NONE");
    }

    private Declaration ownDeclaration(String owner, String name, String desc, boolean field, Set<String> visiting) {
        if (!visiting.add(owner)) throw failure("LOGIC_HIERARCHY_CYCLE", owner);
        ClassNode node = own.get(owner);
        if (node == null) return externalDeclaration(sourceApi, owner, name, desc, field, new HashSet<>());
        if (field) for (FieldNode entry : node.fields) {
            if (entry.name.equals(name) && entry.desc.equals(desc)) return new Declaration(owner, new ApiIndex.ApiMember(name, desc, entry.access));
        }
        if (!field) for (MethodNode entry : node.methods) {
            if (entry.name.equals(name) && entry.desc.equals(desc)) return new Declaration(owner, new ApiIndex.ApiMember(name, desc, entry.access));
        }
        if (name.equals("<init>")) return null;
        if (node.superName != null) {
            Declaration parent = ownDeclaration(node.superName, name, desc, field, new HashSet<>(visiting));
            if (parent != null) return parent;
        }
        for (String contract : node.interfaces) {
            Declaration parent = ownDeclaration(contract, name, desc, field, new HashSet<>(visiting));
            if (parent != null) return parent;
        }
        return null;
    }

    private Declaration externalDeclaration(ApiIndex api, String owner, String name, String desc, boolean field, Set<String> visited) {
        if (!visited.add(owner)) return null;
        if (visited.size() > 512) throw failure("LOGIC_HIERARCHY_LIMIT", owner);
        Declaration local = declared(api, owner, name, desc, field);
        if (local != null || name.equals("<init>")) return local;
        ApiIndex.ApiClass type = owner.startsWith("java/") ? jdkClass(owner) : requireApiClass(api, owner);
        if (type.superName() != null) {
            Declaration parent = externalDeclaration(api, type.superName(), name, desc, field, visited);
            if (parent != null) return parent;
        }
        for (String contract : type.interfaces()) {
            Declaration parent = externalDeclaration(api, contract, name, desc, field, visited);
            if (parent != null) return parent;
        }
        return null;
    }

    private Declaration declared(ApiIndex api, String owner, String name, String desc, boolean field) {
        var type = owner.startsWith("java/") ? jdkClass(owner) : requireApiClass(api, owner);
        for (var member : field ? type.fields() : type.methods()) {
            if (member.name().equals(name) && member.descriptor().equals(desc)) return new Declaration(owner, member);
        }
        return null;
    }

    private static ApiIndex.ApiClass requireApiClass(ApiIndex api, String owner) {
        var type = api.getClass(owner);
        if (type == null) throw failure("LOGIC_MAPPING_DATA_REQUIRED", "API declaration absent: " + owner + "; explicitly prepare complete pinned API data (including Forge eventbus/FML), never infer declarations");
        return type;
    }

    private ApiIndex.ApiClass jdkClass(String owner) {
        ApiIndex.ApiClass cached = jdk.get(owner);
        if (cached != null) return cached;
        if (!owner.startsWith("java/")) throw failure("LOGIC_JDK_NAMESPACE", owner);
        try (InputStream input = ClassLoader.getPlatformClassLoader().getResourceAsStream(owner + ".class")) {
            if (input == null) throw failure("LOGIC_MAPPING_DATA_REQUIRED", "Java 17 declaration unavailable: " + owner);
            byte[] bytes = input.readNBytes(2 * 1024 * 1024 + 1);
            if (bytes.length > 2 * 1024 * 1024) throw failure("LOGIC_JDK_LIMIT", owner);
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            var type = new ApiIndex.ApiClass(node.name, node.access, node.superName, node.interfaces,
                    node.methods.stream().map(m -> new ApiIndex.ApiMember(m.name, m.desc, m.access)).toList(),
                    node.fields.stream().map(f -> new ApiIndex.ApiMember(f.name, f.desc, f.access)).toList());
            jdk.put(owner, type);
            return type;
        } catch (IOException invalid) { throw new TransformationException("LOGIC_MAPPING_DATA_REQUIRED: Cannot read Java declaration " + owner, invalid); }
    }

    private void inspectDynamicSites(ClassNode node) {
        for (MethodNode method : node.methods) for (AbstractInsnNode instruction : method.instructions) {
            if (!(instruction instanceof InvokeDynamicInsnNode dynamic)) continue;
            Handle bootstrap = dynamic.bsm;
            if (bootstrap.getTag() != Opcodes.H_INVOKESTATIC || bootstrap.isInterface() || !bootstrap.getOwner().equals(METAFACTORY)
                    || !bootstrap.getName().equals("metafactory") || !bootstrap.getDesc().equals(METAFACTORY_DESC)
                    || dynamic.bsmArgs.length != 3 || !(dynamic.bsmArgs[0] instanceof Type sam)
                    || sam.getSort() != Type.METHOD || !(dynamic.bsmArgs[1] instanceof Handle implementation)
                    || !(dynamic.bsmArgs[2] instanceof Type instantiated) || instantiated.getSort() != Type.METHOD
                    || !own.containsKey(implementation.getOwner())) throw failure("LOGIC_UNSUPPORTED_BOOTSTRAP", node.name + "." + method.name);
            Type callType = Type.getMethodType(dynamic.desc);
            Type result = callType.getReturnType();
            if (result.getSort() != Type.OBJECT) throw failure("LOGIC_UNSUPPORTED_LAMBDA", dynamic.desc);
            String contract = result.getInternalName();
            if (network) {
                if (!dev.legacybridge.converter.transformation.ReviewedNetworkLambda.accepts(contract, dynamic.name, sam, instantiated, callType, own.keySet())
                        || implementation.getTag() != Opcodes.H_INVOKESTATIC)
                    throw failure("NETWORK_UNREVIEWED_LAMBDA", dynamic.desc);
            } else if (contract.equals(SUPPLIER)) {
                if (!dynamic.name.equals("get") || !sam.getDescriptor().equals("()Ljava/lang/Object;")
                        || instantiated.getArgumentTypes().length != 0 || instantiated.getReturnType().getSort() != Type.OBJECT
                        || !(own.containsKey(instantiated.getReturnType().getInternalName())
                        || instantiated.getReturnType().getInternalName().equals(sourceNamespace.productionClass(ITEM))
                        || plainBlock && Set.of(sourceNamespace.productionClass(PlainBlockApiRecipe.BLOCK),
                                sourceNamespace.productionClass(PlainBlockApiRecipe.BLOCK_ITEM)).contains(instantiated.getReturnType().getInternalName()))) throw failure("LOGIC_UNSUPPORTED_LAMBDA", dynamic.desc);
                if (plainBlock && (implementation.getTag() != Opcodes.H_INVOKESTATIC || callType.getArgumentTypes().length != 0))
                    throw failure("PLAIN_BLOCK_UNSUPPORTED_LAMBDA", dynamic.desc);
            } else if (contract.equals(OPERATOR)) {
                if (!dynamic.name.equals("applyAsInt") || !sam.getDescriptor().equals("(I)I") || !instantiated.equals(sam)) throw failure("LOGIC_UNSUPPORTED_LAMBDA", dynamic.desc);
            } else if (suiteProof != null && dev.legacybridge.converter.conversion.LifecycleSuiteContract.accepts(contract, dynamic.name, sam, instantiated, callType)) {
                if (implementation.getTag() != Opcodes.H_INVOKESTATIC) throw failure("LIFECYCLE_SUITE_UNSUPPORTED_HANDLE", implementation.toString());
            } else if (serverConfigProof != null && dev.legacybridge.converter.conversion.ServerConfigContract.accepts(contract, dynamic.name, sam, instantiated, callType)) {
                if (implementation.getTag() != Opcodes.H_INVOKESTATIC) throw failure("SERVER_CONFIG_UNSUPPORTED_HANDLE", implementation.toString());
            } else if (clientConfigProof != null && dev.legacybridge.converter.conversion.ClientConfigContract.accepts(contract, dynamic.name, sam, instantiated, callType)) {
                if (implementation.getTag() != Opcodes.H_INVOKESTATIC) throw failure("CLIENT_CONFIG_UNSUPPORTED_HANDLE", implementation.toString());
            } else if (eventProof != null && dev.legacybridge.converter.conversion.EventPriorityContract.accepts(eventProof.event(), contract, dynamic.name, sam, instantiated, callType)) {
                if (implementation.getTag() != Opcodes.H_INVOKESTATIC) throw failure("EVENTS_UNSUPPORTED_HANDLE", implementation.toString());
            } else if (lifecycle && dev.legacybridge.converter.transformation.ReviewedLifecycleLambda.accepts(contract, dynamic.name, sam, instantiated, callType)) {
                if (implementation.getTag() != Opcodes.H_INVOKESTATIC) throw failure("LOGIC_UNSUPPORTED_LAMBDA_HANDLE", implementation.toString());
            } else {
                ClassNode ownInterface = own.get(contract);
                if (ownInterface == null || (ownInterface.access & Opcodes.ACC_INTERFACE) == 0
                        || !Set.of("(I)I", "(I)Z").contains(sam.getDescriptor()) || !sam.equals(instantiated)) throw failure("LOGIC_UNSUPPORTED_LAMBDA", dynamic.desc);
                List<MethodNode> abstractMethods = ownInterface.methods.stream().filter(m -> (m.access & Opcodes.ACC_ABSTRACT) != 0).toList();
                if (!ownInterface.interfaces.isEmpty() || abstractMethods.size() != 1 || !abstractMethods.get(0).name.equals(dynamic.name)
                        || !abstractMethods.get(0).desc.equals(sam.getDescriptor())) throw failure("LOGIC_UNSUPPORTED_LAMBDA", dynamic.desc);
            }
            Type implementationType = Type.getMethodType(implementation.getDesc());
            List<Type> expectedArguments = new ArrayList<>(List.of(callType.getArgumentTypes()));
            expectedArguments.addAll(List.of(instantiated.getArgumentTypes()));
            List<Type> actualArguments = new ArrayList<>();
            if (Set.of(Opcodes.H_INVOKEVIRTUAL, Opcodes.H_INVOKESPECIAL, Opcodes.H_INVOKEINTERFACE).contains(implementation.getTag())) {
                actualArguments.add(Type.getObjectType(implementation.getOwner()));
            } else if (implementation.getTag() != Opcodes.H_INVOKESTATIC && implementation.getTag() != Opcodes.H_NEWINVOKESPECIAL) {
                throw failure("LOGIC_UNSUPPORTED_LAMBDA_HANDLE", implementation.toString());
            }
            actualArguments.addAll(List.of(implementationType.getArgumentTypes()));
            Type actualReturn = implementation.getTag() == Opcodes.H_NEWINVOKESPECIAL ? Type.getObjectType(implementation.getOwner()) : implementationType.getReturnType();
            if (!expectedArguments.equals(actualArguments) || !reviewedLambdaReturn(actualReturn, instantiated.getReturnType())) throw failure("LOGIC_LAMBDA_ABI_CHANGED", dynamic.desc);
            if (implementation.getTag() == Opcodes.H_NEWINVOKESPECIAL) {
                ClassNode constructed = own.get(implementation.getOwner());
                if (!contract.equals(SUPPLIER) || !implementation.getName().equals("<init>") || !implementation.getDesc().equals("()V")
                        || callType.getArgumentTypes().length != 0 || implementation.isInterface()
                        || (constructed.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE)) != 0
                        || !externalAncestors(constructed.name, false, new HashSet<>()).contains(sourceNamespace.productionClass(ITEM))
                        || !externalAncestors(constructed.name, true, new HashSet<>()).contains(targetNamespace.productionClass(ITEM))) {
                    throw failure("LOGIC_UNSUPPORTED_LAMBDA_HANDLE", implementation.toString());
                }
            }
            lambdaClasses.add(node.name);
        }
    }

    private boolean reviewedLambdaReturn(Type actual, Type expected) {
        if (actual.equals(expected)) return true;
        if (actual.getSort() != Type.OBJECT || expected.getSort() != Type.OBJECT || !own.containsKey(actual.getInternalName())
                || !expected.getInternalName().equals(sourceNamespace.productionClass(ITEM))) return false;
        return externalAncestors(actual.getInternalName(), false, new HashSet<>()).contains(sourceNamespace.productionClass(ITEM))
                && externalAncestors(actual.getInternalName(), true, new HashSet<>()).contains(targetNamespace.productionClass(ITEM));
    }

    private void requireBootstrapContext(LegacyReferenceScanner.ExternalReference reference) {
        if (!lambdaClasses.contains(reference.sourceClass())) throw failure("LOGIC_BOOTSTRAP_ONLY_TYPE", reference.owner());
        if (reference.instructionIndex() < 0) {
            if (reference.kind() != LegacyReferenceScanner.ReferenceKind.CLASS) throw failure("LOGIC_BOOTSTRAP_ONLY_TYPE", reference.owner());
            return; // javac's InnerClasses attribute for MethodHandles.Lookup; not a loadable value.
        }
        ClassNode node = own.get(reference.sourceClass());
        MethodNode method = node.methods.stream().filter(m -> (m.name + m.desc).equals(reference.enclosingMethod())).findFirst().orElseThrow();
        if (!(method.instructions.get(reference.instructionIndex()) instanceof InvokeDynamicInsnNode)) throw failure("LOGIC_BOOTSTRAP_ONLY_TYPE", reference.owner());
    }

    private static boolean containsBootstrapType(Type type) {
        if (type.getSort() == Type.OBJECT) return BOOTSTRAP_TYPES.contains(type.getInternalName());
        if (type.getSort() == Type.ARRAY) return containsBootstrapType(type.getElementType());
        if (type.getSort() == Type.METHOD) {
            if (containsBootstrapType(type.getReturnType())) return true;
            for (Type argument : type.getArgumentTypes()) if (containsBootstrapType(argument)) return true;
        }
        return false;
    }

    private void rejectOwnedAnnotations(List<? extends AnnotationNode> annotations) {
        if (annotations == null) return;
        if (!annotations.isEmpty()) throw failure("LOGIC_UNSUPPORTED_ANNOTATION", "Only actual class-target @Mod / interface-target @FunctionalInterface annotations are reviewed");
    }

    private void classAnnotations(ClassNode node, List<? extends AnnotationNode> annotations) {
        if (annotations == null) return;
        for (AnnotationNode annotation : annotations) {
            String owner = Type.getType(annotation.desc).getInternalName();
            if (eventProof != null && node.name.equals(eventProof.event()) && owner.equals(dev.legacybridge.converter.conversion.EventPriorityContract.CANCELABLE)) continue;
            if (lifecycle && owner.equals(SUBSCRIBER)) continue; // Complete values/side/bus validation is mandatory in LifecycleProfileShape.
            if (!owner.equals(MOD) && !owner.equals("java/lang/FunctionalInterface")) {
                throw failure("LOGIC_UNSUPPORTED_ANNOTATION", owner);
            }
            if (owner.equals("java/lang/FunctionalInterface") && ((node.access & Opcodes.ACC_INTERFACE) == 0
                    || (node.access & Opcodes.ACC_ANNOTATION) != 0 || annotation.values != null && !annotation.values.isEmpty())) {
                throw failure("LOGIC_UNSUPPORTED_ANNOTATION", "FunctionalInterface requires an actual owned interface and no values");
            }
            if (owner.equals(MOD) && ((node.access & (Opcodes.ACC_INTERFACE | Opcodes.ACC_ANNOTATION | Opcodes.ACC_ENUM | Opcodes.ACC_ABSTRACT)) != 0
                    || annotation.values == null || annotation.values.size() != 2
                    || !annotation.values.get(0).equals("value") || !(annotation.values.get(1) instanceof String))) {
                throw failure("LOGIC_UNSUPPORTED_ANNOTATION", "Only exact @Mod(value=modId) is reviewed");
            }
        }
    }

    private void methodAnnotations(MethodNode method, List<? extends AnnotationNode> annotations) {
        if (lifecycle && annotations != null && annotations.stream().allMatch(a -> a.desc.equals("L" + SUBSCRIBE + ";"))) {
            if ((method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) != (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC))
                throw failure("LIFECYCLE_SUBSCRIBER_ACCESS", method.name);
            return;
        }
        rejectOwnedAnnotations(annotations);
    }

    private void add(LegacySymbol source, TargetSymbol target, String contract, String structuralConfidence, String genericCandidate) {
        MappingCategory category = !source.descriptor().equals(target.descriptor()) ? MappingCategory.DESCRIPTOR_CHANGE
                : !source.owner().equals(target.owner()) ? MappingCategory.PACKAGE_MOVE
                : !source.name().equals(target.name()) ? MappingCategory.PURE_RENAME : MappingCategory.IDENTITY;
        String adapter = plainBlock && contract.equals("plain-block.deferred-block-item-late-get.v1") ? AdapterCatalog.PLAIN_BLOCK
                : suiteProof != null && contract.equals("lifecycle.config-spec-identity.v1") ? AdapterCatalog.CONFIG_SPEC_IDENTITY : network ? AdapterCatalog.NETWORK : lifecycle && contract.startsWith("lifecycle.") ? AdapterCatalog.LIFECYCLE
                : food && contract.startsWith("food.") ? AdapterCatalog.FOOD : AdapterCatalog.LOGIC;
        var rule = new MappingRule(source, target, category, MappingConfidence.VERIFIED, MappingStatus.RESOLVED,
                List.of("Independent finite semantic contract: " + contract,
                        "Exact declaration/namespace composition; structural confidence=" + structuralConfidence + "; generic candidate=" + genericCandidate,
                        "Descriptor operation=" + adapter + "; original owned logic remains; no generic semantic promotion"));
        MappingRule old = approved.putIfAbsent(source, rule);
        if (old != null && !old.target().equals(target)) throw failure("LOGIC_CONFLICTING_RULE", readable(source));
        bindings.put(source, adapter);
        review.putIfAbsent(source, Map.of("source", readable(source), "target", target.kind() + " " + target.owner() + "." + target.name() + target.descriptor(),
                "contract", contract, "structuralConfidence", structuralConfidence,
                "generatedConfidence", generated == null ? "NOT_REQUESTED" : generated.resolve(source).confidence().name(), "genericCandidate", genericCandidate,
                "confidence", MappingConfidence.VERIFIED.name(), "adapter", adapter, "reason", "Exact contract approval for the bounded own-logic profile only"));
    }

    private static LegacySymbol symbol(String owner, String name, String descriptor, boolean field) {
        return field ? LegacySymbol.field(owner, name, descriptor) : name.equals("<init>") ? LegacySymbol.constructor(owner, descriptor) : LegacySymbol.method(owner, name, descriptor);
    }
    private static byte[] bytes(ClassNode node) { ClassWriter writer = new ClassWriter(0); node.accept(writer); return writer.toByteArray(); }
    private static String readable(LegacySymbol symbol) { return symbol.kind() + " " + symbol.owner() + "." + symbol.name() + symbol.descriptor(); }
    private static int handleOpcode(Handle handle) {
        return switch (handle.getTag()) {
            case Opcodes.H_INVOKESTATIC -> Opcodes.INVOKESTATIC;
            case Opcodes.H_INVOKEINTERFACE -> Opcodes.INVOKEINTERFACE;
            case Opcodes.H_INVOKEVIRTUAL -> Opcodes.INVOKEVIRTUAL;
            case Opcodes.H_INVOKESPECIAL, Opcodes.H_NEWINVOKESPECIAL -> Opcodes.INVOKESPECIAL;
            default -> throw failure("LOGIC_UNSUPPORTED_LAMBDA_HANDLE", handle.toString());
        };
    }
    private static TransformationException failure(String code, String message) { return new TransformationException(code + ": " + message); }
}
