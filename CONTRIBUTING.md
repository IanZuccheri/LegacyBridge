# Contributing to LegacyBridge

Help expand compatibility one reproducible behavior at a time. Start with the
[development contract](docs/contract/README.md), its
[current status](docs/contract/status.json) and the
[project checkpoint](docs/PROJECT_STATUS.md). The default player profile is
`simple-content-v1`; experimental adapters or developer profiles do not become
public support just because they compile.

## Useful contributions

- A small, original Forge 1.16.5 fixture demonstrating one missing pattern.
- An exact source/target API audit, including descriptors, invocation kinds,
  behavior differences and provenance.
- Positive and negative tests for conversion, cancellation, malformed data,
  dependencies, installation or recovery.
- Independent behavior checks in disposable source and target installations.
- Better reports, accessibility, English/Portuguese text and documentation.

Use issues to discuss a large feature before starting. Link the applicable
contract item and state the smallest behavior you intend to implement. A
rejection with an accurate explanation is preferable to a guessed translation.

## Development setup

Use a 64-bit JDK 17 and the checked-in Gradle 8.8 wrapper. Versions are pinned:
source Minecraft 1.16.5 / Forge 36.2.42; target Minecraft 1.20.1 /
Forge 47.4.23; ForgeGradle 6.0.54. Do not casually upgrade them.

Run builds **serially**, from the repository root:

```powershell
.\gradlew.bat build --no-daemon --no-parallel --max-workers=1
```

On a Unix-like shell, use `./gradlew` with the same arguments. The build can
download its declared development dependencies on first use; it does not launch
Minecraft. Do not commit dependency caches or game artifacts. Java toolchain
auto-download is disabled, so install JDK 17 explicitly.

For the standalone development CLI:

```powershell
.\gradlew.bat :legacy-converter:installDist --no-daemon --no-parallel --max-workers=1
.\legacy-converter\build\install\legacy-converter\bin\legacy-converter.bat analyze path\to\mod.jar
```

The public artifact is `legacy-product/build/libs/LegacyBridge.jar`.
Do not treat the thin module JARs or validation probes as release files.

Some real-environment tests require explicitly supplied local mappings/API
inputs and fixtures. Record skipped tests and unavailable inputs honestly;
a default build is not a substitute for these gates. See [validation](VALIDATION.md),
[fixtures](fixtures/README.md), [mappings](docs/MAPPINGS.md) and
[portable data](docs/PORTABLE_MAPPING_DATA.md). Full local data remains
`LOCAL_USE_ONLY_NOT_APPROVED_FOR_REDISTRIBUTION`; do not commit it.

## Conversion rules

1. Never load or execute original mod classes during analysis or conversion.
2. Rewrite only code owned by the authorized input operation. Do not modify
   Minecraft, Forge, Java or platform-library classes.
3. Use real pinned APIs and exact rules. An unresolved reference must block the
   affected conversion, not pass unchanged by accident.
4. Bind every transformation to an implemented operation/adapter and document
   Legacy API, Target API, Transformation, Behavior differences and Known limitations.
5. Preserve mod identity, names, content and behavior where the supported
   contract permits; record necessary differences in the TransformationLog.
6. Refuse incomplete output. Preserve schema versions, adapter identities and
   existing profiles unless an explicit migration is reviewed.
7. Cover positive, negative and boundary cases. Test callbacks, side, thread,
   lifecycle, ordering and persistence wherever the change depends on them.

## Evidence and game tests

Build, ASM validation, offline linkage, game startup, observable behavior and
multiplayer are separate results. Record exact versions, artifact SHA-256,
commands, outcomes and missing gates. Never use resolved symbols or test counts
as a percentage of compatible mods.

Run native tests only in explicitly chosen disposable installations, after
separate agreement about launching clients/servers. Never open someone else's
active game, change their worlds or weaken resource guards to make a test run.
Screenshots must come from the real tested interface, not mockups. Static
validation must not be labeled native acceptance.

Run relevant tests after changes, then the serial build/packaging checks.
Keep existing failures and skipped cases visible in the PR. Do not publish a
fixture or candidate as broadly supported before its required gates are met.

## Privacy and permissions

Submit code, fixtures and assets only when you have permission to contribute
them under the applicable terms. Prefer original minimal reproductions and
public publisher/source links. Do not attach third-party binaries without
redistribution permission, even when a mod is free to download.

Do not commit authentication tokens, account/session files, private mods,
personal logs, worlds, full Mojang mappings, old Minecraft/Forge implementations,
or local mapping/API bundles. Review reports for paths and other personal
information before sharing. Keep local inputs in ignored private directories.

If a potential security issue could affect users, do not post secrets or a
working malicious payload in a public issue; use GitHub's private vulnerability
reporting if enabled, or ask for a private contact without disclosing the exploit.

## Licensing contributions

Original project code is GPL-3.0-only with the approved narrow
[Minecraft/Forge linking exception](LINKING_EXCEPTION.md); see
[LICENSING.md](LICENSING.md). Contributions intended for inclusion should be
offered under those same terms, with existing third-party notices preserved.
Do not add unrelated code whose license or provenance you cannot establish.

There is no 5% share or mandatory fee. This does not grant rights to
third-party mods or automatically change the license of conversion output.
