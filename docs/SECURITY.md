# Security model

## Current exposure

The analyzer CLI accepts untrusted JARs for static analysis and JSON reporting;
that path does not transform, load, or execute them. The separate implemented
`convert` path transforms only the proven `simple-content-v1` subset, never
executes source classes, and validates candidate bytecode before JAR emission.
See [ASM pipeline](ASM_PIPELINE.md) for that boundary. The implemented protections
and remaining hardening items are distinguished below; static analysis is not a
general-purpose sandbox for malformed formats outside the supported policy.

The [in-game product architecture](PRODUCT_ARCHITECTURE.md) adds explicit future
installation/restart trust boundaries. P1 supplies read-only workspace and pure
workflow contracts. P2 adds a bounded asynchronous engine worker shared by the
CLI and future UI; no Minecraft screen, installer or restart provider exists.
Path inspection is not a filesystem-race defense or permission to write.

Treat every mod JAR, metadata string, classfile, nested archive, and output path
as untrusted input.

## Non-execution guarantee

Static analysis must never:

- add the input to a JVM classpath or module path;
- create a class loader for it;
- call `Class.forName`, reflection, method handles, or input static initializers;
- invoke an input `main` method, Forge entrypoint, transformation service,
  language provider, coremod, LaunchWrapper tweaker, Mixin plugin, or service
  provider;
- start Minecraft, Forge, ModLauncher, a client, or a server;
- follow manifest `Class-Path` entries;
- contact update URLs, dependency URLs, or other network endpoints;
- load native libraries;
- deserialize Java objects from the archive.

Input service descriptors and executable markers are data to report, never
extension points for LegacyBridge.

## Implemented archive handling

`SafeJarReader` uses `ZipFile` and never extracts entries. It currently:

- requires a readable regular `.jar` input that is not a symbolic link;
- rejects control and Unicode formatting characters in the input basename;
- copies the bounded input through a no-follow file handle to a private
  temporary snapshot, hashes that snapshot, and runs both preflight and
  `ZipFile` only against the stable copy; the snapshot is deleted afterward;
- performs a bounded EOCD/ZIP64 preflight before constructing `ZipFile`,
  rejecting multi-disk archives, excessive central directories or entry
  counts, and directory bounds outside the input;
- computes the input SHA-256 without loading the whole file into memory;
- rejects absolute/drive-qualified names, control/format characters,
  backslashes, repeated separators, and `.` or `..` path segments;
- rejects exact duplicates and case/Unicode-NFC collisions;
- streams every payload while enforcing per-entry, aggregate-data, and
  aggregate-entry-name limits;
- checks actual byte counts, declared sizes, CRC values, and compression ratio;
- captures only bounded classfile, manifest, `mods.toml`, Mixin, AT, and
  coremod-configuration bytes for parsers;
- caps conventionally named Mixin configuration candidates before retaining
  their parsed summaries;
- inventories signatures, ATs, coremods, legacy bootstrap manifest hooks,
  external classpaths, services, Mixins, nested/native files, modules, and
  multi-release entries using bounded per-kind samples and an exact total
  count;
- never recurses into a nested JAR.

Malformed archives and limit violations are rejected before a report is
claimed complete. Signed and multi-release archives can be inventoried, but the
policy emits blocking/error findings as appropriate.

The initial archive subset requires a canonical, non-prefixed layout. It
rejects self-extracting prefixes, gaps before ZIP end records, and optional
ZIP64 records whose classic EOCD fields do not use ZIP64 sentinels. Canonical
mandatory ZIP64 is accepted. These are deliberate safe-subset restrictions,
not claims that every rejected layout is malicious. If the original path
changes while the stable snapshot is analyzed, publication fails with
`INPUT_CHANGED`.

Archive handling must continue rejecting or conservatively flagging:

- absolute names, Windows drive prefixes, control/format characters,
  backslashes, and `.` or `..` path segments;
- exact duplicate names and names that collide after normalization;
- case-insensitive and Unicode-normalization collisions;
- encrypted or malformed entries that the ZIP implementation cannot read;
- impossible size metadata, truncated streams, and CRC mismatches;
- excessive entry count, compressed size, uncompressed size, or compression
  expansion;
- filesystem-special archive entries when their external attributes can be
  inspected reliably;
- unsupported multi-release layouts;
- unexpected nested JARs.

