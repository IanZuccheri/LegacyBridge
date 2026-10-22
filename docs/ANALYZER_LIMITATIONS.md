# Legacy Mod Analyzer limitations

The analyzer is intentionally conservative. A completed report means the
bounded static pass completed; it does not mean the mod can be transformed or
run on Forge 1.20.1.

## Evidence boundaries

- No input class, static initializer, Forge entrypoint, Mixin plugin, coremod
  script, service provider, or native library is loaded or executed.
- Bytecode classification observes names exactly as stored in the JAR.
  Production mods may use SRG/reobfuscated member names. The separate
  `mappings` command resolves symbols using local baselines; `analyze` itself
  preserves raw names. Exceptionally long retained analyzer report
  components are bounded (owner 1,024, member 512, descriptor 2,048
  characters) and set a truncation flag.
- Type references show that a symbol occurs, not that a handler is registered,
  a code path is reachable, or a physical side is safe.
- Reflection and dynamic loading are detected from explicit reflection,
  method-handle, Unsafe, `ClassLoader` loading/definition, and `ServiceLoader`
  calls. String-only class names are not data-flow resolved and do not add
  reflection risk.
- `ResourceLocation`, `PacketBuffer`, generic Entity/Player types, and
  `OnlyIn(Dist.CLIENT)` alone deliberately do not trigger registry, networking,
  custom-entity, or rendering classifications.
- External package inference cannot recover a Maven coordinate or Forge mod ID.
  Only `mods.toml` dependencies are treated as declared mod IDs. Inferred
  packages come from the bounded external-reference sample and may therefore
  be incomplete.

## Bounded detail

- Occurrence counts for ASM kinds and feature families are exact for every
  readable class.
- At most 256 distinct references are retained for one class and 256 class
  detail records are emitted for one archive. Truncation flags expose omitted
  details. Ordinary-reference sampling stops after 4,096 attempts per
  class/archive accumulator, so the retained set is deterministic within that
  prefix rather than globally exhaustive for a noisy class. Hierarchy samples
  bypass the attempt budget and have retention priority, but a class with more
  than 256 distinct hierarchy edges still cannot be listed fully.
- Minecraft (`net/minecraft/` and `com/mojang/`) and Forge top-level samples
  retain at most 256 distinct symbols each and stop sampling after 4,096
  matching reference observations. Exact occurrence counts continue.
- The combined package list retains at most 256 names; inferred third-party
  packages retain at most 128 names.
- Feature evidence retains at most 16 strings of 4,096 characters per feature
  and stops attempting evidence retention after 256 observations per feature;
  feature occurrence counts continue. Content path samples retain at most 16
  names per category.
- Unreadable/malformed class bodies retain only trustworthy fixed-header facts;
  their API references cannot be reported.
- Unknown custom JVM attributes are opaque. `invokedynamic` and
  `ConstantDynamic` are represented through descriptor/argument types and
  bootstrap field/method handles rather than a synthetic owner.

## Archive and configuration scope

- Nested JARs are detected but not recursively scanned. Their classes,
  metadata, resources, and dependencies are absent from the parent report.
- Only canonical classic ZIP and mandatory-sentinel ZIP64 layouts are in the
  initial archive subset. Self-extracting prefixes, gapped layouts, and
  optional ZIP64 end records without classic sentinels are safely rejected.
- Multi-release JAR entries are inventoried as physical class entries; the
  analyzer does not select one logical runtime variant.
- Mixin JSON is parsed for bounded declarative fields only. It does not validate
  targets, refmaps, injection points, plugins, or whether a Mixin would apply.
  Conventional `mixins.*.json`, `*.mixins.json`, and `*.mixin.json` filenames
  are conservative candidates and can produce false positives; a custom path
  is read only when declared by manifest `MixinConfigs`.
- A missing, malformed, or partial manifest-declared Mixin configuration makes
  the analysis partial. More than 256 distinct paths in `MixinConfigs` emits an
  error diagnostic, retains only bounded declarations, and also yields
  `analysisStatus=PARTIAL`.
- Access-transformer rules are counted and sampled lexically. Owners/members
  are not resolved or mapped.
