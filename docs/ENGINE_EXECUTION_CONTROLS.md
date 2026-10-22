# Cooperative engine execution and private preparation

2026-09-12: [aggregate controls and measurements](PROMPT_02_HEADLESS.md) now
limit service instances in one classloader to one active / 33 admitted jobs,
add `WAITING_FOR_ENGINE`, a cooperative `MEMORY_PRESSURE` brake and separate
`EngineJob.metrics()`. These are not process isolation or hard heap limits.

The synchronous conversion implementation is still `SimpleContentConverter`.
The CLI and asynchronous service share that implementation, not a second
conversion grammar. This change adds no supported legacy features and executes
no input classes.

## Non-publishing planning

`prepare(input, prospectiveOutput, mappingInputs)` returns an `AutoCloseable`
`PreparedConversion`. It performs the full existing semantic/resource/ASM gates
and serializes candidate bytes privately. It does not create the destination.
The prospective output must have an existing parent and must not already exist
or alias the input. Private input snapshots are cleaned after scanning.

The immutable preparation report is `LEGACY_CONVERSION_PLAN`, schema 1:

- `status`: `PLANNED` or `ABORTED`.
- `output`: null, always; a plan is not a file installation.
- `candidate`: proposed filename/SHA-256 only for an accepted candidate.
- `bytecodeModified` and `transformationsCommitted`: false.
- The transformation log describes tentative changes, not committed changes.

`publish(prepared)` consumes the candidate exactly once and returns the existing
`LEGACY_CONVERSION`, schema 2 report. Both refusal and success retain the existing
conversion report contract. Candidate bytes are private, cannot be edited by a
caller, and are released on close or publication. Do not retain preparations in
an unbounded UI history; retain their immutable bounded summary/report instead.
Repreparing during conversion intentionally repeats the exact validation rules.

The existing `convert` methods delegate to prepare/publish. Immediately before
publication, the original is rehashed, output nonexistence is rechecked, and only
a fully written/closed temporary is moved to the destination without replacement.
A changed source aborts with no output. Existing path checks do not constitute a
filesystem sandbox or eliminate hostile concurrent parent-directory replacement.

## Worker-scoped controls

`OperationControl` is a JDK-only, non-inheritable `ThreadLocal` scope installed
by the service on its worker. Existing synchronous callers without a scope keep
the archive/conversion policies already enforced by the engine. A scope cannot
be nested or used by two workers at once. The asynchronous service and shared
`EngineAdmission`, not this class, own executor and admission capacity.

`OperationLimits` defines compressed source bytes, actual inflated work, captured
payload work, and a cooperative monotonic deadline. Source bytes measure the
maximum observed source length on each read/rehash; inflated and captured budgets
accumulate actual work across the job, including repeated scans. Capture includes
compressed output serialization. Declared ZIP lengths alone are not trusted.
These source-mod payload counters do not charge mapping baselines against the
mod's byte allowance: mapping/API inputs have their own fixed file, inflation,
class and member limits in `BoundedInputs`/`ApiIndex` and the portable data reader.
They do share the job's deadline/cancellation checkpoints during file reads,
class/member indexing, mapping merges and rule generation.
The deadline begins when the control is constructed, so queue time counts if the
service constructs it at submission.

These are **not hard JVM heap limits**. Trees, indexes, strings, immutable copies,
parsers and buffering have additional allocation overhead. Existing fixed bounds
on archive entries, class files, instructions, references, JSON structures and
image dimensions remain in force. A separate worker process would be required
for a hard memory boundary; this milestone does not claim process isolation.

Checkpoints cover snapshot reads, ZIP preflight and inflation, rehashes, class
and reference scans, extractor instruction loops, descriptor/adapter stage
boundaries, bounded JSON nodes, resources, serialization, and final publication.
Some ASM/internal parser/image/OS operations remain opaque until they return.
Timeout/cancellation is therefore cooperative, not a maximum wall-clock kill
guarantee. The input is bounded even where interruption is not immediate.

`OperationStoppedException` retains typed `CANCELLED`, `TIMEOUT`, `INPUT_LIMIT`,
`INFLATED_LIMIT` and `CAPTURE_LIMIT` codes. It must propagate as control flow,
not be converted into malformed-bytecode or unsupported-feature diagnostics.
The helper `rethrowIfPresent` preserves stops wrapped by ASM's analyzer.

Progress is typed by actual stage. `total=-1` explicitly denotes an unknown total;
stages do not fabricate percentage completeness. Callbacks run on the worker and
must enqueue UI work rather than touch render state. Observer exceptions are
isolated, except fatal JVM/thread termination; blocking observers cannot be
preempted and are an application responsibility.

## Cancellation and commit boundary

Cancellation and `beginPublication` share one synchronized state transition.
If `requestCancel()` returns true before that transition, the final move cannot
begin, and no candidate is published. Cancelled snapshot/candidate writes clean
their owned temporaries. Once the transition wins, cancellation returns false;
deadline/interrupt checkpoints no longer relabel a successfully published result
as cancelled. The service must wait for the actual publication outcome.

Publication can still fail with an I/O error after the barrier. The barrier is a
no-cancellation boundary, not a guarantee that the OS move will succeed. The
service's read-only terminal completion must likewise seal late cancellation
before completing its result future. This engine performs no mod installation,
activation, game shutdown, or restart.
