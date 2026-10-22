package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.converter.conversion.extract.ContentPlan;
import dev.legacybridge.converter.conversion.extract.SubsetRejectionException;
import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.operation.OperationStoppedException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.CRC32;

/**
 * Fail-closed validation of the first content language's unchanged resource bytes.
 * This is deliberately not a general resource-pack validator. In particular, custom
 * model geometry, state properties, animation metadata, serializers and codecs are
 * refused rather than silently copied into a supposedly supported converted mod.
 */
public final class ContentResourceValidator {
    private static final int MAX_JSON_BYTES = 1_048_576;
    private static final int MAX_RESOURCE_BYTES = 16 * 1024 * 1024;
    private static final Set<String> VANILLA_ITEMS = Set.of("stone", "dirt", "cobblestone", "oak_planks",
            "stick", "iron_ingot", "gold_ingot", "diamond", "coal", "redstone", "sand", "glass");
    private static final Set<String> VANILLA_BLOCKS = Set.of("stone", "dirt", "cobblestone", "oak_planks",
            "sand", "glass");
    private static final Set<String> VANILLA_MODELS = Set.of("item/generated", "item/handheld", "block/cube_all");
    private static final Set<String> VANILLA_TEXTURES = Set.of("block/stone", "block/dirt", "block/cobblestone",
            "block/oak_planks", "block/sand", "block/glass", "item/iron_ingot", "item/gold_ingot",
            "item/diamond", "item/stick", "item/coal", "item/redstone");
    private static final Set<String> VANILLA_SOUND_EVENTS = Set.of("block.note_block.pling",
            "block.stone.place", "block.stone.break", "block.wood.place", "block.wood.break",
            "entity.experience_orb.pickup", "ui.button.click");
    private static final byte[] PNG_SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    private final Map<String, byte[]> entries;
    private final ContentPlan plan;
    private final Set<String> items = new HashSet<>();
    private final Set<String> blocks = new HashSet<>();
    private final Map<String, Map<String, Object>> models = new TreeMap<>();
    private final Map<String, Map<String, Object>> soundEvents = new TreeMap<>();
    private final Map<String, List<String>> tagEdges = new HashMap<>();
    private final Map<String, List<String>> soundEdges = new HashMap<>();
    private final boolean datapackRecipes;
    private final boolean horizontalBlock;
    private final boolean staticShapeBlock;
    private final boolean booleanToggleBlock;
    private final Set<String> tagsWithValues = new HashSet<>();
    private final List<Map<String, Object>> recipeDecisions = new ArrayList<>();

    private ContentResourceValidator(Map<String, byte[]> entries, ContentPlan plan) {
        this(entries, plan, false);
    }

    private ContentResourceValidator(Map<String, byte[]> entries, ContentPlan plan, boolean datapackRecipes) {
        this(entries, plan, datapackRecipes, false);
    }

    private ContentResourceValidator(Map<String, byte[]> entries, ContentPlan plan, boolean datapackRecipes, boolean horizontalBlock) {
        this(entries,plan,datapackRecipes,horizontalBlock,false);
    }

    private ContentResourceValidator(Map<String,byte[]> entries,ContentPlan plan,boolean datapackRecipes,boolean horizontalBlock,boolean staticShapeBlock) {
        this(entries,plan,datapackRecipes,horizontalBlock,staticShapeBlock,false);
    }
    private ContentResourceValidator(Map<String,byte[]> entries,ContentPlan plan,boolean datapackRecipes,boolean horizontalBlock,boolean staticShapeBlock,boolean booleanToggleBlock) {
        if((horizontalBlock ? 1 : 0)+(staticShapeBlock ? 1 : 0)+(booleanToggleBlock ? 1 : 0)>1)
            throw new IllegalArgumentException("Independent resource contracts");
        this.booleanToggleBlock=booleanToggleBlock;
        this.staticShapeBlock=staticShapeBlock;
        this.horizontalBlock = horizontalBlock;
        this.entries = entries;
        this.plan = plan;
        this.datapackRecipes = datapackRecipes;
        plan.items().forEach(item -> items.add(qualified(item.name())));
        plan.blocks().forEach(block -> blocks.add(qualified(block.name())));
    }

    public static void validate(Map<String, byte[]> entries, ContentPlan plan) throws SubsetRejectionException {
        new ContentResourceValidator(entries, plan).validate();
    }

    /** Separate pinned data policy. Existing callers retain the original resource language. */
    public static List<Map<String, Object>> validateDatapackRecipes(Map<String, byte[]> entries, ContentPlan plan)
            throws SubsetRejectionException {
        var validator = new ContentResourceValidator(entries, plan, true);
        validator.validate();
        return List.copyOf(validator.recipeDecisions);
    }

    /** Private state contract only; existing simple and datapack languages remain unchanged. */
    static List<Map<String,Object>> validateHorizontalRecipes(Map<String,byte[]> entries, ContentPlan plan)
            throws SubsetRejectionException {
        if (plan.blocks().size() != 1) throw new SubsetRejectionException("HORIZONTAL_RESOURCE: exactly one proved block");
        var validator = new ContentResourceValidator(entries, plan, true, true);
        validator.validate();
        return List.copyOf(validator.recipeDecisions);
    }

