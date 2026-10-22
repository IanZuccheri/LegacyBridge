# Food-item validation — 2026-09-06

Status: **experimental, developer-only closed slice**. This step does not enable
tools, armor, block behavior or arbitrary food callbacks. The UI/installer default
remains simple-content-v1. No real corpus release is newly unlocked.

See [the exact profile](FOOD_ITEM_PROFILE.md), [API evidence](FOOD_API_BASELINES.md)
and [corpus review/prioritized batches](ITEM_BLOCK_CORPUS_AUDIT.md).

## Static gates actually executed

| Gate | Result |
|---|---|
| Root build/test | Passed: 784 tests, 782 passed, 2 existing Windows symlink-privilege skips; zero failures/errors |
| New root tests | 52: 35 closed-shape, 4 implemented-adapter, 13 genuine-JAR integration tests; not a mod-compatibility count |
| Original fixture build | Genuine Forge 36.2.42, Java 8 classfiles; four archive-only tests passed |
| Conversion | Two owned classes/all three methods preserved, no replacement bootstrap |
| ASM validation | Passed on original and exact output; offline frame/max computation |
| Source/target linkage/access | PASSED_SCOPED; no original class loaded, not a complete JVM/module proof |
| CLI/API parity | Canonical analysis/mapping/conversion reports and converted bytes identical |
| Private planning/cancellation | Planning publishes nothing; accepted PRE_PUBLICATION cancellation leaves no JAR/temp |
| Previous profile isolation | All three previous profiles refuse the food fixture without output |
| Old output regression | Four fresh outputs byte-identical to the approved simple/logic/lifecycle baselines |
| Packaging | Current single public JAR passes class/library/service/private-data checks; probe/fixture code excluded |

Module totals are converter 556, corpus 84, product 82, runtime 30 and workflow 32.
The fixture/probe builds are separate projects, not added to that root total.
Prior client/UI evidence is historical and is not transferred to this artifact.

The positive JAR defines only `legacybridge_food:ration`, preserving mod version
1.0.0 and original subclass `dev.legacybridge.fixture.food.RationItem`. It has
stack size 16, nutrition 3 and saturation modifier 0.5. Supported resource bytes
and both translation keys/values survive static conversion. The model deliberately
uses an already allowlisted native gold-ingot texture placeholder; no Minecraft
texture is copied and no gold-ingot gameplay is changed. Client asset loading is
not proved by byte preservation.

Negative genuine JAR mutations cover native fast/meat/always-eat methods,
durability, remainder, a known Item override, early RegistryObject access,
unknown external calls, a missing mandatory server dependency, invalid nutrition,
NaN/infinite/negative saturation and an incorrect interface opcode. Each is
refused without a partial output, with immutable inputs and serializable errors.
Additional inert ASM tests cover effects, helpers/state, alternative control flow,
constructors, ownership, supplier/bus/registry mismatch and literal bounds.

## Cacheless operation, not a hidden downloader

The installed CLI libraries, test-only consumer and explicitly prepared local
13-source logic mapping bundle were copied to an independent temporary directory.
Java 17 ran with Xmx512m, initially empty user-home/Gradle/temp directories and
test-only file/network/process/exit tripwires. Three canonical report pairs and
the converted JAR matched; original bytes stayed intact; forbidden file, network,
process and exit attempts were all zero. The empty directories stayed empty.
Missing mapping data explicitly returned MAPPING_DATA_REQUIRED / CLI exit 3.

The Java 17 SecurityManager used by that test is deprecated and is **not** a
production security sandbox. Trusted JDK reads and exact ancestor metadata reads
are explicit exceptions. The retained mapping bundle is local-use-only, not
approved for redistribution or included in LegacyBridge.jar. Generic mapping
inspection still requests review; its symbol counts are not a compatibility
percentage or permission to rewrite. Conversion additionally requires the exact
independently reviewed profile recipes.

## Native runtime proof boundary

The original and converted JARs are executed only in separate authorized QA
processes, never during conversion. Ordinary Forge server installations copy
previously prepared exact libraries and an already accepted isolated-test EULA;
they do not use Gradle runServer, previous worlds, launcher profiles or user mods.
The target contains the public LegacyBridge.jar, converted fixture and a test-only
observer. This is not a public-only/no-observer or client test.

The final `comparison3` passed on source Forge 36.2.42 / Temurin 8u492-b09 and
target Forge 47.4.23 / Oracle Java 17.0.12. Both exited 0 after normal saves.
Each native mode transition was checked against the actual manager, isCreative,
non-spectator state and native abilities. The sole test-owned chunk reached
native entity readiness for two consecutive SERVER_END observations (two ticks
in both runs), and its ticket was released before publishing the trace.

| Native mode / starting food | Use admitted | Final food / saturation | Stack before → after |
|---|---|---|---|
| Survival / 10 | Yes | 13 / 3 | 3 → 2 |
| Survival / 20 | No | 20 / 0 | 3 → 3 |
| Creative / 10 | Yes | 13 / 3 | 3 → 3 |
| Creative / 20 | Yes | 20 / 3 | 3 → 3 |

Three independent idle survival drop/pickup cycles started with seven ration
units. Native inventory removal left five; the real world-discovered live drop
contained two. Full pickup removed that entity and returned inventory to seven.
Every nonempty slot retained the expected item and stayed within stack limit 16;
no effects or container items appeared. Removed entities were never counted or
replayed as additional items. These finite cases show no loss/duplication, not
arbitrary anti-dupe safety.

