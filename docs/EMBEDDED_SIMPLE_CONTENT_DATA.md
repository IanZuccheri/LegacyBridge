# Included data for simple-content-v1

Status: experimental/Beta. Contract schema 1, revision 1. This stage removes
external mapping-data setup from the existing player-facing profile; it does
not add a conversion profile, semantic rule, runtime adapter or restart provider.
Execution evidence is recorded separately below; build is not gameplay proof.

Update 2026-09-07: the [new native UI continuation](EMBEDDED_SIMPLE_NATIVE_UI_VALIDATION.md)
accepts DATA-SIMPLE-01-NATIVE_UI on the unchanged public hash. The initial focus
failure below remains historical. Cursor now LB-06 / PUBLIC-CURRENT-E2E for
remaining negative/qualification gates, not broader conversion support.

## Finite contract, not a generic mapping pack

`simple-content-v1` interprets a closed registration graph and replaces it with
the existing adapter bootstrap. It does not retain arbitrary legacy methods.
It needs exact source class/member aliases, descriptors and public/static
declaration facts for its grammar, not the four full mapping tables and two
full API inventories used by broader profiles.

`EmbeddedSimpleContentData` includes only this finite vocabulary. Existing
`StaticContentExtractor`, resource validators, `TransformationBindings` and
implemented runtime adapters still decide admission. Unknown code, wrong
invocation kinds/descriptors and properties without adapters remain refusals.
A recognized API name is not a verified semantic mapping rule.

Inputs are explicitly `SIMPLE_CONTENT_CONTRACT`, `complete=false`. They cannot
be exported as a complete portable bundle, used for generic mapping inspection
or used by the five development profiles. Their empty target API index does
not prove JVM linkage. Existing simple-profile ASM/generated-adapter checks
and separate runtime tests remain distinct.

## Included bytes and provenance

Resource: `META-INF/legacybridge/simple-content-v1/source-contract-v1.tsv`.
Size: 6,603 bytes. SHA-256:

```text
4868d957c3da0707f1178f809f452e43022aa1205d3a803501f563dc37a91170
```

18 selected classes, 26 methods/constructors, 31 fields and five source
fingerprints describe this vocabulary, never compatible mods. Pins remain
Minecraft 1.16.5 / Forge 36.2.42 to Minecraft 1.20.1 / Forge 47.4.23 / Java 17;
runtime identity/ABI `legacybridge_runtime` / `0.1.1-SNAPSHOT` is unchanged.

This independently structured, application-specific source contract is checked
against the existing official client/server mappings, MCPConfig SRG aliases,
source API and eventbus declarations. No obfuscated-name table, implementation,
complete hierarchy, path inventory or full development export is included.
Integration tests check every retained declaration/access flag, composed alias
and five source digests. Source hashes are evidence, not files searched or
downloaded on a player's machine. Target semantics remain existing runtime recipes.

Loading checks the exact hash, schema/pins, a 32 KiB cap, bounded rows/members,
strict UTF-8, descriptors and alias uniqueness. Missing/tampered data aborts;
no cache/network fallback. Git newline conversion is disabled for pinned text.
Packaging checks the resource hash/size and still rejects `.lbmap`, `.lbapi`,
platform classes, private fixtures and unexpected embedded dependencies.

## CLI, service and menu

With no explicit mapping selection, the default command is now:

```text
legacy-converter convert mod.jar --output staging/converted.jar --report reports/conversion.json
```

The menu uses the same service: leave the optional advanced data field blank.
`MappingData.builtinSimpleContent()` explicitly selects the included contract.
`MappingData.unavailable()` still means unavailable, not an implicit fallback.
Background/queue/cancellation/publication boundaries are unchanged. Installation
is a separately approved transaction; activation requires another Forge launch.

Explicit CLI `--mapping-profile`, `--mapping-bundle` or nonblank
`LEGACYBRIDGE_MAPPING_BUNDLE` takes precedence. The UI's nonblank absolute
bundle path is also explicit. Bad explicit data yields a structured error
without fallback. Broad `mappings` and other profiles need full explicit data;
finite service input there produces `MAPPING_SCOPE_UNSUPPORTED`.

CompatibilityReport/TransformationLog keep their schema versions. Included-data
conversion reports add `mappingData.scope`, `completeMappingEnvironment=false`,
`supportProfile` and source provenance. Original mod classes remain unexecuted.

## Rights boundary

