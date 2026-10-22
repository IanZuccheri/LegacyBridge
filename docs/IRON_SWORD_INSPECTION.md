# Iron sword inspection and semantic blockers

The LB-10 equipment slice now has an original Forge 36.2.42 fixture and a
read-only engine operation. It recognizes one precise registration/construction
graph, checks its real API declarations, source bytecode/linkage and finite
resources, then reports **UNSUPPORTED**. It does not convert a sword or enlarge
the six public conversion profiles.

Matching constructor signatures and attribute values do not preserve all
inherited behavior. The [exact source audit](IRON_SWORD_API_AUDIT.md) identifies
material-based mining versus reloadable tags, and target ToolActions/sweep
dispatch changes. No semantic adapter for those changes has been implemented.

## Implemented scope

The authored `fixtures/iron-sword-1.16.5` original contains one final Java 8 entry,
three fields and three methods. A zero-capture static supplier constructs
`SwordItem(ItemTier.IRON, 3, -2.4F, new Item.Properties())`. DeferredRegister
registers `legacybridge_iron_sword:bridge_sword` on the mod event bus. There is no
subclass, creative tab, customized tier/properties or additional callback.

Identity and local field/method names are checked by their roles, not a filename
or hard-coded entrypoint allowlist. Additional state, classes, executable bodies,
unknown metadata, constructor arguments, handles or dependencies fail closed.
The target shape can be tested independently by the structural contract, but
the engine inspection accepts only the source namespace and source metadata.

The resource inspection admits one exact shapeless iron-ingot recipe, the
declared item result, a native handheld model/iron-sword texture reference,
bounded `en_us`/`pt_br` translations and pack format 6. Duplicate JSON keys,
malformed UTF-8, unsupported fields/paths and unknown files are rejected.
Model/texture references were reviewed in both pinned client archives; native
resource bytes are not redistributed. Nothing is removed or rewritten.

## Typed API and developer CLI

`LegacyEngineService.inspectIronSword(IronSwordInspectionRequest)` uses the
existing bounded worker, queue/admission controls, deadline and cancellation.
The request selects the original path, exact SHA-256 and explicit full local
mapping/API data. It has no output path. Included simple-content/cohort data
cannot impersonate a full equipment API inventory.

```text
legacy-converter inspect-iron-sword original.jar --input-sha256 <sha256> --mapping-profile <explicit-local-profile.properties>
```

The CLI calls that service. It prints canonical JSON and exits with code 3 for
the unsupported result; this is the expected diagnostic, not successful
conversion. No data is downloaded and no development cache is discovered by
the operation. The explicit local profile remains a developer dependency for
this inspection, not a new setup requirement for public simple-content use.

The immutable schema-1 `LEGACY_IRON_SWORD_SEMANTIC_INSPECTION` report separates:

- `constructorRecognized`: only the finite structural proof.
- Source ASM/linkage and exact API declaration evidence.
- Pinned historical source/body facts, explicitly not inferred from ApiIndex.
- Resource schema/reference evidence, not in-game loading.
- Structured missing-semantic-contract blockers.

`convertible`, `candidateGenerated`, `publicationAllowed`, `installationAllowed`,
`installed` and `classesExecuted` remain false. `runtimeValidation` is NOT_RUN,
and the transformation count is zero. There is no fabricated TransformationLog
or adapter for a transformation that never occurred. Existing CompatibilityReport
and TransformationLog schemas and runtime identity are unchanged.

## Integrity and limits

Only canonical regular paths without observed symlink/junction aliases are
accepted. The operation revalidates the selected hash after inspection, including
rejected diagnostics. The shared safe archive reader takes and cleans private
snapshots; it does not execute/extract classes into a loader. There is no output
archive, install transaction, world operation or Apply authority.

Capture is limited to 16 files and 2 MiB, with 128 KiB per class and 64 KiB per
resource, in addition to shared ZIP/decompression/name limits. Recognition
narrows this further to one class/three bounded methods and the exact resource
set. These are cooperative engine bounds, not a hard JVM heap sandbox. Unknown
external declarations are not silently accepted by a remapper.

Tests inspect pinned native bytecode as data; the mining oracle uses synthetic
material/tag predicates to expose a counterexample. It does not execute a native
world, tag reload or original mod. Constructor/UUID/arithmetic checks do not prove
damage, breakage, anvil costs, consumption, combat, multiplayer or modpack
integration. Test cases are not counts or percentages of supported mods.

## Continuation boundary

This recognition/diagnostic milestone can be completed while the sword remains
unsupported. Do not reopen the same audit indefinitely or approve construction
alone. Inherited sword compatibility needs its own implemented semantic recipe;
changing global platform tags/classes is not authorized.

The next independent equipment slice is `OWNED_ATTRIBUTE_MODIFIERS`: a plain
owned Item subclass with a finite immutable main-hand modifier contract, not a
SwordItem adapter. Its [actual API/behavior audit](ITEM_ATTRIBUTE_API_AUDIT.md) defines the proposed
fixture and exact mapping/access obligations before implementation. Equipment and LB-10 remain
PARTIAL; neither this diagnostic nor the next source audit advances native gates.

Artifact-bound results, test counts, limitations and hashes belong in
[the validation ledger](evidence/iron-sword-inspection-2026-10-01.json).
