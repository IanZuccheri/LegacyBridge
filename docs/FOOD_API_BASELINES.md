# Plain food: exact API audit and native-endpoint proof boundary

Audit date: 2026-09-06. Source: Minecraft **1.16.5 / Forge 36.2.42**.
Target: Minecraft **1.20.1 / Forge 47.4.23**, Java 17. This document records
read-only inspection and a proposed finite `food-item-v1` envelope. It does not
itself implement or certify conversion, gameplay, multiplayer or anti-duplication.
No original mod was loaded or executed, and no build or game was run in this audit.

## Evidence and namespaces

These exact local ForgeGradle artifacts were read and hashed again for this audit.
They live under `minecraft_user_repo/net/minecraftforge/forge` in the development
cache. That location is evidence provenance, **not** an operational dependency to
introduce into the player product. Derived game sources/binaries are not to be
redistributed. Existing [portable-data](PORTABLE_MAPPING_DATA.md) and
[packaging-rights](PACKAGING_RIGHTS.md) gates remain unchanged.

| Inspected archive | SHA-256 |
| --- | --- |
| `1.16.5-36.2.42_mapped_official_1.16.5/forge-1.16.5-36.2.42_mapped_official_1.16.5-sources.jar` | `bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376` |
| `1.20.1-47.4.23_mapped_official_1.20.1/forge-1.20.1-47.4.23_mapped_official_1.20.1-sources.jar` | `4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932` |
| `1.16.5-36.2.42/forge-1.16.5-36.2.42-srg.jar` | `26ccfe3a5e3cd2c82d2c4bfc38c254c151e8e10ef085a18149aa293db4323a34` |
| `1.20.1-47.4.23/forge-1.20.1-47.4.23-srg.jar` | `22716a4e2161afd0c0b34e3ceac3065f69a7b5d1e12a82ad1567d8df0a536ba7` |

The builder/property declarations and descriptors were checked with read-only
`javap -p -s` against those trusted SRG archives. Official client mapping entries
were also read: both versions use canonical `net.minecraft.world.food.FoodProperties`
and `$Builder`. Their obfuscated owners are `bhz` / `bhz$a` in 1.16.5 and
`cbc` / `cbc$a` in 1.20.1. Source production instead uses MCP owners
`net/minecraft/item/Food` / `Food$Builder`; target production uses official owners.
The existing official/obfuscated/SRG composition must derive and verify rules from
the supplied versioned data; this small audit table is not a replacement mapping database.

