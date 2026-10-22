package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.conversion.composition.CompositionPlan;
import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.transformation.TransformationException;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import java.util.*;

/** Complete immutable byte ownership, not an executable caller-supplied plan or install receipt. */
final class CompositionArtifactPlan {
    record Entry(String path, Optional<String> before, String after, String operation, Optional<String> adapter) {
        Map<String, Object> toTree() {
            Map<String, Object> result = new TreeMap<>();
            result.put("path", path); result.put("sourceSha256", before.orElse(null)); result.put("targetSha256", after);
            result.put("operation", operation); result.put("adapter", adapter.map(AdapterCatalog::require).map(a -> a.referenceTree()).orElse(null));
            result.put("disposition", before.isEmpty() ? "GENERATE" : before.get().equals(after) ? "PRESERVE_BYTES" : "REWRITE");
            return SimpleContentConverter.canonicalTree(result);
        }
    }
    private final List<Entry> entries;
    private final CompositionPlan base;
    private final Map<String, String> metadataRules;
    private CompositionArtifactPlan(List<Entry> entries, CompositionPlan base, Map<String, String> metadataRules) {
        this.entries = List.copyOf(entries); this.base = base; this.metadataRules = Map.copyOf(metadataRules);
    }

    static Map<String, String> metadataRules(String modId) {
        return metadataRules(modId, false);
    }
    static Map<String, String> metadataRules(String modId, boolean constructors) {
        return metadataRules(modId, constructors, false);
    }
    static Map<String, String> metadataRules(String modId, boolean constructors, boolean hierarchy) {
        return metadataRules(modId, constructors, hierarchy, false);
    }
    static Map<String, String> metadataRules(String modId, boolean constructors, boolean hierarchy, boolean defaults) {
        return metadataRules(modId, constructors, hierarchy, defaults, false);
    }
    static Map<String,String> metadataRules(String modId, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers) {
        return metadataRules(modId, constructors, hierarchy, defaults, receivers, false);
    }
    static Map<String,String> metadataRules(String modId, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events) {
        return metadataRules(modId, constructors, hierarchy, defaults, receivers, events, false);
    }
    static Map<String,String> metadataRules(String modId, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events, boolean clientConfig) {
        return metadataRules(modId, constructors, hierarchy, defaults, receivers, events, clientConfig, false);
    }
    static Map<String,String> metadataRules(String modId, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events, boolean clientConfig, boolean serverConfig) {
        return metadataRules(modId, constructors, hierarchy, defaults, receivers, events, clientConfig, serverConfig, false);
    }
    static Map<String,String> metadataRules(String modId, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events, boolean clientConfig, boolean serverConfig, boolean suite) {
        return metadataRules(modId, constructors, hierarchy, defaults, receivers, events, clientConfig, serverConfig, suite, false);
    }
    static Map<String,String> metadataRules(String modId, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events, boolean clientConfig, boolean serverConfig, boolean suite, boolean durable) {
        return metadataRules(modId, constructors, hierarchy, defaults, receivers, events, clientConfig, serverConfig, suite, durable, false);
    }
    static Map<String,String> metadataRules(String modId, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events, boolean clientConfig, boolean serverConfig, boolean suite, boolean durable, boolean plainBlock) {
        return metadataRules(modId,constructors,hierarchy,defaults,receivers,events,clientConfig,serverConfig,suite,durable,plainBlock,false);
    }
    static Map<String,String> metadataRules(String modId, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events, boolean clientConfig, boolean serverConfig, boolean suite, boolean durable, boolean plainBlock, boolean horizontalBlock) {
        return metadataRules(modId,constructors,hierarchy,defaults,receivers,events,clientConfig,serverConfig,suite,durable,plainBlock,horizontalBlock,false);
    }
    static Map<String,String> metadataRules(String modId, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events, boolean clientConfig, boolean serverConfig, boolean suite, boolean durable, boolean plainBlock, boolean horizontalBlock, boolean staticShapeBlock) {
        return metadataRules(modId,constructors,hierarchy,defaults,receivers,events,clientConfig,serverConfig,suite,durable,plainBlock,horizontalBlock,staticShapeBlock,false);
    }
    static Map<String,String> metadataRules(String modId, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events, boolean clientConfig, boolean serverConfig, boolean suite, boolean durable, boolean plainBlock, boolean horizontalBlock, boolean staticShapeBlock, boolean booleanToggleBlock) {
        return metadataRules(modId,constructors,hierarchy,defaults,receivers,events,clientConfig,serverConfig,suite,durable,plainBlock,horizontalBlock,staticShapeBlock,booleanToggleBlock,false);
    }
    static Map<String,String> metadataRules(String modId, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events, boolean clientConfig, boolean serverConfig, boolean suite, boolean durable, boolean plainBlock, boolean horizontalBlock, boolean staticShapeBlock, boolean booleanToggleBlock, boolean itemAttributes) {
        return metadataRules(modId,constructors,hierarchy,defaults,receivers,events,clientConfig,serverConfig,suite,durable,plainBlock,horizontalBlock,staticShapeBlock,booleanToggleBlock,itemAttributes,false);
    }
    static Map<String,String> metadataRules(String modId, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events, boolean clientConfig, boolean serverConfig, boolean suite, boolean durable, boolean plainBlock, boolean horizontalBlock, boolean staticShapeBlock, boolean booleanToggleBlock, boolean itemAttributes, boolean itemAttributeDurability) {
        return metadataRules(modId,constructors,hierarchy,defaults,receivers,events,clientConfig,serverConfig,suite,durable,plainBlock,horizontalBlock,staticShapeBlock,booleanToggleBlock,itemAttributes,itemAttributeDurability,false);
    }
    static Map<String,String> metadataRules(String modId, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events, boolean clientConfig, boolean serverConfig, boolean suite, boolean durable, boolean plainBlock, boolean horizontalBlock, boolean staticShapeBlock, boolean booleanToggleBlock, boolean itemAttributes, boolean itemAttributeDurability, boolean ownedStackDamage) {
        return metadataRules(modId,constructors,hierarchy,defaults,receivers,events,clientConfig,serverConfig,suite,durable,plainBlock,horizontalBlock,staticShapeBlock,booleanToggleBlock,itemAttributes,itemAttributeDurability,ownedStackDamage,false);
    }
    static Map<String,String> metadataRules(String modId, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events, boolean clientConfig, boolean serverConfig, boolean suite, boolean durable, boolean plainBlock, boolean horizontalBlock, boolean staticShapeBlock, boolean booleanToggleBlock, boolean itemAttributes, boolean itemAttributeDurability, boolean ownedStackDamage, boolean ownedStackRepairUse) {
        require((ownedStackRepairUse ? 1 : 0) + (ownedStackDamage ? 1 : 0) + (itemAttributeDurability ? 1 : 0) + (itemAttributes ? 1 : 0) + (booleanToggleBlock ? 1 : 0) + (staticShapeBlock ? 1 : 0) + (horizontalBlock ? 1 : 0) + (plainBlock ? 1 : 0) + (constructors ? 1 : 0) + (hierarchy ? 1 : 0) + (defaults ? 1 : 0) + (receivers ? 1 : 0) + (events ? 1 : 0) + (clientConfig ? 1 : 0) + (serverConfig ? 1 : 0) + (suite ? 1 : 0) + (durable ? 1 : 0) <= 1, "Independent candidate contracts cannot combine implicitly");
        var rules = new TreeMap<>(Map.of("META-INF/mods.toml", AdapterCatalog.METADATA, "pack.mcmeta", AdapterCatalog.PACK,
                "META-INF/MANIFEST.MF", ownedStackRepairUse ? AdapterCatalog.STACK_REPAIR_USE_MANIFEST : ownedStackDamage ? AdapterCatalog.STACK_DAMAGE_MANIFEST : itemAttributeDurability ? AdapterCatalog.ATTRIBUTE_DURABILITY_MANIFEST : itemAttributes ? AdapterCatalog.ATTRIBUTE_MANIFEST : booleanToggleBlock ? AdapterCatalog.BOOLEAN_TOGGLE_BLOCK_MANIFEST : staticShapeBlock ? AdapterCatalog.STATIC_SHAPE_MANIFEST : horizontalBlock ? AdapterCatalog.HORIZONTAL_BLOCK_MANIFEST : plainBlock ? AdapterCatalog.PLAIN_BLOCK_MANIFEST : durable ? AdapterCatalog.DURABLE_MANIFEST : suite ? AdapterCatalog.LIFECYCLE_SUITE_MANIFEST : serverConfig ? AdapterCatalog.SERVER_CONFIG_MANIFEST : clientConfig ? AdapterCatalog.CLIENT_CONFIG_MANIFEST : events ? AdapterCatalog.EVENT_MANIFEST : receivers ? AdapterCatalog.RECEIVER_MANIFEST : defaults ? AdapterCatalog.INTERFACE_MANIFEST : hierarchy ? AdapterCatalog.HIERARCHY_MANIFEST : constructors ? AdapterCatalog.CONSTRUCTOR_MANIFEST : AdapterCatalog.COMPOSE_MANIFEST,
                "META-INF/legacybridge/" + modId + "/adapters.json", ownedStackRepairUse ? AdapterCatalog.STACK_REPAIR_USE_BINDINGS : ownedStackDamage ? AdapterCatalog.STACK_DAMAGE_BINDINGS : itemAttributeDurability ? AdapterCatalog.ATTRIBUTE_DURABILITY_BINDINGS : itemAttributes ? AdapterCatalog.ATTRIBUTE_BINDINGS : booleanToggleBlock ? AdapterCatalog.BOOLEAN_TOGGLE_BLOCK_BINDINGS : staticShapeBlock ? AdapterCatalog.STATIC_SHAPE_BINDINGS : horizontalBlock ? AdapterCatalog.HORIZONTAL_BLOCK_BINDINGS : plainBlock ? AdapterCatalog.PLAIN_BLOCK_BINDINGS : durable ? AdapterCatalog.DURABLE_BINDINGS : suite ? AdapterCatalog.LIFECYCLE_SUITE_BINDINGS : serverConfig ? AdapterCatalog.SERVER_CONFIG_BINDINGS : clientConfig ? AdapterCatalog.CLIENT_CONFIG_BINDINGS : events ? AdapterCatalog.EVENT_BINDINGS : receivers ? AdapterCatalog.RECEIVER_BINDINGS : defaults ? AdapterCatalog.INTERFACE_BINDINGS : hierarchy ? AdapterCatalog.HIERARCHY_BINDINGS : constructors ? AdapterCatalog.CONSTRUCTOR_BINDINGS : AdapterCatalog.COMPOSE_BINDINGS));
        if(plainBlock || horizontalBlock || staticShapeBlock || booleanToggleBlock)rules.put(dev.legacybridge.runtime.compat.world.BlockMiningAdapter.TARGET_PATH,AdapterCatalog.MINING);
        return Map.copyOf(rules);
    }

