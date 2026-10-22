# Authorized public corpus intake

`rights-candidates.json` pins three exact publisher releases and their immutable
release-associated license evidence. It is a provenance input to the corpus,
not a list of supported mods. The separate corpus observations decide whether a
particular artifact can be analyzed, converted, validated or run.

Only official publisher Modrinth release/API/CDN endpoints and the source
repositories linked by those projects were used. No account, third-party mirror,
private upload or CurseForge scraping was involved. The upstream public releases
were acquired on 2026-09-06 solely for local static analysis. Their original
classes have not been executed by intake.

## Exact permission basis

| Release | Evidence tied to this release | Local permission basis |
| --- | --- | --- |
| Clumps 6.0.0.28 | [Exact release](https://modrinth.com/mod/clumps/version/UutzgwbZ) changelog links commit `34c57ca0bd068655a63d99eabd215219432c32d4`; [license at that commit](https://github.com/jaredlll08/Clumps/blob/34c57ca0bd068655a63d99eabd215219432c32d4/LICENSE.md) | MIT allows use, copying and modification; copyright and permission notice retained |
| Controlling 7.0.0.29 | [Exact release metadata](https://api.modrinth.com/v2/version/Z7UMwzSN) changelog links commit `1975807fd663f62d5631f94b9dee19372a5a2d08`; [license at that commit](https://github.com/jaredlll08/Controlling/blob/1975807fd663f62d5631f94b9dee19372a5a2d08/LICENSE) | MIT allows use, copying and modification; copyright and permission notice retained |
| AppleSkin 2.5.1+mc1.16.4 | [Exact release metadata](https://api.modrinth.com/v2/version/633NTwhH), tag `v2.5.1` / commit `5bedbcc501848f3a22a650f1f661e57d6973af83`; [license at that commit](https://github.com/squeek502/AppleSkin/blob/5bedbcc501848f3a22a650f1f661e57d6973af83/LICENSE) | Unlicense explicitly permits use, copying and modification of source and compiled binaries |

Full upstream notices are preserved byte-for-byte in `../licenses/`, with
SHA-256 values in the manifest. The acquired JARs do not contain their own full
license files, so local acquisition stores an unchanged notice sidecar too.
This is engineering provenance, not a legal opinion about every jurisdiction or
permission to publish an arbitrary derived artifact. No binaries are
redistributed by this corpus operation, even where their license could allow it.

Clumps and Controlling's exact release changelogs provide source association, not
reproducible build attestations. AppleSkin's exact source properties target
Minecraft 1.16.4 / Forge 35.1.4 while its publisher also lists compatibility with
1.16.5. The corpus must retain both facts; it must not invent a Forge 36 build or
change source-profile admission to improve the result.

## Explicit local acquisition

Read the manifest and pinned notices, then run from the repository root:

```powershell
.\scripts\Acquire-PublicCorpusCandidates.ps1 -AcceptReviewedLicenses
```

This optional development operation is not called by the product, converter,
build, corpus validation or tests. Without the explicit switch it starts no
download. It uses no Minecraft/Forge code, developer cache, hidden download or
authentication data. HTTPS certificate validation remains enabled.

The script verifies the immutable license hash before requesting each binary,
limits a license response to 64 KiB and a JAR to 10 MiB, disallows redirects,
checks the exact publisher byte size and SHA-512 and the corpus SHA-256, and then
creates files with no-overwrite semantics. Reparse points in acquisition
subdirectories are rejected. It creates only:

```text
build/corpus-intake/public/<project>/
  <publisher filename>.jar
  UPSTREAM-LICENSE.txt
  acquisition.json
```

`build/` is ignored and these files are not fixtures, source sets or packaging
inputs. Existing files cause a refusal, including after an interrupted attempt;
inspect and explicitly manage such local leftovers before attempting a new
acquisition. Do not point this procedure at a player's installation. Private mods
require a separate explicit authorization record and remain outside this public
intake list.

The three projects were selected as small, rights-verifiable, structurally varied
inputs. This is a convenience sample, not a representative benchmark. Neither
their download counts nor the number of resolved bytecode symbols estimates how
many real mods LegacyBridge can port.

## Intake checks performed

- Each official artifact's advertised byte size and SHA-512 matched the download.
- Each local JAR's SHA-256 matches the locked value in the manifest.
- All three full license snapshots match their immutable upstream SHA-256.
- Bounded ZIP metadata reads confirmed each binary's mod ID and exact manifest
  version; no input class was loaded.
- `git check-ignore` confirms all three binary paths are ignored.
- The explicit-permission and no-overwrite refusals passed; PowerShell parsing
  passed. Product build/tests are run serially by the main validation workflow.

No client, dedicated-server, behavior, multiplayer, conversion or bytecode
validation result is implied by these intake checks.
