# Explicit portable mapping and API data

2026-09-12: [typed local data review](PROMPT_02_HEADLESS.md) adds
`inspect-data --mapping-bundle <directory>` (or `--builtin <contract>`), a
schema-1 scope/hash/count summary, the client's Data/Dados action and optional
CLI `--mapping-sha256` binding. Data is strictly revalidated on each use;
changed reviewed manifests fail with `MAPPING_DATA_CHANGED`. Complete index
kinds do not guarantee optional library closure or semantic compatibility.
Individual composition keeps the explicit local full-data route after auditing
its ownership/hierarchy needs. No complete bundle has been embedded or cleared
for redistribution. See the linked ledger for cacheless fixture equivalence.

2026-09-12 addition: [included cohort declarations](FINITE_COHORT_DATA.md) also
serve the exact authored pair's source/target preview without this full bundle.
That annotation-only `FINITE_COHORT_CONTRACT` is incomplete and cannot be exported
as a full environment or used by generic mappings/ordinary conversion profiles.
Other advanced operations still require explicit full data. No private bundle
has become redistributable or been embedded.

Current scope (2026-09-07): this page describes full explicit development
bundles. The player-facing `simple-content-v1` now defaults to the smaller
[included finite source contract](EMBEDDED_SIMPLE_CONTENT_DATA.md), not an
export of this bundle. Its `complete=false` scope cannot be exported here.
Other profiles and broad mappings still require full explicit data; all bundle
rights restrictions below remain unchanged.

P2 adds `PortableMappingBundle.export(MappingInputs, Path)` and
`PortableMappingBundle.load(Path)` in `legacy-converter`. They export/import
**local development data**, not game implementations. Loading uses only the
directory selected by the caller; it never searches Gradle caches, Maven,
Minecraft installations or the network. Source preparation is a separate,
explicit operation using the existing pinned loader or a local profile.

This is not a new conversion profile. `simple-content-v1`, its finite semantic
proof, adapter ABI, reports and output namespace are unchanged. The bundle does
not make a previously unsupported call safe to rewrite.

## Preparation and import workflow

The CLI and service expose the same preparation/import path:

```text
legacy-converter prepare-mappings --from-development-cache --output local-data/bundle-v1
legacy-converter prepare-mappings --mapping-profile mappings.properties --output local-data/custom-bundle-v1
legacy-converter mappings mod.jar --mapping-bundle local-data/bundle-v1
legacy-converter convert mod.jar --mapping-bundle local-data/bundle-v1 --output staging/converted.jar --report reports/conversion.json
```

