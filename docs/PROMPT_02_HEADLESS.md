# LB-02: explicit data review and aggregate engine controls

Review: 2026-09-12. `COMPOSITION_DATA_SCOPE` implementation is delivered, with
an initial measured `ENGINE-ISOLATION` implementation. This does **not** accept
the whole LB-02 prompt, introduce process isolation or promote another public
conversion profile. States and the next development cursor remain in
[the contract](contract/status.json).

## Composition data decision

The existing individual logic+recipes preview uses
`LogicPreservingConverter.prepare`, `VerifiedLogicMappings` and
`LogicApiNamespace`. These consume four source/target official/SRG tables and
two API indexes for exact member rules, ancestor/access closure and platform
ownership/collision rejection. A requested-symbol map alone does not establish
the absence of unknown external or platform-owned classes. The included
annotation-only cohort inventory cannot replace this proof.

Decision: retain **explicit, locally prepared full data** for this operation.
Do not disguise the full `LOCAL_USE_ONLY_NOT_APPROVED_FOR_REDISTRIBUTION`
bundle as a redistributable miniature, fall back to an incomplete inventory or
embed old Minecraft/Forge implementations. Included simple-content and exact
authored cohort contracts are unchanged. No acquisition or license change was
performed. The existing [preparation and rights audit](PORTABLE_MAPPING_DATA.md)
governs these local inputs. Reviewing data is not permission to redistribute it.

The existing local bundles deliberately differ:

| Bundle | Manifest SHA-256 | Files / bytes | Fixture uses |
| --- | --- | --- | --- |
| logic-preserving-v1 | `0edd3ba641dbf858b7464096794a3caf4efca9d04ef4484a740a9599e9aec2bb` | 7 / 35,272,616 | logic, food, datapack, individual composition |
| lifecycle-preserving-v1 | `c21faf7b47d88ec2393cc3bd238742059e597f83ab448eaeca243824a3540d2f` | 7 / 35,612,459 | lifecycle, with its Brigadier/NightConfig declarations |
| network-snapshot-v1 | `9aaea9e90a95afe3a8ba1c359230841c8badaa833106d37e21f9de3bd4b7bcf4` | 7 / 38,461,855 | network, with its source/target Netty declarations |

`completeMappingEnvironment=true` means the six required index kinds and
declarations parsed successfully. **It does not mean every optional library,
dependency, semantic rule or supported profile is present.** The review's
`eligibleDataOperations` describes data scope, not mod conversion authorization.
Per-mod linkage/dependency/semantic gates still decide.

## Typed review and binding to reviewed data

`LegacyEngineService.inspectMappingData(MappingData)` runs the existing strict
loader off the caller/render thread. `MappingDataReview` retains a bounded
immutable summary, not the parsed indexes. Schema-1 JSON includes scope,
completeness, index counts, provenance hashes, manifest fingerprint and
distribution policy. Publisher authentication, semantic equivalence, conversion
authorization and installation authorization are explicitly false.

The CLI uses that service, not a second validation implementation:

```text
legacy-converter inspect-data --builtin simple-content-v1
legacy-converter inspect-data --builtin cohort-preview-v1
legacy-converter inspect-data --mapping-bundle C:/local-data/lifecycle-bundle
legacy-converter mappings mod.jar --mapping-bundle C:/local-data/lifecycle-bundle --mapping-sha256 <manifest-sha256-from-review>
legacy-converter convert mod.jar --mapping-bundle C:/local-data/lifecycle-bundle --mapping-sha256 <manifest-sha256-from-review> --conversion-profile lifecycle-preserving-v1 --output staging/converted.jar
```

`inspect-data` accepts only an explicit bundle or identified included contract.
It never reads an environment-selected bundle/developer profile, exports data,
downloads anything or approves conversion. `--mapping-sha256` is optional for
backwards-compatible CLI use; when supplied it requires `--mapping-bundle`.
Malformed, repeated or misplaced pins are errors.

The existing composition screen gains **Data / Dados** to review the prepared
local directory selected on the parent screen. It displays the summary and
uses the existing copy-details action. The UI calls the typed controller and
service: no CLI, stdout parsing or System.exit. Heavy reads stay on the shared
coordinator/engine path with progress, cancellation and retryable errors.
Labels/help are en_us and pt_br. Native focus, resizing and visual verification
of this new action are NOT_RUN.

