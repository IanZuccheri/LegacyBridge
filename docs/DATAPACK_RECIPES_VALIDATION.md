# Datapack recipes: independent validation evidence

Date: **2026-09-06**. Status: **experimental, scoped developer profile**.
This record covers only [datapack-recipes-v1](DATAPACK_RECIPES_PROFILE.md), not
every requested datapack/resource format or any whole real corpus mod.

The [machine-readable ledger](evidence/datapack-recipes-v1/validation.json)
binds 22 local evidence files by size and SHA-256. The
[observed native trace](evidence/datapack-recipes-v1/observed-trace.json) contains
actual observations, not a mockup or a fabricated gameplay screenshot.

## Build and static tests

The serial root build passed with **832 tests: 830 passed, 2 existing Windows
symlink-privilege skips, zero failures/errors**. Module counts: converter 604,
corpus 84, product 82, runtime 30, workflow 32. The two skips are in converter
and corpus. There are **48 new root cases**: 27 resource policy tests, four
genuine-JAR integration cases, six independent depth/pack audit regressions and
11 ASCII parser cases. These numbers are not numbers of compatible mods.

The genuine Forge-36 fixture and both independently compiled native probes also
passed their separate builds, with five tests each (**15 additional fixture/probe
tests**). They are not included in the root count or the public product. The
target probe produces a deprecation warning for the real pinned ResourceLocation
constructor; no invented replacement API was introduced.

Logs under `build/datapack-recipes-v1/`:

- `root-build2.log`: full implementation, regression and packaging checks,
  BUILD SUCCESSFUL, 2m24s.
- `fixture-build.log`, `source-probe-build.log`, `target-probe-build.log`:
  separate genuine Forge builds, all successful.
- `root-build1.log`: earlier successful implementation build retained; it
  predates the independent depth and pack-decoding corrections and is not
  substituted for the second build.

The full root command used all existing real-baseline flags plus
`-PverifyDatapackEnvironment=true`, the explicit 13-source local mapping profile
for logic/food/datapack and the separate 17-source lifecycle profile:

```powershell
.\gradlew.bat build -PverifyMappingEnvironment=true -PverifyLogicEnvironment=true -PverifyLifecycleEnvironment=true -PverifyFoodEnvironment=true -PverifyDatapackEnvironment=true "-PlogicMappingProfile=${LEGACYBRIDGE_SOURCE_ROOT}/build/logic-preserving-v1/mapping-profile.properties" "-PlifecycleMappingProfile=${LEGACYBRIDGE_SOURCE_ROOT}/build/lifecycle-preserving-v1/mapping-profile.properties" "-PfoodMappingProfile=${LEGACYBRIDGE_SOURCE_ROOT}/build/logic-preserving-v1/mapping-profile.properties" "-PdatapackMappingProfile=${LEGACYBRIDGE_SOURCE_ROOT}/build/logic-preserving-v1/mapping-profile.properties" --no-daemon --max-workers=1
```

All builds use JDK 17, Windows ROOT trust-store integration and normal TLS
verification. Native source execution separately uses Temurin 8u492-b09;
target execution uses Oracle 17.0.12. No Gradle/game operations ran concurrently.

## Artifacts and byte preservation

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Public `LegacyBridge.jar` | 1,159,454 | `09409a47c724f0a261766bbe2fc6edfe81fb01cb6c5c58f7820622ceb4868443` |
| Original fixture 1.0.0 | 12,147 | `9b71a7b57fb8657355166b8f5353b0454284df1cce21ca682ccbb204129665c5` |
| Converted fixture | 9,503 | `97d4f699c3d31d05ac42288a3946a6d451429d1ecbf86957c3fb14883e2d8979` |
| Source QA observer | 16,531 | `58d29da135eeaf852dc616f6e7093aae5f2bfd47e8c49dc3a87a908f6ef990b6` |
| Target QA observer | 16,815 | `61a06b28359fb4e17250b880806cbdb59038a68058065295f2f212786cb57068` |

