# Original horizontal-facing block fixture

Project-authored Forge **1.16.5 / 36.2.42**, version 1.0.0, Java 8 bytecode.
The two owned classes register `legacybridge_horizontal_block:bridge_block`
and its late-resolved native BlockItem. The block directly subclasses native
Block, uses STONE with strength 2/6, and has one horizontal FACING property.
Its default is NORTH; placement faces opposite the placing player's horizontal
direction. The property is registered through the native protected state-builder
override. The entry registers blocks before items on the same mod event bus,
using named static suppliers; the BlockItem stack limit is 32.

One shapeless iron-ingot recipe, all four facing blockstate variants, one
cube_all model referencing vanilla stone, its item model, and en_us/pt_br
translations are authored here. The isotropic stone texture does not visually
distinguish facing: future native QA must inspect block state, not infer facing
from appearance. No Minecraft assets or code are bundled. No loot table is
invented, so this fixture makes no self-drop claim.

The exact 1.16.5 source names are StateContainer.Builder and BlockItemUseContext.
The default-state/placement pattern was checked against the locally prepared
Forge 36.2.42 source archive's Block, HorizontalBlock and CarvedPumpkinBlock.
The latter's unrelated pumpkin behaviors are not copied or supported.
There are no rotation/mirror overrides, shapes, callbacks, ticking, block
entities, creative membership, capabilities, networking, GUIs or worldgen.

Build serially from the repository root with Java 17:
`gradlew.bat -p fixtures/horizontal-block-1.16.5 build --offline --max-workers=1`.
The four standalone archive tests inspect metadata, class headers and bounded
resources; they never load, reference or execute an original fixture class.

This is a private fail-closed preservation candidate, not a public profile.
Build, linkage and structural comparison do not establish runtime behavior.
Original/converted placement, persistence, client/server and multiplayer
comparison remain NOT_RUN until independently evidenced.