    /** Exact finite geometry language; never an expansion of the public resource policy. */
    static List<Map<String,Object>> validateStaticShapeRecipes(Map<String,byte[]> entries,ContentPlan plan)
            throws SubsetRejectionException {
        if(plan.blocks().size()!=1 || plan.items().size()!=1 || !plan.sounds().isEmpty()
            || !plan.items().get(0).name().equals(plan.blocks().get(0).name())
            || !(plan.modId()+":"+plan.blocks().get(0).name()).equals(plan.items().get(0).block()))
            throw new SubsetRejectionException("STATIC_SHAPE_RESOURCE: exactly one proved Block/BlockItem");
        var validator=new ContentResourceValidator(entries,plan,true,false,true);
        validator.validate();
        return List.copyOf(validator.recipeDecisions);
    }

    /** Exact two-state visual graph; does not widen the public resource policy. */
    static List<Map<String,Object>> validateBooleanToggleRecipes(Map<String,byte[]> entries,ContentPlan plan)
            throws SubsetRejectionException {
        if(plan.blocks().size()!=1 || plan.items().size()!=1 || !plan.sounds().isEmpty()
            || !plan.items().get(0).name().equals(plan.blocks().get(0).name())
            || !(plan.modId()+":"+plan.blocks().get(0).name()).equals(plan.items().get(0).block()))
            throw new SubsetRejectionException("BOOLEAN_TOGGLE_RESOURCE: exactly one proved Block/BlockItem");
        var validator=new ContentResourceValidator(entries,plan,true,false,false,true);
        validator.validate();
        return List.copyOf(validator.recipeDecisions);
    }

    private void validate() throws SubsetRejectionException {
        if(staticShapeBlock) StaticShapeLootContract.inspect(entries,plan);
        // Index first so references are independent of ZIP entry order.
        for (var entry : new TreeMap<>(entries).entrySet()) {
            OperationControl.checkpoint();
            String path = entry.getKey();
            if (!safePath(path)) reject(path, "invalid or ambiguous resource path");
            if (entry.getValue().length > MAX_RESOURCE_BYTES) reject(path, "resource exceeds 16 MiB limit");
            if (path.startsWith("assets/" + plan.modId() + "/models/") && path.endsWith(".json")) {
                models.put(modelId(path), jsonObject(path));
            }
            if (path.equals("assets/" + plan.modId() + "/sounds.json")) {
                for (var event : jsonObject(path).entrySet()) {
                    String id = id(event.getKey(), plan.modId(), path);
                    if (!namespace(id).equals(plan.modId())) reject(path, "sound event must use the mod namespace");
                    soundEvents.put(id, object(event.getValue(), path, "sound event"));
                }
            }
        }
        if(staticShapeBlock) {
            String name=plan.blocks().get(0).name();
            if(!models.keySet().equals(Set.of(qualified("block/"+name),qualified("item/"+name))))
                reject("models","Fixed-shape model graph must contain exactly the registered block and its item");
        }
        if(booleanToggleBlock) {
            String name=plan.blocks().get(0).name();
            if(!models.keySet().equals(Set.of(qualified("block/"+name),qualified("block/"+name+"_active"),qualified("item/"+name))))
                reject("models","Boolean toggle requires exactly its two block models and matching item model");
        }
        for (String path : new TreeMap<>(entries).keySet()) {
            OperationControl.checkpoint();
            if (ignored(path)) continue;
            if (path.startsWith("assets/")) validateAsset(path);
            else if (path.startsWith("data/")) validateData(path);
            else reject(path, "resource kind is outside SIMPLE_CONTENT_V1");
        }
        for (String item : items) require("assets/" + plan.modId() + "/models/item/" + value(item) + ".json", item);
        for (String block : blocks) require("assets/" + plan.modId() + "/blockstates/" + value(block) + ".json", block);
        for (var sound : plan.sounds()) {
            if (!soundEvents.containsKey(sound.resourceLocation())) {
                reject("assets/" + plan.modId() + "/sounds.json", "registered sound has no definition: " + sound.resourceLocation());
            }
        }
        for (String model : models.keySet()) validateModelChain(model, new HashSet<>(), new LinkedHashMap<>(), 0);
        rejectCycles(tagEdges, "tag");
        if (datapackRecipes) for (String tag : tagEdges.keySet()) {
            if (!hasTagValues(tag, new HashSet<>(), 0)) reject(tag, "Empty or transitively empty tags differ between source and target; unsupported");
        }
        rejectCycles(soundEdges, "sound event");
    }

    private boolean hasTagValues(String tag, Set<String> visited, int depth) throws SubsetRejectionException {
        OperationControl.checkpoint();
        if (depth > 64) reject(tag, "tag reference depth limit");
        if (!visited.add(tag)) return false;
        if (tagsWithValues.contains(tag)) return true;
        for (String edge : tagEdges.getOrDefault(tag, List.of())) if (hasTagValues(edge, visited, depth + 1)) return true;
        return false;
    }

