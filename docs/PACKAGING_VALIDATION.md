# P3: single-artifact production validation

Validation date: 2026-09-05. Scope: Windows AMD64, Java 17.0.12, Minecraft
1.20.1, Forge **47.4.23**, unchanged `simple-content-v1`. This is a technical
candidate, **not a rights-cleared CurseForge release**. See
[packaging architecture](PACKAGING_ARCHITECTURE.md) and
[remaining licensing/data gates](PACKAGING_RIGHTS.md).

## Artifact and ownership

The only required LegacyBridge mod artifact is
`legacy-product/build/libs/LegacyBridge.jar`. Runtime identity stays
`legacybridge_runtime`, version/adapter ABI `0.1.1-SNAPSHOT`. Do not install the
development runtime JAR beside it. Converted mods remain separate mods; the
single-artifact requirement does not mean placing their code inside LegacyBridge.

The public archive contains the runtime, shared compatibility definitions,
typed engine, workflow/controller, physical-client UI, original report schemas
and adapter/capability catalogs. It contains one unmodified SRG Utils 0.6.2 JiJ
dependency and its license/published library sources. It contains no CLI,
fixture, harness, game source, original game/Forge binary, `.lbmap` or `.lbapi`.
The published library source ZIP is inert legal material, not game source.

`verifyProductJar` checks the **reobfuscated final JAR**, required metadata,
runtime ABI/catalog equality, exact Forge restriction, duplicate entries,
project-only outer class ownership, forbidden services/module descriptors and
platform packages, signatures, nested-library allowlist/range and the known
binary/source SHA-256 values. No ASM, NightConfig, SLF4J provider or JSON library
is shaded. The exact host supplies ASM 9.9.1, NightConfig 3.6.4 and logging;
SRG Utils is negotiated globally by JarJar, not isolated in a private loader.

## Serial build and test

The full rerun passed in **1m 29s**, all **54 tasks executed** (no reused test
outcomes). Evidence: `build/p3-production/final-build.log` and the four modules'
JUnit XML reports. Total: **426 tests, 425 passed, 1 skipped, 0 failed/errors**.

| Module | Tests | Passed | Skipped |
| --- | ---: | ---: | ---: |
| legacy-converter | 347 | 346 | 1 |
| legacy-runtime | 30 | 30 | 0 |
| legacy-workflow | 32 | 32 | 0 |
| legacy-product | 17 | 17 | 0 |

The skipped converter symlink test requires Windows link-creation permission.
It is not counted as an exercised native-symlink case. All newly added
controller tests ran. Build, bytecode validity, production startup and fixture
behavior remain distinct evidence below.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:DEBUG = ''
$env:GRADLE_OPTS = '-Djavax.net.ssl.trustStore=NONE -Djavax.net.ssl.trustStoreType=Windows-ROOT'
.\gradlew.bat build :legacy-product:validationJar --rerun-tasks `
  -PverifyMappingEnvironment=true --no-daemon --no-parallel --max-workers=1 --console=plain
```

The scoped Windows root store retains TLS certificate verification. Existing
CLI/service-equivalence tests run in `legacy-converter`; CLI launchers and
development distributions remain unchanged. The product UI never calls CLI
entry points, interprets stdout or exits the process.

New controller tests cover input-only inventory, inert original initializers,
explicit/invalid/missing data, duplicate identities, changed input fingerprints,
unique report paths, workspace collisions, bounded admission, cancellation,
nonblocking close, absolute instance paths and clearing stale results on a
different selection. Cancellation availability is a UI hint; the service's
publication barrier remains authoritative in a race.

## Ordinary installations, not ForgeGradle userdev

Both instances live beneath `build/p3-production`; existing player profiles,
accounts, worlds and mods were not changed. The official installer was verified
against the published SHA-1 `ed31ce02ac69176f34353235cb2508d5a0f1e088` before use.

