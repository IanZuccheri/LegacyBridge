# Offline ASM transformation pipeline

The `convert` command now runs an explicit ASM pipeline before writing any
converted class. It still accepts only the fixture-backed
[simple-content-v1 profile](SIMPLE_CONTENT.md), not arbitrary Forge 1.16.5 mods.
The target remains Minecraft 1.20.1, Forge 47.4.23 and Java 17, with the
[implemented compatibility layer](COMPAT_LAYER.md).

Current fixture results and exact output hashes are recorded in
[ASM-pipeline validation](ASM_PIPELINE_VALIDATION.md).

```text
Original legacy JAR (bounded, read-only capture)
  -> ClassReader
  -> LegacyReferenceScanner
  -> MappingResolver
  -> ClassRemapper
  -> LegacyApiTransformer
  -> DescriptorTransformer
  -> CompatibilityAdapterTransformer
  -> ClassWriter
  -> CheckClassAdapter + offline reference-type validation
  -> Converted JAR (commit only after every gate succeeds)
```

The entrypoint is `TransformationPipeline`. The CLI remains:

```text
legacy-converter convert mod.jar --mapping-bundle local-data/bundle-v1 --output converted.jar --report conversion.json
```

The output directory must exist. Existing output JARs are not overwritten, and
the output cannot alias the source. `analyze` and `mappings` remain read-only.

## What each stage actually does

| Stage | Implementation and boundary |
|---|---|
| `ClassReader` | Reads class bytes with no `SKIP_DEBUG` or `SKIP_FRAMES`. No mod class is defined or initialized. |
| `LegacyReferenceScanner` | Produces immutable per-class data and an occurrence-level inventory before rewriting. Fresh ASM tree copies are provided for transformation. |
| `MappingResolver` | Selects exact `RESOLVED`, `VERIFIED` rules with an existing adapter binding. A mapping candidate is not authorization to change behavior. |
| `ClassRemapper` | `LoggedClassRemapper` delegates structural rewriting to ASM commons and records class, member, descriptor and generic-signature changes. |
| `LegacyApiTransformer` | Lowers only a whole registration graph already accepted by `StaticContentExtractor` into the native bootstrap plus declarative plan. |
| `DescriptorTransformer` | Applies verified object-type component changes in descriptors/signatures; it does not invent an argument-conversion recipe. |
| `CompatibilityAdapterTransformer` | Inserts the exact cataloged bootstrap call into the proven constructor shape, checking its descriptor and instruction preconditions. |
| `ClassWriter` | `OfflineClassWriter` recomputes frames and maxima using class-file metadata, never mod class loading. |
| Final validation | Checks the actual candidate bytes again immediately before archive serialization and commit. |

The source is also checked by `BytecodeValidator` before semantic extraction.
The finite extractor is the semantic proof between scanning and rewriting: it
must account for every supported executable method and registration holder.
Unexpected behavior is rejected, not carried into the new game unchecked.

The current CLI creates one `VERIFIED` structural rule for the mod's own
entrypoint, relocating it to
`dev/legacybridge/generated/<modId>/ConvertedMod`. That rule is bound to
`registry.simple-content.v1` because the complete graph has been proven to fit
that adapter recipe. It does **not** upgrade inferred `HIGH` Minecraft/Forge
mappings into verified direct-call replacements. Helper classes, fields and
methods are consumed only by this whole-graph recipe, with journal entries
explaining their replacement by the declarative plan.

## Ownership and reference scanning

Only source classes belonging to the converted mod enter the transformation
stages. A source definition in any of the following namespaces aborts the
conversion, even if its ZIP entry attempts to disguise its declared name:

```text
net/minecraft/          net/minecraftforge/    com/mojang/
cpw/mods/               java/                  javax/
jdk/                   sun/                   dev/legacybridge/runtime/
```

The exact entry path must equal `<declared internal name>.class`. Mismatched
paths, duplicate declarations, invalid class headers and multi-release aliases
are not remapping opportunities. Minecraft, Forge and platform classes are not
copied or modified. References *to* external APIs are distinct from class
definitions owned by those APIs.

The scanner records external superclass/interface, class, method, field,
descriptor, signature, annotation and frame references. It also traverses
bootstrap handles, invokedynamic arguments, recursively nested
`ConstantDynamic`, annotation enum fields, record components, module service
types, exception handlers and local-variable metadata. Handles are reported as
method or field references according to their JVM handle tag. A string that
happens to resemble a class name is not treated as a proven type reference.

Each occurrence includes the source class, owner, name, complete descriptor,
enclosing method, source location, source line and original ASM `InsnList`
index. The index includes labels, line and frame nodes; `-1` denotes metadata
outside an instruction. `SourceFile` is untrusted display metadata, never a path
to open. Mod-local symbol occurrences are excluded from the external inventory.

The scanner bounds input at 512 classes, 16 MiB per class, 128 MiB total class
bytes, 100,000 reference occurrences and nesting depth 128. Exceeding a bound
aborts; references are never silently sampled or truncated. The finite CLI
profile can impose stricter limits. Unknown custom attribute payloads remain
opaque and are not interpreted as hidden API references.

## Rewrite authorization

Every applied change carries an existing adapter reference. Neither an adapter
ID by itself nor matching names prove semantic equivalence. Structural helpers
require an exact verified rule and reject unresolved confidence/status,
unimplemented bindings, class collisions and chained class rules.

