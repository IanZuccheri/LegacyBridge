# P6: final public artifact, ordinary Forge, external UI validation

Current-hash continuation (2026-09-07): [embedded-data native UI acceptance](EMBEDDED_SIMPLE_NATIVE_UI_VALIDATION.md)
records the new public hash, actual conversion/Apply/next-launch/crafting and
translations. The P6 record below remains historical, not transferred to new bytes.
LB-06 / PUBLIC-CURRENT-E2E still needs the remaining negative/qualification gates.
The [new finite negative continuation](PUBLIC_CURRENT_NEGATIVE_VALIDATION.md)
now records current-hash refusals, write recovery and accepted cancellation;
public-client interruption and wider qualification still are not accepted.

Status: **experimental/Beta; not approved for publication**. Validation date:
2026-09-06, Windows, Java 17.0.12, Minecraft 1.20.1 / Forge 47.4.23.
The source baseline remains Minecraft 1.16.5 / Forge 36.2.42. No conversion
category, runtime adapter, dependency version or public mod implementation was
changed for this validation. `simple-content-v1` is still a finite grammar,
not general compatibility with item/block mods.

This record separates real public-only execution from the earlier P5 runs,
which included a QA mod. P6 used **no QA mod, Java agent, in-process probe,
development runClient classpath or replacement public JAR**. External Win32
mouse/keyboard input drove native screens; screenshots are actual captured
pixels. Vanilla commands prepared only a new disposable test world.

## Artifacts and preparation

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `legacy-product/build/libs/LegacyBridge.jar` | 975498 | `00cc7ebde1279c1ac3cc6ac0a54498e4598ca4f45700a6799f915ce9dddea991` |
| Original `legacybridge-example-1.16.5-1.0.0.jar` | 9534 | `00dd689f53f9631ecf4a8e84da142eb21051e2ca33f1c05403e4060e15b10947` |
| UI-converted and UI-installed fixture | 6979 | `541cc64b44b97d0e4e79a193dd2ce3a86a5feba6e15714d0ee24ad63751492bb` |

The mandatory implementation is one public JAR, retaining mod ID
`legacybridge_runtime` and exact adapter ABI `0.1.1-SNAPSHOT`. Converted mods
are their own output JARs, not a second LegacyBridge implementation.

Disposable installations: `build/p6-public/client` and `build/p6-public/server`.
The client used the normal pinned Forge profile and verified installer outputs;
the server used the normal `win_args.txt` and `forgeserver` target. Preparation
copied 3,679 verified files from a previously prepared ordinary installation
(including 3,575 asset objects), downloading zero new files in this preparation.
This is not a claim that Forge installation never needs downloads.

The client initially had only `mods/LegacyBridge.jar` and an empty input folder.
After the real title screen appeared, the genuine legacy fixture was copied
into `legacybridge/input`. It was never put in `mods` or executed.

Conversion consumed a deliberately selected local seven-file mapping/API bundle:
35,223,698 bytes, manifest SHA-256
`bac4e0018cc51bd13a7e5ae19116d04cb6b4bc271d3aecfba5e4ac0d27df5c69`.
The path was entered in the native screen. This local-use bundle was prepared
previously and is **not approved for redistribution or embedded in the JAR**.
Its recorded Gradle-source provenance describes export inputs, not a runtime
lookup of those caches. No old Minecraft/Forge implementation JAR was supplied
to conversion to replace the bundle.

## Public-only positive scenario

Evidence paths below are relative to `build/p6-public/evidence/`.

