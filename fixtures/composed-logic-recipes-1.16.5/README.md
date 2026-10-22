# COMPOSE-01 authored fixture (original only)

Compile with the pinned Forge 36.2.42 / Minecraft 1.16.5 APIs, Java 8 classfiles
using the repository's Java 17 Gradle toolchain. No game sources, textures or
private mod binaries are included; the model references a vanilla texture.

One native DeferredRegister item has an Item subclass, interface dispatch and a
stateful owned helper. One native smelting recipe uses a local nonempty item tag.
This deliberately requires both existing logic and datapack-recipe contracts.

Behavior oracle for future isolated native comparison, **NOT RUN**:

- Unenchanted counts 1, 2, 3, 4: foil true, false, true, false; one helper call each.
- Enchanted stack: native super predicate true, helper call count unchanged.
- One iron ingot cooks into one count_token in 40 recipe ticks, XP zero.
- Stack limit 16, original ID/name/localization/model and bytes retained when applicable.

The original-only project remains unchanged. A separate controlled developer
export now retains the statically proved candidate for later isolated QA; see
[the export contract](../../docs/COMPOSITION_QA_EXPORT.md). Gameplay, client/server,
reload and multiplayer remain NOT_RUN. Export is not public support or installation
approval. Do not install this source JAR in 1.20.1.
