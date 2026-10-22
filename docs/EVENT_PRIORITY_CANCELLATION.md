# LB-09: finite event priority and cancellation candidate

Development slice: `LIFE-EVENTS / EVENT_PRIORITY_CANCELLATION`. This is a **private
static candidate**, not a seventh conversion profile or public Convert/Apply route.
No source mod class is loaded, initialized or executed. Native client/server,
behavior, discovery, activation and multiplayer validation are **NOT_RUN**.

## Implemented vertical slice

The original authored Forge 36.2.42 fixture in
[fixtures/event-priority-1.16.5](../fixtures/event-priority-1.16.5) has four genuine
Java 8 classes and twelve methods, one plain Item, one local-tag smelting recipe,
models and translations. Its public entry registers the item and one explicit
MOD-bus common-setup listener, then installs two Forge-bus listeners. The setup
callback only enqueues the owned work; it does not post during parallel setup.

An owned final `@Cancelable Event` has only a pure zero-argument super constructor.
A final helper owns zero-initialized private primitive observations. Its HIGH
listener appends 1 and cancels; its LOW listener appends 2. The LOW listener's
literal receiveCancelled flag is retained, including the false variant.
Queued work posts one fresh event synchronously and retains the returned boolean.
No handlers are synthesized, rescheduled, discarded, or run by the converter.

Expected isolated native oracle (not executed): trace is 0 before queued work;
HIGH/LOW with receiveCancelled=true produces 12 and post returns true;
receiveCancelled=false produces 1 and post returns true. This concerns a fresh
fixture-only session without foreign listeners, not repeated public helper calls
or interactions with other mods. Native thread, lifecycle and event-transformer
behavior must still be measured independently.

## Exact source audit

Pinned source artifacts inspected locally (declarations and implementation):

| EventBus | Source JAR SHA-256 |
| --- | --- |
| 4.0.0, source Forge 36.2.42 | `88af63462fd2880bb12ed3a9c8fa98cff0e0b2195dd60001175f717e12a03f43` |
| 6.2.33, target Forge 47.4.23 | `6973f199d644a1ba81d8f703c8599ddff07448f2f62c5c0c664d765c3ead16a6` |

Reviewed `IEventBus`, `EventBus`, `Event`, `EventPriority`, `Cancelable`,
`EventListenerHelper` and `EventSubclassTransformer`, plus the already pinned
Forge lifecycle sources in [LIFECYCLE_API_BASELINES](LIFECYCLE_API_BASELINES.md).
Sources remain development inputs; no old game/Forge implementation is embedded.

Both baselines expose the explicit priority/boolean/Class/Consumer overload.
MOD and Forge bus registration are separate contracts; shared signatures do not
make them interchangeable. Both dispatch synchronously in listener order.
Priorities HIGH and LOW have the same relative order; ordering with arbitrary
third-party listeners or same-priority registrations is not proved here.

Important differences, not erased by mapping:

- EventBus 6.2.33 adds MONITOR and rejects cancellation after that phase. It is
  not a legacy equivalent and is blocked in this candidate.
- Source post returns isCancelable AND isCanceled; target returns isCanceled.
  Equivalent only for this owned cancelable event with no state overrides.
- Source cancellation filtering checks cancelability as well as state; target
  optimizes using cancellation state and declared event shape. No noncancelable
  overrides, generic events, inherited payload or phase observations are allowed.
- The source Event constructor calls setup, and source listener discovery can
  instantiate events. Cancelability is provided by the source event subclass
  transformer; target has annotation-aware helpers and transformer optimizations.
  The event constructor must therefore be pure and its runtime-visible annotation
  must be retained. Static verification is not proof of native transformer wiring.
- Target deferred work sets an active context and aggregates work failures
  differently from the source. The owned callbacks here contain only reviewed
  primitive effects and native event dispatch; arbitrary throwing callbacks,
  context inspection and foreign-listener interactions are outside this contract.
  No equivalence of error aggregation is claimed.

