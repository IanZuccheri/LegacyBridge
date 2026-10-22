package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.conversion.extract.ContentPlan;
import dev.legacybridge.runtime.compat.*;
import java.util.List;
import java.util.Map;

/** No transformation is emitted without an exact implemented adapter identity and revision. */
public final class TransformationBindings {
    public static TransformationBindings forOwnedHierarchy(){
        return new TransformationBindings(dev.legacybridge.runtime.compat.logic.HierarchyArtifactAdapter.requiredAdapters()
                .stream().map(AdapterCatalog::require).toList());
    }
    private final List<AdapterDescriptor> adapters;
    private TransformationBindings(List<AdapterDescriptor> adapters){this.adapters=List.copyOf(adapters);}
    public static TransformationBindings forOwnedConstructors(){
        return new TransformationBindings(dev.legacybridge.runtime.compat.logic.ConstructorArtifactAdapter.requiredAdapters()
                .stream().map(AdapterCatalog::require).toList());
    }
    public static TransformationBindings forComposition(){
        return new TransformationBindings(dev.legacybridge.runtime.compat.logic.ComposedArtifactAdapter.requiredAdapters()
                .stream().map(AdapterCatalog::require).toList());
    }
    public static TransformationBindings forNetwork(){
        return new TransformationBindings(List.of(AdapterCatalog.NETWORK, AdapterCatalog.NETWORK_MANIFEST,
                AdapterCatalog.NETWORK_BINDINGS, AdapterCatalog.METADATA, AdapterCatalog.PACK)
                .stream().map(AdapterCatalog::require).toList());
    }
    public static TransformationBindings forLogic(){
        return new TransformationBindings(List.of(AdapterCatalog.LOGIC,AdapterCatalog.LOGIC_MANIFEST,AdapterCatalog.LOGIC_BINDINGS,
                AdapterCatalog.METADATA,AdapterCatalog.PACK).stream().map(AdapterCatalog::require).toList());
    }
    public static TransformationBindings forOwnedInterfaceDefaults() {
        return new TransformationBindings(dev.legacybridge.runtime.compat.logic.InterfaceArtifactAdapter.requiredAdapters()
                .stream().map(AdapterCatalog::require).toList());
    }
    public static TransformationBindings forLifecycle(){
        return new TransformationBindings(List.of(AdapterCatalog.LOGIC, AdapterCatalog.LIFECYCLE,
                AdapterCatalog.LIFECYCLE_MANIFEST, AdapterCatalog.LIFECYCLE_BINDINGS,
                AdapterCatalog.METADATA, AdapterCatalog.PACK).stream().map(AdapterCatalog::require).toList());
    }
    public static TransformationBindings forBooleanToggleBlock() {
        return new TransformationBindings(dev.legacybridge.runtime.compat.logic.BooleanToggleBlockArtifactAdapter.requiredAdapters()
            .stream().map(AdapterCatalog::require).toList());
    }
    public static TransformationBindings forStaticShapeBlock() {
        return new TransformationBindings(dev.legacybridge.runtime.compat.logic.StaticShapeBlockArtifactAdapter.requiredAdapters()
            .stream().map(AdapterCatalog::require).toList());
    }
    public static TransformationBindings forHorizontalBlock() {
        return new TransformationBindings(dev.legacybridge.runtime.compat.logic.HorizontalBlockArtifactAdapter.requiredAdapters()
                .stream().map(AdapterCatalog::require).toList());
    }
    public static TransformationBindings forPlainBlock() {
        return new TransformationBindings(dev.legacybridge.runtime.compat.logic.PlainBlockArtifactAdapter.requiredAdapters()
                .stream().map(AdapterCatalog::require).toList());
    }
    public static TransformationBindings forItemAttributes() {
        return new TransformationBindings(dev.legacybridge.runtime.compat.logic.ItemAttributeArtifactAdapter.requiredAdapters()
                .stream().map(AdapterCatalog::require).toList());
    }

