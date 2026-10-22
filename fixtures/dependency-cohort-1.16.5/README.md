# Authored dependency cohort, source 1.16.5

Original LegacyBridge fixture sources under the repository's all-rights-reserved
license; no new redistribution permission is granted by this fixture. No real
third-party mod, private binary, Forge source or mapping payload is redistributed.

Forge 36.2.42, official 1.16.5 mappings, ForgeGradle 6.0.54, JDK17 compiler with
`--release 8`. Run the root wrapper with `-p fixtures/dependency-cohort-1.16.5 build`.
No run configuration or automatic game launch is provided.

Two separate reobfuscated archives are built:

- `legacybridge-cohort-library-1.16.5-1.0.0.jar`: `lb_cohort_library`, entry point
  plus public instance `Score(int).compute(int)` API, private per-instance offset.
- `legacybridge-cohort-consumer-1.16.5-1.0.0.jar`: `lb_cohort_consumer`, mandatory
  exact library 1.0.0, BOTH, AFTER. The library is compile-only, never embedded.

Consumer construction assigns `observed = evaluate(5)` through `new Score(2)`.
The expected observation is 17, `evaluate(0)` is 2, and negative input throws
IllegalArgumentException. These are **future behavioral oracles, not observed
runtime results**. Original or converted classes are never loaded during static
inspection. All methods, debug information and bytes remain original here.

The engine tests also generate inert small negative JARs for missing/changed
dependency versions, members/access/staticness, unknown references, split
packages, duplicates, optional/side/ordering, hooks, corruption and hash changes.
Synthetic API data in those unit tests is not a verified platform mapping.

Current endpoint: `inspect-cohort`, source linkage only. No target pair, native
comparison, resources/reload validation, public conversion or Apply is implemented
by this fixture. Do not install either original in a player's target `mods` folder.
