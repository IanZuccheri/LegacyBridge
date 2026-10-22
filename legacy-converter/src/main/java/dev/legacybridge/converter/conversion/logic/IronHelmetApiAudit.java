package dev.legacybridge.converter.conversion.logic;

import dev.legacybridge.converter.mapping.source.ApiIndex;
import dev.legacybridge.converter.mapping.source.MappingInputs;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import dev.legacybridge.converter.transformation.TransformationException;
import java.util.*;
import org.objectweb.asm.Type;
import static org.objectweb.asm.Opcodes.*;

/**
 * Read-only native IRON/HEAD/fresh-properties declaration audit. This is not a mapping
 * compiler or an adapter: the target HELMET constructor argument is contextual and native
 * equipment use differs. Supplied indexes contain declarations, not method bodies; pinned
 * historical source facts below never authenticate behavior of a caller-provided index.
 */
public final class IronHelmetApiAudit {
    private static final String ITEM="net/minecraft/world/item/Item", ARMOR="net/minecraft/world/item/ArmorItem",
            MATERIAL="net/minecraft/world/item/ArmorMaterial", MATERIALS="net/minecraft/world/item/ArmorMaterials",
            SLOT="net/minecraft/world/entity/EquipmentSlot", ARMOR_TYPE=ARMOR+"$Type", WEARABLE="net/minecraft/world/item/Wearable",
            EQUIPABLE="net/minecraft/world/item/Equipable", VANISHABLE="net/minecraft/world/item/Vanishable",
            PROPS=ITEM+"$Properties", STACK="net/minecraft/world/item/ItemStack", INGREDIENT="net/minecraft/world/item/crafting/Ingredient",
            LEVEL="net/minecraft/world/level/Level", PLAYER="net/minecraft/world/entity/player/Player",
            HAND="net/minecraft/world/InteractionHand", HOLDER="net/minecraft/world/InteractionResultHolder",
            SOUND="net/minecraft/sounds/SoundEvent", BLOCK_SOURCE="net/minecraft/core/BlockSource",
            DISPENSER="net/minecraft/world/level/block/DispenserBlock", DISPENSE="net/minecraft/core/dispenser/DispenseItemBehavior";
    private static final int CLASS_MASK=ACC_PUBLIC|ACC_PRIVATE|ACC_PROTECTED|ACC_FINAL|ACC_INTERFACE|ACC_ABSTRACT|ACC_ENUM|ACC_ANNOTATION;
    private static final int METHOD_MASK=ACC_PUBLIC|ACC_PRIVATE|ACC_PROTECTED|ACC_STATIC|ACC_FINAL|ACC_ABSTRACT|ACC_NATIVE
            |ACC_SYNCHRONIZED|ACC_BRIDGE|ACC_VARARGS|ACC_SYNTHETIC;
    private static final int FIELD_MASK=ACC_PUBLIC|ACC_PRIVATE|ACC_PROTECTED|ACC_STATIC|ACC_FINAL|ACC_VOLATILE|ACC_TRANSIENT|ACC_SYNTHETIC|ACC_ENUM;
    private IronHelmetApiAudit() { }