Payload limits are enforced against bytes actually read rather than trusted
solely from the ZIP central directory. The directory itself is bounded in the
preflight described above. The implemented policy version is `2026-09-v2`:

| Resource | Implemented maximum |
|---|---:|
| Input JAR | 256 MiB |
| Entries | 50,000 |
| ZIP central directory | 64 MiB |
| One uncompressed entry | 128 MiB |
| Total uncompressed data | 1 GiB |
| One classfile | 16 MiB |
| One parsed metadata file | 1 MiB |
| Compression ratio | 200:1 after 1 MiB |
| Entry-name length | 4,096 characters |
| All entry names combined | 4,000,000 characters |
| Conventionally named Mixin configs | 256 |
| Special-entry evidence | 4 names per kind; 40 names total |
| Namespace names retained internally | 128 per category and direction |
| Namespace names emitted | 12 per category and direction |
| Reference samples | 256 distinct references per class/archive view |
| Ordinary reference sampling attempts | 4,096 per class/archive accumulator |
| Minecraft/Forge sampling attempts | 4,096 per platform family |
| Retained reference components | owner 1,024; member 512; descriptor 2,048 characters |
| Per-class detail records | 256 classes |
| Feature evidence | 16 strings per feature family |
| Feature-evidence sampling attempts | 256 per feature family |
| One retained feature-evidence string | 4,096 characters |
| Content path samples | 16 names per category |
| Access-transformer rule samples | 64 rules |
| Declarative config samples | 256 declarations |
| Retained manifest/config value | 4,096 characters |
| Config JSON structure | depth 64; 20,000 nodes; strings 16,384 characters |
| `mods.toml` structure | depth 64; 100,000 structural tokens |
| Nested archive recursion | Disabled |

These values are security policy, not compatibility facts. Corpus testing may
justify changing them in a future version. There is currently no CLI option to
raise or disable them.

The conventionally named Mixin-config count is a hard archive rejection. A
manifest `MixinConfigs` value declaring more than 256 distinct paths is handled
differently: the bounded manifest is reported with an error diagnostic and the
result is `analysisStatus=PARTIAL`; excess declarations are never followed.

## Bytecode parser boundary

The implementation checks the classfile magic and major/minor version before
ASM traversal and bounds each classfile before allocation. ASM visits existing
descriptors, signatures, annotations, instructions, frames, bootstrap handles,
constant dynamics, nests, records, modules, services, permitted subclasses, and
native methods. Analysis and mapping inspection do not create a `ClassWriter`,
compute frames, use reflection, or delegate hierarchy resolution to a class
loader. The separate conversion pipeline uses `OfflineClassWriter` with
metadata-only hierarchy resolution and validates candidate bytes before commit;
it does not define or initialize the original mod's classes.

Whole-archive bytecode aggregation is incremental. Each bounded per-class scan
is merged only after it succeeds into exact counters, a class-version
histogram, feature/platform counters, and fixed-category samplers. The report
retains at most 256 deterministic class-detail records and 256 reference
samples per class/archive view. Ordinary reference and platform sample attempts
stop at 4,096 observations; feature-evidence attempts stop at 256 per family.
This bounds comparison and string-construction work without changing exact
occurrence counters, and the corresponding truncation flag is set. The
remaining temporary scan state becomes eligible for collection. Names of
native methods, Java modules, and module packages are intentionally not
collected because they are not part of report schema v2; their modeled counts
remain exact.

Malformed classes become unreadable-class facts rather than executable input.
Custom opaque JVM attributes are not interpreted; a report cannot use them as
evidence of support.

Java 8 major 52 is valid input for a Java 17 analyzer. Merely changing the
classfile major to 61 is neither necessary nor a migration strategy.

## Signed JARs

Detect signature material including `META-INF/*.SF`, `*.RSA`, `*.DSA`, and
`*.EC`. Static analysis may inventory a signed JAR, but any future byte change
invalidates its original signature. Conversion must therefore refuse signed
inputs until an explicit policy defines authorization, signature removal,
reporting, license preservation, and output identity. LegacyBridge must never
present a modified JAR as still signed by the original author.

## Filesystem and report-output safety

