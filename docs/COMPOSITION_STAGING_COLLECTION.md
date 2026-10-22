# COMPOSE-01: unarmed staging and inert process collection

LB-08 / COMPOSE-01 remains **PARTIAL**. This continuation implements developer
mechanics in `legacy-corpus`, not another conversion profile or player installer.
The six profiles, runtime ABI and public JAR are unchanged. No Minecraft client
or server is started. Real composition behavior remains **NOT_RUN**.

## Separate boundaries

| Operation | Implemented result | Does not establish |
| --- | --- | --- |
| `CompositionRunPlanner.plan` | `PLANNED_ONLY`, reviewed input/argument digest | Permission or execution |
| `CompositionRunStager.stage` | `STAGED_UNARMED`, new private copies and hashed receipt | Armed probe, native launch, installation |
| Internal `CompositionProcessCollector.collect` | `PROCESS_COLLECTED_ONLY`, observed child exit and both pipe EOFs | Association with an approved native plan, game correctness |
| `CompositionExitVerifier.verify` | `RECORDED_EXIT_ONLY` for supplied exit/log records | Proven origin of records or live process observation |

The collector is package-private, has no command/task, and is exercised only
with a JDK-only test helper JAR. Supplying a syntactically valid plan digest is
**not** verification that the command belongs to that plan. Connecting these
pieces through a reviewed, explicitly authorized native launch contract and
post-exit publication is still work to do. No flag is silently promoted.

## Staging contract

The public materializer regenerates the plan from the fixed reviewed local
inventory and artifact indexes. The caller must supply the exact reviewed plan
SHA-256. It rejects drift, noncanonical paths, symlink/junction ancestors,
selected hard-link aliases, duplicate destinations, unknown metadata and an
existing run ID before writing. The package-private fabricated-plan seam is for
inert tests, not a public selection API.

Outputs are only under `build/composition-probe-runs/<new-run-id>/`:

- `stage-intent.json`: immutable operation ID, plan binding, PREPARING_UNARMED.
- `stage-plan.json`: complete canonical directive data, not an executable script.
- `journal/NNNN.json`: create-new events for completed copies/generated properties.
- `source/` or `target/`: selected independent copies and owned empty homes/tmp.
- `stage-receipt.json`: final hashes, closed file/directory inventory and withheld marker.

The materializer deliberately **does not write**
`<role>/composition-probe-authorized.txt`. Its text in the saved plan is only a
directive. It never accepts an EULA, invokes Java, registers mods, downloads
anything, reads/copies template worlds or modifies an original. Only already
accepted template EULA bytes and selected platform/fixture inputs are copied.
These local QA copies contain privately prepared platform files; they are not
redistributable artifacts and are never packaged in LegacyBridge.jar.

The saved canonical plan keeps its original PLANNED_ONLY/pending directives;
those are per-operation declarations, not the current implementation backlog.
Staging records its separate result/receipt without changing the reviewed plan.

There is one staging slot per developer JVM, no queue, at most 261 copies, 128 MiB per copy,
512 MiB total copy payload, 512 input pins, 2 MiB per metadata file and 8 KiB generated
properties. Copy buffers are 64 KiB. Hash rechecks bracket copies/publication;
the final audit rejects even empty unknown directories, extras, missing files,
changed bytes and aliases to selected originals. Hashing and OS I/O are not
hard-real-time operations and cancellation is cooperative.

Each file is opened CREATE_NEW/NOFOLLOW_LINKS, fully written, forced and
hash-checked. The final pending receipt is moved without REPLACE_EXISTING or
ATOMIC_MOVE. Java's ATOMIC_MOVE contract permits provider-specific replacement
behavior; the inspected Windows provider uses MOVEFILE_REPLACE_EXISTING. This
is **not** an atomic multi-file transaction or directory fsync guarantee.
[Java 17 Files](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html).

Failures/cancellation retain private, unapproved files; no automatic deletion,
adoption, resume or overwrite is attempted. `remainderRetained` includes parent
directories created before acquisition of a run ID; in that case the reported
run path may not yet exist. Once acquired, the run name is consumed: use a new
reviewed ID after inspecting the old evidence. Late cancellation after receipt
publication does not relabel complete valid staging as CANCELLED. A later
audit/I/O failure returns FAILED and no approved receipt hash even if a complete
file named `stage-receipt.json` exists. Mere receipt existence never grants
authority, including after a crash.

Use a trusted, quiescent local filesystem. Canonical rechecks and hashes are not
a hostile-concurrent-writer sandbox. On the inspected JDK Windows fileKey is
null; an independent replacement with identical bytes can satisfy hashes.
Unknown external hard links, TOCTOU races and power-loss durability are not
proven away. Windows share modes do not turn a Java lock into tree ownership.

## Explicit developer-only copying

First run `:legacy-corpus:planCompositionRun` with explicit
`compositionRunRole`, a new `compositionRunId` and `compositionJavaHome`.
Review its complete canonical plan and digest. Then run:

