# Lifecycle-preserving validation — 2026-09-06

Status: **experimental developer profile**, not general lifecycle/config/command
compatibility. The [profile contract](LIFECYCLE_PRESERVING_PROFILE.md) is opt-in via
CLI/shared API; the player UI and transactional installer still select
`simple-content-v1`. Two source forms of one original authored mod are exercised,
not two newly supported real mods. No complete real corpus release is unlocked.

## Build, static conversion and regressions

All builds and runtime runs were serialized. Local log root:
`build/lifecycle-preserving-v1/`.

| Gate | Recorded result |
|---|---|
| Root build | `root-build-attempt-4.log`: BUILD SUCCESSFUL; 732 tests, 730 passed, two existing Windows symlink skips |
| Root test breakdown | Converter 504 / one skip; corpus 84 / one skip; product 82; runtime 30; workflow 32 |
| New root coverage | 77 additional tests, included in the root total, not a mod-compatibility count |
| Four fixture/probe builds | 23 tests passed across the explicit/inferred Forge 36 fixtures and the two native runtime probes |
| Genuine-JAR admission checks | Eight cases: seven negative mutations plus one positive inferred-listener control |
| CLI/shared API | Genuine analysis, planning and conversion passed; canonical reports and converted JAR bytes agree |
| Static output validation | Four owned classes retained; source/target declaration, access, hierarchy, constructor and handle checks plus ASM dataflow passed; no original class loaded |
| Old profiles | Fresh simple/logic conversions reproduce their previous output SHA-256 exactly |

The genuine negative mutations reject a client-setup event token, a command
subscriber on the MOD bus, constructor invocation of queued work, CLIENT config,
division by zero in queued work, an escaping future and a retained legacy
`IForgeRegistryEntry` field. Rejection publishes no partial output. The positive
control retains native `addListener(Consumer)` without inventing a Class argument.
Additional unit/integration coverage includes unknown APIs, early reads, callback
provenance, dependencies and closed instruction envelopes.

The first CLI attempt correctly aborted with `LOGIC_UNSUPPORTED_TYPE` for the
real enclosing `ModConfig` metadata type; its declaration was then verified and
admitted without admitting arbitrary methods. Root attempt 3 failed after large
static mapping inputs remained retained across integration test classes. The fix
releases those references in `@AfterAll`; the successful rerun did not increase
the test heap. Earlier failures remain in the local evidence directory.

### Cacheless engine evidence

`cacheless-reports/cacheless-engine-evidence.json` reports PASSED under Java 17,
with initially empty user/Gradle/temp directories and an explicitly supplied
local portable mapping bundle. The test guard recorded **zero** forbidden file,
network, process-execution and process-exit attempts. CLI/API canonical JSON and
JAR bytes matched, the original remained unchanged, and owned temporary storage
was empty afterward. Missing data produces `MAPPING_DATA_REQUIRED`, not a hidden
download. The Java installation and narrowly permitted ancestor metadata reads
are declared exceptions; this test-only guard is not a production sandbox.

Generic `mappings` can still report MANUAL/UNSUPPORTED candidates while the
separate finite recipe compiler proves its exact admitted contracts. Neither
resolved-symbol counts nor test counts are a compatibility percentage. Prepared
mapping inventories remain local-use-only, not approved for redistribution.

## Separate native dedicated-server observations

Source: Minecraft **1.16.5**, Forge **36.2.42**, Java **8**. Target: Minecraft
**1.20.1**, Forge **47.4.23**, Java **17**. Each fresh target used the ordinary Forge
server argument file, the public LegacyBridge JAR, the converted fixture and a
test-only native probe. The probe is not a required public artifact. Source QA
executes the authorized original only in its separate original-version server;
analysis and conversion never invoke that runner.

Eight initial runs passed: four source and four target runs. Their paired traces
agree for the following bounded observations:

| Scenario | Loaded COMMON values | Command inputs → results | Paired trace |
|---|---|---|---|
| Default | `enabled=true`, `factor=3` | `[7,0,100] → [21,0,300]` | [default](evidence/lifecycle-preserving-v1/default-trace.json) |
| Configured startup | `enabled=true`, `factor=5` | `[7,0,100] → [35,0,500]` | [configured](evidence/lifecycle-preserving-v1/configured-trace.json) |
| Invalid numeric config | Input `factor=99` corrected natively to `8` | `[7,0,100] → [56,0,800]` | [invalid config](evidence/lifecycle-preserving-v1/invalidconfig-trace.json) |
| Inferred listener | Same defaults, native Consumer overload retained | `[7,0,100] → [21,0,300]` | Same default trace |

Every trace observes the original mod/registry identity, the owned Item class,
stack size 16, exactly one setup callback and queued-work completion, identity
with the registered item, loaded config values, one command registration,
permission-level-1 denial and three rejected argument cases without executing
the command body. Execution counts then progress `[1,2,3]`. The probe itself ran
on the server thread; the **queued-work thread was NOT_OBSERVED**.

Final public artifact: `LegacyBridge.jar`, **1,143,519 bytes**, SHA-256
`5bad76f00c88a342ec3af590315436f95b88f79ddbccf3cb951b5640ead0ac90`.
Final-artifact repetition: Default, Configured, InvalidConfig and Inferred all
**PASSED**, each with exit code 0 and the exact public hash above.
The fresh repeat directories are `target-<variant>-final-server`; earlier target
successes are not substituted for this final-artifact gate. Each final target's
trace SHA-256 equals its corresponding original
`source-<variant>-server/behavior-receipt.json`:

| Trace pair | SHA-256 |
|---|---|
| Default and Inferred | `f84ce1bcd731adb786a1881f4e7e7b49eccff602ccb261b75335334054bcf58b` |
| Configured | `20d28f3ae4e931d6295b111ca5d710c972a54c77d25b4e811c5221b6e800343b` |
| InvalidConfig | `d3cdb90c5373d4efdedfe0fccf0f76565f975cf83cd8073ec0ef7f8fd46832eb` |

Each `behavior-receipt.json` binds the installed originals/converted/public/probe
JARs, preparation inventory, Java executable, trace, stdout/stderr and config
hashes. It records `originalIntact=true` after rechecking the source artifact.
The [consolidated portable receipt](evidence/lifecycle-preserving-v1/validation.json)
records the eight final comparison receipts, verified hashes and separate gates.
Raw logs and full local installation inventories remain under the build root;
they are not public JAR contents.

### Artifact identities

| Artifact | SHA-256 |
|---|---|
| Original explicit fixture | `a97836fd2b9cd1deb5c0f5d5dd66388b6320209b4909c703611abf778c3d817d` |
| Converted explicit fixture | `68e9550129d4a2cb88fa184323ae7d4badef8ffb4f31f328e10dfa0eccdba58c` |
| Original inferred fixture | `476981d6725ed3cf5b3bcfd5a06ef7f377fcd0ce84a4530348b9288ec85b07ac` |
| Converted inferred fixture | `b3aad65a5871fd5deb1884b332ca3019c9dfbfb4631f9928331db1f29610dd68` |
| Unchanged simple-profile regression | `541cc64b44b97d0e4e79a193dd2ce3a86a5feba6e15714d0ee24ad63751492bb` |
| Unchanged logic-profile regression | `78de0e2fd62ed83315518a1980ae0738b354531736a5eea73195d332aecc186d` |

## Why the two contextual descriptor operations exist

These are exact native contracts, not a global erasure map:

- Legacy `RegistryObject.get()IForgeRegistryEntry` becomes target `get()Object`
  only for the proven owned late reference, preserving its Item CHECKCAST.
  Actual source entries: Forge 36 `net/minecraftforge/fml/RegistryObject.java`,
  lines 103–107; Forge 47 `net/minecraftforge/registries/RegistryObject.java`,
  lines 201–205. Exact archive hashes and evidence are in the
  [registry API audit](LIFECYCLE_API_BASELINES.md#1-registryobjectget-return-erasure-is-a-real-abi-change).
- `ModLoadingContext.registerConfig(Type,ForgeConfigSpec)V` becomes
  `registerConfig(Type,IConfigSpec)V`, with the real target ForgeConfigSpec
  implementing IConfigSpec. Actual entry `net/minecraftforge/fml/ModLoadingContext.java`:
  source Forge 36 line 48, target FML Core 47.4.23 line 107. Source archive SHA-256
  `bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376`;
  target source archive SHA-256
  `fc6df13c54c211ea9c0220ec76a2ebe1e573187112467cfc1397a25fee191ec1`.
  See the [config contract audit](CONFIG_COMMAND_API_BASELINES.md#common-configuration)
  and [exact target FML sources](https://maven.minecraftforge.net/net/minecraftforge/fmlcore/1.20.1-47.4.23/fmlcore-1.20.1-47.4.23-sources.jar).

## Reproduction and remaining gates

Build the four fixture/probe projects serially using the root wrapper with
`-p fixtures/<project> build --no-daemon --max-workers=1`, then run the root build
with `verifyMappingEnvironment`, `verifyLogicEnvironment` and
`verifyLifecycleEnvironment` enabled and the explicit local mapping profile paths.
Prepare/export the existing local-data bundle through the documented developer
workflow; no old game/Forge binaries or private data should be packaged.
`Validate-CachelessEngine.ps1` accepts `-ConversionProfile lifecycle-preserving-v1`,
`-BundleDirectory`, `-JavaHome`, `-FixtureJar` and a fresh `-EvidenceDirectory`.

For an already prepared, authorized local Forge baseline, run
`Prepare-LifecycleRuntime.ps1 -Variant Default -RunLabel final`, then
`Test-LifecycleRuntime.ps1 -Side Source -Variant Default -RunLabel final` and the
Target equivalent **serially**. Repeat with Configured, InvalidConfig and Inferred.
Use a new label when a directory already exists: scripts refuse overwriting prior
evidence. Preparation copies only existing local libraries and an already accepted
test EULA; it neither downloads nor newly accepts an EULA. Test worlds are fresh
and isolated; player worlds and original inputs are not altered.

Logs are not warning-free: the test-only probe lacks pack metadata, Forge's update
check failed, and Yggdrasil key-fetch failures were recorded. Empty development
caches do not prove an offline game launch; **offline gameplay was NOT_TESTED**.
No clean-log, client visuals, multiplayer, GUI conversion/install, live reload,
dispatcher recreation, enabled=false runtime branch, CLIENT/SERVER config scope,
config synchronization, nondefault priority/canceled-event delivery or arbitrary
queued-task semantics are certified here. Native unexpected failure handling can
differ across Forge versions. Static conversion reports correctly retain
`runtimeValidation=NOT_RUN`; the separate runtime receipts do not broaden the
admission contract or make compatibility universal.