Object components in descriptors may be renamed under those rules. Primitive
changes, argument count/order changes, array-rank changes, independent member
owner relocation and incompatible return shapes require a concrete invocation
recipe; none is inferred here. Constructors preserve `<init>`. Invokedynamic
SAM renames/interface relocations require a bootstrap recipe; ambiguous
annotation-attribute and record-component renames without a complete constructor
and ObjectMethods recipe are refused. A verified external class rename alone
does not authorize method/field/constructor relocation: an exact verified
member rule is also required, including in the later descriptor stage.

An unlisted reference is unchanged by the low-level structural remapper, not
declared compatible. The CLI's preceding whole-profile proof prevents unknown
legacy behavior from surviving in an accepted output. Unsupported direct APIs
remain `UNSUPPORTED`; the pipeline introduces no generic entity, packet,
capability, GUI, renderer, mixin, coremod or world-generation migration.

## TransformationLog

The schema-2 `LEGACY_CONVERSION` JSON report embeds a separate schema-1
`transformationLog` object containing `entryCount` and ordered `entries`.
Available kinds are:

- `CLASS_REMAP`
- `METHOD_REMAP`
- `FIELD_REMAP`
- `DESCRIPTOR_REMAP`
- `ADAPTER_CALL`
- `RESOURCE_REWRITE`

Each entry contains `sequence`, `kind`, `location`, `before`, `after`, `rule`,
`confidence`, `reason` and the exact `adapter` reference: ID, revision,
implementation class, method and descriptor. No-op changes are omitted; the
100,000-entry limit aborts rather than dropping changes. Current applied entries
must have `VERIFIED` confidence. A particular fixture need not use every kind.

Resource additions and rewrites use `sha256:<digest> bytes=<length>` for their
before/after values; a missing side is `<absent>`. The allowed changes are the
cataloged metadata/manifest/pack operations, generated content and adapter
bindings, and the target pickaxe-mining tag. An unbound resource rewrite aborts.
Byte-identical resources have no rewrite entry and are instead listed in
`preservedResources` with their hashes.

Successful reports set `transformationsCommitted: true`. A semantic or bytecode
rejection reports `ABORTED`, `UNSUPPORTED`, `output: null`,
`bytecodeModified: false` and `transformationsCommitted: false`. The journal can
retain changes attempted **in memory** before the failing gate; these entries
must not be read as modifications committed to a JAR. Archive/input/output I/O
errors are separately surfaced by the CLI. The source hash is rechecked before
commit, and the source JAR is never rewritten.

## Debug information

Scanning and structural remapping retain debug metadata when ASM can carry it.
The current semantic lowering retains the entrypoint's `SourceFile` and
`SourceDebugExtension` (SMAP), the first existing no-argument constructor source
line, and an existing constructor slot-zero local with its new class descriptor.
It does not fabricate source data for a stripped input.

Legacy methods and helper classes are replaced by the finite bootstrap recipe.
Their original line ranges, locals and frames no longer describe equivalent
instructions, so these are not copied into the replacement body. The journal's
whole-method lowering reason states this loss explicitly. Preserved SMAP data
is provenance, not a promise of instruction-for-instruction source debugging.

## Bytecode safety and its limits

`BytecodeValidator` runs ASM `CheckClassAdapter` with dataflow checking against
a non-recomputing `ClassWriter(0)` delegate and expanded original frames. This
matters: checking only a tree while recomputing dataflow can mask a malformed
original StackMap table. Additional checks cover duplicate declarations,
branch/handler frame presence, stack/local frame compatibility and method
dataflow.

Source validation uses `BasicVerifier`, which does not require an obsolete
Minecraft/Forge runtime. Generated candidate validation additionally uses an
offline `SimpleVerifier` subclass for reference assignability. Its class-loading
hook is forbidden. Hierarchies come from explicitly supplied class bytes,
platform class resources, or available bridge runtime class resources; reading
a `.class` resource does not define or initialize that class.

Changed bodies are written with `COMPUTE_FRAMES | COMPUTE_MAXS`. The writer's
common-superclass lookup uses the same class-file-only hierarchy. If a required
type is missing, it aborts instead of guessing `java/lang/Object`. No
`Class.forName`, reflective mod invocation or source class initialization is
used to validate conversion. Any detected invalid bytecode aborts before output
commit.

These checks are **not JVM linkage or complete JVM verification**. In
particular, they do not establish target member existence/accessibility,
constructor initialization correctness in arbitrary bytecode, successful mod
discovery or in-game behavior. Reports explicitly set `modClassesLoaded: false`
and `jvmLinkageVerified: false`. Real Forge validation remains a separate gate;
historical runtime evidence must not be relabeled as evidence for newly emitted
artifacts.

## ASM provenance

The build pins **ASM 9.9.1** for core, tree, commons, analysis and util. The local
installed distribution contains those exact artifact versions, and the
`CheckClassAdapter(ClassVisitor, boolean)` API was checked in the local JAR.
The upstream [Javadoc](https://asm.ow2.io/javadoc/overview-summary.html) identifies
itself as ASM 9.10 as inspected on 2026-09-05; it is reference material, not the
build's resolved version. The [official FAQ](https://asm.ow2.io/faq.html)
documents `ClassRemapper`, frame/maxima generation and verification diagnostics.
Local artifact inspection and tests determine the behavior relied on here;
examples that load generated classes are deliberately not part of this pipeline.
