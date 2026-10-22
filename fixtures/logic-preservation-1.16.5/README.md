# Genuine Forge 1.16.5 logic-preservation fixture

This new, separate project-authored fixture targets Minecraft **1.16.5 / Forge
36.2.42**, Java 8 bytecode. It does not change the existing `simple-content-v1`
fixture or establish a newly supported conversion profile by its existence.
Its one registered item is **not plain content**: correctness requires preserving
its subclass and the original mod-owned behavior graph.

Build serially from the LegacyBridge root using JDK 17 and the pinned wrapper:

```powershell
.\gradlew.bat -p fixtures/logic-preservation-1.16.5 build --no-daemon --no-parallel --max-workers=1 --console=plain
```

The standalone build uses actual ForgeGradle 6.0.54, official 1.16.5 development
mappings, `javac --release 8` and `reobfJar`. Its output is
`build/libs/legacybridge-logic-1.16.5-1.0.0.jar`, with mod ID
`legacybridge_logic`, version `1.0.0`. Four archive-only tests inspect the
production JAR; none executes the original classes. A successful build does not
prove conversion, JVM linkage, initialization, behavior or multiplayer.

## Behavior being preserved

`parity_token` is a `LogicItem`, with max stack size 16. Its native
`Item.isFoil(ItemStack)` override calls `super.isFoil(stack)` first, then invokes
an original `ScoreRule` through an interface. The registered item must remain
the exact original mod-owned subclass after conversion; replacing it with a
plain Item would lose its behavior even if registration succeeded.

The six classes cover:

- Original Forge entrypoint and a constructor-reference registration supplier.
- External Item inheritance with one native override and `super` dispatch.
- Mod-owned interface and subclass dispatch (`ScoreRule`, `ParityRule`, `BaseRule`).
- Protected/final instance fields, a shared static instance counter, and two
  fields retaining the same rule-object identity.
- A negative-input exception thrown by the superclass and caught by its subclass.
- A three-iteration loop and four-way switch.
- One class-initialization seed and initialization counter, a static
  `IntUnaryOperator` method reference, and a primitive-capturing lambda.

The runtime logic uses no file/network I/O, logging, reflection, System calls,
collections, StringBuilder, original-side probe or conversion hook. The only
non-platform classes packaged are this original fixture's six classes.
The item model references `minecraft:item/iron_ingot`; no Minecraft texture
bytes are copied. EN/PT translation JSON and the model JSON are original.

## Deterministic runtime oracle

Only a **separate, explicitly authorized runtime test**, after Forge has loaded
the original or converted artifact, may invoke these methods. Analysis and
conversion must still never execute an original class. Do not run the original
fixture and converted fixture together: they intentionally share a mod ID.

Start each side in a fresh JVM. After registration, find the single
`legacybridge_logic:parity_token` and assert class name
`dev.legacybridge.fixture.logic.LogicItem` and max stack size 16. Before any
scoring/glint calls, `callCount()` is 0 and `ruleInstances()` is 1. The probe may
then call `helperSeed()` and `helperInitializationCount()`; these deliberately
initialize the helper if it has not run yet, and must report 7 and 1. No reset
method exists. Repeated checks must remain 7 and 1.

Invoke `score(int)` on that same registered item in exactly this order:

| Input | Result | Call count afterwards |
| ---: | ---: | ---: |
| -3 | -1002 | 1 |
| 0 | 17 | 2 |
| 1 | 22 | 3 |
| 2 | 27 | 4 |
| 3 | 32 | 5 |
| 8 | 33 | 6 |
| 16 | 49 | 7 |
| 31 | 88 | 8 |
| 64 | 145 | 9 |

These values are a hand-derived test oracle, **not an execution result**. For
nonnegative `n`, the formula is `2*n + 17 + 3*(n & 3)`; a negative call returns
`-1000 - bias` after incrementing the same instance counter. The call counter
does not affect the return value: render call frequency must never become a
visual-state toggle. `ParityRule`
implements that formula through inheritance/control flow/lambdas, not by
hardcoding the oracle vector.

Next call native `ItemStack.hasFoil()` with unenchanted nonempty stacks of
counts `[1, 2, 3, 8, 16, 31, 64]`. Expected booleans are
`[true, false, true, false, false, true, false]`, with call counts 10 through 16.
Counts above max stack size are an explicit method-level probe, not a claim that
players can acquire oversized stacks. A separately enchanted stack must return
true by the native superclass path **without incrementing** the rule counter.
`ItemStack.hasFoil()` must dispatch into the registered item's original override.
The runtime probe must construct enchantment data using separately verified
version-native APIs; source/target setup code is not part of this mod.

Public fixture-owned probe signatures (binary descriptors unchanged):

```text
LogicItem.score(I)I
LogicItem.callCount()I
LogicItem.ruleInstances()I                 static
LogicItem.helperSeed()I                    static
LogicItem.helperInitializationCount()I     static
BaseRule.instanceCount()I                 static
LogicMath.seed()I                         static
LogicMath.initializationCount()I          static
```

The external override uses official source signature
`boolean Item.isFoil(ItemStack)`; its production owner/descriptor/member mapping
must be verified independently for each Forge version. Probes write bounded,
deterministic JSON behavior traces outside the fixture, compare observations
with this oracle and with each other, then request orderly server shutdown.
Exceptions must fail the runtime test, not be hidden by a fake success marker.

See [runtime environment plan](../../docs/LOGIC_FIXTURE_RUNTIME_PLAN.md).
This fixture remains subject to the repository's **All Rights Reserved**
license; local test authorization does not grant binary redistribution.
