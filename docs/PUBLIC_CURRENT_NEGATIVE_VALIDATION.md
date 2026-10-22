# Current public artifact: finite negative QA

Date: 2026-09-07. Cursor remains **LB-06 / PUBLIC-CURRENT-E2E — PARTIAL**.
Product is experimental/Beta, not approved for publication. The
[versioned ledger](evidence/public-current-negatives-2026-09-07.json) binds the
actual jobs, screenshots, source/output hashes, logs and separate headless tests.

Current user restriction: **headless work only**. Both native clients described
below had already exited before the user requested no screen testing. Since that
request, no game, focus, mouse or keyboard automation was used. Do not launch or
interact with Minecraft again without explicit renewed authorization.

## Artifact and scope

Public SHA-256 is unchanged:
`3749da06606f36b7e9bcbb97e4d9558f91d0bfa1c60e5da533767fd722607df6`.
Source Minecraft 1.16.5 / Forge 36.2.42; target Minecraft 1.20.1 / Forge 47.4.23 /
Java 17.0.12, `legacybridge_runtime` / `0.1.1-SNAPSHOT`.

No production code, conversion rule, adapter or supported profile was changed.
The normal disposable instance `build/embedded-simple/client` retained the public
JAR plus the previously approved example conversion. Its title consistently had
four loaded mods including platform. Negative inputs were never installed.
The optional mapping-data field stayed empty; the finite included provider was
used. No QA mod, debugger, agent or development game classpath was added.
No world was opened, downloaded mod acquired, file published or launcher restarted.

The [previous positive UI scenario](EMBEDDED_SIMPLE_NATIVE_UI_VALIDATION.md) and
[same-hash server/cacheless evidence](EMBEDDED_SIMPLE_CONTENT_DATA.md) remain
independent evidence. They were not repeated merely to accumulate passes.

## Native observations completed before the no-screen request

| Gate | Actual result | Boundary |
| --- | --- | --- |
| Corrupt input | INVALID / MALFORMED_ZIP, conversion disabled, usable technical details | Not a malware/security certification |
| Duplicate input IDs | Both `p6_duplicate` copies unselectable; Space did not check the row | Input inventory; not a fresh installed-ID conflict |
| Required dependency | BLOCKED / UNSUPPORTED for `p6_missing_dependency`, no output | Conservative manual-migration refusal, not generic dependency resolution |
| Unsupported resource | BLOCKED / RESOURCE_OUTSIDE_SUBSET for worldgen sentinel, no output | No worldgen feature enabled |
| Two selected inputs | One processing, one queued, then two separate refusals | Not saturation or every ordering scenario |
| Actual write denial | FAILED / IO_ERROR with null artifact; details remained usable | Real Windows staging create denial, not physical disk-full/power-loss |
| Permission recovery | Original ACL semantics restored, zero deny rules; native create/delete proof and later UI conversion passed | No change to original/input/mods permissions |
| Cancel button | Second bounded attempt CANCELLED before publication, no artifact | Earlier completed attempts retained, not counted as cancellations |
| Close active screen | Escape during processing returned to title; receipt CANCELLED, no artifact; reopen remained usable | In-memory job list is still not restored |

PID 32620 exited 0 at 14:24:20.533; PID 8424 exited 0 at 14:32:42.983.
Logs are preserved separately in
`build/public-current-negatives-2026-09-07-run1/launch-1` and `launch-2`.
Both latest logs have zero ERROR lines, but standard Forge metadata/resource,
vanilla sound/shader and offline Realms notices remain. This is not a claim of
warning-free logs or network-isolated offline gameplay.

Selected real captures (all 29 are hashed in the ledger):

- [Duplicate inputs](../build/embedded-simple/evidence/public-neg-r1-04-duplicates.png)
- [Corrupted input diagnostic](../build/embedded-simple/evidence/public-neg-r1-06-corrupt-details.png)
- [Two independent blocked results](../build/embedded-simple/evidence/public-neg-r1-09-two-blocked.png)
- [Dependency refusal](../build/embedded-simple/evidence/public-neg-r1-10-dependency-details.png)
- [Worldgen refusal](../build/embedded-simple/evidence/public-neg-r1-11-unsupported-details.png)
- [Real write failure](../build/embedded-simple/evidence/public-neg-r1-16-write-details.png)
- [Successful conversion after permission restoration](../build/embedded-simple/evidence/public-neg-r1-18-permission-recovery-converted.png)
- [Accepted cancellation](../build/embedded-simple/evidence/public-neg-r2-06-terminal-cancel.png)
- [Active operation before Escape](../build/embedded-simple/evidence/public-neg-r2-08-before-close-active.png)
- [Reopened usable screen](../build/embedded-simple/evidence/public-neg-r2-10-reopened-usable.png)

## Cancellation fixture and timing

The 6,124-byte authored control finished before the first cancellation attempt.
It remains CONVERTED, with its complete staged result. This is not a failure of
the atomic-publication contract, nor a successful cancellation.

Added only a development fixture generator,
[`P6BoundedCancellationFixture.java`](../scripts/P6BoundedCancellationFixture.java),
with [bounds and reproduction notes](../scripts/P6_BOUNDED_CANCELLATION_FIXTURE.md).
It preserves every source ZIP entry byte, including the sole Java 8 class, and
adds 24 bounded language JSON files with unused deterministic QA strings. The
12,340,435-byte STORED input is a resource-load test, not a new compatible mod,
translation feature or ordinary small-fixture size claim. Original en_us/pt_br,
the engine's limits and all executable code stay unchanged. There are no sleeps,
game pauses or cancellation hooks. Generator compilation, repeated identity
checks and no-replace publication were exercised on Java 17.