The public JAR audit passed: one runtime identity, existing exact adapter ABI,
one unchanged JiJ library, no platform/logging duplicates, old game/Forge,
private mappings, fixtures or QA observers. CLI remains in the development
distribution, not a player requirement. The target native run used this exact
public artifact, not Gradle source sets.

Static conversion was repeated after the safety fixes; the original and
rechecked converted JARs are byte-identical. CLI/service canonical reports and
output bytes agree. Planning remains private and cancellation at PRE_PUBLICATION
leaves no result or temporary in the requested output directory.

The conversion report at `build/datapack-recipes-v1/rechecked/conversion.json`
has SHA-256 `cb732ae90a5f18c344f30ca8573a5f9096c9fb04003daa82d5ee11285229b2d9`.
All **19 source asset/data entries** are preserved byte-for-byte, including six
recipe documents and the local tag chain. Original modId, version, item/block
registry IDs and all recipe resource IDs are unchanged. Necessary existing
metadata/bootstrap/mining operations remain explicit.

The report records one input class, one generated bootstrap class and 144
scanned references; CheckClassAdapter dataflow and offline target reference-type
analysis passed. **General JVM linkage is not claimed by this bootstrap static
gate.** No source class was defined, initialized or executed during conversion.
The independent native runs provide the scoped actual linkage/registration proof.

TransformationLog schema 1 has 35 entries: CLASS_REMAP 16, DESCRIPTOR_REMAP 1,
FIELD_REMAP 6, ADAPTER_CALL 6, RESOURCE_REWRITE 6. Every change has an implemented
adapter binding; the unchanged recipes are preservation decisions, not fake
rewrites. Conversion schema 2 and resourceValidation schema 1 explicitly retain
`runtimeValidation=NOT_RUN`; later runtime receipts are separate, not retroactive
claims embedded by static conversion.

## Negative gates

Tests refuse missing/foreign/air/unaudited item IDs, wrong tag kinds, missing
required or optional tags, cycles, empty/transitively empty tags, unreviewed
external tag composition, unsupported serializers/ingredients/NBT results,
conditional wrappers and **even false or empty conditions**. They never evaluate
unknown mod code, strip a branch or remove a resource to obtain success.

Parser tests cover duplicate keys, trailing content, truncated JSON, lenient
syntax, malformed UTF-8, BOM, oversize/depth limits, non-ASCII numeric/hex
tokens, malformed pack description bytes, and out-of-range XP/time/count.
Source-only ignored recipe IDs and target-only category/smithing schemas reject.
`replace:true` and `replace:false` for valid local tags retain their exact bytes.

Seven negative genuine fixture-JAR variants are exercised within the integration
case: missing result ID, false condition, custom serializer, custom ingredient,
truncated parser, duplicate keys and target-only serializer. Each yields
ABORTED/UNSUPPORTED, no output JAR and unchanged input. The remaining profile
negative and parser-bound cases are in-memory fixture resources. Native parser
failure injection and false-condition execution are **not claimed**; those
unsupported inputs are blocked before publication.

The independent audit fixed two additional bugs: child-first tag traversal could
hide a path longer than 64 edges through a done-set shortcut; malformed pack
UTF-8 could be replaced silently before rewriting. New tests verify exact
64/65-edge boundaries and strict, bounded pack decoding. Ordinary valid Unicode
descriptions and all existing fixture outputs remain unchanged.

## Native source/target comparison

After the static gates and separate probe builds:

```powershell
.\scripts\Prepare-DatapackRuntime.ps1 -RunLabel comparison1
.\scripts\Test-DatapackRuntime.ps1 -Side Source -RunLabel comparison1
.\scripts\Test-DatapackRuntime.ps1 -Side Target -RunLabel comparison1
```

The preparation copied only already installed, exact Forge libraries/launchers
and an already accepted isolated-test EULA. Fresh loopback-only servers, worlds,
mods directories, user/Gradle homes and temporaries were created. No existing
world, launcher profile, credentials, configs or mod installation was changed.
No new EULA was accepted and preparation downloaded nothing.

