# LB-06: current-hash evidence consolidation

Historical after the [job-receipt correction](JOB_INSTALL_RECEIPTS.md): this page
qualifies only `3749da...`, not a later `build/libs/LegacyBridge.jar`. The original
index/ledgers remain untouched. An archived index under
`build/job-install-receipts-2026-09-07-run1/baseline-index.json` points E190 at the
retained baseline JAR, with all hashes/assertions unchanged. The original default
index should detect drift against a newly built JAR, not certify it accidentally.
Use the archive index explicitly for historical integrity verification.

Date: 2026-09-07. **PUBLIC-CURRENT-E2E remains PARTIAL, experimental/Beta.**
This continuation is entirely headless: no Minecraft, desktop controls, screenshot
capture, new mod acquisition, world change or publication. The user's no-screen
restriction remains in force until explicitly lifted.

Public SHA-256:
`3749da06606f36b7e9bcbb97e4d9558f91d0bfa1c60e5da533767fd722607df6`.
Exact source Minecraft 1.16.5 / Forge 36.2.42, target 1.20.1 / Forge 47.4.23 /
Java 17; runtime `legacybridge_runtime` / `0.1.1-SNAPSHOT`. No production class,
mapping, adapter, profile or public dependency was changed.

## What was consolidated

The [versioned evidence index](evidence/public-current-index-2026-09-07.json)
joins existing receipts by exact bytes, rather than inheriting results from old
public builds. It pins 192 local files (95,949,903 bytes) and 61 scalar JSON
assertions. Its 23 recorded observations are not 23 tests or compatible mods.
Four explicit pending entries and three unclaimed dimensions remain visible.

| Evidence family | Actual existing evidence | Not established by that family |
| --- | --- | --- |
| [Positive native client](EMBEDDED_SIMPLE_NATIVE_UI_VALIDATION.md) | Public-only initial menu, blank-data conversion, continue without activation, approved Apply/close, next discovery, crafting/placement and pt_br/en_us assets | Arbitrary mods, automatic restart or interrupted installation |
| [Finite native negatives](PUBLIC_CURRENT_NEGATIVE_VALIDATION.md) | Corruption/duplicate-input/dependency/worldgen refusals, queue pair, write denial/recovery, accepted cancel and active-screen closure | Installed-ID conflict matrix, disk-full, native commit interruption |
| Native client reload log | Recorded reload/parser result in the actual client log | No dedicated reload screenshot or second crafting cycle |
| [Dedicated server](EMBEDDED_SIMPLE_CONTENT_DATA.md) | Same public and converted hashes, ordinary Forge readiness, registry/tag checks, reload and normal save/stop | Client assets/crafting, multiplayer or network isolation |
| Cacheless static conversion | Original/output parity, included data and bounded test-only I/O tripwire | Production sandbox, game launch or full offline Minecraft |
| Headless recovery | Four installer test-classpath crash/sharing-handle cases | An actual public-only Minecraft process interrupted during commit |

All original ledgers, including failed attempts and historical pending text, are
preserved. This index and the current contract establish the later consolidation;
they do not rewrite old observations. Screenshots are existing native captures,
not newly produced images. Their pixels were not inspected again in this turn.

## Read-only verifier and its limits

`legacy-corpus` now contains `PublicEvidenceVerifier`, a development tool excluded
from the public product/runtime. With the local evidence archive present:

```powershell
.\gradlew.bat '-Djavax.net.ssl.trustStore=NONE' '-Djavax.net.ssl.trustStoreType=Windows-ROOT' :legacy-corpus:verifyPublicEvidence --no-daemon --max-workers=1
```

Use the Windows root-store flags only on the documented Windows environment.
The task is opt-in, not a dependency of the root build: a clean checkout does
not contain private build evidence. Missing files fail explicitly, without
download, regeneration, skip-as-pass or silently repinning changed hashes.
Synthetic tests of the verifier are part of the normal root build.

