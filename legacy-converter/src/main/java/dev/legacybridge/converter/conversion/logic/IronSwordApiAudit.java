package dev.legacybridge.converter.conversion.logic;

import dev.legacybridge.converter.mapping.source.ApiIndex;
import dev.legacybridge.converter.mapping.source.MappingInputs;
import dev.legacybridge.converter.transformation.TransformationException;
import java.util.*;
import org.objectweb.asm.Type;
import static org.objectweb.asm.Opcodes.*;

/**
 * Read-only declaration audit for the authored IRON/3/-2.4F/fresh-properties candidate.
 * This is deliberately NOT a mapping compiler: no MappingRule, adapter, transformed class,
 * eligible candidate or gameplay-equivalence approval can be produced by this API.
 * MappingInputs contains declarations, not method bodies. Historical body-review facts are
 * labelled separately and never promoted to verification of the supplied input's behavior.
 */
public final class IronSwordApiAudit {
    private static final String ITEM="net/minecraft/world/item/Item", SWORD="net/minecraft/world/item/SwordItem",
            TIERED="net/minecraft/world/item/TieredItem", TIER="net/minecraft/world/item/Tier",
            TIERS="net/minecraft/world/item/Tiers", MARKER="net/minecraft/world/item/Vanishable",
            PROPS=ITEM+"$Properties", STACK="net/minecraft/world/item/ItemStack",
            STATE="net/minecraft/world/level/block/state/BlockState", LEVEL="net/minecraft/world/level/Level",
            POSITION="net/minecraft/core/BlockPos", PLAYER="net/minecraft/world/entity/player/Player",
            LIVING="net/minecraft/world/entity/LivingEntity", SLOT="net/minecraft/world/entity/EquipmentSlot",
            INGREDIENT="net/minecraft/world/item/crafting/Ingredient", ACTIONS="net/minecraftforge/common/ToolActions";
    private static final int MEMBER_MASK=ACC_PUBLIC|ACC_PRIVATE|ACC_PROTECTED|ACC_STATIC|ACC_FINAL|ACC_ABSTRACT|ACC_NATIVE;
    private static final int CLASS_MASK=ACC_PUBLIC|ACC_PRIVATE|ACC_PROTECTED|ACC_FINAL|ACC_INTERFACE|ACC_ABSTRACT|ACC_ENUM;
    private IronSwordApiAudit() {}

    /** Finite source linkage metadata for the strict authored factory/registration graph, not approval to convert it. */
    public static ApiIndex sourceLinkageApi(MappingInputs inputs) {
        Objects.requireNonNull(inputs,"inputs");
        if(!inputs.complete()||inputs.scope()!=MappingInputs.Scope.FULL_ENVIRONMENT)
            throw failure("Complete explicit source mapping and API inputs are required for linkage");
        var roots=Set.of("net/minecraft/item/SwordItem","net/minecraft/item/Item","net/minecraft/item/Item$Properties",
                "net/minecraft/item/ItemTier","net/minecraft/item/IItemTier","net/minecraftforge/registries/DeferredRegister",
                "net/minecraftforge/registries/ForgeRegistries","net/minecraftforge/fml/RegistryObject",
                // Exact field/factory descriptor in ForgeRegistries.ITEMS and DeferredRegister.create.
                "net/minecraftforge/registries/IForgeRegistry",
                "net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext","net/minecraftforge/eventbus/api/IEventBus",
                "net/minecraftforge/fml/common/Mod");
        for(String root:roots)if(!inputs.legacyApi().hasClass(root))throw failure("Source linkage root absent: "+root);
        return new LogicApiNamespace(inputs.legacyOfficial(),inputs.legacySrg(),inputs.legacyApi(),true).normalize(roots);
    }

