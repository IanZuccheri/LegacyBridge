# Exact-release semantic dependency review

Reviewed 2026-09-06. This is a **source-review hypothesis for backlog planning**,
separate from the converter's actual first refusal and separate from runtime
evidence. No rule, adapter or acceptance condition was enabled by this review.
The source commits, permission basis and exact binary hashes are pinned in
[rights-candidates.json](rights-candidates.json). Source-to-binary reproducibility
is not established. The bounded review fetched official public Java source as
text, never loaded the original JARs or executed their classes.

## Actual converter first gates versus further reviewed dependencies

The current local `build/corpus-intake/observations/<sample>/conversion.json`
reports all three attempts as `ABORTED`, without output artifacts:

| Exact release | Actual first diagnostic | Further dependencies visible in its source, not later converter results |
| --- | --- | --- |
| Clumps 6.0.0.28 | `Unapproved manifest attribute: Implementation-Timestamp` | Custom XP entity, entity registry, lifecycle/world events, reflection, spawn data, persistence, custom renderer, pickup/mending semantics |
| Controlling 7.0.0.29 | `Side-specific dependencies require manual review.` | Client discovery/handshake policy, screen replacement, keybinding mutation, three access-transformer declarations, custom UI events |
| AppleSkin 2.5.1+mc1.16.4 | `Side-specific dependencies require manual review.` | Adjacent source baseline, client config and explicit file I/O, lifecycle/HUD/tooltip/player events, two synchronization messages, reflection, render-state management |

Removing an initial metadata refusal would not make any of these whole mods
convertible. These observations give **zero verified whole-mod unlocks** to any
proposed new task until all its downstream dependencies and runtime gates pass.

## What is genuinely shared

All Java paths in the pinned trees were searched for the relevant config,
registration and event constructs: Clumps has 6 source files, Controlling 15,
AppleSkin 18. These are source-file counts, not supported-mod counts; their
compiled JARs contain 6, 26 and 30 class entries respectively due to inner classes.

| Concern | Clumps | Controlling | AppleSkin | Distinct exact releases observed |
| --- | --- | --- | --- | --- |
| Explicit mod-bus lifecycle listeners | Common and client setup | Client setup | Common and client setup | 3 |
| Forge event-bus callbacks | Entity-join and world-tick listeners | One screen-opening subscriber | Eight annotated subscribers | 3 |
| `ForgeConfigSpec` / config file implementation | None found | None found | One CLIENT spec: nine booleans, one ranged double | 1 |
| Custom entity implementation | XP orb subclass | None found | None found | 1 |
| Custom packet implementation | Entity spawn data | No application packet codec found; display-test extension exists | Two float-valued application messages | 2 |

