# Add-only installation: scope and recovery contract

Historical P4/V1 API contract, retained for inspection and development callers.
The current native UI uses the [P5 transactional installer](TRANSACTIONAL_INSTALLATION.md);
V1 receipts remain readable and can authorize explicitly selected managed upgrades.

This milestone implements one explicit installation of one already converted
`simple-content-v1` JAR. It does not enlarge conversion support. The pinned
baseline remains Minecraft 1.16.5 / Forge 36.2.42 to Minecraft 1.20.1 / Forge
47.4.23 / Java 17, with `legacybridge_runtime` and adapter version
`0.1.1-SNAPSHOT` unchanged.

The implementation is in
`legacy-product/src/main/java/dev/legacybridge/product/install`. This document
describes that implementation, superseding the future-only installation text in
older architecture documents for this narrow add-only operation. It is not a
claim that replacement, automatic recovery, restart, or activation is complete.

## Authority and phase separation

The controller must retain the original and converted SHA-256 values from the
completed engine job in the current session. A user-selected file or editable
JSON report is not sufficient authority. The installer accepts six arguments:

```java
InstallResult apply(Path gameDir, Path stagedJar, String expectedSha256,
                    Path original, String expectedOriginalSha256,
                    Set<String> loadedModIds) throws InstallException;
InstallResult inspect(Path gameDir, Path transactionDirectory)
        throws InstallException;
```

`gameDir` comes from the actual instance. The original must remain below
`legacybridge/input`; the result must be below `legacybridge/staging`. Both must
be canonical real regular files and must not alias one another. Existing real
workspace directories are required; installation does not create or repair the
instance layout. The loaded-ID snapshot must include `minecraft`, `forge`, and
`legacybridge_runtime`.

The controller obtains explicit confirmation before calling `apply`, runs it
off the render thread, and does not cancel or interrupt an installation after
starting it. Closing the screen must not imply cancellation or installation
failure. The installer has an internal publication barrier, not a public
cancellation API.

```text
engine analysis/conversion/validation -> staging (not installed)
explicit Apply -> install preflight -> one add-only publication
publication -> PENDING_RESTART (not loaded, activated, or gameplay-tested)
```

Continuing conversion does not install results or change the running mod set.
Installation never adds an input directory to Forge discovery, defines a mod
class, reloads a classloader, or registers content in the current session.

## Preflight and static validation

Before publication, the installer:

1. Validates the workspace, input/staging containment, original hash, and
   converted hash against the session result.
2. Obtains a process-local lock and a cooperative filesystem lock at
   `legacybridge/transactions/add-only.lock`.
3. Creates an owned UUID transaction directory, copies the converted JAR to
   its private `payload.jar`, and forces that file's contents to storage.
4. Reuses the bounded archive reader, metadata analyzer, offline bytecode
   validator, runtime content-plan parser, adapter-binding verifier, and
   existing resource validator. No original or installed mod class executes.
5. Requires exactly the generated one-class bootstrap, exact implemented
   bootstrap call recipe, profile manifest, target dependencies, adapter
   bindings, and supported content resources. The generated target mining tag
   is separately checked against its implemented adapter before applying the
   source-oriented resource validator. Target `pack_format` must be 15.
6. Inventories installed direct JARs and loaded IDs, refuses duplicate incoming
   IDs, and writes the prepared receipt.
7. Revalidates paths, source/staged/payload hashes, and the installed JAR set
   and hashes immediately before publication.

This revalidation does not convert an arbitrary hand-written JAR into an
authorized result. The trusted session controller remains responsible for
provenance of the two supplied engine fingerprints. Static validation is
separate from Forge discovery and from actual item/block/recipe behavior.

## Supported installed-mod layout

The current inventory deliberately fails closed for layouts it cannot fully
enumerate within its narrow grammar:

- Only direct children of `mods` are considered. Every direct child must be a
  canonical real regular file. Ordinary subdirectories are currently refused,
  as are symbolic links, junction aliases and special files. Regular non-JAR
  files are ignored after this path check. A harmless directory can therefore
  block Apply; the installer neither traverses nor removes it.
- Every direct `.jar` must have readable, unambiguous JavaFML metadata, a
  supported loader range admitting 47, and declared IDs matching the statically
  scanned `@Mod` identities. Missing metadata, unsupported language loaders,
  truncated inventories, and malformed archives refuse the entire operation.
