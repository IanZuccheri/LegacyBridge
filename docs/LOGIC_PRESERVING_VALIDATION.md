# Preserved-logic validation — 2026-09-06

Status: **experimental, one verified vertical slice**. No new real mod compatibility
count, client visual certification, multiplayer certification or publication is
claimed. The current player UI/installer still uses `simple-content-v1`.

The source and converted fixture produced **identical observed trace bytes** in
two separate ordinary Forge dedicated-server installations. The source process
ran the original Forge36 JAR; the target ran the converted JAR with the final public
LegacyBridge JAR. This explicit comparative execution was separate from static
analysis/conversion. Neither server was a Gradle `runServer` classpath.

## Evidence levels

| Gate | Actual result |
|---|---|
| Root build/test | Passed; 655 tests, 653 passed, 2 existing Windows symlink-privilege skips, zero failures/errors |
| Separate original/probe builds | Three genuine Forge projects, four tests each; all 12 passed |
| Original archive analysis | Six owned Java 8 classes read without loading them |
| Conversion | Six classes and 25 methods retained, zero replacement/bootstrap classes |
| ASM/dataflow | Passed on original and exact converted classes; output frames/maxima recomputed offline |
| Source/target linkage and access | `PASSED_SCOPED`; not a complete JVM/module/classloader proof |
| CLI/service parity | Canonical analysis, mapping and conversion reports equal; planned/published JAR bytes equal |
| Cache independence | Passed outside repository with copied installed CLI libraries, local bundle, empty home/temp/Gradle directories |
| Source runtime | Forge 36.2.42 / Minecraft 1.16.5 / Temurin 8u492 x64; native item registration/behavior passed, clean save and exit 0 |
| Target runtime | Forge 47.4.23 / Minecraft 1.20.1 / Java 17.0.12 x64; current public JAR + converted fixture + test-only probe, passed, clean save and exit 0 |
| Controlled native behavior | Original/converted traces identical, including native `ItemStack.hasFoil()` dispatch |
| Old simple profile | Newly converted output byte-identical to the previous approved `simple-content-v1` result |
| Client glint/models/translations | Not run for this new profile; asset bytes are statically preserved, not visually certified |
| Multiplayer | Not run |
| Fully offline game | Not tested; the static cacheless engine test blocks network, but the server tests do not claim an air-gapped game |

The cacheless probe's deprecated Java 17 SecurityManager is **test-only**, not a
production sandbox. Its tripwires were tested first; during engine operations it
observed zero forbidden file reads, network/DNS, subprocess or exit attempts. JDK
installation reads and exact ancestor-directory metadata checks are explicit
exceptions. All three initially empty directories remained empty after completion.

The ordinary server worlds and libraries are under distinct owned
`build/logic-preserving-v1/source-server` and `target-server` directories. Source
installation was explicitly prepared with the official exact Forge installer,
normal TLS and checksum validation. The previously accepted isolated-test EULA was
reused. No existing P6 world, mods folder or launcher configuration was modified.
The source Java 8 executable is an explicitly selected installed development
toolchain, not a mapping-data lookup; the standalone cacheless proof uses Java 17.

## What was observed, not inferred from symbols

The native registry contained exactly the expected fixture item identity
`legacybridge_logic:parity_token`, with original subclass
`dev.legacybridge.fixture.logic.LogicItem` and stack limit 16. The single item
constructed one rule. Helper initialization ran once with seed 7.

For inputs `[-3,0,1,2,3,8,16,31,64]`, the actual score sequence was
`[-1002,17,22,27,32,33,49,88,145]` on both versions. This exercises the interface,
overrides, super call, loop, switch, exception path, static method reference and
primitive capture. Native foil checks on counts `[1,2,3,8,16,31,64]` returned
`[true,false,true,false,false,true,false]`. An enchanted stack returned true through
the native superclass path without incrementing the custom rule counter.

Both actual traces hash to
`bc9e6f7f8f2bd5c23ead0e34e09c79245078d0989cfa7b85a1c02d24256ffbaf`.
The [recorded observed trace](evidence/logic-preserving-v1/observed-trace.json)
contains no screenshot, mockup or substitute simulation. Its values came from the
two already-running native game instances; reflection in the test-only probes
was limited to exact public fixture-owned methods.

## Artifact identity

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Original logic fixture 1.0.0 | 8,301 | `49dfbd7c65918c16ce8c057df992f7e9b7c188a3fe5634adb42bb1bc5dbfc19f` |
| Converted logic fixture | 7,562 | `78de0e2fd62ed83315518a1980ae0738b354531736a5eea73195d332aecc186d` |
| Public LegacyBridge.jar | 1,077,285 | `f73d3b0606c56ca1686ddd2c216668a1c71ad6811b8f41e0929c712efa537211` |
| Old simple-profile regression output | 6,979 | `541cc64b44b97d0e4e79a193dd2ce3a86a5feba6e15714d0ee24ad63751492bb` |

