# Experimental `logic-preserving-v1`

This is a **separate opt-in developer profile**, not an extension of the default
`simple-content-v1` grammar. The latter still replaces a finite registration graph
with its existing runtime bootstrap. This profile instead retains every owned
class and executable method. Neither profile supports arbitrary Forge behavior.

The separate [COMPOSE-01 static preview](COMPOSITION_PREVIEW.md) now inspects
owned logic together with the existing datapack recipe contract in memory.
It does not extend this conversion profile or emit a composed JAR.

## Invocation and boundaries

```text
legacy-converter convert original.jar --conversion-profile logic-preserving-v1 --mapping-bundle prepared-local-data --output converted.jar --report conversion.json
```

The typed service uses `ConversionRequest(..., Optional<sha256>,
ConversionProfile.LOGIC_PRESERVING_V1)` for both `plan` and `convert`. Existing
constructors and CLI invocations default to `SIMPLE_CONTENT_V1`. The Minecraft UI
still selects the original profile; this step does not enable the new profile in
the player queue/installer or declare those flows validated for preserved logic.

Planning, conversion, publication and runtime testing remain separate. Analysis
and conversion never define, initialize or execute original classes. Closing a
prepared candidate discards it; accepted cancellation before publication cannot
publish a JAR. Existing bounded input capture, worker scheduling, private candidate
and no-replace publication are shared, not reimplemented in the CLI.

## Implemented vertical slice

The original, genuinely compiled/reobfuscated Forge 36.2.42 fixture lives in
`fixtures/logic-preservation-1.16.5`. It registers one item (`parity_token`) through
native `DeferredRegister`, uses a real `Item` subclass and overrides the native
foil/glint predicate. It contains a separate helper hierarchy and interface,
virtual/interface/super calls, constructors, instance and static state, exception
handling, loops, switch branches, static initialization, a static method reference,
a captured primitive lambda and a constructor method handle.

Its result depends on stack count, not the number/frequency of renderer calls.
Instrumentation counters are compared only in a controlled server-side trace.
The superclass enchanted-item branch must short-circuit its custom predicate.
The model uses the existing vanilla iron-ingot texture and contains English and
Portuguese translations. The fixture introduces no copied game source/assets.

Admission is deliberately narrow:

- Java 8 classfiles, at most 32 owned classes, 256 methods and 32,768 instruction/
  debug nodes, with additional archive, per-method and linkage limits.
- One exact `@Mod` entrypoint: constant identity, one item `DeferredRegister`, one
  constructor supplier, one `RegistryObject`, and mod-event-bus attachment. No
  extra entrypoint effects, registry escape or additional registration site.
- One public direct `Item` subclass, with a single no-argument constructor whose
  prefix is the reviewed native `Properties().stacksTo(1..64)` super invocation.
- Owned helpers/interfaces can retain logic only inside the closed external
  contract set. A mapping miss is a blocker, never permission to copy an external
  call unchanged. Inherited and accidental target overrides are checked too.
- Finite existing resource validation is reused to validate preserved assets/data;
  a resource index is **not** substituted for any original executable body.

The original constructor/supplier and native Forge registry execution remain in
the result. Forge 47 now owns registry-name assignment through `RegisterEvent`;
the exact native route is reviewed, not generalized to arbitrary registry APIs.
No additional runtime behavior shim is necessary for the admitted native calls.

## Exact mappings and implemented operations

[API baselines and recipes](LOGIC_API_BASELINES.md) records the real official/SRG
composition and exact declarations. The generic mapping generator is unchanged:
its `HIGH` candidates do **not** authorize transformation. `VerifiedLogicMappings`
requires a separate finite semantic recipe, exact source/target declaration,
descriptor and invocation-kind check. The report retains candidate confidence
separately from the reviewed contract decision.

Every emitted symbol change binds to catalog revision 1 of
`logic.symbol-contracts.v1`: the implemented, JDK-only
`PreservedLogicAdapter.remapDescriptor` operation. Only object-type components
change; primitive shape, array rank, argument order and arity stay fixed.
Manifest serialization binds to `logic.mod-manifest.v1`; metadata/pack operations
reuse their existing implemented adapters. No empty adapter or fake return value
is introduced. `logic.adapter-bindings.v1` builds the exact inert adapter identity
tree for JSON serialization. The required runtime identity/version remains
`legacybridge_runtime` / `0.1.1-SNAPSHOT`; old adapter IDs/revisions are unchanged.

