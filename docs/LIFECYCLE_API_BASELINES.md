# Lifecycle, deferred registry reads and event delivery: exact API audit

Audit: 2026-09-06. Source: Minecraft 1.16.5 / Forge **36.2.42**, EventBus
**4.0.0**. Target: Minecraft 1.20.1 / Forge **47.4.23**, EventBus **6.2.33**,
Java 17. This document is an inspection and proposed finite proof boundary, not
authorization to rewrite calls or a claim that these features are implemented.
The existing conversion profiles remain separate and unchanged by this audit.

Only source text, mapping tables, archive metadata and `javap` declarations were
read. No original mod class was loaded, initialized or executed. No build or game
was launched by this audit.

## Exact evidence and provenance

The ForgeGradle-derived source archives and their locations are identified in
[LOGIC_API_BASELINES.md](LOGIC_API_BASELINES.md). They contain generated game
sources and are **local evidence only**, not redistributable product contents:

| Derived local archive | SHA-256 |
|---|---|
| `forge-1.16.5-36.2.42_mapped_official_1.16.5-sources.jar` | `bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376` |
| `forge-1.20.1-47.4.23_mapped_official_1.20.1-sources.jar` | `4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932` |
| `forge-1.16.5-36.2.42-srg.jar` | `26ccfe3a5e3cd2c82d2c4bfc38c254c151e8e10ef085a18149aa293db4323a34` |
| `forge-1.20.1-47.4.23-srg.jar` | `22716a4e2161afd0c0b34e3ceac3065f69a7b5d1e12a82ad1567d8df0a536ba7` |

Exact independently published Forge component archives were read from the local
resolved dependencies; these URLs identify the matching official publications,
not an unpinned current GitHub branch:

