# LegacyBridge open-source publication

On 2026-10-22 the owner requested free public development on GitHub and selected
GPL-3.0-only with a narrow Minecraft/Forge linking exception. The earlier 5%
revenue-share proposal is abandoned. See [licensing](../LICENSING.md).

Source repository: https://github.com/IanZuccheri/LegacyBridge . The owner will
submit CurseForge manually; the assistant must not submit or claim approval there.
The prepared release is experimental/Beta, not a completed conversion roadmap.

## Current scope

The single player-facing JAR includes analysis, conversion engine, matching runtime,
client menu, bounded queue, reports and transactional next-launch installation.
The public conversion route admits only simple-content-v1. The five broader
development profiles, private equipment experiments and contract milestones do
not broaden that UI admission. Unsupported code/resources/dependencies are refused.

The complete 23-prompt plan is not finished. The last implemented feature slice
remains the restricted helmet dispenser, without an armor conversion recipe.
The next feature plan is IRON_HELMET_INTEGRATED_SUCCESSOR, deferred while source
publication takes priority. Publishing source does not complete its tests or gates.

## Source boundary

scripts/Export-PublicSource.ps1 creates a new allowlisted snapshot and a separate
per-file hash receipt. It excludes caches, build outputs, installed games, logs,
private originals and full local mapping/API inventories. Historical machine roots
are redacted only in the public snapshot; original local evidence stays intact.
The receipt distinguishes changed/redacted files from byte-preserved files.

The public tree retains actual code, build scripts, authored fixtures, source
notices and the existing SRG Utils rebuild/relink utilities. Historical evidence
paths may point to intentionally absent local files. Paths/hashes in old records
are not claims that those native runs were repeated for the new license artifact.

## Distribution and remaining qualification

Keep the source snapshot and notices with the experimental binary. No third-party
mod binaries or complete mapping inventories are licensed by this publication.
SRG Utils remains LGPL-2.1-only; Gradle wrapper remains Apache-2.0. The root GPL
and linking permission apply to the original covered LegacyBridge code.

Current-hash Minecraft client, dedicated-server and multiplayer validation are
NOT_RUN. No game or screen automation is used to manufacture evidence. Existing
historical successful fixtures do not certify this new hash or arbitrary mods.
Use an isolated test instance; do not advertise stable/world/modpack compatibility.

Release artifacts, exact hashes, headless build/test results and Git commit identity
are recorded in the local publication handoff. No CurseForge approval is implied.

## Artifact-bound headless evidence

The [publication ledger](evidence/publication-2026-10-22.json) records the exact
JAR hash, versions, changed archive entries and test scopes. All 707 class entries,
adapters, nested dependency and finite contracts match the previous JAR byte for
byte; only six legal/manifest/mod-metadata entries differ. License bytes were
checked against the source files.

Publication-scope assembly, package/runtime audits, installed CLI and selected
tests passed. The full default build was stopped during the extensive corpus
filesystem suite and is **incomplete**, not passed. Its converter suite had
finished: 3,571 passed, 472 skipped. The later selected corpus run has 143 passed
and three skipped; seven client-boundary tests passed. Skipped cases are not
approved behavior; cached workflow results and historical runtime tests are not
included as fresh tests. No native Minecraft qualification is inferred.

The isolated public source copy also passed assembly, selected checks and the
installed CLI smoke. It produced the exact same JAR SHA-256. Its build used the
existing dependency cache with Gradle offline; this is not a clean-cache or
offline-gameplay claim. The exporter's 16 synthetic tests passed. The source
tree excludes private build outputs and native evidence files. The export
receipt describes the initial snapshot; subsequent publication metadata and
one additional local-username redaction are captured by the Git commit and
handoff record. The source commit, not an older receipt, identifies the release.
