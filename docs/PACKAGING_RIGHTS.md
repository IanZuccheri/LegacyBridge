# Single-JAR packaging: rights audit and release gates

## Current owner decision 2026-10-22

The owner authorized public source publication and selected GPL-3.0-only with
an explicit Minecraft/Forge linking permission. See ../LICENSING.md and
../LINKING_EXCEPTION.md. No payment, 5% share or mandatory donation applies.
The previous owner-term decision is resolved; independent third-party rights
and licenses are not changed. The source snapshot includes application build
files, the original Gradle wrapper notices, finite-data notices and the existing
SRG Utils rebuild/replacement implementation. Distribution must retain those
materials and the dependency sources already included in the public JAR.

The user will submit CurseForge manually. Source publication is not a claim
of native qualification of the current binary or a blanket clearance of local
mapping inventories. Current-hash gameplay and changed-library Forge loading
remain pending; do not advertise a stable/universal release. The audit below
is historical and explains how the packaging boundary was established. Its
statements about an unchanged proprietary LICENSE describe the older artifact,
not the current GPL grant.

## Historical audit

Audit date: 2026-09-05. Decision: **REVIEW_REQUIRED for public distribution**.
This is an engineering record, not legal advice. A successful build, packaged
Forge launch, or conversion test does not establish distribution permission.

Technical addendum, 2026-09-12: [local source rebuild/relinking](SRGUTILS_RELINKING.md)
is implemented and tested in private copies, including modified-library behavior
and finite public-engine equivalence. The public JAR and project LICENSE did
not change. This independent recipe is not upstream release reproduction or
complete rights clearance. The owner terms/release-material decision and native
Forge JiJ/JPMS/coexistence validation remain open; see the
[artifact-bound evidence](evidence/relink-2026-09-12.json).

## Selected boundary

`legacy-product` combines LegacyBridge's runtime, neutral adapter API, reusable
engine, workflow and client interface into one public-mod candidate. The runtime
identity remains `legacybridge_runtime`, with adapter/runtime version
`0.1.1-SNAPSHOT`; no adapter ABI migration or extra required LegacyBridge JAR is
introduced. CLI entry points and their logging backend remain development-only.

The candidate depends on the exact tested Minecraft 1.20.1 / Forge 47.4.23 /
Java 17 platform. ASM 9.9.1, NightConfig 3.6.4 and platform logging are provided by
that installation, not shaded into the candidate. SRG Utils 0.6.2 is the only
additional third-party library embedded: its original binary is selected through
Forge Jar-in-Jar with range `[0.6.2,0.6.3)`. It is neither relocated nor merged
into LegacyBridge's own classes. Exact-patch restriction is intentional; these
decisions must not be generalized to all Forge 47.x without fresh evidence.

See the packaged
[third-party notices](../legacy-product/src/main/resources/META-INF/legacybridge/legal/THIRD_PARTY_NOTICES.md)
and [full LGPL 2.1 text](../legacy-product/src/main/resources/META-INF/legacybridge/legal/LGPL-2.1.txt).

## Exact dependency evidence

The audit inspected the installed CLI libraries and their cached published POMs,
not merely the current upstream project descriptions. Those local locations were
development evidence; none is a runtime dependency on a developer's cache.