- Canonical `META-INF/coremods.json` names/script paths and manifest
  `FMLCorePlugin` are recorded. Manifest `TweakClass` and
  `ForceLoadAsMod: true` are additionally classified as legacy bootstrap hooks.
  JavaScript, plugin classes, and tweakers are never parsed for transformation
  targets, loaded, or executed.
- Resource/data analysis is path-only. JSON/NBT syntax, pack format, Forge
  conditions, cross-resource references, recipes, loot semantics, worldgen,
  and dimension validity are not evaluated.
- Only allowlisted manifest main attributes are retained. Manifest sections and
  arbitrary attributes are ignored; `Class-Path` and `ContainedDeps` tokens are
  inventoried as manifest dependency evidence but never resolved or followed.
  A non-empty `Class-Path` is also reported as an unresolved external-classpath
  risk feature.

## Version and scoring scope

- `1.16.5` and `36.x` are conservative metadata-range detections, not exact
  build fingerprints. Wide ranges can include additional versions. Detection
  is suppressed when metadata validation fails; dependency-based inference
  additionally requires an explicitly mandatory dependency. `36.x` can be
  inferred from either the JavaFML loader range or a mandatory Forge
  dependency; the stricter source-profile candidate still requires a JavaFML
  loader range intersecting major 36 and an explicitly mandatory Minecraft
  dependency accepting `1.16.5`.
- Qualifiers, Maven unions, substitutions, and other syntax outside the
  conservative numeric grammar are retained with a warning but excluded from
  version inference.
- For multi-mod JARs, top-level `modId`/`modVersion` select the first valid,
  sorted mod ID; all declarations remain under `metadata.mods`.
- Missing/unparsed metadata, no valid mod ID, or a blank loader range produces
  `METADATA_UNCERTAINTY`; a parsed non-`javafml` loader produces
  `UNSUPPORTED_LOADER`. These are risk features as well as source-profile
  evidence.
- Risk weights estimate migration review effort. They are not a probability,
  compatibility verdict, or security score. Correlated feature families can
  accumulate. Access transformers, nested/signed/multi-release JARs, external
  manifest classpaths, and metadata uncertainty have a `MODERATE` floor;
  Mixins and unsupported loaders have a `COMPLEX` floor; coremods,
  transformation services, native code, and legacy bootstrap hooks have an
  `EXTREME` floor. Finding severity and migration complexity are separate.
- The general analyzer retains its inspection-only policy and historical
  `NOT_IMPLEMENTED` conversion state. It does not consult the enabled
  `simple-content-v1` adapter to authorize conversion. Only the separate strict
  converter can establish that the complete registration/resource grammar fits.

## Work deliberately not performed

The `analyze` command does not rewrite bytecode or metadata, emit converted mod
JARs, launch Forge, or execute legacy code. The separate
[simple-content converter](SIMPLE_CONTENT.md) now emits a target-native bootstrap
and runtime plan for its finite subset; its report and runtime evidence are
independent of analyzer findings. General legacy binary remapping remains absent.

## Mapping inspection limits

The separate [mapping report](MAPPINGS.md) uses its own ASM pass with 20,000
retained symbols, 4,096-character symbol components, four class evidence names,
and explicit incompleteness flags. It does not use the analyzer's reference
sample. Descriptors from member and `TYPE` observations are mapped only when
their class components resolve; generic signatures are not treated as JVM
descriptors.

Official name/signature matches plus local API declarations establish
structural resolution at `HIGH` confidence. They do not establish semantic
equivalence or authorize a rewrite. Inherited members, MCP snapshot aliases,
reflection strings, third-party API migration, and Mixin/AT/coremod remapping
are outside the current generator. Private target members remain manual;
access transformers are not assumed to make them accessible.

Default mapping files are pinned; API JAR hashes and explicit profile hashes
record local provenance rather than publisher authentication. Profiles are
trusted development inputs. The mapping parser can collapse duplicates inside
one file before the loader checks cross-file conflicts. Missing required
default inputs produce partial reports. Optional missing FML artifacts can
leave declarations uncovered even when required mapping sources are complete.