| Official component | Binary SHA-256 | Sources SHA-256 |
|---|---|---|
| [EventBus 4.0.0 binary](https://maven.minecraftforge.net/net/minecraftforge/eventbus/4.0.0/eventbus-4.0.0.jar), [sources](https://maven.minecraftforge.net/net/minecraftforge/eventbus/4.0.0/eventbus-4.0.0-sources.jar) | `8754a57ff4d15dffcab15f50b691f4a9c1108b057fbcfff5c915ce928c41f84e` | `88af63462fd2880bb12ed3a9c8fa98cff0e0b2195dd60001175f717e12a03f43` |
| [EventBus 6.2.33 binary](https://maven.minecraftforge.net/net/minecraftforge/eventbus/6.2.33/eventbus-6.2.33.jar), [sources](https://maven.minecraftforge.net/net/minecraftforge/eventbus/6.2.33/eventbus-6.2.33-sources.jar) | `c9c9aab0979e08a0489fc497ba433276648d5192608e53535bad881dfe14d712` | `6973f199d644a1ba81d8f703c8599ddff07448f2f62c5c0c664d765c3ead16a6` |
| [FML Core 1.20.1-47.4.23 binary](https://maven.minecraftforge.net/net/minecraftforge/fmlcore/1.20.1-47.4.23/fmlcore-1.20.1-47.4.23.jar), [sources](https://maven.minecraftforge.net/net/minecraftforge/fmlcore/1.20.1-47.4.23/fmlcore-1.20.1-47.4.23-sources.jar) | `cb7467136d76ed8cdbf1d6cecca40e0034cd77ce78f64a72105346deaa184daa` | `fc6df13c54c211ea9c0220ec76a2ebe1e573187112467cfc1397a25fee191ec1` |
| [Java FML 1.20.1-47.4.23 binary](https://maven.minecraftforge.net/net/minecraftforge/javafmllanguage/1.20.1-47.4.23/javafmllanguage-1.20.1-47.4.23.jar), [sources](https://maven.minecraftforge.net/net/minecraftforge/javafmllanguage/1.20.1-47.4.23/javafmllanguage-1.20.1-47.4.23-sources.jar) | `c11e456e4486152f77d7f34af37ce795e9abbf3a100663ed62abdd6ff5cd3661` | `1d786de4b0701e82d871f3ed6ac663d1ffa443ca9af21e97ef6cfc3c263278c1` |

The APIs below are Forge/EventBus names, unchanged by Minecraft member-name
mapping. Embedded Minecraft types in any newly admitted descriptor must still use
the exact mapping composition and complete target declaration checks. Reading
these archives does not grant redistribution of other data; the existing
[portable-data](PORTABLE_MAPPING_DATA.md) and [packaging-rights](PACKAGING_RIGHTS.md)
gates continue to apply.

## 1. `RegistryObject.get`: return erasure is a real ABI change

Legacy declaration: `net/minecraftforge/fml/RegistryObject<T extends
IForgeRegistryEntry<? super T>>`, final class implementing `Supplier<T>`.
Target: `net/minecraftforge/registries/RegistryObject<T>`, also final and implementing
`Supplier<T>`, but without the old generic bound.

| Instruction/member | Source production descriptor | Target production descriptor |
|---|---|---|
| Direct `INVOKEVIRTUAL RegistryObject.get` | `()Lnet/minecraftforge/registries/IForgeRegistryEntry;` | `()Ljava/lang/Object;` |
| Legacy synthetic bridge `get` | `()Ljava/lang/Object;` | Target has the ordinary generic-erased `get()Object`, not the old typed method plus bridge |
| Call through `Supplier`, `INVOKEINTERFACE Supplier.get` | `()Ljava/lang/Object;` | Same descriptor; receiver provenance and timing still require proof |

Both ordinary `get` declarations are public, non-static and non-final methods;
their containing class is final. The actual source implementations are
`net/minecraftforge/fml/RegistryObject.java:103–107` and
`net/minecraftforge/registries/RegistryObject.java:201–205`: read the stored value,
require non-null with a registry-identifier message, then return it. Absence throws
`NullPointerException` in both versions; returning null or a dummy item would not
be equivalent. Target registry-reference maintenance and holder handling are
different internals and must not be exposed as legacy registry-entry behavior.

Proposed first safe operation: a proven owned item registry object, read only after
the registration phase, using the old typed `get` immediately followed by a
`CHECKCAST` to the reviewed Item or a proven owned Item subclass. Rewrite only that
call to the native target `get()Object`, preserving the cast and all consuming
logic. Validate stack dataflow and source/target assignability, not merely nearby
text or a field's generic signature.

This is a contextual erasure operation. **Never globally map
`IForgeRegistryEntry` to `Object`**. Direct legacy registry-entry fields, arrays,
interfaces, methods, `setRegistryName`/`getRegistryName`, arbitrary registry
objects, raw escaping values and optional/absent-registry behavior stay blocked.
The alternative `Supplier.get`/bridge route needs its own receiver and lifecycle
proof; a matching erased descriptor alone is insufficient.

Late resolution is not late registration: the mod still creates and attaches its
DeferredRegister during its original construction. Reading a handle during common
setup does not authorize registering new content after the registry phase, or
loading additional mods into the running session.

## 2. `FMLCommonSetupEvent` and `enqueueWork`

Both exact `net/minecraftforge/fml/event/lifecycle/FMLCommonSetupEvent` classes
extend `ParallelDispatchEvent`. The source lifecycle source lines 13–27 and target
lines 15–28 explicitly place common setup after registration events and before
client/dedicated setup. It is parallel-dispatched; interacting with ordinary game
state directly from that callback is not equivalent to deferred main-thread work.

| Actual declaration | Source | Target |
|---|---|---|
| Public common-setup constructor, `INVOKESPECIAL` | `(Lnet/minecraftforge/fml/ModContainer;)V` | `(Lnet/minecraftforge/fml/ModContainer;Lnet/minecraftforge/fml/ModLoadingStage;)V` |
| Public `ParallelDispatchEvent` constructor | Same one-container form | Same additional-stage form |
| Public instance `enqueueWork`, `INVOKEVIRTUAL` | `(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;` | Identical descriptor |
| Public instance generic `enqueueWork`, `INVOKEVIRTUAL` | `(Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;` | Identical descriptor |

The two enqueue methods are declared on `ParallelDispatchEvent`, not directly on
`FMLCommonSetupEvent`. A source constant-pool reference may name the inherited
common-setup owner; resolve that inheritance explicitly on both sides. Do not
invent a declaration or promote a whole class merely because one method matches.
Mod construction/posting/subclassing of these lifecycle events is outside the
proposed slice: the constructor ABI and dispatch-stage ownership changed.

The old `ParallelDispatchEvent.java:20–29` locates the queue using the event class,
then passes the container's `IModInfo`. The target at lines 24–33 locates it by
`ModLoadingStage`, then passes the container itself. In both versions a missing
queue raises a runtime failure; neither is a general scheduler after startup.

### Semantic difference that a name match would miss

Old `DeferredWorkQueue.java:55–79` runs the queued CompletableFuture task wrappers
sequentially. Target FML Core `DeferredWorkQueue.java:55–114` additionally installs
and clears the active ModLoadingContext while running a task, attaches exception
handling to its future, aggregates failures, logs them and throws after processing
the queue. The old inspected implementation does not perform that aggregation or
active-context management. A task's exception may be held in its old future while
the target turns it into a loading failure. Ignoring the returned future does not
make this behavior difference disappear.

Both queue implementations explicitly disclaim a guaranteed order between mods;
their own queued work is sequential. Preserve native scheduling, do not execute
the Runnable during conversion, in the parallel callback, or immediately inside an
adapter. Do not claim globally deterministic inter-mod ordering.

A conservative first fixture should use only a bounded, non-throwing owned
Runnable with proven-safe primitive/state operations, registered during common
setup; discard its returned future and prohibit observation of active loading
context. Assert exactly-once execution and stage order using a separate runtime
probe. Division with unproved divisors, array indexing without bounds proof, opaque
calls, blocking, `join`/`get`, continuations, cancellation, supplier results,
arbitrary exception paths and deprecated `DeferredWorkQueue.runLater` remain
unsupported until a semantic recipe and negative fixtures address them.

If the implementation elects to retain native target failure semantics instead of
proving the non-throwing boundary, that is an explicit documented behavior migration,
not equivalence. This audit alone does not approve that migration.

## 3. Explicit event-bus listener registration

`net/minecraftforge/eventbus/api/IEventBus` exposes the following public abstract
interface method in both pinned binaries:

```text
addListener(Lnet/minecraftforge/eventbus/api/EventPriority;
            ZLjava/lang/Class;Ljava/util/function/Consumer;)V
```

The descriptor above is wrapped for readability only; its real JVM descriptor has
no whitespace/newlines. Argument order is **priority, receiveCancelled, eventType,
consumer**. Calls through `IEventBus` use `INVOKEINTERFACE`. All three shorter
overloads also exist in both versions: `(Consumer)V`, `(EventPriority,Consumer)V`
and `(EventPriority,boolean,Consumer)V`. Their defaults are NORMAL and false.
The explicit Class overload avoids the implementation's runtime generic
`TypeResolver` inference and is the preferable first proof route.

EventBus 4 source `EventBus.java:190–192,234–247` and EventBus 6 source
`EventBus.java:205–207,249–267` reject generic events, enforce the bus's marker/base
type and invoke the consumer through cancellation filtering. Keeping the same
method signature does not make mod-bus and Forge-bus events interchangeable.

For a first lifecycle slice, admit only the proven native mod-bus receiver,
`FMLCommonSetupEvent.class`, NORMAL priority, false receiveCancelled and an owned
consumer with one exact common-setup parameter. Validate the consumer bootstrap,
capture list, implementation handle, body and installation phase. The erased
`Consumer.accept(Object)V` bridge must cast to the exact event type; arbitrary
consumer reflection or generic-event routing remains blocked. Inferred-listener
overloads require their own proof before being transformed into the explicit form;
do not silently insert an event class based on an ambiguous lambda signature.

The native old priorities are HIGHEST, HIGH, NORMAL, LOW, LOWEST. Target 6.2.33
adds **MONITOR**, whose phase prohibits cancellation changes. The original five
named constants retain their priority ordering, but `values()`, `ordinal()`-based
logic, switch-table assumptions over all values and target MONITOR are not granted
equivalence. A later priority/cancellation slice requires separate event fixtures;
non-cancelable common setup cannot prove canceled-event behavior.

## 4. Automatic subscribers and annotated methods

The annotation owners remain:

```text
Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber;
Lnet/minecraftforge/eventbus/api/SubscribeEvent;
```

Both `EventBusSubscriber` declarations are runtime-retained, TYPE-targeted. Their
actual members are `value(): Dist[]` (default CLIENT and DEDICATED_SERVER),
`modid(): String` (default empty), and `bus(): Mod$EventBusSubscriber$Bus` (default
FORGE). The nested Bus enum provides FORGE and MOD. Target FORGE lookup uses
Bindings instead of the old direct MinecraftForge field; annotation users should
not be translated into copied loader internals.

Both `SubscribeEvent` declarations are runtime-retained, METHOD-targeted, with
`priority(): EventPriority` default NORMAL and **`receiveCanceled(): boolean`**
default false. This annotation member has one `l`; the explicit listener's source
parameter spelling is `receiveCancelled`. Parameter names are not JVM ABI, while
annotation element names are.

Old injection is `net/minecraftforge/fml/AutomaticEventSubscriber.java:37–68`;
target is `net/minecraftforge/fml/javafmlmod/AutomaticEventSubscriber.java:37–68`
in the Java FML source archive. Both first filter metadata by the annotation's
type, resolve modId/bus/distribution, then register the loaded Class with the
selected bus. Forge's runtime loading here is not permission for the static
converter to execute input classes. In particular, do not depend exclusively on
Java annotation `@Target`: validate the actual bytecode annotation placement,
elements, enum descriptors/values and ownership before preserving it.

`IEventBus.register(Object)V` distinguishes a Class argument (public static
annotated methods) from an object instance (non-static methods). EventBus 4 and 6
have different instance-handler inheritance scanning implementations. The first
automatic-subscriber fixture should therefore be a dedicated owned class with
explicit matching modId, MOD bus, both distributions, and declared **public static
void** handlers each taking exactly the reviewed common-setup event. No inherited
or overridden subscriber, generic event, wrong target, repeated registration,
extra subscriber annotation, unknown bus/side value or client-only class reference
should be admitted incidentally.

Keep this automatic route separate from explicit listener registration in the
first fixtures. Combining both for one handler can double delivery; constructing
an empty adapter to suppress that symptom would corrupt behavior.

## Corpus observations: observed dependency is not a whole-mod unlock

This audit re-fetched all Java production source paths in the three already
authorized exact commits via the official GitHub trees/raw endpoints. The trees
were complete (not truncated): 6 Clumps paths, 15 Controlling paths, 18 AppleSkin
paths. Their exact original binary hashes and permission records remain in the
[versioned corpus records](../corpus/records/) and
[rights intake](../corpus/intake/rights-candidates.json). Source-to-binary
reproducibility is not claimed.

| Observation across these exact releases | Clumps 6.0.0.28 | Controlling 7.0.0.29 | AppleSkin 2.5.1+mc1.16.4 |
|---|---|---|---|
| Common setup listener | Yes | Not found | Yes |
| Client setup listener | Yes | Yes | Yes |
| `SubscribeEvent` handlers | Not found; explicit Forge listeners occur | One screen handler | Eight handlers |
| `EventBusSubscriber` annotation | Not found | Not found | Not found |
| `ParallelDispatchEvent.enqueueWork` | Not found | Not found | Not found |
| `ForgeConfigSpec` | Not found | Not found | One CLIENT spec, nine booleans and one ranged double |
| Command-registration/Brigadier markers | Not found | Not found | Not found |

Pinned entrypoint evidence:
[Clumps](https://github.com/jaredlll08/Clumps/blob/34c57ca0bd068655a63d99eabd215219432c32d4/src/main/java/com/blamejared/clumps/Clumps.java),
[Clumps client](https://github.com/jaredlll08/Clumps/blob/34c57ca0bd068655a63d99eabd215219432c32d4/src/main/java/com/blamejared/clumps/ClumpsClient.java),
[Controlling](https://github.com/jaredlll08/Controlling/blob/1975807fd663f62d5631f94b9dee19372a5a2d08/src/main/java/com/blamejared/controlling/Controlling.java),
[AppleSkin](https://github.com/squeek502/AppleSkin/blob/5bedbcc501848f3a22a650f1f661e57d6973af83/java/squeek/appleskin/AppleSkin.java).
The AppleSkin schema also performs explicit file configuration; see its exact
[ModConfig](https://github.com/squeek502/AppleSkin/blob/5bedbcc501848f3a22a650f1f661e57d6973af83/java/squeek/appleskin/ModConfig.java).

AppleSkin's two actual `enqueueWork` occurrences belong to
**NetworkEvent.Context**, in its
[exhaustion](https://github.com/squeek502/AppleSkin/blob/5bedbcc501848f3a22a650f1f661e57d6973af83/java/squeek/appleskin/network/MessageExhaustionSync.java)
and [saturation](https://github.com/squeek502/AppleSkin/blob/5bedbcc501848f3a22a650f1f661e57d6973af83/java/squeek/appleskin/network/MessageSaturationSync.java)
packet handlers. They are not evidence for lifecycle enqueue equivalence.
The command search checked `brigadier`, `RegisterCommandsEvent`,
`FMLServerStartingEvent`, `CommandDispatcher` and `registerCommand`; zero markers
means no observed command-registration case here, not a universal proof about
all embedded dependencies or arbitrary dynamically resolved code.

Consequently lifecycle listeners are a dependency cluster observed in three exact
releases, but common setup only in two, config in one, and command registration
in zero. Automatic subscribers, lifecycle queued work and commands require new
original positive/negative fixtures. None of these counts means any of the three
whole real mods becomes convertible; their remaining entity/network/rendering/UI
closures are documented in [the semantic review](../corpus/intake/semantic-review.md).

## Proposed implementation order and acceptance gates

1. Explicit mod-bus common-setup listener plus a post-registry, owned-item
   `RegistryObject.get`/checked-cast read. Preserve original callbacks, handle
   construction and mod identity. Add a fixture whose native Item predicate
   observes state established by the callback, with exactly-once and registration
   timing recorded independently in source and target probes.
2. Add the bounded non-throwing Runnable enqueue form; assert deferred, ordered,
   exactly-once state changes through both probes. Negative fixtures cover early
   registry reads, unknown consumers, inherited owner resolution, wrong bus,
   constructor invocation, future chaining, queued failure/context observation and
   unsupported event payloads. Unknown behavior remains MANUAL/UNSUPPORTED.
3. Independently admit the finite static automatic-subscriber grammar and compare
   event delivery against the explicit route, including malformed annotations,
   duplicate registration and dedicated-server class-isolation negatives. General
   Forge events, instance inheritance, priority/cancellation and side-specific
   subscribers are later slices, not implied support.

Config persistence and commands are separate work. No config or command contract
was declared equivalent by this audit. Each needs its own real API audit, exact
source fixture, conversion operation and client/server behavior checks. In
particular, this corpus supplies no evidence for prioritizing commands by a claimed
number of whole mods unlocked.

Static analysis success, conversion success, validated bytecode, initialization,
behavior and multiplayer remain separate evidence states. This read-only audit
establishes none of the execution gates and makes no universal-compatibility claim.
