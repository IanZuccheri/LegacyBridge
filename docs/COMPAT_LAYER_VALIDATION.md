# Compatibility layer validation - 2026-09-05

Earlier 0.1.1 record, before the explicit ASM pipeline. For the current emitted
artifact, tests and later game runs, see [ASM-pipeline validation](ASM_PIPELINE_VALIDATION.md).
The earlier hashes/counts below are retained as historical evidence; later runs
replace local `latest.log` files.

Validated contract: `0.1.1-SNAPSHOT`, adapter catalog/bindings schema 1,
conversion report schema 2. This is evidence for the finite original fixture,
not universal Forge compatibility or arbitrary legacy-call emulation.

## Passed gates

| Gate | Observed result |
|---|---|
| Root build/tests | 227 converter + 30 runtime JUnit tests; zero failures, errors or skips, with `verifyMappingEnvironment=true`. |
| Real API baselines | Pinned Forge 36.2.42 / Minecraft 1.16.5 and Forge 47.4.23 / Minecraft 1.20.1 declarations verified. |
| Packaged adapter audit | All 11 catalog entrypoints exist as concrete public static methods in the built/reobfuscated runtime; generated catalog matches the compiled contract. |
| Dependency boundary | Converter consumes only the JDK-only compatibility API artifact, not a game/Forge runtime classpath. |
| Refusal tests | Unknown/missing/extra/duplicate/mismatched bindings, unsupported materials/tabs/properties, vanilla-tag writes and unimplemented direct-call mappings reject. |
| Offline conversion | CLI exit 0; source identity/version retained, 10 exact adapter bindings, all 12 accepted source resources byte-identical, additive target mining tag generated. |
| Repeat conversion | Separate CLI/JVM invocation produced an identical converted JAR. |
| Dedicated Forge server | Native registrations, stack/strength, three tags, iron-pickaxe speed, BASEDRUM instrument, positive and negative crafting checks passed. |
| Forge client | Three baked models, two atlas sprites, two language resources/live translations, resolved sound resource and two populated native creative tabs passed. |

The fixture contains one plain item, one block and its BlockItem, and one recipe.
`itemCount=2` counts the BlockItem, not a second standalone item. Its source JAR
was already built against genuine Forge 36.2.42 with Java 8 classfile version 52
and production SRG members; the unchanged source hash is recorded below. The
four source-artifact tests from the earlier run are not added to the 257 current
root-project tests.

Server marker at **14:08:06 America/Sao_Paulo**:

```text
LEGACYBRIDGE_SERVER_VALIDATION_OK modId=legacybridge_example version=1.0.0 itemStack=32 blockHardness=2 blockResistance=6 instrument=BASEDRUM tags=3 ironPickaxe=legacySpeed recipe=9_to_1 negativeRecipe=passed
```

Client marker at **14:09:52 America/Sao_Paulo**:

```text
LEGACYBRIDGE_CLIENT_VALIDATION_OK models=3 atlasSprites=2 languages=2 soundResource=resolved creativeTabs=2 modId=legacybridge_example
```

Both runs exited normally with `BUILD SUCCESSFUL`; Gradle required fresh success
markers and no validation-failure marker. Runtime/test-harness source sets were
loaded by ForgeGradle; the converted fixture was loaded from the actual staged
JAR. This does not claim a separate production-launcher test of the runtime JAR.

## Artifact chain

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Original source fixture | 9534 | `00dd689f53f9631ecf4a8e84da142eb21051e2ca33f1c05403e4060e15b10947` |
| New converted fixture | 6894 | `5c55c25c30cf2048fcd5b52aaffdce40bd9fbd6027dd3a274521a91a5d7d2afe` |
| Runtime 0.1.1 player JAR | 59803 | `b03f9d0b888da7677f78d9063770cf401ad88b8c4b8a8a56752ed748adc9c428` |

The repeat conversion and both staged client/server fixture JARs match the new
converted hash. Previous 0.1.0 conversion artifacts were preserved separately.

Local generated artifacts:

- `build/conversion/compat-v1/legacybridge-example-1.20.1.jar`
- `build/conversion/compat-v1/report.json` and `report-repeat.json`
- `legacy-runtime/build/libs/legacy-runtime-0.1.1-SNAPSHOT-forge-1.20.1.jar`
- `legacy-runtime/run-validation/server/logs/latest.log`
- `legacy-runtime/run-validation/client/logs/latest.log`

## Commands and environment

JDK 17.0.12, Gradle 8.8, ForgeGradle 6.0.54, target Forge 47.4.23. Commands were
sequential within this project, using `--no-daemon --no-parallel --max-workers=1`.
Gradle used the host Windows trusted roots through scoped `GRADLE_OPTS`; no
global trust changes or disabled TLS verification were used.

```powershell
.\gradlew.bat build -PverifyMappingEnvironment=true --no-daemon --no-parallel --max-workers=1
.\legacy-converter\build\install\legacy-converter\bin\legacy-converter.bat convert fixtures/simple-content-1.16.5/build/libs/legacybridge-example-1.16.5-1.0.0.jar --output build/conversion/compat-v1/legacybridge-example-1.20.1.jar --report build/conversion/compat-v1/report.json
.\gradlew.bat build :legacy-runtime:runServer -PbridgeValidation=true -PverifyMappingEnvironment=true --no-daemon --no-parallel --max-workers=1
.\gradlew.bat :legacy-runtime:runClient -PbridgeValidation=true --no-daemon --no-parallel --max-workers=1
```

Create the output directory first; conversion intentionally refuses an existing
output JAR. Reconvert old unbound artifacts for the new runtime contract.

## Limitations and warnings actually observed

Local development runs logged external Forge update-check/Yggdrasil TLS/PKIX
failures, ForgeGradle resource/metadata warnings and vanilla sound/shader
warnings. The isolated server reused its test world and warned about the runtime
version change. Neither validation probe failed; these are not warning-free or
online-authentication test results. Gradle/plugin and target API deprecation
warnings remain on the pinned toolchain.

MISC to INGREDIENTS is a documented category migration, not identical legacy
tab appearance/order. Registered SoundEvent construction has synthetic-fixture
evidence only: the real client check resolves a sounds.json alias, not a playback
call or registered-event gameplay. There is no local Ogg/file sound support.
Adapter bindings check implementation consistency, not cryptographic authenticity.

Entity, networking, capability and custom rendering packages deliberately have
no adapters. Other unsupported behavior also aborts conversion. Manual visual
inspection, interactive crafting, multiplayer/relog, other mods' interactions,
existing-world migration and production-packaged runtime startup remain separate
gates. See [compatibility layer](COMPAT_LAYER.md) and [actual API evidence](COMPAT_API_BASELINES.md).
