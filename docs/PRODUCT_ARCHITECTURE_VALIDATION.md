# P1 product architecture validation

Date: 2026-09-05, America/Sao_Paulo. This record covers executable architecture
contracts and regression of the existing converter. It does **not** cover an
in-game conversion menu or a combined public release.

## Build and tests

The forced serial test/build command exited 0, `BUILD SUCCESSFUL in 3m 14s`:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
$env:DEBUG=''
$env:GRADLE_OPTS='-Djavax.net.ssl.trustStore=NONE -Djavax.net.ssl.trustStoreType=Windows-ROOT'
.\gradlew.bat :legacy-converter:test --rerun :legacy-runtime:test --rerun :legacy-workflow:test --rerun build -PverifyMappingEnvironment=true --no-daemon --no-parallel --max-workers=1 --console=plain
```

The truststore uses this host's existing trusted Windows roots for Gradle only;
TLS verification was not disabled. Other machines should use their own trusted
certificate configuration. Java 17, Gradle 8.8 and the pinned Forge baselines
were retained. No other project's running processes were altered.

Fresh JUnit XML totals:

| Module | Tests | Failures | Errors | JUnit skips |
|---|---:|---:|---:|---:|
| legacy-converter | 279 | 0 | 0 | 0 |
| legacy-runtime | 30 | 0 | 0 | 0 |
| legacy-workflow | 32 | 0 | 0 | 0 |
| Total | 341 | 0 | 0 | 0 |

The existing real-mapping and reobfuscated Forge 36 fixture integration tests
were enabled, not skipped. All 309 existing tests passed. The 32 new cases
cover workspace paths (9), workflow transitions (14) and restart decisions (9).
The workflow matrix tests all ordinary source/destination stage pairs.

`verifyWorkflowBoundary` passed: the internal JAR has no runtime dependency,
Forge mod metadata, nested JAR, bootstrap service or foreign class definition.
JDK 17 `jdeps -s legacy-workflow/build/libs/legacy-workflow-0.1.1-SNAPSHOT.jar`
reported only `java.base`. This is a dependency audit, not an implementation of
the future installer or proof against all malicious input.

## Explicit native-link test limitation

This Windows account could not create a symbolic link. The conditional test
reported the following marker and ran its documented file-collision fallback:

```text
LEGACYBRIDGE_WORKSPACE_SYMLINK_TEST_UNAVAILABLE FileSystemException
```

The XML consequently has zero JUnit skips, but **native symlink rejection was
not exercised on this host**. The fallback proves only collision rejection;
it is not equivalent coverage. Windows junction/alias behavior and concurrent
filesystem replacement also remain unvalidated by this run. The implementation
rejects unsafe existing reserved paths conservatively, but its read-only checks
are snapshots, not transaction-time race protection.

## Existing conversion and ABI regression

A separate installed-CLI conversion of the original fixture completed with
exit 0 and `CONVERTED` after the build:

```powershell
New-Item -ItemType Directory -Path build/conversion/product-p1
.\legacy-converter\build\install\legacy-converter\bin\legacy-converter.bat convert fixtures/simple-content-1.16.5/build/libs/legacybridge-example-1.16.5-1.0.0.jar --output build/conversion/product-p1/legacybridge-example-1.20.1.jar --report build/conversion/product-p1/report.json
```

Use a new output path when repeating this command: existing JARs are not
overwritten. The report preserves `simple-content-v1`, `legacybridge_example`,
version `1.0.0`, required runtime `legacybridge_runtime` / `0.1.1-SNAPSHOT`,
target Forge `47.4.23`, TransformationLog schema 1 and its 35 entries.
Static bytecode validation passed; `modClassesLoaded=false`,
`bytecodeExecuted=false` and `jvmLinkageVerified=false` remain truthful.

| Artifact | SHA-256 |
|---|---|
| Unchanged original Forge 36 fixture | `00dd689f53f9631ecf4a8e84da142eb21051e2ca33f1c05403e4060e15b10947` |
| Fresh `product-p1` converted fixture | `541cc64b44b97d0e4e79a193dd2ce3a86a5feba6e15714d0ee24ad63751492bb` |
| Unchanged runtime player JAR | `b03f9d0b888da7677f78d9063770cf401ad88b8c4b8a8a56752ed748adc9c428` |
| New internal workflow JAR | `ab8493d81e549be3811ada52ceb07b2c0bb316ca7fbcc8ecdcff0237452f9b60` |

The fresh output is byte-identical to the prior `asm-v1` converted fixture.
Original input and runtime hashes match their pre-change values. No runtime
entrypoint, adapter implementation/catalog, Forge metadata or conversion rule
was changed. The internal workflow artifact is **not** a player installation.

## What was and was not validated

Implemented: path layout, immutable protocol guards, restart-selection policy,
Gradle integration, dependency/archive boundary and documented acceptance plan.
The model prevents ordinary/direct activation and rejects same-launch activation
observations. A trusted future caller still has to authenticate actual discovery
and launch identity; constructing a UUID or provider-evidence record proves nothing.

Not implemented or validated here: title button/screen, worker service,
portable mapping bundle, combined public JAR, actual installation, durable crash
recovery, automatic restart provider, activation observation integration or
production-launcher end-to-end flow. No new Minecraft client/server run was
started for P1 because no game-loaded code/artifact changed. The earlier game
evidence is [separately recorded](ASM_PIPELINE_VALIDATION.md); it must not be
represented as testing the future UI or restart behavior.

The next stage is P2 in the [implementation plan](PRODUCT_IMPLEMENTATION_PLAN.md).