    private void validateAsset(String path) throws SubsetRejectionException {
        if (!path.matches("[a-z0-9_./-]+")) reject(path, "asset paths must be lowercase resource locations");
        String[] parts = path.split("/", 3);
        if (parts.length < 3 || !parts[1].equals(plan.modId())) reject(path, "assets must belong to the source mod namespace");
        String relative = parts[2];
        if (relative.startsWith("lang/") && relative.endsWith(".json")) {
            if (!relative.matches("lang/[a-z]{2}_[a-z]{2}\\.json")) reject(path, "unsupported language file name");
            for (var translation : jsonObject(path).entrySet()) string(translation.getValue(), path, "translation");
        } else if ((relative.startsWith("models/item/") || relative.startsWith("models/block/")) && relative.endsWith(".json")) {
            validateModel(path, models.get(modelId(path)));
        } else if (relative.startsWith("blockstates/") && relative.endsWith(".json")) {
            String block = qualified(relative.substring("blockstates/".length(), relative.length() - 5));
            if (!blocks.contains(block)) reject(path, "blockstate has no registered plain block: " + block);
            validateBlockstate(path);
        } else if ((relative.startsWith("textures/item/") || relative.startsWith("textures/block/")) && relative.endsWith(".png")) {
            validatePng(path, entries.get(path));
        } else if (relative.equals("sounds.json")) {
            for (var sound : soundEvents.entrySet()) validateSound(path, sound.getKey(), sound.getValue());
        } else if (relative.startsWith("sounds/") && relative.endsWith(".ogg")) {
            reject(path, "Local Ogg/Vorbis files are unsupported until bounded audio decoding is implemented; use audited vanilla sound-event aliases");
        } else {
            reject(path, "unsupported asset (including animation metadata, custom shaders, or custom resource loaders)");
        }
    }

    private void validateModel(String path, Map<String, Object> model) throws SubsetRejectionException {
        if(booleanToggleBlock) { validateBooleanToggleModel(path,model); return; }
        if(staticShapeBlock) { validateStaticShapeModel(path,model); return; }
        keys(model, path, "parent", "textures", "ambientocclusion");
        String parent = id(string(model.get("parent"), path, "parent"), "minecraft", path);
        if (namespace(parent).equals("minecraft")) {
            if (!VANILLA_MODELS.contains(value(parent))) reject(path, "vanilla model template is not in the audited first subset: " + parent);
        } else {
            if (!namespace(parent).equals(plan.modId()) || !models.containsKey(parent)) reject(path, "missing or foreign parent model: " + parent);
        }
        if (model.containsKey("ambientocclusion")) bool(model.get("ambientocclusion"), path, "ambientocclusion");
        if (model.containsKey("textures")) {
            for (var texture : object(model.get("textures"), path, "textures").entrySet()) {
                if (!texture.getKey().matches("[a-z0-9_]+")) reject(path, "invalid texture variable");
                String reference = string(texture.getValue(), path, "texture reference");
                if (reference.startsWith("#")) {
                    if (!reference.substring(1).matches("[a-z0-9_]+")) reject(path, "invalid texture alias");
                } else texture(reference, path);
            }
        }
    }

    private void validateModelChain(String model, Set<String> seen, Map<String, String> textures, int depth)
            throws SubsetRejectionException {
        if(staticShapeBlock) return; // Exact two-node graph, parent, texture and all faces checked independently above.
        if (depth > 64 || !seen.add(model)) reject(model, "model parent cycle or depth limit");
        Map<String, Object> definition = models.get(model);
        if (definition.containsKey("textures")) {
            for (var entry : object(definition.get("textures"), model, "textures").entrySet()) {
                textures.putIfAbsent(entry.getKey(), string(entry.getValue(), model, "texture"));
            }
        }
        String parent = id(string(definition.get("parent"), model, "parent"), "minecraft", model);
        if (namespace(parent).equals(plan.modId())) {
            validateModelChain(parent, seen, textures, depth + 1);
            return;
        }
        String required = value(parent).equals("block/cube_all") ? "all" : "layer0";
        if (!textures.containsKey(required)) reject(model, "vanilla parent requires texture variable " + required);
        for (String variable : textures.keySet()) {
            Set<String> aliases = new HashSet<>();
            String reference = textures.get(variable);
            while (reference.startsWith("#")) {
                String next = reference.substring(1);
                if (!aliases.add(next) || !textures.containsKey(next)) reject(model, "missing or cyclic texture alias " + next);
                reference = textures.get(next);
            }
            texture(reference, model);
        }
    }

    private void validateBooleanToggleModel(String path,Map<String,Object> model) throws SubsetRejectionException {
        String name=plan.blocks().get(0).name(), ownId=modelId(path);
        if(ownId.equals(qualified("item/"+name))) {
            if(!model.equals(Map.of("parent",qualified("block/"+name))))
                reject(path,"Toggle item must inherit its inactive block model exactly");
            return;
        }
        boolean active=ownId.equals(qualified("block/"+name+"_active"));
        if(!active && !ownId.equals(qualified("block/"+name)))
            reject(path,"Unknown boolean-toggle model");
        String texture=active ? "minecraft:block/cobblestone" : "minecraft:block/stone";
        if(!model.equals(Map.of("parent","minecraft:block/cube_all","textures",Map.of("all",texture))))
            reject(path,"Exact audited full-cube model and state-specific texture required");
        texture(texture,path);
    }