| Stage | Actual observation | Evidence |
| --- | --- | --- |
| Initial discovery | Title screen reached with three loaded mods: Minecraft, Forge and LegacyBridge | `01-main-menu-public-only.png` |
| Input inventory | Native screen detected the genuine fixture's ID, version, size and SHA-256 | `02-fixture-detected.png` |
| UI conversion | Actual mapping-resolution progress followed by a staged, not-installed result | `04-conversion-progress.png`, `05-converted-staging-only.png` |
| Continue conversion | A second native job completed with identical output; `mods` still contained only LegacyBridge | `06-continued-conversion-state.png`, `20-window-visible-main-menu.png`, launch-1 trace |
| Screen closure | Returning to title discarded the in-memory job list, but retained reports and staging. Reopening and reconverting remained usable after the write-denial test | `21-reopened-converter.png`, `22-reconversion-after-screen-close.png` |
| Explicit approval | Only one current baseline result was checked; the confirmation accurately described add-only installation and manual relaunch | `23-install-approval-selection.png`, `24-apply-and-manual-close-confirmation.png` |
| Safe close | Native Apply and close committed the exact result, then the client logged `Stopping!` and exited | launch-1 logs, transaction V2 checkpoints |
| Next launch | A separately invoked ordinary Forge client showed four loaded mods and the fixture in Forge's native mod list | `25-next-launch-title.png`, `26-forge-mod-list.png`, `27-forge-recognizes-converted-fixture.png` |
| Item | Vanilla `/give` recognized `legacybridge_example:bridge_ingot`; the native item model and English name rendered | `32-native-item-give-and-crafting-table.png`, `35-nine-ingots-native-recipe-result.png` |
| Recipe negative control | Eight individually placed ingots left the crafting output empty | `34-eight-ingots-no-recipe-result.png` |
| Recipe success | The ninth ingot produced one block; taking it consumed all nine inputs | `35-nine-ingots-native-recipe-result.png`, `36-crafting-consumed-inputs-block-in-inventory.png` |
| Server-authoritative inventory | After crafting, vanilla `/data get entity @s Inventory` returned exactly one `legacybridge_example:bridge_block`, count 1 | launch-2 latest log |
| Block and assets | That crafted block was placed manually in the world, with its actual turquoise model/texture; a vanilla execute-if-block check succeeded | `37-crafted-block-placed-native-textures.png`, `P6_CRAFTED_BLOCK_PLACED_OK` in launch-2 log |
| Portuguese translations | The native language screen switched to Brazilian Portuguese and reloaded resources; item/block tooltips rendered `Lingote da Ponte` and `Bloco da Ponte` | `44-portuguese-visible.png`, `46-pt-br-ingot-tooltip.png`, `47-pt-br-block-tooltip.png` |
| End of client test | The disposable world saved all dimensions, returned to the Portuguese title screen, and the native Quit button closed the process normally | `49-portuguese-title-after-save.png`, launch-2 logs |

The committed transaction is `c2740813-06f0-4bde-8456-ddc449891c23`.
Its plan and checkpoints have schema version 2 and identify the original/output
hashes, profile, runtime version and exact installed filename. `mods` gained
only `legacybridge-legacybridge_example-c2740813-06f0-4bde-8456-ddc449891c23-0.jar`.
Unapproved staged control/duplicate conversions were not installed.

Persisted schemas observed: CompatibilityReport 2, TransformationLog 1,
conversion report 2, job receipt 1 and transaction plan/checkpoint 2. The
conversion job receipt remains `CONVERTED`; this run did not persist a separate
`PENDING_RESTART` job receipt before automatic closing. The evidence collector
therefore retains `NO_PERSISTED_JOB_RECEIPT_STATE:PENDING_RESTART` as a missing
observation, rather than inventing it. The installation proof is the distinct
committed journal and exact installed artifact; activation proof is the next
launch and native behavior, not a rewritten conversion receipt.

Launch 1's class-loading trace contains neither the original fixture class nor
the generated fixture class. Launch 2 resolves
`dev.legacybridge.generated.legacybridge_example.ConvertedMod` from the installed
JAR; it does not resolve `dev.legacybridge.fixture.LegacyBridgeExample`.
All 12 original nonempty `assets/` and `data/` entries compare byte-identically
with the converted output, including recipe and both translation files.
Byte preservation and actual UI/world rendering are separate evidence.

The client displayed the original `en_us` names `Bridge Ingot` / `Bridge Block`
and the original `pt_br` names after the native resource reload. No replacement
language file or custom screenshot overlay was introduced. The final title
button also rendered the product's Portuguese `Converter mods` translation.

Only after the crafting/placement evidence was recorded were extra individual
item/block stacks given for translation inspection. They were not the source
of the earlier crafting result. The only world touched was newly created
`P6 Content QA`, a peaceful superflat test world with commands enabled.

## Negative scenarios and their evidence boundaries

