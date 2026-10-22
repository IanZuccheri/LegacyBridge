# LegacyBridge: player installation and optional local data setup

Status: experimental `0.1.1-SNAPSHOT` candidate, `simple-content-v1` only.
The owner authorized free source publication on 2026-10-22 under GPL-3.0-only
with the Minecraft/Forge linking exception; see [publication scope](PUBLICATION_2026-10-22.md).
This guide describes the implemented workflow, not stable runtime qualification
or permission to redistribute arbitrary converted mods. Artifact-bound native
validation gates remain open. The historical [P6 public-only scenario](PUBLIC_ONLY_END_TO_END_VALIDATION.md)
used explicit local data and its recorded older hash; it does not certify the
new included-data default. See [embedded simple-content data](EMBEDDED_SIMPLE_CONTENT_DATA.md)
for implementation details. The later [current-hash qualification](PUBLIC_CURRENT_QUALIFICATION.md)
joins the accepted finite blank-data UI workflow, native negatives and separate
static/server evidence; public-client mid-commit recovery and other gates remain open.
That evidence is now historical: the [job-receipt correction](JOB_INSTALL_RECEIPTS.md)
and [read-only history API](JOB_HISTORY_READ.md) change the product JAR and require
separate native requalification. The later [read-only history screen](UI_HISTORY.md)
is implemented with headless checks; these do not prove the new artifact has run
in a client or dedicated server.

## 1. Use the correct instance and artifact

Use **Minecraft 1.20.1, Forge 47.4.23 and a 64-bit Java 17 runtime**. The patch
version matters: other Forge 47.x versions are not currently certified. The
legacy reference is Minecraft 1.16.5 / Forge 36.2.42; this is not blanket support
for all Forge 36.x mods.

Create a separate test instance through your launcher. In this guide,
`<gameDir>` means that instance's actual game directory, not necessarily your
global `.minecraft` directory. Keep your existing worlds and unrelated profiles
out of the initial test. Back up a world before testing converted content in it;
LegacyBridge performs no world-data migration.

With Minecraft closed, install **only `LegacyBridge.jar` as the LegacyBridge
component** in `<gameDir>/mods`. The developer build produces it at
`legacy-product/build/libs/LegacyBridge.jar`; no public download is assumed by
this guide. Do not install module JARs, a CLI distribution, fixtures, validation
harnesses or QA/probe JARs as dependencies.

Do not install the old standalone `legacy-runtime` alongside the public JAR.
Both use the mod ID `legacybridge_runtime`. If migrating an existing test
instance, replace that old runtime while Minecraft is closed and preserve your
backups. Converted outputs must match the exact `0.1.1-SNAPSHOT` adapter binding;
older unbound/mismatched conversions need reconversion, not renamed files.

Converted mods are separate JARs. Keep LegacyBridge installed when using them.
On a dedicated server, install the same public JAR and the intentionally chosen
converted mods with the exact target platform. No conversion menu exists on the
server, and a mapping bundle is not required merely to load converted output.

## 2. Keep originals outside Forge discovery

Put candidate legacy JARs directly in `<gameDir>/legacybridge/input`, not in
`mods` and not inside another mod archive. The inventory is not a recursive
search of your computer or modpack.

If originals are already in `mods`, move them to the input folder while the
game is closed. LegacyBridge does not perform that move for you. An old JAR
left in `mods` may prevent Forge from reaching the title screen; the conversion
menu cannot repair a loader failure that happens before it opens.

Opening **Convert mods** initializes the reserved workspace areas as needed.
You may create the input directory beforehand to place your files. Use normal
local directories under your control, not symlinks, junctions or redirected
paths. Installation requires the reserved workspace and `mods` to support
same-filesystem hard links; unsupported providers are refused without an
overwrite/copy fallback.

| Instance location | Purpose and handling |
| --- | --- |
| `mods/LegacyBridge.jar` | The one required LegacyBridge implementation/runtime |
| `legacybridge/input/` | Original JARs; not loaded or overwritten by conversion |
| `legacybridge/staging/<operation>/` | Validated conversion results; not installed automatically |
| `legacybridge/reports/<operation>/` | Compatibility, transformation and conversion reports/job evidence |
| `legacybridge/cache/` | Regenerable data; optional advanced bundle location, not needed for included simple-content contracts |
| `legacybridge/transactions/<transaction>/` | Plans, checkpoints, managed payloads, update backups and recovery evidence; **not cache** |
| `mods/legacybridge-...jar` | Target mod installed only after explicit approval; discovered on a later launch |

