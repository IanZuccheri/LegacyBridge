# COMPOSE-01: closed inert sessions and read-only stage admission

Scope: **LB-08 / COMPOSE-01, PARTIAL**, developer-only QA infrastructure.
No conversion rule, adapter, profile, runtime identity or player installation
policy changes. Minecraft clients, servers and screen automation remain
unauthorized. A JDK-only test helper is not an original or converted mod.

## What is implemented

`CompositionStagedRunVerifier.verify` reconstructs the currently reviewed exact
SOURCE/TARGET selection for an already materialized tree. Its private-constructor
Snapshot links the current selectors, canonical plan, reviewed receipt digest,
original inputs, copied files and JDK executable/release pins. The public planner
and stager still reject an existing destination; only the internal reconstruction
used by this read-only verifier accepts a materialized directory.

The verifier derives expected files and directories from the trusted plan, not
from the receipt's own assertions. It reconstructs intent, copy journal and
withheld-marker record; checks schema, canonical bytes, operation ID, role,
run ID, hashes, input aliases and missing/extra paths; and rejects an armed stage.
Its `STAGED_INPUTS_VERIFIED_ONLY` result grants no execution authority. Public
`recheck` repeats current reviewed selection. Synthetic test plans use a separate
package-private seam and are never accepted by the public selector.

`CompositionInertSession` connects that snapshot to the process collector and
post-exit publisher, **only with a closed test helper**. It never uses the native
plan's arguments, copies the helper outside Forge discovery and never writes an
authorization marker. The helper class must match bytes from the trusted test
classpath; a filename, class name or caller-supplied digest is insufficient.
The helper resource is absent from all distributed JARs: the session refuses
there rather than substituting an arbitrary executable. There is no native
session CLI, automatic execution command, UI action or product service entrypoint.

Each operation exclusively creates:

```text
build/composition-inert-sessions/session-<unique-id>/
  session-plan.json
  work/
    inert-helper.jar
    input.dat
    tmp/
    empty-user-home/absent-gradle/
  evidence/                      # only after successful inert collection
    stdout.log
    stderr.log
    collection.json
    binding.json
    session-receipt.json          # published last; .pending before publication
```

The canonical session plan binds stage plan/receipt, role/run/session, actual
helper argv/cwd, helper class/JAR hashes, JDK metadata, immutable inputs, exact
minimum environment and limits. Its digest is **different from the native plan
digest** and is the collector's binding. The collector's command hash alone
covers argv; the session-plan hash additionally covers cwd/environment/limits.
The plan's JDK declaration is metadata, not a measured native startup version.
No unfiltered environment, launcher arguments, credentials or tokens are stored.

Staging and original hashes are checked before and after collection. The complete
inert work tree must remain closed, including at the final post-publication check.
The inherited immutable-input list excludes native `server.properties`; this
inert session nevertheless rechecks its original bytes as part of the complete
unarmed stage, because this helper has no reason to modify it.

## Publication, cancellation and recovery

`CompositionSessionEvidence.publish` accepts only a successful actual collector
result with matching session digest, zero exit code, both EOFs and no errors.
It writes bounded logs and canonical JSON with CREATE_NEW, complete channel
writes, force, rehash and closed-tree/alias checks. The final receipt is moved
without ATOMIC_MOVE or REPLACE_EXISTING. It never overwrites a destination or
cleans up an unknown/partial operation. Multiple files are not an atomic transaction.

`PUBLISHED_INERT_ONLY` is a successful inert session, **not** Minecraft execution,
native launch-contract verification or installation approval. Failures, timeout
and cancellation do not return an approved receipt. Cancellation inside the
verifier/publisher retains its cancellation classification. After the completed
publication barrier, cancellation is not retroactive. A late integrity failure
can leave a private final-named receipt but returns FAILED without approval;
the mere existence of a receipt is never sufficient. Interruption that prevents
the integrity recheck after publication returns FAILED / INTEGRITY_RECHECK_INTERRUPTED
and preserves the interrupt flag; it does not approve unchecked evidence or claim
that completed publication was retroactively cancelled.

Read-only inspection requires the expected Binding and returns:

- `RECORDED_INERT_ONLY`: the closed unsigned record agrees with that binding and
  has consistent schemas, completion flags and log hashes. This does not observe
  a live process or authenticate the writer.
- `INCOMPLETE`: only a bounded subset of known filenames exists without a final
  receipt; no binding or execution is approved. Contents may be truncated.
- A sanitized error for corruption, unknown paths, aliases, schema drift or a
  malformed purported complete result. No file is repaired, removed or adopted.

Inspection does not retry a process, complete a partial publication, promote a
native observation, install a mod or arm a probe. `recordedBindingVerified` means
agreement of recorded fields with the supplied Binding, not authenticated
provenance. Coherently forged unsigned records are not an execution attestation.

## Bounds and trust model