    static CompositionArtifactPlan resolve(Map<String, byte[]> source, Map<String, byte[]> target,
                                           CompositionPlan base, String modId) {
        return resolve(source, target, base, modId, false);
    }
    static CompositionArtifactPlan resolve(Map<String, byte[]> source, Map<String, byte[]> target,
                                           CompositionPlan base, String modId, boolean constructors) {
        return resolve(source, target, base, modId, constructors, false);
    }
    static CompositionArtifactPlan resolve(Map<String, byte[]> source, Map<String, byte[]> target,
                                           CompositionPlan base, String modId, boolean constructors, boolean hierarchy) {
        return resolve(source, target, base, modId, constructors, hierarchy, false);
    }
    static CompositionArtifactPlan resolve(Map<String, byte[]> source, Map<String, byte[]> target,
                                           CompositionPlan base, String modId, boolean constructors, boolean hierarchy, boolean defaults) {
        return resolve(source, target, base, modId, constructors, hierarchy, defaults, false);
    }
    static CompositionArtifactPlan resolve(Map<String,byte[]> source, Map<String,byte[]> target,
                                           CompositionPlan base, String modId, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers) {
        return resolve(source, target, base, modId, constructors, hierarchy, defaults, receivers, false);
    }
    static CompositionArtifactPlan resolve(Map<String,byte[]> source, Map<String,byte[]> target,
                                           CompositionPlan base, String modId, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events) {
        return resolve(source, target, base, modId, constructors, hierarchy, defaults, receivers, events, false);
    }
    static CompositionArtifactPlan resolve(Map<String,byte[]> source, Map<String,byte[]> target,
                                           CompositionPlan base, String modId, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events, boolean clientConfig) {
        return resolve(source, target, base, modId, constructors, hierarchy, defaults, receivers, events, clientConfig, false);
    }
    static CompositionArtifactPlan resolve(Map<String,byte[]> source, Map<String,byte[]> target,
                                           CompositionPlan base, String modId, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events, boolean clientConfig, boolean serverConfig) {
        return resolve(source, target, base, modId, constructors, hierarchy, defaults, receivers, events, clientConfig, serverConfig, false);
    }
    static CompositionArtifactPlan resolve(Map<String,byte[]> source, Map<String,byte[]> target,
                                           CompositionPlan base, String modId, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events, boolean clientConfig, boolean serverConfig, boolean suite) {
        return resolve(source, target, base, modId, constructors, hierarchy, defaults, receivers, events, clientConfig, serverConfig, suite, false);
    }
    static CompositionArtifactPlan resolve(Map<String,byte[]> source, Map<String,byte[]> target,
                                           CompositionPlan base, String modId, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events, boolean clientConfig, boolean serverConfig, boolean suite, boolean durable) {
        return resolve(source,target,base,modId,constructors,hierarchy,defaults,receivers,events,clientConfig,serverConfig,suite,durable,false);
    }
    static CompositionArtifactPlan resolve(Map<String,byte[]> source, Map<String,byte[]> target,
            CompositionPlan base, String modId, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events, boolean clientConfig, boolean serverConfig, boolean suite, boolean durable, boolean plainBlock) {
        return resolve(source,target,base,modId,constructors,hierarchy,defaults,receivers,events,clientConfig,serverConfig,suite,durable,plainBlock,false);
    }
    static CompositionArtifactPlan resolve(Map<String,byte[]> source, Map<String,byte[]> target, CompositionPlan base,
            String modId, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events,
            boolean clientConfig, boolean serverConfig, boolean suite, boolean durable, boolean plainBlock, boolean horizontalBlock) {
        return resolve(source,target,base,modId,constructors,hierarchy,defaults,receivers,events,clientConfig,serverConfig,suite,durable,plainBlock,horizontalBlock,false);
    }
    static CompositionArtifactPlan resolve(Map<String,byte[]> source, Map<String,byte[]> target, CompositionPlan base,
            String modId, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events,
            boolean clientConfig, boolean serverConfig, boolean suite, boolean durable, boolean plainBlock, boolean horizontalBlock, boolean staticShapeBlock) {
        return resolve(source,target,base,modId,constructors,hierarchy,defaults,receivers,events,clientConfig,serverConfig,suite,durable,plainBlock,horizontalBlock,staticShapeBlock,false);
    }
    static CompositionArtifactPlan resolve(Map<String,byte[]> source, Map<String,byte[]> target, CompositionPlan base,
            String modId, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events,
            boolean clientConfig, boolean serverConfig, boolean suite, boolean durable, boolean plainBlock, boolean horizontalBlock, boolean staticShapeBlock, boolean booleanToggleBlock) {
        return resolve(source,target,base,modId,constructors,hierarchy,defaults,receivers,events,clientConfig,serverConfig,suite,durable,plainBlock,horizontalBlock,staticShapeBlock,booleanToggleBlock,false);
    }
    static CompositionArtifactPlan resolve(Map<String,byte[]> source, Map<String,byte[]> target, CompositionPlan base,
            String modId, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events,
            boolean clientConfig, boolean serverConfig, boolean suite, boolean durable, boolean plainBlock, boolean horizontalBlock, boolean staticShapeBlock, boolean booleanToggleBlock, boolean itemAttributes) {
        return resolve(source,target,base,modId,constructors,hierarchy,defaults,receivers,events,clientConfig,serverConfig,suite,durable,plainBlock,horizontalBlock,staticShapeBlock,booleanToggleBlock,itemAttributes,false);
    }
    static CompositionArtifactPlan resolve(Map<String,byte[]> source, Map<String,byte[]> target, CompositionPlan base,
            String modId, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events,
            boolean clientConfig, boolean serverConfig, boolean suite, boolean durable, boolean plainBlock, boolean horizontalBlock, boolean staticShapeBlock, boolean booleanToggleBlock, boolean itemAttributes, boolean itemAttributeDurability) {
        return resolve(source,target,base,modId,constructors,hierarchy,defaults,receivers,events,clientConfig,serverConfig,suite,durable,plainBlock,horizontalBlock,staticShapeBlock,booleanToggleBlock,itemAttributes,itemAttributeDurability,false);
    }
    static CompositionArtifactPlan resolve(Map<String,byte[]> source, Map<String,byte[]> target, CompositionPlan base,
            String modId, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events,
            boolean clientConfig, boolean serverConfig, boolean suite, boolean durable, boolean plainBlock, boolean horizontalBlock, boolean staticShapeBlock, boolean booleanToggleBlock, boolean itemAttributes, boolean itemAttributeDurability, boolean ownedStackDamage) {
        return resolve(source,target,base,modId,constructors,hierarchy,defaults,receivers,events,clientConfig,serverConfig,suite,durable,plainBlock,horizontalBlock,staticShapeBlock,booleanToggleBlock,itemAttributes,itemAttributeDurability,ownedStackDamage,false);
    }
    static CompositionArtifactPlan resolve(Map<String,byte[]> source, Map<String,byte[]> target, CompositionPlan base,
            String modId, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events,
            boolean clientConfig, boolean serverConfig, boolean suite, boolean durable, boolean plainBlock, boolean horizontalBlock, boolean staticShapeBlock, boolean booleanToggleBlock, boolean itemAttributes, boolean itemAttributeDurability, boolean ownedStackDamage, boolean ownedStackRepairUse) {
        require(target.size() <= 4096 && target.keySet().containsAll(source.keySet()), "Entry bound or source loss");
        require(base.operations().size() == 2
                && base.operations().get(0).id().equals("logic.owned-bodies")
                && base.operations().get(0).kind() == CompositionPlan.Kind.PRESERVE_OWNED_BODIES
                && base.operations().get(1).id().equals("data.recipe-resources")
                && base.operations().get(1).kind() == CompositionPlan.Kind.PRESERVE_RECIPE_RESOURCES
                && base.operations().get(1).requires().equals(Set.of("logic.owned-bodies")), "Unimplemented base graph");
        Map<String, String> owners = new HashMap<>();
        for (var operation : base.operations()) for (var claim : operation.claims()) {
            require(source.containsKey(claim.path()) && hash(source.get(claim.path())).equals(claim.sourceSha256()), "Stale source claim: " + claim.path());
            owners.put(claim.path(), operation.id());
        }
        var rules = metadataRules(modId, constructors, hierarchy, defaults, receivers, events, clientConfig, serverConfig, suite, durable, plainBlock, horizontalBlock, staticShapeBlock, booleanToggleBlock, itemAttributes, itemAttributeDurability, ownedStackDamage, ownedStackRepairUse);
        if(plainBlock || horizontalBlock || staticShapeBlock || booleanToggleBlock)require(!source.containsKey(dev.legacybridge.runtime.compat.world.BlockMiningAdapter.TARGET_PATH),"Source cannot predeclare generated mining tag");
        require(target.keySet().containsAll(rules.keySet()), "Missing generated metadata");
        List<Entry> entries = new ArrayList<>();
        for (String path : new TreeSet<>(target.keySet())) {
            OperationControl.checkpoint();
            String after = hash(target.get(path)); new CompositionPlan.Claim(path, after);
            String owner = owners.get(path), adapter = rules.get(path);
            if (owner != null) {
                if (owner.equals("data.recipe-resources")) require(Arrays.equals(source.get(path), target.get(path)), "Resource mutation: " + path);
            } else if (adapter != null) {
                owner = metadataOperation(adapter);
            } else {
                // The resource validator already admits these inert notices, not arbitrary META-INF data.
                String leaf = path.startsWith("META-INF/") ? path.substring(9) : path;
                require(!leaf.contains("/") && leaf.toLowerCase(Locale.ROOT).matches("(?:license|copying|notice|readme|authors)(?:[._-][a-z0-9._-]+)?")
                        && source.containsKey(path) && Arrays.equals(source.get(path), target.get(path)), "Unowned entry: " + path);
                owner = "inert.notices";
            }
            require(source.containsKey(path) || adapter != null && adapter.equals(ownedStackRepairUse ? AdapterCatalog.STACK_REPAIR_USE_BINDINGS : ownedStackDamage ? AdapterCatalog.STACK_DAMAGE_BINDINGS : itemAttributeDurability ? AdapterCatalog.ATTRIBUTE_DURABILITY_BINDINGS : itemAttributes ? AdapterCatalog.ATTRIBUTE_BINDINGS : booleanToggleBlock ? AdapterCatalog.BOOLEAN_TOGGLE_BLOCK_BINDINGS : staticShapeBlock ? AdapterCatalog.STATIC_SHAPE_BINDINGS : horizontalBlock ? AdapterCatalog.HORIZONTAL_BLOCK_BINDINGS : plainBlock ? AdapterCatalog.PLAIN_BLOCK_BINDINGS : durable ? AdapterCatalog.DURABLE_BINDINGS : suite ? AdapterCatalog.LIFECYCLE_SUITE_BINDINGS : serverConfig ? AdapterCatalog.SERVER_CONFIG_BINDINGS : clientConfig ? AdapterCatalog.CLIENT_CONFIG_BINDINGS : events ? AdapterCatalog.EVENT_BINDINGS : receivers ? AdapterCatalog.RECEIVER_BINDINGS : defaults ? AdapterCatalog.INTERFACE_BINDINGS : hierarchy ? AdapterCatalog.HIERARCHY_BINDINGS : constructors ? AdapterCatalog.CONSTRUCTOR_BINDINGS : AdapterCatalog.COMPOSE_BINDINGS)
                    || (plainBlock || horizontalBlock || staticShapeBlock || booleanToggleBlock) && AdapterCatalog.MINING.equals(adapter)
                    || path.equals("META-INF/MANIFEST.MF"), "Unapproved generated entry: " + path);
            entries.add(new Entry(path, Optional.ofNullable(source.get(path)).map(CompositionArtifactPlan::hash), after, owner, Optional.ofNullable(adapter)));
        }
        return new CompositionArtifactPlan(entries, base, rules);
    }

