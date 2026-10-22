# LB-05: independent preserved-artifact readback

Delivered slice: `INSTALL-PROMOTION / INDEPENDENT_LOGIC_READBACK_V1` (2026-09-13).
The parent cut remains PARTIAL. This is a working offline inspector of an existing
original/candidate pair, not installer promotion, a new conversion profile or a
claim about arbitrary preserved behavior. [Evidence](evidence/preserved-admission-2026-09-13.json).

## API and developer CLI

`LegacyEngineService.inspectPreservedArtifact(PreservedArtifactRequest)` returns
an `EngineJob<PreservedArtifactInspection>` using the existing bounded worker,
queue, aggregate admission, progress, cancellation and error contracts. It never
calls conversion/prepare/publish, executes mod classes or writes a converted JAR.
The request pins both JAR SHA-256 values and explicitly selected `MappingData`.
Use `MappingData.reviewedBundle(directory, manifestSha256)` to bind reviewed data.

```text
legacy-converter inspect-preserved original.jar candidate.jar --original-sha256 <sha256> --candidate-sha256 <sha256> --mapping-bundle <directory> --mapping-sha256 <manifest-sha256>
```

All four options are required, unique and explicit. Exit codes: 0 scoped pass,
2 invalid usage, 3 refused/invalid input, 4 report output failure. Stdout is JSON
presentation for developers, never an interface consumed by Minecraft. CLI classes
remain excluded from the public JAR; its reusable engine does include this API.

Report schema 1, type `LEGACY_PRESERVED_ARTIFACT_INSPECTION`, distinguishes
`PASSED_SCOPED` from `UNSUPPORTED`; read/parse/data failures also use structured
engine errors. `installationAllowed` and `publicationAllowed` are always false;
`classesExecuted=false`, `runtimeValidation=NOT_RUN`. Neither an editable report
nor constructing a Java result record grants installation authority. Existing
CompatibilityReport, TransformationLog, adapters and journal schemas are unchanged.

## Independent checks and deliberately narrow contract

1. Require distinct canonical regular files, reject observed symlink/junction
   aliases and an original/candidate hardlink pair. Snapshot-read both with
   `SafeJarReader`; match selected hashes and reject special loader/archive features.
2. Require one source JavaFML mod admitting Forge 36, resolved identity/version,
   non-reserved modId and mandatory Minecraft 1.16.5 dependency. Only mandatory
   Minecraft/Forge dependencies with BOTH/NONE and compatible pinned versions
   are admitted. Unknown/optional/duplicate/side- or order-specific dependencies
   are refused, not silently dropped. This is stricter than broader converter
   grammars and intentionally does not admit dependency cohorts.
3. Reconstruct only the four implemented metadata recipes (mods.toml, manifest,
   pack.mcmeta, adapter binding); require exact resulting bytes and exact entry
   inventory. Other non-class bytes must equal the original. The existing closed
   content resource validator must also accept the resources; unchanged does not
   make an unknown format supported. Binding includes the current exact profile/ABI.
4. Compile the existing `VerifiedLogicMappings` recipes from the actual original
   and selected API inventories. Keep HIGH distinct from VERIFIED. Unknown
   external references cannot pass through an unreviewed remap fallback.
5. Validate the finite target `LogicProfileShape`. Compare every actual owned
   class against the exact canonical symbol projection of its source: declarations,
   hierarchy, descriptors, opcodes, constants, fields, methods, annotations, debug
   information, control flow, exception tables and handles must agree. No converter
   or `LoggedClassRemapper` is called and no replacement class/JAR is published.
6. Canonicalization ignores derived frames/maxima and unused constant-pool layout;
   the actual source and candidate bytes separately pass the existing ASM
   `BytecodeValidator` (including frames/maxima) and scoped offline linkage/access
   verifier. Re-read and rehash both selected files before returning the snapshot.

Independence means readback of actual bytes without trusting conversion success,
logs, or rerunning the converter. The inspector shares the reviewed rule compiler,
metadata recipes, ASM commons projection and offline verifier with the project.
It is not a second independently implemented ASM stack, formal semantic proof or
JVM/Forge runtime verification. A shared rule defect still needs source/target
behavioral QA to detect it. Hash integrity is not authenticity or an OS sandbox;
the returned snapshot makes no promise against a later external file replacement.