- Declared IDs must be unique across installed JARs. The incoming ID must occur
  neither in the loaded-ID snapshot nor in any installed JAR, including JARs
  added externally after launch. The matching runtime ID/version must also
  physically exist in `mods` for the next launch.
- Minecraft and Forge dependency declarations must admit the pinned target.
  Other dependency ranges must use the implemented numeric grammar, except
  the two explicit supported runtime SNAPSHOT ranges. Dependencies declared by
  existing mods **on the incoming mod** must admit the candidate's validated
  version, whether mandatory or optional. An unknown range or an uncomparable
  candidate version refuses installation; an omitted range is unbounded.
  Both declared sides are checked conservatively rather than assuming a
  client/server-specific installation. This is **not a complete dependency
  solver for the existing modpack**: existence/version closure and ordering
  cycles among arbitrary installed third-party mods are not proved.
- Unknown Jar-in-Jar dependency closure is refused, even when another valid
  Forge mod uses it. The one implemented exception is LegacyBridge's own
  single `net.minecraftforge:srgutils:0.6.2` entry, negotiation range
  `[0.6.2,0.6.3)`, and exact audited nested binary SHA-256:
  `f10b366cdc4df20de8a50511f49fded5ea0fe8f7a476ff984a12f65a1602533e`.
  No nested code is loaded to determine its identity.

The runtime check proves declared identity/version and the recognized nested
dependency, not a cryptographic attestation of every runtime implementation
class against the running artifact. Arbitrary changes to an existing modpack
remain outside this installer. Its snapshot is not a full simulation of Forge
discovery, custom locators, service behavior, or future-launch dependency
resolution. In particular, Apply is not evidence that an independently broken
installed set can start successfully.

A future relaxation for harmless directories must first prove the target
Forge discovery policy, retain refusal of unsafe aliases, and use exactly the
same filtering in initial inventory and commit-time revalidation. Simply
skipping unknown directories to make an installation appear successful is not
the current contract.

The inbound check follows the pinned Forge source behavior:
`fmlloader`'s `ModSorter.verifyDependencyVersions()` checks optional dependencies
when their target is installed; `moddiscovery/ModInfo.ModVersion` uses an
unbounded range when `versionRange` is absent. See the exact source artifact
and dependency evidence in [product API baselines](PRODUCT_API_BASELINES.md).
This installer deliberately implements only the existing conservative numeric
comparison, not Maven's entire version-ordering grammar.

## Publication and transaction files

An operation creates only its own directory and the following new files:

```text
legacybridge/transactions/<uuid>/payload.jar
legacybridge/transactions/<uuid>/prepared.json
legacybridge/transactions/<uuid>/committed.json   (after publication)
mods/legacybridge-<modId>-<uuid>.jar               (new hard link)
```

The payload is fully copied, closed, forced, and validated outside `mods`.
Publication uses `Files.createLink` to add a new destination name pointing to
that complete payload. There is no replacement and no partially copied JAR in
`mods`. The payload and `mods` must be on the same filesystem, and the provider
must support the hard-link operation. Cross-filesystem layouts, unsupported
providers, locked/denied operations, and name collisions are refusals. There is
no fallback to a non-atomic copy or an overwrite-capable move.

After publication, `payload.jar` and the installed JAR are two names for the
same file; they are not independent backups. Do not edit either file in place.
The original and staging result are separate files and are never overwritten,
renamed, deleted, or installed directly. The installer never updates or removes
an existing mod or the LegacyBridge runtime.

Receipts use schema version 1 and report type `LEGACY_INSTALL_TRANSACTION`.
They record the UUID, owned destination basename, original and result hashes,
one mod ID, state, and durability limitation. The prepared state is
`PREPARED_ADD_ONLY`; the committed state is `PENDING_RESTART`.

| Boundary/outcome | Meaning and handling |
|---|---|
| Failure before publication | No destination was created by the operation. Private payload/receipt evidence may remain. |
| Successful publication and receipt | Return `InstallResult`, with installed path, transaction path, result hash, IDs, and `PENDING_RESTART`. |
| Failure after publication | `PUBLISHED_RECEIPT_INCOMPLETE` retains the installed path and transaction. The controller must not lose the installed outcome or offer a blind retry. |
| Inspection proves matching hard link, hash, and valid payload | Return `PENDING_RESTART`, even if the committed receipt was never written. |
| Inspection finds no destination | `NOT_PUBLISHED`; inspection does not retry installation. |
| Inspection cannot prove completion | `RECOVERY_REQUIRED`; absence of a proven installed path is not proof that nothing was installed. |

