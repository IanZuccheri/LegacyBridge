# COMPOSE-01: private serialized candidate

The separate [finite owned-constructor candidate](OWNED_CONSTRUCTOR_DELEGATION.md)
reuses this assembly/readback pipeline with its own identity and proof. It does
not broaden this candidate, any existing profile or installation authority.

The [static preview](COMPOSITION_PREVIEW.md) is now followed by an independent
**private candidate inspection API**. This is still LB-08 / COMPOSE-01 PARTIAL.
It is not public profile promotion, a runnable-artifact handoff or runtime acceptance.
The exact existing Forge 36.2.42 → 47.4.23 / Minecraft 1.16.5 → 1.20.1 baselines
and Java 17 engine/runtime remain unchanged.

A separate [developer QA export](COMPOSITION_QA_EXPORT.md) can now retain these
validated bytes through a package-private, scoped borrow. It is not part of this
public inspection API, the installed CLI or the public JAR. The inspection result
and its no-publication contract below are unchanged.

## Typed API and lifetime

`LegacyEngineService.planLogicWithRecipes(ConversionRequest)` returns
`EngineJob<CompositionCandidate>` on the existing bounded worker. The corresponding
direct developer entry point is `LogicPreservingConverter.planLogicWithRecipes`.
Both reuse the preview's actual mapping, finite body-shape, resource and source/target
offline-linkage implementation; there is no second set of conversion rules.

The request must select `ConversionProfile.LOGIC_PRESERVING_V1` as its base and
explicit complete mapping/API data. Built-in simple-content data are refused.
`candidate.validated()` means that a real private ZIP was serialized and reopened.
It does **not** mean that Forge discovered the mod or that gameplay worked.
`candidateSha256()` is optional proof data, never an installation approval.
`publicationAllowed()` is always false. Neither candidate bytes nor a scratch path
are exposed. The result is deliberately not `ConversionPlan` or `ConversionResult`.

The requested output path is only checked as a prospective destination using
existing path/no-overwrite guards. It is not created. The old preview API still
does not serialize a JAR. The new candidate inspection performs:

1. The existing complete static body/resource admission, preserving four fixture
   classes and ten methods without replacing them with a bootstrap.
2. Source dependency verification on both physical sides and dependency-preserving
   metadata migration via the implemented `LifecycleMetadata` helper. Reusing that
   helper does not enable lifecycle callbacks or the lifecycle profile.
3. A complete immutable `CompositionArtifactPlan` for every source/output **file**
   entry, including metadata and unchanged inert legal notices. Directory records
   are not content files. No original class or resource file is silently removed.
4. Exact implemented metadata emission and four bound `RESOURCE_REWRITE` entries.
   Body remaps retain the existing exact reviewed mappings and transformation log.
5. Deterministic ZIP serialization through the existing bounded writer.
6. Writing an operation-owned temporary JAR, reopening it through `SafeJarReader`
   (including ZIP central-directory/CRC checks), and deleting the temporary in
   `finally`. The reader's own private snapshot is separately cleaned up.
7. Exact source/output hash-and-entry-set verification; target metadata/dependency,
   resource, ASM dataflow, ownership and offline linkage validation on reopened bytes.
8. A schema-1 `LEGACY_COMPOSITION_CANDIDATE` report; candidate arrays are discarded.

No new threads, plugins, downloads, external processes or original class execution
are introduced. The service still checks the selected input hash. Ordinary `plan`
and `convert`, the CLI, public UI and installer retain the six existing profiles.

## Identity and implemented metadata operations

The candidate manifest identifies `composed-logic-recipes-v1`, revision 1, and
`STATIC-CANDIDATE-ONLY`. That is distinct inert provenance, **not an enabled
`ConversionProfile` enum value or runtime capability**. It must not impersonate
`logic-preserving-v1` or `simple-content-v1` to bypass future installer checks.

| File | Exact operation |
| --- | --- |
| `META-INF/mods.toml` | Existing `registry.mod-metadata.v1` plus the existing dependency-preserving writer. Retain declarations, change only reviewed platform ranges and add the required BOTH/AFTER runtime edge. |
| `pack.mcmeta` | Existing `client.resource-pack.v1`; validated source format 6 → target format 15, description retained. |
| `META-INF/MANIFEST.MF` | New OFFLINE `composition.mod-manifest.v1`, implemented by `ComposedArtifactAdapter.targetManifest`. |
| `META-INF/legacybridge/<modId>/adapters.json` | New OFFLINE `composition.adapter-bindings.v1`, implemented by `ComposedArtifactAdapter.bindingTree`. |