| Exact component | License evidence | Public-mod treatment |
|---|---|---|
| `org.ow2.asm:asm`, `asm-commons`, `asm-tree`, `asm-analysis`, `asm-util`:9.9.1 | POM `BSD-3-Clause`, matching binary manifest `Bundle-License`, [upstream license](https://asm.ow2.io/LICENSE.txt) | Platform-provided; no copied classes or modules |
| `com.electronwill.night-config:core:3.6.4`, `toml:3.6.4` | Both exact POMs declare GNU LGPL v3; [exact source tag](https://github.com/TheElectronWill/night-config/blob/v3.6.4/LICENSE) confirms it | Platform-provided; do not apply a newer release's license |
| `net.minecraftforge:srgutils:0.6.2` | Exact POM declares `LGPL-2.1-only`; all eight published Java source files carry the same SPDX identifier and Forge Development LLC/contributors copyright | Unmodified nested binary and published source archive; complete compliance still under review |
| `org.slf4j:slf4j-api:2.0.17`, `slf4j-simple:2.0.17` | Exact development binary `META-INF/LICENSE.txt` is MIT, copyright QOS.ch Sarl | These copies are not included; use the exact installation's API/provider in the public mod |

The JSON readers/writers used by the engine are original JDK-only code. This
strategy does not add a bundled JSON library, ModLauncher bootstrap service,
logging provider or classloader implementation.

### SRG Utils provenance and source correspondence

The reviewed publication endpoints are:

- [Binary 0.6.2](https://maven.minecraftforge.net/net/minecraftforge/srgutils/0.6.2/srgutils-0.6.2.jar).
- [Published sources 0.6.2](https://maven.minecraftforge.net/net/minecraftforge/srgutils/0.6.2/srgutils-0.6.2-sources.jar).
- [Published POM 0.6.2](https://maven.minecraftforge.net/net/minecraftforge/srgutils/0.6.2/srgutils-0.6.2.pom).
- [Upstream repository](https://github.com/MinecraftForge/SrgUtils).
- [Upstream LGPL text](https://raw.githubusercontent.com/MinecraftForge/SrgUtils/master/LICENSE.txt).

```text
srgutils-0.6.2.jar SHA-256:
f10b366cdc4df20de8a50511f49fded5ea0fe8f7a476ff984a12f65a1602533e

srgutils-0.6.2-sources.jar SHA-256:
3f0c9f692d6243ef6c854408aa93b47ccb77343cc6d80d8a5d85c16b46f93dec

packaged LGPL-2.1.txt SHA-256 (UTF-8 / LF):
20c17d8b8c48a600800dfd14f95d5cb9ff47066a9641ddeab48dc54aec96e331
```

The source archive is stored byte-for-byte as
`META-INF/legacybridge/legal/srgutils-0.6.2-sources.zip`. Renaming the enclosing
archive does not modify its source entries or notices. It is inert legal/source
material, not a nested loadable JAR and not a game or test source distribution.

The exact published archive contains eight library Java files, but no upstream
build scripts, wrapper, license file or release-to-commit attestation. The audit
found an upstream `0.6` tag, not a `0.6.2` tag; the current `master` build must not
be presented as the exact release build. The included LGPL text is the complete
standard license used by the upstream repository, separate from the SPDX notices
that establish the exact source release's license. No claim of reproducible
source-to-binary correspondence has been made.

## LGPL publication gate

Keeping a replaceable original nested library and supplying its published Java
sources improves the audit boundary. It does **not** alone establish that all
LGPL requirements have been satisfied. Sections 1, 4 and 6 of the accompanying
license govern notices, corresponding source, and conditions for the linked
work. A list of library names or links is not a substitute for those conditions.

The project [LICENSE](../LICENSE) currently retains proprietary restrictions and
has deliberately not been changed in this task. Before CurseForge or other
public distribution, the rights holder must resolve and record:

1. Reconciliation of the project's terms with applicable own-use modification
   and reverse-engineering rights for debugging library modifications.
2. An adequate corresponding-source and build/relinking distribution for the
   exact SRG Utils binary, including whatever build/install materials the chosen
   compliance route requires; published Java sources alone are not declared
   sufficient here.
3. A validated route for a user to use an interface-compatible modified library,
   and the appropriate application object/source and utility materials if that
   route requires them. The technical procedure below and the new finite Java
   test are not full native Forge or rights validation.
4. The final artifact's license/notice/source contents and the permitted release
   terms. No automatic three-year source offer or unsupported legal promise has
   been added on the user's behalf.

Until these gates are closed, the artifact is an internally testable candidate,
not a rights-cleared public release. The original third-party library license
still governs the library; LegacyBridge does not relicense it as proprietary.

### Technical nested-library replacement boundary

The shipped build preserves the independent SRG Utils JAR. The original
procedure below is now implemented in the headless developer tool linked above;
native loading remains a future gate. Work only with a separate copy:

1. Read `META-INF/jarjar/metadata.json` and locate its
   `net.minecraftforge:srgutils` entry and nested path.
2. Replace only that nested library in the copy with a rebuilt compatible
   library, preserving its package and module identities. Ensure artifact
   metadata and version satisfy the recorded dependency negotiation range; a
   different selection policy requires explicit metadata changes and new tests.
3. Audit the resulting ZIP and run the ordinary Forge loading and shared-engine
   equivalence suite on a separate installation, including successful and refused
   conversions. Test the modified library's actual behavior, not just startup.

The Jar-in-Jar negotiation metadata has no library checksum field. Shipping hash
audits identify the reviewed unmodified binary and are not a promise that any
modified binary works. Version labels are not proof of compatibility. This
procedure neither edits a live installation nor guarantees validation of an
unbuilt replacement. Do not install a second copy of LegacyBridge to substitute
a library.

## Mapping and content data

2026-09-12: [FINITE_COHORT_DATA](FINITE_COHORT_DATA.md) adds a separately identified
altered declaration component under LGPL-2.1-only: one public Forge annotation
class/member declaration, exact provenance hashes and no implementation. Its
537-byte preferred editable TSV, attribution and full license are packaged at
`META-INF/legacybridge/cohort-preview-v1/`. Real source headers/declarations from
36.2.42 and 47.4.23 were checked. This engineering distribution decision covers
only that component, not the complete local bundle, Minecraft/MCP/JDK bodies,
original mods, project relicensing or the still-open public release gates.

The original schemas, catalog, finite `simple-content-v1` implementation,
version identifiers and recorded provenance hashes may be packaged as
LegacyBridge's own material. This does not grant rights to included names/data
from arbitrary third parties and does not clear a complete mapping inventory.

The existing [portable-data audit](PORTABLE_MAPPING_DATA.md) remains authoritative:
no generated bundle has been approved for public redistribution. The candidate
contains no Mojang mapping TXT, MCP ZIP, derived `.lbmap`/`.lbapi` files, legacy
game/Forge implementation, game source, private path inventory, fixtures or
harnesses. The SRG Utils library's source archive is not such data.

The [included finite source contract](EMBEDDED_SIMPLE_CONTENT_DATA.md) now
supplies the existing simple interpreter's selected references/aliases and
declaration facts. It is an independently structured original contract, not a
complete inventory or repackaged full data export. MCPConfig attribution and
its unmodified notice accompany the JAR; the selection is marked altered.
Official names are source-verified references, not a wholesale table import.
This engineering boundary is not legal clearance. Broader profiles still need
four symbol tables and two API indexes through explicit local preparation/import.
Their `LOCAL_USE_ONLY_NOT_APPROVED_FOR_REDISTRIBUTION` policy is unchanged.

The simple player flow no longer needs a separately prepared bundle. This is
not a generic pre-cleared mapping pack and does not close the exact-artifact
rights review or LGPL/project-license gates. Original mods/content retain
their own rights; successful conversion is not permission to publish them.

## Evidence to keep separate

The final packaging validation must separately record build/test results,
duplicate-class/metadata audit, ordinary packaged client and dedicated-server
loading, and conversion equivalence. None of those closes the rights gates in
this document. No game process was launched by the rights audit itself.
