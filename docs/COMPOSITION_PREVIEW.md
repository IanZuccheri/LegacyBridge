# LB-08 / COMPOSE-01: static composition preview

This is an implemented **developer inspection**, not a seventh conversion profile.
It combines the existing finite owned-body logic contract with the existing
datapack recipe/resource contract. The six `ConversionProfile` values, default
`simple-content-v1`, CLI conversions, runtime ABI and public installer admission
are unchanged. Mutable acceptance remains in [the contract](contract/status.json).

Continuation: a separate [private candidate API](COMPOSITION_CANDIDATE.md) now
serializes, reopens and validates composed bytes, then discards them. The preview
API described below retains its earlier no-serialization behavior. Neither API
publishes or installs a result. The candidate adds two implemented OFFLINE metadata
operations, not gameplay support or a seventh public conversion profile.

## Implemented boundary

`LegacyEngineService.previewLogicWithRecipes(ConversionRequest)` submits work to
the existing bounded worker. The request must name `LOGIC_PRESERVING_V1` as its
base and explicitly select complete local mappings/API data. This does not grant
the ordinary `convert` operation permission to combine profiles. Built-in finite
simple-content data are insufficient and are refused. There is no download,
Gradle-cache discovery, UI dropdown, CLI stdout parsing or class loading.

`LogicPreservingConverter.previewLogicWithRecipes` uses the same implementation
as the ordinary body-preserving converter up to its in-memory proof boundary:

1. Capture/hash the archive with existing ZIP and ownership limits; validate
   source metadata/dependencies. Read source classfiles as data, never execute them.
2. Use the existing exact reviewed recipes, source offline linkage/access checks,
   remapper and body-preservation verifier. Infer the finite item inventory using
   `LogicProfileShape`, without replacing the original registration bodies.
3. Resolve an immutable operation graph, then validate *all* resources with
   `ContentResourceValidator.validateDatapackRecipes`. A recipe is required.
4. Compute frames/maxs using offline declarations, run ASM dataflow and target
   offline linkage/access checks, and verify that every original class remains.
5. Return `CompositionPreview`; discard the inspected class arrays. **Stop before
   target metadata, adapter manifest, archive serialization and publication.**

The preview has no candidate bytes, candidate hash or installation authority.
Its internal proof also has a null candidate; it cannot publish through the
existing `PreparedConversion` path. No directory or JAR is written by the preview.
The prospective destination is checked using existing no-overwrite/path rules,
but is not created. A structured failure blocks the whole preview. The ordinary
simple-content, datapack-bootstrap and logic converters refuse the combined fixture.

## Graph contract (schema 1)

`CompositionPlan` is immutable, deterministically topologically ordered and
explicitly labelled `STRUCTURAL_GRAPH_NOT_AUTHORITY`. Arbitrary construction of
this Java value is not semantic admission; no executor accepts caller-supplied
graphs. Only the engine's actual proof can produce an admitted preview.

Each operation names a fixed implemented validator/transformer, revision,
preconditions, phase, original runtime context and class/resource claims pinned
by source SHA-256. Dependencies express proof order, **not runtime rescheduling**.
The report separately records all real data-source hashes, exact mapping recipes
and actual adapter bindings. A generic HIGH candidate is not automatically VERIFIED.

| Operation | Implementation and effect |
| --- | --- |
| `logic.owned-bodies` | Existing `LogicBodyPreservation`, exact remapping, finite shape and offline linkage checks; owns every mod class. Retains supplier/constructor/static-initializer order, MOD bus, original virtual-call context and BOTH-side envelope. |
| `data.recipe-resources` | Existing `ContentResourceValidator.validateDatapackRecipes`; depends on the proven item inventory; owns assets/data and requires byte preservation. Datapack loading/reload remains native, not invoked by analysis. |

Duplicate IDs, ownership overlap, missing prerequisites, cycles, protected
platform class claims and invalid paths are rejected. Body preservation conflicts
with bootstrap graph replacement even when file claims are disjoint. The enum
can describe the *existing* replacement operation for conflict detection; this
preview never schedules it. Metadata has no claim because it is not transformed.

Limits: at most eight operations, 4,096 claims globally, 512 characters per claim
path. Existing logic admission further restricts classes/methods/instructions.
The service still uses one worker, eight queued jobs, 64 MiB source/capture,
128 MiB inflated payload and a cooperative two-minute deadline by default.
Cancellation checkpoints are shared; cancellation cannot expose candidate bytes.
These are workload budgets, **not a hard process-wide heap ceiling**. The preview
adds no executor, reflection, downloads or temporary archives.