Input SHA-256:
`17b74d20cb9dfccb9b3afafa76698c9ebf0ae7b410fc4454ccd345b93d77e3c7`.
The separate default-data CLI control exited 0; its output and the completed
native control were byte-identical (7,837,048 bytes), SHA-256:
`9858512148b4562a7e205e39a259276b1c82cc1876928f6b4b026d1842138c32`.
Static conversion is separate from cancellation and never executes the source.
Only the operation-owned small input copy was archived outside `input` to avoid
duplicate `p6_cancel` identities; the authored source was not moved or overwritten.

The first bounded attempt also completed: an early Cancel click displayed
unavailable while the screen still indicated processing. Controller inspection
allows an inter-stage completion race as a hypothesis; its exact timing cause
was not instrumented or proved. The next attempt captured progress before the
click and persisted job `449a5b67-6b77-4fb9-827d-f4a25f4cb0af` as CANCELLED.
Closing the screen during a third operation persisted
`841aa3e9-288c-4f2a-935c-48297b673c12` as CANCELLED. Both have null artifact/hash.
The earlier CONVERTED jobs were not relabeled, removed or substituted.

## Filesystem and error handling audit

Only the disposable staging directory received a temporary current-user deny ACE
for creating files/directories, with no inheritance to children. A `finally`
restored its original access rules, owner/group and protection flag; normalized
semantic comparison and a real create/delete proof succeeded. A later independent
audit found zero deny rules and no proof directories. Native reconversion then
succeeded. No permission widening or source-file rewrite was required.

Two QA-wrapper errors are preserved, not attributed to the mod or hidden:

- After successful permission cleanup/assertions, formatting the tool receipt
  failed because PowerShell boolean literals lacked `$`. Subsequent filesystem
  inspection and actual reconversion independently confirmed recovery.
- The first CLI wrapper treated normal INFO stderr as a terminating PowerShell
  error. Its files remain, but it is not the accepted exit receipt. A fresh
  no-overwrite retry with separate stdout/stderr capture exited 0.

Final staging has exactly six complete JARs, each matched to one successful job:
three prior example conversions, two small controls and one bounded control.
No orphan/partial file or extra operation directory remained. Eight new native
jobs account for two BLOCKED, one FAILED, three CONVERTED and two CANCELLED
outcomes; those are attempts, not eight compatible mods. No new transaction or
installed file was produced. The genuine original and six authored negative
source hashes remained intact. Neither trace resolved the original fixture
class, any generated negative-control class or a development-cache class source.

## Headless recovery tests after the user's restriction

Four existing tests freshly passed, no failures/errors/skips, in a serial
47-second targeted Gradle run:

- Actual helper JVM halt after the first publication; another JVM resumes twice
  idempotently, revalidating both installed artifacts and original hashes.
- Actual helper JVM halt after retirement of a prior managed identity; another
  JVM restores the verified previous identity/backup.
- Windows deny-delete sharing handle preserves the old artifact and permits
  explicit resume after releasing the handle.
- Windows deny-read sharing handle refuses preflight without retiring the old
  artifact and permits a subsequent explicit retry.

These tests use inert declarations, generated test artifacts and installer code
on the test classpath, **not the public-only Minecraft process**. Their real
`halt(81)` and sharing violations are bounded synthetic boundary tests, not
native menu interruption, disk-full, power-loss or general filesystem coverage.
They open no game/UI. The XML results were archived before the subsequent root
build could replace Gradle's test-result directory; hashes are in the ledger.

## Root build and packaging after documentation changes

The serial root `build --no-daemon --max-workers=1` passed in 1m 51s,
68 actionable tasks (24 executed, 44 up-to-date). It freshly executed all 84
`legacy-product` tests, with zero failures/errors/skips; the four targeted
recovery cases above are part of that suite, not additional unique tests.
Other modules' test tasks were UP-TO-DATE, not fresh reruns. Previously retained
XML includes skipped real-API/fixture checks; no skipped gate is promoted.

The eight fresh product XML files and build log are separately archived and
hashed in the ledger. `verifyPromptContract` and `verifyProductJar` passed.
The public JAR remains byte-identical: no platform/logging duplicates, private
mappings or this development fixture/helper were added. This is build/packaging
evidence, not native interruption or gameplay acceptance.

## Remaining scope and next action

Keep LB-06 / PUBLIC-CURRENT-E2E PARTIAL. The next runtime gap is external,
ownership-checked interruption/recovery during an actual public-only client
transaction; this was not executed. Under the current user restriction, only
headless design/tests/audit are permitted. Do not silently resume UI automation.
Separate input-duplicate refusal from installed-ID conflict; reconcile the latter
and dependency-installation boundaries in the final same-hash qualification.
Native scale/resize/queue-saturation coverage remains limited. Reopenable job
history and persisted PENDING_RESTART installation linkage remain UI-PROMOTION.
Automatic restart and network-isolated offline gameplay are not claimed.
LB-12's normal CLIENT_PROTOCOL handshake failure remains unresolved; do not advance
capabilities or expand conversion categories to bypass these gates.