    public static TransformationBindings forItemAttributeDurability() {
        return new TransformationBindings(dev.legacybridge.runtime.compat.logic.ItemAttributeDurabilityArtifactAdapter.requiredAdapters()
                .stream().map(AdapterCatalog::require).toList());
    }
    public static TransformationBindings forOwnedStackRepairUse() {
        return new TransformationBindings(dev.legacybridge.runtime.compat.logic.OwnedStackRepairUseArtifactAdapter.requiredAdapters()
                .stream().map(AdapterCatalog::require).toList());
    }
    public static TransformationBindings forOwnedStackDamage() {
        return new TransformationBindings(dev.legacybridge.runtime.compat.logic.OwnedStackDamageArtifactAdapter.requiredAdapters()
                .stream().map(AdapterCatalog::require).toList());
    }
    public static TransformationBindings forDurableItem() {
        return new TransformationBindings(dev.legacybridge.runtime.compat.logic.DurableItemArtifactAdapter.requiredAdapters()
                .stream().map(AdapterCatalog::require).toList());
    }
    public static TransformationBindings forLifecycleSuite() {
        return new TransformationBindings(dev.legacybridge.runtime.compat.logic.LifecycleSuiteAdapter.requiredAdapters()
                .stream().map(AdapterCatalog::require).toList());
    }
    public static TransformationBindings forServerConfig() {
        return new TransformationBindings(dev.legacybridge.runtime.compat.logic.ServerConfigArtifactAdapter.requiredAdapters()
                .stream().map(AdapterCatalog::require).toList());
    }
    public static TransformationBindings forClientConfig() {
        return new TransformationBindings(dev.legacybridge.runtime.compat.logic.ClientConfigArtifactAdapter.requiredAdapters()
                .stream().map(AdapterCatalog::require).toList());
    }
    public static TransformationBindings forEventPriority() {
        return new TransformationBindings(dev.legacybridge.runtime.compat.logic.EventArtifactAdapter.requiredAdapters()
                .stream().map(AdapterCatalog::require).toList());
    }
    public static TransformationBindings forOwnedReceiverHandles() {
        return new TransformationBindings(dev.legacybridge.runtime.compat.logic.ReceiverArtifactAdapter.requiredAdapters()
                .stream().map(AdapterCatalog::require).toList());
    }
    public static TransformationBindings forFood(){
        return new TransformationBindings(List.of(AdapterCatalog.LOGIC, AdapterCatalog.FOOD,
                AdapterCatalog.FOOD_MANIFEST, AdapterCatalog.FOOD_BINDINGS,
                AdapterCatalog.METADATA, AdapterCatalog.PACK).stream().map(AdapterCatalog::require).toList());
    }
    public static TransformationBindings forPlan(ContentPlan plan){
        if(plan.items().size()>1024||plan.blocks().size()>1024||plan.sounds().size()>1024
                || plan.items().size()+plan.blocks().size()+plan.sounds().size()>2048)
            throw new UnsupportedCompatibilityException("Plan exceeds the runtime's validated array/definition bounds");
        // Former speculative presets are deliberately not grandfathered into the compatibility layer.
        for(var item:plan.items()) {
            var p=item.properties();
            if(p.stackSize()<1||p.stackSize()>64||p.durability()!=0||p.fireResistant()||!"COMMON".equals(p.rarity()))
                throw new UnsupportedCompatibilityException("No fixture-backed adapter for these item properties: "+item.name());
            if(p.creativeTab()!=null&&!java.util.Set.of("MISC","BUILDING_BLOCKS").contains(p.creativeTab()))
                throw new UnsupportedCompatibilityException("No fixture-backed creative category adapter: "+p.creativeTab());
        }
        for(var block:plan.blocks()) {
            var p=block.properties();
            if(!"STONE".equals(p.material())||!"STONE".equals(p.sound())||p.requiresCorrectTool()||p.noOcclusion())
                throw new UnsupportedCompatibilityException("No fixture-backed semantic material/property adapter: "+block.name());
        }
        return new TransformationBindings(AdapterSelection.required(!plan.items().isEmpty(),!plan.blocks().isEmpty(),
                plan.items().stream().anyMatch(i->i.block()!=null),!plan.sounds().isEmpty(),
                plan.items().stream().anyMatch(i->i.properties().creativeTab()!=null)).stream().map(AdapterCatalog::require).toList());
    }
    public AdapterDescriptor require(String id){
        return adapters.stream().filter(a->a.id().equals(id)).findFirst().orElseThrow(()->new UnsupportedCompatibilityException("Transformation omitted its required adapter: "+id));
    }
    public Map<String,Object> bindingTree(){return Map.of("schemaVersion",AdapterCatalog.SCHEMA_VERSION,
            "runtimeVersion",AdapterCatalog.RUNTIME_VERSION,"adapters",adapters.stream().map(AdapterDescriptor::referenceTree).toList());}
    public List<Map<String,Object>> report(){return adapters.stream().map(a->Map.<String,Object>of(
            "operation",a.transformation(),"adapter",a.referenceTree(),"phase",a.phase(),
            "legacyApi",a.legacyApi(),"targetApi",a.targetApi(),"behaviorDifferences",a.behaviorDifferences(),
            "knownLimitations",a.knownLimitations(),"fixtures",a.fixtures())).toList();}
}
