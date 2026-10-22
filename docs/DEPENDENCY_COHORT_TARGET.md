# LB-19 / DEPS-COHORT: finite target pair

Data follow-up: [FINITE_COHORT_DATA](FINITE_COHORT_DATA.md) now provides a bounded
included default for this exact pair. Omit `--mapping-bundle` to use it; explicit
invalid data never falls back. Candidate hashes and recipes below are unchanged;
source reports now additionally identify actual data scope/completeness.

Implementation slice `FINITE_TARGET_PAIR`, 2026-09-12. The engine now actually
serializes and reopens **two private target JAR candidates** for the reviewed
authored library and consumer. This is beyond source inspection, but **not**
public cohort conversion, installation approval, or evidence of game behavior.
DEPS-COHORT remains PARTIAL until its isolated source/target acceptance is run.
No Minecraft client/server or screen automation was used for this slice.

## Product and service boundary

`LegacyEngineService.planCohort(CohortPlanningRequest)` shares the existing
single worker, bounded request queue, cooperative work budgets, stage progress
and cancellation. The request explicitly wraps a complete source selection with
expected hashes and full local mapping data. It has **no output path**.

```text
legacy-converter plan-cohort --mapping-bundle <explicit-local-bundle> \
  --artifact library 7ccceb72ef6d1518310b21253acf18cff0502e8f06a892758ea254ae799f5cd4 <library.jar> \
  --artifact consumer a67a24e331c1d81b5e85ce267c9d55e622294f01134cdc9cca8cdcad76e7e8f1 <consumer.jar>
```

The CLI invokes the typed service, not a second converter. Exit 0 means
`STATIC_PAIR_VALIDATED`; exit 1 means BLOCKED. JSON is stdout-only. The returned
typed candidates contain fingerprints/proofs, **not JAR bytes, output paths,
PreparedConversion objects or installer capabilities**. Private scratch JARs
are removed after reopening, including on failure/cancellation. A cleanup error
blocks approval and is not silently ignored. No staging, mods directory or
installation journal is touched. `inspect-cohort` remains source-only.

## Exact implemented recipe

Only these two existing Forge 36.2.42-compiled **original** 1.0.0 archives are
admitted. IDs/filenames or successful linkage alone never qualify another JAR.

| Artifact | Original SHA-256 | Owned classes |
| --- | --- | ---: |
| lb_cohort_library | `7ccceb72ef6d1518310b21253acf18cff0502e8f06a892758ea254ae799f5cd4` | 2 |
| lb_cohort_consumer | `a67a24e331c1d81b5e85ce267c9d55e622294f01134cdc9cca8cdcad76e7e8f1` | 1 |

The complete archive hashes bind the reviewed bodies, entry ownership, metadata
and debug information. **Even a harmless repack requires review** in this first
recipe. This is intentionally not an expandable list of renamed API symbols.
All three original class byte arrays are preserved, including fields, offsets,
constructors, super calls, opcodes, exception/branch behavior, static assignment,
descriptors, annotations, constant pools and debug attributes. No ClassWriter
is used to transform them; ASM checks them as data. There are no lambdas, custom
interfaces, gameplay calls, assets or datapacks in this particular fixture.

The only changes are performed by three concrete, catalog-bound OFFLINE methods
in `compat.registry.CohortMetadataAdapter`:

| Operation | Change |
| --- | --- |
| `cohort.finite-metadata.v1` | Update loader/platform requirements; retain original modIds, versions, display/license text and the mandatory BOTH `[1.0.0]` AFTER library dependency; add the required compatible `legacybridge_runtime` dependency. |
| `cohort.finite-manifest.v1` | Preserve the pinned inert headers; append distinct `finite-cohort-pair-v1`, `STATIC-CANDIDATE-ONLY` and runtime-version markers. Never label this simple-content or composed-logic-recipes. |
| `cohort.finite-bindings.v1` | Add a schema-1 binding document identifying those three actual implementations. No empty runtime shim. |

The metadata adapter separately checks the exact input metadata hashes before
rewriting. Six RESOURCE_REWRITE records (three per artifact) include before/after
hash+size, exact rule, VERIFIED confidence, reason and real adapter descriptors.
VERIFIED applies **only to these finite metadata operations**, not to arbitrary
JDK compatibility, modules, @Mod equivalence or native behavior. No HIGH mapping
is promoted. Unchanged class hashes are reported separately, not as fake remaps.

These are additive offline operations. Runtime identity/version remain
`legacybridge_runtime` / `0.1.1-SNAPSHOT`; old catalog entry descriptors, revisions,
selection lists and runtime call implementations are unchanged. Old converted
JARs require their existing adapter subsets, not the new offline operations.
No new public ConversionProfile, Convert/Apply button or installer admission was
added. The existing simple-content regression is still checked separately.

## Whole-pair validation and failure policy