    private void validateStaticShapeModel(String path,Map<String,Object> model) throws SubsetRejectionException {
        String name=plan.blocks().get(0).name();
        if(modelId(path).equals(qualified("item/"+name))) {
            if(!model.keySet().equals(Set.of("parent"))
                || !qualified("block/"+name).equals(string(model.get("parent"),path,"parent")))
                reject(path,"Fixed-shape item must inherit exactly its proved block model");
            return;
        }
        if(!modelId(path).equals(qualified("block/"+name))
            || !model.keySet().equals(Set.of("parent","textures","elements"))
            || !"minecraft:block/block".equals(string(model.get("parent"),path,"parent")))
            reject(path,"Expected one explicit half-block model with the audited native block parent");
        var textures=object(model.get("textures"),path,"textures");
        if(!textures.equals(Map.of("all","minecraft:block/stone","particle","#all")))
            reject(path,"Only the reviewed stone texture and particle alias in this geometry contract");
        texture("minecraft:block/stone",path);
        var elements=array(model.get("elements"),path,"elements");
        if(elements.size()!=1)reject(path,"Exactly one half-cuboid visual element");
        var element=object(elements.get(0),path,"element");
        if(!element.keySet().equals(Set.of("from","to","faces")))reject(path,"No additional element transforms or shade settings");
        exactVector(element.get("from"),path,"from",0,0,0);
        exactVector(element.get("to"),path,"to",16,8,16);
        var faces=object(element.get("faces"),path,"faces");
        if(!faces.keySet().equals(Set.of("down","up","north","south","west","east")))
            reject(path,"Exactly six faces required");
        for(var entry:faces.entrySet()) {
            var face=object(entry.getValue(),path,"face");
            Map<String,String> expected=entry.getKey().equals("up") ? Map.of("texture","#all")
                : Map.of("texture","#all","cullface",entry.getKey());
            if(!face.equals(expected))reject(path,"Only default UV and exact boundary culling; top is internal and unculled");
        }
    }

    private static void exactVector(Object input,String path,String name,int x,int y,int z) throws SubsetRejectionException {
        var values=array(input,path,name);
        if(values.size()!=3)reject(path,"Three numeric coordinates required");
        int[] expected={x,y,z};
        for(int i=0;i<3;i++) {
            if(integer(values.get(i),path,name,0,16)!=expected[i])
                reject(path,"Visual geometry differs from the proved collision/outline");
        }
    }

    private void validateBlockstate(String path) throws SubsetRejectionException {
        Map<String, Object> root = jsonObject(path);
        if(booleanToggleBlock) {
            if(!root.keySet().equals(Set.of("variants")))reject(path,"Only exact boolean-toggle variants");
            var variants=object(root.get("variants"),path,"variants");
            String name=plan.blocks().get(0).name();
            if(!variants.equals(Map.of("active=false",Map.of("model",qualified("block/"+name)),
                    "active=true",Map.of("model",qualified("block/"+name+"_active")))))
                reject(path,"Exactly active=false/true with corresponding models; no alternatives or transforms");
            return;
        }
        if(staticShapeBlock) {
            if(!root.keySet().equals(Set.of("variants")))reject(path,"Only the fixed default state");
            var variants=object(root.get("variants"),path,"variants");
            if(!variants.keySet().equals(Set.of("")))reject(path,"No state-dependent geometry");
            var variant=object(variants.get(""),path,"default variant");
            if(!variant.equals(Map.of("model",qualified("block/"+plan.blocks().get(0).name()))))
                reject(path,"Exact half model without rotation, alternatives or overrides required");
            return;
        }
        if (horizontalBlock) {
            keys(root, path, "variants");
            Map<String,Object> variants = object(root.get("variants"), path, "variants");
            var rotations = Map.of("facing=north",0,"facing=east",90,"facing=south",180,"facing=west",270);
            if (!variants.keySet().equals(rotations.keySet())) reject(path,"exact four horizontal facing states required");
            String sharedModel = null;
            for (var rotation : rotations.entrySet()) {
                var definition = object(variants.get(rotation.getKey()),path,"horizontal variant");
                keys(definition,path,"model","y");
                int y = definition.containsKey("y") ? integer(definition.get("y"),path,"y",0,270) : 0;
                if (y != rotation.getValue()) reject(path,"horizontal model rotation differs from proved orientation");
                String model = id(string(definition.get("model"),path,"model"),"minecraft",path);
                if (sharedModel != null && !sharedModel.equals(model)) reject(path,"one shared model in finite horizontal contract");
                sharedModel = model;
                variant(definition,path);
            }
            return;
        }
        keys(root, path, "variants", "multipart");
        if (root.containsKey("variants") == root.containsKey("multipart")) reject(path, "choose exactly one variants or multipart definition");
        if (root.containsKey("variants")) {
            Map<String, Object> variants = object(root.get("variants"), path, "variants");
            if (!variants.keySet().equals(Set.of(""))) reject(path, "plain blocks support only the default state; state properties require custom code");
            variant(variants.get(""), path);
        } else {
            List<Object> parts = array(root.get("multipart"), path, "multipart");
            if (parts.isEmpty()) reject(path, "empty multipart model");
            for (Object part : parts) {
                Map<String, Object> definition = object(part, path, "multipart part");
                keys(definition, path, "apply");
                variant(definition.get("apply"), path);
            }
        }
    }