The common lifecycle/event claim comes from actual registrations in
[Clumps](https://github.com/jaredlll08/Clumps/blob/34c57ca0bd068655a63d99eabd215219432c32d4/src/main/java/com/blamejared/clumps/Clumps.java#L31),
[ClumpsClient](https://github.com/jaredlll08/Clumps/blob/34c57ca0bd068655a63d99eabd215219432c32d4/src/main/java/com/blamejared/clumps/ClumpsClient.java),
[Controlling](https://github.com/jaredlll08/Controlling/blob/1975807fd663f62d5631f94b9dee19372a5a2d08/src/main/java/com/blamejared/controlling/Controlling.java#L20)
and [AppleSkin](https://github.com/squeek502/AppleSkin/blob/5bedbcc501848f3a22a650f1f661e57d6973af83/java/squeek/appleskin/AppleSkin.java#L22).
It does **not** imply that the different event payloads are interchangeable.

AppleSkin's actual config implementation uses `CommentedFileConfig.builder`,
`sync`, `autosave`, `WritingMode.REPLACE`, `load` and `SPEC.setConfig`, in addition
to `ModLoadingContext.registerConfig`. Consequently a nine-boolean/one-double
schema adapter alone would not account for that code's file behavior. Do not
attribute this config dependency to Clumps or Controlling merely because it is
common elsewhere in Forge. See [the exact ModConfig source](https://github.com/squeek502/AppleSkin/blob/5bedbcc501848f3a22a650f1f661e57d6973af83/java/squeek/appleskin/ModConfig.java).

AppleSkin's eight annotated subscriber methods are:

- `DebugInfoHandler.onTextRender(RenderGameOverlayEvent.Text)`.
- `HUDOverlayHandler.onPreRender(RenderGameOverlayEvent.Pre)` and
  `onRender(RenderGameOverlayEvent.Post)`, both priority LOW, plus
  `onClientTick(TickEvent.ClientTickEvent)`.
- `TooltipOverlayHandler.onItemTooltip(ItemTooltipEvent)` and
  `onRenderTooltip(RenderTooltipEvent.PostText)`.
- `SyncHandler.onLivingUpdateEvent(LivingUpdateEvent)` and
  `onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent)`.

See [debug handler](https://github.com/squeek502/AppleSkin/blob/5bedbcc501848f3a22a650f1f661e57d6973af83/java/squeek/appleskin/client/DebugInfoHandler.java),
[HUD handler](https://github.com/squeek502/AppleSkin/blob/5bedbcc501848f3a22a650f1f661e57d6973af83/java/squeek/appleskin/client/HUDOverlayHandler.java),
[tooltip handler](https://github.com/squeek502/AppleSkin/blob/5bedbcc501848f3a22a650f1f661e57d6973af83/java/squeek/appleskin/client/TooltipOverlayHandler.java)
and [sync handler](https://github.com/squeek502/AppleSkin/blob/5bedbcc501848f3a22a650f1f661e57d6973af83/java/squeek/appleskin/network/SyncHandler.java).

## Per-mod closure requirements

### Clumps: small class count does not mean simple semantics

The custom `EntityXPOrbBig` maintains a denomination-to-count map, merges orbs,
preserves motion, ages/despawns, handles fluid motion and player attraction,
applies mending per stored orb, and grants remaining XP. It serializes that map
to `clumpedMap` NBT and carries it plus XP in additional spawn data. An adapter
that keeps only total XP would lose relevant pickup/mending behavior. Review
[the exact entity source](https://github.com/jaredlll08/Clumps/blob/34c57ca0bd068655a63d99eabd215219432c32d4/src/main/java/com/blamejared/clumps/entities/EntityXPOrbBig.java).

`EXPCloneEvent` and `EXPMergeEvent` expose replacement setters, but the observed
posting sites keep using their local orb variables; honoring those setters in
an adapter without an explicit behavior decision would itself change this
release. Do not silently fix upstream quirks. A real port must also preserve the
cancelled-pickup branch and client/server entity lifecycle. The custom renderer
uses camera-facing vertices, light, blending and the XP texture; replacing it
with an empty renderer or pretending registration is enough is not acceptable.
See [clone event](https://github.com/jaredlll08/Clumps/blob/34c57ca0bd068655a63d99eabd215219432c32d4/src/main/java/com/blamejared/clumps/events/EXPCloneEvent.java),
[merge event](https://github.com/jaredlll08/Clumps/blob/34c57ca0bd068655a63d99eabd215219432c32d4/src/main/java/com/blamejared/clumps/events/EXPMergeEvent.java)
and [renderer](https://github.com/jaredlll08/Clumps/blob/34c57ca0bd068655a63d99eabd215219432c32d4/src/main/java/com/blamejared/clumps/client/render/RenderXPOrbBig.java).

### Controlling: native input semantics and access boundaries

The [opening handler](https://github.com/jaredlll08/Controlling/blob/1975807fd663f62d5631f94b9dee19372a5a2d08/src/main/java/com/blamejared/controlling/events/ClientEventHandler.java)
replaces `ControlsScreen` with a custom subclass. Its [access transformer](https://github.com/jaredlll08/Controlling/blob/1975807fd663f62d5631f94b9dee19372a5a2d08/src/main/resources/META-INF/accesstransformer.cfg)
exposes the keybinding list, parent screen and input registry. This is not a
license to modify target Minecraft classes. A future plan needs a semantically
adequate public API/owned-screen route or remains UNSUPPORTED.

The [controls screen](https://github.com/jaredlll08/Controlling/blob/1975807fd663f62d5631f94b9dee19372a5a2d08/src/main/java/com/blamejared/controlling/client/gui/GuiNewControls.java)
changes bindings and modifiers, resets the binding lookup, switches lists and
handles keyboard focus, mouse capture and filtering. The
[keybinding list](https://github.com/jaredlll08/Controlling/blob/1975807fd663f62d5631f94b9dee19372a5a2d08/src/main/java/com/blamejared/controlling/client/gui/GuiNewKeyBindingList.java)
posts four custom API events, some of which replace listeners or handle input.
Acceptance requires persistence/reload, modifier/conflict correctness, focus,
narration, GUI scaling, and coexistence tests, not merely a visible search box.

### AppleSkin: a HUD mod with server synchronization

Its [synchronization handler](https://github.com/squeek502/AppleSkin/blob/5bedbcc501848f3a22a650f1f661e57d6973af83/java/squeek/appleskin/network/SyncHandler.java)
registers two messages, tracks per-player saturation/exhaustion changes and
resets that tracking at login. The
[exhaustion message](https://github.com/squeek502/AppleSkin/blob/5bedbcc501848f3a22a650f1f661e57d6973af83/java/squeek/appleskin/network/MessageExhaustionSync.java)
and [saturation message](https://github.com/squeek502/AppleSkin/blob/5bedbcc501848f3a22a650f1f661e57d6973af83/java/squeek/appleskin/network/MessageSaturationSync.java)
each enqueue game-thread work. Optional-peer/version acceptance, direction,
disconnect/null-player boundaries, ordering and multiplayer isolation all need
proof; these messages cannot be replaced by fictitious client values.

[HungerHelper](https://github.com/squeek502/AppleSkin/blob/5bedbcc501848f3a22a650f1f661e57d6973af83/java/squeek/appleskin/helpers/HungerHelper.java)
reflectively resolves and reads/writes the legacy exhaustion field. The HUD also
uses Forge's hunger-bar offset, cancellation and LOW event priority. Tooltip
drawing changes depth/lighting/blend state. Even correct float codecs would not
establish rendering equivalence or behavioral compatibility.

## Recommended next three bounded investigations

Effort ranges below are engineering estimates for one experienced engineer,
including negative fixtures and target-client/server checks, not delivery
promises. They do not count unresolved-symbol reductions as supported mods.

| Order | Vertical / proof contract | Observed reach; whole-mod unlock estimate | Dependencies | Semantic risk | Initial effort |
| --- | --- | --- | --- | --- | --- |
| 1 | Metadata envelope and explicit side/source-role decisions: allow only individually audited inert metadata, retain originals/notices, report every rewrite, reject unknown active metadata; positive/negative fixtures must still reach the existing semantic gate | Three current first-refusal cases; **0 whole mods unlocked by this alone** | Corpus observations and actual source admission policy; AppleSkin adjacent baseline must remain explicit | Low to medium; side/identity errors can break discovery | 3–5 engineer-days |
| 2 | Finite lifecycle/event delivery slice: separate mod/Forge buses, side isolation, exactly-once delivery and order; begin with owned fixtures and fully accounted-for handler bodies, not arbitrary preserved legacy code | Lifecycle/event dependency appears in **3 exact mods**; **0 verified whole-mod unlocks** | 1, real target API audit, runtime invocation/bytecode boundary design | Medium to high; payload/cancellation/thread semantics vary | 8–15 engineer-days for the narrow slice, not all event families |
| 3 | Clumps denomination-preserving XP entity slice: one exact behavior cluster, save/reload, merge/pickup/mending, spawn synchronization and renderer, with explicit extension-event decisions | **1 conditional whole-mod candidate**, Clumps only; **0 currently verified** | 1–2 plus entity registration/state, serialization, networking, reflection replacement and client rendering work | High; duplication/loss of XP, state drift and multiplayer desynchronization | 30–50 engineer-days after prerequisite feasibility, revised after spike |

Clumps is chosen as the smallest inspected full behavioral closure, not because
six classes make it safe or because it is proven easier than every other mod.
Split its work into independently verified negative/positive fixtures, but keep
the actual Clumps JAR blocked until the complete closure passes. If the spike
finds no equivalent public target route, retain MANUAL/UNSUPPORTED and reprioritize.

Configuration persistence (observed in AppleSkin only), keybinding screens
(Controlling only) and the full HUD/tooltip stack (AppleSkin only) remain explicit
parallel backlog clusters, not claims that task 2 implements them. Acquisition
of additional authorized, materially different exact releases is needed before
generalizing these priorities to the wider ecosystem.