## Versioned operation documentation

Legacy API: pinned Forge 36.2.42 `DeferredRegister`, registry suppliers and
Minecraft 1.16.5 `Item.isFoil`/`ItemStack.getCount`; native cooking recipe JSON and
local item tags.

Target API: pinned Forge 47.4.23 registries and Minecraft 1.20.1 Item/ItemStack,
`SimpleCookingSerializer` and local item tags. Actual SRG/declaration composition
is the existing [logic API contract](LOGIC_API_BASELINES.md), not a name guess.

Transformation: the existing reviewed owned-body remaps operate in memory;
recipe/model/tag/translation bytes are validated without rewriting. Each logged
symbol change retains its existing implemented adapter and exact rule. No new
mapping table, adapter, runtime identity or resource rewrite is introduced.

Behavior differences: target cooking recipe-book category defaults to MISC;
identical client book grouping is not promised. Runtime effects are proposed
oracles, not observations of this preview. See [resource baselines](RESOURCE_API_BASELINES.md).

Known limitations: only this finite combination; no cross-mod dependencies,
early RegistryObject access, conditions/custom serializers/custom ingredients,
external tags, capabilities/network/lifecycle combination or arbitrary callbacks.
This preview itself performs no metadata migration or archive validation; those
are now implemented by the separate candidate API. Publication is still absent.
This is not independent of explicit developer mapping/API inputs; those are
LOCAL_USE_ONLY and are not bundled. No old Minecraft/Forge implementation is added.

The exact source archives were inspected again for this cut (not redistributed):

- Forge 36.2.42 mapped source JAR SHA-256
  `bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376`.
- Forge 47.4.23 mapped source JAR SHA-256
  `4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932`.

Primary orientation: [Forge 1.16 registries](https://docs.minecraftforge.net/en/1.16.x/concepts/registries/)
and [Forge 1.20.1 registries](https://docs.minecraftforge.net/en/1.20.1/concepts/registries/).
The exact local inventories/recipes, not those family pages alone, govern admission.

## Authored fixture and verification

`fixtures/composed-logic-recipes-1.16.5` is a new MIT original compiled/reobfuscated
against the pinned source APIs. Its four classes contain an entrypoint, item
subclass, helper interface and stateful helper implementation. Native super
short-circuit and an odd/even foil predicate make body replacement observable.
It adds one local-tag smelting recipe and authored en_us/pt_br/model JSON;
the model references a vanilla texture instead of copying one.

The fixture README defines the future count/foil/helper-call/cooking oracle.
**No source or converted fixture was executed in this cut.** ASM checks and
source/target linkage are static in-memory evidence; no serialized converted JAR,
Forge discovery, resource reload, gameplay or multiplayer result exists yet.

Run serially from the repository root (Java 17; Windows certificate provider only
on the matching host, as documented in the common contract):

```powershell
.\gradlew.bat -p fixtures/composed-logic-recipes-1.16.5 build --no-daemon --max-workers=1
.\gradlew.bat :legacy-converter:test --tests '*CompositionPlanTest' --tests '*CompositionPreviewIntegrationTest' -PverifyCompositionEnvironment=true '-PlogicMappingProfile=../build/logic-preserving-v1/mapping-profile.properties' --no-daemon --max-workers=1
.\gradlew.bat build :legacy-converter:installDist -PverifyCompositionEnvironment=true '-PlogicMappingProfile=../build/logic-preserving-v1/mapping-profile.properties' --no-daemon --max-workers=1
```

The profile path is an explicit local development prerequisite, not a redistributable
file. Without `verifyCompositionEnvironment=true`, the real-fixture tests are
skipped; pure graph tests still run. Evidence/failed attempts and exact hashes:
[COMPOSE-01 ledger](evidence/compose-01-2026-09-07.json).

## Next work in the same cut

COMPOSE-01 remains PARTIAL, not accepted by the static preview:

- The [private candidate continuation](COMPOSITION_CANDIDATE.md) implements distinct
  manifest/bindings, complete file ownership and actual ZIP/metadata/ASM/linkage
  revalidation. Neither preview nor candidate report is publication authority.
- Next, implement controlled developer-only QA export and comparison harnesses;
  do not counterfeit an existing public profile's identity.
- Add the necessary layout/negative coverage and isolated native comparison of
  the authored original and actual converted artifact **when games are authorized**.
- Keep public installer/UI promotion behind their independent LB-05/LB-04 gates.

`LINKAGE-GENERAL` and later dependent cuts are not unlocked by this partial work.