```text
:legacy-corpus:stageCompositionRun
  -PcompositionRunRole=SOURCE
  -PcompositionRunId=<the-reviewed-new-run-id>
  -PcompositionJavaHome=<reviewed-Java-17-home>
  -PcompositionReviewedPlanSha256=<exact-plan-sha256>
```

Use the repository Gradle wrapper, serially with `--no-daemon --max-workers=1`.
This opt-in task is not a dependency of `build` or `check`. Its CLI entrypoint
returns bounded schema-1 JSON, and throws a sanitized error for non-success;
there is no System.exit. No task executes the generated native arguments.

## Collector contract and limits

The internal immutable `Launch` records cwd, argv, supplied plan digest, exact
input pins and limits. Java must be an absolute selected hashed executable;
referenced class/module-path JARs must be selected files within cwd. Arguments
reject argfiles, injected agents/JVM options, sensitive fields and the explicitly
checked game/world redirection forms. This is not a general argument sanitizer
and does not replace the future closed native launch contract. Homes/tmp must
already exist. A fresh minimum environment is
applied, not just described: no inherited CLASSPATH, JVM injection variables,
launcher credentials or arbitrary user environment. No shell is used.

The collector closes child stdin, drains stdout/stderr concurrently, waits for
actual process exit and both EOFs, rejects nonzero exit, checks strict UTF-8 and
sensitive-output patterns, and rehashes immutable inputs after termination.
Failures return sanitized codes without raw captured bytes. Successful bytes
are defensively copied in memory; this class does not persist raw logs or exit
indexes. The sensitive-pattern filter is deliberately conservative, not a
general secret detector; only trusted inert helper code is executed now.

Bounds: one process slot per developer JVM/no queue, up to 240 seconds observation, 60 seconds
cleanup grace, 8 MiB per stream, 2 GiB maximum requested heap and bounded input
counts/bytes. These limits do not bound JVM native memory, OS process startup or
blocking filesystem calls. Only the returned owned Process may be stopped;
unknown processes/descendants are never enumerated or killed. A surviving child
or reader quarantines the slot until the developer JVM ends. Cancellation,
timeout, interruption, exit failure and uncertain cleanup are distinct evidence.
Cancellation and interruption are checked again after cleanup/input rehash and
before completing a successful capture; exceptions from cancellation callbacks
are sanitized and cannot publish raw bytes or leak callback messages.
`destroyForcibly()` is followed by bounded waiting, not assumed to be immediate.
[Java 17 Process](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Process.html).

Native `server.properties` is expected to expand at startup: a future executor
must separate preparation/configuration hashes from immutable artifact pins.
Do not feed mutable server output into the immutable-input contract. Likewise,
source Forge with Java 17 has **not** been validated by the earlier Java 8 run.

## Validation and continuation

This staging checkpoint is historical. Its next integration step is now described
in [closed inert sessions and read-only stage admission](COMPOSITION_INERT_SESSIONS.md).
That implementation still does not arm probes or authorize native execution.

The [artifact-bound ledger](evidence/compose-staging-2026-09-08.json) records the
fresh test/build results and any local copy-only checks. Inert helper success
does not populate the native observation index (`runs: null`). Negative tests
use controlled I/O exceptions, tampering, cancellation and alias injection;
they do not prove physical disk-full, power-loss, force/close failure or a JVM
surviving forced termination. Those remain explicit QA gaps.

Fresh corpus suite: 552 cases, 547 passed, five symlink-privilege skips and zero
failures/errors. New suites: collector 42/42, staging 68 passed/one skipped,
command 3/3. Both real Windows sharing-denial tests and junction tests passed.
The first focused run exposed three test assertions that assumed compact JSON;
they now parse/assert the structured JSON fields without loosening production
checks. The full build/package audit passed; other module test tasks remained
UP-TO-DATE. Counts are test cases, never a count of supported mods.

The actual selected templates also passed **copy-only** staging through the
opt-in Gradle task: SOURCE 33 copies / 54,483,655 bytes, TARGET 71 copies /
71,890,536 bytes. Both returned STAGED_UNARMED; all receipt files and selected
inputs were independently rehashed without drift. Neither tree has an arming
marker, world, native logs or observations. These new private trees are not the
old read-only planning run IDs, which remain absent. No template/original was
overwritten and no EULA was accepted by this operation.

Next headless work: bind the owned materialized receipt/argv/immutable artifacts
to a closed launch session, test its entire path using inert helpers, and add
bounded create-new post-exit publication/recovery. No arming or native game
execution without renewed authorization. Only native original/target behavior
comparison and the remaining independent gates may accept COMPOSE-01 or unlock
LINKAGE-GENERAL; no metrics, profile or installation promotion follows this work.

The next session contract must verify a previously materialized tree without
relaxing the public planner's create-new rule: simply calling that planner with
an existing run ID is correctly blocked. Rebind the stored canonical directive,
receipt, current trusted selectors, manifest/dynamic dependency closure and JDK
metadata explicitly. Separate immutable inputs from permitted native outputs
(expanded configuration, logs and disposable world state). Do not import those
outputs as approval. Test missing/extra/changed entries, wrong role/digest/argv,
post-exit publication collisions and interruption using inert helpers before any
separate request to authorize native execution.
