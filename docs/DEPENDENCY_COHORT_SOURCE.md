# LB-19 / DEPS-COHORT: source ownership and linkage

Data follow-up: [FINITE_COHORT_DATA](FINITE_COHORT_DATA.md) adds the exact-pair
included default. `--mapping-bundle` is now optional; a provided path still means
full explicit data, never automatic fallback. The source-only behavior and
historical full-data evidence below remain distinct from target planning.

2026-09-12, implementation slice `SOURCE_COHORT_INSPECTION_V1`. DEPS-COHORT is
PARTIAL: **no target pair or dependency conversion is produced by this slice**.
The six existing profiles and installer admission are unchanged. This closes
the gap between a metadata-only dependency graph and actual source class linkage;
it does not establish semantic portability, native module wiring or discovery.

## Implemented operation

`LegacyEngineService.inspectCohort(CohortInspectionRequest)` uses the existing
single bounded worker, queue, stage progress, cooperative cancellation and typed
outcomes. `CohortSourceInspector` reuses SafeJarReader, LegacyReferenceScanner,
DependencyGraphVerifier and OfflineLinkageVerifier. It does not call a CLI,
load/initialize source classes, find developer caches, or download anything.

Each explicit selection supplies a stable artifact label, regular local JAR path
and expected SHA-256. This first request represents the **complete selected set
present on both physical sides**, not an inferred installed modpack. One mod per
artifact. There is no native-port substitution or version-based ABI assumption.

```text
legacy-converter inspect-cohort --mapping-bundle <explicit-directory> \
  --artifact library <library-sha256> <library.jar> \
  --artifact consumer <consumer-sha256> <consumer.jar>
```

The CLI calls the same service and emits canonical JSON to stdout only; it has
no report overwrite or output-JAR option. Exit 0 means SOURCE_LINKAGE_ONLY, not
conversion. A rejected cohort emits BLOCKED and exit 1. Invalid CLI usage or
failed setup has the existing distinct error exit. No environment-variable
fallback, built-in simple-data fallback, runtime API download or original-code
execution is used. The explicit full bundle retains its existing local-use-only
redistribution restrictions. No mapping equivalence is added.

## Verification and ownership

1. Reject symlinks/redirected ancestors/junctions, nonregular inputs and repeated
   file identities (including hard-link selections). Snapshot with SafeJarReader;
   validate archives and compare the supplied hash to the inspected bytes.
2. Preserve exact metadata/dependency rows. Require javafml 36 and mandatory BOTH
   Minecraft dependency; the graph checks Minecraft 1.16.5 / Forge 36.2.42.
   Arbitrary loader plugins, services, JiJ, mixins/coremods/ATs, native libraries,
   module descriptors, signed or multi-release archives are rejected.
3. Inventory each class's byte hash, flags, superclass, interfaces, method/field
   signatures and owning JAR/package. Duplicate classes and split packages
   **between selected artifacts** are blocked, even if bytes match. Unknown
   resources are only listed; no resource-validation or preservation claim is made.
4. Reuse the source metadata graph on CLIENT and SERVER, retaining optional and
   inactive declarations. For every scanned cross-JAR reference, require a direct
   mandatory BOTH dependency. Optional absence is metadata-valid when unused;
   actual optional/side-conditional linkage is not implemented here.
5. Validate each artifact **separately**. Only its bytes are owned. Permitted
   direct dependency artifacts contribute immutable declarations, not owned
   method bodies. An unrelated selected library grants no API access, including
   unused constant-pool references checked by the existing linkage verifier.
   A required transitive declaration not provided by a direct dependency blocks
   this deliberately conservative slice; it is not silently imported.
6. ASM/dataflow/linkage/access check each owned body using the existing finite
   verifier. Missing members, wrong descriptors/opcodes/staticness, private
   access and unknown external classes fail the whole inspection. Per-artifact
   successful checks preceding a rejection are diagnostic evidence, not partial
   approval. No conversion/publish/install capability is returned.

Only JDK declarations read from `jrt:` and the real Forge `@Mod` annotation are
platform roots for this first fixture. The source and target annotation
declarations are compared exactly in the trusted local data; this is a structural
precondition, **not a VERIFIED mapping or semantic equivalence**. No general
Minecraft API is admitted. Existing linkage restrictions on constructors,
reflection, callbacks, nests, default interfaces and sealed types remain.
The JDK declaration view is Java 17, not a guarantee of Java 8 runtime behavior.

