# Native datapack recipe/reload probe: Minecraft 1.20.1

Test-only Forge 47.4.23 mod, ID `legacybridge_datapack_probe`, version 1.0.0.
Its declared dependencies are Forge, Minecraft and fixture
`legacybridge_datapack` 1.0.0. The target instance executes the converted fixture plus the current public LegacyBridge JAR on Java 17.
Never convert this probe, run it during analysis/conversion, or bundle it in the
public product. All Rights Reserved and local-QA-only fixture rules apply.

Build serially from the LegacyBridge root:

```text
gradlew -p fixtures/datapack-runtime-probe-1.20.1 build --no-daemon --max-workers=1
```

Artifact: `build/libs/legacybridge-datapack-probe-1.20.1-1.0.0.jar` inside
this project. ForgeGradle 6.0.54 and official 1.20.1 mappings are pinned.
Java 17 compiles Java 17 bytecode, followed by native reobfuscation. Five tests
inspect the bounded neutral JSON writer and archive boundaries without initializing
game or original fixture classes. The writer permits at most 48 fields, 128 values
per primitive array and 64 KiB; files are CREATE_NEW, forced and never overwritten.

## Real native observation, not a recipe implementation

The actual ServerStartedEvent supplies the server; observations run only
on native `ServerTickEvent.END`, with `server.isSameThread()` and zero connected
players. The target additionally checks the event's server identity.

The probe verifies fixture mod/version discovery, the plain native Item/Block and
their actual BlockItem relationship, owned registry IDs and ingot stack size 32.
For each phase it enumerates the current native RecipeManager and requires exactly
six recipe IDs in the fixture namespace. It does not treat the game's “Loaded N
recipes” message or a loader error flag as proof that all six recipes loaded.

Both nested local item tags must contain exactly one expected actual registered
item and reject the other. Recipe checks then use the actual manager's
`byKey`, `getRecipeFor`, `matches`, `Ingredient.test` and `assemble`.
Manager selection must identify the exact expected recipe object/ID/type, not an
unrelated recipe of the same serializer. Positive input must match; the other owned
item must not match any recipe of that type.

The four cooking recipes assemble one block item and retain their declared times
(200/100/100/600) and experience 0.25. Stonecutting assembles two block items.
Two assembly calls must produce distinct result stack objects with the expected
item/count; the three-item input stack must remain unchanged. This deliberately
proves recipe lookup/assembly, not input consumption or XP award by a machine.

The original 3x3 shaped recipe is also matched and assembled to one block in every
phase; a missing slot must stop matching. Its real native crafting grid needs a
test-only menu notification owner. That owner is never registered or opened:
unexpected validity/quick-move interaction throws explicitly. It is not a GUI
adapter or an implementation of inventory transfers.

## Baseline, higher-priority replacement, restoration

1. Observe the original ingot-only leaf and its nested parent tag.
2. Create one new local pack below the native datapack directory of the explicitly
   owned `datapack-comparison-world`. It has native pack format 15 and
   `data/legacybridge_datapack/tags/items/processing_leaf.json` containing
   `replace:true` and the nonempty owned `bridge_block` value.
3. Call the native repository discovery API and retain the exact original selected
   pack order using `getSelectedPacks()`, not a set from `getSelectedIds()`.
   Append the test pack last, then call actual `MinecraftServer.reloadResources`.
4. On a later native END event, require the actual future to be complete without
   swallowing exceptional completion, require a new RecipeManager and exact
   selected pack order, and repeat all checks. Block must now match; ingot must not.
5. Restore the original selected list through a second actual reload and repeat
   all checks again. Ingot must match, block must not, and the test pack must be
   unselected. Only then publish the deterministic trace.

Both exact native `reloadResources` methods internally call `managedBlock` on
their own server thread. The probe does not invent another pump, manually tick a
world, run a blocking future join or use a worker-thread mutation. It only calls
`getNow` after `isDone` and limits post-return polling to 200 END ticks.
That bound does not pretend to time-limit the native method's internal blocking;
the separate runner also has its wall-clock timeout and orderly-stop gate.