    private void variant(Object value, String path) throws SubsetRejectionException {
        if (value == null) reject(path, "variant apply/model definition is missing");
        List<Object> variants = value instanceof List<?> ? array(value, path, "variants") : List.of(value);
        if (variants.isEmpty()) reject(path, "empty variant alternatives");
        for (Object entry : variants) {
            Map<String, Object> variant = object(entry, path, "variant");
            keys(variant, path, "model", "x", "y", "uvlock", "weight");
            String model = id(string(variant.get("model"), path, "model"), "minecraft", path);
            if (!namespace(model).equals(plan.modId()) || !models.containsKey(model) || !value(model).startsWith("block/")) {
                reject(path, "blockstate requires an existing local block model: " + model);
            }
            for (String axis : List.of("x", "y")) {
                if (variant.containsKey(axis) && integer(variant.get(axis), path, axis, 0, 270) % 90 != 0) reject(path, "model rotation must be a multiple of 90");
            }
            if (variant.containsKey("uvlock")) bool(variant.get("uvlock"), path, "uvlock");
            if (variant.containsKey("weight")) integer(variant.get("weight"), path, "weight", 1, 1000);
        }
    }

    private void validateData(String path) throws SubsetRejectionException {
        if (!path.matches("[a-z0-9_./-]+")) reject(path, "data paths must be lowercase resource locations");
        String[] parts = path.split("/", 3);
        if (parts.length != 3) reject(path, "invalid data path");
        String namespace = parts[1];
        String relative = parts[2];
        if (datapackRecipes && relative.startsWith("tags/") && !namespace.equals(plan.modId()))
            reject(path, "datapack-recipes-v1 requires a closed local tag namespace; external tag composition is unsupported");
        // This validator inspects source resources, before a named target adapter
        // emits any necessary generated tags. Vanilla tag writes can change their
        // meaning across versions even when the JSON bytes remain identical.
        if (namespace.equals("minecraft") && relative.startsWith("tags/")) {
            reject(path, "Source Minecraft tag declarations are unsupported: behavior-changing global tags from 1.16.5 cannot be proven equivalent in 1.20.1");
        }
        if(staticShapeBlock && path.equals("data/"+plan.modId()+"/loot_tables/blocks/"+plan.blocks().get(0).name()+".json")) {
            // Independently parsed above; only this exact optional self-drop path can reach preservation.
            return;
        }
        if (namespace.equals(plan.modId()) && relative.startsWith("recipes/") && relative.endsWith(".json")) {
            validateRecipe(path);
        } else if (Set.of(plan.modId(), "forge").contains(namespace)
                && (relative.startsWith("tags/items/") || relative.startsWith("tags/blocks/")) && relative.endsWith(".json")) {
            validateTag(path, relative.startsWith("tags/items/") ? "items" : "blocks");
        } else reject(path, "data kind or namespace is outside SIMPLE_CONTENT_V1 (worldgen, dimensions, loot tables, functions and custom serializers are unsupported)");
    }

    private void validateRecipe(String path) throws SubsetRejectionException {
        Map<String, Object> recipe = jsonObject(path);
        String type = id(string(recipe.get("type"), path, "type"), "minecraft", path);
        if (datapackRecipes) {
            String recipeId = id(path.substring(("data/" + plan.modId() + "/recipes/").length(), path.length() - 5), plan.modId(), path);
            if (value(recipeId).startsWith("_")) reject(path, "Forge ignores recipe paths starting with underscore in both versions; refusing silent omission");
            if (recipe.containsKey("conditions") || type.equals("forge:conditional"))
                reject(path, "Conditional recipes require source/target reload-context semantics; even false conditions are unsupported, never stripped");
            if (DatapackRecipeContract.COOKING_DEFAULT_TICKS.containsKey(type) || type.equals("minecraft:stonecutting")) {
                validateSingleInputRecipe(path, recipe, type);
                recordRecipe(path, type);
                return;
            }
        }
        if (type.equals("minecraft:crafting_shaped")) {
            keys(recipe, path, "type", "group", "pattern", "key", "result");
            List<Object> rows = array(recipe.get("pattern"), path, "pattern");
            if (rows.isEmpty() || rows.size() > 3) reject(path, "shaped recipe must have 1..3 rows");
            int width = -1;
            Set<String> used = new HashSet<>();
            for (Object row : rows) {
                String text = string(row, path, "pattern row");
                if (text.isEmpty() || text.length() > 3 || (width != -1 && width != text.length())) reject(path, "pattern rows must have equal width 1..3");
                width = text.length();
                for (int i = 0; i < text.length(); i++) if (text.charAt(i) != ' ') used.add(String.valueOf(text.charAt(i)));
            }
            Map<String, Object> key = object(recipe.get("key"), path, "key");
            if (used.isEmpty() || !used.equals(key.keySet())) reject(path, "pattern symbols and ingredient keys do not match");
            for (Object ingredient : key.values()) ingredient(ingredient, path);
        } else if (type.equals("minecraft:crafting_shapeless")) {
            keys(recipe, path, "type", "group", "ingredients", "result");
            List<Object> ingredients = array(recipe.get("ingredients"), path, "ingredients");
            if (ingredients.isEmpty() || ingredients.size() > 9) reject(path, "shapeless recipe must have 1..9 ingredients");
            for (Object ingredient : ingredients) ingredient(ingredient, path);
        } else reject(path, "recipe serializer is unsupported: " + type);
        if (recipe.containsKey("group")) string(recipe.get("group"), path, "group");
        Map<String, Object> result = object(recipe.get("result"), path, "result");
        keys(result, path, "item", "count");
        contentReference(string(result.get("item"), path, "result item"), "items", path);
        if (result.containsKey("count")) integer(result.get("count"), path, "count", 1, 64);
        if (datapackRecipes) recordRecipe(path, type);
    }

