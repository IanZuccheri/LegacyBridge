# Experimental lifecycle-preserving-v1

This opt-in developer profile extends the **shared body-preserving engine**, not
the default `simple-content-v1` grammar or the previous `logic-preserving-v1`
admission contract. It retains four owned Java 8 classes and all 22 methods in the
new original fixture. No registration bootstrap replaces their executable bodies.
The player UI/transactional installer still selects the simple profile; this step
does not enable a new player-facing conversion category.

```text
legacy-converter convert original.jar --conversion-profile lifecycle-preserving-v1 --mapping-bundle prepared-local-data --output new.jar --report conversion.json
```

The reusable API selects `ConversionProfile.LIFECYCLE_PRESERVING_V1`. Analysis,
planning, conversion, offline validation, publication and separately authorized
runtime tests remain distinct operations. Input classes are never defined or
initialized by the converter. Unknown external references, including references
that a remapper would otherwise leave unchanged, abort without an output JAR.

## Separate admitted contracts

| Slice | Implemented boundary | Not implied |
|---|---|---|
| Registration | One native item DeferredRegister, owned constructor Supplier and RegistryObject; exactly one original constructor attachment to the mod bus | RegistryEvent/RegisterEvent callbacks, extra registries, custom registries, blocks or foreign suppliers |
| Late reference | One proven owned item handle, read in the queued common-setup action; preserve the immediate Item cast | Early reads, optional entries, arbitrary Supplier/bridge calls, foreign registry entries |
| Lifecycle/listener | Native FMLCommonSetupEvent on its own mod bus; explicit Class overload or native Consumer-only overload with a proven zero-capture static method reference | Client/dedicated setup, inherited/instance handlers, inferred arbitrary event types, changed event constructors |
| Deferred work | One native enqueueWork(Runnable); finite safe reads/state writes; returned future discarded | Immediate execution, future escapes/chaining, arbitrary exceptions/context observation, blocking or general schedulers |
| Subscriber | One owned static public RegisterCommandsEvent handler, explicit matching modId and FORGE bus, default distribution pair | Mod-bus command events, client-only classes, instance/inherited subscribers, duplicate attachment |
| Configuration | One native COMMON spec with a boolean and ranged integer; literal defaults/range; direct native loaded-value getters | CLIENT/SERVER scope migration, config synchronization, explicit file I/O, custom validators/types or config reload listeners |
| Commands | Native literal/integer Brigadier tree, linked owned permission predicate and execution callback, preserved integer return and state | Chat/text migration, custom argument codecs, suggestions, players/entities, arbitrary external command APIs |

These contracts currently compose one closed four-role envelope (entry, plain Item
subclass, config holder and command subscriber). They are not six independent
general-purpose converter backends. The explicit and inferred fixtures are two
source forms of the **same mod**, not two newly supported real mods. Native
Consumer inference is retained, not rewritten into a guessed Class-token overload.

The exact source corpus observes lifecycle listeners in three releases, common
setup in two and config in AppleSkin only. It currently has **no command or
automatic EventBusSubscriber example**. Those are new original fixtures; they do
not establish AppleSkin config/file-I/O equivalence or unlock any complete real
release. See [the API/corpus audit](LIFECYCLE_API_BASELINES.md).

## Rules and semantics

`VerifiedLogicMappings` composes the pinned official and SRG namespaces and
checks independently named recipes against the real source/target declarations,
member access, inherited resolution and invocation kind. Generic HIGH candidates
remain separate from exact VERIFIED contract decisions. Every change has an
implemented catalog operation and a TransformationLog entry.

Two descriptor changes are contextual, not class aliases:

- `RegistryObject.get():IForgeRegistryEntry` becomes target `get():Object` only
  at the proven late call site. No `IForgeRegistryEntry -> Object` map exists;
  retained fields/arrays/interfaces/references to that removed type fail linkage.