Both new operation IDs have revision 1, concrete descriptors and full catalog
documentation. They are additive to `legacy-compat-api` and packaged into the
runtime/product in the existing way. Existing IDs/revisions and runtime identity
`legacybridge_runtime` / `0.1.1-SNAPSHOT` remain unchanged. The runtime capability
catalog still enables only simple-content; adding an OFFLINE metadata emitter
is not enabling another gameplay API. A future installer must verify exact
composition bindings, not accept a runtime version range as sufficient proof.

Legacy API: validated inert Forge 36 metadata, native registries and the already
reviewed owned-body/recipe contracts. Target API: Forge 47 metadata and the same
reviewed target-native contracts. Transformation: serialize the new provenance
and exact implemented bindings, preserve dependency components and resource bytes.
Behavior differences: old build-provenance headers are not retained; native
cooking book grouping may differ (target MISC default). Known limitations:
no new callbacks, serializers, dependency cohorts, networking, capabilities,
dynamic loading, runtime shims or migration of existing worlds.

The original and target mapped source archives were read and hash-checked again;
their SHA-256 values remain those in [logic baselines](LOGIC_API_BASELINES.md) and
[resource baselines](RESOURCE_API_BASELINES.md). The official
[Forge 1.16 metadata overview](https://docs.minecraftforge.net/en/1.16.x/gettingstarted/structuring/)
and [Forge 1.20.1 mod files](https://docs.minecraftforge.net/en/1.20.1/gettingstarted/modfiles/)
corroborate the metadata boundary; exact local versions and implemented metadata
validation govern this cut. No old Minecraft/Forge code or private data is embedded.

## Complete output ownership and failure handling

The artifact plan is schema 1 and labelled `PRIVATE_ARTIFACT_PLAN_NOT_AUTHORITY`.
Every entry has source SHA-256 (or an explicit generated entry), target SHA-256,
operation identity, disposition and any transformation adapter reference.
Metadata operations declare prerequisites, phase, worker context and implemented
binding; unchanged legal notices have their own inert operation declaration.
No consumer accepts caller-supplied plans as semantic or installation authority.

The source class/resource operation graph must be the actually implemented two-node
combination, with every claim matching the source. Unowned entries, file loss,
stale claims, changed resources/notices, missing metadata and new classes block the
whole candidate. Reopening additionally rejects any changed bytes or entry set.
Generated metadata is checked against the exact generator output; only that verified
binding document is excluded from the legacy resource language on the target pass.

The plan caps file claims at 4,096; existing class/resource/ZIP limits and cumulative
service work budgets still apply. Reopening consumes additional bounded capture and
inflation work. These budgets do not impose a hard limit on the shared JVM heap.
Cancellation before/during serialization, private re-scan, final bytecode checks or
PREPARED returns no accepted candidate. Scratch cleanup failure propagates an I/O
failure instead of a success receipt. A hard process termination or OS-locked scratch
file may leave an untrusted file in the JVM temporary directory; no startup scratch
recovery or crash-proof cleanup is claimed here. Such files are never input,
staging, installed mods or installation transaction authority.

## Evidence and next step

The original authored fixture was not changed or executed. Its future behavioral
oracle remains in `fixtures/composed-logic-recipes-1.16.5/README.md`.
The [candidate ledger](evidence/compose-candidate-2026-09-07.json) records exact
source/product/candidate hashes, executed tests, skips and regressions.

Static evidence separates actual serialized/reopened bytes from Forge discovery,
client/server initialization, resource reload, behavior and multiplayer: **all
native dimensions remain NOT_RUN**. The candidate is not retained as a published JAR.
No number of tests is a number of supported real mods.

The controlled developer-only QA export is now implemented separately. Continue
COMPOSE-01 headlessly with comparison harnesses for the actual original and composed
artifact, with negative layout/contract coverage. Then run the isolated native comparison
only after games are explicitly authorized. Keep LB-04/LB-05 promotion and later
`LINKAGE-GENERAL` acceptance gated. Do not turn the report/hash into an Apply button.
