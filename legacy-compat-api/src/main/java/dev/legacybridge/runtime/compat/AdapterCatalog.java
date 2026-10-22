package dev.legacybridge.runtime.compat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Shared, game-class-free contract. Entries are checked against real compiled implementations in the build. */
public final class AdapterCatalog {
    public static final String BOOLEAN_TOGGLE_BLOCK_MANIFEST="block.boolean-toggle-manifest.v1";
    public static final String BOOLEAN_TOGGLE_BLOCK_BINDINGS="block.boolean-toggle-bindings.v1";
    public static final String STATIC_SHAPE_PROPERTIES="block.static-half-stone-properties.v1";
    public static final String STATIC_SHAPE_MANIFEST="block.static-shape-manifest.v1";
    public static final String STATIC_SHAPE_BINDINGS="block.static-shape-bindings.v1";
    public static final String HORIZONTAL_BLOCK_MANIFEST="block.horizontal-manifest.v1";
    public static final String HORIZONTAL_BLOCK_BINDINGS="block.horizontal-bindings.v1";
    public static final String PLAIN_BLOCK="block.native-registration.v1";
    public static final String PLAIN_BLOCK_PROPERTIES="block.stone-properties-recipe.v1";
    public static final String PLAIN_BLOCK_MANIFEST="block.plain-manifest.v1";
    public static final String PLAIN_BLOCK_BINDINGS="block.plain-bindings.v1";
    public static final int SCHEMA_VERSION=1;
    public static final String RUNTIME_VERSION="0.1.1-SNAPSHOT";
    public static final String BOOTSTRAP="registry.simple-content.v1";
    public static final String ITEMS="registry.item-properties.v1";
    public static final String BLOCK_ITEMS="registry.block-item.v1";
    public static final String SOUNDS="registry.sound-event.v1";
    public static final String EVENTS="event.mod-bus.v1";
    public static final String BLOCKS="world.stone-properties.v1";
    public static final String MINING="world.stone-mining.v1";
    public static final String TABS="client.creative-tabs.v1";
    public static final String METADATA="registry.mod-metadata.v1";
    public static final String MANIFEST="registry.mod-manifest.v1";
    public static final String PACK="client.resource-pack.v1";
    public static final String LOGIC="logic.symbol-contracts.v1";
    public static final String LOGIC_MANIFEST="logic.mod-manifest.v1";
    public static final String LOGIC_BINDINGS="logic.adapter-bindings.v1";
    public static final String LIFECYCLE="logic.lifecycle-contracts.v1";
    public static final String LIFECYCLE_MANIFEST="logic.lifecycle-manifest.v1";
    public static final String LIFECYCLE_BINDINGS="logic.lifecycle-bindings.v1";
    public static final String FOOD="item.food-properties.v1";
    public static final String FOOD_MANIFEST="item.food-manifest.v1";
    public static final String FOOD_BINDINGS="item.food-bindings.v1";
    public static final String IRON_HELMET_EMPTY_SLOT="item.iron-helmet-empty-slot.v1";
    public static final String IRON_HELMET_HEAD_EQUIP_EFFECTS="item.iron-helmet-head-equip-effects.v1";
    public static final String IRON_HELMET_DISPENSER="item.iron-helmet-dispenser.v1";
    public static final String NETWORK="network.snapshot-contracts.v1";
    public static final String NETWORK_MANIFEST="network.snapshot-manifest.v1";
    public static final String NETWORK_BINDINGS="network.snapshot-bindings.v1";
    public static final String COMPOSE_MANIFEST="composition.mod-manifest.v1";
    public static final String COMPOSE_BINDINGS="composition.adapter-bindings.v1";
    public static final String CONSTRUCTOR_MANIFEST="logic.constructor-manifest.v1";
    public static final String CONSTRUCTOR_BINDINGS="logic.constructor-bindings.v1";
    public static final String HIERARCHY_MANIFEST="logic.hierarchy-manifest.v1";
    public static final String HIERARCHY_BINDINGS="logic.hierarchy-bindings.v1";
    public static final String LIFECYCLE_SUITE_MANIFEST="logic.lifecycle-suite-manifest.v1";
    public static final String LIFECYCLE_SUITE_BINDINGS="logic.lifecycle-suite-bindings.v1";
    public static final String CONFIG_SPEC_IDENTITY="logic.config-spec-identity.v1";
    public static final String STACK_REPAIR_USE_MANIFEST="item.owned-stack-repair-use-manifest.v1";
    public static final String STACK_REPAIR_USE_BINDINGS="item.owned-stack-repair-use-bindings.v1";
    public static final String STACK_DAMAGE_MANIFEST="item.owned-stack-damage-manifest.v1";
    public static final String STACK_DAMAGE_BINDINGS="item.owned-stack-damage-bindings.v1";
    public static final String ATTRIBUTE_DURABILITY_MANIFEST="item.attribute-durability-manifest.v1";
    public static final String ATTRIBUTE_DURABILITY_BINDINGS="item.attribute-durability-bindings.v1";
    public static final String ATTRIBUTE_MANIFEST="item.attributes-manifest.v1";
    public static final String ATTRIBUTE_BINDINGS="item.attributes-bindings.v1";
    public static final String DURABLE_MANIFEST="item.durable-manifest.v1";
    public static final String DURABLE_BINDINGS="item.durable-bindings.v1";
    public static final String SERVER_CONFIG_MANIFEST="logic.server-config-manifest.v1";
    public static final String SERVER_CONFIG_BINDINGS="logic.server-config-bindings.v1";
    public static final String CLIENT_CONFIG_MANIFEST="logic.client-config-manifest.v1";
    public static final String CLIENT_CONFIG_BINDINGS="logic.client-config-bindings.v1";
    public static final String EVENT_MANIFEST="logic.event-manifest.v1";
    public static final String EVENT_BINDINGS="logic.event-bindings.v1";
    public static final String RECEIVER_MANIFEST="logic.receiver-manifest.v1";
    public static final String RECEIVER_BINDINGS="logic.receiver-bindings.v1";
    public static final String INTERFACE_MANIFEST="logic.interface-manifest.v1";
    public static final String INTERFACE_BINDINGS="logic.interface-bindings.v1";
    public static final String COHORT_TOML="cohort.finite-metadata.v1";
    public static final String COHORT_MANIFEST="cohort.finite-manifest.v1";
    public static final String COHORT_BINDINGS="cohort.finite-bindings.v1";
    private static final String P="dev.legacybridge.runtime.compat.";
    private static final String PLAN="Ldev/legacybridge/runtime/SimpleContentPlan$";
    private static final List<String> REAL=List.of("fixtures/simple-content-1.16.5");
    private static final List<String> SYNTHETIC=List.of("StaticContentExtractorTest.extractsItemBlockBlockItemAndSimpleSoundWithoutLoadingAnySourceClass");
    private static final Map<String,AdapterDescriptor> ADAPTERS=index(List.of(
        entry(IRON_HELMET_DISPENSER,"item.LegacyIronHelmetDispenser","dispense","(Lnet/minecraft/core/BlockSource;Lnet/minecraft/world/item/ItemStack;)Z","RUNTIME",
            "Fixed iron HEAD ArmorItem.dispenseArmor with native player or zombie", "Native facing-block query, split(1), guarded player HEAD effect and silent Mob armor write",
            "Keep the first eligible entity. Reject unknown receiver, non-HEAD slot or zombie list layout before split(1); equip the returned stack, then zombie drop chance 2 and persistence.",
            "False means no eligible result only, not unsupported fallback. Player sound precedes write; Zombie has no added sound or target verify/onEquipItem/GameEvent. Native exceptions after split propagate without fabricated rollback.",
            "Unused by conversion or registration. Nonempty final LegacyIronHelmetItem, exact ServerPlayer or Zombie only; no unknown-first skipping. Native query/copy hooks, sound events, threads, fallback dispenser, other mobs, rendering and native/multiplayer qualification remain separate.",
            List.of("fixtures/iron-helmet-1.16.5","fixtures/iron-helmet-dispenser-1.16.5")),
        entry(IRON_HELMET_HEAD_EQUIP_EFFECTS,"item.LegacyIronHelmetHeadEquipEffects","equip","(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)V","RUNTIME",
            "Fixed iron HEAD PlayerEntity.setItemSlot and inherited playEquipSound", "Guarded native player sound, inventory armor list and HEAD index",
            "For exact ServerPlayer or physically guarded exact LocalPlayer/RemotePlayer, play the iron material sound before writing the same received stack reference to HEAD. Empty stacks write without sound.",
            "No target verifyEquippedItem/onEquipItem/GameEvent or first-tick/silent/spectator/side gate. Virtual native sound dispatch remains; legacy and target server/client sound-event contracts differ.",
            "Unused by conversion: no recipe, profile or Apply. HEAD only; empty or one final LegacyIronHelmetItem; unknown receiver subclasses rejected before effects. Copy ownership, thread and transformed-platform/listener cohort proof remain caller gates; dispenser, Equipable and native/multiplayer qualification remain separate.",
            List.of("fixtures/iron-helmet-1.16.5")),
        entry(IRON_HELMET_EMPTY_SLOT,"item.LegacyIronHelmetItem","create","()Ldev/legacybridge/runtime/compat/item/LegacyIronHelmetItem;","RUNTIME",
            "Native IRON/HEAD ArmorItem with legacy empty-slot-only use", "Fixed final IRON/HELMET ArmorItem and exact received-hand empty-slot override",
            "Allocate the fixed runtime item; use equips a copy only into the resolved empty slot, clears the original hand stack and returns that same stack.",
            "Retains legacy writes on both logical sides; no target occupied-slot swap, Binding Curse/stack-match guards or direct ITEM_USED award. Target equipment hooks remain native.",
            "Unused by conversion: no contextual constructor recipe, public profile or Apply authority. Inherited Equipable paths, dispenser, sound/rendering, wear and native/multiplayer qualification remain separate.",
            List.of("fixtures/iron-helmet-1.16.5")),
        entry(STACK_REPAIR_USE_MANIFEST,"logic.OwnedStackRepairUseArtifactAdapter","targetManifest","(Ljava/lang/String;)[B","OFFLINE",
            "Guarded same-hand Item.use repair","Private preserved item-use candidate","Generate distinct private item-use provenance.",
            "Client SUCCESS is prediction; server calls the helper once. Native creative restoration is unchanged.",
            "No generic use, costs, synchronization or native qualification.",List.of("fixtures/owned-stack-repair-use-1.16.5")),
        entry(STACK_REPAIR_USE_BINDINGS,"logic.OwnedStackRepairUseArtifactAdapter","bindingTree","()Ljava/util/Map;","OFFLINE",
            "Exact use callback and guarded helper","Exact offline operation bindings","Serialize schema-1 item-use bindings.",
            "No platform rewriting, thread rescheduling or hook bypass.",
            "No publication, installation or implicit composition.",List.of("fixtures/owned-stack-repair-use-1.16.5")),
        entry(STACK_DAMAGE_MANIFEST,"logic.OwnedStackDamageArtifactAdapter","targetManifest","(Ljava/lang/String;)[B","OFFLINE",
            "Guarded own-stack damage-minus-one helper","Private preserved stack helper candidate","Generate distinct private candidate provenance.",
            "Original guards and native hook dispatch preserved; no raw NBT or generated gameplay invocation.",
            "No wear, break, synchronization, thread authority or native qualification.",List.of("fixtures/owned-stack-damage-1.16.5")),
        entry(STACK_DAMAGE_BINDINGS,"logic.OwnedStackDamageArtifactAdapter","bindingTree","()Ljava/util/Map;","OFFLINE",
            "Finite helper and native durability constructor","Exact offline operation bindings","Serialize schema-1 guarded stack helper bindings.",
            "No native hook bypass or behavior emulation.",
            "No publication, installation or implicit feature composition.",List.of("fixtures/owned-stack-damage-1.16.5")),
        entry(ATTRIBUTE_DURABILITY_MANIFEST,"logic.ItemAttributeDurabilityArtifactAdapter","targetManifest","(Ljava/lang/String;)[B","OFFLINE",
            "Fixed modifiers plus durability and iron repair predicate","Private composed item candidate","Generate distinct composed candidate provenance.",
            "Original bodies, UUID identities and native dispatch preserved; no synthesized wear.",
            "No full anvil, breakage, equipment or multiplayer qualification.",List.of("fixtures/item-attribute-durability-1.16.5")),
        entry(ATTRIBUTE_DURABILITY_BINDINGS,"logic.ItemAttributeDurabilityArtifactAdapter","bindingTree","()Ljava/util/Map;","OFFLINE",
            "Finite two-callback owned item","Exact offline operation bindings","Serialize schema-1 composed bindings.",
            "No item behavior emulation or third-party library bundling.",
            "No publication or installation authority.",List.of("fixtures/item-attribute-durability-1.16.5")),
        entry(ATTRIBUTE_MANIFEST,"logic.ItemAttributeArtifactAdapter","targetManifest","(Ljava/lang/String;)[B","OFFLINE",
            "Finite owned native attribute override","Private owned-attribute candidate","Generate distinct candidate provenance.",
            "Bodies and UUID object references preserved; target-native item hooks.",
            "No native equipment, combat, tooltip or multiplayer qualification.",List.of("fixtures/item-attributes-1.16.5")),
        entry(ATTRIBUTE_BINDINGS,"logic.ItemAttributeArtifactAdapter","bindingTree","()Ljava/util/Map;","OFFLINE",
            "Reviewed immutable MAINHAND modifiers","Exact offline operation bindings","Serialize schema-1 bindings.",
            "No item behavior emulation or third-party library bundling.",
            "No publication or installation authority.",List.of("fixtures/item-attributes-1.16.5")),
        entry(DURABLE_MANIFEST,"logic.DurableItemArtifactAdapter","targetManifest","(Ljava/lang/String;)[B","OFFLINE",
            "Finite durability and repair predicate","Private durable item candidate","Generate candidate provenance.","Bodies preserved; native target item hooks.",
            "No tool, armor, anvil or runtime qualification.",List.of("fixtures/durable-item-1.16.5")),
        entry(DURABLE_BINDINGS,"logic.DurableItemArtifactAdapter","bindingTree","()Ljava/util/Map;","OFFLINE",
            "Reviewed constructor and virtual override","Exact offline operation bindings","Serialize schema-1 bindings.","No repair emulation.",
            "No publication or installation authority.",List.of("fixtures/durable-item-1.16.5")),
        entry(LIFECYCLE_SUITE_MANIFEST,"logic.LifecycleSuiteAdapter","targetManifest","(Ljava/lang/String;)[B","OFFLINE",
            "Finite integrated lifecycle graph","Private lifecycle-suite candidate","Generate provenance only.","Retain original bodies.",
            "No runtime qualification or install authority.",List.of("fixtures/lifecycle-suite-1.16.5")),
        entry(LIFECYCLE_SUITE_BINDINGS,"logic.LifecycleSuiteAdapter","bindingTree","()Ljava/util/Map;","OFFLINE",
            "Reviewed reload, events and commands","Exact offline operation bindings","Serialize schema-1 bindings.","No event emulation.",
            "Not arbitrary lifecycle composition.",List.of("fixtures/lifecycle-suite-1.16.5")),
        entry(CONFIG_SPEC_IDENTITY,"logic.LifecycleSuiteAdapter","methodDescriptor","(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;","OFFLINE",
            "ModConfig.getSpec():ForgeConfigSpec","ModConfig.getSpec():IConfigSpec","Rewrite exact return descriptor after whole-graph identity-filter proof.",
            "Interface return only, same spec identity.","No dereference, cast, global type substitution or method handle.",List.of("fixtures/lifecycle-suite-1.16.5")),
        entry(SERVER_CONFIG_MANIFEST,"logic.ServerConfigArtifactAdapter","targetManifest","(Ljava/lang/String;)[B","OFFLINE",
            "World-scoped SERVER spec and started/stopped snapshot", "Distinct server-config-candidate-v1 identity",
            "Generate private static-only config candidate provenance.", "No emulation or body replacement.",
            "No native proof or public installation authority.",List.of("fixtures/server-config-1.16.5")),
        entry(SERVER_CONFIG_BINDINGS,"logic.ServerConfigArtifactAdapter","bindingTree","()Ljava/util/Map;","OFFLINE",
            "Native SERVER defaults, validation and logical-server phases", "Schema-1 implemented offline operations",
            "Bind retained bodies and generated metadata.", "Native world config loading; no client snapshot or new sync.",
            "No config events, reload handling or failed-start equivalence.",List.of("fixtures/server-config-1.16.5")),
        entry(CLIENT_CONFIG_MANIFEST,"logic.ClientConfigArtifactAdapter","targetManifest","(Ljava/lang/String;)[B","OFFLINE",
            "Guarded CLIENT spec and queued startup read", "Distinct client-config-candidate-v1 identity",
            "Generate private static-only config candidate provenance.", "No emulation or body replacement.",
            "No native proof or public installation authority.",List.of("fixtures/client-config-1.16.5")),
        entry(CLIENT_CONFIG_BINDINGS,"logic.ClientConfigArtifactAdapter","bindingTree","()Ljava/util/Map;","OFFLINE",
            "Exact physical-side guard, defaults, validation and phase", "Schema-1 implemented offline operations",
            "Bind retained bodies and generated metadata.", "Native CLIENT loading and enqueueWork preserved.",
            "No reload handling, SERVER config or synchronization.",List.of("fixtures/client-config-1.16.5")),
        entry(EVENT_MANIFEST,"logic.EventArtifactAdapter","targetManifest","(Ljava/lang/String;)[B","OFFLINE",
            "Owned cancelable event and explicit listeners", "Distinct event-priority-candidate-v1 identity",
            "Generate private static-only event candidate provenance.", "No event emulation or body replacement.",
            "No native proof or public installation authority.",List.of("fixtures/event-priority-1.16.5")),
        entry(EVENT_BINDINGS,"logic.EventArtifactAdapter","bindingTree","()Ljava/util/Map;","OFFLINE",
            "Exact event bus, priority, cancellation and setup contracts", "Schema-1 real offline operations",
            "Bind retained bodies and generated metadata.", "Native dispatch remains on the original bus and queue.",
            "Only two finite HIGH/LOW listeners; no MONITOR or arbitrary events.",List.of("fixtures/event-priority-1.16.5")),
        entry(RECEIVER_MANIFEST,"logic.ReceiverArtifactAdapter","targetManifest","(Ljava/lang/String;)[B","OFFLINE",
            "Proved owned this-bound virtual method and Forge 36 metadata", "Distinct owned-receiver-candidate-v1 identity",
            "Generate private static-only receiver provenance.", "No body replacement or runtime shim.",
            "No publication or installation authority.",List.of("fixtures/owned-receiver-1.16.5")),
        entry(RECEIVER_BINDINGS,"logic.ReceiverArtifactAdapter","bindingTree","()Ljava/util/Map;","OFFLINE",
            "Exact receiver/capture/implementation contracts", "Schema-1 implemented offline bindings",
            "Bind preserved bodies and receiver candidate metadata operations.", "Provenance is not native qualification.",
            "One this-bound IntUnaryOperator in a final helper; no general handles.",List.of("fixtures/owned-receiver-1.16.5")),
        entry(INTERFACE_MANIFEST,"logic.InterfaceArtifactAdapter","targetManifest","(Ljava/lang/String;)[B","OFFLINE",
            "Proved owned interface/default path and Forge 36 metadata", "Distinct owned-interface-candidate-v1 identity",
            "Generate private static-only interface provenance.", "No executable body or runtime shim inserted.",
            "No public profile or installation authority.",List.of("fixtures/owned-interface-1.16.5")),
        entry(INTERFACE_BINDINGS,"logic.InterfaceArtifactAdapter","bindingTree","()Ljava/util/Map;","OFFLINE",
            "Reviewed owned interface/default and exact symbol contracts", "Schema-1 implemented offline bindings",
            "Bind preserved bodies and implemented interface-candidate metadata operations.", "Provenance is not runtime qualification.",
            "One flat interface and final helper; native comparison pending.",List.of("fixtures/owned-interface-1.16.5")),
        entry(HIERARCHY_MANIFEST,"logic.HierarchyArtifactAdapter","targetManifest","(Ljava/lang/String;)[B","OFFLINE",
            "Proven finite owned Item hierarchy and Forge 36 metadata", "Distinct owned-hierarchy-candidate-v1 identity",
            "Serialize private candidate provenance without changing dispatch or bodies.",
            "Build provenance replaced; no runtime acceptance implied.",
            "No public profile or installation authority.",List.of("fixtures/owned-hierarchy-1.16.5")),
        entry(HIERARCHY_BINDINGS,"logic.HierarchyArtifactAdapter","bindingTree","()Ljava/util/Map;","OFFLINE",
            "Reviewed owned hierarchy and exact symbol contracts", "Schema-1 implemented offline bindings",
            "Bind preserved-body remapping and hierarchy-candidate metadata operations.",
            "Inert provenance only; no gameplay shim or inferred API equivalence.",
            "One abstract base and final leaf; native comparisons pending.",List.of("fixtures/owned-hierarchy-1.16.5")),
        entry(CONSTRUCTOR_MANIFEST,"logic.ConstructorArtifactAdapter","targetManifest","(Ljava/lang/String;)[B","OFFLINE",
            "Validated Forge 36 manifest and finite owned constructor chain", "Distinct owned-constructors-candidate-v1 identity",
            "Serialize preserved version and private candidate provenance without rewriting constructor bodies.",
            "Build provenance replaced, no runtime qualification implied.",
            "No public profile or install authority; native comparison pending.",List.of("fixtures/owned-constructors-1.16.5")),
        entry(CONSTRUCTOR_BINDINGS,"logic.ConstructorArtifactAdapter","bindingTree","()Ljava/util/Map;","OFFLINE",
            "Proven owned-body and integer delegation contracts", "Schema-1 implemented offline bindings",
            "Bind exact symbol remapping and distinct constructor-candidate metadata operations.",
            "Inert provenance only, no gameplay shim or new external API equivalence.",
            "Finite private candidate only; no constructor interpretation or dropped methods.",List.of("fixtures/owned-constructors-1.16.5")),
        entry(COHORT_TOML,"registry.CohortMetadataAdapter","targetToml","(Ljava/lang/String;[B)[B","OFFLINE",
            "Hash-pinned authored Forge 36 library and consumer mods.toml",
            "Forge 47 metadata retaining modIds, versions and mandatory BOTH library dependency",
            "Rewrite exact platform ranges and add the required compatible runtime without discarding dependencies.",
            "Target-only loading; runtime dependency added. No class or gameplay changes.",
            "Finite private pair only; not general metadata migration or installation approval.",List.of("fixtures/dependency-cohort-1.16.5")),
        entry(COHORT_MANIFEST,"registry.CohortMetadataAdapter","targetManifest","(Ljava/lang/String;[B)[B","OFFLINE",
            "Hash-pinned inert authored cohort manifests","Distinct finite-cohort-pair-v1 static candidate manifest",
            "Preserve original headers and append explicit candidate identity and runtime version.",
            "Build provenance advertises a candidate, never runtime qualification.",
            "No services, multi-release, signed archives or arbitrary input headers.",List.of("fixtures/dependency-cohort-1.16.5")),
        entry(COHORT_BINDINGS,"registry.CohortMetadataAdapter","bindingTree","()Ljava/util/Map;","OFFLINE",
            "Reviewed finite pair metadata recipe","Schema-1 implemented offline adapter bindings",
            "Bind the three real cohort metadata operations to the unchanged runtime ABI.",
            "Adds conversion provenance, not a runtime shim or installation authority.",
            "Does not authorize unknown classes, dependencies or public profile promotion.",List.of("fixtures/dependency-cohort-1.16.5")),
        entry(COMPOSE_MANIFEST,"logic.ComposedArtifactAdapter","targetManifest","(Ljava/lang/String;)[B","OFFLINE",
            "Validated inert Forge 36 manifest", "Distinct composed-logic-recipes-v1 static candidate identity",
            "Serialize the preserved version, composition revision and explicit non-qualified marker.",
            "Build provenance is replaced; no existing profile identity is impersonated.",
            "Private candidates only; no publication, installation or runtime qualification.",List.of("fixtures/composed-logic-recipes-1.16.5")),
        entry(COMPOSE_BINDINGS,"logic.ComposedArtifactAdapter","bindingTree","()Ljava/util/Map;","OFFLINE",
            "Independently proven owned-body logic and recipe operations", "Schema-1 exact implemented adapter references",
            "Serialize only the implemented logic and composed metadata bindings.",
            "Inert provenance does not prove runtime behavior or extend the runtime capability catalog.",
            "No generic composition, dependency migration or empty shim.",List.of("fixtures/composed-logic-recipes-1.16.5")),
        entry(NETWORK,"networking.SnapshotContractAdapter","remapDescriptor","(Ljava/lang/String;Ljava/util/Map;)Ljava/lang/String;","OFFLINE",
            "Reviewed Forge 36 explicit S2C SimpleChannel registration, PacketBuffer and queued Context",
            "Native Forge 47 SimpleChannel, FriendlyByteBuf and queued Context",
            "Compose only exact reviewed components, retaining the authored codec and session rejection bodies.",
            "Target dispatch may begin on Netty; explicit enqueueWork is retained. Unknown IDs emit Forge ERROR, not a promised disconnect.",
            "Reviewed network-snapshot-v1 authored method recipes only; no C2S state mutation or cross-version connections.",List.of("fixtures/network-snapshot-1.16.5")),
        entry(NETWORK_MANIFEST,"networking.SnapshotContractAdapter","targetManifest","(Ljava/lang/String;)[B","OFFLINE",
            "Validated inert manifest", "Explicit network-snapshot-v1 marker",
            "Serialize preserved version and profile marker.", "Provenance, not multiplayer certification.",
            "No active metadata.",List.of("fixtures/network-snapshot-1.16.5")),
        entry(NETWORK_BINDINGS,"networking.SnapshotContractAdapter","bindingTree","()Ljava/util/Map;","OFFLINE",
            "Reviewed snapshot operation set", "Schema-1 implemented adapter identities",
            "Serialize exact implemented bindings.", "Inert provenance only.",
            "No placeholder networking adapter.",List.of("fixtures/network-snapshot-1.16.5")),
        entry(FOOD,"logic.FoodContractAdapter","remapDescriptor","(Ljava/lang/String;Ljava/util/Map;)Ljava/lang/String;","OFFLINE",
            "Forge 36 Food.Builder literal nutrition/saturation/build and Item.Properties.food", "Forge 47 FoodProperties.Builder and Item.Properties.food",
            "Remap only exact independently verified components; preserve original constructor and native consumption.",
            "Target native eating/use adds EAT/ITEM_INTERACT_START/ITEM_INTERACT_FINISH events and entity-sensitive queries; no identical world reaction is promised.",
            "food-item-v1 only: no effects, flags, containers/remainders, custom use, tools or blocks.",List.of("fixtures/food-item-1.16.5")),
        entry(FOOD_MANIFEST,"logic.FoodContractAdapter","targetManifest","(Ljava/lang/String;)\u005bB","OFFLINE",
            "Validated inert manifest", "Explicit food-item-v1 marker",
            "Serialize preserved version and profile marker.", "Provenance only; no runtime certification.",
            "No active metadata.",List.of("fixtures/food-item-1.16.5")),
        entry(FOOD_BINDINGS,"logic.FoodContractAdapter","bindingTree","()Ljava/util/Map;","OFFLINE",
            "Reviewed food operation set", "Schema-1 implemented adapter identities",
            "Serialize only exact implemented bindings.", "Inert provenance only.",
            "No placeholder adapters or gameplay emulation.",List.of("fixtures/food-item-1.16.5")),
        entry(LIFECYCLE,"logic.LifecycleContractAdapter","methodDescriptor","(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;","OFFLINE",
            "Exact RegistryObject.get erasure and ForgeConfigSpec registration declarations", "Native target Object return and IConfigSpec parameter declarations",
            "Rewrite only independently verified call-site descriptors, preserving the caller cast and native scheduling.",
            "No global IForgeRegistryEntry erasure; native lifecycle/config behavior requires the separate admission proof.",
            "Finite lifecycle-preserving-v1 slices only; no arbitrary event families or unknown external effects.",List.of("fixtures/lifecycle-content-1.16.5")),
        entry(LIFECYCLE_MANIFEST,"logic.LifecycleContractAdapter","targetManifest","(Ljava/lang/String;)\u005bB","OFFLINE",
            "Validated inert legacy manifest", "Explicit lifecycle-preserving-v1 manifest marker",
            "Serialize the opt-in profile marker and preserved version.", "Adds provenance, not runtime certification.",
            "No active or unknown manifest headers.",List.of("fixtures/lifecycle-content-1.16.5")),
        entry(LIFECYCLE_BINDINGS,"logic.LifecycleContractAdapter","bindingTree","()Ljava/util/Map;","OFFLINE",
            "Verified lifecycle operation set", "Schema-1 adapter identity tree",
            "Serialize only implemented exact bindings.", "Inert metadata only.",
            "No runtime shim placeholders.",List.of("fixtures/lifecycle-content-1.16.5")),
        entry(LOGIC_BINDINGS,"logic.PreservedLogicAdapter","bindingTree","()Ljava/util/Map;","OFFLINE",
            "Reviewed legacy profile operations after all static gates", "Versioned exact adapter identities for the retained-logic artifact",
            "Build the exact profile-bound adapter declaration tree for canonical JSON serialization.",
            "Adds inert conversion provenance; does not execute or certify original behavior.",
            "logic-preserving-v1 fixed operations only; no runtime adapter placeholders or dynamic catalog discovery.",List.of("fixtures/logic-preservation-1.16.5")),
        entry(BOOLEAN_TOGGLE_BLOCK_MANIFEST,"logic.BooleanToggleBlockArtifactAdapter","targetManifest","(Ljava/lang/String;)[B","OFFLINE",
            "Validated legacy boolean-state interaction mod","Private boolean-toggle candidate manifest",
            "Write exact private candidate identity after state/side/ownership proof.",
            "Inert provenance does not certify server writes or native behavior.",
            "Only finite local boolean toggle; no public export/install.",List.of("fixtures/boolean-toggle-block-1.16.5")),
        entry(BOOLEAN_TOGGLE_BLOCK_BINDINGS,"logic.BooleanToggleBlockArtifactAdapter","bindingTree","()Ljava/util/Map;","OFFLINE",
            "No conversion provenance","Exact boolean-toggle operation bindings",
            "Record implemented property, member and metadata operation identities.",
            "No bus/thread rescheduling or fake world implementation.",
            "Independent two-class contract; not arbitrary interactions or composition.",List.of("fixtures/boolean-toggle-block-1.16.5")),
        entry(STATIC_SHAPE_PROPERTIES,"logic.StaticShapeBlockArtifactAdapter","stonePropertiesCalls","()Ljava/util/List;","OFFLINE",
            "STONE properties of exact bottom-half box Block","Native explicit color/instrument and geometry-derived solidity",
            "Resolve and emit the five native property operations only after complete fixed-geometry proof.",
            "Material removed; half-cuboid defaults requalified independently. Mining tag and native placement event remain explicit.",
            "Only box(0,0,0,16,8,16), constant outline/collision, finite strength and default predicates; not arbitrary geometry.",List.of("fixtures/static-shape-block-1.16.5")),
        entry(STATIC_SHAPE_MANIFEST,"logic.StaticShapeBlockArtifactAdapter","targetManifest","(Ljava/lang/String;)[B","OFFLINE",
            "Validated source manifest","Private fixed-shape candidate manifest","Write distinct static candidate identity.",
            "Inert provenance, not native qualification.","No public output, install or generalized shape support.",List.of("fixtures/static-shape-block-1.16.5")),
        entry(STATIC_SHAPE_BINDINGS,"logic.StaticShapeBlockArtifactAdapter","bindingTree","()Ljava/util/Map;","OFFLINE",
            "No conversion provenance","Fixed-shape operation bindings","Record exact implemented operation identities.",
            "Not an install receipt or runtime shim.","Independent two-class bottom-half contract only.",List.of("fixtures/static-shape-block-1.16.5")),
        entry(HORIZONTAL_BLOCK_MANIFEST,"logic.HorizontalBlockArtifactAdapter","targetManifest","(Ljava/lang/String;)[B","OFFLINE",
            "Validated source manifest","Private horizontal-block candidate manifest","Write finite candidate identity and qualification.",
            "Inert metadata, not native acceptance.","No export, installation or general block callbacks.",List.of("fixtures/horizontal-block-1.16.5")),
        entry(HORIZONTAL_BLOCK_BINDINGS,"logic.HorizontalBlockArtifactAdapter","bindingTree","()Ljava/util/Map;","OFFLINE",
            "No conversion provenance","Versioned horizontal candidate bindings","Record exact implemented operations.",
            "Not a runtime shim or install receipt.","One property and reviewed native overrides only.",List.of("fixtures/horizontal-block-1.16.5")),
        entry(PLAIN_BLOCK,"logic.PlainBlockArtifactAdapter","registryDescriptor","(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;","OFFLINE",
            "Deferred BlockItem supplier RegistryObject.get returning IForgeRegistryEntry", "Target RegistryObject.get returning Object",
            "Change only the independently proved late lookup return erasure; retain Block cast and native constructors.",
            "Registry scheduling is target native and scoped to blocks before items.","No early lookup, custom registries or foreign blocks.",List.of("fixtures/plain-block-1.16.5")),
        entry(PLAIN_BLOCK_PROPERTIES,"logic.PlainBlockArtifactAdapter","stonePropertiesCalls","()Ljava/util/List;","OFFLINE",
            "AbstractBlock.Properties.of(Material.STONE)", "BlockBehaviour.Properties.of().mapColor(STONE).instrument(BASEDRUM)",
            "Supply the five canonical native operations for the exact consumed material/of pair, independently resolved by the engine.",
            "Material is removed, not renamed; additive mining tag is separate. Target placement emits BLOCK_PLACE.",
            "Full cube, default predicates and finite strength only; no escaping material/properties.",List.of("fixtures/plain-block-1.16.5")),
        entry(PLAIN_BLOCK_MANIFEST,"logic.PlainBlockArtifactAdapter","targetManifest","(Ljava/lang/String;)[B","OFFLINE",
            "Validated inert manifest", "Private plain-block candidate manifest", "Write a distinct private candidate marker.",
            "Not a public conversion profile or install receipt.","No export or native behavior qualification.",List.of("fixtures/plain-block-1.16.5")),
        entry(PLAIN_BLOCK_BINDINGS,"logic.PlainBlockArtifactAdapter","bindingTree","()Ljava/util/Map;","OFFLINE",
            "No conversion provenance", "Exact versioned operation bindings", "Record the implemented finite operations.",
            "Inert provenance only.","No arbitrary adapters or public install authority.",List.of("fixtures/plain-block-1.16.5")),
        entry(LOGIC,"logic.PreservedLogicAdapter","remapDescriptor","(Ljava/lang/String;Ljava/util/Map;)Ljava/lang/String;","OFFLINE",
            "Exact reviewed Item.isFoil, ItemStack.getCount, item properties and finite DeferredRegister contracts",
            "Exact target declarations plus preserved mod-owned bodies, helpers, hierarchy and initialization",
            "Compose only verified object descriptor components and apply independently approved exact class/member recipes.",
            "Registration naming is target-native; no generic call-body emulation, initializer execution or HIGH-to-VERIFIED promotion.",
            "logic-preserving-v1 only; changed RegistryObject.get erasure, unknown external calls and unreviewed overrides abort.",List.of("fixtures/logic-preservation-1.16.5")),
        entry(LOGIC_MANIFEST,"logic.PreservedLogicAdapter","targetManifest","(Ljava/lang/String;)\u005bB","OFFLINE",
            "Validated single-mod inert legacy manifest", "Version-bound manifest declaring logic-preserving-v1",
            "Serialize preserved version and explicit logic-preserving profile marker after source hook/signature rejection.",
            "Build headers are omitted; the retained mod classes are not a generated simple-content bootstrap.",
            "No arbitrary manifest fields, signed archives, services or loader hooks.",List.of("fixtures/logic-preservation-1.16.5")),
        entry(BOOTSTRAP,"registry.SimpleContentAdapter","register","(Ljava/lang/String;Ljava/lang/ClassLoader;Ljava/lang/String;)V","RUNTIME",
            "Forge 36 @Mod constructor, DeferredRegister and fml.RegistryObject",
            "Forge 47 @Mod constructor and native DeferredRegister/registries.RegistryObject",
            "Replace only fully interpreted registration bytecode with a mod-scoped plan and a concrete bootstrap.",
            "The old classes/static handles are not retained; target registries own newly created native values.",
            "Finite registration grammar only; no arbitrary callers, custom subclasses or custom registries.",REAL),
        entry(ITEMS,"registry.ItemPropertiesAdapter","create","("+PLAN+"ItemProperties;)Lnet/minecraft/world/item/Item$Properties;","RUNTIME",
            "net.minecraft.item.Item.Properties.stacksTo(int)","net.minecraft.world.item.Item.Properties.stacksTo(int)",
            "Construct target-native basic item properties from proven values.","No creative group is stored on target Item.Properties; the creative adapter handles membership.",
            "COMMON, non-damageable, non-fire-resistant items only; stack size 1..64.",REAL),
        entry(BLOCK_ITEMS,"registry.BlockItemAdapter","create","(Lnet/minecraft/world/level/block/Block;"+PLAN+"ItemProperties;)Lnet/minecraft/world/item/BlockItem;","RUNTIME",
            "net.minecraft.item.BlockItem(Block, Item.Properties)","net.minecraft.world.item.BlockItem(Block, Item.Properties)",
            "Resolve the proven local block after its registry is populated and create a native BlockItem.",
            "RegistryObject owner/lifecycle are target-native; legacy handles cannot escape the finite plan.",
            "No foreign blocks, custom BlockItem subclass or override behavior.",REAL),
        entry(SOUNDS,"registry.SoundEventAdapter","create","(Ljava/lang/String;)Lnet/minecraft/sounds/SoundEvent;","RUNTIME",
            "net.minecraft.util.SoundEvent(ResourceLocation)","SoundEvent.createVariableRangeEvent(ResourceLocation)",
            "Create an equivalent variable-range event for a validated local sound alias.",
            "Sound assets are resolved separately; no packet or playback-call emulation is supplied.",
            "Motivated by an existing synthetic fixture only; registered-event gameplay is not live-verified. No Ogg/file sounds.",SYNTHETIC),
        entry(EVENTS,"event.ModEventBusAdapter","requireContext","(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraftforge/eventbus/api/IEventBus;","RUNTIME",
            "FMLJavaModLoadingContext.get().getModEventBus() in the source mod constructor",
            "Forge 47 active ModLoadingContext and FMLJavaModLoadingContext mod bus",
            "Check active mod identity/version before attaching native deferred registrations to its own bus.",
            "Legacy RegistryEvent objects are not forwarded or simulated.","Only constructor-context registration; no general event translation.",REAL),
        entry(BLOCKS,"world.BlockPropertiesAdapter","create","("+PLAN+"BlockProperties;)Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;","RUNTIME",
            "AbstractBlock.Properties.of(Material.STONE).strength(float,float)",
            "BlockBehaviour.Properties.of().mapColor(STONE).strength(...).instrument(BASEDRUM)",
            "Reconstruct audited plain STONE properties using target-native flags.",
            "Material was removed; tool behavior is supplied separately by the stone-mining adapter.",
            "STONE and STONE sound only; no custom state, noOcclusion or tool-required properties, material overrides or behavior-changing vanilla tags.",REAL),
        entry(MINING,"world.BlockMiningAdapter","targetTag","(Ljava/lang/String;Ljava/util/List;)Ljava/util/Map;","OFFLINE",
            "PickaxeItem.getDestroySpeed uses Material.STONE in Minecraft 1.16.5",
            "DiggerItem/PickaxeItem consult minecraft:mineable/pickaxe in Minecraft 1.20.1",
            "Generate an additive target pickaxe-mining block tag for the proven STONE declarations.",
            "Selection changes from a Material predicate to data-driven tags; source assets/tags are not rewritten.",
            "Plain STONE only, no harvest-level/custom tool or external behavior-changing tags.",REAL),
        entry(TABS,"client.CreativeTabAdapter","attach","(Lnet/minecraftforge/eventbus/api/IEventBus;Ljava/util/Map;)V","RUNTIME",
            "Item.Properties.tab(ItemGroup.TAB_MISC or TAB_BUILDING_BLOCKS)",
            "BuildCreativeModeTabContentsEvent with INGREDIENTS or BUILDING_BLOCKS",
            "Attach declared items to the target tab population event.",
            "MISC to INGREDIENTS is an explicit category migration, not identical tab identity, icon or ordering.",
            "Only the two fixture categories; no custom tabs, searches, ordering guarantees or arbitrary legacy client callbacks.",REAL),
        entry(METADATA,"registry.ModMetadataAdapter","targetRanges","(Ljava/lang/String;Ljava/lang/String;)Ljava/util/Map;","OFFLINE",
            "Forge 36 javafml loaderVersion and Minecraft 1.16.5 dependency ranges",
            "Forge 47 javafml, Minecraft 1.20.1, required compatible LegacyBridge runtime",
            "Validate the source baseline and return exact target dependency ranges.",
            "The generated mod now requires the matching runtime and no longer advertises old-platform loading.",
            "Only already validated single-mod metadata; does not port third-party dependencies.",REAL),
        entry(MANIFEST,"registry.ModMetadataAdapter","targetManifest","(Ljava/lang/String;)\u005bB","OFFLINE",
            "Source Implementation-Version and inert allowlisted manifest metadata",
            "Target JAR Manifest-Version, preserved Implementation-Version and simple-content-v1 marker",
            "Serialize a target manifest with safe line folding after source manifest hooks/signatures have been rejected.",
            "Non-functional build provenance headers are not carried forward; mod version is preserved.",
            "No signed archives, services, launcher hooks or multi-release behavior.",REAL),
        entry(PACK,"client.ResourcePackAdapter","targetFormat","(I)I","OFFLINE",
            "Minecraft 1.16.5 pack.mcmeta pack_format 6","Minecraft 1.20.1 pack.mcmeta pack_format 15",
            "Upgrade only the already validated finite resource pack format while preserving its description.",
            "Version advertisement changes; this method never proves arbitrary resources compatible.",
            "Caller must run ContentResourceValidator first. No codecs, custom models, shaders or animation conversion.",REAL)
    ));
    private AdapterCatalog(){}
    private static AdapterDescriptor entry(String id,String cls,String method,String desc,String phase,String legacy,String target,String change,String differences,String limitations,List<String> fixtures){
        return new AdapterDescriptor(id,1,P+cls,method,desc,phase,legacy,target,change,differences,limitations,fixtures);
    }
    private static Map<String,AdapterDescriptor> index(List<AdapterDescriptor> values){
        Map<String,AdapterDescriptor> result=new TreeMap<>();
        for(var value:values) if(result.putIfAbsent(value.id(),value)!=null) throw new IllegalStateException("Duplicate adapter");
        return java.util.Collections.unmodifiableMap(result);
    }
    public static List<AdapterDescriptor> all(){return List.copyOf(ADAPTERS.values());}
    public static Optional<AdapterDescriptor> find(String id){return Optional.ofNullable(ADAPTERS.get(id));}
    public static AdapterDescriptor require(String id){return find(id).orElseThrow(()->new UnsupportedCompatibilityException("No implemented adapter: "+id));}
    /** Entirely unimplemented adapter areas; other areas still have strictly profile-specific limitations. */
    public static List<String> unsupportedAreas(){return List.of("entity","capability","rendering");}
}