The [portable bundle policy](PORTABLE_MAPPING_DATA.md) remains
`LOCAL_USE_ONLY_NOT_APPROVED_FOR_REDISTRIBUTION`. No renamed/pruned/reencoded
full development export is shipped. SRG provenance is attributed to Forge
Development LLC and contributors; the full [MCPConfig notice](https://github.com/MinecraftForge/MCPConfig/blob/master/LICENSE)
is packaged and the application-specific selection is marked altered.

Actual official mapping headers allow development use and restrict complete
unmodified redistribution; changing formats is not treated as a new license.
The [Minecraft EULA](https://www.minecraft.net/en-us/eula) is not a blanket grant
for a derived database. This finite reference vocabulary follows the existing
rule-code boundary, but the engineering choice is not legal advice or release
clearance. [Packaging rights](PACKAGING_RIGHTS.md), including LGPL/project-license
obligations, remain open. Input mod/asset rights are unchanged. Nothing is uploaded.

## Acceptance and evidence

Required separately: unit refusals/corruption; real-API checks; genuine fixture
output parity; CLI/service parity in an isolated installation without bundle/cache
access; final archive audit; native public-only client conversion with blank
data field; same-artifact dedicated server without client class resolution.
Evidence must bind the new public hash, not inherit P6's older results.

The [versioned ledger](evidence/embedded-simple-content-2026-09-07.json) binds
the public artifact (1,208,820 bytes) to SHA-256
`3749da06606f36b7e9bcbb97e4d9558f91d0bfa1c60e5da533767fd722607df6`.

- Full real-environment build passed in 14m33s: 916 Java cases freshly observed,
  914 passed and two native-symlink privilege skips; runtime/workflow's 62 cases
  were UP-TO-DATE. All six profiles' explicit data gates were enabled. These
  include 42 finite-contract unit cases and two real-input comparison cases.
- Final notice-packaging standard build passed in 4m26s. It does not enable the
  65 explicit environment tests already recorded above. Unit/controller/queue
  and packaging checks passed, plus 488 pure PowerShell cases (17 new preflight
  cases). Test counts are not supported-mod counts.
- Isolated CLI/service conversion, plan and canonical reports passed without a
  bundle, with empty user/Gradle/temp directories and zero forbidden external
  file/network/process attempts. Output remains exactly 6,979 bytes, SHA-256
  `541cc64b44b97d0e4e79a193dd2ce3a86a5feba6e15714d0ee24ad63751492bb`;
  original SHA-256 remains `00dd689f53f9631ecf4a8e84da142eb21051e2ca33f1c05403e4060e15b10947`.
  This is a test-only Java 17 tripwire, not a game/offline/sandbox claim.
- Initial historical attempt: ordinary public-only Forge client loaded the runtime and reached its native
  window; normal close returned zero, no world opened. Windows denied foreground
  automation and requested user assistance did not arrive. **No new UI conversion
  or screenshot is claimed.** Its initial QA simulation-distance value 4 was
  rejected (minimum 5); corrected while closed, not yet rerun.
- Same public artifact plus the converted fixture passed native dedicated-server
  readiness, block placement/tag, item NBT, datapack reload, normal save and exit.
  The final fresh normal-world run has zero ERROR lines, no QA mod/bundle,
  no development-cache or product-client classes resolved. A prior flat-world QA
  configuration caused a logged fallback; that entire first server is preserved
  separately and is not called a clean run. Reload is not crafting evidence.

The initial cacheless wrapper treated Java's deprecated SecurityManager warning
as a PowerShell terminating error. Both logs are preserved; corrected outer
process capture succeeded. No such guard is included in the public JAR.
All owned native processes/port listeners were stopped at closeout.

**DATA-SIMPLE-01-NATIVE_UI is now accepted for its finite scope**, with a
[separate native ledger](evidence/embedded-simple-native-ui-2026-09-07.json).
Real UI used the empty optional field, preserved staging/session separation,
committed one approved ADD before closing and discovered the mod on a separate
launch. Native crafting, placement, assets and both translations passed; corrupt
input was refused. No QA mod, original class execution or mocked screenshot was
used. Public release, general offline gameplay and wider-profile independence
remain unapproved. Remaining E2E negatives and persistent job/install linkage
are explicit, not covered by this bounded GUI acceptance.

Networking's `NET-QA-02-HANDSHAKE / CLIENT_PROTOCOL` normal-oracle failure stays
open. The user's product-data priority temporarily precedes that investigation;
included mappings do not fix it or enable capabilities.
