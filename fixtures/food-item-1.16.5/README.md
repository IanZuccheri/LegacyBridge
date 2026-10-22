# Original food-only fixture

Project-authored original Forge **36.2.42 / Minecraft 1.16.5** mod, ID
`legacybridge_food`, version **1.0.0**. It is a separate source fixture, not a
replacement for the proven simple-content or earlier logic/lifecycle fixtures.
The repository's All Rights Reserved license permits no implied public binary
redistribution grant; fixture outputs remain local test artifacts.

## Exact finite content

`FoodContent` is the genuine `@Mod` entrypoint with one native DeferredRegister,
one constructor supplier and ordinary mod-bus registration. `RationItem` is the
only other owned class. It extends native Item and contains only this constructor:

```java
super(new Item.Properties().stacksTo(16)
        .food(new Food.Builder().nutrition(3).saturationMod(0.5F).build()));
```

There is no override, custom use/eating callback, food effect, meat/fast/always-eat
flag, container/remainder item, config, custom registration event handler, creative
tab, block, recipe, custom renderer or network packet. Registry identity is
`legacybridge_food:ration`. The original model refers to
`minecraft:item/generated` and the installed vanilla `minecraft:item/gold_ingot`
texture as a deliberate validation placeholder from the existing resource
allowlist; it does not copy Minecraft texture bytes or change gold ingots.
English and Portuguese
translations use the same preserved translation key.

ForgeGradle 6.0.54 and official 1.16.5 mappings are pinned. Java 17 compiles with
`--release 8`, followed by production reobfuscation. From the project root,
build serially:

```text
gradlew -p fixtures/food-item-1.16.5 build --no-daemon --max-workers=1
```

Artifact: `build/libs/legacybridge-food-1.16.5-1.0.0.jar` inside this fixture.
Four archive-only tests inspect identity, the two original Java 8 classes,
resource references, translations and absence of platform/harness/JAR payload.
They never initialize an original mod class. They are not four supported mods.

## Independent runtime oracle

The paired food runtime probes use native Forge FakePlayer and actual game
ItemStack use/completion, inventory, hunger, drop and pickup methods. They do not
convert the probe or substitute a fake implementation of Minecraft food logic.
Initial hunger/saturation are controlled through the native food-state NBT reader,
because the source version's direct saturation setter is client-only.

With three rations in hand and zero saturation, the expected native endpoints are:

| Case | Initial hunger | Use result | Final hunger | Final saturation | Final rations |
|---|---:|---|---:|---:|---:|
| Survival, hungry | 10 | CONSUME | 13 | 3.0 | 2 |
| Survival, full | 20 | FAIL | 20 | 0.0 | 3 |
| Creative, hungry | 10 | CONSUME | 13 | 3.0 | 3 |
| Creative, full | 20 | CONSUME | 20 | 3.0 | 3 |

The enhanced probe requests the real native SURVIVAL/CREATIVE GameType through
ServerPlayer.setGameMode, respects Forge cancellation, and verifies the effective
manager state, isCreative, non-spectator status and native abilities. It never
substitutes direct ability writes for a mode transition. CONSUME must
start the actual use state with the same stack and native remaining duration 32.
Only then does the probe explicitly call `finishUsingItem`, verify original stack
identity and reset active use. Full survival hunger must not start use. All
nonempty inventory slots must contain only ration stacks within the limit.
The saturation oracle is nutrition 3 times modifier 0.5 times native factor 2.

The historical source `comparison1` observation predates that GameType correction:
it tested only native food endpoints under manually configured ability flags.
Its original receipt stays intact and is not promoted to a real-game-mode pass.
The preserved source `comparison2` passed native GameType checks; target comparison2
failed the strict drop lookup because a synchronous FULL chunk request did not
establish native entity visibility. Fresh `comparison3` probes preserve that
failure and additionally wait for real entity-ticking readiness on two consecutive
server END ticks (maximum 200), using one native forced-chunk ticket for 0,0.
The probe requires a fresh empty forced set and releases only its ticket before
publishing the trace. No private state writes, manual ticks or lookup fallbacks
replace native readiness. Existing conversion and fixture bytes are unchanged by
this test-environment correction.

The fresh trace must include `nativeGameModeValidated=true`, observed game-mode
IDs `[0,0,1,1]`, `nativeChunkReadinessValidated=true`, phase `SERVER_END` and
`nativeForcedChunkReleased=true`; the drop phase returns natively to SURVIVAL
(ID 0). Runtime wait counts belong only to the environment log/receipt, not the
paired deterministic behavior trace. These new probe sources are not a runtime
pass until the exact final artifacts are independently rebuilt and run.

For three fresh native drops, inventory begins with seven rations, splits two
before calling player.drop, and must contain five while the world contains the
returned live two-item entity. The world must discover that exact entity by ID.
After native playerTouch pickup, inventory must contain seven and the entity must
be removed. A new entity is used each cycle. Removed entities retain stack data
for native pickup feedback and are not counted or invoked again.

## Limits of this evidence

FakePlayer.tick is a no-op. The probes deliberately do **not** claim a complete
32-tick eating sequence, Forge finish-use event timing, client animation, sound,
player UX, latency, multiplayer, death drops, full inventory/container edge cases,
save/reload persistence or a general anti-duplication guarantee. Pickup calls the
real server endpoint after clearing the native delay; automatic movement/collision
detection itself is not exercised. Source and target share the measured endpoint
oracle; the target's native `GameEvent.EAT` side effect has no equivalent in the
source version and is not claimed to be erased or fully behaviorally matched.

No original class executes during static analysis/conversion. Original execution
belongs only to a separately authorized source Forge server; converted execution
belongs to a separate target Forge server with the current public LegacyBridge
JAR. Build, conversion, bytecode/linkage validity and runtime behavior have separate
hash-bound evidence. These source files alone claim none of those gates passed.

See the [source probe](../food-runtime-probe-1.16.5/README.md) and
[target probe](../food-runtime-probe-1.20.1/README.md). Do not enable unsupported
effects, callbacks, remainders or other item/block categories merely because the
plain food constructor was analyzable.