Pack writing resolves the real native world path and requires it to equal the
named disposable world's datapack directory. It refuses existing packs and aliased
parents, writes only its two new files, and never touches the original/converted
JAR. The completed test pack is retained but inactive for inspection; no restart
or restoration-after-restart has been tested. On failure a partially created pack
or changed selection may remain in the disposable failed world. That evidence is
retained, the test fails, and the next attempt requires a fresh directory.

## Audited native differences

The source uses `IRecipeType`, `IRecipe<IInventory>`, `Inventory` and
`CraftingInventory`; target uses `RecipeType`, `Recipe<Container>`,
`SimpleContainer` and `TransientCraftingContainer`. Source assembly takes only
the inventory; target assembly also takes the actual server `RegistryAccess`.
The source class is `StonecuttingRecipe`; the target is `StonecutterRecipe`.
Source tags are read through the actual server tag collection. Target tags use
the actual registry's named holder set and `ItemStack.is(TagKey)`.

These were inspected in exact local official-mapped Forge source archives:

| Audit input | SHA-256 |
|---|---|
| Forge 1.16.5-36.2.42 official sources | `bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376` |
| Forge 1.20.1-47.4.23 official sources | `4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932` |

Those archives are inspection inputs, not packaged or redistributed probe assets.
The mirror observation code is an independent runtime oracle, not duplicated
production conversion rules or copied game logic.

## Fresh ordinary server preparation and evidence

After the original, both probes, current public artifact and separately validated
`build/datapack-recipes-v1/converted.jar` are ready:

```powershell
./scripts/Prepare-DatapackRuntime.ps1 -RunLabel comparison1
./scripts/Test-DatapackRuntime.ps1 -Side Source -RunLabel comparison1
./scripts/Test-DatapackRuntime.ps1 -Side Target -RunLabel comparison1
```

Run builds and both servers serially. Preparation uses only already installed
ordinary Forge libraries, source launcher/game JARs and an already accepted
isolated-test EULA. It performs no downloads, accepts no new EULA, and does not
copy previous worlds, logs, configs or mods. It refuses reused/partial destinations
and symlinks/junctions. Fresh directories are:

```text
build/datapack-recipes-v1/source-default-comparison1-server
build/datapack-recipes-v1/target-default-comparison1-server
```

Loopback ports are 25589/25590. Source installs only original fixture + native
source probe. Target installs only converted fixture + public LegacyBridge +
native target probe. All other project/P6/food/lifecycle worlds remain untouched.
Use a new RunLabel after a failed or completed attempt, never overwrite history.

The runner uses closed ordinary Forge arguments, empty runtime user/Gradle homes,
a test-owned new world and no auth/launcher tokens. This is not proof that the
machine lacks development caches: installation libraries and Java executables are
explicit local preparation dependencies. Offline behavior is NOT_TESTED; native
update/key requests may still be attempted.

Success creates `datapack-trace.json`, prints exactly one
`LEGACYBRIDGE_DATAPACK_BEHAVIOR_OK`, and requests native orderly shutdown.
The runner requires the marker, no failure, exit 0, clean save, exact trace oracle,
original/installed JAR integrity and both actual override-pack JSON files.
`behavior-receipt.json` binds versions, original/converted/public/probe hashes,
prepared-library receipt, stdout/stderr, trace and override-file hashes.
It never claims a build or conversion pass merely from server startup.

Single-input recipe arrays have layout:

```text
[positiveMatch, negativeMatch, assembledCount, declaredCookingTime,
 declaredExperienceMilli, unchangedInputCount]
```

Cooking uses `[1,0,1,time,250,3]`; stonecutting uses `[1,0,2,0,0,3]`.
The shaped vector is `[9,1,0]` (input slots, output count, incomplete match).
Actual native assertions precede recording. Three phases share these counts but
record their observed accepted/rejected registry IDs separately.

## Remaining gates

No furnace/blast-furnace/smoker/campfire progression, fuel, consumption, awarded XP,
stonecutter transfers, creative interaction, recipe-book/category UX, client assets,
translations, sound playback, multiplayer synchronization, relog or world-reopen
proof is claimed. Source and target defaults/categories can differ even when these
recipe endpoints match. No empty/optional/external tag equivalence, custom ingredient,
custom serializer, conditional recipe, loot, advancement or other datapack support
is inferred. The original and converted mod still require separate static validation.