    private void recordRecipe(String path, String type) {
        recipeDecisions.add(Map.of("path", path, "format", type, "rule", "resource.recipe." + value(type) + ".preserve.v1",
                "disposition", "PRESERVE_BYTES", "sha256", SimpleContentConverter.hash(entries.get(path)),
                "reason", "Pinned serializer schema and closed ingredient/result references validated; no rewrite necessary"));
    }

    private void validateSingleInputRecipe(String path, Map<String, Object> recipe, String type)
            throws SubsetRejectionException {
        boolean cooking = DatapackRecipeContract.COOKING_DEFAULT_TICKS.containsKey(type);
        if (cooking) keys(recipe, path, "type", "group", "ingredient", "result", "experience", "cookingtime");
        else keys(recipe, path, "type", "group", "ingredient", "result", "count");
        if (recipe.containsKey("group")) string(recipe.get("group"), path, "group");
        ingredient(recipe.get("ingredient"), path);
        String result = string(recipe.get("result"), path, "result (only the vanilla string form is supported)");
        contentReference(result, "items", path);
        if (cooking) {
            if (recipe.containsKey("experience")) {
                Object raw = recipe.get("experience");
                if (!(raw instanceof BigDecimal number) || !Float.isFinite(number.floatValue())
                        || number.signum() < 0 || number.compareTo(BigDecimal.valueOf(100)) > 0)
                    reject(path, "experience must be finite numeric 0..100");
            }
            if (recipe.containsKey("cookingtime")) integer(recipe.get("cookingtime"), path, "cookingtime", 1, 72000);
        } else {
            String qualifiedResult = id(result, "minecraft", path);
            int maxStack = plan.items().stream().filter(item -> qualified(item.name()).equals(qualifiedResult))
                    .mapToInt(item -> item.properties().stackSize()).findFirst().orElse(64);
            integer(recipe.get("count"), path, "count", 1, maxStack);
        }
    }

    private void ingredient(Object value, String path) throws SubsetRejectionException {
        if (value instanceof List<?>) {
            List<Object> alternatives = array(value, path, "ingredient alternatives");
            if (alternatives.isEmpty()) reject(path, "empty ingredient alternatives");
            for (Object alternative : alternatives) {
                if (alternative instanceof List<?>) reject(path, "nested ingredient alternatives are unsupported");
                ingredient(alternative, path);
            }
            return;
        }
        Map<String, Object> ingredient = object(value, path, "ingredient");
        keys(ingredient, path, "item", "tag");
        if (ingredient.containsKey("item") == ingredient.containsKey("tag")) reject(path, "ingredient must have exactly one item or tag");
        if (ingredient.containsKey("item")) contentReference(string(ingredient.get("item"), path, "ingredient item"), "items", path);
        else tagReference(string(ingredient.get("tag"), path, "ingredient tag"), "items", path, true);
    }

    private void validateTag(String path, String kind) throws SubsetRejectionException {
        Map<String, Object> tag = jsonObject(path);
        keys(tag, path, "replace", "values");
        if (tag.containsKey("replace")) bool(tag.get("replace"), path, "replace");
        List<String> edges = new ArrayList<>();
        tagEdges.put(path, edges);
        for (Object raw : array(tag.get("values"), path, "tag values")) {
            boolean required = true;
            String reference;
            if (raw instanceof String text) reference = text;
            else {
                Map<String, Object> entry = object(raw, path, "tag entry");
                keys(entry, path, "id", "required");
                reference = string(entry.get("id"), path, "tag entry id");
                if (entry.containsKey("required")) required = bool(entry.get("required"), path, "required");
            }
            if (reference.startsWith("#")) {
                String target = tagReference(reference.substring(1), kind, path, required);
                if (entries.containsKey(target)) edges.add(target);
            } else {
                contentReference(reference, kind, path);
                tagsWithValues.add(path);
            }
        }
    }

    private String tagReference(String reference, String kind, String path, boolean required) throws SubsetRejectionException {
        String id = id(reference, "minecraft", path);
        if (datapackRecipes && !namespace(id).equals(plan.modId()))
            reject(path, "datapack-recipes-v1 requires local closed tag references: " + id);
        if (!Set.of(plan.modId(), "forge", "minecraft").contains(namespace(id))) reject(path, "foreign tag dependency: " + id);
        String target = "data/" + namespace(id) + "/tags/" + kind + "/" + value(id) + ".json";
        boolean vanilla = namespace(id).equals("minecraft") && (kind.equals("items")
                ? Set.of("planks", "logs", "wool").contains(value(id))
                : Set.of("planks", "logs", "wool", "mineable/pickaxe", "mineable/axe").contains(value(id)));
        if ((required || datapackRecipes) && !entries.containsKey(target) && !vanilla) reject(path, "required tag is absent or not in the audited vanilla subset: " + id);
        return target;
    }