It checks bounded inert JSON, IDs/references, canonical paths without symlink or
junction redirection, exact
file sizes/SHA-256, public identity binding and scalar assertions selected by a
restricted JSON pointer. Native visual observations need declared image and log
evidence; log-only client observations are a separate dimension. It refuses
unknown states, erased pending gates and qualification other than PARTIAL.

Limits: 512 files, 128 MiB per file, 512 MiB total streamed in 64 KiB buffers;
512 assertions, 128 observations, 16 parsed ledgers/8 MiB aggregate, existing
2 MiB bounded UTF-8 JSON/parser limits, a 256 MiB verification JVM heap. No JAR
entry is extracted or loaded; images/logs/XML are hashed, not executed or decoded.
Use a quiescent archive under local control. This is not hostile concurrent
filesystem isolation, a digital signature, image-forensics software or proof
that a human-authored assertion is true. Deliberately changing both evidence and
index still requires human review. An integrity pass cannot promote the contract,
certify gameplay or authorize publication; the task never edits statuses.

The [headless validation receipt](evidence/public-current-integrity-2026-09-07.json)
records 49 targeted cases: 48 passed, one Windows symlink-creation skip, zero
failures/errors in the final run. The real retained archive passed 192 hashes
and 61 assertions. Earlier two test-message casing failures and the initially
misclassified reload-log observation remain recorded, with their corrections.
No skipped check is treated as native or filesystem qualification.

The serial root build then passed in 4m 41s (68 tasks: 29 executed, 39 up-to-date).
It freshly ran all 210 corpus tests: 206 passed, four filesystem/symlink skips,
zero failures/errors. The other Java test modules were UP-TO-DATE. Contract and
packaging checks passed, and the public JAR stayed byte-identical. The six XML
suites and root build log are archived and hashed in the same receipt. This does
not execute the opt-in archive verifier implicitly or replace native QA.

## Remaining native interruption oracle: design boundary, not implementation

Actual `TransactionalInstaller.perform` publishes each complete payload with a
no-replace hard link, records its INSTALLED checkpoint and only then records the
batch COMMITTED checkpoint. Its package-private `Hook` is used by synthetic tests;
the normal public constructor has no observation/termination hook. Polling for
a file and killing later can miss this window. A generic process kill, elapsed
delay or absence of a success log is not sufficient evidence of mid-commit loss.

A future authorized test must bind a newly owned disposable process and instance,
the exact unmodified public JAR, selected originals/results and transaction plan.
Before any termination it needs an externally validated way to capture an actual
incomplete commit boundary. If the operation has already committed, preserve it
as a missed boundary, never a successful interruption. Do not patch the public
JAR or relabel a test hook/QA mod/debugger run as public-only native validation.
There is no such validated external oracle implemented by this continuation.

Later supporting work: [synthetic next-session recovery tests](PUBLIC_NEXT_SESSION_RECOVERY_VALIDATION.md)
exercise owned partial/all-published batches already declared loaded, missing
commit and loaded-ID/file/world refusals. These actual helper-JVM crash tests do
not implement the external native oracle or change the index's pending gates.

After a captured interruption, inspect immutable plan/payload/checkpoints and
on-disk identities separately from Forge discovery. The next launch may already
load a published subset: loaded-file and dependency guards must remain enforced.
Do not falsify `InstallSession`, suppress discovery or remove loaded files to force
RESTORE. Recovery must be explicitly player-approved; subsequent discovery and
behavior are separate oracles. No world may be open and no credentials/launcher
arguments may be copied. These constraints remain work for the native gate,
not an instruction to run a client now.

Next: public-client commit interruption/recovery and remaining installed-conflict
and GUI matrix. Persisted job/install linkage remains UI-PROMOTION. Fully offline
gameplay, automatic launcher restart and multiplayer qualification are unclaimed.
LB-12's normal CLIENT_PROTOCOL issue is still deferred/unresolved. Do not expand
conversion categories or acquire new famous mods to bypass the active gate.