    void verify(Map<String, byte[]> source, Map<String, byte[]> target) {
        Set<String> expectedSource = new TreeSet<>(), expectedTarget = new TreeSet<>();
        for (Entry entry : entries) {
            OperationControl.checkpoint(); expectedTarget.add(entry.path());
            require(target.containsKey(entry.path()) && hash(target.get(entry.path())).equals(entry.after()), "Target byte drift: " + entry.path());
            if (entry.before().isPresent()) {
                expectedSource.add(entry.path());
                require(source.containsKey(entry.path()) && hash(source.get(entry.path())).equals(entry.before().get()), "Source byte drift: " + entry.path());
            }
        }
        require(expectedSource.equals(source.keySet()) && expectedTarget.equals(target.keySet()), "Complete ownership mismatch");
    }

    Map<String, Object> toTree() {
        var operations = new ArrayList<Map<String, Object>>();
        for (var rule : new TreeMap<>(metadataRules).entrySet()) {
            operations.add(Map.of("id", metadataOperation(rule.getValue()), "path", rule.getKey(), "adapter", AdapterCatalog.require(rule.getValue()).referenceTree(),
                    "requires", List.of("logic.owned-bodies", "data.recipe-resources"),
                    "phase", AdapterCatalog.MINING.equals(rule.getValue()) ? "PRIVATE_MINING_RESOURCE_GENERATION" : "PRIVATE_METADATA_SERIALIZATION", "thread", "BOUNDED_ENGINE_WORKER",
                    "runtimeContext", AdapterCatalog.MINING.equals(rule.getValue()) ? "Additive pickaxe eligibility for proven STONE blocks; behavior-affecting target tag, native QA pending" : "Inert native loading metadata; no callbacks, bus or thread rescheduling",
                    "preconditions", AdapterCatalog.MINING.equals(rule.getValue()) ? List.of(metadataRules.containsValue(AdapterCatalog.STATIC_SHAPE_MANIFEST) ? "Exact static bottom-half geometry with independently requalified STONE defaults" : "Exact native STONE full-cube contract", "No source global tag override", "Regenerated tag bytes match on readback", "Complete byte ownership") : List.of("Source and target dependency closure", "Exact implemented metadata generators", "Complete byte ownership")));
        }
        return SimpleContentConverter.canonicalTree(Map.of("schemaVersion", 1, "kind", "PRIVATE_ARTIFACT_PLAN_NOT_AUTHORITY",
                "baseOperations", base.toTree(), "metadataOperations", operations, "entryCount", entries.size(),
                "entries", entries.stream().map(Entry::toTree).toList(), "runtimeVersion", AdapterCatalog.RUNTIME_VERSION,
                "inertOperations", entries.stream().anyMatch(e -> e.operation().equals("inert.notices")) ? List.of(Map.of(
                        "id", "inert.notices", "revision", 1, "implementation", "ContentResourceValidator + exact byte preservation",
                        "requires", List.of("data.recipe-resources"), "phase", "RESOURCE_PRESERVATION", "thread", "BOUNDED_ENGINE_WORKER",
                        "runtimeContext", "Inert notices; no executable or resource-pack behavior",
                        "preconditions", List.of("Existing allowlisted notice path", "Source and target bytes identical"))) : List.of(),
                "publicationAllowed", false));
    }
    private static String metadataOperation(String adapter) {
        return "metadata." + switch (adapter) {
            case AdapterCatalog.METADATA -> "mod-dependencies";
            case AdapterCatalog.PACK -> "pack-format";
            case AdapterCatalog.COMPOSE_MANIFEST -> "composition-identity";
            case AdapterCatalog.COMPOSE_BINDINGS -> "adapter-bindings";
            case AdapterCatalog.CONSTRUCTOR_MANIFEST -> "constructor-candidate-identity";
            case AdapterCatalog.CONSTRUCTOR_BINDINGS -> "constructor-adapter-bindings";
            case AdapterCatalog.HIERARCHY_MANIFEST -> "hierarchy-candidate-identity";
            case AdapterCatalog.HIERARCHY_BINDINGS -> "hierarchy-adapter-bindings";
            case AdapterCatalog.PLAIN_BLOCK_MANIFEST -> "plain-block-candidate-identity";
            case AdapterCatalog.PLAIN_BLOCK_BINDINGS -> "plain-block-adapter-bindings";
            case AdapterCatalog.BOOLEAN_TOGGLE_BLOCK_MANIFEST -> "boolean-toggle-block-candidate-identity";
            case AdapterCatalog.BOOLEAN_TOGGLE_BLOCK_BINDINGS -> "boolean-toggle-block-adapter-bindings";
            case AdapterCatalog.STATIC_SHAPE_MANIFEST -> "static-shape-block-candidate-identity";
            case AdapterCatalog.STATIC_SHAPE_BINDINGS -> "static-shape-block-adapter-bindings";
            case AdapterCatalog.HORIZONTAL_BLOCK_MANIFEST -> "horizontal-block-candidate-identity";
            case AdapterCatalog.HORIZONTAL_BLOCK_BINDINGS -> "horizontal-block-adapter-bindings";
            case AdapterCatalog.MINING -> "stone-mining-tag";
            case AdapterCatalog.STACK_REPAIR_USE_MANIFEST -> "owned-stack-repair-use-candidate-identity";
            case AdapterCatalog.STACK_REPAIR_USE_BINDINGS -> "owned-stack-repair-use-adapter-bindings";
            case AdapterCatalog.STACK_DAMAGE_MANIFEST -> "owned-stack-damage-candidate-identity";
            case AdapterCatalog.STACK_DAMAGE_BINDINGS -> "owned-stack-damage-adapter-bindings";
            case AdapterCatalog.ATTRIBUTE_DURABILITY_MANIFEST -> "item-attribute-durability-candidate-identity";
            case AdapterCatalog.ATTRIBUTE_DURABILITY_BINDINGS -> "item-attribute-durability-adapter-bindings";
            case AdapterCatalog.ATTRIBUTE_MANIFEST -> "item-attribute-candidate-identity";
            case AdapterCatalog.ATTRIBUTE_BINDINGS -> "item-attribute-adapter-bindings";
            case AdapterCatalog.DURABLE_MANIFEST -> "durable-item-candidate-identity";
            case AdapterCatalog.DURABLE_BINDINGS -> "durable-item-adapter-bindings";
            case AdapterCatalog.LIFECYCLE_SUITE_MANIFEST -> "lifecycle-suite-candidate-identity";
            case AdapterCatalog.LIFECYCLE_SUITE_BINDINGS -> "lifecycle-suite-adapter-bindings";
            case AdapterCatalog.SERVER_CONFIG_MANIFEST -> "server-config-candidate-identity";
            case AdapterCatalog.SERVER_CONFIG_BINDINGS -> "server-config-adapter-bindings";
            case AdapterCatalog.CLIENT_CONFIG_MANIFEST -> "client-config-candidate-identity";
            case AdapterCatalog.CLIENT_CONFIG_BINDINGS -> "client-config-adapter-bindings";
            case AdapterCatalog.EVENT_MANIFEST -> "event-candidate-identity";
            case AdapterCatalog.EVENT_BINDINGS -> "event-adapter-bindings";
            case AdapterCatalog.RECEIVER_MANIFEST -> "receiver-candidate-identity";
            case AdapterCatalog.RECEIVER_BINDINGS -> "receiver-adapter-bindings";
            case AdapterCatalog.INTERFACE_MANIFEST -> "interface-candidate-identity";
            case AdapterCatalog.INTERFACE_BINDINGS -> "interface-adapter-bindings";
            default -> throw new TransformationException("COMPOSITION_ARTIFACT: Unknown metadata operation");
        };
    }
    private static String hash(byte[] bytes) { return SimpleContentConverter.hash(bytes); }
    private static void require(boolean condition, String detail) {
        if (!condition) throw new TransformationException("COMPOSITION_ARTIFACT: " + detail);
    }
}