The server was installed with `--installServer`. An initial server download
timed out; the same official vanilla server JAR was explicitly downloaded,
SHA-1 verified as `84194a2f286ef7c14ed7ce0090dba59902951553`, and supplied to the
installer's exact expected path. The successful install generated ordinary
`libraries/net/minecraftforge/forge/1.20.1-47.4.23/win_args.txt`; production
launches use `forgeserver`, not `forgeserveruserdev`. The test server binds
127.0.0.1:25595, uses the existing accepted development-test EULA, and is shut
down normally after checks. The server used `online-mode=false` and loopback
binding for this isolated test; outbound network access was not blocked.
This is not an authenticated multiplayer test.

The client was installed using `--installClient` into a separately owned empty
launcher profile. [Client preparation/launch scripts](PRODUCTION_CLIENT_LAUNCH.md)
verify normal manifests, libraries and 3,575 unique asset objects. Preparation
copied 3,496 files, explicitly downloaded 143 and reused 32 verified files.
No real account tokens or launcher profiles were read. Launch uses the installed
Forge JSON, `forgeclient`, dummy local-validation identity and a 1280x720 window.
There is no developer source set or Gradle cache on either production classpath.

The test-only `legacybridge-product-probe-*-validation-only.jar` is a separate
QA tool requiring an explicit JVM flag. It is never included in the public JAR
or required for a player. It terminates only its own isolated game instance.
On reruns, preserve previous reports and choose fresh outputs: the probe refuses
an existing report directory rather than overwrite it. One development rerun
hit this safeguard; its log was kept and earlier reports were renamed within
the owned report area before retrying.

## Static conversion and runtime evidence are different

The original fixture belongs only in `legacybridge/input`, never `mods`:

```text
legacybridge-example-1.16.5-1.0.0.jar SHA-256
00dd689f53f9631ecf4a8e84da142eb21051e2ca33f1c05403e4060e15b10947

validated converted fixture SHA-256
541cc64b44b97d0e4e79a193dd2ce3a86a5feba6e15714d0ee24ad63751492bb
```

The private mapping bundle was explicitly copied to each instance's
`legacybridge/cache/mapping-data`. It is **not** in the public artifact. Each
probe uses an empty instance-owned `user.home` without a `.gradle` cache.
The bundle manifest SHA-256 is
`bac4e0018cc51bd13a7e5ae19116d04cb6b4bc271d3aecfba5e4ac0d27df5c69`.

The service probe checks analysis, mappings, non-publishing plan and conversion
using the actual packaged engine. It asserts exact source/result hashes,
unchanged loaded-mod set and an explicit `MAPPING_DATA_REQUIRED` failure with
no output JAR when data is omitted. The positive result must match the existing
CLI/service baseline byte for byte. This is static conversion proof, not mod
activation. No original classloader or original code execution is used.

Separately, the prevalidated converted fixture is loaded on startup. Server
checks verify native item/block/BlockItem registrations, stack size, hardness,
resistance, STONE instrument/mining behavior, tags, nine-ingot crafting and a
negative incomplete recipe. Client checks verify three models, two texture
atlas sprites, translation and the simple sound event. Those are actual game
assertions for this fixture, not universal gameplay coverage.

The client QA also uses real buttons: title button -> `ConversionScreen` ->
inventory -> Analyze -> Convert with an explicitly entered local bundle. It
checks report/output files, render frames and client ticks during processing,
then runs the packaged service proof. Successful conversion only stages a JAR;
the currently loaded mod set remains unchanged.

## Final artifact-bound results

The final public artifact is **800,910 bytes**, SHA-256:

```text
89692f4e1fb792922474c163de3704a37702302452a54b2840aef1bf1cd1f4e9
```

The final hash passed the ordinary dedicated-server run at **20:32:44-20:32:48
America/Sao_Paulo**. The server reached `Done`, then emitted
`LEGACYBRIDGE_PRODUCT_SERVER_CONTENT_OK`, `LEGACYBRIDGE_PRODUCT_PACKAGING_OK`
and `LEGACYBRIDGE_PRODUCT_SERVER_OK`, including `missingDataRefusal=passed`.
It shut down normally with all dimensions saved (process exit 0).