Do not edit staged JARs, transaction payloads, journals or installed files while
the operation is running. Keep originals, reports and transaction evidence for
troubleshooting. There is no automatic transaction-history cleanup; deleting
that history can remove the evidence needed for managed update/restoration.

## 3. Use included simple-content data

**Leave the mapping field blank.** `LegacyBridge.jar` includes versioned finite
contracts for the existing `simple-content-v1` profile. Conversion within that
profile needs no separately prepared mapping/API bundle, developer cache or
mapping download. Analysis and inventory also need no separate mapping data.
The development CLI's default `convert` selects the same included data when no
mapping option or `LEGACYBRIDGE_MAPPING_BUNDLE` has been configured.

These are reviewed finite contracts, not generic mappings or a promise to convert
unknown code. The profile and exact adapter ABI are unchanged. Missing, damaged
or out-of-scope data is an error, not permission to invent a mapping.

An explicit trusted local bundle remains an **optional advanced override**. The
screen accepts an absolute directory path, not a mapping ZIP or a Forge JAR.
If supplied, that selection must validate: LegacyBridge does not silently switch
back to included contracts when the explicit path or data is invalid.

Broad `mappings` inspection and all five developer profiles
(`logic-preserving-v1`, `lifecycle-preserving-v1`, `food-item-v1`,
`datapack-recipes-v1`, `network-snapshot-v1`) still require explicit full local
data. They are not enabled in the player interface by leaving this field blank.
If you only need the supported player profile, continue to section 4.

No hidden mapping downloads. **Fully offline operation is not certified.**
Included conversion data does not prove Minecraft/Forge network isolation.
An empty user home and an absent Gradle classpath establish a different boundary from blocked network
access; a dummy offline test identity does not prove network isolation.

### Optional advanced preparation

There are two existing full-data setup routes, both advanced and explicit:

- Prepare from an already populated development environment for the exact two
  baselines, using `--from-development-cache` intentionally.
