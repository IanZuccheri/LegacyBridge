# P2 - reusable engine and cacheless conversion evidence

Validated on 2026-09-05, Windows, Java 17.0.12, Gradle 8.8. The pinned source
remains Minecraft 1.16.5 / Forge 36.2.42; target remains Minecraft 1.20.1 /
Forge 47.4.23. This milestone does not widen `simple-content-v1`.

## Build, tests and boundaries

Root `build -PverifyMappingEnvironment=true --no-daemon --no-parallel
--max-workers=1 --console=plain` passed after implementation. Builds and probes
ran serially. Neutral/runtime Java compilation and runtime/workflow tests were
also explicitly rerun during integration; subsequent checks reused unchanged
outputs where Gradle reported `UP-TO-DATE`.

| Module | Tests | Passed | Failed/errors | Skipped |
|---|---:|---:|---:|---:|
| `legacy-converter` | 347 | 346 | 0 | 1 |
| `legacy-runtime` | 30 | 30 | 0 | 0 |
| `legacy-workflow` | 32 | 32 | 0 | 0 |
| Total | 409 | 408 | 0 | 1 |

The neutral `legacy-compat-api` has no separate JUnit suite: its archive boundary
task and converter/runtime integration tests check its actual contracts. The
source fixture was not modified or rebuilt in P2; its earlier four tests are
not added to this milestone's count.

The one skipped test is native symlink creation in `PortableMappingBundleTest`:
this Windows account lacks that permission. Existing P1 workflow link tests
retain their documented collision fallback; that is not native symlink/junction
execution coverage. Platform filesystem-race hardening remains separate.

Coverage added in P2 includes full CLI/service analysis, mappings and conversion
JSON equality; byte-identical output; non-publishing plans and selected-source
fingerprints; queued/active cancellation and publication linearization; deadlines,
actual-byte budgets and temporary cleanup; one worker and bounded queue;
callback/close races; structured failures and bounded inventory; portable data
corruption/limits/provenance/no-overwrite checks; and canonical manifest ordering.

`verifyEngineConsumerBoundary` passed: the normal engine runtime variant excludes
the Forge runtime project, game implementations and `slf4j-simple`. The latter
belongs only to the standalone CLI distribution/test logging. The engine JAR has
no foreign class implementations, portable data payloads or ModLauncher bootstrap
service. The neutral and runtime archive audits passed; adapter bindings still
refer to concrete implementations under the same runtime identity/version.

## Actual isolated installation, not just synthetic tests

The installed CLI explicitly prepared the real pinned development data twice,
in separate JVMs, using `prepare-mappings --from-development-cache`. All seven
files were byte-identical across exports, including the canonical manifest.
The local snapshot is 35,223,698 bytes: six inert mapping/API indexes and one
manifest, with 11 original source provenance records. It has 6,868 legacy and
8,825 target API declarations. This is a local development artifact, not a
redistribution-approved release asset.

The final standalone probe used a fresh directory outside the repository:

```text
${DEVELOPER_HOME}/AppData/Local/Temp/legacybridge-p2-clean-1853fd30fa4b4eebbef034e596aeb72d/
```

Only copied installed CLI libraries, copied test-probe classes, the original
fixture and the explicit local bundle were available on its configured paths.
No original API JAR, Mojang TXT, MCP ZIP, Gradle installation/cache or game
implementation was included in that isolated installation.

Java was launched with `-Xmx512m`, empty redirected `user.home`,
`GRADLE_USER_HOME` and `java.io.tmpdir`, and a classpath containing only those
copied libraries/probe classes. A self-tested **test-only Java 17 SecurityManager**
denied networking/DNS, subprocess execution, process exit and external file
contents. Exceptions allowed JDK files and metadata-only checks of the exact
ancestor directories (`readAttributes`/`toRealPath`) needed to reject aliases.
It did not allow developer-cache or repository file reads.

The first harness attempt was too restrictive about ancestor canonical-path
metadata and failed before conversion. The harness restriction was corrected;
production engine access was not broadened. Java's deprecation warning for this
test guard is expected. It is not shipped, a native-code sandbox, or a proposal
to enable SecurityManager in Minecraft.

Final marker:

```text
CACHELESS_ENGINE_EQUIVALENCE_OK modId=legacybridge_example sha256=541cc64b44b97d0e4e79a193dd2ce3a86a5feba6e15714d0ee24ad63751492bb canonicalReports=3 originalUnchanged=true externalFileAttempts=0 networkAttempts=0
```