`RegistryObject.get()` is specifically **unsupported**: its erased return changes
from `IForgeRegistryEntry` to `Object`. There is no global
`IForgeRegistryEntry -> Object` substitution. An API rename alone cannot establish
semantic equivalence or adapter permission.

## Static gates and audit records

The pipeline captures bounded input, scans owned references, compiles exact
recipes, checks original linkage, remaps with ASM `ClassRemapper`, checks the
registration envelope, validates resources, computes frames/maxima offline,
performs ASM dataflow checks and checks the exact output linkage before writing.
`LogicBodyPreservation` additionally checks class identity/version/access, member
counts, opcodes, branches, literals, field constants, handlers and debug locations
before frame serialization. It is an invariant, not a proof of arbitrary program
equivalence. Source classfile version 52 is retained and runs on Java 17.

`OfflineLinkageVerifier` checks declarations, hierarchy cycles, interface/class
kinds, missing members, staticness, access, protected receivers, final writes and
overrides, abstract requirements, invocation opcodes, supported constructor
uninitialized states and the finite LambdaMetafactory recipes. It reads trusted
JDK `jrt:` class resources, not `Class.forName` or an input classloader.

It reports `PASSED_SCOPED`, **not complete JVM linkage certification**. API indices
lack external module/nest/sealed metadata; exact platform wiring still requires
the isolated real Forge tests. The source JDK call set is restricted to reviewed
Java 8/17 common declarations; this is not a complete Java 8 implementation scan.
The converter never installs class-header stubs into the result. Header-only API
bytes exist solely for offline hierarchy/frame queries.

CompatibilityReport schemas remain unchanged. Conversion retains schema 2,
planning schema 1 and TransformationLog schema 1. Additional profile evidence
includes source/target linkage summaries, exact mapping recipes, preserved-class/
method counts and `replacedClassCount=0`. `runtimeValidation=NOT_RUN` in a static
conversion report is intentional even when that fixture has a separate runtime
receipt. All failures abort without a partial output.

## Explicit data preparation

There is no operational Gradle-cache discovery or downloader in this profile.
Select a prepared local bundle or explicit mapping profile. In addition to the
existing pinned official mappings and mapped Forge declarations, the native bus
route requires actual eventbus 4.0.0 (source) and 6.2.33 (target) declarations plus
the pinned target FML modules. Missing input fails with an actionable diagnostic.

`prepare-mappings --mapping-profile ... --output new-directory` exports these
inert declarations with hashes/provenance for local offline reuse. Current
mapping/API bundles remain `LOCAL_USE_ONLY`: this step grants no redistribution
rights and embeds neither those bundles nor old Minecraft/Forge implementations.
See [portable-data policy](PORTABLE_MAPPING_DATA.md) and the existing repository
mapping-data/license documentation for the full release gates.

## Validation and remaining exclusions

[Separate validation evidence](LOGIC_PRESERVING_VALIDATION.md) distinguishes build,
ASM/linkage, conversion, source execution, target execution and behavior parity.
The two `logic-runtime-probe-*` projects are test-only native Forge mods, excluded
from the public artifact. Their explicit runtime reflection only probes already
registered fixture methods; it is not part of the converter.

Not supported: arbitrary item hooks, blocks in this new profile, entities, block
entities, networking, capabilities, worlds/dimensions/worldgen, custom GUIs or
renderers/shaders, mixins/coremods/ATs, arbitrary dependencies/reflection, changed
argument/return contracts, default-interface dispatch, native methods, records,
sealed/nestmate access, `jsr/ret`, `ConstantDynamic`, `altMetafactory`, standalone
method handles, boxing/adaptation, or general constructor delegation/branching
with uninitialized values. Accepted dynamic sites are the intersection of the
mapping compiler and offline verifier, not every form either can parse.

No percentage of symbols, number of tests or single fixture result is a
percentage of compatible mods. This remains experimental; visual client and
multiplayer behavior need their own evidence.