A successful review binds the directory's manifest SHA-256 for subsequent
composition/cohort/simple requests in the **same controller session**. Each
request revalidates the pin and payload hashes; no parsed index is cached.
Manifest drift fails with `MAPPING_DATA_CHANGED` before conversion. A failed or
cancelled review cannot publish a partial summary or erase the previous binding.
Explicitly reviewing the directory again acknowledges a new manifest. Reopening
a session does not persist that binding. Pins detect changes, not publisher
identity, semantic equivalence or hostile filesystem races. An unreviewed
explicit selection retains the previous strict per-load validation.

Review alone creates no job receipt, candidate, Apply permission or workspace
file. Composition/cohort previews still lack public publication/install authority.

## Aggregate execution and memory decision

All `LegacyEngineService` instances in the **same engine classloader** share one
active-work permit and at most 33 admitted jobs, including active work.
Per-service queue bounds also apply. `ENGINE_CAPACITY` reports aggregate
admission refusal. `WAITING_FOR_ENGINE` has timed permit waits, cancellation
checks and queue time charged to the deadline. Leases release on success,
failure, cancellation, timeout and submission rejection, before completion
callbacks. Fatal JVM errors poison the affected service; this does not guarantee
that a shared JVM survived an OOM. Timed acquisition/fairness use the JDK
[Semaphore contract](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Semaphore.html).

Active checkpoints apply a 32 MiB shared-heap headroom brake, returning typed
`MEMORY_PRESSURE`. Full-bundle parsing also requires configured maximum heap
capacity of at least 32 MiB plus six times payload bytes. The multiplier is an
**admission heuristic**, not an allocation/retained-size proof. No forced GC,
heap increase, process launch or retry occurs. The publication barrier still
rejects late cancellation; new memory checks cannot relabel a committed file.

These measurements use `maxMemory`, `totalMemory` and `freeMemory`, not physical
RAM/native allocations. Shared heap includes other work and uncollected objects;
headroom is not retained live size. See the JDK
[Runtime memory methods](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Runtime.html).

`EngineJob.metrics()` exposes schema-1 largest source size, cumulative inflated
and captured payload work, sampled shared-heap peak and sample count. Metrics
are separate from canonical reports to preserve deterministic CLI/service
comparison. They explicitly deny hard-heap limits and job-only attribution.

Decision: this bounded finite path stays in-process. A **hard memory guarantee
requires a separate worker process/protocol**, neither implemented nor claimed.
These shared limits exclude foreign classloaders and direct synchronous callers.
Opaque ASM/JSON/image/OS calls may allocate or block between checkpoints.
Exact retained ASM-tree/index/image attribution, native/RSS profiling,
adversarial resource-limit qualification and coexistence under runtime load
remain open acceptance work. Existing structure/graph/resource/image bounds
remain; no limit was raised.

## Validation and remaining work

See [the exact-artifact ledger](evidence/prompt02-headless-2026-09-12.json).
The focused final round passed 101 JUnit cases with zero failures/errors and
one native-symlink case skipped by the existing Windows privilege assumption.
Closeout added 75 passing contract checks and two existing symlink privilege
skips: 176 distinct passing cases / three skips in total, not repeated runs
counted as extra tests. Assemble/audits/installed CLI passed; the final artifact
hash was unchanged on rebuild.
Six fixture profiles passed installed CLI/service equivalence in separate
cacheless Java17 processes; composition and exact-cohort private previews also
passed. Sources were only ZIP/bytecode/resource inputs, never on the classpath.
These Java processes are test-only engine checks, not production isolation or
Minecraft launches. The deprecated Java17 SecurityManager is a test-only access
tripwire, not an OS sandbox or an offline-game certification.

Initial focused tests exposed two false `MEMORY_PRESSURE` refusals: full-index
capacity estimates were compared to momentary free headroom, including
uncollected garbage. That was corrected to check maximum capacity separately
from the runtime brake and regression-tested, without raising heap limits.
The first matrix correctly refused lifecycle with the generic logic bundle:
NightConfig's `UnmodifiableConfigWrapper` ancestor was absent. Repeating with
the pre-existing lifecycle bundle passed; no ancestor/dependency was discarded.

Build, static conversion, ASM/linkage, client/server initialization, behavior
and multiplayer are separate dimensions. Extensive/native QA, COMPOSE-01
acceptance and packaging-rights clearance remain pending. Experimental/Beta;
no new conversion features, public cohort profile, restart, acquisition or
publication. Development may advance to LB-03 technical relinking preparation
while these LB-02 acceptance obligations stay visible; it is not whole-prompt
completion or permission to change the rights holder's license.
