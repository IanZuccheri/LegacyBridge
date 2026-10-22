# ASM pipeline validation - 2026-09-05

Scope: the original Forge 36.2.42 / Minecraft 1.16.5 fixture, transformed by the
explicit ASM pipeline for Forge 47.4.23 / Minecraft 1.20.1 and Java 17. The
runtime contract remains `0.1.1-SNAPSHOT`; no new runtime adapter category was
enabled. Earlier compatibility-layer artifacts are recorded separately.

## Results

- Root build: **309 JUnit tests** (279 converter + 30 runtime), zero failures,
  errors or skips with `verifyMappingEnvironment=true`.
- New coverage: 10 scanner, 23 remapping/descriptor policy, 14 bytecode/frame
  verification and five converter integration tests. Existing tests also pass.
- Installed CLI conversion: one source class, 144 external-reference occurrences,
  one validated target bootstrap, 10 adapter bindings and 35 journal entries.
- All 12 accepted source resources remain byte-identical; required metadata,
  plan/bindings and additive STONE mining-tag changes are explicitly journaled.
- Separate CLI/JVM repeat conversions, including after the final header guard,
  produced identical JAR bytes and deterministic transformation journals.
- Fresh Forge dedicated-server and client probes both passed and stopped normally.

The real journal uses 16 `CLASS_REMAP`, one `DESCRIPTOR_REMAP`, six `FIELD_REMAP`,
six `ADAPTER_CALL` and six `RESOURCE_REWRITE` entries. Counts are occurrences and
whole-body lowering operations, not unique symbols. `METHOD_REMAP` is covered by
synthetic remapping tests; the actual fixture does not need a method-name rename.

Server marker at **15:11:43 America/Sao_Paulo**:

```text
LEGACYBRIDGE_SERVER_VALIDATION_OK modId=legacybridge_example version=1.0.0 itemStack=32 blockHardness=2 blockResistance=6 instrument=BASEDRUM tags=3 ironPickaxe=legacySpeed recipe=9_to_1 negativeRecipe=passed
```

Client marker at **15:13:13 America/Sao_Paulo**:

```text
LEGACYBRIDGE_CLIENT_VALIDATION_OK models=3 atlasSprites=2 languages=2 soundResource=resolved creativeTabs=2 modId=legacybridge_example
```

Both Gradle tasks checked fresh success markers and absence of failure markers,
then returned `BUILD SUCCESSFUL`. The converted mod was loaded from its staged
JAR. Runtime and validation harness were ForgeGradle source sets, not a separate
production-launcher installation of the packaged runtime.

## Exact artifacts

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Original 1.16.5 fixture | 9534 | `00dd689f53f9631ecf4a8e84da142eb21051e2ca33f1c05403e4060e15b10947` |
| ASM-pipeline converted fixture | 6979 | `541cc64b44b97d0e4e79a193dd2ce3a86a5feba6e15714d0ee24ad63751492bb` |
| Unchanged 0.1.1 runtime player JAR | 59803 | `b03f9d0b888da7677f78d9063770cf401ad88b8c4b8a8a56752ed748adc9c428` |

Both staged test-mod JARs, the repeat output and the final-check output match the
converted hash. The older `compat-v1` artifact was preserved; the harness now
defaults to `asm-v1` so an ordinary validation run checks the new output.

Generated paths relative to the project root:

- `build/conversion/asm-v1/legacybridge-example-1.20.1.jar`
- `build/conversion/asm-v1/report.json` (includes `transformationLog`)
- `build/conversion/asm-v1/report-repeat.json` and `report-final-check.json`
- `legacy-runtime/run-validation/server/logs/latest.log`
- `legacy-runtime/run-validation/client/logs/latest.log`

## Commands

All builds/game runs were sequential within this project. JDK 17.0.12,
Gradle 8.8, ForgeGradle 6.0.54 and all five ASM artifacts at 9.9.1 were used.

```powershell
.\gradlew.bat build -PverifyMappingEnvironment=true --no-daemon --no-parallel --max-workers=1
New-Item -ItemType Directory -Force build/conversion/asm-v1
.\legacy-converter\build\install\legacy-converter\bin\legacy-converter.bat convert fixtures/simple-content-1.16.5/build/libs/legacybridge-example-1.16.5-1.0.0.jar --output build/conversion/asm-v1/legacybridge-example-1.20.1.jar --report build/conversion/asm-v1/report.json
.\gradlew.bat build :legacy-runtime:runServer -PbridgeValidation=true -PverifyMappingEnvironment=true --no-daemon --no-parallel --max-workers=1
.\gradlew.bat build :legacy-runtime:runClient -PbridgeValidation=true -PverifyMappingEnvironment=true --no-daemon --no-parallel --max-workers=1
```

Use a new output filename when repeating conversion: existing JARs are never
overwritten. Gradle used the Windows host trusted-root store through scoped
`GRADLE_OPTS`; TLS verification was not disabled or globally changed.

## Limits and observed warnings

External Forge version-check/Yggdrasil TLS/PKIX failures and development
resource/metadata warnings remain in game logs, alongside vanilla sound/shader
warnings. No fixture validation assertion failed. These are not warning-free
or authenticated multiplayer results.

The converter's CheckClassAdapter/dataflow/reference analysis does not execute
or define source classes. Its report correctly keeps `jvmLinkageVerified=false`:
offline ASM checks are not complete JVM verification/linkage. The actual game
probes are separate evidence for this exact fixture, not for arbitrary bytecode.
Original constructor initialization and target member access/linkage in general
are not certified by the reusable offline validator.

SourceFile/SMAP and the surviving constructor line/this-local are retained;
debug data for consumed bodies is explicitly lost. MISC-to-INGREDIENTS remains
the documented migration policy. Registered SoundEvent gameplay, manual visual
QA, multiplayer/relog, other mods and production-packaged launcher startup are
not validated by this record. See [pipeline contract](ASM_PIPELINE.md).
