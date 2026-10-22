# Included finite cohort declarations

2026-09-12 — LB-02 / DATA-EXPAND / FINITE_COHORT_DATA.
Implementation delivered for the **exact authored library/consumer pair only**.
DATA-EXPAND remains PARTIAL; this is not a new public conversion profile or
native acceptance. [Artifact-bound evidence](evidence/finite-cohort-data-2026-09-12.json).

## Player, service and CLI behavior

The [Group preview](PRODUCT_COHORT_PREVIEW.md) now uses included data when the
parent's bundle field is blank. An explicit path still selects the complete
local bundle. Invalid explicit data fails; it never falls back to included
data or searches a developer cache. The individual composition preview and
five development conversion profiles still require complete explicit data.
The existing built-in simple-content-v1 conversion remains available.

`MappingData.builtinCohortPreview()` is consumed by the shared
`inspectCohort` / `planCohort` service, not a second implementation in the UI.
The two CLI commands now make `--mapping-bundle` optional:

```text
legacy-converter plan-cohort \
  --artifact library 7ccceb72ef6d1518310b21253acf18cff0502e8f06a892758ea254ae799f5cd4 library.jar \
  --artifact consumer a67a24e331c1d81b5e85ce267c9d55e622294f01134cdc9cca8cdcad76e7e8f1 consumer.jar
```

Use actual input paths. `inspect-cohort` accepts the same arguments but proves
only source linkage. No environment-variable data fallback is introduced.
The successful plan still returns fingerprints/diagnostics for two **private,
temporary** candidates, not an installable JAR, ordinary conversion job, receipt
or Apply permission. Their original hashes, target hashes, mandatory dependency,
three unchanged class bodies and six existing metadata operations are preserved.
Continuing previews cannot activate or install anything in the current session.

## Minimal data and admission boundary

`EmbeddedCohortData` reads only
`META-INF/legacybridge/cohort-preview-v1/platform-contract-v1.tsv` from the JAR.
Schema/revision 1, UTF-8 with LF, **537 bytes**, hard read ceiling 2 KiB, SHA-256:
`20fda70724153dee806f1fa20812a4cc8713da440d58692255d8597d1503eafe`.
Missing, altered, oversized or incompatible data is rejected, with no fallback.

Both actual baselines have the same declaration:

```text
net/minecraftforge/fml/common/Mod
access 0x2601; superclass java/lang/Object
interface java/lang/annotation/Annotation
value()Ljava/lang/String; access 0x0401
no fields
```

Only that declaration and provenance hashes are included. No Mojang/SRG rename
entries, Minecraft API, EventBusSubscriber, Forge implementation, JDK bodies or
fixture classes are included. Java platform declarations are read by the
existing offline verifier from the running **Java 17** platform; other Java
feature versions are rejected by this data provider. This does not establish
Java 8 behavior or arbitrary cross-version API equivalence.

The scope is `FINITE_COHORT_CONTRACT`, explicitly `complete=false`. An incomplete
inventory cannot establish general platform ownership: the inspector checks the
**actual captured archive hash**, not just the caller's claimed hash, against
the existing finite recipe before scanning classes with this data. The pins
are shared through `FiniteCohortContract`, not duplicated in a new converter.
Unreviewed archives fail with `FINITE_COHORT_DATA_SCOPE`; changed bytes with a
claimed old digest fail with `INPUT_CHANGED`. Missing dependencies still block
the pair. The full-data source inspection retains its previous broader static
scope; full-data target planning still requires the exact pair.

Generic mappings, all six ordinary conversion profiles and composition reject
this data kind with `MAPPING_SCOPE_UNSUPPORTED`. Direct converter use also
rejects it; portable export cannot relabel it a complete environment.
No confidence was promoted and no transformation/adapter recipe was added.

## Provenance and distribution boundary

Actual source files and class declarations were inspected from the exact local
baselines, not guessed from names. `javap -p -s -verbose` inspected the class
files without executing them. Source/target classfile majors are 52/61; both
declare runtime retention and TYPE targeting. The nested subscriber API differs
and is deliberately absent. All four full Minecraft mapping tables have no
Forge @Mod entry; no mapping is fabricated for it.

| Evidence | SHA-256 |
| --- | --- |
| Forge 36.2.42 mapped source archive | `bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376` |
| JavaFML 47.4.23 source archive | `1d786de4b0701e82d871f3ed6ac663d1ffa443ca9af21e97ef6cfc3c263278c1` |
| Source API archive | `1a3e71d6ccb8461fe9d94f4c63551fe52a8c0c01116572c49a0c3253d0506b03` |
| Target JavaFML API archive | `c11e456e4486152f77d7f34af37ce795e9abbf3a100663ed62abdd6ff5cd3661` |
| Source Mod.class | `726a43641c8037d48d5a29e8111df52e8e6e715d503960ce2a4e173cdfe8601d` |
| Target Mod.class | `36245dc17464bf7a9df47b983ea5098340661ffcc9309c33c883464d92813c5e` |