    /** Uses only explicitly supplied mappings and declaration indexes; no cache lookup or classloading. */
    public static Report inspect(MappingInputs inputs) {
        Objects.requireNonNull(inputs,"inputs");
        if(!inputs.complete()||inputs.scope()!=MappingInputs.Scope.FULL_ENVIRONMENT)
            throw failure("Complete explicit source/target mapping and API inputs are required");
        Side old=new Side(inputs,true), target=new Side(inputs,false);
        var hierarchy=new ArrayList<Map<String,Object>>();
        for(String type:List.of(SWORD,TIERED,ITEM,TIER,TIERS,MARKER,PROPS))
            hierarchy.add(Map.of("canonical",type,"source",old.type(type),"target",target.type(type)));
        old.hierarchy();target.hierarchy();
        var declarations=new ArrayList<Map<String,Object>>();
        pair(declarations,old,target,SWORD,"<init>","("+ref(TIER)+"IF"+ref(PROPS)+")V",ACC_PUBLIC,INVOKESPECIAL);
        pair(declarations,old,target,TIERED,"<init>","("+ref(TIER)+ref(PROPS)+")V",ACC_PUBLIC,INVOKESPECIAL);
        pair(declarations,old,target,ITEM,"<init>","("+ref(PROPS)+")V",ACC_PUBLIC,INVOKESPECIAL);
        pair(declarations,old,target,PROPS,"<init>","()V",ACC_PUBLIC,INVOKESPECIAL);
        field(declarations,old,target,TIERS,"IRON",ref(TIERS),ACC_PUBLIC|ACC_STATIC|ACC_FINAL,GETSTATIC);
        for(String uuid:List.of("BASE_ATTACK_DAMAGE_UUID","BASE_ATTACK_SPEED_UUID"))
            field(declarations,old,target,ITEM,uuid,"Ljava/util/UUID;",ACC_PROTECTED|ACC_STATIC|ACC_FINAL,GETSTATIC);
        pair(declarations,old,target,SWORD,"getDamage","()F",ACC_PUBLIC,INVOKEVIRTUAL);
        pair(declarations,old,target,SWORD,"canAttackBlock","("+ref(STATE)+ref(LEVEL)+ref(POSITION)+ref(PLAYER)+")Z",ACC_PUBLIC,INVOKEVIRTUAL);
        pair(declarations,old,target,SWORD,"getDestroySpeed","("+ref(STACK)+ref(STATE)+")F",ACC_PUBLIC,INVOKEVIRTUAL);
        pair(declarations,old,target,SWORD,"hurtEnemy","("+ref(STACK)+ref(LIVING)+ref(LIVING)+")Z",ACC_PUBLIC,INVOKEVIRTUAL);
        pair(declarations,old,target,SWORD,"mineBlock","("+ref(STACK)+ref(LEVEL)+ref(STATE)+ref(POSITION)+ref(LIVING)+")Z",ACC_PUBLIC,INVOKEVIRTUAL);
        pair(declarations,old,target,SWORD,"isCorrectToolForDrops","("+ref(STATE)+")Z",ACC_PUBLIC,INVOKEVIRTUAL);
        pair(declarations,old,target,SWORD,"getDefaultAttributeModifiers","("+ref(SLOT)+")Lcom/google/common/collect/Multimap;",ACC_PUBLIC,INVOKEVIRTUAL);
        pair(declarations,old,target,TIERED,"getTier","()"+ref(TIER),ACC_PUBLIC,INVOKEVIRTUAL);
        pair(declarations,old,target,TIERED,"getEnchantmentValue","()I",ACC_PUBLIC,INVOKEVIRTUAL);
        for(String owner:List.of(TIERED,ITEM))
            pair(declarations,old,target,owner,"isValidRepairItem","("+ref(STACK)+ref(STACK)+")Z",ACC_PUBLIC,INVOKEVIRTUAL);
        for(String method:List.of("defaultDurability","durability"))
            pair(declarations,old,target,PROPS,method,"(I)"+ref(PROPS),ACC_PUBLIC,INVOKEVIRTUAL);
        for(String owner:List.of(TIER,TIERS)) {
            for(var entry:Map.of("getUses","()I","getSpeed","()F","getAttackDamageBonus","()F","getLevel","()I",
                    "getEnchantmentValue","()I","getRepairIngredient","()"+ref(INGREDIENT)).entrySet().stream().sorted(Map.Entry.comparingByKey()).toList())
                pair(declarations,old,target,owner,entry.getKey(),entry.getValue(),ACC_PUBLIC|(owner.equals(TIER)?ACC_ABSTRACT:0),owner.equals(TIER)?INVOKEINTERFACE:INVOKEVIRTUAL);
        }
        // Forge-added target APIs have no invented official/SRG rows and no source equivalence.
        var targetOnly=new ArrayList<Map<String,Object>>();
        targetOnly.add(target.direct(SWORD,"canPerformAction","("+ref(STACK)+"Lnet/minecraftforge/common/ToolAction;)Z",ACC_PUBLIC,INVOKEVIRTUAL,false));
        for(String owner:List.of(TIER,TIERS))
            targetOnly.add(target.direct(owner,"getTag","()Lnet/minecraft/tags/TagKey;",ACC_PUBLIC,owner.equals(TIER)?INVOKEINTERFACE:INVOKEVIRTUAL,false));
        for(String field:List.of("SWORD_DIG","SWORD_SWEEP"))
            targetOnly.add(target.direct(ACTIONS,field,"Lnet/minecraftforge/common/ToolAction;",ACC_PUBLIC|ACC_STATIC|ACC_FINAL,GETSTATIC,true));
        targetOnly.add(target.direct(ACTIONS,"DEFAULT_SWORD_ACTIONS","Ljava/util/Set;",ACC_PUBLIC|ACC_STATIC|ACC_FINAL,GETSTATIC,true));
        if(old.index.getClass(old.ns.productionClass(SWORD)).methods().stream().anyMatch(m->m.name().equals("canPerformAction")))
            throw failure("Unexpected source SwordItem tool-action override; source contract changed");
        List<Map<String,Object>> supplied=inputs.sources().stream().map(s->Map.<String,Object>of("id",s.id(),"sha256",s.sha256(),"origin",s.origin())).toList();
        return new Report(hierarchy,declarations,targetOnly,supplied);
    }