The two complete observed traces are byte-identical, SHA-256
`369b8fc3cb57dc8c540038b4f112f3f4215555e5edb9ec7e5775c98c73fb4eb8`.
[The versioned trace](evidence/food-item-v1/observed-trace.json) contains actual
native observations, not a mockup or simulated eating implementation. Both
probe builds passed their four independent archive/writer tests (12 total
including the original fixture's four tests).

FakePlayer.tick is a no-op on both versions. Native use/finish endpoints do not
prove the full 32-tick interaction, Forge finish-use event sequence, client eating
animation or multiplayer. Native pickup on a live world entity is not automatic
collision timing. Last-unit consumption, full/partial inventory, active-use drops,
death drops, cancellation by other mods, persistence/relog, stats and advancements
are not certified. No general anti-duplication guarantee follows from finite
inventory-count checks.

Target EAT/ITEM_INTERACT_START/ITEM_INTERACT_FINISH game events are an explicit
native migration; old/new world or sensor reactions are not claimed identical.
No Minecraft/Forge class is patched to imitate the old system. Fully offline
Minecraft was not tested: native Forge version checks can attempt network access;
online-mode=false is not network isolation.

## Issues found, retained rather than hidden

- The first positive fixture used an unaudited bread-texture reference and was
  correctly refused. The fixture was changed to an existing allowed placeholder;
  the shared resource allowlist was not broadened.
- A test retained an additional full mapping graph while CLI/API loaded theirs,
  exhausting the test JVM heap. Lazy test-owned mappings with per-test release
  fixed it; no heap-limit increase or production bypass was introduced.
- The first probes changed abilities only. Native GameType is now selected via
  the actual player transition and checked separately from ability flags.
  comparison1 receipts remain historical ability-only endpoint evidence.
- comparison2 source passed, but target refused the drop-world lookup despite
  an alive entity. Source inspection established that FULL chunk availability
  alone does not prove entity tracking readiness during ServerStartedEvent.
  That failed target gate and its logs remain retained; process exit 0 alone
  was correctly insufficient for success.
  The native ticket/readiness correction passed both fresh comparison3 servers;
  it did not weaken lookup or alter any converter/platform code.
- The pinned target probe build warns that ResourceLocation(String,String) is
  deprecated. Native Forge update checks also reported PKIX certificate warnings;
  TLS was not disabled. These warnings are not substituted for behavior assertions.

## Reproduction and local evidence

| Final artifact | Bytes | SHA-256 |
|---|---:|---|
| Original food fixture | 4,975 | `f0549a65b861525385b7c1cc422624496afda6b2113cbb99243f5e3e35e46b62` |
| Converted food fixture | 4,202 | `ecf854aa1826641f6a2ef305b188e685d40178ff9740dc4b5dc1c4f7bff9234e` |
| Public LegacyBridge.jar | 1,154,179 | `f255a92f730c100074702e61fbede63e984bfa3c35941a0fb5ed450d93e12a72` |

[The versioned validation receipt](evidence/food-item-v1/validation.json) binds
probe/JAR/build/log/data hashes and separates unperformed gates. Raw logs,
worlds, original/converted JARs and local-use mapping data remain in ignored
build storage, not the public product. Original bytes were rechecked after
conversion, cacheless checks and both native runs.

Build the three standalone food fixture/probe projects serially, then the root
with verifyMappingEnvironment, verifyLogicEnvironment, verifyLifecycleEnvironment
and verifyFoodEnvironment enabled and their explicit mapping profile properties.
The food profile uses the same local mapping profile/bundle as logic-preserving-v1.

```text
legacy-converter convert fixtures/food-item-1.16.5/build/libs/legacybridge-food-1.16.5-1.0.0.jar --conversion-profile food-item-v1 --mapping-bundle build/logic-preserving-v1/mapping-bundle --output build/food-item-v1/converted.jar --report build/food-item-v1/conversion.json
```

Use a fresh output/report and fresh runtime RunLabel for each execution. The
preparer/runner refuse prior worlds, logs, artifacts or unsafe paths. Builds and
games must never run concurrently.

After conversion and closing every build/game process, prepare a new RunLabel
with `scripts/Prepare-FoodRuntime.ps1`, then run `scripts/Test-FoodRuntime.ps1`
first with `-Side Source`, then `-Side Target`, using the same fresh label.
The completed run used `-RunLabel comparison3`; repeating that label is refused.

- [Root build](../build/food-item-v1/root-build-complete.log).
- [CompatibilityReport](../build/food-item-v1/cacheless-reports/analyze-service.json).
- [Conversion and TransformationLog](../build/food-item-v1/conversion.json).
- [Cacheless CLI/API evidence](../build/food-item-v1/cacheless-reports/cacheless-engine-evidence.json).
- [Source server log](../build/food-item-v1/source-default-comparison3-server/behavior-stdout.log) and [receipt](../build/food-item-v1/source-default-comparison3-server/behavior-receipt.json).
- [Target server log](../build/food-item-v1/target-default-comparison3-server/behavior-stdout.log) and [receipt](../build/food-item-v1/target-default-comparison3-server/behavior-receipt.json).

No files were published/uploaded. The broader exclusions and next independent
batches remain in the profile/corpus documents; native endpoint progress does
not enable unimplemented conversion categories.