## Typed report and limits

Schema 1 / `LEGACY_COHORT_SOURCE_INSPECTION` records all selected expected hashes,
successfully inventoried artifacts, complete metadata graph, external reference
occurrences, per-artifact source linkage, bounded diagnostics and mapping-data
provenance. BLOCKED never becomes an approved subset. Flags remain:

```text
conversionAuthorized=false; publicationAllowed=false; classesExecuted=false
targetLinkage=NOT_RUN; runtimeValidation=NOT_RUN
dependencyTreatment=SEPARATE_CONVERSION_REQUIRED; targetApiStatus=NOT_PLANNED
```

This new report does not change CompatibilityReport or TransformationLog schemas.
There is no TransformationLog because no transformation is performed. No static
report is accepted by the public installer or exposed as an Apply result.

Bounds: 1–4 JARs, 16 MiB compressed per input, 512 entries per artifact, 32 classes
per artifact / 64 total, 256 KiB per class / 4 MiB aggregate captured class bytes,
64 KiB each captured TOML/manifest, 8,192 declarations and 8,192 scanned external
occurrences. The 64 MiB aggregate inflated admission check is after each bounded
SafeJarReader scan; that reader/service also has its independent streaming work
limits. These are not a hard heap sandbox or preemption of an OS call/parser.
The engine queue still retains requests, not prepared class graphs.

Only SafeJarReader-owned temporary snapshots are used; no converted JAR or staging
directory is created. Cooperative cancellation returns no approved result. Path
revalidation is not an OS sandbox against hostile concurrent replacement, and a
hash identifies inspected bytes, not a publisher signature or redistribution grant.

## Exact fixture, APIs and evidence

[Two authored Forge source artifacts](../fixtures/dependency-cohort-1.16.5/README.md)
compile with ForgeGradle 6.0.54 against real Forge 36.2.42, Java 8 bytecode using
JDK17. The consumer calls `new Score(2).compute(input)` across JARs. Expected
future observations: input 5 → 17, input 0 → 2, negative input throws. **Not
executed here.** The public product contains neither fixture nor harness.

Source `net/minecraftforge/fml/common/Mod.java` was read from the mapped
1.16.5-36.2.42 source archive (SHA-256
`bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376`).
Target source was read from `javafmllanguage-1.20.1-47.4.23-sources.jar`
(`1d786de4b0701e82d871f3ed6ac663d1ffa443ca9af21e97ef6cfc3c263278c1`).
`@Mod.value()` is a String element in both; nested EventBusSubscriber behavior
is not included. The pinned inventories used in the real fixture inspection
are recorded in its JSON. No source or full inventory is redistributed.

Existing graph semantics and exact loader fingerprints are in
[DEPENDENCY_GRAPH](DEPENDENCY_GRAPH.md); modular package ownership limits in
[PACKAGING_ARCHITECTURE](PACKAGING_ARCHITECTURE.md). Primary orientation:
[JVMS 17 resolution/access](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4.3),
[Forge target ModSorter](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.1/fmlloader/src/main/java/net/minecraftforge/fml/loading/ModSorter.java).
No change to those existing algorithms was made to admit this fixture.

[Commands, hashes, failures and validation dimensions](evidence/deps-cohort-source-2026-09-12.json)
are separate from this implementation description. Native tests remain NOT_RUN.

## Historical handoff, implemented by the next slice

The target work below was delivered by [FINITE_TARGET_PAIR](DEPENDENCY_COHORT_TARGET.md).
This source-only operation/report remains unchanged. Current development cursor
is in the contract, not this historical handoff.

Continue **LB-19 / DEPS-COHORT**, next slice `FINITE_TARGET_PAIR`:
implement exact per-artifact target recipes for this authored pair, preserve the
consumer's required dependency, validate target metadata/linkage/access against
the entire proposed pair, then create/reopen two private candidates with one
cohort result. Failure in either must publish neither as an approved pair.
Do not route them through the ordinary single-mod converter or remove the
dependency to satisfy its closed profile. No public Apply/promotion until the
separate acceptance gates. Native source/target behavior comparison is deferred,
not waived; do not expand QA launch harnesses as the next automatic task.
