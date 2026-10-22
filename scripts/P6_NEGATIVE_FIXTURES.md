# P6 local negative fixtures

These QA tools are not product source sets and must never be packaged. They do
not launch Minecraft, run Gradle, change a client input folder, download data,
or place an original fixture on any execution classpath.

Run from the existing project with its already-built CLI distribution:

```powershell
.\scripts\Prepare-P6NegativeFixtures.ps1 -JavaHome 'C:\Program Files\Java\jdk-17'
```

The helper requires Java 17, compiles only `P6NegativeFixtures.java`, and reads
the genuine Forge 36.2.42 fixture at
`fixtures/simple-content-1.16.5/build/libs/legacybridge-example-1.16.5-1.0.0.jar`.
Its required SHA-256 is
`00dd689f53f9631ecf4a8e84da142eb21051e2ca33f1c05403e4060e15b10947`.
A different original is refused. ASM changes only namespace string literals,
including the field constant, `@Mod` annotation and registration arguments;
matching resource paths/text receive the same namespace substitution. Original
methods are never defined or invoked. PNGs remain byte-identical. Java 8 class
version, class identity and registration grammar remain unchanged.

All generated data stays under `build/p6-public/negative-fixtures`:

| File | Purpose |
|---|---|
| `corrupt.jar` | Plain text, deliberately not a ZIP; expects `MALFORMED_ZIP` |
| `p6-unsupported.jar` | Genuine clone `p6_unsupported` plus a worldgen path; conversion must abort |
| `p6-dependency.jar` | Genuine clone `p6_dependency`, mandatory absent `p6_missing_dependency` |
| `p6-cancel.jar` | Valid control clone `p6_cancel`, for separate cancellation/write-denial QA |
| `p6-duplicate-a.jar`, `p6-duplicate-b.jar` | Byte-identical `p6_duplicate` clones; both must be marked duplicate in inventory |

The extra worldgen entry is `data/p6_unsupported/worldgen/configured_feature/p6_probe.json`
containing the inert `{}` sentinel. The negative assertion is rejection of the
unsupported resource category, not validity of that entry as a playable feature.
The dependency case tests the existing refusal of third-party dependencies; it
does not implement or prove a dependency resolver.

Sorted ZIP entries and fixed local timestamp `1980-01-01T00:00:00` make fixture
JARs reproducible on the pinned JDK/ASM. Existing JARs must match exactly and are
never replaced. Existing JSON evidence may match structurally with different
key order; it is preserved. Each qualification gets a new UUID directory, keeping
earlier evidence and converted control artifacts separate from the six inputs.

Qualification executes the real standalone CLI in child JVMs using only its
development libraries and explicitly selects the existing local bundle at
`build/p5-transactions/client/legacybridge/cache/mapping-data`. It never copies
or redistributes that bundle. Expected conversion exit codes are 3 for corrupt,
1 for unsupported/dependency and 0 for valid controls. Failed conversions must
leave no output JAR. The shared typed inventory additionally verifies six entries,
no truncation, the corruption diagnostic and both duplicate identities.

`fixture-manifest.json` records source provenance and input hashes.
`qualification-<UUID>/qualification.json` records the actual commands, CLI library
hashes, mapping manifest hash, exit codes, diagnostics and output hashes. The
original and all six fixture hashes are rechecked after qualification.

This is static qualification only. It does **not** prove public-JAR loading,
Minecraft UI behavior, cancellation timing, write permission failures,
installation, restart, recipe behavior or assets loaded in the game. The valid
control is intentionally tiny, so a human cancellation click can lose the race
to completion. A separate deterministic cancellation test or clearly observed
queued cancellation is necessary; a completed conversion is not a cancelled one.