1. Run the existing source inspector for the whole explicit selection: safe
   paths/aliases, snapshot hashes, metadata closure on both sides, separate
   ownership/API inventories and per-JAR source linkage/access.
2. Require the complete exact reviewed pair **before serializing any target**.
   Re-capture each original using SafeJarReader and recheck its selected hash.
3. Account for the full non-directory entry set (only the reviewed classes and
   two metadata files). Unknown resources/bodies/dependencies require another
   implemented recipe; they are not copied or dropped to claim support.
4. Serialize each private candidate deterministically. Reopen real ZIP bytes
   with SafeJarReader, verify archive hash/CRC/central directory and every entry
   name/byte against the planned output. Reparse both target metadata documents.
5. Validate the reopened target dependency graph independently on CLIENT/SERVER,
   preserving required versions, sides and relative ordering. No original or
   supposedly compatible native port substitutes for the converted provider.
6. ASM/dataflow and offline linkage/access validate each target owner's classes
   separately. A required other JAR contributes declarations, never owned bodies.
   Those declarations come from the source inventory **only because complete
   byte equality was checked after target reopening**. Unknown references remain
   blocked; the existing JDK17/@Mod external-root restriction is unchanged.
7. Recheck both original hashes after candidate work. Only then return the
   complete pair proof. A failure of the second candidate discards the first
   as an approved result. Cancellation never returns a successful partial pair.

The new schema-1 `LEGACY_COHORT_TARGET_PLAN` encloses the unchanged source-only
subreport, target graph, candidate hashes/owned hashes/linkage, structured errors
and schema-1 TransformationLog. `transformationDisposition` explicitly marks
`VALIDATED_PRIVATE_ONLY` or `DISCARDED_ATTEMPT`. Failed attempts can retain their
diagnostic log, but `candidates` is empty and publication/installation remain false.
CompatibilityReport, existing source inspection and TransformationLog schemas
are not replaced or silently reinterpreted.

Work remains bounded by the source inspector and engine budgets. The second
capture/reopen allows at most 32 entries and 64 KiB per captured entry, plus the
exact-source identity gate. Original immutable snapshots and candidate scratch
files are operation-owned. These limits are not a hard shared-JVM heap sandbox,
OS-call timeout or protection against every hostile filesystem race.

## Actual API/semantic audit

The exact source and target `Mod.java` and `FMLModContainer.java` were reread:

- Forge 36.2.42 mapped source archive SHA-256:
  `bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376`.
- Forge 47.4.23 javafmllanguage source archive SHA-256:
  `1d786de4b0701e82d871f3ed6ac663d1ffa443ca9af21e97ef6cfc3c263278c1`.

Both declare `@Mod.value(): String`. The old container initializes a class
through its mod classloader and uses `Class.newInstance`; the target locates
the owning module, obtains the entry class, and chooses a context constructor
or a no-argument constructor. This fixture has only public no-argument mod
constructors, no `<clinit>` and no lifecycle-sensitive cross-mod static reads.
Its consumer constructs an independent `Score` instance. The module/initialization
differences still require actual Forge validation; structural linkage is not
proof of discovery. Nested EventBusSubscriber changes are **not** admitted.

`javap -p -c -s` on the originals confirmed `Score.compute(I)I` uses integer
multiply/add, an instance field and a negative-input IllegalArgumentException;
the consumer uses `invokespecial Score.<init>(I)V` and `invokevirtual compute(I)I`.
Expected future observations remain 5 -> 17, 0 -> 2, negative -> exception.
They were **not executed** by the tests or converter.

External orientation: [Forge mod metadata/entrypoints](https://docs.minecraftforge.net/en/1.20.1/gettingstarted/modfiles/)
and [Java 17 class-file/verification specification](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html).
These do not substitute for the exact local-source audit above.

## Evidence, rights and next implementation

See [the target-pair ledger](evidence/deps-cohort-target-2026-09-12.json) for
commands, test counts/skips, original/candidate/public hashes and package checks.
The full local mapping bundle is still explicit and **not approved for
redistribution**. No hidden Gradle-cache lookup, original Forge/game binaries,
full mapping inventory, fixture or harness is added to the public JAR. Included
recipe fingerprints and metadata operations describe this authored fixture;
they do not grant rights to redistribute anyone's mod. No mod acquisition or
publication occurred.

Next headless implementation: **LB-01 / PRODUCT-COMPOSED / COHORT_TARGET_PREVIEW**.
Connect explicit multiple selections to this typed private plan, with real
progress/cancellation, readable per-artifact proof and blocking reasons. Keep
ordinary conversion, Apply and loaded mods unchanged; do not create a public
cohort profile or wait on another launch-harness expansion. DEPS-COHORT native
source/target comparison, module wiring, behavior, multiplayer and subsequent
INSTALL-PROMOTION remain required acceptance gates, not completed work.
