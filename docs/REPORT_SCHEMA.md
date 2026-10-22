# Compatibility report schema

## Contract

`legacy-converter analyze` emits an immutable schema-v2
`CompatibilityReport`. The formal JSON Schema Draft 2020-12 resource is
packaged at:

```text
META-INF/legacybridge/compatibility-report-v2.schema.json
```

Schema v1 remains packaged as a historical artifact. New analyzer output uses
only version `2`. The report is static evidence, not a certificate that a mod
can be converted or run.

Report versions are independent: analyzer `CompatibilityReport` remains schema
2, `LEGACY_MAPPINGS` remains schema 1, and `LEGACY_CONVERSION` is now schema 2
for the `0.1.1-SNAPSHOT` compatibility layer. The formal schema named above is
the analyzer schema, not a schema for conversion or adapter bindings.

## Required analyzer fields

The requested fields occur directly at the top level:

```text
modId
modVersion
detectedMinecraftVersion
detectedForgeVersion
classCount
minecraftReferences
forgeReferences
thirdPartyDependencies
mixins
accessTransformers
coremods
unsupportedFeatures
riskScore
complexity
```

`modId` and `modVersion` come from the lexicographically first validated
`[[mods]]` declaration. For a multi-mod JAR the complete declaration list is
still available under `metadata.mods`; choosing one primary value does not
collapse the others. The exact standard `${file.jarVersion}` placeholder may
be resolved from allowlisted `Implementation-Version`; arbitrary substitutions
are not evaluated.

`detectedMinecraftVersion` is `1.16.5` only when a declared Minecraft numeric
range accepts that baseline. `detectedForgeVersion` is `36.x` only when the
JavaFML loader range or a declared Forge numeric range intersects Forge major
36. Neither value proves the exact Forge build used. Ambiguous input is
`UNKNOWN`.

`minecraftReferences` and `forgeReferences` are deterministic, bounded samples
of ASM references. Minecraft includes both `net/minecraft/` and `com/mojang/`.
Companion occurrence counts are exact for their owner prefix. Sampling attempts
stop after 4,096 matching observations per platform family, while at most 256
distinct references are retained. `*ReferencesTruncated` states whether
details were omitted or a sampling budget was reached.
`*ReferenceExternalityComplete` becomes false if the input defines a class in
the corresponding platform namespace, because the prefix total then is an
upper bound for external occurrences.

`thirdPartyDependencies` distinguishes exact `DECLARED_MOD` facts,
`MANIFEST_CLASS_PATH` and `MANIFEST_CONTAINED_DEPS` declarations, and
`INFERRED_PACKAGE` evidence. Manifest values are retained as inert strings;
they are never resolved or opened. An inferred Java package is never presented
as a Maven coordinate or Forge mod ID. Minecraft, Forge, the JDK, ModLauncher,
Mixin, and classes defined inside the analyzed JAR are excluded from that
inference.

## Per-class references

`classReferences` reports exact whole-archive occurrence totals by kind:

```text
SUPERCLASS
INTERFACE
FIELD
METHOD
TYPE
```

Each retained readable class has:

```text
entryName
className
referenceOccurrenceCount
referencesTruncated
superclass
interfaces[]
methods[]
fields[]
types[]
```

Method and field objects preserve `owner`, raw `method` or `field`, and JVM
`descriptor`. Hierarchy references preserve their owner through `superclass`
and `interfaces`. Internal owners are removed only after all readable class
definitions have been indexed; the analyzer never calls `Class.forName`.

At most 256 distinct references are retained for a class and at most 256 class
details are emitted. Ordinary-reference sampling stops after 4,096 attempts
per class/archive accumulator; hierarchy edges bypass that attempt budget.
Exact counts remain available, and
`referencesTruncated`, `classDetailsTruncated`, `definitionIndexComplete`, and
`externalSamplesComplete` expose whether details are exhaustive. Unreadable
classes cannot supply reliable member details and make analysis partial.

## Loader configuration and content