The general bus/cancellation terminology is corroborated by
[Forge 1.20.1 events documentation](https://docs.minecraftforge.net/en/1.20.1/concepts/events/);
the exact pinned sources take precedence over general documentation.

Corpus motivation: the already reviewed Clumps commit
`34c57ca0bd068655a63d99eabd215219432c32d4` uses explicit listeners and
[a custom EXPCloneEvent](https://raw.githubusercontent.com/jaredlll08/Clumps/34c57ca0bd068655a63d99eabd215219432c32d4/src/main/java/com/blamejared/clumps/events/EXPCloneEvent.java).
That event is **not cancelable**, carries entity references and supports mutation.
This authored cancellation fixture exercises an adjacent API contract; it does
not reproduce Clumps, unlock that release, or establish support for its entities.
No new real-mod acquisition, binary redistribution or rights decision occurred.

## Shared implementation and authority

`LegacyEngineService.planEventPriority` runs on the existing bounded worker and
uses explicit, hash-bound mapping/API data. It requires the lifecycle base selection
but is distinct from normal conversion. There is no CLI command, screen route,
candidate export or installation admission for this slice.

The shared pipeline performs safe archive scan, ASM validation, independent finite
effect proof, exact native/member/opcode mapping checks, source offline linkage,
body-preserving remapping, target proof comparison, resource validation, frame
generation, target linkage, complete byte ownership, temporary serialization,
safe ZIP re-open and re-derived linkage/effect proof. Returned data is proof/hash;
the prospective output remains absent. Cancellation cannot publish the candidate.

`EventPriorityContract` recognizes roles from class/member structure, not fixture
class names. Its admission-only projection reuses the existing plain Item registry
and resource contract. Projected trees are NEVER emitted: every original class,
method, control-flow instruction and debug record follows the existing body
preservation checks. The original archive and resource bytes remain intact.

`VerifiedLogicMappings.compileEvents` adds exact contracts only behind this proof.
The ordinary mappings entry points and six existing conversion grammars retain
their previous boundaries. `OfflineLinkageVerifier` independently re-derives the
event proof before admitting the owned Consumer SAM; passing a policy flag alone
cannot authorize an arbitrary event.

`EventArtifactAdapter` implements two real OFFLINE metadata operations: a distinct
`event-priority-candidate-v1` static-only manifest and versioned binding document.
The shared descriptor and lifecycle operations are explicitly bound too.
No new executable runtime adapter, old API emulation, empty stub or runtime ABI
change is introduced. Compatibility and transformation report schemas stay 1.

## Validation and remaining work

Artifact hashes, serial commands, actual test counts and initial failures are in
[the evidence ledger](evidence/event-priority-2026-09-19.json).
Positive checks include original/readback proof, service parity, deterministic
candidate hashes, exact resource bytes, renamed owned classes and both cancellation
flags. Negative checks include missing/nonvisible Cancelable, event payload or
overrides, wrong bus/side, premature real RegistryObject.get, constructor effects,
unregistered/duplicate callbacks, MONITOR, unsupported dependencies, hidden effects,
unknown annotations/SAMs and cancellation at publication boundaries.

Still unsupported: arbitrary events, entity payloads, instance subscribers,
generic event types, results/uncancellation, MONITOR, event inheritance, listener
unregistration/re-registration, late registry reads in this candidate, third-party
listener interaction, client-only events and native queue-error equivalence.
Existing lifecycle-base late reads are a separate grammar, not implicitly composed.

LIFE-EVENTS remains PARTIAL; COMPOSE-01 requires and native comparisons are not
waived. The next headless slice is **LB-09 / LIFE-CONFIG / CLIENT_CONFIG_SCOPE**:
a finite client config registration/read contract with verified physical-side
guard, separate from COMMON/SERVER and reload. Audit pinned APIs, start from the
existing lifecycle fixture, retain real defaults/validation, block wrong-side
reads and do not open the game. Static prerequisites permit development, not
acceptance or public promotion. Broader event forms remain listed above.
