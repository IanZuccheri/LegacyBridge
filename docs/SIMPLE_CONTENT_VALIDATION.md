# Simple-content fixture validation — 2026-09-05

Historical record for runtime 0.1.0 and the unbound converted artifact. These
hashes/counts do not describe the current build; later runs also replace the
local `latest.log` files. See [0.1.1 compatibility-layer validation](COMPAT_LAYER_VALIDATION.md).

Scope: the original `legacybridge_example` 1.0.0 fixture, compiled against real
Forge 36.2.42 for Minecraft 1.16.5 and converted for Forge 47.4.23 / 1.20.1.
This is fixture evidence, not a compatibility certificate for other mods.

## Gates passed

| Gate | Observed result |
|---|---|
| Source fixture | Genuine ForgeGradle build, Java 8 classfile version 52, production SRG members; 4 artifact tests passed. |
| Converter | 214 tests passed, including actual local mapping/API inputs and conversion of the genuine fixture. |
| Runtime | 10 tests passed; player JAR audit excludes the validation harness and all runtime bytecode transformers. |
| Offline conversion | CLI exit 0, CONVERTED; source input unchanged and all 12 accepted resources retained byte-for-byte. |
| Repeat conversion | A separate CLI/JVM invocation produced an identical converted JAR after the final safety changes. |
| Dedicated Forge server | Native item, block and BlockItem registered; item stack 32, block hardness 2/resistance 6; two tags loaded; nine ingots craft one block and an incomplete grid does not match. |
| Forge client | Three baked models, two 16x16 atlas sprites, two language resources/live translations, sound event resolution to an existing audio resource and two populated vanilla creative tabs. |

Total: **228 JUnit tests, zero failures/errors/skips**, plus the two real game
probes. The standalone fixture has one plain item and one block; its BlockItem
is the second entry in the item registry, not a second standalone content item.

Server marker at 12:18:45 America/Sao_Paulo:

```text
LEGACYBRIDGE_SERVER_VALIDATION_OK modId=legacybridge_example version=1.0.0 itemStack=32 blockHardness=2 blockResistance=6 tags=2 recipe=9_to_1 negativeRecipe=passed
```

Client marker at 12:20:18 America/Sao_Paulo:

```text
LEGACYBRIDGE_CLIENT_VALIDATION_OK models=3 atlasSprites=2 languages=2 soundResource=resolved creativeTabs=2 modId=legacybridge_example
```

Both test instances stopped normally. With `bridgeValidation=true`, Gradle now
requires a fresh success marker and rejects a failure marker, because Forge can
exit with status 0 even when loading failed.

## Exact artifact chain

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Source fixture | 9534 | `00dd689f53f9631ecf4a8e84da142eb21051e2ca33f1c05403e4060e15b10947` |
| Converted fixture | 5767 | `26776823cf6a1bd675857a4b368b68d7e1bb6d914678a72596f5b21a9f10ea9a` |
| Packaged runtime | 29136 | `97b635d07d6278bcb41e0960bc3e4a85985fb0ebe38f6b3d83fb8848a8db92a0` |

Both staged `mods/legacybridge-example-1.20.1.jar` files match the converted
hash above. The produced runtime JAR was built/reobfuscated and audited; actual
game probes used ForgeGradle's runtime and test-harness source sets, with the
converted mod loaded from the real JAR. A separate production-launcher/profile
test of the packaged runtime has not been performed.

Local outputs (generated files, not committed):

- `fixtures/simple-content-1.16.5/build/libs/legacybridge-example-1.16.5-1.0.0.jar`
- `build/conversion/legacybridge-example-1.20.1.jar`
- `build/conversion/report.json` and `report-repeat.json`
- `legacy-runtime/build/libs/legacy-runtime-0.1.0-SNAPSHOT-forge-1.20.1.jar`
- `legacy-runtime/run-validation/server/logs/latest.log`
- `legacy-runtime/run-validation/client/logs/latest.log`

## Commands and environment

JDK 17.0.12, Gradle wrapper 8.8, ForgeGradle 6.0.54. Builds and launches were
sequential within this project, with `--no-daemon --no-parallel --max-workers=1`.
The Windows host's trusted root store was supplied to Gradle through scoped
`GRADLE_OPTS`; TLS validation was not disabled or changed globally.

```powershell
.\gradlew.bat -p fixtures/simple-content-1.16.5 clean build --no-daemon --no-parallel --max-workers=1
.\gradlew.bat build -PverifyMappingEnvironment=true --no-daemon --no-parallel --max-workers=1
.\legacy-converter\build\install\legacy-converter\bin\legacy-converter.bat convert fixtures/simple-content-1.16.5/build/libs/legacybridge-example-1.16.5-1.0.0.jar --output build/conversion/legacybridge-example-1.20.1.jar --report build/conversion/report.json
.\gradlew.bat build :legacy-runtime:runServer -PbridgeValidation=true -PverifyMappingEnvironment=true --no-daemon --no-parallel --max-workers=1
.\gradlew.bat :legacy-runtime:runClient -PbridgeValidation=true --no-daemon --no-parallel --max-workers=1
```

## Corrections and remaining boundaries

The initial harness dependency `[0.1.0,)` excluded `0.1.0-SNAPSHOT`; its range
was corrected before the successful server/client runs. This was a harness
metadata defect, not a guessed source API mapping.

The local development runs logged Yggdrasil public-key TLS/PKIX errors;
outbound network access was not blocked, and authentication/network isolation
are not part of this validation. The initial
flat-world preset used default settings and logged a missing `layers` field
before using the fallback. Vanilla sound/shader and first-run configuration
warnings also occurred. Neither probe reported missing fixture resources or
failed assertions. These logs are not presented as globally warning-free.

Custom creative tabs, local Ogg/file sounds, custom model geometry, animation,
unknown APIs/metadata/resources and everything outside the documented finite
grammar abort conversion without an output JAR. Accepted presets beyond the
fixture have unit coverage, not equivalent interactive gameplay evidence.
Manual visual inspection, interactive crafting, multiplayer, relog, existing
world migration and production-packaged runtime startup remain separate gates.