    private void contentReference(String reference, String kind, String path) throws SubsetRejectionException {
        String id = id(reference, "minecraft", path);
        Set<String> local = kind.equals("items") ? items : blocks;
        Set<String> vanilla = kind.equals("items") ? VANILLA_ITEMS : VANILLA_BLOCKS;
        if (namespace(id).equals(plan.modId()) && local.contains(id)) return;
        if (namespace(id).equals("minecraft") && vanilla.contains(value(id))) return;
        reject(path, "missing, foreign, or unaudited " + kind + " reference: " + id);
    }

    private void texture(String reference, String path) throws SubsetRejectionException {
        String id = id(reference, "minecraft", path);
        if (namespace(id).equals("minecraft") && VANILLA_TEXTURES.contains(value(id))) return;
        if (!namespace(id).equals(plan.modId())) reject(path, "foreign or unaudited texture reference: " + id);
        require("assets/" + namespace(id) + "/textures/" + value(id) + ".png", path);
    }

    private void validateSound(String path, String id, Map<String, Object> event) throws SubsetRejectionException {
        keys(event, path, "replace", "subtitle", "sounds");
        if (event.containsKey("replace")) bool(event.get("replace"), path, "replace");
        if (event.containsKey("subtitle")) string(event.get("subtitle"), path, "subtitle");
        List<String> edges = new ArrayList<>();
        soundEdges.put(id, edges);
        List<Object> sounds = array(event.get("sounds"), path, "sounds");
        if (sounds.isEmpty()) reject(path, "empty sound definition: " + id);
        for (Object raw : sounds) {
            String name;
            String type = "file";
            if (raw instanceof String text) name = text;
            else {
                Map<String, Object> sound = object(raw, path, "sound entry");
                keys(sound, path, "name", "type", "volume", "pitch", "weight", "stream", "attenuation_distance", "preload");
                name = string(sound.get("name"), path, "sound name");
                if (sound.containsKey("type")) type = string(sound.get("type"), path, "sound type");
                if (sound.containsKey("volume")) decimal(sound.get("volume"), path, "volume", 0.0, 16.0);
                if (sound.containsKey("pitch")) decimal(sound.get("pitch"), path, "pitch", 0.01, 4.0);
                if (sound.containsKey("weight")) integer(sound.get("weight"), path, "weight", 1, 1000);
                if (sound.containsKey("attenuation_distance")) integer(sound.get("attenuation_distance"), path, "attenuation_distance", 1, 1024);
                for (String key : List.of("stream", "preload")) if (sound.containsKey(key)) bool(sound.get(key), path, key);
            }
            String target = id(name, "minecraft", path);
            if (type.equals("event")) {
                if (namespace(target).equals(plan.modId()) && soundEvents.containsKey(target)) edges.add(target);
                else if (!namespace(target).equals("minecraft") || !VANILLA_SOUND_EVENTS.contains(value(target))) reject(path, "missing, foreign, or unaudited sound event: " + target);
            } else if (type.equals("file")) {
                reject(path, "Sound type=file is unsupported until bounded audio decoding is implemented; use type=event aliases to audited vanilla events");
            } else reject(path, "unsupported sound type: " + type);
        }
    }

    private static void validatePng(String path, byte[] bytes) throws SubsetRejectionException {
        if (bytes.length < 45) reject(path, "truncated PNG");
        for (int i = 0; i < PNG_SIGNATURE.length; i++) if (bytes[i] != PNG_SIGNATURE[i]) reject(path, "invalid PNG signature");
        ByteBuffer input = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        int width = input.getInt(16), height = input.getInt(20);
        if (input.getInt(8) != 13 || input.getInt(12) != 0x49484452 || width < 1 || height < 1
                || width > 2048 || height > 2048 || (long) width * height > 1_048_576) reject(path, "PNG dimensions/header outside bounded texture subset");
        int offset = 8;
        boolean end = false, data = false;
        while (offset < bytes.length) {
            if (bytes.length - offset < 12) reject(path, "truncated PNG chunk");
            int length = input.getInt(offset);
            if (length < 0 || (long) offset + 12 + length > bytes.length) reject(path, "invalid PNG chunk length");
            int type = input.getInt(offset + 4);
            if (type == 0x6163544c || type == 0x6663544c || type == 0x66644154) reject(path, "APNG is outside the static texture subset");
            CRC32 crc = new CRC32();
            crc.update(bytes, offset + 4, length + 4);
            if ((int) crc.getValue() != input.getInt(offset + 8 + length)) reject(path, "PNG CRC mismatch");
            if (type == 0x49444154) data = true;
            if (type == 0x49454e44) {
                if (length != 0 || offset + 12 != bytes.length) reject(path, "invalid PNG end chunk");
                end = true;
            }
            offset += length + 12;
        }
        if (!end || !data) reject(path, "PNG is missing image data or end chunk");
        ImageReader reader = ImageIO.getImageReadersByFormatName("png").next();
        try (MemoryCacheImageInputStream stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            reader.setInput(stream, true, true);
            if (reader.read(0).getWidth() != width) reject(path, "PNG decoder/header disagreement");
        } catch (OperationStoppedException stopped) {
            throw stopped;
        } catch (IOException | RuntimeException exception) {
            reject(path, "PNG could not be decoded within the bounded static texture subset");
        } finally {
            reader.dispose();
        }
    }