| Scenario | Actual result | Evidence / boundary |
| --- | --- | --- |
| Corrupt JAR | Inventory `INVALID`, `MALFORMED_ZIP`; conversion unavailable | `08-negative-inventory-refreshed.png`, `09-corrupt-jar-details.png` |
| Duplicate mod IDs | Both `p6_duplicate` inputs marked duplicate and unselectable for conversion | `10-duplicate-modids.png`; installer duplicate/installed-ID regressions separately rerun |
| Unsupported resource | `BLOCKED`, `UNSUPPORTED`, `RESOURCE_OUTSIDE_SUBSET`; worldgen resource rejected and no output published | `12-unsupported-resource-report.png`, conversion report schema 2 |
| Missing dependency | `BLOCKED`; required third-party dependency `p6_missing_dependency` needs manual migration | `13-missing-dependency-report.png`; installer dependency closure regressions separately rerun |
| Accepted cancellation | Native job reached `CANCELLED` and published no output | `16-cancel-second-attempt.png`; an earlier too-early cancellation attempt completed normally and is **not** counted as a cancellation success |
| Real write failure | A temporary Windows deny-create rule on this owned `staging` directory produced `FAILED / IO_ERROR`; details stayed accessible | `17-real-write-denial.png`, `18-write-denial-details.png` |
| Permission recovery | The temporary deny rule was removed; zero deny rules and native create/delete of one fresh empty proof directory succeeded; later native reconversion succeeded | `22-reconversion-after-screen-close.png`; originals and unrelated ACLs untouched |
| Process interruption / recovery | Two real separate-JVM `Runtime.halt(81)` tests passed: after first publication, resume in another JVM; after retirement, restore prior identity in another JVM | `TransactionalProcessCrashTest` XML, build log; **test-controlled boundaries, not a forced kill of the P6 public-only client** |
| Windows locked files | Actual deny-read and deny-delete sharing handles passed refusal and explicit recovery tests | `TransactionalWindowsLockTest` XML; separate from GUI validation |

The ACL test's original SDDL string-equality assertion reported a mismatch after
restoration. That string comparison is not the evidence of successful cleanup:
subsequent inspection found zero deny rules, native create/delete succeeded,
and a new conversion completed. No permission widening or remaining test deny
rule is asserted merely from `Set-Acl` returning successfully.

Synthetic installer boundary failures cover additional partial writes and
repeated recovery. They do not certify physical disk-full behavior, sudden power
loss, every filesystem/provider, or a crash during every native UI operation.
In particular, **public-only client termination mid-commit remains untested**.

Negative fixture generation is documented in
[`scripts/P6_NEGATIVE_FIXTURES.md`](../scripts/P6_NEGATIVE_FIXTURES.md).
Its positive control and five negative cases were deterministically derived by
static rewriting of this project-owned genuine fixture, then qualified with
the development CLI. No original mod class was executed by that generation.

## Dedicated server: same public artifact

[`scripts/Test-P6PublicServer.ps1`](../scripts/Test-P6PublicServer.ps1) launched
the normal dedicated server with exactly the public JAR and a hash-verified copy
of the artifact applied by the native client. No QA JAR, mapping bundle or
client interface was supplied to the server. Java user home was initially empty.

The external stdin runner observed `Done (14.448s)!`, then verified actual
chunk loading, the native block, its block tag, and a summoned native item with
its serialized registry ID. A data-pack reload returned with recipes loaded;
**reload alone is not crafting proof**. The server saved all dimensions and
exited with code 0 without forced termination. Its class-loading trace contains
no development-cache source or LegacyBridge client/validation class.

Machine-readable receipt:
`build/p6-public/server/p6-public-server-receipt.json` (`PASSED`, schema 1).
Separate stdout, stderr, command and class-loading logs are hash-bound there.
This loopback `online-mode=false` test is not authenticated multiplayer,
launcher integration or offline-network certification.

## Build, static validation and cacheless engine equivalence

The root suite recorded **502 tests: 501 passed, zero failures/errors, one
existing Windows symlink-privilege skip**. By module: converter 358 (one skipped),
product 82, runtime 30, workflow 32. Source fixture tests are not added to this
count because that separate source-version build was not rerun in P6.

The full serial command was:

```powershell
.\gradlew.bat build -PverifyMappingEnvironment=true --rerun-tasks --no-daemon --no-parallel --max-workers=1 --console=plain
```