- Prepare from already available local mapping/API inputs using an explicit
  twelve-key mapping profile documented in [MAPPINGS.md](MAPPINGS.md#explicit-mapping-profile).

These commands belong to the separate development/setup CLI. They are not
commands accepted by Minecraft or by `java -jar LegacyBridge.jar`. The CLI is
not another mod that players install in `mods`. Preparing data may require
development tooling on the setup machine; consuming the prepared directory in
the game does not require Gradle or the original development API JARs.

For a developer with repository access, Java 17 and permitted local inputs,
build the CLI distribution from the repository root:

```powershell
.\gradlew.bat :legacy-converter:installDist --no-daemon --no-parallel
```

Gradle is an explicitly invoked development dependency/build tool, not an
in-game acquisition flow. Building the CLI alone does not prepare both legacy
and target API baselines. Obtain/configure those inputs separately under their
applicable terms; see [mapping inputs and provenance](MAPPINGS.md#local-inputs-and-provenance)
and the [portable-data audit](PORTABLE_MAPPING_DATA.md#redistribution-audit-and-release-gate).

Choose **one** preparation command, supplying your own existing paths. The
bundle's parent must exist; the final bundle directory must not exist, even if
empty. Preparation has no overwrite option.

```powershell
# Route A: explicitly read an already prepared pinned development cache.
.\legacy-converter\build\install\legacy-converter\bin\legacy-converter.bat `
  prepare-mappings --from-development-cache --output C:/LocalData/legacybridge-bundle
```

```powershell
# Route B: explicitly select existing local input files via a mapping profile.
.\legacy-converter\build\install\legacy-converter\bin\legacy-converter.bat `
  prepare-mappings --mapping-profile C:/LocalData/mappings.properties `
  --output C:/LocalData/legacybridge-bundle
```

`prepare-mappings` does not download or populate missing inputs. An incomplete
baseline must be corrected before export. No Minecraft/Forge implementation
JAR belongs in the exported bundle or the player's `mods` as a workaround.

A complete bundle contains exactly seven direct files:

```text
manifest.json
legacy-official.lbmap
target-official.lbmap
legacy-srg.lbmap
target-srg.lbmap
legacy-api.lbapi
target-api.lbapi
```

Keep this directory intact. Its manifest identifies `simple-content-v1`, the
exact source/target versions, schema, provenance and payload hashes. Hashes
detect changed data; they do not authenticate an unknown provider or grant
redistribution permission. Generated bundles remain
`LOCAL_USE_ONLY_NOT_APPROVED_FOR_REDISTRIBUTION`. Do not upload them as modpack
resources or treat a random shared bundle as trusted LegacyBridge data.

You may use your own prepared local bundle in another controlled test instance
under the applicable terms. `<gameDir>/legacybridge/cache/mapping-data` is a
suggested location, not an automatic lookup/download. Paste the **absolute
directory path containing `manifest.json`** into the screen's mapping field.
The original preparation-machine paths and Gradle cache are not needed for
this consumption step. Keep a way to regenerate data before clearing cache.

If you do not need this advanced data, leave the screen's mapping field blank
and use the included `simple-content-v1` path. `MAPPING_DATA_REQUIRED` for broad
inspection or another developer profile is a setup refusal, not a compatibility
verdict or a prompt for an automatic download. A supplied invalid override is
reported as an error and must be corrected or deliberately cleared by the user.

## 4. Analyze and convert

Open **Convert mods** from the title screen and choose **Refresh**. Search by
filename, mod ID or name; inspect the identity, detected source, file size and
SHA-256. A **Forge 1.16.5 candidate** is not yet proven convertible. Duplicate
IDs, invalid files, other/unconfirmed source profiles and already-converted
outputs are distinguished.

Select a row and use **Analyze** for static evidence. Check candidate rows for
conversion, leave the mapping field blank for included data and choose **Convert**.
Use a local bundle path only for a deliberate advanced override. Up to 32 jobs
including retained results fit in the bounded list; jobs process serially and
one refusal does not convert or discard the others. **Clear done** clears
finished screen summaries only; it does not delete any files.

Use **Jobs** and **Details** to inspect each result. Analysis,
conversion and ASM reports are separate. **Folder** opens the result's folder,
not the JAR as an executable. OS open requests can fail outside Minecraft;
use **Copy paths/details** if nothing opens. Reports can contain local paths;
review/redact them before sharing publicly. Do not send account credentials or
upload another author's mod without the necessary permission.

Keyboard controls include Tab between controls, Space to check a focused row,
and arrows/Page Up/Page Down/Home/End or the wheel for lists/details. Resize
preserves the screen's search, selection and mapping-field state.

Cancel conversion before publication when available and wait for its actual
terminal state. A late request may be refused because publication already
occurred; a fully staged result is not retroactively called cancelled. Closing
the conversion flow requests cancellation of queued/active cancellable work.
The in-memory job list is not restored on reopening; completed reports/JARs
remain on disk, and a new conversion creates fresh operation-owned output.

Choose **History** to read saved receipts separately from the current jobs.
Use **Refresh**, search and **Details** to inspect recorded outcomes one receipt
at a time; **Diagnostics** explains invalid, incomplete or ambiguous history.
Reading uses the bounded background coordinator, not the rendering thread. If
another operation is busy, wait and Refresh. Cancel keeps the previous snapshot.
Details are a captured snapshot and can be copied as text. Historical paths and
hashes are not revalidated by this view and cannot be opened or used to Apply.
PENDING_RESTART in history is not proof of current installation or activation.
The screen's native layout/keyboard/resize acceptance remains pending.

## 5. Apply for the next launch

Advanced inspection, separate from Apply: **Preview (adv.)** on a selected input
uses an explicitly supplied full local mapping/API bundle to inspect the finite
logic-plus-recipes combination. It displays operations, blockers and a copyable
report path. It does not generate a converted JAR or authorize installation.
Blank mapping data remains sufficient only for the ordinary simple-content
conversion path. See [inspection details and limits](PRODUCT_COMPOSITION_INSPECTION.md).

Conversion only writes staging/reports. To install:

1. Open **Apply...** to enter **Installation and recovery**.
2. In **Staged results**, explicitly check one to four converted results.
   Conversion selection is not installation approval.
3. Choose **Apply selected...** and review the confirmation. Add-only operations
   cannot overwrite an existing mod. The installer rechecks source/output
   hashes, profile/runtime bindings, paths, IDs and the represented dependencies.
4. Wait for the individual result and transaction state. **COMMITTED** or
   **Applied — restart through your launcher required** means installed for a
   later discovery pass, not active content or successful gameplay.

Continue converting if desired; that does not alter the loaded mod set. Normal
Apply leaves Minecraft running. Close Minecraft normally when ready and reopen
the same instance yourself through your launcher.

Alternatively, **Apply and close — reopen through your launcher** approves a
new selected installation and waits for that exact operation's safe result
before closing the game. It is not a shortcut that trusts an old successful
transaction, and it does not relaunch Minecraft. Do not expect a restart button,
automatic launcher authentication or automatic world reopening.

Installing/recovering while a world, integrated server or connection is open
is refused. Accepted installation/recovery cannot be cancelled, including by
closing its screen. Normal navigation out of this flow is blocked during work.
OS close cannot be vetoed: an orderly JVM shutdown guard waits for accepted
work, but forced termination, VM failure or power loss are not protected by
that guard. Do not force-close Minecraft to cancel an installation.

## 6. Managed updates and recovery

This is a restricted pending-installation updater, not a general updater for
mods already running in your instance. It requires the exact existing
LegacyBridge-managed file identity and receipt, matching mod ID and a strictly
newer comparable numeric version. Same-version replacements, downgrades,
unknown/copied files and loaded IDs/files are refused. A matching filename or
even matching bytes alone does not prove managed ownership.

For an eligible **unloaded** prior result, check one new staged result, switch
to **Transactions**, select the earlier managed journal and choose
**Link update...**. Confirm that association, then separately approve Apply.
Linking alone changes no installed file. An update may refer to a journal with
up to four prior entries; only the verified matching mod ID may be replaced.
Updates that could leave a mandatory dependent without its required mod during
interruption are refused. No dependent multi-JAR atomic upgrade is implemented.

**Inspect journals** reads persistent evidence asynchronously, even when a new
screen has no retained conversion jobs. A batch can be partly installed:
per-file commits are journaled, not one atomic filesystem transaction. After a
failure, do not assume every file is absent and do not blindly apply again.

- **Resume...** explicitly continues a valid unfinished transaction after
  revalidation.
- **Restore...** explicitly attempts the recorded prior safe set: remove only
  matching owned/unloaded new files and, for an update, restore the valid prior
  managed identity. It does not roll back worlds.
- **MANUAL**, changed/missing identity, invalid journals, loaded files or
  unsupported dependency situations require review; LegacyBridge will not
  overwrite an unknown file to manufacture recovery.
- **ARTIFACT_SUPERSEDED** means a later approved update owns the change. Inspect
  and resolve that successor first, rather than undoing its predecessor.

Keep transaction directories and independent update backups. A backup preserves
bytes but is not permission to replace an unknown destination. History is
bounded to 32 transaction directories; no automatic destructive cleanup is
provided. If that limit or a conservative layout/permission check refuses an
operation, inspect its diagnostic instead of deleting recovery evidence blindly.

Recovery runs from the menu, not before Forge discovery. If the instance cannot
start far enough to reach it, the menu cannot fix that startup failure. Resolve
the problem outside the running game with the recorded diagnostics and an
appropriate manual recovery plan; no bootstrap service or silent repair is
installed. A mod loaded on a later launch cannot then be updated/restored by
this session's in-game installer.

## 7. Supported content and what success means

The exact [simple-content-v1 specification](SIMPLE_CONTENT.md) is authoritative.
It accepts one JavaFML declaration, a finite DeferredRegister/Supplier grammar,
plain Item/Block/BlockItem objects, STONE material/sound and bounded basic
properties. It rejects custom subclasses and arbitrary behavior, including
some otherwise harmless coding styles. Stack sizes are 1–64; nondefault
rarity, durability and fire resistance are not supported. Creative membership
is absent, MISC (target Ingredients) or BUILDING_BLOCKS only.

Accepted resources are tightly validated shaped/shapeless recipes, restricted
own/Forge item/block tags, translations, static PNGs, vanilla-template models,
simple blockstates and sound-event aliases to audited vanilla events. Custom
audio files, animation, unsupported model features and unknown vanilla or
third-party references are refused. Source `data/minecraft/tags/**` declarations
are outside the profile.

Entities, TileEntity/BlockEntity, rendering extensions, custom GUIs/shaders,
worldgen, dimensions, packets, capabilities, mixins, coremods, access
transformers, reflection, custom event behavior, loot tables and custom recipe
serializers remain unsupported. Unsupported content aborts conversion without
a new output JAR. The tool never executes the original mod's classes during
analysis/conversion and is not a general malware scanner.

Accepted IDs, names and resource content are preserved as specified; target
metadata, pack format, generated registration/adapters and the required STONE
pickaxe tag are explicit changes. Read the TransformationLog for differences.
No score, `CONVERTED`, `COMMITTED` or `PENDING_RESTART` state proves gameplay.
After reopening, separately check Forge discovery, item/block registration,
recipes and visible assets in a disposable world. No real-player world,
third-party modpack or universal compatibility claim follows from the fixture.

## Release gates and evidence used

The historical P5 candidate is identified in
[TRANSACTIONAL_INSTALLATION_VALIDATION.md](TRANSACTIONAL_INSTALLATION_VALIDATION.md).
It records 502 tests, 501 passed and one Windows symlink-privilege skip. P5
ordinary client/server scenarios used the public JAR **plus a separate QA mod**;
they are neither a public-only player installation nor a real launcher restart.
The separate [P6 record](PUBLIC_ONLY_END_TO_END_VALIDATION.md) documents that
then-final artifact with no QA mod, Java agent or development classpath. Both
records used explicit local mapping data; neither old hash validates the new
embedded-data path. The later [current-hash qualification](PUBLIC_CURRENT_QUALIFICATION.md)
records the finite embedded-data UI workflow and negatives, reusing only matching
static/server hashes. Keep it distinct from the historical P5/P6 observations below.
External mouse/keyboard input drove real native screens: inventory, repeatable
conversion, corrupt/duplicate/unsupported/missing-dependency refusals, accepted
cancellation, and a real Windows staging write-denial followed by recovery.
Only explicit Apply added the chosen result; Apply and close exited the client.
A separately invoked ordinary client then discovered the installed fixture,
proved the eight-versus-nine-ingredient crafting behavior and input consumption,
placed the crafted block, and rendered assets and English/Portuguese names.
The separate public-plus-converted-JAR server reached readiness and passed its
native item/block/tag, reload and save/exit checks without a QA mod. Client
crafting, server readiness and next-launch discovery are separate observations.

Public-only client termination during commit remains untested. Separate-JVM
halt/recovery tests exercise specific crash boundaries, not that Minecraft
scenario or power loss. Fully offline networking, automatic launcher restart,
arbitrary modpacks/launchers and complete accessibility coverage are not certified.

Public release additionally requires closure of the
[packaging-rights review](PACKAGING_RIGHTS.md), including SRG Utils LGPL
source/replacement and project-license obligations, review of final included
data/provenance/notices, and validation tied to the changed public artifact.
A generated full local bundle is not approved for upload. Conversion does not
grant rights to redistribute input mods or assets.
This guide does not provide new legal clearance.

This guide was checked against [README](../README.md),
[product architecture](PRODUCT_ARCHITECTURE.md),
[simple-content-v1](SIMPLE_CONTENT.md),
[packaging architecture](PACKAGING_ARCHITECTURE.md),
[historical P3 packaging evidence](PACKAGING_VALIDATION.md),
[portable-data rules](PORTABLE_MAPPING_DATA.md), [mapping profiles](MAPPINGS.md),
[transaction semantics](TRANSACTIONAL_INSTALLATION.md),
[P5 evidence](TRANSACTIONAL_INSTALLATION_VALIDATION.md),
[P6 public-only evidence](PUBLIC_ONLY_END_TO_END_VALIDATION.md), and the actual native
client controls/English translation keys. Build, bytecode, GUI, installation,
fresh discovery, content behavior and launcher/platform coverage remain separate
evidence categories. This documentation work runs no build or game and publishes
nothing.
