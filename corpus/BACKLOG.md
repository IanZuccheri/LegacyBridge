# Evidence-prioritized backlog — 2026.09.06.1

No functionality below was enabled in this task. The currently verified number
of real whole-mod conversions is **zero**. Priorities are provisional for three
authorized exact releases, not a popularity ranking or an ecosystem estimate.
See [exact-source review](intake/semantic-review.md) for concrete call sites and
[records](catalog.json) for actual converter refusals.

Ranking first considers distinct observed projects affected, then prerequisites,
semantic loss risk and estimated effort. An affected project is **not** an
unlocked project. Counts are deduplicated by project and exact source hash.
Estimates are one experienced engineer's person-days including negative fixtures
and relevant target execution checks, not promises. A class/symbol/test count is
never substituted for these quantities.

## The next three selected slices

| Priority / ID | Bounded deliverable | Exact cohort affected | Whole mods unlocked by this task alone | Prerequisites | Semantic risk | Estimate |
| --- | --- | --- | --- | --- | --- | --- |
| 1 / B01 | Audited inert metadata envelope plus explicit physical-side/source-role decisions | Clumps, Controlling, AppleSkin: 3 first-refusal cases | 0; deeper behavior still blocked | Corpus identity/rights; individual field semantics and pinned Forge discovery audit | Low–medium; side mistakes break server loading/handshake | 3–5 days |
| 2 / B02 | Finite lifecycle/event delivery, beginning with completely accounted-for owned handlers | All 3 have real registration/callback dependencies, but different payloads | 0 verified; no complete real release expected from lifecycle alone | B01; real 36.x/47.4.23 event APIs; implemented body translation and side isolation | Medium–high; duplicate callbacks, order, cancellation and wrong-thread behavior | 8–15 days for this finite slice |
| 3 / B03 | Exact Clumps 6.0.0.28 XP-denomination behavior closure, not generic entity support | Clumps only: 1 conditional candidate | 0 currently; at most this 1 release after every prerequisite and behavior gate passes | B01, B02 and the entity/state/spawn/render proof checkpoints below | High; XP loss/duplication, mending changes and multiplayer divergence | 30–50 days after feasibility, including the internal checkpoints |

B03 is selected as the smallest **inspected complete behavior cluster**, not
because its six classfiles make it simple or because every other mod is harder.
The estimate includes its specific internal checkpoints; do not add it to an
imaginary independent estimate for generic entities, networking or rendering.
If a required public target route has no equivalent, keep the release
MANUAL/UNSUPPORTED and reassess this selection.

### B01 acceptance and stop gates

- Audit each allowed manifest/dependency field against the actual loader; preserve
  notices, identity and originals. A timestamp is not permission to discard
  arbitrary manifest services, coremods, signatures or active metadata.
- Positive fixture reaches the unchanged semantic gate; negative active/unknown
  metadata still refuses without an output. Every actual rewrite has a verified
  rule, implemented operation and transformation-log entry.
- Side-declared positive fixture starts on a common Forge client and dedicated
  server without client-only resolution on the server; dependency and handshake
  policy must be explicit. Side admission is not an adapter for GUI code.
- AppleSkin's 1.16.4/35.1.4 source evidence and ambiguous analyzer profile stay
  visible. Do not relabel it Forge36-compiled or silently broaden the source
  profile merely to move the record past a refusal.
- All three real releases remain blocked until their full remaining dependencies
  are implemented. A later refusal is useful diagnostic progress, not compatibility.

### B02 acceptance and stop gates

- Prove mod bus versus Forge bus, common/client lifecycle isolation, exactly-once
  registration, callback ordering and game-thread ownership using a tiny owned
  fixture with a finite, fully translated handler body.
- Refuse unknown event payloads, unsupported handler branches and inaccessible
  state. Do not retain unexplained legacy bytecode or implement a generic empty
  callback adapter. Cancellation and priority require separate targeted evidence.
- Static rule/adapter coverage, CheckClassAdapter and target references must pass;
  then run the actual converted fixture on both physical sides. Wrong-side and
  repeated-registration fixtures must fail without a partial JAR.