The probe independently checked:

- Missing data returns typed `MAPPING_DATA_REQUIRED` and CLI exit 3, no fallback.
- Inventory finds the single original without loading it; analysis JSON is equal.
- Mapping JSON is equal: 58 symbols, 116 observations, 36 `RESOLVED`, 15 `MANUAL`,
  7 `UNSUPPORTED`, zero `ADAPTER_REQUIRED`. CLI exit 1 is expected: direct-call
  mappings do not authorize the separately proven finite content-plan conversion.
- Planning proves the candidate without creating an output file.
- CLI/service conversion JSON and JAR bytes are identical; planned and committed
  hashes match; source remains unchanged. Conversion CLI exit is 0.
- ASM dataflow/offline target reference validation passes. Original mod classes
  are not loaded; JVM linkage/gameplay are not claimed by this check.
- No external file/network/process/exit attempts occur after guard self-tests.
  Redirected user/Gradle directories and owned temporary directory remain empty.

Six complete reports and `cacheless-engine-evidence.json` are retained locally at
`build/conversion/product-p2/cacheless-final-reports/`. The isolated installation
is intentionally retained for inspection, not leaked engine temporary work.

## Artifact fingerprints

| Artifact | SHA-256 |
|---|---|
| Original fixture, 9,534 bytes | `00dd689f53f9631ecf4a8e84da142eb21051e2ca33f1c05403e4060e15b10947` |
| Converted fixture, 6,979 bytes | `541cc64b44b97d0e4e79a193dd2ce3a86a5feba6e15714d0ee24ad63751492bb` |
| `legacy-converter-0.1.1-SNAPSHOT.jar` | `68682bc74706fdeaf1847bb865806a8c1016743f9c66d4fd55f583368e880ddb` |
| `legacy-compat-api-0.1.1-SNAPSHOT.jar` | `8b99e39730a8b8d6e04d6b821cdc2f3cec3225885b6ff4763bca0a9c817229a5` |
| `legacy-runtime-0.1.1-SNAPSHOT-forge-1.20.1.jar` | `b03f9d0b888da7677f78d9063770cf401ad88b8c4b8a8a56752ed748adc9c428` |
| `legacy-workflow-0.1.1-SNAPSHOT.jar` | `ab8493d81e549be3811ada52ceb07b2c0bb316ca7fbcc8ecdcff0237452f9b60` |
| Locally prepared canonical `manifest.json` | `bac4e0018cc51bd13a7e5ae19116d04cb6b4bc271d3aecfba5e4ac0d27df5c69` |

The runtime and converted fixture hashes match P1/ASM evidence exactly. P2 did
not launch a new game: earlier [client/server fixture results](ASM_PIPELINE_VALIDATION.md)
remain historical evidence for those identical bytes, not proof of a new UI,
public combined JAR, launcher installation, restart or other mod compatibility.

## Reproduce

After the documented sequential build and explicit local bundle preparation:

```powershell
.\scripts\Validate-CachelessEngine.ps1 `
  -JavaHome 'C:\Program Files\Java\jdk-17' `
  -BundleDirectory .\build\conversion\product-p2\mapping-data-canonical-a
```

The script creates a new isolated directory every time, never changes real
caches/profiles and restores its temporary environment settings. The probe is
compiled by `testClasses`, but is not part of any distributed application JAR.
Optional `-EvidenceDirectory` must name a new destination.

## Remaining gates

- Data import is explicit and local. Redistribution clearance is **not** granted;
  see the [source-based rights audit](PORTABLE_MAPPING_DATA.md). No hidden download
  or old game implementation supplies independence.
- Queue/payload/index/deadline limits are cooperative and not an exact shared-JVM
  heap ceiling. The 512 MiB probe result applies to this fixture, not arbitrary
  modpacks. Parser/OS latency and abrupt crash cleanup remain bounded only as
  documented in [execution controls](ENGINE_EXECUTION_CONTROLS.md).
- The future UI must own one service, dispatch updates onto its client thread,
  bound retained history and never block in completion callbacks. Actual Forge
  dependency isolation, title screen, staging controller, installation/recovery
  and restart are still P3-P6, not delivered by P2.
- The supported conversion grammar, runtime ABI, CompatibilityReport schema 2,
  conversion schema 2 and TransformationLog schema 1 are unchanged. Planning
  and mapping preparation have distinct schema-1 envelopes.