    /** Original finite factory/registration graph only; no converted target or compatibility approval. */
    public static ApiIndex sourceLinkageApi(MappingInputs inputs) {
        complete(inputs);
        var roots=Set.of("net/minecraft/item/ArmorItem","net/minecraft/item/Item","net/minecraft/item/Item$Properties",
                "net/minecraft/item/ArmorMaterial","net/minecraft/item/IArmorMaterial","net/minecraft/inventory/EquipmentSlotType",
                "net/minecraftforge/registries/DeferredRegister","net/minecraftforge/registries/ForgeRegistries",
                "net/minecraftforge/registries/IForgeRegistry","net/minecraftforge/fml/RegistryObject",
                "net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext","net/minecraftforge/eventbus/api/IEventBus",
                "net/minecraftforge/fml/common/Mod");
        for(String root:roots)if(!inputs.legacyApi().hasClass(root))throw failure("Source linkage root absent: "+root);
        return new LogicApiNamespace(inputs.legacyOfficial(),inputs.legacySrg(),inputs.legacyApi(),true).normalize(roots);
    }
    public static Report audit(MappingInputs inputs) { return inspect(inputs); }
    public static Report inspect(MappingInputs inputs) {
        complete(inputs);var old=new Side(inputs,true);var target=new Side(inputs,false);
        old.hierarchy();target.hierarchy();
        var hierarchy=new ArrayList<Map<String,Object>>();
        for(String canonical:List.of(ARMOR,ITEM,MATERIAL,MATERIALS,SLOT,PROPS,VANISHABLE))
            hierarchy.add(Map.of("canonical",canonical,"source",old.type(canonical),"target",target.type(canonical)));
        hierarchy.add(Map.of("role","armorInterfaceChanged","source",old.type(WEARABLE),"target",target.type(EQUIPABLE)));
        hierarchy.add(Map.of("role","targetConstructorEquipmentType","target",target.type(ARMOR_TYPE),"sourceEquivalent","NONE_AUTOMATIC"));
        var declarations=new ArrayList<Map<String,Object>>();
        declarations.add(Map.of("role","contextualConstructor","source",old.member(ARMOR,"<init>",constructor(true),ACC_PUBLIC,INVOKESPECIAL,false),
                "target",target.member(ARMOR,"<init>",constructor(false),ACC_PUBLIC,INVOKESPECIAL,false),"evidenceKind","DECLARATION_ONLY",
                "automaticEquivalence",false));
        declarations.add(Map.of("role","contextualConstructorEquipmentArgument","source",old.member(SLOT,"HEAD",ref(SLOT),ACC_PUBLIC|ACC_STATIC|ACC_FINAL|ACC_ENUM,GETSTATIC,true),
                "target",target.member(ARMOR_TYPE,"HELMET",ref(ARMOR_TYPE),ACC_PUBLIC|ACC_STATIC|ACC_FINAL|ACC_ENUM,GETSTATIC,true),
                "evidenceKind","DECLARATION_ONLY","globalRenameAllowed",false));
        pair(declarations,old,target,ITEM,"<init>","("+ref(PROPS)+")V",ACC_PUBLIC,INVOKESPECIAL);
        pair(declarations,old,target,PROPS,"<init>","()V",ACC_PUBLIC,INVOKESPECIAL);
        field(declarations,old,target,MATERIALS,"IRON",ref(MATERIALS),ACC_PUBLIC|ACC_STATIC|ACC_FINAL|ACC_ENUM,GETSTATIC);
        // HEAD remains an equipment slot on the target; replacing every HEAD with HELMET is invalid.
        field(declarations,old,target,SLOT,"HEAD",ref(SLOT),ACC_PUBLIC|ACC_STATIC|ACC_FINAL|ACC_ENUM,GETSTATIC);
        for(String name:List.of("defaultDurability","durability"))pair(declarations,old,target,PROPS,name,"(I)"+ref(PROPS),ACC_PUBLIC,INVOKEVIRTUAL);
        for(String name:List.of("getDefense","getEnchantmentValue"))pair(declarations,old,target,ARMOR,name,"()I",ACC_PUBLIC,INVOKEVIRTUAL);
        pair(declarations,old,target,ARMOR,"getToughness","()F",ACC_PUBLIC,INVOKEVIRTUAL);
        pair(declarations,old,target,ARMOR,"getMaterial","()"+ref(MATERIAL),ACC_PUBLIC,INVOKEVIRTUAL);
        pair(declarations,old,target,ARMOR,"use",useDescriptor(),ACC_PUBLIC,INVOKEVIRTUAL);
        pair(declarations,old,target,ARMOR,"getDefaultAttributeModifiers","("+ref(SLOT)+")Lcom/google/common/collect/Multimap;",ACC_PUBLIC,INVOKEVIRTUAL);
        for(String owner:List.of(ARMOR,ITEM))pair(declarations,old,target,owner,"isValidRepairItem","("+ref(STACK)+ref(STACK)+")Z",ACC_PUBLIC,INVOKEVIRTUAL);
        for(String owner:List.of(MATERIAL,MATERIALS)) {
            int access=ACC_PUBLIC|(owner.equals(MATERIAL)?ACC_ABSTRACT:0),opcode=owner.equals(MATERIAL)?INVOKEINTERFACE:INVOKEVIRTUAL;
            for(var entry:Map.of("getEnchantmentValue","()I","getToughness","()F","getKnockbackResistance","()F",
                    "getEquipSound","()"+ref(SOUND),"getRepairIngredient","()"+ref(INGREDIENT)).entrySet().stream().sorted(Map.Entry.comparingByKey()).toList())
                pair(declarations,old,target,owner,entry.getKey(),entry.getValue(),access,opcode);
            for(String role:List.of("Durability","Defense"))
                declarations.add(Map.of("role",owner+".get"+role+"ForSlot-to-Type",
                        "source",old.member(owner,"get"+role+"ForSlot","("+ref(SLOT)+")I",access,opcode,false),
                        "target",target.member(owner,"get"+role+"ForType","("+ref(ARMOR_TYPE)+")I",access,opcode,false),
                        "evidenceKind","DECLARATION_ONLY","automaticEquivalence",false));
        }
        field(declarations,old,target,ARMOR,"DISPENSE_ITEM_BEHAVIOR",ref(DISPENSE),ACC_PUBLIC|ACC_STATIC|ACC_FINAL,GETSTATIC);
        pair(declarations,old,target,ARMOR,"dispenseArmor","("+ref(BLOCK_SOURCE)+ref(STACK)+")Z",ACC_PUBLIC|ACC_STATIC,INVOKESTATIC);
        pair(declarations,old,target,DISPENSER,"registerBehavior","("+ref("net/minecraft/world/level/ItemLike")+ref(DISPENSE)+")V",ACC_PUBLIC|ACC_STATIC,INVOKESTATIC);
        var targetOnly=new ArrayList<Map<String,Object>>();
        targetOnly.add(target.member(ARMOR,"getType","()"+ref(ARMOR_TYPE),ACC_PUBLIC,INVOKEVIRTUAL,false));
        targetOnly.add(target.member(ARMOR,"getEquipmentSlot","()"+ref(SLOT),ACC_PUBLIC,INVOKEVIRTUAL,false));
        targetOnly.add(target.member(ARMOR,"getEquipSound","()"+ref(SOUND),ACC_PUBLIC,INVOKEVIRTUAL,false));
        targetOnly.add(target.member(ARMOR_TYPE,"getSlot","()"+ref(SLOT),ACC_PUBLIC,INVOKEVIRTUAL,false));
        targetOnly.add(target.member(EQUIPABLE,"getEquipmentSlot","()"+ref(SLOT),ACC_PUBLIC|ACC_ABSTRACT,INVOKEINTERFACE,false));
        targetOnly.add(target.member(EQUIPABLE,"getEquipSound","()"+ref(SOUND),ACC_PUBLIC,INVOKEINTERFACE,false));
        targetOnly.add(target.member(EQUIPABLE,"swapWithEquipmentSlot","("+ref(ITEM)+ref(LEVEL)+ref(PLAYER)+ref(HAND)+")"+ref(HOLDER),ACC_PUBLIC,INVOKEINTERFACE,false));
        targetOnly.add(target.member(STACK,"copyAndClear","()"+ref(STACK),ACC_PUBLIC,INVOKEVIRTUAL,false));
        List<Map<String,Object>> provenance=inputs.sources().stream().map(s->Map.<String,Object>of("id",s.id(),"sha256",s.sha256(),"origin",s.origin())).toList();
        return new Report(hierarchy,declarations,targetOnly,provenance);
    }

