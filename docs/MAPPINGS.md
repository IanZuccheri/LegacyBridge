# Legacy symbol mappings

2026-09-12: [data review](PROMPT_02_HEADLESS.md) adds `inspect-data` and optional
`--mapping-sha256` for `mappings`/`convert` with an explicit bundle. The same
typed service supplies scope/hash/provenance/counts to the client. A successful
data review is not semantic approval; full indexes may still lack a required
optional library. Individual composition retains the explicit full-data route.

2026-09-12: `inspect-cohort` / `plan-cohort` may omit `--mapping-bundle` for the
[exact authored pair's included contract](FINITE_COHORT_DATA.md). This is one
Forge declaration and **no general name-mapping tables**; the broad `mappings`
command and other advanced operations described below still require full data.

The `mappings` command resolves the symbols referenced by a JAR against local
Minecraft 1.16.5 / Forge 36.2.42 and Minecraft 1.20.1 / Forge 47.4.23 development
artifacts. Resolution is static. It does not load mod or Minecraft classes,
modify bytecode, create a converted JAR, or enable a runtime adapter.

```text
legacy-converter mappings mod.jar --mapping-bundle local-data/bundle-v1
legacy-converter mappings mod.jar --mapping-bundle local-data/bundle-v1 --report mappings.json
legacy-converter mappings mod.jar --mapping-profile mappings.properties --report mappings.json
```

The default output is canonical JSON on stdout; logs and errors use stderr.
`--report -` explicitly selects stdout. An existing report requires
`--overwrite`, which cannot be combined with stdout. The input JAR cannot be
used as the output report. Mapping profiles are accepted by `mappings` and the
separate strict `convert` command, not by `analyze`.

Broad mapping inspection and the five development conversion profiles require
an explicit bundle/profile or `LEGACYBRIDGE_MAPPING_BUNDLE`. Without it, they
fail with `MAPPING_DATA_REQUIRED`, never searching the developer's cache.
Default `simple-content-v1` conversion now uses its [included finite source
contract](EMBEDDED_SIMPLE_CONTENT_DATA.md), not a full environment that can be
used by this generic inspection command. Explicit invalid data never falls back.
[Portable mapping data](PORTABLE_MAPPING_DATA.md)
documents versioned indexes, hashes, provenance, bounds and the local preparation
workflow. No mapping bundle is approved for public redistribution in this stage.

[Simple-content conversion](SIMPLE_CONTENT.md) is a distinct semantic adapter
path. It proves an entire finite registration grammar and lowers it to a
declarative plan; it does not use `RESOLVED` mappings as rewrite authorization.
Its [implemented compatibility catalog](COMPAT_LAYER.md) binds the whole-plan
transformation to exact adapters. A plan adapter is not an equivalent shim for
an arbitrary call to the corresponding legacy API.

## Symbols, rules, and confidence

`LegacySymbol` and `TargetSymbol` identify a `CLASS`, `METHOD`, `FIELD`,
`CONSTRUCTOR`, or `DESCRIPTOR` using JVM internal owners, member names, and
descriptors. Constructors retain `<init>` and an exact method descriptor.
`MappingRule` pairs symbols with category, confidence, status, and bounded
evidence. `MappingDatabase` provides immutable exact-key lookup, conservative
conflict handling, and all-or-nothing descriptor composition.

The four report statuses are separate from the kind of change:

| Status | Meaning |
|---|---|
| `RESOLVED` | A structural target symbol was found with sufficient naming and declaration evidence. This is not semantic compatibility. |
| `ADAPTER_REQUIRED` | Retained in the report/model contract for an explicitly reviewed adaptation rule. The current generator does not emit it merely because adaptation would be needed. |
| `MANUAL` | Inputs, aliases, declarations, or equivalent targets are missing, ambiguous, or insufficiently verified. An explicitly marked heuristic class candidate may be included. |
| `UNSUPPORTED` | No known equivalent target or implemented semantically equivalent direct-call adapter exists. Human porting may remain possible; a whole-plan fixture adapter does not change this result. |

The seven categories are `IDENTITY`, `PURE_RENAME`, `PACKAGE_MOVE`,
`DESCRIPTOR_CHANGE`, `REMOVED_API`, `ADAPTER_REQUIRED`, and
`NO_AUTOMATIC_EQUIVALENT`. A rule records one primary category; its evidence
explains overlapping changes. A package move can also change all owners inside
a method descriptor. Changed argument/return contracts require adaptation;
descriptor string replacement alone does not select a new overload.

`MappingConfidence` distinguishes `VERIFIED`, `HIGH`, `HEURISTIC`, and `NONE`.
Exact official names corroborated by both API inventories produce `HIGH`.
`VERIFIED` is reserved for independent semantic validation of an explicit rule;
the generator does not promote name/signature matches to that level.
`automaticRewriteAllowed` is a policy decision exposed in the report, not a
bytecode writer. Generated mappings do not authorize call rewriting. Conflicts
and explicit blocking rules cannot be overridden by a weaker inferred match.

The [ASM pipeline](ASM_PIPELINE.md) adds a separate verified own-entrypoint rule
only after the finite registration graph is proven compatible with its real
adapter. External method/field/constructor changes additionally need exact
verified member rules; a class rename alone is insufficient. This does not
promote the mapping generator's inferred Minecraft/Forge rules to rewrite permission.

## Data composition

Forge `srgutils` 0.6.2 parses Mojang ProGuard and MCP TSRG mapping data. The
generator joins these within each version, then looks for exact official
symbols across versions and checks ASM declarations from the supplied Forge
development JARs. It generates rules for requested symbols and their class
components, rather than maintaining a manually enumerated migration map.

The namespaces must be kept distinct:

| Input | Direction and role |
|---|---|
| Mojang client/server TXT | Official names to obfuscated names, including field types and method/constructor signatures. |
| Source MCP TSRG1 | Obfuscated names to legacy MCP class names and SRG `func_*` / `field_*` members. Constructor rows may be absent. |
| Target MCP TSRG2 | Obfuscated names to intermediate `C_*`, `m_*`, and `f_*` names. `C_*` owners are not production target owners. |
| Source official-mapped Forge API JAR | Actual declarations with MCP class owners and Mojang member names: the Forge 1.16.5 development namespace is hybrid. |
| Target official-mapped Forge API JARs | Actual declarations with Mojang class and member names, including Forge patches and separately supplied FML APIs. |

The output namespace is `MOJANG_CLASS_NAMES_WITH_SRG_MEMBERS`: target classes
use Mojang names, while Minecraft members use target `m_*` / `f_*` names.
Forge-owned members keep their actual Forge names. Raw target `C_*` class
placeholders must never be emitted as production owners.

One verified composition is:

```text
1.16.5 production:
net/minecraft/util/ResourceLocation.func_110623_a()Ljava/lang/String;

1.16.5 official identity (obfuscated vk.a):
net/minecraft/resources/ResourceLocation.getPath()Ljava/lang/String;

1.20.1 official identity (obfuscated acq.a):
net/minecraft/resources/ResourceLocation.getPath()Ljava/lang/String;

1.20.1 production:
net/minecraft/resources/ResourceLocation.m_135815_()Ljava/lang/String;
```

This chain proves names and signatures. It does not establish every behavior
of `ResourceLocation` across the two releases. The private `namespace` field
also composes from `field_110626_a` to `f_135804_`, but current declaration
visibility checks keep that field `MANUAL`; an access transformer is not
assumed. Public constructors are resolved only when their exact signatures
exist in both mapping/API inputs.

Forge has no general official cross-version migration map. Exact unchanged
Forge owners/members are checked against both inventories. Same-simple-name
classes in another package are only `HEURISTIC` / `MANUAL` candidates. The
small explicit refusal policy contains five legacy anchors:
`IForgeRegistryEntry`, `RegistryEvent$Register`, `CapabilityInject`,
`RenderingRegistry`, and `GlobalLootModifierSerializer`. These identify APIs
that require semantic adapters; they do not invent target symbols.
These anchors, and detected descriptor/contract changes lacking an implemented
direct-call adapter, now report `UNSUPPORTED` with
`category=NO_AUTOMATIC_EQUIVALENT`. The presence of a restricted content-plan
adapter must not turn general registry, capability or rendering calls into
`ADAPTER_REQUIRED` promises. All four status-count keys remain in JSON, including
zero-valued `ADAPTER_REQUIRED`; no mapping enables automatic rewriting.

## Local inputs and provenance

The development loader, used by explicitly requested
`prepare-mappings --from-development-cache` and baseline tests, uses
`GRADLE_USER_HOME`, or the user's `.gradle` directory.
It reads both client/server mapping TXT files from
`caches/forge_gradle/minecraft_repo/versions/<minecraft>/`, and the pinned MCP
configuration ZIPs under `caches/forge_gradle/maven_downloader/`. It selects
`config/joined.tsrg` from each MCP ZIP.

Pinned MCP coordinates are:

- `de.oceanlabs.mcp:mcp_config:1.16.5-20210115.111550@zip`
- `de.oceanlabs.mcp:mcp_config:1.20.1-20230612.114412@zip`

The six default mapping inputs have pinned SHA-256 checks:

| Input | SHA-256 |
|---|---|
| Legacy client TXT | `7931ed6d723eceb1d621d05a76e10ddf643bf468c6ecf1c4ecf377bd72cf8b8c` |
| Legacy server TXT | `b3732b0f031abce7083cac8e594427e70d2e19674b8de2c682630c39b64228a7` |
| Legacy MCP ZIP | `80216a0f4ae79d81a5e11ceccaa97582a1f6a2cf079b7703b88a99a26547b342` |
| Target client TXT | `67bfc33ef0c7103f002d6ac134f8eddd3d67e2ce45d266e83816d1a2e2c9b01d` |
| Target server TXT | `dca153d20defb32cfac3f069c3bf77b3e13c30ae63847637479d3137e099bb72` |
| Target MCP ZIP | `f46d1050d8bed9046886b90a0d2fb80b4c6b5120fc35d32e5d37498dbe0a6d2a` |

The exact `mapped_official` Forge JARs are read from
`caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/`. The target
loader also looks for the matching `javafmllanguage`, `fmlcore`, and
`fmlloader` JARs in Gradle's module cache. Missing optional FML artifacts emit
diagnostics and reduce API coverage; they do not silently supply declarations.
Ambiguous cache variants require an explicit profile.

API JAR hashes record the locally read bytes; they are not pinned publisher
authentication. A locally generated mapped Forge JAR can depend on development
tooling and patches. Reports record logical source IDs, origins, and SHA-256
hashes without exposing local absolute input paths.

No command downloads mappings, resolves Maven dependencies, or follows a mod's
manifest classpath. Missing required development-cache inputs produce incomplete
internal data; the preparation service refuses to export it. A present default mapping input with the
wrong pinned hash is rejected. A missing or invalid explicit-profile input is
an input error. Populate the development cache independently or provide a
profile; `legacy-runtime:build` alone does not create the legacy development
baseline.

The official mapping TXT headers govern their use and redistribution. The
converter reads these local inputs and does not bundle the mapping files or
Minecraft API JARs in its distribution.

## Explicit mapping profile

The profile uses Java `.properties` syntax and requires exactly these twelve
keys. Version values are fixed to the project's baselines. Mapping/API paths
can be absolute or relative to the profile directory; semicolons separate API
JARs. Use forward slashes for Windows paths because Java properties treats
backslashes as escapes. A literal backslash must be escaped as `\\`.

```properties
legacy.minecraft=1.16.5
legacy.forge=36.2.42
target.minecraft=1.20.1
target.forge=47.4.23
legacy.clientMappings=inputs/1.16.5/client_mappings.txt
legacy.serverMappings=inputs/1.16.5/server_mappings.txt
legacy.srg=inputs/mcp_config-1.16.5-20210115.111550.zip
target.clientMappings=inputs/1.20.1/client_mappings.txt
target.serverMappings=inputs/1.20.1/server_mappings.txt
target.srg=inputs/mcp_config-1.20.1-20230612.114412.zip
legacy.apiJars=inputs/forge-1.16.5-36.2.42_mapped_official_1.16.5.jar
target.apiJars=inputs/forge-1.20.1-47.4.23_mapped_official_1.20.1.jar;inputs/javafmllanguage-1.20.1-47.4.23.jar;inputs/fmlcore-1.20.1-47.4.23.jar;inputs/fmlloader-1.20.1-47.4.23.jar
```

Each `.srg` input may alternatively be an extracted TSRG text file. Profiles
are trusted local development data. Their bytes are hashed and structural
conflicts are checked, but their content is not authenticated against the
default published mapping pins. The loader checks client/server overlap and
injective class targets. `srgutils` can collapse duplicate entries within one
mapping file before those checks; arbitrary profiles are not an adversarial
mapping-language validation boundary.

## Report and limits

`MappingReport` is a separate `reportType=LEGACY_MAPPINGS`, schema-version-1
document. The analyzer's `CompatibilityReport` v2 is unchanged. It includes
input fingerprint, baseline versions, target namespace, source provenance,
diagnostics, readable/unreadable class counts, reference counts, truncation,
all four `statusCounts`, `unresolvedMappingCount`, and per-symbol `entries`.
Each entry carries its legacy symbol, nullable target, category, confidence,
status, rewrite gate, occurrence count, and bounded class/evidence samples.
`bytecodeModified=false` and `conversionStatus=NOT_IMPLEMENTED` are explicit.
These are inspection-report fields, not a claim that the separate
`simple-content-v1` converter is unimplemented. Generated mapping rules still
have `automaticRewriteAllowed=false`, including for mods accepted by that profile.

The mapping pass reads ASM references directly. It does not reuse the
analyzer's 256-reference sample. It retains up to 20,000 distinct symbols,
permits at most 4,096 characters per symbol component, and retains four class
entry names per symbol. Valid descriptors from members and `TYPE` observations
are collected separately, including declaration, handle, and invokedynamic
type evidence. Generic signatures are not reinterpreted as JVM descriptors.
Omitted symbols set `referencesTruncated`; unreadable classes and missing
required input sources make `analysisStatus=PARTIAL`. Counts cannot describe
unreadable code, omitted symbols, or unscanned nested JARs.

Internal owners defined in readable input classes are excluded from direct
external-symbol entries. Descriptor composition can still require review for
mod-owned or third-party types whose target identity is not established.
JDK types are treated as descriptor components, not as a Java 8 to 17 API
compatibility audit. `COMPLETE` means the bounded scan completed with its
required sources, not that every symbol is resolved.

Exit code `1` means the report requires review because of unresolved entries,
incomplete input sources, unreadable classes, or truncation. Code `0` means
none of those conditions was detected; it does not authorize a conversion.
Codes `2`, `3`, `4`, and `70` retain the CLI usage, input, output, and internal
failure meanings documented in [VALIDATION.md](../VALIDATION.md).

## Limits of resolution and validation

The current generator does not search inherited members, import MCP snapshot
CSV aliases, resolve reflection strings, analyze adapter semantics, infer API
equivalence from bytecode bodies, resolve third-party dependencies, or remap
Mixin refmaps/access transformers/coremod scripts. Minecraft/Forge owner and
descriptor matches do not verify generic contracts, every invocation opcode,
side restrictions, final/abstract constraints, lifecycle, networking, or data
behavior. Removed official names may hide a renamed equivalent that needs
manual review. No fuzzy name match becomes a resolved rewrite rule.

Portable unit tests use small synthetic ProGuard/TSRG texts and ASM API JARs.
The real pinned environment integration is explicitly disabled in a portable
build. Require it on a populated development environment with:

```powershell
.\gradlew.bat clean build -PverifyMappingEnvironment=true --no-daemon --no-parallel
```

When enabled, a missing/broken baseline fails the test instead of being counted
as a pass. This test checks the `ResourceLocation` chain, public fields and
constructors, private-field refusal, and disabled automatic rewriting. It
does not start Minecraft or execute a mod.

Primary references: [ForgeGradle mapping namespaces](https://docs.minecraftforge.net/en/fg-6.x/configuration/#human-readable-mappings),
[Forge 1.16 official mapping migration](https://docs.minecraftforge.net/en/1.16.x/gettingstarted/#migration-to-mojangs-official-mappings),
and [MCPConfig mapping publication](https://github.com/MinecraftForge/MCPConfig).