`inspect` is read-only. It bounds and parses the prepared receipt, derives an
operation-owned destination rather than trusting an arbitrary path, and checks
actual file identity, hash, and the staged profile. It does not rely on a
committed-state string alone, automatically repair a receipt, retry a copy,
delete a file, or roll back a potentially loaded mod. It is a diagnostic API,
not an automatic startup recovery service.

The prepared schema requires exactly its known fields, `PREPARED_ADD_ONLY`,
the declared durability limitation, and well-formed lowercase SHA-256 values
for both fingerprints. Tampered or unknown values refuse inspection even if
an installed file remains present; an invalid receipt is not evidence that an
installation did not occur.

## Durability, concurrency, retention and bounds

Payload and receipt writes use create-new semantics and `FileChannel.force`.
This does **not** promise total power-loss durability. Parent-directory fsync is
not guaranteed by this portable Java/Windows implementation; storage devices,
filesystems, operating-system crashes, and power loss can violate stronger
assumptions. A process crash can leave a partial receipt outside `mods`, and
inspection can then require manual recovery. Unit boundary injection is not a
power-cut or filesystem-corruption test.

The JVM lock serializes installer calls in this process; the lock file
coordinates cooperating processes. Repeated path/hash checks reduce races but
are not an operating-system sandbox against an uncooperative process changing
directories or files at the same time. Filesystem calls and parsers are not
hard-preemptible. The scope does not guarantee a strict JVM heap ceiling.

| Limit | Current implementation |
|---|---|
| Original, staged, payload or installed JAR read/hash | 64 MiB per file during Apply |
| Cumulative inflated archive work per Apply | 512 MiB |
| Cumulative captured archive work per Apply | 256 MiB |
| Retained staged resource bytes during revalidation | 64 MiB |
| Captured entry during staged validation | At most 16 MiB, or the stricter archive policy |
| Installed metadata / Jar-in-Jar metadata capture | 1 MiB per entry |
| Installed class / nested JAR capture | At most 16 MiB, or the stricter archive policy |
| Cooperative pre-publication deadline | 3 minutes; not a hard OS-call timeout |
| Direct `mods` entries / JARs | 1,024 / 256 |
| Retained transaction directories / direct transaction entries | 32 / 128 |
| Prepared receipt read during inspection | 16 KiB |

Limits compose with the existing archive policy; the stricter limit wins.
`inspect` uses bounded receipt/archive/hash readers, but does not create Apply's
three-minute `OperationControl` scope. Call it outside the render thread too.
The installer currently retains its transaction payloads and receipts, including
failed attempts, rather than automatically deleting recovery evidence. At the
retention limit it refuses further installs and requests explicit review. These
files are not cache; there is no implemented automatic retention/cleanup tool.
Removing payloads can prevent later hard-link identity inspection even when the
installed file still exists. Existing scanner-owned short-lived snapshots use
the archive reader's normal cleanup policy.

## Not implemented and evidence requirements

Not included: replacing/upgrading installed mods; uninstall; multi-JAR atomic
commit; arbitrary JiJ dependency resolution; a full modpack dependency solver;
repairing unknown layouts; startup recovery/bootstrap hooks; automatic restart;
next-launch activation observation; world migration; or additional conversion
features. Manual launcher relaunch is required. The current session does not
prove that Forge discovered the new JAR.

`AddOnlyInstallerTest` supplies small inert positive/negative fixtures and
injected pre-/post-publication failures. It covers preservation of original
bytes, missing/malformed/duplicate installed identities, hash/path/binding and
resource refusals, unknown nested dependencies, retained-transaction limits,
compatible and incompatible inbound dependencies (including optional ones),
unknown version comparisons, receipt-schema tampering, and truthful inspection
after a post-publication failure. Such tests do not
execute Minecraft or simulate physical power loss. Test execution, final
artifact hash, ordinary Forge launch/discovery, and real content behavior must
be reported as separate evidence by the validation run; this document alone
does not mark those gates passed.

This feature does not change the mapping-data import or redistribution gates
in [portable mapping data](PORTABLE_MAPPING_DATA.md) and
[packaging rights](PACKAGING_RIGHTS.md).
