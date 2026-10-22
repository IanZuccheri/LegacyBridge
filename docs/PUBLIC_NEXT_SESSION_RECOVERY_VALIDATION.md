# LB-06: headless recovery across a synthetic next-session snapshot

Date: 2026-09-07. **PUBLIC-CURRENT-E2E remains PARTIAL.** The user restriction
remains HEADLESS_ONLY: no game launch, screen automation, world opening, original
mod execution or publication occurred in this continuation.

This is supporting installer QA for the pending native interrupted-commit gate,
not a new profile, adapter, mapping rule, public JAR or simulated native pass.
Exact versions stay 1.16.5 / Forge 36.2.42 to 1.20.1 / Forge 47.4.23 / Java 17;
runtime identity remains `legacybridge_runtime` / `0.1.1-SNAPSHOT`.

## Gap found and bounded implementation

The existing separate-JVM crash tests resumed with only the platform/runtime IDs
declared loaded. A future real next launch can already discover published entries
of an incomplete batch. The existing installer treats an owned completed entry
differently from a not-yet-published entry or an entry selected for removal.
This boundary needed direct regression coverage before a native campaign.

Extended only
[`TransactionalProcessCrashTest`](../legacy-product/src/test/java/dev/legacybridge/product/install/TransactionalProcessCrashTest.java).
Seven additional cases use the actual installer implementation on the test
classpath, with an actual helper JVM `halt(81)` at PUBLISHED/index 0 or
BEFORE_COMMIT/index -1. A different helper JVM receives an explicitly synthetic
`InstallSession` and attempts recovery. No production installer change was needed.

| New case | Expected and observed test result | Scope |
| --- | --- | --- |
| First payload published, declared loaded on next session | RESUME commits the other entry and is idempotent; existing loaded JAR identity/hash/mtime unchanged | The supplied loaded set is not updated to activate the second mod |
| All payloads published, no final commit checkpoint | Initially PARTIAL / RECOVERY_REQUIRED, unsafe to close; RESUME with both declared loaded records COMMITTED | Complete files alone are not a completed transaction |
| RESTORE with loaded ID only | Refused with TRANSACTION_REFUSED, remains PARTIAL | Protects the ID guard |
| RESTORE with loaded path only | Refused even through a distinct retained hard-link path | Protects `Files.isSameFile`, not just path-string comparison |
| RESTORE with both loaded ID and path | Refused without removing protected artifacts | Does not authorize unloading or live rollback |
| RESUME with world-open flag | INSTALL_MANUAL before publication | No world was actually opened |
| Missing second entry's ID declared already loaded | Refused before finishing the batch | No discard of a loaded-ID conflict to make recovery succeed |

Each case asserts the incomplete plan/file/checkpoint state after the actual
helper halt. The first-publication boundary has no INSTALLED/0 checkpoint yet;
the final boundary has both INSTALLED checkpoints but no COMMITTED checkpoint.
The two previous process-crash tests remain present and pass independently.

Retained test-owned hard links, SHA-256 and last-modified times guard the inert
runtime declaration, originals, staged candidates, payloads, plan, existing
checkpoints and already published JARs. This detects replacement and ordinary
in-place rewriting of protected files. Cooperative locks and retained operation
canaries may be cleaned/created by legitimate recovery; the tests do not claim
that all filesystem metadata or temporary guard names remain unchanged.

## Evidence boundaries

The inputs are inert strings, not genuine legacy Forge JARs. Staged candidates
are the small existing ASM-generated installer fixtures. The file named
`mods/LegacyBridge.jar` in each JUnit temporary directory is an inert metadata
declaration, **not the public artifact**. No original or generated mod class is
added to the helper classpath or executed. Runtime discovery is neither simulated
by class loading nor claimed: IDs/files/world state are deliberately supplied
test values, not observations from Forge.

Helpers run serially with Java 17, `-Xmx128m`, `java.awt.headless=true`, an empty
test user home and a 30-second per-child timeout. Only owned helper processes
can be forcibly cleaned up on timeout. No user process or launcher arguments
are inspected/terminated; printed receipts contain modes/exit codes and the
explicit synthetic-result markers, not full launch arguments.

The initial targeted run passed all nine cases (seven new, two existing), with
zero failures/errors/skips. Its XML/log are archived before later Gradle runs.
After that run, test naming was narrowed to protected artifacts and bounded
child-exit markers were added for the root-build receipt. The
[versioned validation ledger](evidence/public-next-session-recovery-2026-09-07.json)
separates these executions and their hashes from the older four-case ledger.

The subsequent serial root build plus evidence verification passed in 2m 3s:
69 tasks (26 executed, 43 up-to-date), all 91 product tests freshly passed with
zero failures/errors/skips. Other Java test modules were UP-TO-DATE. Archived
XML confirms nine helper exits at 81, nine recovery/refusal helper exits at zero
and seven explicitly synthetic next-session result markers. Those are process
observations, not counts of compatible mods or Minecraft sessions.

Contract checks still report 23 prompts/77 cuts and LB-06/PUBLIC-CURRENT-E2E;
the existing 192-file/61-assertion evidence index passed without promotion.
Packaging validation passed and the public SHA-256 remains
`3749da06606f36b7e9bcbb97e4d9558f91d0bfa1c60e5da533767fd722607df6`.
The eight product XML suites and build log are hashed in the new ledger.

## Remaining work

This does not implement an external interruption oracle for the unmodified public
Minecraft process. A helper's internal test hook is not such an oracle. The
[current evidence index](PUBLIC_CURRENT_QUALIFICATION.md) and its recorded native
gate remain unchanged; the present source-only test addition cannot promote them.
Once UI is explicitly authorized and an external boundary method is validated,
the actual discovery snapshot, interruption, explicit recovery, next activation
and behavior still need separate native evidence. RESTORE must continue to reject
loaded artifacts; do not falsify the session or disable discovery to bypass it.

Installed-conflict/UI matrices and persistent job/install linkage remain open.
No automatic restart, network-isolated offline gameplay, multiplayer qualification,
new conversion category or compatibility with new real mods is claimed.
