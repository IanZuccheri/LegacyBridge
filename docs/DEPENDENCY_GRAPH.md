# Metadata-only dependency graph

The separate [source cohort inspector](DEPENDENCY_COHORT_SOURCE.md) now binds
this graph to explicit hashed JAR ownership and per-artifact offline linkage.
This metadata-only API remains unchanged; neither result authorizes conversion.

`DependencyGraphVerifier` is a separate, read-only service under
`legacy-converter/conversion/dependency`. It does not broaden a conversion profile,
rewrite `mods.toml`, install dependencies, inspect a Gradle cache, download anything,
open a mod JAR, or execute any mod class. A passing graph is not permission to convert.

## Explicit input and API

All contracts below are public nested records/enums in `DependencyGraphVerifier`.

```java
ParsedMetadata parseMetadata(String artifactId, byte[] modsToml,
                             Optional<String> exactJarVersion);
Result verify(List<Installation> installations);
```

`parseMetadata` reuses the existing strict-UTF-8 `ForgeMetadataAnalyzer` and TOML
parser. `ParsedMetadata.valid()` means the bounded declarations were parsed, not
that their dependency graph passed. The caller must reject an invalid parse.
`${file.jarVersion}` requires an explicitly supplied exact manifest version;
there is no filename/version guessing. Other unresolved substitutions are blocked.
The original `[[mods]]` declaration order is retained as `declarationIndex`.

The data model is:

- `Mod(artifactId, declarationIndex, modId, version, dependencies)`, with a
  four-argument convenience constructor that uses index zero for a single-mod JAR.
- `Dependency(modId, mandatory, versionRange, side, ordering)`; side is
  `BOTH`, `CLIENT` or `SERVER`, and ordering is `NONE`, `BEFORE` or `AFTER`.
- `Installation(side, installed, selected, platform)`, where physical side is
  `CLIENT` or `SERVER`. Supply one or both sides, without duplicate sides.
- `Platform.source()` fixes Minecraft 1.16.5 / Forge 36.2.42 with no runtime;
  `Platform.target()` fixes Minecraft 1.20.1 / Forge 47.4.23 and the current
  `AdapterCatalog.RUNTIME_VERSION` (0.1.1-SNAPSHOT when this service was added).

The caller must supply the complete actual installed/selected cohort for each
side and trustworthy artifact grouping. A dependency marked `CLIENT` does not
prove that its owner or provider is a client-only mod. No installed/selected
presence is inferred. `artifactId` is an opaque ownership label, never a path
the service opens; it is not a verified file hash or an ownership proof.

All mods in one artifact must be supplied together, with contiguous declaration
indices starting at zero. An artifact cannot be partly installed and partly
selected. Duplicate modIds are blocked, even when versions are identical or the
source loader would choose one version. Minecraft, Forge and the runtime are
reserved platform identities; explicit mods cannot impersonate them.

## Closure and ordering

Every dependency from installed and selected mods is checked. This includes
incoming requirements from an already-installed consumer to a newly selected
library. An active mandatory dependency must exist. An active optional dependency
must match its range when present; absence alone is allowed. Unknown range grammar
is blocked even when the dependency is optional/absent or inactive on that side.
No declaration is dropped to turn a failure into success.

Ordering is a graph of artifacts, not independent modIds. Present `BEFORE` creates
owner-artifact -> dependency-artifact; `AFTER` reverses that edge. Internal
dependencies within one artifact do not create a self-edge. The graph detects
cycles that an ungrouped modId graph would miss. Version requirements alone do not
create ordering edges when the declaration says `NONE`.

Both reviewed Forge lines filter version/presence checks by dependency side, but
add ordering edges without that side filter. Therefore a client-side ordering
declaration can still participate in a dedicated-server cycle if both mods are
present. The verifier preserves this distinction in `EvaluatedDependency`.

There is an actual lookup difference: Forge 36 builds its ordering lookup using
all modIds, whereas the inspected Forge 47.4.23 `ModSorter` indexes the first modId
of each artifact. A cross-artifact target ordering edge referring to a secondary
modId is blocked as `ORDERING_CONTRACT_DIVERGENCE`; the service neither erases the
edge nor pretends that the loaders enforce the same relationship.

## Reports and limits

`Result.toTree()` is schema 1, report type `LEGACY_DEPENDENCY_GRAPH`, with
`PASSED_METADATA_ONLY` or `BLOCKED`. `Result.declarations` retains the complete
explicit input, including every dependency row. Side-specific diagnostics include
the owning artifact/modId, requested dependency/range and actual available version.
For graph-admitted input, `SideResult.dependencies` also records inactive and
optional-absent declarations. Preflight-invalid input remains in `declarations`
but does not receive a partial graph interpretation.

Blocked sides have no published topological order. A passing `artifactOrder` and
`modOrder` are deterministic valid linearizations, not predictions of discovery
order or Forge's tie-breaking. Per-artifact mod order retains declaration order.
No runtime, networking, API compatibility or conversion success is inferred.

Bounds are two physical sides, 256 non-platform mod declarations per side,
64 dependency declarations per mod, 2 MiB TOML, 256-character artifact labels,
128-character exact versions and 512-character ranges. More than 1,024 blocking
diagnostics produces an explicit `DIAGNOSTIC_LIMIT`; complete input declarations
remain available and no order is approved. Cooperative cancellation checkpoints
abort without producing an approved result. This service has no publication step.

Version matching reuses `NumericVersionRange`: numeric exact/range expressions
only, no Maven unions, qualifier comparison, loader support-matrix aliases or
development `NONE`/`0.0NONE` exemptions. Bare numeric expressions are conservatively
treated as exact requirements, stricter than general Maven recommendations.
An omitted range is unconstrained, but cannot satisfy an absent mandatory mod.
For the qualified runtime version, only the exact current catalog version in
brackets and the exact catalog-generated runtime range are explicitly recognized.
These restrictions can reject metadata that Forge itself accepts.

## Real API evidence

The implementation was checked against these local, trusted artifacts. They are
development audit inputs only, not dependencies of the graph service and are not
redistributed by this change:

| Artifact | SHA-256 | Inspection |
| --- | --- | --- |
| `forge-1.16.5-36.2.42-launcher.jar` | `dfeab9c7d582fce5b1112e9d3038fa0e7075296e467031ce1e2c0ba9fdcf441f` | Read-only `javap -p -c` on Forge `ModSorter`, not loading a mod |
| `fmlloader-1.20.1-47.4.23-sources.jar` | `7917b9e169ed8d1f34299ebd6c8136f310a176754b52b1e492b86638aa059667` | `ModSorter.sort/addDependency/buildUniqueList/verifyDependencyVersions` and `MinecraftLocator` |

Corresponding primary upstream source: [Forge 1.16.x ModSorter](https://github.com/MinecraftForge/MinecraftForge/blob/1.16.x/src/fmllauncher/java/net/minecraftforge/fml/loading/ModSorter.java)
and [Forge 1.20.1 ModSorter](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.1/fmlloader/src/main/java/net/minecraftforge/fml/loading/ModSorter.java).
The artifact hashes, not a moving branch URL, identify the inspected baselines.

`DependencyGraphVerifierTest` provides small metadata-only positive/negative tests
for both sides, exact platforms, inbound closure, optional dependencies, duplicate
IDs, ordering, multi-mod groups, source/target divergence, bounds and cancellation.
Build/test execution is a separate serial validation step; these tests do not
launch Forge or prove actual installation/discovery.