- This does not implement entity-join/world ticks, GUI-open events or every
  AppleSkin HUD/tooltip/living/login callback simply because all are events.
  Record those downstream gaps separately until verified.

### B03 acceptance and internal dependencies

This is an end-to-end closure with internal proof gates, not scaffolding that
declares Clumps supported after registering an entity:

1. Feasibility against exact 1.16.5 and 1.20.1 APIs: finite entity registration,
   state access and reviewed replacements for reflection. No target-game class
   mutation and no placeholder getters. Stop if equivalence is unavailable.
2. Preserve denomination-to-count XP state, merge/pickup, cancelled pickup,
   per-orb mending distribution, movement and age/despawn semantics. A single
   total-XP integer does not preserve this release's behavior.
3. Preserve `clumpedMap` NBT and save/reload boundaries, plus exact spawn-state
   synchronization. Unknown/malformed/overflowing state is a negative fixture.
   No claimed migration of unrelated original worlds.
4. Implement the actual renderer behavior and physical-side isolation, not an
   empty renderer. Validate assets, lighting/blend state and scene interaction.
5. Decide explicitly how the release's custom clone/merge event API is handled.
   Its posting sites ignore exposed replacement setters; silently honoring them
   would change the observed source semantics. External listener compatibility
   needs additional authorized paired fixtures; do not promise it by default.
6. Actual converted release: client/server discovery, gameplay pickup/merge/XP
   conservation, mending, save/reload, reconnect and two-player synchronization
   under controlled conditions. Validate negative interruption/duplication cases.
   Only this gate can change this exact Clumps record's runtime statuses.

All behavior observations must be hash-bound and compared to an explicit oracle.
Original-mod execution for reference behavior, if needed later, is a **separate
authorized runtime test**, never part of analysis or conversion. No such original
execution was performed while assembling this corpus.

## Remaining prioritized clusters

| ID / order | Cluster | Observed affected exact projects | Dependencies | Risk / estimated effort | Unlock boundary |
| --- | --- | --- | --- | --- | --- |
| B04 / 4 | Controlling owned keybinding-screen route: replace its three legacy access-transformer needs using adequate public APIs, input/modifiers, persistence, focus and custom events | Controlling: 1 | B01–B02; target-access feasibility before implementation | High / 20–35 days after feasibility | At most this exact release after all UI/coexistence gates; otherwise UNSUPPORTED |
| B05 / 5 | AppleSkin exact client config, nine booleans/one double plus explicit file loading/autosave semantics | AppleSkin: 1, not all three | B01–B02; explicit adjacent-source decision | Medium / 5–10 days | 0 whole mods alone; still HUD, packets, state/reflection and rendering |
| B06 / 6 | AppleSkin HUD/tooltip/state and two server-to-client sync messages, including optional-peer policy | AppleSkin: 1 | B01–B02, B05; adjacent source policy, payload/order/priority and public state access | High / 30–50 days after prerequisite review | At most this release after client/server/two-player behavior tests; not all HUD mods |
| B07 / research | Acquire the next small, authorized, materially different exact-release cohort | No new releases counted yet | Rights/hash/source review before download; keep binaries local | Low technical / 2–4 days per reviewed small batch; permission delays unestimated | Improves prioritization evidence, unlocks 0 by itself |

Only B01–B03 are the selected next implementation slices. B04–B07 remain backlog;
none authorizes feature enablement now. Dependencies, estimates and ordering must
be revised when a new corpus version supplies contradictory evidence. Config is
not artificially prioritized as a shared-three-mod dependency: it was found only
in AppleSkin's inspected source. Mixins, coremods, capabilities, world generation,
dimensions, arbitrary networking/rendering and other unobserved families remain
unsupported, with **unknown reach and unestimated effort** in this cohort.

## Definition of a measurable unlock

An exact real artifact moves from BLOCKED/FAILED to a completed conversion with
output validation under the same reviewed workload. Report this static change
separately from initialization, scoped behavior and multiplayer. Announcing a
supported playable port additionally requires the specified real runtime gates.
All task impacts above are conditional forecasts until those observations exist.
Do not count a metadata workaround, synthetic test method, resolved symbol or
manually edited substitute input as an unlocked original mod.