`mixins`, `accessTransformers`, and `coremods` contain inert, bounded artifact
facts: path, parse/partial state, declaration count, attributes/samples,
diagnostics, and truncation. Coremod JavaScript paths, `FMLCorePlugin`,
`TweakClass`, and `ForceLoadAsMod: true` are recorded but never followed,
loaded, or executed. Only the canonical Forge paths
`META-INF/accesstransformer.cfg`, `META-INF/coremods.json`, and
`META-INF/services/cpw.mods.modlauncher.api.ITransformationService` activate
their file-based feature markers. Manifest `MixinConfigs`, `FMLCorePlugin`,
`Class-Path`, `TweakClass`, and true `ForceLoadAsMod` values activate their
respective static markers. Case variants are suspicious content, not active
Forge declarations.

A manifest-declared non-conventional Mixin config can be captured in a bounded
second pass. Missing/malformed/partial configs and more than 256 distinct
manifest `MixinConfigs` paths make the artifact and analysis partial; excess
paths are not followed.

`manifest` exposes presence/parse state, allowlisted main attributes, and value
truncation. `content` inventories assets and datapack entry paths, namespaces,
and categories including recipes, loot tables, worldgen, dimensions, tags,
structures, and Forge loot modifiers. Content JSON/NBT is not semantically
validated in this milestone.

## Unsupported features and risk

Every detected feature family lacking a reviewed transformation/runtime
capability is listed in `unsupportedFeatures` with an exact occurrence count,
up to 16 sorted evidence strings, and explicit truncation. One evidence string
is capped at 4,096 characters and evidence sampling stops after 256 attempts
per family without stopping occurrence counts. Current families include
registries, events, networking, capabilities, custom entities, TileEntity,
client rendering, GUI, world generation, dimensions, recipes, loot,
reflection, Mixins, access transformers, coremods, transformation services,
native code, nested/signed/multi-release JARs, service providers, unresolved
manifest classpaths, unreadable/preview/too-new bytecode, metadata uncertainty,
unsupported loaders, and legacy bootstrap hooks.

The score is a deterministic migration-effort heuristic capped at 100. Feature
weights are applied once per family; declared third-party dependencies and
class-count scope add bounded weights. Classification is:

| Score | Complexity |
|---:|---|
| 0-14 | `SIMPLE` |
| 15-34 | `MODERATE` |
| 35-59 | `COMPLEX` |
| 60-100 | `EXTREME` |

Access-transformer, nested/signed/multi-release JAR, external-classpath, and
metadata-uncertainty evidence sets a minimum of `MODERATE`. Mixin and
unsupported-loader evidence sets a minimum of `COMPLEX`. Coremod, ModLauncher
transformation-service, native-code, or legacy-bootstrap evidence sets a
minimum of `EXTREME`. Finding severity is separate from this migration-effort
classification. The score is not a success probability, security verdict, or
compatibility claim. Correlated categories may intentionally accumulate
because each adds independent migration review work.

## Preserved foundation sections

Schema v2 retains the prior structured sections:

- `tool`: analyzer version and policy version;
- `input`: basename, size, and SHA-256 (never an absolute source path);
- `archive`: bounded ZIP inventory, exact `specialEntryCounts` by recognized
  category, and bounded risky-entry samples;
- `metadata`: complete modeled `mods.toml` declarations and dependencies;
- `bytecode`: class versions, unreadable/native counts, and namespaces;
- `markers`: high-signal feature presence with an occurrence count, up to 16
  sorted evidence strings, and explicit evidence truncation;
- `findings`: stable code, severity, category, message, and evidence;
- `assessment`: analysis/source/conversion/execution states.

`conversionStatus` remains exactly `NOT_IMPLEMENTED`, and `executionStatus`
remains exactly `NOT_TESTED` in analyzer output. They describe inspection only,
not the implementation status of the separate `convert` command.

## Separate conversion and adapter contracts

The ASM pipeline additionally emits `pipeline`, `transformationsCommitted`,
`bytecodeValidation` on success and `transformationLog` on success or refusal.
The latter has schema 1, ordered `sequence`, `kind`, `location`, `before`, `after`,
`rule`, `confidence`, `reason` and exact `adapter` references. Resource values
use SHA-256/byte-count fingerprints; untouched resources have no change entry.
Logs from aborted attempts describe tentative work only, never committed output.
See [ASM pipeline](ASM_PIPELINE.md) for all six change kinds and verifier limits.