The source is opened read-only and is never modified in place. The CLI rejects
an output path whose normalized absolute spelling equals the input path. The
writer additionally resolves the destination parent, rejects destination
symlinks and filesystem aliases of the input (including hard links), and
revalidates immediately before publication. A report file is created through a
private temporary file in the resolved, existing target directory. Overwrite
publication uses an atomic move where supported and a non-atomic fallback
otherwise; no-overwrite publication uses a plain same-directory move so the
filesystem preserves the non-replacement contract. Existing reports require
`--overwrite`; failed writes remove the owned temporary file.

The report rules apply to `--report <path|->`; `-` writes the report to stdout
and must not mix log records into that stream. The implemented converter also
emits a validated output JAR to a new path only, after rechecking the source
fingerprint. `--overwrite` never authorizes overwriting a JAR. Emitting a file
is not installation: the planned player installer requires an independently
authorized, hash-bound transaction and recovery policy; P1 implements neither.

An attacker concurrently changing directory mappings or links can still create
filesystem time-of-check/time-of-use races on providers that do not offer a
secure directory stream. Users should write reports only into directories they
control. Archive entry names are never used as output paths.

## Determinism and denial of service

Deterministic ordering does not require retaining an unbounded archive in
memory. Special-entry names are reduced online to the lexicographically first
four names per risk kind. Bytecode types are classified immediately into eight
fixed namespace categories and reduced online to the lexicographically first
128 names per category and direction; only 12 are serialized. Reference and
feature samples are deterministic within their fixed attempt budgets rather
than guaranteed to be the globally smallest values in an arbitrarily noisy
archive. Exact occurrence or entry totals are maintained separately, and
explicit truncation booleans indicate omitted details.

Analysis is bounded by archive policy and, through `LegacyEngineService`,
cooperative operation controls. The defaults add one worker, eight waiting
requests, 128 retained inventory candidates, a two-minute deadline (including
queue time), 64 MiB compressed source, 128 MiB accumulated inflated work and
64 MiB accumulated captured payload work. Inventory enumeration also stops after
1,024 entries, including non-JAR entries, and exposes truncation. Repeated reads
within a job count as work. Full reports are not retained in inventory rows.

These are payload/record bounds, **not a hard shared-JVM heap ceiling**. Mapping
indexes have separate fixed bounds; parsers, object graphs and serialization
allocate additional memory. Opaque JVM/ASM/image/OS calls are cooperatively
interruptible only at the next checkpoint. The application must own one service
and bound its own retained results, rather than create one executor per button.

Accepted cancellation before the publication barrier cannot publish a candidate.
After that barrier `cancel()` returns false, and the caller must await the actual
result. Snapshots and owned temporary output are cleaned on success, cancellation
and ordinary failure; abrupt process termination and failed OS cleanup are not
claimed recoverable by this engine. Structured diagnostics are bounded and
control-character sanitized. See [execution controls](ENGINE_EXECUTION_CONTROLS.md).

## Dependencies and network

The analyzer performs static local-file analysis and issues no network requests;
this does not establish network isolation of its Minecraft/Forge host.
Build-time dependencies must come from the
configured Maven Central and MinecraftForge repositories, use pinned versions,
and be covered by dependency verification/checksum policy before a release.
Resolved Minecraft and Forge artifacts are build inputs, not files to copy into
the converter output.

Operational mapping/conversion data is explicitly selected using a local bundle
or profile. Missing configuration produces `MAPPING_DATA_REQUIRED`; only the
separate opt-in preparation command may inspect a development cache. No automatic
download or original game implementation is embedded. Bundle hashes detect
corruption, not publisher authenticity: import assumes locally generated/trusted
data and rejects malformed/oversized structures. Redistribution clearance is a
separate [documented release gate](PORTABLE_MAPPING_DATA.md).

## Fixtures and disclosure

Only synthetic, license-compatible fixtures may be committed. Private local
mods remain ignored and must not be included in bug reports without the owner's
permission. Security reports should include the LegacyBridge version, input
SHA-256, exit code, and a minimal redistributable reproducer where possible;
they should not include a proprietary mod JAR by default.

## Remaining security validation

Current tests cover basic valid scanning, traversal, malformed ZIP and extension
rejection, bytecode non-execution design, and canonical serialization. Before
calling the analyzer hardened against actively adversarial archives, add focused
tests for duplicate central-directory entries, Unicode/case collisions,
compression-ratio and aggregate limits, CRC/size mismatches, encrypted entries,
filesystem-special entries, output aliases/symlinks, cancellation cleanup, log
sanitization, input replacement between hashing and ZIP traversal, and
deterministic rejected-input behavior.