    public record Report(List<Map<String,Object>> hierarchy,List<Map<String,Object>> declarations,
                         List<Map<String,Object>> targetOnlyDeclarations,List<Map<String,Object>> suppliedProvenance) {
        public Report { hierarchy=freezeEntries(hierarchy);declarations=freezeEntries(declarations);
            targetOnlyDeclarations=freezeEntries(targetOnlyDeclarations);suppliedProvenance=freezeEntries(suppliedProvenance); }
        public Map<String,Object> toTree() {
            var tree=new LinkedHashMap<String,Object>();
            tree.put("schemaVersion",1);tree.put("audit","IRON_SWORD_SEMANTIC_CONTRACT");
            tree.put("status","UNSUPPORTED");tree.put("declarationStatus","CHECKED_EXACT_DECLARATIONS");
            tree.put("candidateGenerated",false);tree.put("mappingRulesEmitted",0);tree.put("classesExecuted",false);
            tree.put("nativeValidation","NOT_RUN");tree.put("hierarchy",hierarchy);tree.put("declarations",declarations);
            tree.put("targetOnlyDeclarations",targetOnlyDeclarations);tree.put("suppliedProvenance",suppliedProvenance);
            tree.put("knownFacts",reviewedFacts());tree.put("semanticBlockers",blockers());
            tree.put("limitations",List.of("Declaration/access/opcode agreement is not semantic equivalence and is not MappingConfidence.VERIFIED.",
                    "ApiIndex contains no method bodies; supplied provenance is not authentication of those bodies or of this index.",
                    "Pinned historical source/body facts below do not attest behavior of caller-supplied API declarations.",
                    "No complete inventory of inherited Item/Forge behavior, vanilla-default tag equality, anvil, breakage, combat, reload or multiplayer qualification.",
                    "No constructor-only conversion, migration recipe, runtime adapter or public-profile expansion is authorized."));
            return Collections.unmodifiableMap(tree);
        }
    }

