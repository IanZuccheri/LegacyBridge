# CurseForge summary

Experimental, open-source Forge 1.16.5 to 1.20.1 converter with an in-game menu, reports and restart-required installation. Supports only a narrow simple-content subset; not a universal converter.

# LegacyBridge

LegacyBridge aims to make more Minecraft Forge 1.16.5 content usable on
Forge 1.20.1 through reviewed transformations and compatibility adapters.

**Experimental Beta — limited compatibility.** The current player workflow
converts only a strictly defined simple-content subset. Most complex mods,
and many mods that only appear to add simple items or blocks, are unsupported.
The latest candidate has headless validation, but fresh native client/server
and gameplay qualification for its exact artifact remains pending. Earlier
successful fixture runs do not certify every later build.

## What you can use today

- **Convert mods / Converter mods** in the client main menu.
- Bounded JAR inventory and static analysis without executing the original mod.
- Serial conversion jobs, cancellation before output publication, individual
  results and readable diagnostics.
- CompatibilityReport and TransformationLog records.
- Staged output, explicit installation approval and journal-based recovery for
  supported operations.
- English and Brazilian Portuguese interface text.

One required **LegacyBridge.jar** contains the engine, runtime and client
interface. The finite mapping/API contract needed by the supported
`simple-content-v1` profile is included. **Leave the optional mapping field
blank**; that profile needs no external mapping pack or developer cache.

## Supported content

Inputs must match the checked Forge DeferredRegister registration pattern and
use only the covered code and resource forms. Within that pattern:

- Plain items, plain STONE blocks and BlockItems with bounded basic properties.
- Vanilla shaped/shapeless recipes and restricted item/block tags.
- Translations, static PNG textures, simple vanilla-template models and blockstates.
- Sound aliases ending at selected vanilla sound events; no custom audio files.
- Absent creative membership or legacy MISC/BUILDING_BLOCKS categories.
  MISC maps to the target Ingredients tab; exact legacy ordering is not promised.

Accepted mod IDs, registry names and resource locations are preserved.
Necessary changes to registration code, metadata, pack format and STONE mining
tags are recorded. The profile replaces a limited registration graph with a
target bootstrap; it does not preserve arbitrary mod behavior.

Unsupported code or resources abort conversion instead of generating a partial
mod. A successful conversion is **not** proof of successful gameplay.

## Not supported

General tools/armor, custom item/block subclasses, entities, TileEntity/BlockEntity,
world generation, dimensions, packets, capabilities, custom GUIs/renderers/shaders,
mixins, coremods, access transformers, reflection, arbitrary event handlers,
general mod dependencies, loot tables, custom recipe serializers, animated
textures and custom audio remain outside the public profile.

Developer-only experiments for preserved logic, lifecycle, food, recipes and
networking are not additional player Convert/Apply compatibility.

This is not a world-save migrator, a general mod updater or a way to connect
Minecraft 1.16.5 clients to Minecraft 1.20.1 servers.

## Requirements

- **Minecraft 1.20.1**
- **Forge 47.4.23**
- **Java 17**
- Source reference: Minecraft 1.16.5 / Forge 36.2.42

Other Forge patches are not blanket-certified. Fabric and NeoForge are not
supported targets. The menu is client-only; the same public artifact contains
the runtime needed on a server for supported converted content, with fresh
current-artifact server qualification still pending.

## Installation and conversion

1. Use a separate test instance. Keep important worlds backed up.
2. Place **LegacyBridge.jar** in `mods`. Do not install the separate developer
   runtime or QA/probe JARs alongside it.
3. Put original legacy JARs directly in **`<gameDir>/legacybridge/input`**.
   Never put them in the target instance's `mods` folder: Forge may fail before
   the conversion menu appears.
4. Open **Convert mods**, refresh the list, inspect and select candidates.
   Leave the optional mapping field blank for included simple-content data.
5. Convert and inspect each result. Successful output stays in `staging`;
   reports are stored in `reports`.
6. Continue converting, or explicitly **Apply** approved results for the next
   launch. Continuing does not change the loaded mod set.
7. Close and relaunch through your launcher. **Apply and close — reopen through
   your launcher** waits for safe completion and closes the game; it does not
   perform an automatic restart.

Converted mods remain separate files and require LegacyBridge at runtime.
Installation cannot activate a new mod in the current session. Keep
`legacybridge/transactions`, including recovery journals and managed backups.
Do not force-close Minecraft during installation.

No hidden mapping download is performed. Advanced full mapping/API bundles
remain local development data and must not be redistributed. Fully offline
gameplay, arbitrary modpacks/launchers and existing-world compatibility are
not certified.

## Free and open source

LegacyBridge is free of charge and released under **GPL-3.0-only with a narrow
Minecraft/Forge linking exception**. There is no revenue share, royalty or
mandatory donation. Third-party licenses still apply.

Converting another author's mod does not grant permission to publish it or its
assets. Check that mod's license and applicable dependencies before redistribution;
LegacyBridge does not automatically relicense converted mods.

## Help improve compatibility

Contribute small original fixtures, exact source/target API comparisons, negative
tests, independently recorded gameplay checks, translations or documentation.
Report the exact source mod/version/hash and diagnostic, with a public source
link or an authorized reproduction. Please do not upload private JARs,
authentication tokens, complete mapping bundles or unredacted personal logs.

- [Source and issues](https://github.com/IanZuccheri/LegacyBridge)
- [Contribution guide](https://github.com/IanZuccheri/LegacyBridge/blob/main/CONTRIBUTING.md)
- [Exact supported profile](https://github.com/IanZuccheri/LegacyBridge/blob/main/docs/SIMPLE_CONTENT.md)
- [Project status and remaining work](https://github.com/IanZuccheri/LegacyBridge/blob/main/docs/PROJECT_STATUS.md)
- [License scope](https://github.com/IanZuccheri/LegacyBridge/blob/main/LICENSING.md)