One session/one collector slot per developer JVM, no queue; helper heap 32 MiB;
up to 240 seconds collection and 60 seconds cleanup, up to 8 MiB per stream,
512 immutable pins / 512 MiB aggregate input, 1 MiB helper archive and class;
2 MiB session-plan JSON, 64 KiB per evidence JSON. Only the collector's owned
Process is terminated. A surviving process/reader quarantines its collector slot.
Filesystem and OS process APIs are not hard-real-time; heap is not total native
memory. No promise of directory fsync, power-loss atomicity or hostile-writer
isolation is made. Hashes do not prove same-byte filesystem object identity, and
hard links outside the selected set are not exhaustively discoverable.

The first integrated test exposed HotSpot's default `work/tmp/hsperfdata_<user>`
directory after an otherwise successful helper exit. The exact helper invocation
now uses `-XX:-UsePerfData` to suppress it; the collector permits only that exact
option once, not arbitrary `-XX` arguments. The closed-tree validator still
rejects unexpected directories. This does not change native game arguments.
[Java 17 launcher options](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html).

The helper's manifest and exact two ZIP entries are closed, including a byte-exact
trusted class comparison. This is important because `Launcher-Agent-Class` can
start an agent **before main**, without a `-javaagent` argument. Class-Path,
agent attributes, indexes, services, module descriptors and multi-release variants
are refused for this helper. [Java 17 JAR specification](https://docs.oracle.com/en/java/javase/17/docs/specs/jar/jar.html).

This policy must **not** be blindly imposed on Forge's existing platform JARs:
the exact Mixin libraries have legitimate agent metadata and other libraries
have multi-release entries/indexes. The local launcher audit found old dependency
names in native JAR indexes. Static manifest selection is not a proof of all
late classloader lookups. No platform JAR is patched to hide those differences.

## Separate future native contract

The subsequent [bounded output-layout policy](COMPOSITION_NATIVE_OUTPUT_POLICY.md)
implements only static path/budget/fixed-property review. Native content,
arming, process execution and comparison remain separate pending gates.

Real sources for both exact game versions write `server.properties` during
startup; world metadata can be touched before the probe's first tick. Forge
also writes configuration, and Log4j writes/rotates native logs. The future
native contract must separate immutable libs/mods/arguments/receipts from
pre-start mutable configuration and explicitly bounded generated output roots.
It cannot reuse this inert closed work-tree policy as a native runtime oracle.

Source Forge 36.2.42 under Java 17 remains unqualified: the earlier successful
source run used Java 8. Native startup, observables, save/close, EOF/exit and
original/target comparison are separate gates. Current native observation index
remains `runs: null` / `NOT_RUN`; no helper output is inserted into it.

## Developer read-only command

Run only after staging, supplying the already reviewed hashes; no placeholders
are inferred or downloaded. All five properties are mandatory. Quote complete
PowerShell `-P` arguments, especially paths ending in `.json`.

```powershell
.\gradlew.bat '-Djavax.net.ssl.trustStore=NONE' '-Djavax.net.ssl.trustStoreType=Windows-ROOT' :legacy-corpus:verifyCompositionStage `
  '-PcompositionRunRole=SOURCE' '-PcompositionRunId=<existing-run-id>' `
  '-PcompositionJavaHome=C:/Program Files/Java/jdk-17' `
  '-PcompositionReviewedPlanSha256=<reviewed-plan-hash>' `
  '-PcompositionReviewedReceiptSha256=<reviewed-receipt-hash>' `
  --no-daemon --max-workers=1
```

The developer verifier starts its own Java tool only, never Minecraft or the
stored native argv. This task is opt-in and not part of normal `check`.

## Evidence and next step

The [continuation ledger](evidence/compose-sessions-2026-09-08.json) separates
fresh tests, real-stage **read-only** checks, build/package hashes and unrun
native gates. Tests include complete inert sessions, manifest/bytecode refusal,
stage drift, unknown files, output rejection, timeout, cancellation, collisions,
post-publication failure and read-only partial recovery. Tests do not simulate
physical power loss/disk-full or prove a game behavior merely by passing.

Serial full regression/package audit passed: corpus 693 cases, 686 passed,
seven symlink-privilege skips, zero failures/errors. This continuation adds
141 cases (139 passed, two skipped). Converter tests ran in the first full
round: 831 cases, 748 passed, 83 skipped (82 opt-in environment gates, one
symlink privilege); UP-TO-DATE in the successful final regression, as were
product/runtime/workflow tests. The ledger preserves the earlier failures and
their corrections, suite XMLs and hashes. Actual SOURCE/TARGET stages passed
read-only admission with 111/223 immutable inputs and no mutation. Public JAR
remains byte-identical; the changed corpus JAR contains no test helper.

Next, same LB-08 / COMPOSE-01: define and statically test the finite native
mutable-output contract and resolve/document late launcher lookups against the
exact platform artifacts, still without executing a game. Actual arming, native
process execution and comparison require new authorization. Do not accept
COMPOSE-01, unlock LINKAGE-GENERAL or promote installation/UI from this work.
