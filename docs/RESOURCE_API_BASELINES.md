# Recipe resources: exact API and reload audit

Audit date: 2026-09-06. Source is Minecraft **1.16.5 / Forge 36.2.42**;
target is Minecraft **1.20.1 / Forge 47.4.23**, Java 17. This is a read-only
API/format audit for a finite `datapack-recipes-v1` slice, not a claim that arbitrary
data packs or recipe serializers can be ported. No original mod class was loaded
or executed, and no build or game was run by this audit.

## Provenance and evidence scope

These local ForgeGradle source archives were opened as ZIP data and hashed again.
Names and line references below refer to their merged, patched development
sources, not to an assumed vanilla implementation or a different Forge patch.
The cache is an audit input, not a new operational dependency for the player.
Do not redistribute these game sources or embed them in LegacyBridge.

| Archive under `minecraft_user_repo/net/minecraftforge/forge` | SHA-256 |
| --- | --- |
| `1.16.5-36.2.42_mapped_official_1.16.5/forge-1.16.5-36.2.42_mapped_official_1.16.5-sources.jar` | `bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376` |
| `1.20.1-47.4.23_mapped_official_1.20.1/forge-1.20.1-47.4.23_mapped_official_1.20.1-sources.jar` | `4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932` |

Corresponding primary upstream patch references are
[source cooking serializer](https://github.com/MinecraftForge/MinecraftForge/blob/1.16.x/patches/minecraft/net/minecraft/item/crafting/CookingRecipeSerializer.java.patch)
and [target cooking serializer](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.1/patches/minecraft/net/minecraft/world/item/crafting/SimpleCookingSerializer.java.patch).
Those branch links corroborate the Forge extension, but are not immutable
identifiers and do not replace the exact local hashes. Existing
[portable-data](PORTABLE_MAPPING_DATA.md) and [packaging-rights](PACKAGING_RIGHTS.md)
restrictions remain unchanged.

Source owners are MCP packages with mapped development method names; target
owners are official packages. These probe signatures are not a handwritten
mapping table and do not authorize calls from converted mod code. Byte-preserved
JSON recipes need no transformation of those game classes.

## Cooking and stonecutting schemas

The actual source `net/minecraft/item/crafting/CookingRecipeSerializer.java:20`
and target `net/minecraft/world/item/crafting/SimpleCookingSerializer.java:20`
read the same core cooking fields. Serializer registrations are in source
`IRecipeSerializer.java:24` and target `RecipeSerializer.java:25`.

| Serializer/resource ID | Default `cookingtime`, source and target | Result in the proposed plain-string slice |
| --- | --- | --- |
| `minecraft:smelting` | 200 ticks | One item |
| `minecraft:blasting` | 100 ticks | One item |
| `minecraft:smoking` | 100 ticks | One item |
| `minecraft:campfire_cooking` | **100 ticks** | One item |
| `minecraft:stonecutting` | No cooking-time field | Explicit integer `count` |

The campfire **serializer default is 100**, not 600. An individual recipe may
explicitly choose 600; that does not change the serializer default.

For cooking, `ingredient` is required and accepts an object or array;
`result` is required. `group` defaults to the empty string and `experience`
defaults to `0.0F`. Omitted cooking time takes the corresponding table value.
Neither serializer alone establishes positive/bounded time or sane finite XP;
the converter must apply its own documented finite bounds before preservation.
Do not interpret an ignored extra field as supported behavior.

Forge in **both** versions additionally accepts an object-form cooking result,
delegating to the shaped-recipe ItemStack parser. That extension reaches count
and NBT/capability handling. Its existence does not certify equivalence of NBT
or capability semantics. A first slice can legitimately refuse all object-form
cooking results while accepting a fully resolved ordinary string result.

Stonecutting uses `SingleItemRecipe.Serializer.fromJson` in both versions
(source line 70, target line 71). It reads `group` with the same default, an
ingredient object/array, a **string** `result`, and **required** integer `count`.
There is no default count and no cooking/experience field. The native registry
lookup is not a sufficient unknown-item check: validate item existence and an
ordinary non-air result separately, and bound count to the actual result item's
stack limit. Do not silently turn a missing or invalid result into an empty stack.

### Required behavioral disclosure: recipe-book category

Target cooking JSON also recognizes `category`; omission or an unrecognized
name falls back to `CookingBookCategory.MISC` (`SimpleCookingSerializer:22`).
The old cooking serializer does not read that field. The closed source schema
should therefore reject explicit `category`, not copy an old ignored field that
becomes meaningful in the target.

This is more than a newly stored field. Source
`net/minecraft/client/util/ClientRecipeBook.java:78` infers smelting FOOD/BLOCKS
from the result item and blasting BLOCKS from `BlockItem`. Target
`net/minecraft/client/ClientRecipeBook.java:80` uses the recipe's category.
An unchanged old smelting recipe producing a block can move from
`FURNACE_BLOCKS` to `FURNACE_MISC`; a blasting block can similarly move to the
miscellaneous tab. Smoking stays in `SMOKER_FOOD`, and campfire/stonecutting keep
their dedicated category routing. Preserve bytes only with this UI migration
disclosed, or separately implement and validate an explicit category rewrite.
Do not claim the same recipe-book organization merely because crafting matches.

## Ingredients, tags and Forge conditions

Source `Ingredient.java:187` and target `Ingredient.java:188` dispatch through
Forge's `CraftingHelper`. Its default object serializer is `minecraft:item`.
The vanilla value language is one `item` **or** one `tag`, never both. Arrays
represent alternatives; custom serializers can also participate in Forge's
array composition, so recursively validate every alternative rather than only
checking the outer JSON type. For cooking/stonecutting the target passes
`allowEmpty=false`, retaining refusal of an empty ingredient array.

The narrow preserve-only proposal is a nonempty, bounded flat array of ordinary
`{item: ...}` / `{tag: ...}` objects, or one such object. Require exact keys and
fully resolved, supported items. Refuse explicit ingredient `type`, NBT,
intersection/difference/custom Forge ingredients, nested arrays and opaque
extra fields until their semantics have their own implemented contract.
Vanilla ingredient testing compares item identity, not stack count or NBT;
that fact is not permission to discard an input's NBT-sensitive ingredient.

Tag behavior has material version differences:

- Source `Ingredient.valueFromJson:210` looks up the tag immediately in
  `TagCollectionManager` and rejects an unknown tag. Target
  `Ingredient.valueFromJson:215` creates a `TagKey`; it does not prove the tag
  exists at parse time.
- Source `Ingredient.TagList:262` can represent an empty tag as BARRIER or as
  an empty list, controlled by `ForgeConfig.SERVER.treatEmptyTagsAsAir`.
  Target `Ingredient.TagValue:254` uses BARRIER for an empty tag.
- Source ingredients hold legacy tag references and have a weak-instance
  invalidation path; target ingredients hold registry tag keys and an
  invalidation counter. Reload must use the native lifecycle; manually replacing
  one recipe map or comparing a retained stale ingredient is not a reload test.

Consequently the baseline should require a closed local tag graph with existing,
nonempty leaves in the source mod namespace. Refuse external tag composition,
missing references, cycles and optional/empty uncertainty rather than asserting
that every same-named tag denotes the same set across Minecraft versions.
Preservation of an additive `replace:false` document cannot freeze the effects
of other installed packs. A controlled override test should replace a local tag
with a **different nonempty known item set**, then restore the original selection.

The familiar tag `replace` operation is real in both versions: source
`net/minecraft/tags/ITag.java` owns the builder/parser; target
`TagFile.java:7` describes `values` and default-false `replace`, and
`TagLoader.java:39` processes each resource stack and clears prior entries when
replacement is requested. Target Forge also supports `remove`; that is not
part of this closed preserve-only tag language. A test-only override data pack
is not itself a newly supported source-mod resource category.

Recipe-level conditions are executable loader semantics, not comments:

- Source `RecipeManager.apply:48` calls `CraftingHelper.processConditions`
  with the `conditions` array. The helper invokes `ICondition.test()`.
- Target `RecipeManager.apply:53` passes a per-reload `ICondition.IContext`;
  the helper invokes `test(context)`. `ReloadableServerResources:38` constructs
  that context from its tag manager. In particular, `forge:tag_empty` changed
  from consulting source global tags to consulting target reload-context tags.
- `forge:conditional` has its own serializer selecting the first passing
  nested recipe, or returning null. It is not an ordinary recipe whose wrapper
  can be deleted. Ingredient and condition serializer registries are extensible.

Refuse **all** conditions, including apparently constant-false/true cases, the
conditional wrapper and custom serializers in this slice. The converter must
not run these registries, evaluate unknown conditions, download dependencies,
strip excluded branches or silently omit an affected recipe. Newer
`forge:conditions` fields in other resource loading contexts do not make an
unreviewed old field safe to retain either.

## IDs and native manager behavior

Source `JsonReloadListener.java:33` and target
`SimpleJsonResourceReloadListener.java:27` discover `recipes/` JSON resources;
the directory prefix and `.json` suffix are removed to obtain the recipe ID.
`data/<namespace>/recipes/sub/path.json` is `<namespace>:sub/path`. Preserve
that identity and validate safe paths/collisions before publishing.

**Both Forge managers skip an ID whose path starts with `_`** (source manager
line 54, target line 60). Reject those recipe paths in an admitted slice; a
well-formed document that the loader ignores is not a working preserved recipe.

Recipe parse errors are caught and logged for individual recipes, and loading
can continue with omissions. A completed reload or a server reaching `Done`
does not prove the intended recipes loaded. The native `Loaded N recipes` log
uses the number of recipe-type buckets in these sources, not a complete recipe
count. The target `hadErrorsLoading()` field is not set by those catch blocks
and is not an adequate success oracle.

`getRecipeFor` chooses a first matching recipe; different packs may supply
overlapping inputs without duplicating IDs. Do not promise cross-version
selection priority. Use unique fixture inputs where practical, and assert both
the intended ID and positive/negative matching, not merely that some recipe was
found. Foreign pack overrides and arbitrary modpack recipe conflicts remain
outside a single-artifact static proof.

## Exact native probe endpoints and their limits

These are development names from the source archives. They are for separately
authorized runtime probes, not newly allowed source bytecode transformations.

| Operation | Source 1.16.5 | Target 1.20.1 |
| --- | --- | --- |
| Manager lookup | `RecipeManager.byKey(ResourceLocation)` | Same name, target ResourceLocation type |
| Match lookup | `getRecipeFor(IRecipeType<T>, IInventory, World)` | `getRecipeFor(RecipeType<T>, Container, Level)` |
| All matches | `getRecipesFor(IRecipeType<T>, IInventory, World)` | `getRecipesFor(RecipeType<T>, Container, Level)` |
| Plain cooking/stonecutting test | `matches(IInventory, World)` | `matches(Container, Level)` |
| Assemble result | `assemble(IInventory)` | `assemble(Container, RegistryAccess)` |
| Inspect result | `getResultItem()` | `getResultItem(RegistryAccess)` |
| Cooking properties | `getExperience()`, `getCookingTime()`, `getGroup()`, `getId()` | Same mapped method names |
| One-slot test inventory | `net.minecraft.inventory.Inventory` | `net.minecraft.world.SimpleContainer` |
| Native reload | `MinecraftServer.reloadResources(Collection<String>)` | Same mapped signature |

Source recipe packages are `net.minecraft.item.crafting`; target packages are
`net.minecraft.world.item.crafting`. Source uses `StonecuttingRecipe`; target
uses `StonecutterRecipe`. The target registry argument is available from the
actual level's `registryAccess()`.

For this native recipe family, `assemble` returns a **copy** of the configured
result. It does not remove ingredients, consume fuel, award XP, update a furnace,
or perform a player's menu transaction. A probe can check result identity/count,
input unchanged, independent successive result copies, invalid input refusal,
and configured XP/time. Such evidence is native matching/assembly, not completed
smelting, campfire drops, GUI actions or anti-duplication proof.

If a later probe covers actual apparatus execution, the real boundaries are:

- Source `AbstractFurnaceTileEntity.tick()` and target
  `AbstractFurnaceBlockEntity.serverTick(Level, BlockPos, BlockState, entity)`.
  Their private burn paths assemble output, enforce output capacity and shrink
  input. Use a properly installed, native-ticking block entity; do not reflectively
  invoke private burn methods or pretend an assembly call consumed fuel.
- Source `CampfireTileEntity.placeFood(ItemStack, int)` plus its native tick;
  target `CampfireBlockEntity.placeFood(Entity, ItemStack, int)` plus `cookTick`.
  Placement splits one input. Target also checks enabled features and emits
  `BLOCK_CHANGE` game events when placing/completing; source has no equivalent
  game-event behavior. A recipe JSON pass does not cover those world reactions.
- Source `StonecutterContainer` and target `StonecutterMenu` select via
  `clickMenuButton`; their result slot's native `onTake` removes one input.
  Target also checks result feature flags. A directly assembled result does not
  exercise that transaction, full inventory, quick-move or client networking.

## Real resource reload, without a fake manager replacement

Source `MinecraftServer.java:1287` and target `MinecraftServer.java:1317`
return `CompletableFuture<Void>` from `reloadResources`. They open the requested
available packs, build fresh resource managers/listeners, then apply on the
server executor. Both implementations call their own
`managedBlock(future::isDone)` when invoked on the server thread. An audit must
not assume that the method immediately returns on that thread.

The apply stage replaces the resource manager, updates pack selection/world
configuration, installs native tags, refreshes the player list's resources and
functions, and updates structures/commands. Source calls
`DataPackRegistries.updateGlobals`; target calls
`ReloadableServerResources.updateRegistryTags`, which binds tags and posts
`TagsUpdatedEvent`. Use the native entrypoint so those stages are not bypassed.

Safe isolated-probe sequence:

1. Capture the ordered selected pack IDs from `getSelectedPacks()` (source pack
   objects are `ResourcePackInfo`, target `Pack`). `getSelectedIds()` is a set-like
   collection, so do not use it to infer priority ordering.
2. Create only an explicitly owned test data pack, with the correct pack format
   (source 6; target resource/data format 15), and call the repository's `reload()`
   to discover it. Verify its exact discovered ID; native reload filters unknown
   IDs rather than proving every requested pack exists.
3. Set the probe phase before calling `reloadResources` to avoid reentrant
   scheduling. Pass a copied ordered selection with only the owned override added.
   Do not block on a separately scheduled server task with `join/get` from the
   same thread. Record and check the returned future's failure, and marshal all
   follow-up game access onto the server thread or a subsequent server tick.
4. After completion, obtain **the new** `server.getRecipeManager()` and verify
   every expected recipe ID, tag-dependent positive/negative matches, result
   count and relevant pack selection. A retained old recipe is not the current
   server authority after manager replacement.
5. Reload the original selection, verify original matching returns, and only
   then report restoration. Clean up only the owned temporary test pack, never
   the source JAR or another data pack. Report partial failures truthfully.

The native API has no general bounded wall-clock completion guarantee. A
same-thread managed reload can pause normal tick progression; a tick-only
deadline is not an independent watchdog. Keep parent-process timeout/log
monitoring separate. A local reload probe does not establish connected-client
resynchronization, multiplayer behavior, recipe-book presentation, relog safety,
apparatus timing, or release readiness. Those remain separate evidence gates.