- `registerConfig(Type,ForgeConfigSpec)` becomes `registerConfig(Type,IConfigSpec)`;
  the exact target ForgeConfigSpec implementation satisfies that interface.

`LifecycleContractAdapter` implements these offline descriptor operations plus
the separate profile manifest/binding serialization operations. Native scheduling,
registrations, config correction and command execution stay native. No old Forge
implementation or empty runtime shim is included. Existing adapter IDs/revisions
and runtime identity `legacybridge_runtime` / `0.1.1-SNAPSHOT` remain unchanged;
new operations have their own revision-1 identities.

`LifecycleProfileShape` checks phase ownership and closed instruction envelopes:
constructor 18/15 instructions, common setup 9, queued work 13, config initializer
20, config getters 5, inert helper constructors 3 and Item constructor 8. These
limits deliberately refuse equivalent-but-unreviewed alternative code shapes.
The linked command execution body keeps its allowed arithmetic, control flow and
state rather than being replaced by an oracle. Unlinked helper calls cannot
bypass the early-registry/config gates.

Common setup is parallel-dispatched; enqueueWork remains deferred to the native
loading queue. No total order between mods is invented. The selected events are
non-cancelable and use NORMAL/default false cancellation reception. Nondefault
priorities, canceled-event delivery, logical-side-specific payloads and physical
client subscribers remain UNSUPPORTED; this fixture cannot prove those contracts.

Forge 47 additionally aggregates failed deferred tasks and supplies active loading
context. The accepted task cannot inspect that context or perform unproven throwing
operations. Unexpected platform/resource failures are not a promised equivalent
failure trace. Ordinary command exceptions remain their original body/Brigadier
behavior; the converter does not suppress them.

COMMON is loaded independently on both physical sides and is **not synchronized**.
The fixture reads loaded values in deferred setup and on command execution. A
saved file, a native reload and another startup are different events. Runtime
tests of prepared files on new startup are not live-reload certification.
Native integer correction clamps out-of-range numeric values to the range;
it does not always substitute the default. See [config/command source evidence](CONFIG_COMMAND_API_BASELINES.md).

## Dependencies and data

The [typed dependency graph](DEPENDENCY_GRAPH.md) retains every declaration and
checks explicit cohorts by physical side: missing mandatory dependencies, versions,
duplicates and BEFORE/AFTER cycles. Actual Forge ordering is grouped by artifact,
not guessed from modId alphabetical order. A real source/target secondary-modId
ordering divergence is refused. The topological result does not predict discovery
tie-breaking or create a total lifecycle task order.

This conversion entry point currently has only a single selected mod and the exact
platform cohort. Third-party mandatory dependencies therefore block; the separate
graph API can inspect explicit larger cohorts, but does not authorize their
conversion. Optional absent dependencies are retained. Metadata changes only the
approved platform ranges and adds the required runtime edge; side, mandatory flag,
ordering and other dependency version ranges survive a checked multiset invariant.

No operational Gradle-cache search or hidden download was introduced. Explicit
mapping preparation now also needs actual Brigadier 1.0.17/1.1.8 and NightConfig
3.6.3/3.6.4 API declarations for the finite linkage closure, in addition to the
previous pinned Forge/eventbus/FML declarations. Exported bundles carry hashes and
provenance and remain **LOCAL_USE_ONLY_NOT_APPROVED_FOR_REDISTRIBUTION**. They are
not bundled with the public JAR; the existing rights/data-preparation gate remains.

## Evidence boundaries

See [separate validation receipts](LIFECYCLE_PRESERVING_VALIDATION.md). Static
conversion reports intentionally retain `runtimeValidation=NOT_RUN`, even when a
separate exact-artifact runtime test succeeds. Report/TransformationLog schemas
remain versioned; new source/target dependency graphs each have schema 1.

No arbitrary lifecycle, config, command, rendering, networking, entity or
multiplayer support is claimed. No new corpus compatibility percentage is inferred
from resolved symbols, passing tests or two forms of one authored fixture.