`convert` emits `reportType=LEGACY_CONVERSION`, `schemaVersion=2`. Common fields
include `supportProfile`, input/source hashes, target Minecraft/Forge versions,
`runtimeRequired`, exact `runtimeVersion`, and `bytecodeExecuted=false`.
Identity fields are included once source metadata has been validated.

Successful conversion has `status=CONVERTED`,
`compatibilityStatus=SUPPORTED_FIXTURE_PROFILE`, `bytecodeModified=true`, output
fingerprint, content/replaced-class counts and `preservedResources` hashes.
`transformations` is a structured list, replacing the old schema-1 text list.
Each operation carries `adapter` (`id`, `revision`, `implementationClass`,
`method`, `descriptor`), `phase`, `operation`, `legacyApi`, `targetApi`,
`behaviorDifferences`, `knownLimitations` and `fixtures`.

`adapterBindings` has its own schema version 1, exact runtime version
`0.1.1-SNAPSHOT`, and the complete set of adapter references needed by the plan.
The same data is stored in `META-INF/legacybridge/<modId>/adapters.json` in the
converted JAR. Runtime validation compares all references to the compiled
catalog before registration; names in this data are never dynamically loaded.
Old output lacking these bindings must be reconverted.

A subset refusal has `status=ABORTED`, `compatibilityStatus=UNSUPPORTED`,
`bytecodeModified=false`, `output=null`, an empty `transformations` list and
precise diagnostics. No output JAR is produced. I/O/security failures remain
separate CLI failure paths and need not produce a conversion report.

Preserved source resource bytes and newly generated target resources are
distinct: the STONE mining-tag adapter adds `minecraft:mineable/pickaxe`; it
does not claim that this new file existed unchanged in the source. All source
vanilla tag declarations are currently unsupported.

The implementation catalog is independently packaged at
`META-INF/legacybridge/adapters.json` in the runtime player JAR. Its entries
contain exact references plus the five documentation fields `legacyApi`,
`targetApi`, `transformation`, `behaviorDifferences`, `knownLimitations`, along
with phase and fixture motivation. See [Compatibility layer](COMPAT_LAYER.md).
Neither conversion status nor an adapter binding proves fresh game execution.
Artifact-bound execution evidence is recorded separately in
[Compatibility-layer validation](COMPAT_LAYER_VALIDATION.md).

## Canonical serialization

P2 adds a separate non-publishing planning envelope:
`reportType=LEGACY_CONVERSION_PLAN`, `schemaVersion=1`, `status=PLANNED|ABORTED`.
`output` is always null, `bytecodeModified=false` and
`transformationsCommitted=false`; accepted plans have a `candidate` filename/hash.
Tentative changes retain TransformationLog schema 1. This is not a new status in
the existing conversion schema. Conversion revalidates the request and returns
the unchanged `LEGACY_CONVERSION` schema 2, with no partial JAR on refusal.

Explicit mapping preparation emits `LEGACY_MAPPING_PREPARATION`, schema 1,
`status=PREPARED`, a user-selected `bundleDirectory`, and
`distributionPolicy=LOCAL_USE_ONLY_NOT_APPROVED_FOR_REDISTRIBUTION`. This setup
receipt intentionally contains the selected absolute destination; unlike a
compatibility report it is not an environment-free compatibility evidence tree.
The bundle manifest/payload schemas are documented in
[portable mapping data](PORTABLE_MAPPING_DATA.md).

The asynchronous API uses typed `EngineOutcome<T>` and `EngineError` instead of
a console protocol. `COMPLETED`, `BLOCKED`, `FAILED` and `CANCELLED` describe job
completion, never mod activation. A blocked plan/conversion retains its refusal
report; failed/cancelled jobs have an error but no success value.

`CanonicalReportWriter` uses UTF-8, two-space indentation, LF endings, a final
newline, fixed property order, sorted immutable collections, and escaped JSON
control characters. It includes no timestamp, hostname, user, process ID,
working directory, or absolute input path.

The short CLI form writes JSON only to stdout:

```text
legacy-converter analyze path/to/mod.jar
```

Logs and diagnostics use stderr. `--report <path>` writes through a temporary
file in the resolved target directory and does not replace an existing file
without `--overwrite`.

## Evolution rule

Any incompatible field/type/enum/meaning change requires a new schema version.
Transformation or execution states may be added only with corresponding
implementation and validation; analysis success must never silently imply
runnability.