`prepare-mappings` is an explicit development/setup command. The mandatory
choice `--from-development-cache` reads the already prepared, pinned local
development baselines; it does not populate those baselines. Alternatively,
`--mapping-profile` selects already available files
using [the documented format](MAPPINGS.md#explicit-mapping-profile).

The bundle output's parent must already exist. The final directory must not
exist, even if empty. There is no overwrite option. The caller can copy its own
locally prepared bundle to a different directory or installation under its
applicable rights, then explicitly select that directory. The consuming
installation does not need Gradle, the original API JARs, mapping TXT/ZIPs or
their original absolute paths. Ordinary CLI libraries and Java 17 remain
required until P3 packages the public artifact.

Missing/invalid data is an error, not an invitation to search a player cache or
download something. The future UI must expose setup/import failure separately
from mod incompatibility. A downloadable, universally redistributable bundle
is **not** supplied or approved by this milestone.

## Bundle schema and provenance

Each directory contains exactly these seven direct regular files:

| File | Content |
|---|---|
| `manifest.json` | Schema 1, profile, exact baselines/namespaces, completeness, original input hashes/origins, diagnostics and six payload lengths/SHA-256 values |
| `legacy-official.lbmap` | Source official-to-obfuscated class/package/field/method mapping projection |
| `target-official.lbmap` | Target official-to-obfuscated mapping projection |
| `legacy-srg.lbmap` | Source obfuscated-to-legacy-MCP/SRG projection |
| `target-srg.lbmap` | Target obfuscated-to-intermediate projection |
| `legacy-api.lbapi` | Source class/superclass/interface/access and field/method declarations |
| `target-api.lbapi` | Target class/superclass/interface/access and field/method declarations |

The manifest pins Minecraft `1.16.5` / Forge `36.2.42` to Minecraft `1.20.1` /
Forge `47.4.23` / Java `17` and `profile=simple-content-v1`. The namespace
directions remain explicit, including the hybrid legacy development namespace
and `MOJANG_CLASS_NAMES_WITH_SRG_MEMBERS` output. Target intermediate `C_*`
classes do not become production owners.

The payloads are custom length-prefixed big-endian metadata with independent
magic values (`LBMP`, `LBAP`) and schema 1. They are not Java serialization,
classfiles, JARs, source code or compressed archives. No constructor, method body,
constant value, script, asset or executable plugin is stored. Constructor
**signatures** are preserved as method declarations. Mapping line numbers,
parameter names and arbitrary mapping metadata, which today's resolver and
extractor do not consume, are intentionally excluded. No accepted symbol table
or API declaration is pruned: this first portable format retains the full
consumed metadata inventory, not a guessed minimal class whitelist.

The eight required provenance IDs cover both client/server mappings, both MCP
inputs and at least one API JAR per version. Additional original API JAR hashes
are retained. Original provenance and diagnostics remain unchanged when loaded,
so selecting a bundle does not change the semantic mapping/conversion reports.
The manifest separately records payload hashes; it has no embedded signature.

**Hashes establish integrity, not authenticity or semantic truth.** The import
boundary is explicitly selected, trusted, locally generated data. A malicious
party can replace both a declaration and its manifest hash; this is not a signed
publisher database. The pinned profile labels do not authenticate arbitrary
user-authored mapping profiles. Do not distribute unreviewed bundles as trusted
LegacyBridge release data. A later signed-data distribution requires its own
trust, provenance and rights model.

## Bounds, validation and cancellation

Limits are enforced before allocating declared arrays or parsing expensive
payloads and on actual stream reads/writes:

- Manifest: 256 KiB, strict UTF-8, bounded JSON parser, exact fields, no duplicate keys.
- Payload: 64 MiB each; at most 192 MiB total across the six indexes.
- Classes/packages: at most 100,000 per respective table.
- Members: at most 500,000 per index; at most 2,000,000 total counted records
  (including classes, packages, members and interfaces) across the bundle.
- Individual UTF-8 strings: 4,096 bytes; interfaces: at most 1,024 per class.
- Provenance/diagnostics: at most 64 entries each, with bounded fields.

Only the six fixed names may appear in the manifest. Unknown files,
directories, traversal components, duplicate records, non-injective mapped
class names, malformed JVM names/descriptors/flags, truncated records and
trailing bytes fail closed. Each payload is hashed before any index is parsed,
then hashed again through the parser's actual read stream. The manifest is
rechecked after parsing. Empty/incomplete indexes cannot be exported or loaded.

Symlinks, redirected junctions/aliases and nonregular files are refused at the
selected directory, ancestors and direct payload paths. These checks are not an
OS sandbox against another process concurrently replacing owned directories.
The caller must control the selected parent and files. Read-only importing does
not modify a selected bundle. Hard-link aliasing outside the selected directory
is not authenticated by Java's portable filesystem API.

Export streams into a freshly created sibling temporary directory, closes all
files, writes the manifest last and crosses the operation's publication barrier
immediately before a non-replacing final directory move. `ATOMIC_MOVE` is not
used to imply safe no-overwrite semantics on providers where it can replace a
destination. Readers require the complete manifest and all exact files;
same-parent move behavior is filesystem-dependent, not a cross-filesystem
atomicity guarantee.

Accepted cancellation or timeout before publication removes only fixed direct
files owned by that temporary directory and publishes no bundle. Cancellation
is rejected after the publication barrier. Operation-control exceptions remain
control-plane results, not malformed-mapping errors. Native JVM interruption
is also checked when the API is used without an operation scope.

Bounds constrain payloads and record counts, not exact Java heap consumption.
The full mapping/API object graph can consume substantially more memory than
its disk representation. The service must retain its bounded worker/queue
policy; P3 must measure the actual in-game memory impact. There are no hidden
download threads or persistent mapping-data cache inside this loader.

## Redistribution audit and release gate

Reviewed on 2026-09-05 using the actual pinned local files and upstream primary
notices. This is an engineering distribution policy, not legal advice or an
independent grant of permission.

| Data | Evidence and conclusion for this milestone |
|---|---|
| Mojang official mappings | Both local version headers allow development copying/use and prohibit redistributing the complete unmodified mappings. Their input hashes are recorded in [MAPPINGS.md](MAPPINGS.md). The header also refers to the [Minecraft EULA](https://www.minecraft.net/en-us/eula). Re-encoding the table is **not** treated as automatic clearance for public redistribution. |
| MCPConfig | The upstream [MCPConfig license](https://github.com/MinecraftForge/MCPConfig/blob/master/LICENSE) permits software/data redistribution subject to origin, alteration and notice conditions. Neither exact pinned MCP ZIP contains its own LICENSE/NOTICE entry. This does not establish a blanket grant for every combined Minecraft/MCP-derived artifact. |
| Forge and FML | `LICENSE.txt` in both exact `forge-1.16.5-36.2.42-universal.jar` and `forge-1.20.1-47.4.23-universal.jar` states LGPL 2.1 with exceptions and a non-transitive MCP-data restriction. Corresponding upstream notices: [1.16.x](https://github.com/MinecraftForge/MinecraftForge/blob/1.16.x/LICENSE.txt), [1.20.x](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.x/LICENSE.txt). Forge's license cannot be applied to all Minecraft content in a mapped development JAR. |
| Derived API inventory | Declaration-only export avoids copying implementations, but no legal conclusion that the combined derived inventory is freely redistributable is made. It remains locally prepared data pending specific clearance or an approved end-user acquisition strategy. |
| LegacyBridge code and synthetic tests | Original implementation/fixtures follow the repository license. Packaging third-party libraries and their notices is a separate P3 dependency audit; the data export does not change those obligations. |

Every manifest therefore requires:

```text
distributionPolicy=LOCAL_USE_ONLY_NOT_APPROVED_FOR_REDISTRIBUTION
```

No generated bundle, old Forge/Minecraft implementation, decompiled game source,
Mojang TXT or MCP ZIP is added to application resources. Data generated under
`build/` is a local validation artifact, not a release asset. The explicit,
no-download path is prepare locally from permitted inputs, then import that
local result. Broader redistribution requires rights review of the exact data,
appropriate notices and a documented acquisition/release decision. Until then,
the product cannot claim a universally ready-to-use downloadable mapping pack.

## Validation boundaries

Synthetic tests cover round-trip symbols/declarations, provenance, deterministic
bytes, corruption and length mismatches, strict manifests, unsafe names/paths,
missing/extra files, counts/bytes/UTF-8 bounds, duplicate class declarations,
invalid descriptors, cancellation cleanup, publication linearization and
no-overwrite behavior. The native symlink test explicitly aborts when the host
does not grant permission to create a symlink; that is a missing platform gate,
not a passing symlink execution test.

The isolated development check exported the actual pinned baseline to seven
metadata files totaling 35,223,698 bytes, then independently loaded it with
6,868 legacy and 8,825 target API classes. This measures this host's complete
development inputs, not a universal fixture count or a fixed bundle release
hash. The final [P2 validation record](ENGINE_SERVICE_VALIDATION.md) ties
CLI/service equivalence and the actual converted fixture to the final compiled
artifact, with an isolated Java 17 probe denying external file contents/network
and using empty redirected home/Gradle/temp directories. Separate JVM exports
also produced identical payloads and canonical manifest bytes.
