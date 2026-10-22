# LegacyBridge third-party notices

Publication preparation: 2026-10-22. LegacyBridge's original covered code is now
GPL-3.0-only with the Minecraft/Forge linking permission in LINKING_EXCEPTION.md.
LICENSING.md and the complete GPL text accompany the artifact. No royalties or
revenue share are required. Independent dependencies retain the terms below.

The public source repository supplies the application sources, build scripts
and the independent SRG Utils rebuild/replacement tool. Native Forge loading
with a changed library is still unqualified. These notices are not permission
to redistribute private mapping inventories, Minecraft or third-party mods.

## Included library: SRG Utils 0.6.2

Additional data component (2026-09-12): the one-class Forge annotation contract
under `META-INF/legacybridge/cohort-preview-v1/` has its own LGPL-2.1-only
attribution, editable declaration data and complete license. It includes no
Forge implementation or Minecraft/MCP inventory; see that component's NOTICE.md.
This addition does not clear full local mapping bundles or third-party mods.

- Coordinates: `net.minecraftforge:srgutils:0.6.2`.
- Copyright (c) Forge Development LLC and contributors.
- License: **GNU Lesser General Public License, version 2.1 only**
  (`LGPL-2.1-only`). The full license accompanies this notice as `LGPL-2.1.txt`.
- Project: <https://github.com/MinecraftForge/SrgUtils>.
- Published binary: <https://maven.minecraftforge.net/net/minecraftforge/srgutils/0.6.2/srgutils-0.6.2.jar>.
- Published source: <https://maven.minecraftforge.net/net/minecraftforge/srgutils/0.6.2/srgutils-0.6.2-sources.jar>.

LegacyBridge uses this library to parse and represent symbol mappings. Its
binary is included unchanged as a Forge Jar-in-Jar library, not copied into
LegacyBridge packages or relocated. The exact nested path is recorded in
`META-INF/jarjar/metadata.json`, under the identifier `net.minecraftforge:srgutils`.
Negotiation is restricted to `[0.6.2,0.6.3)`; this is a dependency-selection
constraint, not a claim that every possible replacement has been validated.

The exact published source archive is included unchanged, with only its outer
filename changed, at
`META-INF/legacybridge/legal/srgutils-0.6.2-sources.zip`. It contains eight Java
source files with the upstream copyright and SPDX notices. These are mapping
library sources, not Minecraft/Forge game sources, fixture sources or test
harnesses. The archive does **not** include upstream build scripts or a license
file. This separate `LGPL-2.1.txt` supplies the license text; the application source repository also supplies the independent Java 17/release 8
rebuild recipe and relinking utilities in legacy-corpus. See docs/SRGUTILS_RELINKING.md.
The recipe does not claim to reproduce the upstream binary byte for byte.

Pinned SHA-256 values:

```text
binary:  f10b366cdc4df20de8a50511f49fded5ea0fe8f7a476ff984a12f65a1602533e
sources: 3f0c9f692d6243ef6c854408aa93b47ccb77343cc6d80d8a5d85c16b46f93dec
```

These hashes identify the reviewed upstream copies; they do not grant rights
or prove that an independently modified library is semantically compatible.
The library and its use are subject to the accompanying LGPL. The upstream
license includes its warranty and liability disclaimers.

### Replacement and remaining obligations

The nested dependency is kept as an independent, unmodified library to retain
an inspectable replacement boundary. A technical experiment with a replacement
must be performed on a separate copy of LegacyBridge while the game is closed:

1. Inspect `META-INF/jarjar/metadata.json` and identify the nested SRG Utils path.
2. Replace that nested entry in the separate copy with a rebuilt,
   interface-compatible SRG Utils library, retaining its packages and module
   identity. Keep the declared artifact version compatible with the recorded
   range, or explicitly update the private copy's metadata to match a separately
   tested dependency decision. Do not change unrelated entries or install two
   LegacyBridge copies.
3. Validate the ZIP/metadata, start a separate ordinary Forge 47.4.23 installation,
   and repeat the analysis, planning and conversion equivalence checks. A
   successful Forge start alone is not a replacement-behavior test.