Corresponding primary upstream patch locations are
[Forge 1.16 Item](https://github.com/MinecraftForge/MinecraftForge/blob/1.16.x/patches/minecraft/net/minecraft/item/Item.java.patch)
and [Forge 1.20.1 Item](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.1/patches/minecraft/net/minecraft/world/item/Item.java.patch).
Branch URLs are supporting source references, not substitutes for the exact hashes above.

## Proposed admitted construction, not an arbitrary food API

The first candidate is one ordinary deferred item registration and a final owned
Item subclass containing **only** its constructor. That constructor creates the
native properties and food builder with literal `stacksTo(16)`, `nutrition(3)` and
`saturationMod(0.5F)`, calls `build`, attaches the result through `food`, and invokes
the native Item superclass constructor. Preserve this actual owned body.

Do not replace the subclass with generated registration data, intercept consumption,
patch Minecraft/Forge classes, or emulate hunger by returning fabricated stacks.
Neither the source mod nor its converted version needs a direct World/Player
reference for this construction-only slice. Probe code uses those native APIs
separately; that does not admit them into converted mod bodies.

The narrow additional rules are:

| Official operation | Source production declaration | Target production declaration |
| --- | --- | --- |
| `FoodProperties.Builder.<init>` | `net/minecraft/item/Food$Builder.<init>()V` | `net/minecraft/world/food/FoodProperties$Builder.<init>()V` |
| `Builder.nutrition` | `Food$Builder.func_221456_a(I)Lnet/minecraft/item/Food$Builder;` | `FoodProperties$Builder.m_38760_(I)Lnet/minecraft/world/food/FoodProperties$Builder;` |
| `Builder.saturationMod` | `Food$Builder.func_221454_a(F)Lnet/minecraft/item/Food$Builder;` | `FoodProperties$Builder.m_38758_(F)Lnet/minecraft/world/food/FoodProperties$Builder;` |
| `Builder.build` | `Food$Builder.func_221453_d()Lnet/minecraft/item/Food;` | `FoodProperties$Builder.m_38767_()Lnet/minecraft/world/food/FoodProperties;` |
| `Item.Properties.food` | `Item$Properties.func_221540_a(Lnet/minecraft/item/Food;)Lnet/minecraft/item/Item$Properties;` | `Item$Properties.m_41489_(Lnet/minecraft/world/food/FoodProperties;)Lnet/minecraft/world/item/Item$Properties;` |

In that table, abbreviated source owners belong to `net/minecraft/item`; target
FoodProperties belongs to `net/minecraft/world/food`, and target Item to
`net/minecraft/world/item`. Constructors use `INVOKESPECIAL`; the four builder/
property methods are public non-static instance methods using `INVOKEVIRTUAL`.
Their fluent methods return the same builder/properties receiver. `build` creates
the native food object. The previously reviewed stacks-to operation remains
`func_200917_a(I)Properties` -> `m_41487_(I)Properties`.

The two full Food/FoodProperties sources have the same relevant construction
semantics: stored nutrition and saturation modifier; `meat`, `alwaysEat` and
`fast` flags initially false; empty effects list. Both Forge versions internally
represent effect entries using suppliers, but an empty list neither invokes a
supplier nor grants support for effect-bearing foods. Food getters exist in both
versions; they are inspection endpoints, not an automatic conversion allowlist.

Continue rejecting additional fields/methods, overridden consumption/use hooks,
custom builders/properties, dynamic numeric expressions, unusual/NaN/infinite
values, `alwaysEat`, `fast`, meat behavior, effects, containers/crafting remainders,
durability, tools, capabilities, custom renderers and further food subclasses.
Broader finite literal ranges need their own validation decision and fixtures.

## Native consumption: equivalent core, explicit differences

The following names are the actual mapped development declarations. They are
useful for separate probes, not newly approved source-mod transformations:

| Endpoint | 1.16.5 types/signature | 1.20.1 types/signature |
| --- | --- | --- |
| Start stack use | `ItemStack.use(World, PlayerEntity, Hand): ActionResult<ItemStack>` | `ItemStack.use(Level, Player, InteractionHand): InteractionResultHolder<ItemStack>` |
| Finish stack endpoint | `ItemStack.finishUsingItem(World, LivingEntity): ItemStack` | `ItemStack.finishUsingItem(Level, LivingEntity): ItemStack` |
| Native item dispatch | `Item.finishUsingItem(ItemStack, World, LivingEntity): ItemStack` | `Item.finishUsingItem(ItemStack, Level, LivingEntity): ItemStack` |
| Entity consumption | `LivingEntity.eat(World, ItemStack): ItemStack`, overridden by `PlayerEntity` | `LivingEntity.eat(Level, ItemStack): ItemStack`, overridden by `Player` |
| Hunger admission | `PlayerEntity.canEat(boolean): boolean` | `Player.canEat(boolean): boolean` |
| Food data | `PlayerEntity.getFoodData(): net.minecraft.util.FoodStats` | `Player.getFoodData(): net.minecraft.world.food.FoodData` |
| Stack presentation | `ItemStack.getUseDuration(): int`, `getUseAnimation(): UseAction` | `ItemStack.getUseDuration(): int`, `getUseAnimation(): UseAnim` |

Relevant local source locations: source Item 109/123/207/211/346/383, ItemStack
215/219/443/447, PlayerEntity 1599/1603/1970, LivingEntity 3093, FoodStats 24/29;
target Item 139/153/258/262/354/427, ItemStack 274/278/491/495, Player
1685/1689/2001, LivingEntity 3273, FoodData 21/32.

For this no-override, non-fast, non-always-eat food:

- Native use selects the actual hand stack and checks `canEat(false)`. Hungry
  survival starts use and returns `CONSUME`; full-hunger survival returns `FAIL`.
  Both holder types expose `getResult()` and `getObject()`.
- `canEat` accepts an invulnerable player, an always-eat request, or food level
  below 20. Invulnerability here means the **abilities field**, not FakePlayer's
  separately overridden damage predicate.
- Animation is native `EAT`; declared duration is 32 ticks. These values alone
  do not prove that a player actually completed a 32-tick interaction.
- Player consumption adds nutrition and saturation, records native consumption
  side effects, and delegates to LivingEntity consumption. The food level becomes
  `min(old + nutrition, 20)`. Saturation becomes
  `min(oldSaturation + nutrition * modifier * 2, newFoodLevel)`.
  Thus hunger 10/saturation 0 becomes 13/3 for the proposed literal values.
- LivingEntity shrinks the existing stack by one unless the player abilities have
  `instabuild=true`; it returns that same stack for this plain Item path. A last
  survival unit becomes empty; no bowl/bottle/remainder is generated. Creative
  keeps the stack count, but the native food-data path still runs.

Important differences must remain explicit:

1. **Context-sensitive food properties.** Legacy use/duration/FoodStats read the
   Item's no-argument food properties. Target paths use
   `ItemStack.getFoodProperties(@Nullable LivingEntity)`, via the real Forge
   `IForgeItemStack` -> `IForgeItem` extension. Its default implementation returns
   `Item.getFoodProperties()`. The constructor-only subclass inherits that default,
   making the literal food data equivalent for this slice. Custom stack/entity-
   dependent overrides are not equivalent by assumption and remain unsupported.
2. **Target game events.** 1.20.1 emits native `GameEvent.EAT` and use-start/finish
   game events. 1.16.5 has no corresponding emission in these paths. Preserve
   target-native interaction with its world systems; do not promise identical
   event observability or suppress these game events to imitate the old game.
3. **Stopping and zero-duration hooks.** Target stop dispatches the native
   `IForgeItem.onStopUsing`, whose default has no extra effect. Target start also
   accepts duration zero under its Forge server option; source requires positive
   duration. The literal duration 32 with no custom hooks avoids that divergence.
4. **Sides and hook lifecycle.** Direct `finishUsingItem`/`eat` is not itself a
   side-authority or eligibility guard. Normal LivingEntity use ticking completes
   on the server, performs Forge start/tick/finish hooks, hand replacement and
   stopping; a direct endpoint call bypasses some of that enclosing lifecycle.
   Other mods' event cancellation/replacement is not certified by an isolated probe.

The corresponding official patch sources are
[legacy LivingEntity](https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.16.x/patches/minecraft/net/minecraft/entity/LivingEntity.java.patch)
and [target LivingEntity](https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.20.1/patches/minecraft/net/minecraft/world/entity/LivingEntity.java.patch).

## Headless probe seams and misleading substitutes to avoid

Both versions provide `new FakePlayer(serverWorldOrLevel, GameProfile)` and
`FakePlayerFactory.getMinecraft(serverWorldOrLevel)`. Prefer a fresh probe-owned
FakePlayer with a test identity rather than changing the shared factory singleton.
The old factory caches by profile, while the target groups by level/profile.
Run only on the actual server thread in an authorized isolated test world.

Both FakePlayer implementations deliberately leave `tick`, packet handling and
stat awards inert. They are real Forge helpers but **not a real multiplayer client**.
Calling `fake.tick()` 32 times does not test consumption timing. Source FakePlayer
also overrides `position()`/`blockPosition()` with zero-valued results; its ordinary
`getX/getY/getZ` values used by drops can still be set with `setPos`.
See [source FakePlayer](https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.16.x/src/main/java/net/minecraftforge/common/util/FakePlayer.java)
and [target FakePlayer](https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.20.1/src/main/java/net/minecraftforge/common/util/FakePlayer.java).

Use these actual common-side seams for reproducible endpoint checks:

- Reset probe food state using `getFoodData().readAdditionalSaveData(tag)`:
  source `CompoundNBT`, target `CompoundTag`; keys `foodLevel`, `foodTickTimer`,
  `foodSaturationLevel`, `foodExhaustionLevel`. This is test setup, not emulated
  consumption. Source `FoodStats.setSaturation(float)` is **CLIENT-only** and
  must not be referenced by a dedicated 1.16.5 probe.
- Source abilities/inventory are `player.abilities` / `player.inventory`; target
  uses `getAbilities()` / `getInventory()`. Set both `invulnerable` and `instabuild`
  deliberately for survival/creative cases; do not infer those from FakePlayer's
  `isInvulnerableTo`, which always returns true.
- Both inventory implementations provide `clearContent`, `getContainerSize`,
  `getItem(int)`, `removeItem(int,int)` and `add(ItemStack)`. Both living players
  provide `setItemInHand`, `getItemInHand`, `setPos(double,double,double)` and
  `setHealth(float)`. Only manipulate the fresh probe's state.
- Call native stack `use`, verify holder/hand identity, `isUsingItem` and remaining
  duration, then native stack `finishUsingItem` exactly once for an admitted use.
  Observe returned stack, hunger, saturation, item identity and inventory totals.
  Explicit `stopUsingItem()` afterwards is cleanup. Label this **native endpoint
  invocation**, not an elapsed-tick, packet, Forge-finish-hook or animation test.
- At hunger 20 in survival, require `FAIL`, unchanged state and no started use;
  do not call finish anyway. For creative, require admitted use and unchanged
  stack count. Reset setup state between cases.

## Native drops, pickup and bounded conservation evidence

Both server-player versions implement `drop(ItemStack, boolean, boolean)` and
return an ItemEntity already added to the server world unless an active drop
capture redirects it. The two-argument Player overload uses `ForgeHooks` toss
events and also performs the world addition. **Do not add the returned entity
again.** The stack overload does not first remove the supplied units from the
inventory: remove them with the native inventory operation or use the selected-
item drop path. Passing a copied held stack while leaving the inventory intact
manufactures duplication inside the test itself.

`ItemEntity.setNoPickUpDelay()` and `playerTouch(player)` exist on both versions.
Pickup is server-only, observes the native pickup hook, and transfers via inventory
`add`. Count every inventory compartment, including offhand, and count only live
owned dropped entities. Source `Entity.isAlive()` tests `!removed`; target tests
`!isRemoved()`. ItemEntity does not override that predicate.

After a full pickup, both implementations mark the entity removed but may restore
its stored stack count for pickup feedback. That retired object is not additional
inventory. Never call `playerTouch` again on that removed object: normal world
dispatch would no longer call it, and manually doing so can manufacture a replay
outside the native caller's contract. Repeat the operation using a new legitimate
drop, or repeat a pickup only while the entity remains live after partial transfer.

A bounded survival conservation check can track:

`initial units = current inventory units + live dropped units + consumed units`.

For example, begin with 16 units; consume one, transfer two out through the native
inventory/drop path, and pick those two back up. Expected totals are respectively
15 inventory; 13 inventory + 2 live; then 15 inventory + 0 live, with exactly one
consumed throughout. Check registry identity, per-slot limit 16 and the world-
registered entity identity as well as sums. An additional full-inventory refusal
and partial-pickup case would strengthen this bounded proof. Creative full-
inventory pickup can natively discard excess items; do not apply survival's
conservation equality to that different native rule.

There are unrelated native differences: target selected-item dropping explicitly
stops some active uses (the Forge MC-231097 fix), and partial-pickup stat/feedback
counts and owner restrictions differ. Idle, unrestricted, complete pickup does
not prove equivalence for those cases. A FakePlayer also suppresses stat awards,
so it cannot certify stats or advancement/UI/network delivery.

## Remaining gates

Static mapping, source/target linkage and bytecode validation; actual registry
discovery; server endpoint observations; a real client completing normal use;
assets/translation loading; persistence/relog; and multiplayer consistency are
separate evidence. Original-fixture runtime checks, when separately authorized,
are not part of analysis or conversion. A successful native endpoint probe does
not support arbitrary foods, callbacks, dropped-entity behavior or a general
anti-duplication guarantee. No new conversion capability is certified by this
document alone.

## Subsequent probe corrections from actual server execution

The same hash-pinned sources were consulted again after two QA gaps were found.
These are **test infrastructure APIs**, not additional admitted converter calls.

`ServerPlayerEntity.setGameMode(GameType)` returns void in the source and consults
Forge's game-mode-change hook. Target `ServerPlayer.setGameMode(GameType)` returns
boolean, consults its game-type-change hook and can return false when unchanged.
Both `isCreative()` and `isSpectator()` read the actual game-mode manager, not
merely the abilities. Both native FakePlayers have inert test packet handlers.
The final probe requests a transition only when needed and asserts the effective
mode and native abilities. It requires an empty connected PlayerList so mode
broadcasts cannot reach real players. It does not claim network delivery.

A generated FULL chunk is not proof that its entities are tracked. Target
`ServerLevel.getEntity` reads visible entity storage; the
`PersistentEntitySectionManager` starts tracking after accessibility promotion
and processes pending entity loads on native ticks. A live newly added entity
may therefore not be visible during ServerStartedEvent. Retrying the whole test
with another random spawn without fixing this phase dependency is not a solution.

The probe uses the native public `setChunkForced(0,0,true)` on a fresh test world
with no existing forced chunks. It waits for two consecutive native
`TickEvent.ServerTickEvent` END observations, with a maximum of 200 observations:

- Source: `ServerChunkProvider.isEntityTickingChunk(ChunkPos)` checks the actual
  ChunkHolder entity-ticking future, distinct from the merely block-ticking future.
- Target: `ServerLevel.areEntitiesLoaded(long)` checks its entity manager, while
  `isPositionEntityTicking(BlockPos)` also checks position tickability and the
  native distance manager's entity-ticking range. Both must be true.

The actual returned ItemEntity must still be discovered by exact world lookup;
the readiness predicates do not replace that assertion. The sole owned forced
chunk is removed using the native setter and its absence checked before success
is written. Readiness ticks are not eating ticks: the FakePlayer use/finish
endpoint limitations remain unchanged. No private field, fabricated entity index
or platform patch is used to force success.