    private static Map<String,Object> reviewedFacts() {
        var facts=new LinkedHashMap<String,Object>();facts.put("evidenceKind","PINNED_SOURCE_REVIEW_NOT_DERIVED_FROM_API_INDEX");
        facts.put("sourceVersion","1.16.5-36.2.42");facts.put("targetVersion","1.20.1-47.4.23");
        facts.put("sourceApiSha256","1a3e71d6ccb8461fe9d94f4c63551fe52a8c0c01116572c49a0c3253d0506b03");
        facts.put("targetApiSha256","b820f2243c37f060b17678f3a23c868da14d35ebf82ee47b4035b1718c0952ee");
        facts.put("sourceArchiveSha256","bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376");
        facts.put("targetArchiveSha256","4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932");
        facts.put("targetSwordEfficientJsonSha256","c0df9191317f615355983dd39608e0d7572a6cc550fb2cf27632794751a19dd2");
        facts.put("scope","Native IRON, 3, -2.4F, fresh Item.Properties; no subclass/custom tier/additional properties");
        facts.put("ironTier",Map.of("level",2,"uses",250,"speed",6.0F,"damageBonus",2.0F,"enchantmentValue",14,"repairIngredient","minecraft:iron_ingot"));
        facts.put("construction",Map.of("attackDamage",5.0F,"attackSpeedAsDouble",(double)-2.4F,"maxDamage",250,"maxStackSize",1,"slot","MAINHAND","modifierOperation","ADDITION"));
        facts.put("attributeUuids",Map.of("damage","cb3f55d3-645c-4f38-a497-9c13a33db5cf","speed","fa233e1c-4180-4865-b01b-bcce9785aca3"));
        facts.put("repair","Tier ingredient predicate first, then superclass fallback; anvil costs/consumption are not qualified");
        return Collections.unmodifiableMap(facts);
    }
    private static List<Map<String,Object>> blockers() {
        return List.of(
                blocker("SWORD_MINING_PREDICATE_DRIFT","Material.PLANT/REPLACEABLE_PLANT/CORAL/VEGETABLE or LEAVES after COBWEB","Reloadable SWORD_EFFICIENT tag after COBWEB",
                        "No implemented scoped Material-to-tag semantic recipe. A replaced empty target tag gives a source-based counterexample; no vanilla-default mismatch or native test is claimed."),
                blocker("SWORD_TOOL_ACTION_AND_SWEEP_DRIFT","Player sweep dispatch checks instanceof SwordItem","SwordItem.DEFAULT_SWORD_ACTIONS includes SWORD_DIG/SWORD_SWEEP; player queries canPerformAction",
                        "No implemented contract preserving target Forge integrations, custom action handling and sweep dispatch."),
                blocker("TIER_AND_INHERITED_BEHAVIOR_UNQUALIFIED","IItemTier and Item inherit legacy Forge contracts","Tier adds tag/sorting integration; Item has different ancestry/feature contracts",
                        "Matching constructor, modifiers, repair and durability facts do not prove inherited gameplay, save/load, reload, combat or multiplayer."));
    }
    private static Map<String,Object> blocker(String id,String source,String target,String reason) {
        return Map.of("id",id,"status","UNSUPPORTED","source",source,"target",target,"reason",reason,"implementedRecipe",false);
    }
    private static void pair(List<Map<String,Object>> into,Side old,Side target,String owner,String name,String desc,int access,int opcode) {
        into.add(Map.of("canonicalOwner",owner,"canonicalName",name,"canonicalDescriptor",desc,
                "source",old.member(owner,name,desc,access,opcode,false),"target",target.member(owner,name,desc,access,opcode,false),"evidenceKind","DECLARATION_ONLY"));
    }
    private static void field(List<Map<String,Object>> into,Side old,Side target,String owner,String name,String desc,int access,int opcode) {
        into.add(Map.of("canonicalOwner",owner,"canonicalName",name,"canonicalDescriptor",desc,
                "source",old.member(owner,name,desc,access,opcode,true),"target",target.member(owner,name,desc,access,opcode,true),"evidenceKind","DECLARATION_ONLY"));
    }
    private static final class Side {
        final boolean old;final LogicApiNamespace ns;final ApiIndex index,development;
        Side(MappingInputs inputs,boolean old) {
            this.old=old;development=old?inputs.legacyApi():inputs.targetApi();
            ns=new LogicApiNamespace(old?inputs.legacyOfficial():inputs.targetOfficial(),old?inputs.legacySrg():inputs.targetSrg(),development,old);
            var roots=new TreeSet<String>();for(String name:List.of(SWORD,TIERED,ITEM,TIER,TIERS,MARKER,PROPS))roots.add(ns.productionClass(name));
            if(!old)roots.add(ACTIONS);
            index=ns.normalize(roots);
        }
        Map<String,Object> type(String canonical) {
            var c=require(ns.productionClass(canonical));
            return Map.of("owner",c.name(),"access",c.access(),"superclass",Objects.toString(c.superName(),""),"interfaces",c.interfaces());
        }
        void hierarchy() {
            checkClass(SWORD,ACC_PUBLIC,TIERED,List.of(MARKER));checkClass(TIERED,ACC_PUBLIC,ITEM,List.of());
            checkClass(TIER,ACC_PUBLIC|ACC_INTERFACE|ACC_ABSTRACT,null,List.of());
            checkClass(MARKER,ACC_PUBLIC|ACC_INTERFACE|ACC_ABSTRACT,null,List.of());
            checkClass(TIERS,ACC_PUBLIC|ACC_FINAL|ACC_ENUM,null,List.of(TIER));
            checkClass(PROPS,ACC_PUBLIC,null,List.of());
            var item=require(ns.productionClass(ITEM));if((item.access()&CLASS_MASK)!=ACC_PUBLIC)throw failure("Item access changed");
            String parent=old?"net/minecraftforge/registries/ForgeRegistryEntry":"java/lang/Object";
            Set<String> interfaces=old?Set.of("net/minecraft/util/IItemProvider","net/minecraftforge/common/extensions/IForgeItem"):
                    Set.of("net/minecraft/world/flag/FeatureElement","net/minecraft/world/level/ItemLike","net/minecraftforge/common/extensions/IForgeItem");
            if(!parent.equals(item.superName())||!new HashSet<>(item.interfaces()).equals(interfaces))throw failure("Item ancestry changed on "+side());
            if(!require(ns.productionClass(MARKER)).methods().isEmpty())throw failure("Vanishable is no longer a marker interface");
        }
        void checkClass(String canonical,int access,String parent,List<String> interfaces) {
            var c=require(ns.productionClass(canonical));String expectedParent=parent==null?(canonical.equals(TIERS)?"java/lang/Enum":"java/lang/Object"):ns.productionClass(parent);
            Set<String> expected=new HashSet<>();interfaces.forEach(i->expected.add(ns.productionClass(i)));
            if((c.access()&CLASS_MASK)!=access||!expectedParent.equals(c.superName())||!new HashSet<>(c.interfaces()).equals(expected))
                throw failure("Hierarchy/access contract changed on "+side()+": "+canonical);
        }
        Map<String,Object> member(String owner,String name,String desc,int access,int opcode,boolean field) {
            return verify(ns.productionClass(owner),field?ns.productionField(owner,name,desc):ns.productionMethod(owner,name,desc),ns.productionDescriptor(desc),access,opcode,field);
        }
        Map<String,Object> direct(String owner,String name,String desc,int access,int opcode,boolean field) {
            return verify(owner.startsWith("net/minecraftforge/")?owner:ns.productionClass(owner),name,ns.productionDescriptor(desc),access,opcode,field);
        }
        Map<String,Object> verify(String owner,String name,String desc,int access,int opcode,boolean field) {
            var c=require(owner);var matches=(field?c.fields():c.methods()).stream().filter(m->m.name().equals(name)&&m.descriptor().equals(desc)).toList();
            if(matches.size()!=1||(matches.get(0).access()&MEMBER_MASK)!=access)throw failure("Exact declaration/access/opcode contract changed on "+side()+": "+owner+"."+name+desc);
            if(!field&&((opcode==INVOKEINTERFACE)!=((c.access()&ACC_INTERFACE)!=0)))throw failure("Invocation owner kind changed: "+owner);
            descriptorTypes(Type.getType(desc));
            return Map.of("owner",owner,"name",name,"descriptor",desc,"access",matches.get(0).access(),"expectedOpcode",opcode,"kind",field?"FIELD":"METHOD");
        }
        void descriptorTypes(Type type) {
            if(type.getSort()==Type.METHOD){descriptorTypes(type.getReturnType());for(Type argument:type.getArgumentTypes())descriptorTypes(argument);return;}
            if(type.getSort()==Type.ARRAY){descriptorTypes(type.getElementType());return;}
            if(type.getSort()!=Type.OBJECT)return;
            String owner=type.getInternalName();
            // The explicit game/Forge inventories are not an inventory of Guava or the Java platform.
            if(!owner.startsWith("net/minecraft/")&&!owner.startsWith("net/minecraftforge/"))return;
            var declaration=development.getClass(owner);
            if(declaration==null||(declaration.access()&ACC_PUBLIC)==0)
                throw failure("Required public descriptor type absent on "+side()+": "+owner);
        }
        ApiIndex.ApiClass require(String owner) {var c=index.getClass(owner);if(c==null||(c.access()&ACC_PUBLIC)==0)throw failure("Required public declaration absent on "+side()+": "+owner);return c;}
        String side(){return old?"source":"target";}
    }
    private static List<Map<String,Object>> freezeEntries(List<Map<String,Object>> entries) {return entries.stream().map(IronSwordApiAudit::freezeMap).toList();}
    private static Map<String,Object> freezeMap(Map<String,Object> values) {
        var result=new LinkedHashMap<String,Object>();values.forEach((key,value)->result.put(Objects.requireNonNull(key),freezeValue(value)));
        return Collections.unmodifiableMap(result);
    }
    private static Object freezeValue(Object value) {
        if(value instanceof Map<?,?> map){var copy=new LinkedHashMap<String,Object>();map.forEach((key,item)->{if(!(key instanceof String text))throw new IllegalArgumentException("Report keys must be strings");copy.put(text,freezeValue(item));});return Collections.unmodifiableMap(copy);}
        if(value instanceof List<?> list)return list.stream().map(IronSwordApiAudit::freezeValue).toList();
        if(value instanceof String||value instanceof Boolean||value instanceof Integer||value instanceof Long||value instanceof Float||value instanceof Double)return value;
        throw new IllegalArgumentException("Unsupported report value: "+Objects.toString(value));
    }
    private static String ref(String owner){return "L"+owner+";";}
    private static TransformationException failure(String reason){return new TransformationException("IRON_SWORD_API_AUDIT: "+reason);}
}