Evidence: `build/p3-production/server-release.log`, `server-release-debug.log`,
`server-release-console.log`, and `server/server-classloading.log`. The class
load trace contains no `dev.legacybridge.product.client` class, no source from
`legacybridge/input` and no `.gradle` path. Actual module assertions found
runtime/engine/controller in the same `legacybridge_runtime` module,
SRG Utils in `net.minecraftforge.srgutils`, platform ASM 9.9.1/NightConfig 3.6.4
and `org.apache.logging.slf4j.Log4jLogger` as the host logger.

The **same final hash** passed the ordinary client run at **20:35:15-20:35:25**:
native onboarding Continue, exactly one title button, real conversion screen,
GUI analysis and GUI conversion, then the packaged-service proof all passed.
`LEGACYBRIDGE_PRODUCT_CLIENT_OK` records **54 client ticks / 156 rendered frames**
in the conversion phase, exact output parity, unchanged loaded mods and
`missingDataRefusal=passed`. Asset checks emitted
`LEGACYBRIDGE_PRODUCT_CLIENT_ASSETS_OK models=3 sprites=2 language=resolved soundEvent=resolved`.
The client then stopped normally; its owned process exited. No original-input
source or `.gradle` path occurs in the client class-load trace.

Evidence: `build/p3-production/client-release.log`, `client-release-debug.log`,
`client/client-classloading.log`, reports under `client/legacybridge/reports`
and staged GUI/service JARs under `client/legacybridge/staging`. The following
native framebuffer screenshots were actually opened and visually inspected at
1280x720; they are not generated illustrations:

- [Title button](../build/p3-production/client/screenshots/legacybridge-product-title-5156ad73-f8f9-4ca7-9547-28217faee42c.png).
- [Completed staged conversion](../build/p3-production/client/screenshots/legacybridge-product-conversion-complete-0c8b1148-b9b6-4286-a054-3b8ebd4a79b4.png).

Labels/buttons and results were legible in this tested layout. Long paths use
the scrollable details panel and Copy paths; this does not certify every GUI
scale, language, resolution or accessibility interaction.

Intermediate successful
server runs used candidate SHA-256
`a10655691aff71e3b055a476ee6e3e1f0a75c0667ac717440b93fff0f237ed8a`.
Those runs included a public-JAR-only server launch and then content/service
checks. The subsequent UI-state correction is included in the final hash;
its dedicated-server proof above is a new run, not an inference from the old one.

The first final-hash client attempt reached the native first-run accessibility
onboarding screen. The initial harness waited for `TitleScreen` and did not
handle onboarding, so that attempt was not credited as UI/conversion success.
Only that owned client was closed normally; the harness was corrected to use
the native verified onboarding button, without changing the public artifact.

Observed nonfatal platform diagnostics include fresh Forge configuration
defaults, missing `mods.toml` on Forge language-provider library files,
vanilla union-resource URL warnings, vanilla goat-sound/sampler warnings and a
Realms authorization refusal for the intentionally dummy access token `0`.
That authorization attempt is not evidence of network isolation. The successful
final client/server logs contain no ERROR or probe FAILED marker.
These logs are retained; no exception was suppressed to obtain a passing probe.

## Limits that remain

- Only the existing finite `simple-content-v1` grammar is supported. No entity,
  networking, capability, custom GUI/rendering, Mixin, coremod or worldgen support
  was added. A successfully analyzed JAR is not automatically convertible.
- No install/apply/restart or hot loading exists. Results stay in staging. The
  public JAR does not discover legacy files already rejected from `mods` early
  by Forge; originals must be moved to `legacybridge/input` before launch.
- One candidate per action, bounded inventory and cooperative limits; no hard
  JVM heap isolation or hostile-filesystem sandbox. No modpack-wide JiJ/version
  conflict guarantee, alternate Forge patch or loader validation is implied.
- English/Portuguese UI labels are implemented; engine diagnostic text remains
  English. Arbitrary resolutions, accessibility/narration, manual interaction
  matrix, multiplayer/relog and long-running resource stress remain separate QA.
- Legal reconciliation/corresponding-source/replacement obligations and an
  approved public mapping-data acquisition/distribution path remain release
  gates. One required mod JAR does **not** mean all mapping data is ready after
  one download. Missing local data is explicit; no hidden download occurs.