## Bounds and data

Per JAR: 8 MiB captured, 512 KiB/class, 1 MiB/non-class entry, 1..32 classes.
Only Java 8 class files; reject opaque class/field/method attributes. Per class:
at most 128 fields. Per artifact: at most 256 methods and 32,768 instruction-list
nodes; per method at most 4,096 nodes and 256 stack/local slots. Normal archive
entry/name/inflate limits still apply. Default service payload budgets are
64 MiB input / 128 MiB inflated / 64 MiB captured with a two-minute cooperative
deadline and queue capacity eight. Re-reads contribute to the operation budgets.
These are not retained-heap/RSS bounds or guaranteed interruption of blocking I/O.

This exact logic profile still requires an explicitly reviewed full local API/
mapping bundle. Its current classification is
`LOCAL_USE_ONLY_NOT_APPROVED_FOR_REDISTRIBUTION`; it is **not embedded**, downloaded
implicitly, or found via Gradle caches. Included simple/cohort data is rejected for
this inspection. The original and candidate are likewise user-selected local files.
The existing simple player profile still uses its included finite data.

No new equivalence was added. Actual Item.isFoil and DeferredRegister sources from
Forge 36.2.42 / 47.4.23 were reviewed, and the matching inventories/recipes were
revalidated; exact source/JAR/data pins are in the ledger. Runtime remains
`legacybridge_runtime` / `0.1.1-SNAPSHOT`.

## Validation performed

- 71 focused checks passed, no failures/errors/skips in the corrected run;
  22 new cases. Real compiled fixture: six classes, 25 methods. Existing original
  and converted hashes remain intact; no new conversion output was generated.
- Matching-hash mutations of behavior, lambda handle, hierarchy, annotations,
  MAXS, opaque attributes, class inventory, localization, profile, binding and
  dependencies are refused. Corruption, original dependency loss, stale hashes,
  hardlink alias, cancellation and changed data-manifest pins are covered.
- All modules assemble; public/runtime packaging, engine-consumer boundary and
  installed-CLI audits pass. The installer still refuses this actual logic artifact;
  42 existing simple add-only/transactional regression cases pass.
- A developer-only source probe, `tools/PreservedAdmissionProbe.java`, ran the actual
  public-JAR engine in a separate Java 17 process, outside the repository, with
  copied explicit inputs/libraries/data and empty configured homes/Gradle caches.
  A separate installed-CLI process produced identical JSON. Class-load logs locate
  the engine/inspector in the public JAR, not the development classpath; no original
  or candidate classes were defined. Neither empty cache was populated.
- This flat JVM probe supplies platform libraries explicitly. It does **not** test
  Forge discovery, JiJ/JPMS selection, a server, native screens or behavior. It does
  not demonstrate OS-enforced offline execution. No Minecraft process was started.
- Separate contract checks: 75 passed, two Windows symlink-privilege skips;
  23 prompts/81 cuts structurally verified. Evidence paths are not execution or
  freshness proof, and no pending acceptance requirement was marked passed.

Reproduce focused tests with the explicit local opt-ins `-PadmissionBundle=<dir>`
and `-PadmissionCandidate=<jar>`; see the full serial command in the ledger.
Without those inputs the real-pair environment tests are skipped, not passed.

## Next finite implementation (do not repeat readback)

Follow-up now delivered: [journal admission binding](PRESERVED_ADMISSION_JOURNAL.md).
The next active implementation is PRESERVED_ADD_ONLY_INSTALL; the paragraph below
preserves this inspector's original handoff, not the current development cursor.

Continue LB-05 / INSTALL-PROMOTION with **JOURNAL_ADMISSION_BINDING**: bind exact
profile, original/candidate and reviewed data pins to a versioned installation/
recovery contract; freshly rerun inspection rather than trusting persisted PASS.
Define strict legacy V1/V2 handling and interrupted-operation ownership before
connecting a new profile to Apply. Preserve the existing simple-only validator.
Then integrate approved selection, live dependency/loaded-file checks and safe
per-file recovery. Native Apply/discovery/behavior acceptance remains a separate
required gate. COMPOSE-01 and DEPS-COHORT requirements have not been satisfied by
these checks. Generic composed/cohort Apply, lifecycle/food/network profile
admission, automatic restart, loaded upgrades and broad promotion remain unsupported.