[Machine-readable validation receipt](evidence/logic-preserving-v1/validation.json)
pins the fixture/probe/installer/bundle hashes and raw log hashes. Raw logs and
local-use mapping data remain in ignored build directories, not the public JAR.
The original fixture hash was rechecked after all operations.

Local detailed evidence:

- [Static conversion and TransformationLog](../build/logic-preserving-v1/cacheless-reports/convert-service.json).
- [CompatibilityReport](../build/logic-preserving-v1/cacheless-reports/analyze-service.json).
- [Cacheless CLI/service receipt](../build/logic-preserving-v1/cacheless-reports/cacheless-engine-evidence.json).
- [Source server log](../build/logic-preserving-v1/source-server/behavior-stdout.log) and [source runtime receipt](../build/logic-preserving-v1/source-server/behavior-receipt.json).
- [Target server log](../build/logic-preserving-v1/target-server/behavior-stdout.log) and [target runtime receipt](../build/logic-preserving-v1/target-server/behavior-receipt.json).
- [Full root build](../build/logic-preserving-v1/root-build-attempt-2.log).

## Negative checks and implementation issues actually found

The new 69 root tests are **tests**, not 69 supported mods. They cover exact rule
admission, unknown external calls/types/dependencies, changed access/static/final
contracts, inherited members, abstract requirements, protected receivers, constructor
initialization, malformed handles, unsupported bootstraps, annotations in invalid
locations, extra registration effects, unsupported superclasses, cancellation,
private planning, unchanged literals/debug/control flow and default-profile isolation.

The genuine JAR integration mutates a helper with an unknown external call and
adds a missing dependency in a separate case: both conversions abort without an
output JAR. The original simple profile refuses this behavioral fixture; cancelling
the new profile before publication also produces no artifact. Existing archive,
transaction, bytecode, UI and recovery regression tests passed separately.

Testing found and fixed two real mapping-data issues: a canonical class name can
legitimately collide with another MCP class name, and unrelated source API methods
may lack SRG rows. Namespace roles are now explicit, and normalization is limited
to the reviewed referenced API types plus their complete real ancestry. Missing
required rows still block; no guessing or confidence escalation was added.

An additional review against real Forge discovery sources found that `@Mod` on a
method/field could be collected as an entrypoint. Such annotations now abort,
including parameter/type-use variants; only the exact class target is admitted.
The final full build also verifies the installed CLI help exposes both profiles.

## Reproduction

Use the pinned versions already in the project, Java 17 and serial commands:

```text
gradlew -p fixtures/logic-preservation-1.16.5 build --no-daemon --max-workers=1
gradlew -p fixtures/logic-runtime-probe-1.16.5 build --no-daemon --max-workers=1
gradlew -p fixtures/logic-runtime-probe-1.20.1 build --no-daemon --max-workers=1
gradlew build -PverifyMappingEnvironment=true -PverifyLogicEnvironment=true -PlogicMappingProfile=<explicit-local-properties> --no-daemon --max-workers=1
legacy-converter prepare-mappings --mapping-profile <explicit-local-properties> --output <new-local-bundle>
```

Include actual eventbus 4.0.0/6.2.33 and the exact FML declaration JARs in the local
profile; see [API baselines](LOGIC_API_BASELINES.md). Use
`scripts/Validate-CachelessEngine.ps1` with the original logic `-FixtureJar`,
`-ConversionProfile logic-preserving-v1`, Java 17 and the explicit bundle.
This copies only test/CLI artifacts and data to a fresh temporary installation.

`Prepare-LogicRuntime.ps1 -DownloadOfficialSourceInstaller` explicitly prepares
fresh test directories and downloads the official source installer. Run that
installer's `--installServer .` separately, then `Test-LogicRuntime.ps1 -Side Source`
and `-Side Target` serially. The latter expects the approved converted JAR at
`build/logic-preserving-v1/converted.jar` and the current public artifact. Paths to
the installed test JDKs are machine-local and must be reviewed on another machine.
Runners refuse existing traces/logs/worlds; preserve earlier evidence before a new
run. They are not a launcher restart or player installation mechanism.

## Remaining gates

The profile remains the finite [documented slice](LOGIC_PRESERVING_PROFILE.md).
No entities, arbitrary item hooks, general events, custom rendering/networking,
worldgen, capabilities, unknown dependencies or changed-contract adapters are
enabled by these results. Module/classloader completeness and arbitrary program
equivalence are not proven by the scoped offline checker. Visual glint/assets,
client gameplay, multiplayer and the player UI/transactional installer for this
new profile remain unvalidated. Mapping redistribution/legal release gates remain
open. No files were submitted or published.