The baseline rerun log is `build/p6-public/build-baseline.log`; the closing rerun
uses `build/p6-public/build-final-retry.log`. The preceding `build-final.log`
records an incomplete attempt: PowerShell's Stop error preference treated a
native javac deprecation note on stderr as a terminating wrapper error. That
attempt is not counted as a successful build. The retry merges native output
before PowerShell and checks the actual Gradle process exit code.
No Gradle build ran concurrently with a
Minecraft client or dedicated server. Package/bytecode/reference verification
belongs to this level, not the native crafting proof above. External QA helpers
were also compiled/parsed separately; they are not included in the public JAR.

After both game sessions stopped, the existing cacheless-engine probe was
executed again with the explicit local bundle, a 512 MiB heap, isolated CLI/service
files and empty redirected caches. Its self-tested Java 17 test-only guard denied
external filesystem access, DNS/network, subprocesses and `System.exit`.
CLI/service analysis, mapping and conversion JSON pairs were byte-identical;
the converted SHA-256 was again `541cc64b44b97d0e4e79a193dd2ce3a86a5feba6e15714d0ee24ad63751492bb`.
The original was unchanged, and unexpected external-file/network attempts were
both zero. Evidence: `build/p6-public/cacheless-engine/cacheless-engine-evidence.json`
and the six paired reports alongside it.

This last probe uses the development CLI and a test guard, **not a public-only
Minecraft process**. It supplements the ordinary installation evidence, does
not retroactively sandbox those game sessions, and does not certify offline
Minecraft. Java's deprecated SecurityManager warning is expected for this
Java-17-only validation helper; no such guard is installed in the product.

## Reproduction and remaining gates

The external helpers are development validation tools, not shipped mod content:

```powershell
# Existing permitted local Forge/assets preparation; no account/profile reads.
.\scripts\Prepare-ProductionClient.ps1 -ValidationRun p6-public -VanillaDirectory <prepared-instance>
.\scripts\Start-ProductionClient.ps1 -ValidationRun p6-public -JvmArgument @('-Xlog:class+load=info:file=client-classloading.log') -Launch

# Server runs only after the client has stopped and its isolated files are prepared.
.\scripts\Test-P6PublicServer.ps1
.\scripts\Test-P6PublicServer.ps1 -Launch

# Run only after all game sessions have finished; destination must be new.
.\scripts\Write-P6Evidence.ps1 -Collect -ManifestName p6-evidence-final.json
```

Preparation and artifact paths must be adapted deliberately; these are not
instructions to run multiple clients/servers concurrently. Preserve prior
logs before another launch. The evidence index hashes reports, artifacts,
screenshots and session-specific traces; it does not infer screenshot success
or certify a release from file existence.

Screenshot `19-current-screen.png` is an **invalid QA capture**, retained for
diagnostics: the window was partially outside the monitor and included the
taskbar/black area. The external helper was corrected to refuse that condition;
the real window was repositioned, not the image edited. Similarly, progress or
pre-refresh captures are not substituted for terminal-state evidence. No mockup,
generated image or synthetic in-game overlay is used as proof.

The ordinary installations used their own libraries/assets, explicit local
mapping data and redirected empty Java user homes. Observed class sources do
not include `.gradle`; that observation is **not** a filesystem access sandbox.
Outbound networking was not disabled. Dummy local credentials and copied
assets do not establish offline operation; fully offline play is not certified.

No automatic launcher restart provider is enabled. The observed action is
**Apply and close — reopen through your launcher**, followed by a separately
requested test launch. No account secrets or real authentication arguments
were collected. No claim covers arbitrary launchers, macOS/Linux, accessibility
matrices, large modpacks, authenticated multiplayer, existing-world migration,
or third-party mods beyond the actual supported grammar.

Public release remains gated by the
[mapping-data policy](PORTABLE_MAPPING_DATA.md) and
[packaging/licensing review](PACKAGING_RIGHTS.md), including a practical approved
player data-acquisition route. The local mapping bundle, fixtures and harnesses
must not be uploaded as product dependencies. The English
[player guide](PLAYER_INSTALLATION.md) and [CurseForge draft](../CURSEFORGE.md)
are local preparation only. **Nothing was published or uploaded.**