    private static void rejectCycles(Map<String, List<String>> graph, String kind) throws SubsetRejectionException {
        Map<String, Integer> remainingDepth = new HashMap<>();
        for (String node : graph.keySet()) visit(node, graph, remainingDepth, new HashSet<>(), kind, 0);
    }

    private static int visit(String node, Map<String, List<String>> graph, Map<String, Integer> remainingDepth, Set<String> active,
                              String kind, int depth) throws SubsetRejectionException {
        OperationControl.checkpoint();
        // A cached subtree is not depth-free: account for its longest path too.
        Integer cached = remainingDepth.get(node);
        if (cached != null) {
            if (depth + cached > 64) reject(node, kind + " reference depth limit");
            return cached;
        }
        if (depth > 64 || !active.add(node)) reject(node, kind + " reference cycle or depth limit");
        int longest = 0;
        for (String target : graph.getOrDefault(node, List.of()))
            longest = Math.max(longest, 1 + visit(target, graph, remainingDepth, active, kind, depth + 1));
        if (depth + longest > 64) reject(node, kind + " reference depth limit");
        active.remove(node);
        remainingDepth.put(node, longest);
        return longest;
    }

    private Map<String, Object> jsonObject(String path) throws SubsetRejectionException {
        byte[] bytes = entries.get(path);
        if (bytes == null || bytes.length > MAX_JSON_BYTES) reject(path, "JSON resource absent or exceeds 1 MiB");
        try {
            String json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            return object(BoundedJsonParser.parse(json), path, "JSON root");
        } catch (CharacterCodingException | BoundedJsonParser.JsonSyntaxException exception) {
            throw new SubsetRejectionException("RESOURCE_INVALID: " + path + ": " + exception.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String path, String key) throws SubsetRejectionException {
        if (!(value instanceof Map<?, ?>)) reject(path, key + " must be a JSON object");
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> array(Object value, String path, String key) throws SubsetRejectionException {
        if (!(value instanceof List<?>)) reject(path, key + " must be a JSON array");
        return (List<Object>) value;
    }

    private static String string(Object value, String path, String key) throws SubsetRejectionException {
        if (!(value instanceof String)) reject(path, key + " must be a JSON string");
        return (String) value;
    }

    private static boolean bool(Object value, String path, String key) throws SubsetRejectionException {
        if (!(value instanceof Boolean)) reject(path, key + " must be a JSON boolean");
        return (Boolean) value;
    }

    private static int integer(Object value, String path, String key, int minimum, int maximum) throws SubsetRejectionException {
        if (!(value instanceof BigDecimal)) reject(path, key + " must be an integer");
        try {
            int number = ((BigDecimal) value).intValueExact();
            if (number < minimum || number > maximum) reject(path, key + " is outside " + minimum + ".." + maximum);
            return number;
        } catch (ArithmeticException exception) {
            reject(path, key + " must be a bounded integer");
            return 0;
        }
    }

    private static void decimal(Object value, String path, String key, double minimum, double maximum) throws SubsetRejectionException {
        if (!(value instanceof BigDecimal)) reject(path, key + " must be numeric");
        double number = ((BigDecimal) value).doubleValue();
        if (!Double.isFinite(number) || number < minimum || number > maximum) reject(path, key + " is outside the simple sound range");
    }

    private static void keys(Map<String, Object> object, String path, String... allowed) throws SubsetRejectionException {
        Set<String> whitelist = Set.of(allowed);
        for (String key : object.keySet()) if (!whitelist.contains(key)) reject(path, "unsupported JSON member: " + key);
    }

    private void require(String target, String path) throws SubsetRejectionException {
        if (!entries.containsKey(target)) reject(path, "missing required local resource: " + target);
    }

    private String qualified(String name) { return name.contains(":") ? name : plan.modId() + ":" + name; }
    private String modelId(String path) { return plan.modId() + ":" + path.substring(("assets/" + plan.modId() + "/models/").length(), path.length() - 5); }
    private static String namespace(String id) { return id.substring(0, id.indexOf(':')); }
    private static String value(String id) { return id.substring(id.indexOf(':') + 1); }

    private static String id(String input, String defaultNamespace, String path) throws SubsetRejectionException {
        String result = input.contains(":") ? input : defaultNamespace + ":" + input;
        if (!result.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || !safePath(value(result))) reject(path, "invalid resource location: " + input);
        return result;
    }

    private static boolean safePath(String path) {
        if (path.isEmpty() || path.startsWith("/") || path.endsWith("/") || path.contains("\\") || path.contains("//") || path.contains(":")) return false;
        for (String component : path.split("/")) if (component.equals(".") || component.equals("..")) return false;
        return true;
    }

    private static boolean ignored(String path) {
        if (path.endsWith(".class") || path.equals("META-INF/mods.toml") || path.equals("META-INF/MANIFEST.MF") || path.equals("pack.mcmeta")) return true;
        String file = path.startsWith("META-INF/") ? path.substring("META-INF/".length()) : path;
        return !file.contains("/") && file.toLowerCase(java.util.Locale.ROOT).matches("(?:license|copying|notice|readme|authors)(?:[._-][a-z0-9._-]+)?");
    }

    private static void reject(String path, String reason) throws SubsetRejectionException {
        throw new SubsetRejectionException("RESOURCE_OUTSIDE_SUBSET: " + path + ": " + reason);
    }
}