    public record Report(List<Map<String,Object>> hierarchy,List<Map<String,Object>> declarations,
                         List<Map<String,Object>> targetOnlyDeclarations,List<Map<String,Object>> suppliedProvenance) {
        public Report { hierarchy=freezeEntries(hierarchy);declarations=freezeEntries(declarations);
            targetOnlyDeclarations=freezeEntries(targetOnlyDeclarations);suppliedProvenance=freezeEntries(suppliedProvenance); }
        public Map<String,Object> toTree() {
            var tree=new LinkedHashMap<String,Object>();tree.put("schemaVersion",1);tree.put("audit","IRON_HELMET_SEMANTIC_INSPECTION");
            tree.put("status","UNSUPPORTED");tree.put("declarationStatus","CHECKED_EXACT_DECLARATIONS");tree.put("candidateGenerated",false);
            tree.put("mappingRulesEmitted",0);tree.put("classesExecuted",false);tree.put("nativeValidation","NOT_RUN");
            tree.put("hierarchy",hierarchy);tree.put("declarations",declarations);tree.put("targetOnlyDeclarations",targetOnlyDeclarations);
            tree.put("suppliedProvenance",suppliedProvenance);tree.put("knownFacts",reviewedFacts());tree.put("semanticBlockers",blockers());
            tree.put("availableRuntimeOperation",Map.of("adapter",AdapterCatalog.require(AdapterCatalog.IRON_HELMET_EMPTY_SLOT).referenceTree(),
                    "scope","Fixed iron helmet empty-slot use; not a conversion recipe or inherited-behavior qualification",
                    "conversionRecipeImplemented",false,"runtimeArtifactVerified",false,"nativeValidation","NOT_RUN"));
            tree.put("availableHeadEquipEffect",Map.of("adapter",AdapterCatalog.require(AdapterCatalog.IRON_HELMET_HEAD_EQUIP_EFFECTS).referenceTree(),
                    "scope","Fixed HEAD sound-before-write effect with exact native player, slot and stack guards; not wired into use",
                    "conversionRecipeImplemented",false,"composedWithUse",false,"runtimeArtifactVerified",false,"nativeValidation","NOT_RUN",
                    "preconditions",List.of("Non-null player, HEAD slot and stack",
                            "Exact ServerPlayer, or physical-client-only exact LocalPlayer/RemotePlayer; custom subclasses rejected",
                            "Empty stack, or one item of the final LegacyIronHelmetItem type; no arbitrary armor",
                            "Copy ownership, native thread, caller closure and external sound listeners are not established by these guards")));
            tree.put("availableDispenserOperation",Map.of("adapter",AdapterCatalog.require(AdapterCatalog.IRON_HELMET_DISPENSER).referenceTree(),
                    "scope","Separate first-eligible HEAD dispenser operation for exact ServerPlayer or Zombie; no item registration",
                    "conversionRecipeImplemented",false,"registeredForItem",false,"runtimeArtifactVerified",false,"nativeValidation","NOT_RUN",
                    "usesPlayerHeadEffect",AdapterCatalog.require(AdapterCatalog.IRON_HELMET_HEAD_EQUIP_EFFECTS).referenceTree(),
                    "preconditions",List.of("Non-null source and nonempty stack of the final LegacyIronHelmetItem",
                            "First selected receiver must be exact ServerPlayer or Zombie; resolved slot must be HEAD",
                            "Zombie armor list must be exact NonNullList with size four, checked before split",
                            "No eligible result is false; unsupported first receiver throws before split and is never skipped",
                            "Native world query enumeration, caller thread, copy hooks and sound events remain unqualified")));
            tree.put("limitations",List.of("Declaration/access/opcode agreement is not behavioral equivalence or MappingConfidence.VERIFIED.",
                    "ApiIndex has no bodies: its supplied provenance does not authenticate historical body facts or caller-provided declarations.",
                    "HEAD-to-HELMET is a contextual constructor contract, never a global equipment-slot mapping.",
                    "Inherited Item/Forge behavior, equipment hooks/sounds, dispenser lifecycle, enchantments, anvil, reload, save/load and multiplayer remain unqualified.",
                    "Fixed empty-slot and guarded HEAD-effect operations exist separately, but no composed use, constructor recipe, candidate or public-profile expansion is implemented.",
                    "The HEAD effect preserves native sound dispatch before the armor write, not equivalent Forge sound events or arbitrary player overrides.",
                    "The dispenser operation is not registered for an item; its native query uses a different target spatial engine and does not qualify cross-version enumeration order.",
                    "The available operation names the compiled project catalog, not verification of an installed runtime artifact or native behavior."));
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
        facts.put("scope","Native IRON HEAD/HELMET and fresh Item.Properties; no subclass, custom material or added properties");
        facts.put("ironHelmet",Map.of("baseDurability",11,"durabilityMultiplier",15,"maxDamage",165,"maxStackSize",1,"defense",2,
                "toughness",0.0F,"knockbackResistance",0.0F,"enchantmentValue",9,"repairIngredient","minecraft:iron_ingot"));
        facts.put("attributes",Map.of("slot","HEAD","operation","ADDITION","uuid","2ad3f246-fee1-4e67-b886-69fd380bb150",
                "armor",2.0D,"armorToughness",0.0D,"knockbackModifierPresent",false));
        facts.put("equipSound","minecraft:item.armor.equip_iron; material declaration is not whole equipment-sound path qualification");
        facts.put("construction","Both constructors apply material default durability, register native dispenser behavior and build native modifiers; slot-index arrays become armor-type EnumMaps.");
        facts.put("repair","Material ingredient predicate then native superclass fallback; no proof of anvil cost or ingredient consumption.");
        facts.put("sourceUse","Occupied slot FAIL; otherwise equip a copy, clear received hand stack count and return sidedSuccess with the same cleared hand stack. No direct ITEM_USED award in this body.");
        facts.put("targetUse","Delegates to Equipable.swapWithEquipmentSlot: binding curse or matching stacks FAIL; otherwise server ITEM_USED award, copyAndClear hand/equipped stacks, equip hand copy and return prior equipment copy (or cleared original hand when empty).");
        facts.put("aliasDifference","With occupied unbound nonmatching equipment, target returns a different former-equipment stack; legacy returns FAIL with original hand untouched. Inventory/packet callers are not simulated here.");
        facts.put("headSetterOrder","Source PlayerEntity calls playEquipSound before inventory.armor.set; target Player verifies, writes and calls onEquipItem. The separate guarded HEAD operation is not composed with use.");
        facts.put("soundEventDifference","Source server PlaySoundAtEntityEvent does not apply its event pitch; target PlayLevelSoundEvent.AtPosition applies newPitch and uses seeded sound. Source local-client PlaySoundAtEntityEvent becomes PlayLevelSoundEvent.AtEntity. Preserving virtual playSound dispatch is not arbitrary-listener equivalence.");
        facts.put("dispenserOrder","Both native bodies query the facing block using NO_SPECTATORS and armor eligibility, take only the first result, resolve slot, split(1), equip, then set Mob drop chance 2 and persistence.");
        facts.put("dispenserEligibility","Native Player requires an empty resolved slot; native Zombie inherits Mob's additional canPickUpLoot requirement. Alive and spectator filters remain native.");
        facts.put("dispenserMigrationBoundary","Source World implements a chunk query; target EntityGetter delegates through EntityTypeTest. Source Mob setter is silent; target adds verify/onEquipItem. The restricted operation retains target result order and a silent exact Zombie list write, not world-query equivalence.");
        return Collections.unmodifiableMap(facts);
    }
    private static List<Map<String,Object>> blockers() {return List.of(
            blocker("HELMET_EQUIPMENT_TYPE_CONTRACT_CHANGED","ArmorItem(IArmorMaterial, EquipmentSlotType.HEAD, Properties)","ArmorItem(ArmorMaterial, ArmorItem.Type.HELMET, Properties)",
                    "No implemented contextual constructor recipe; HEAD remains a target EquipmentSlot and cannot be globally renamed to HELMET."),
            blocker("HELMET_USE_CONTRACT_CHANGED","Only an empty equipment slot is equipped; occupied returns FAIL without inventory writes",
                    "Occupied equipment can swap; binding curse/stack-match guards, server statistic and copyAndClear return alias differ",
                    "A fixed empty-slot runtime operation is implemented but not bound to a conversion recipe; native swap remains non-equivalent and inherited paths remain unqualified."),
            blocker("HELMET_INHERITED_BEHAVIOR_UNQUALIFIED","Wearable marker and legacy Item/Forge behavior","Equipable defaults plus changed Item/Forge contracts",
                    "Durability/modifier/material facts do not qualify equipment/dispenser hooks, sounds, enchantments, anvil, wear, save/load, client/server or multiplayer."));}
    private static Map<String,Object> blocker(String id,String source,String target,String reason) {return Map.of("id",id,"status","UNSUPPORTED","source",source,"target",target,"reason",reason,"implementedRecipe",false);}
    private static String constructor(boolean old){return "("+ref(MATERIAL)+ref(old?SLOT:ARMOR_TYPE)+ref(PROPS)+")V";}
    private static String useDescriptor(){return "("+ref(LEVEL)+ref(PLAYER)+ref(HAND)+")"+ref(HOLDER);}
    private static void pair(List<Map<String,Object>> entries,Side old,Side target,String owner,String name,String desc,int access,int opcode) {
        entries.add(Map.of("canonicalOwner",owner,"canonicalName",name,"canonicalDescriptor",desc,"source",old.member(owner,name,desc,access,opcode,false),
                "target",target.member(owner,name,desc,access,opcode,false),"evidenceKind","DECLARATION_ONLY"));
    }
    private static void field(List<Map<String,Object>> entries,Side old,Side target,String owner,String name,String desc,int access,int opcode) {
        entries.add(Map.of("canonicalOwner",owner,"canonicalName",name,"canonicalDescriptor",desc,"source",old.member(owner,name,desc,access,opcode,true),
                "target",target.member(owner,name,desc,access,opcode,true),"evidenceKind","DECLARATION_ONLY"));
    }
    private static final class Side {
        final boolean old;final LogicApiNamespace ns;final ApiIndex development,index;
        Side(MappingInputs inputs,boolean old) {
            this.old=old;development=old?inputs.legacyApi():inputs.targetApi();
            ns=new LogicApiNamespace(old?inputs.legacyOfficial():inputs.targetOfficial(),old?inputs.legacySrg():inputs.targetSrg(),development,old);
            var roots=new TreeSet<String>();for(String canonical:List.of(ARMOR,ITEM,MATERIAL,MATERIALS,SLOT,PROPS,VANISHABLE,STACK,DISPENSER))roots.add(ns.productionClass(canonical));
            roots.add(ns.productionClass(old?WEARABLE:EQUIPABLE));if(!old)roots.add(ns.productionClass(ARMOR_TYPE));
            index=ns.normalize(roots);
        }
        void hierarchy() {
            exact(ARMOR,ACC_PUBLIC,ns.productionClass(ITEM),Set.of(ns.productionClass(old?WEARABLE:EQUIPABLE)));
            exact(MATERIAL,ACC_PUBLIC|ACC_INTERFACE|ACC_ABSTRACT,"java/lang/Object",Set.of());
            exact(MATERIALS,ACC_PUBLIC|ACC_FINAL|ACC_ENUM,"java/lang/Enum",old?Set.of(ns.productionClass(MATERIAL)):
                    Set.of(ns.productionClass(MATERIAL),"net/minecraft/util/StringRepresentable"));
            exact(SLOT,ACC_PUBLIC|ACC_FINAL|ACC_ENUM,"java/lang/Enum",Set.of());
            exact(PROPS,ACC_PUBLIC,"java/lang/Object",Set.of());
            exact(VANISHABLE,ACC_PUBLIC|ACC_INTERFACE|ACC_ABSTRACT,"java/lang/Object",Set.of());
            exact(old?WEARABLE:EQUIPABLE,ACC_PUBLIC|ACC_INTERFACE|ACC_ABSTRACT,"java/lang/Object",Set.of(ns.productionClass(VANISHABLE)));
            exact(ITEM,ACC_PUBLIC,old?"net/minecraftforge/registries/ForgeRegistryEntry":"java/lang/Object",
                    old?Set.of("net/minecraft/util/IItemProvider","net/minecraftforge/common/extensions/IForgeItem"):
                            Set.of("net/minecraft/world/flag/FeatureElement","net/minecraft/world/level/ItemLike","net/minecraftforge/common/extensions/IForgeItem"));
            exact(STACK,ACC_PUBLIC|ACC_FINAL,"net/minecraftforge/common/capabilities/CapabilityProvider",Set.of("net/minecraftforge/common/extensions/IForgeItemStack"));
            if(!require(ns.productionClass(VANISHABLE)).methods().isEmpty()||old&&!require(ns.productionClass(WEARABLE)).methods().isEmpty())throw failure("Source marker interface contract changed");
            if(!old)exact(ARMOR_TYPE,ACC_PUBLIC|ACC_FINAL|ACC_ENUM,"java/lang/Enum",Set.of());
        }
        void exact(String canonical,int access,String parent,Set<String> interfaces) {
            var c=require(ns.productionClass(canonical));
            if((c.access()&CLASS_MASK)!=access||!parent.equals(c.superName())||c.interfaces().size()!=interfaces.size()||!new HashSet<>(c.interfaces()).equals(interfaces))
                throw failure("Hierarchy/access contract changed on "+side()+": "+canonical);
        }
        Map<String,Object> type(String canonical){var c=require(ns.productionClass(canonical));return Map.of("owner",c.name(),"access",c.access(),"superclass",Objects.toString(c.superName(),""),"interfaces",c.interfaces());}
        Map<String,Object> member(String owner,String name,String desc,int access,int opcode,boolean field) {
            String productionOwner=ns.productionClass(owner),productionName=field?ns.productionField(owner,name,desc):ns.productionMethod(owner,name,desc),descriptor=ns.productionDescriptor(desc);
            var c=require(productionOwner);var matches=(field?c.fields():c.methods()).stream().filter(m->m.name().equals(productionName)&&m.descriptor().equals(descriptor)).toList();
            if(matches.size()!=1||(matches.get(0).access()&(field?FIELD_MASK:METHOD_MASK))!=access)
                throw failure("Exact declaration/access/opcode changed on "+side()+": "+productionOwner+"."+productionName+descriptor);
            if(!field&&((opcode==INVOKEINTERFACE)!=((c.access()&ACC_INTERFACE)!=0)))throw failure("Invocation owner kind changed: "+productionOwner);
            if(((opcode==INVOKESTATIC||opcode==GETSTATIC)?ACC_STATIC:0)!=(access&ACC_STATIC))throw failure("Invocation staticness mismatch: "+productionOwner);
            descriptorTypes(Type.getType(descriptor));
            return Map.of("owner",productionOwner,"name",productionName,"descriptor",descriptor,"access",matches.get(0).access(),"expectedOpcode",opcode,"kind",field?"FIELD":"METHOD");
        }
        void descriptorTypes(Type type) {
            if(type.getSort()==Type.METHOD){descriptorTypes(type.getReturnType());for(Type arg:type.getArgumentTypes())descriptorTypes(arg);return;}
            if(type.getSort()==Type.ARRAY){descriptorTypes(type.getElementType());return;}
            if(type.getSort()!=Type.OBJECT)return;String owner=type.getInternalName();
            if(!owner.startsWith("net/minecraft/")&&!owner.startsWith("net/minecraftforge/"))return;
            var found=development.getClass(owner);if(found==null||(found.access()&ACC_PUBLIC)==0)throw failure("Required public descriptor type absent on "+side()+": "+owner);
        }
        ApiIndex.ApiClass require(String owner){var c=index.getClass(owner);if(c==null||(c.access()&ACC_PUBLIC)==0)throw failure("Required public declaration absent on "+side()+": "+owner);return c;}
        String side(){return old?"source":"target";}
    }
    private static void complete(MappingInputs inputs){Objects.requireNonNull(inputs,"inputs");if(!inputs.complete()||inputs.scope()!=MappingInputs.Scope.FULL_ENVIRONMENT)throw failure("Complete explicit source/target mapping and API inputs are required");}
    private static List<Map<String,Object>> freezeEntries(List<Map<String,Object>> entries){return entries.stream().map(IronHelmetApiAudit::freezeMap).toList();}
    private static Map<String,Object> freezeMap(Map<String,Object> values){var out=new LinkedHashMap<String,Object>();values.forEach((k,v)->out.put(Objects.requireNonNull(k),freeze(v)));return Collections.unmodifiableMap(out);}
    private static Object freeze(Object value){if(value instanceof Map<?,?> map){var out=new LinkedHashMap<String,Object>();map.forEach((k,v)->{if(!(k instanceof String text))throw new IllegalArgumentException("String report keys required");out.put(text,freeze(v));});return Collections.unmodifiableMap(out);}
        if(value instanceof List<?> list)return list.stream().map(IronHelmetApiAudit::freeze).toList();
        if(value instanceof String||value instanceof Boolean||value instanceof Integer||value instanceof Long||value instanceof Float||value instanceof Double)return value;
        throw new IllegalArgumentException("Unsupported report value: "+Objects.toString(value));}
    private static String ref(String owner){return "L"+owner+";";}
    private static TransformationException failure(String reason){return new TransformationException("IRON_HELMET_API_AUDIT: "+reason);}
}