Source used original+source observer; target used converted+public Bridge+target
observer. The QA observers are separately authored native test code, never part
of analysis/conversion or the public JAR. Both processes exited **0**, emitted
the unique `LEGACYBRIDGE_DATAPACK_BEHAVIOR_OK ... nativeReloads=2` marker, saved
chunks normally and retained empty stderr. Source took 24s and target 33s.

Both actual traces have 1,551 bytes and the same SHA-256:
`17e53ffbe5aa04f34a468c1541fb5f7985b186da9043cad9bc3627f1f60dd400`.

Observed sequence on the actual server thread, without connected players:

1. Forge discovers fixture `legacybridge_datapack:1.0.0`; native Item, plain
   Block, BlockItem relationship and item stack limit 32 are verified. Exactly
   six intended recipe IDs exist, not merely an ambiguous loaded-type count.
2. Baseline local tag chain accepts bridge_ingot and rejects bridge_block.
   Native recipe lookup, Ingredient and matches agree. Each cooking recipe
   returns one block; stonecutting returns two. Configured time is 200/100/100/600
   and XP 0.25. Shaped crafting requires the complete nine-ingot grid.
3. An owned, actually discovered higher-priority pack replaces the leaf tag
   with **bridge_block**, using `replace:true`. Native reload returns a completed
   future and replaces RecipeManager. The new manager accepts block and rejects
   ingot for all five single-input recipes; shaped crafting is unchanged.
4. A second native reload restores the original ordered pack selection and
   matching. The owned override remains on disk **inactive** for inspection.
   No source JAR is modified or deleted. The runner hashes both override files.

Each assembly returns independent result copies and leaves its three input
items unchanged, which is the actual recipe endpoint contract. **This is not
proof of machine consumption or anti-duplication safety.** Furnace/smoker/blast
furnace ticks, campfire drops, fuel, awarded XP, stonecutter player transactions,
client book categories and synchronization have not been exercised.

Receipt hashes and logs are in the ledger; native receipt paths are
`build/datapack-recipes-v1/{source,target}-default-comparison1-server/`.
Both original and installed-mod hashes were revalidated after execution.

## Cacheless and previous-profile regression

The existing guarded Java-17 consumer ran in a fresh temporary installation
with explicit portable local mapping/API data, empty redirected user/Gradle/temp
directories and `-Xmx512m`. It reported **three identical CLI/service canonical
report pairs and identical converted bytes**, zero forbidden-file/network/process/
exit attempts and the original unchanged. Missing mappings still return the
explicit MAPPING_DATA_REQUIRED/CLI exit 3; nothing is downloaded or guessed.

This is a **test-only** deprecated SecurityManager tripwire, not a production
security sandbox. Java-home and exact ancestor metadata reads are expressly
permitted. The supplied bundle still has local-use/redistribution limits; the
new policy does not embed or grant rights to game data. Native Forge runs were
not air-gapped: version checking can attempt network/TLS, so **offline game
operation remains NOT_TESTED**.

Fresh conversions of simple, logic, lifecycle, lifecycle-inferred and food were
compared with the preceding retained artifacts. All five output JARs are
byte-identical; every original remained intact. Their hashes appear in the
ledger. This is static regression evidence, not fresh runtime certification of
all older profiles with the new public JAR.

## Remaining gates and exclusions

The profile is explicit CLI/API development functionality. The native player
screen and transactional installer still use the original simple-content policy
and **do not accept this expanded resource profile**. Native validation used
deliberate staging into a stopped disposable installation, not a new UI flow.
The default remains functional; no runtime capability or original mod class was
silently enabled. No automatic restart or hot loading was added.

No client was launched, no screenshots were fabricated, no multiplayer session
was run and no artifact was uploaded. Visual resources remain within the old
static policy; native model/texture/language reload was not newly certified.
Unknown loot tables/modifiers, advancements, functions/commands, conditional or
custom recipes and animated/custom client formats remain **UNSUPPORTED**.

The [corpus audit](RESOURCE_CORPUS_AUDIT.md) found zero `data/**` producers in the
three exact real releases. **No whole real mod was newly unlocked or behavior-
validated**. Keep the product experimental/Beta and use the explicit next-format
gates, not test counts or resolved-symbol percentages, to decide later expansion.