The source headers identify Forge Development LLC/contributors and
`LGPL-2.1-only`. The engineering distribution choice is to include this altered
declaration component under those terms with attribution, its preferred editable
plain TSV and the full license alongside it. See the upstream
[source-branch license](https://github.com/MinecraftForge/MinecraftForge/blob/1.16.x/LICENSE.txt),
[target-branch license](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.x/LICENSE.txt)
and [target annotation source](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.x/javafmllanguage/src/main/java/net/minecraftforge/fml/common/Mod.java).
Exact archive/class hashes above, rather than moving branches, pin this audit.

This is a separately identified data component, not a relicense of the project
or clearance of the public product. It does not authorize redistribution of
Minecraft, arbitrary derived mappings, original mods or private files. The
[existing rights gates](PACKAGING_RIGHTS.md) remain open. The full local bundle
remains `LOCAL_USE_ONLY_NOT_APPROVED_FOR_REDISTRIBUTION`, never packaged.

The development oracle contained seven files totaling 35,272,616 bytes at this
audit. Replacing its operational use for this exact preview with 537 bytes of
data is a disk/data comparison, **not** a heap measurement or compatibility
percentage. JDK and ordinary packaged engine dependencies remain required.

## Reports, packaging and validation

Source schema 1 gains additive `mappingData` fields: actual scope, actual
completeness, `nameMappingTablesUsed=false`, Forge annotation root and running
Java 17 declaration provenance. Existing CompatibilityReport, TransformationLog,
product/cohort schemas, runtime ID and adapter ABI are not bumped or weakened.
Full versus finite source reports intentionally differ in data provenance;
candidate bytes, graphs, checks and transformation logs match exactly.

167 distinct focused JUnit cases passed: 130 converter + 35 product, followed
serially by the two heavier existing real-source/simple-conversion checks.
There are 15 new cases, not 15 newly supported mods. Tests cover corrupted data,
scope/export restrictions, exact real declarations, full/finite equivalence,
unknown/changed archives, missing providers, controller defaults, explicit bad
data, cancellation and unchanged ordinary jobs. The existing simple fixture
converts byte-identically through its full and built-in paths.

The first combined run failed: a malformed negative ZIP hit the ZIP gate before
the intended scope gate, and the test worker exhausted heap while loading the
old full mapping oracle. The fixture was changed to a valid, unreviewed archive;
the new test releases its static oracle and the two heavy old checks ran in a
separate process. No product memory limit was raised. These failures and the
corrected passes are retained in the ledger, not hidden as successful runs.

An additional Java 17 process ran outside the repository with copied installed
CLI libraries, only the existing test probe classes and the two fixtures as
data. No mapping bundle; empty user/Gradle/temp directories; `-Xmx128m`.
CLI/service canonical reports matched, originals were unchanged and temporary
directories stayed empty. Its self-tested, **test-only deprecated SecurityManager**
reported zero unexpected external-file/network/subprocess/exit attempts.
It allows the Java installation and exact ancestor path metadata; it is not an
OS sandbox, production heap guarantee or an offline-game certification.

All modules assemble and public-product/runtime/API/engine/installed-CLI audits
pass independently. The public JAR includes the TSV, notice/license and loader;
no fixture, CLI launcher, probe, private bundle or legacy game class is packaged.
Runtime adapters/catalog, existing simple data, installer and libraries remain
byte-identical to the previous artifact. Engine classes changed for the new
scope and rejection gates; do not claim that the whole engine is byte-identical.

Minecraft/client/server/screens, behavior comparison, multiplayer and final-JAR
native qualification: **NOT_RUN**. Extensive regression and rights/promotion
remain pending. No new acquisitions or publication occurred.

## Next development

**LB-02 / DATA-EXPAND / COMPOSITION_DATA_SCOPE**: inspect the actual data consumed
by the existing individual logic+recipes preview, derive a separately scoped
minimal contract only if semantic/ownership and redistribution requirements can
be met, otherwise retain an explicit local route. Do not reuse this annotation-
only inventory for that operation, admit unknown inputs, or promote Convert/Apply.
This exact cohort-data implementation is complete; its acceptance dependencies
and native gates are not waived. Continue headlessly, without another launch
harness campaign.