Jar-in-Jar metadata does not record a dependency checksum. The shipping build's
hash checks establish the exact original baseline; they are not evidence that
arbitrary replacement binaries work. The independent source rebuild and replacement procedure has headless tests;
native Forge loading and broader semantic compatibility are separate pending checks.
GPL grants for LegacyBridge do not grant rights for third-party mods. Preserve the
application source snapshot, dependency source, build tools and all notices when
distributing the prepared experimental release. The application source is at
https://github.com/IanZuccheri/LegacyBridge .

The project's prior proprietary terms are superseded for this release by
GPL-3.0-only with its explicit Minecraft/Forge linking permission. The original
LGPL-2.1-only license still governs SRG Utils; it has not been relicensed.

## Platform-provided libraries: no duplicate copies in LegacyBridge

The public mod is restricted to Minecraft 1.20.1 / Forge **47.4.23** / Java 17.
It uses the libraries supplied by that ordinary installation. Other Forge
patches require a new compatibility audit and execution tests.

| Library | Version audit | Packaging decision |
|---|---|---|
| ASM core, commons, tree, analysis, util | 9.9.1, BSD-3-Clause | Use the pinned Forge platform copies; no bundled or relocated ASM |
| NightConfig core and TOML | 3.6.4, LGPL v3 | Use the pinned Forge platform copies; no bundled or relocated parser |
| SLF4J API and logging implementation | Platform-provided; the separate development CLI pins SLF4J 2.0.17 | No `org.slf4j` classes, provider services, `slf4j-simple`, or logging configuration bundled in the public mod |

Upstream information:

- ASM license: <https://asm.ow2.io/LICENSE.txt>.
- NightConfig 3.6.4 license: <https://github.com/TheElectronWill/night-config/blob/v3.6.4/LICENSE>.
- SLF4J license: <https://www.slf4j.org/license.html>.

The NightConfig audit concerns the exact 3.6.4 release, not any later licensing
change. Standalone CLI distributions have their own dependencies and are not
embedded in the public mod. The engine's bounded JSON parser and canonical JSON
writer are original LegacyBridge implementation; no additional JSON library is
bundled for them. Original classes from the runtime, neutral adapter API, engine,
workflow and client interface are combined without relocating the adapter ABI.

## Data and mod rights

Original report schemas, the adapter catalog, finite conversion-rule code,
supported-profile declarations, version identifiers and provenance hashes are
included. The engine now also includes an original finite source-contract
vocabulary for the existing `simple-content-v1` interpreter, at
`META-INF/legacybridge/simple-content-v1/source-contract-v1.tsv` (revision 1).
It is not a complete or general Mojang/MCP mapping database. It contains selected
symbol references, aliases and declaration facts checked against pinned sources,
not their implementations, full tables or obfuscated names. Recognizing a name
does not authorize a transformation: existing implemented adapter rules still
determine admission. Other profiles require explicit development data.

SRG names are verified using MCPConfig from Forge Development LLC and
contributors. This application-specific contract selection is an altered,
independently structured vocabulary, not an original MCPConfig distribution.
The unmodified upstream notice accompanies this artifact as `MCPConfig-LICENSE.txt`.
Source: <https://github.com/MinecraftForge/MCPConfig/blob/master/LICENSE>.
Official symbol references are verified with the pinned Mojang mapping data;
Mojang's complete tables are not redistributed. This engineering distinction
does not grant a new license for that data or settle the public rights review.

No old Minecraft/Forge implementations, game source, fixtures, harnesses,
Mojang mapping TXT files, MCP archives, or locally generated `.lbmap`/`.lbapi`
inventories are included. The player-facing simple profile uses the included
finite contract without a local bundle or hidden download. An explicitly
selected trusted full bundle remains optional for that profile and necessary
for broader development profiles/inspection; an invalid explicit selection
never silently falls back to the embedded contract.
The generated bundle policy remains
`LOCAL_USE_ONLY_NOT_APPROVED_FOR_REDISTRIBUTION`; repackaging metadata does not
automatically establish redistribution rights.

Each original mod remains subject to its own license. LegacyBridge does not
grant permission to transform or redistribute a mod, its textures, sounds or
other content. Conversion, installation and public redistribution are different
operations with different requirements.
