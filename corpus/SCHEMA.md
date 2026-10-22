# Corpus schema 1

The normative executable checks are `CorpusVerifier` and `CorpusJson` in
`legacy-corpus`. JSON is inert UTF-8, at most 2 MiB, 20,000 nodes, depth 64 and
16,384 characters per string. Duplicate keys, unexpected core fields, unsafe
paths and an unknown schema version fail closed. At most 256 records exist in a
catalog and at most 256 evidence entries per record. Local evidence hashing is
streamed with a 256 MiB per-file limit.

`catalog.json`: `schemaVersion`, `corpusVersion`, `records` (relative JSON paths).
Record IDs and input SHA-256 values must be unique across the complete catalog.
Repeated filenames or independently named identical JARs do not create new mods.

`snapshots/<corpusVersion>.json` has `schemaVersion:1`, `corpusVersion` and
`records`, an object mapping the exact catalog record paths to their SHA-256.
The public verification command and observation runner require a matching lock.
Editing/adding/removing a record without updating the reviewed snapshot fails.
Editing both data and lock is a new review and must increment corpusVersion;
hashes alone cannot enforce honesty about version history or act as signatures.

Record fields:

| Field | Contract |
| --- | --- |
| `schemaVersion`, `id`, `kind`, `projectKey`, `version` | Schema 1; stable record ID; PROJECT_FIXTURE or REAL_MOD; project grouping key; exact publisher version or explicitly identified fixture revision |
| `artifact` | `fileName`, positive `sizeBytes`, lowercase 64-character `sha256`; identity is the bytes, not the filename |
| `authorization` | `basis`, `license`, `redistributeBinary:false`, typed evidence IDs; real mods require a reviewed SPDX identifier and an HTTPS authorization source |
| `source` | `loader`, `minecraftVersions`, nullable `forgeBuild`, `forgeVersionEvidence`; UNKNOWN/null when not established; never infer an exact Forge build from a version range |
| `dependencies` | Bounded human-readable dependency declarations; original structured declarations and inferred-dependency caveats remain under `structuredMetadata` |
| `detectedFeatures` | Observed scanner/content/review labels, not an enabled feature list |
| `blockers` | Actual converter diagnostics; first refusal need not enumerate all semantic dependencies |
| `output` | Status CONVERTED, BLOCKED, FAILED or NOT_RUN; only CONVERTED has a non-null SHA-256 and must have the full static proof chain |
| `stages` | Exactly the eight stages below, each with `status` and an `evidence` ID array |
| `evidence` | Exact typed references with hash, source/target bindings and one project-relative path or pinned HTTPS URL |
| `structuredMetadata`, `analysisSummary` | Optional bounded JSON objects for provenance, declarations, scope, truncation, aliases and limitations; no arbitrary executable deserialization |

## Stages are independent evidence gates

| Stage | PASSED requires | Does not establish |
| --- | --- | --- |
| `analyzable` | Complete bounded analysis, exact source fingerprint | Source-profile admission or conversion |
| `convertible` | Successful converter result and exact output, prior analysis | Runtime linkage or semantics |
| `bytecodeValidated` | Explicit output validation proof, prior conversion | A game boot, class resolution in every path, correct behavior |
| `clientInitializes` | Actual client discovery/start evidence, exact output and static proof | Gameplay |
| `serverInitializes` | Actual dedicated-server discovery/start evidence, exact output and static proof | Gameplay or connected players |
| `clientBehavior` | Specified in-game acceptance observations and client initialization | Untested behaviors or multiplayer |
| `serverBehavior` | Specified server behavior observations and server initialization | Client rendering, crafting from data reload alone, multiplayer |
| `multiplayer` | Actual multiplayer test evidence and both initialization gates | Untested peers, mods or network conditions |

Statuses: PASSED, FAILED (attempted and failed), BLOCKED (refused by a prerequisite
or safety gate), PARTIAL (incomplete coverage), NOT_RUN (not executed). NOT_RUN
has no completed evidence references. A scoped PASSED must document its precise
tested behavior and exclusions in the metadata; it never means universal parity.
Cancellation is a scenario/operation outcome, not a new mod compatibility stage.

Evidence fields are `id`, `type`, `sourceSha256`, `targetSha256`, `sha256` and
exactly one of `path` / `url`. Paths are repository-relative, not corpus-relative.
Types: AUTHORIZATION, ANALYSIS, CONVERSION, BYTECODE, CLIENT_INITIALIZATION,
SERVER_INITIALIZATION, CLIENT_BEHAVIOR, SERVER_BEHAVIOR, MULTIPLAYER, SCREENSHOT.
All non-authorization evidence binds the exact original. Output-dependent passes
also bind the exact converted SHA. Screenshots support a runtime record but
cannot alone substitute for a typed behavior/start proof. Build counts and
mapping counts are not allowed evidence types.

Paths reject traversal, aliases/symlinks, non-regular files, Windows devices and
unsafe components. Missing historical local files are listed as missing, never
hash-verified. HTTPS syntax permits no credentials, token query, fragment or
nonstandard port; the verifier does not fetch or certify URL contents or legal
authorization. A known license string alone cannot authorize a real mod record.
Raw private metadata and authentication data must not enter versioned extras.

`CorpusVerifier.Summary` separately reports real artifacts, distinct real
projects and fixture identities, plus separate stage counts and missing evidence.
The counts are historical record attestations checked for integrity/consistency,
not new tests executed by `verify`. Deliberately dishonest typed attestations
cannot be disproved by a hash checker; source/evidence review remains mandatory.

`CorpusRunner` observation schema 1 is separate from record schema 1. It records
catalog/record/input/mapping hashes, bounded stage facts, local raw-report hashes,
operation/error codes and an output fingerprint. It never mutates the catalog or
imports prior runtime statuses. CompatibilityReport and TransformationLog retain
their existing schema versions unchanged.
