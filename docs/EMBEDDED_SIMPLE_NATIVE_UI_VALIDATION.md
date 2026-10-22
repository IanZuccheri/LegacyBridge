# Embedded simple-content: native UI acceptance

Date: 2026-09-07. Accepted cut: **LB-04 / DATA-SIMPLE-01-NATIVE_UI**.
Next cursor: **LB-06 / PUBLIC-CURRENT-E2E**, still PARTIAL. This is not acceptance
of all LB-04, all end-to-end negatives, arbitrary legacy logic or multiplayer.
Product remains experimental/Beta, not approved for publication.

The [versioned ledger](evidence/embedded-simple-native-ui-2026-09-07.json) records
the actual screenshots, logs, report schemas, hashes, observations and omissions.
No production code, mapping rule, adapter, profile or public JAR changed in this
continuation. The earlier [data implementation ledger](evidence/embedded-simple-content-2026-09-07.json)
is preserved, including its foreground failure; this new run closes that UI gap.

## Exact artifacts and environment

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Public LegacyBridge.jar | 1,208,820 | `3749da06606f36b7e9bcbb97e4d9558f91d0bfa1c60e5da533767fd722607df6` |
| Original authored Forge 1.16.5 fixture | 9,534 | `00dd689f53f9631ecf4a8e84da142eb21051e2ca33f1c05403e4060e15b10947` |
| Each staged / installed conversion | 6,979 | `541cc64b44b97d0e4e79a193dd2ce3a86a5feba6e15714d0ee24ad63751492bb` |

Source Forge 36.2.42; target Forge 47.4.23 / Minecraft 1.20.1 / Java 17.0.12.
Runtime identity stays `legacybridge_runtime` / `0.1.1-SNAPSHOT`.
Disposable game directory: `build/embedded-simple/client`. Initial mods directory
contained only the public JAR; originals were in `legacybridge/input`. No QA mod,
JDI probe, source classes or full mapping bundle was installed. Prepared ordinary
Forge libraries/assets were used; this run downloaded nothing. The activation
class-load trace found no development-cache sources or original fixture class.
This is not a claim that the physical developer cache was deleted or that the
network was disconnected. The earlier isolated conversion tripwire remains the
separate evidence for absence of operational cache/network dependencies.

## Actual native sequence

Native input/capture used `Invoke-P6Desktop.ps1`, which checks process ownership,
canonical gameDir, foreground and physical monitor bounds. Captures are real
1280x720 pixels, GUI scale 2, not renderer mocks. The user-requested QA workflow
was exercised through buttons and keyboard, never through a direct controller call.

1. Client PID 6256 reached the title with three loaded mods (platform plus runtime).
   The converter listed the genuine candidate, its modId/version/hash/size and an
   inert corrupted file as INVALID, disabled for selection. Search and Space-key
   selection were exercised.
2. With the optional mapping-data field **empty**, actual UI conversion showed
   CONVERTING, then CONVERTED/staging. Technical details exposed report paths and
   the finite-contract warning. Three conversions across two screen visits all
   produced identical report and JAR hashes. These are repeated fixture results,
   not three different compatible mods.
3. After continuing and a second conversion, title still showed three loaded mods
   and only LegacyBridge was installed. Closing/reopening the screen did not
   activate the fixture. In-memory job history reset; reports/staging survived.
4. Only the third result was approved in the installation screen. Native
   **Aplicar e fechar — abra novamente pelo launcher** confirmation committed one
   ADD transaction, then closed with exit code 0, with no world open. No automatic
   restart was claimed or attempted.
5. A separate ordinary launch (PID 32376) discovered four mods. Forge's native mod
   list displayed LegacyBridge Example 1.0.0 in DONE state. The generated bootstrap
   loaded from the exact transaction-installed JAR (class-load trace line 16389).
6. Created only a new disposable world, `LB Embedded UI r1`: Survival, Peaceful,
   superflat, seed 20260907, structures off. Vanilla chat commands set daytime,
   position, crafting table and nine fixture ingots. No block was granted before
   the crafting oracle. Eight filled crafting slots gave no output; the ninth
   produced one fixture block. Taking it consumed all nine ingots; native inventory
   data showed exactly one block. Real right-click placed it at `1,-60,1`, and
   inventory became empty. A conditional vanilla command confirmed the exact block
   ID. Screenshots show its actual turquoise texture/model, not missing-texture art.
7. `/reload` parsed recipes/datapacks without parser errors. Later grants of one
   item and block were explicitly for translation inspection, after the conservation
   oracle. Native tooltips showed **Lingote da Ponte / Bloco da Ponte** in pt_br,
   then **Bridge Ingot / Bridge Block** after selecting en_us in Minecraft's own
   language screen and reloading resources.
8. Saved and quit the world, observed all dimensions saved at 14:02:51.552, then
   used Quit Game. PID 32376 exited 0 at closeout. No owned client remained and no
   unrelated process was terminated. Source hash stayed unchanged.

Selected real captures (all 41 captures have hashes in the ledger):

- [Inventory and corrupted input](../build/embedded-simple/evidence/embedded-ui-r1-03-inventory-ready.png)
- [Conversion with blank data field](../build/embedded-simple/evidence/embedded-ui-r1-04-convert-blank-data.png)
- [Converted, still staging](../build/embedded-simple/evidence/embedded-ui-r1-05-converted.png)
- [Continue: only three loaded mods](../build/embedded-simple/evidence/embedded-ui-r1-09-continued-not-active.png)
- [Apply and close confirmation](../build/embedded-simple/evidence/embedded-ui-r1-13-apply-close-confirmation.png)
- [Forge discovers fixture on next launch](../build/embedded-simple/evidence/embedded-ui-r1-16-fixture-recognized.png)
- [Eight inputs, no output](../build/embedded-simple/evidence/embedded-ui-r1-25-eight-no-result.png)
- [Ninth input, actual crafting result](../build/embedded-simple/evidence/embedded-ui-r1-26-nine-recipe-result.png)
- [Inputs consumed, one block](../build/embedded-simple/evidence/embedded-ui-r1-27-inputs-consumed-one-block.png)
- [Crafted block placed](../build/embedded-simple/evidence/embedded-ui-r1-28-crafted-block-placed.png)
- [Portuguese item](../build/embedded-simple/evidence/embedded-ui-r1-30-pt-br-item.png) / [block](../build/embedded-simple/evidence/embedded-ui-r1-31-pt-br-block.png)
- [English item](../build/embedded-simple/evidence/embedded-ui-r1-39-en-us-item.png) / [block](../build/embedded-simple/evidence/embedded-ui-r1-40-en-us-block.png)

Screenshot 08 counts two jobs but its retained search filters the rows out; it
does **not** show two visible completed rows. Reports and staged hashes establish
those outcomes. Intermediate screenshots 02/10/17 and language-scroll captures
are transitions, not acceptance evidence by filename alone.

## Reports, installation and preservation

All three conversion reports are schema 2, `CONVERTED`, `SIMPLE_CONTENT_CONTRACT`,
`completeMappingEnvironment=false`. TransformationLog stays schema 1. The reports
record CheckClassAdapter dataflow and offline reference-type checks PASSED,
`bytecodeExecuted=false`, `modClassesLoaded=false`, **`jvmLinkageVerified=false`**.
The native run is separate runtime evidence, not a retroactive full-linkage claim.
All 12 original assets/data entries are byte-identical in the installed output;
the existing pickaxe-tag rewrite is the only extra data entry. Metadata/pack
rewrites and bootstrap replacement remain described by the existing log/rules.

Transaction `c0051adc-3a21-4469-a9ec-6f50d91fe1e8` has plan/checkpoints schema 2 and
COMMITTED, one approved entry, matching source/staging/installed hashes. Journal
durability remains file contents forced, directory fsync not guaranteed. Physical
power-loss and multi-file atomicity are not inferred from a clean commit.

Known limitation: persisted job receipts still say CONVERTED with null installed/
transaction fields after Apply/close. This run does not verify a durable
PENDING_RESTART receipt. Journal commit plus independent next-launch discovery
prove the bounded install oracle. Reopenable history and persistent job/install
linkage remain UI-PROMOTION work; neither receipts nor screenshots were edited to
fake another state.

Logs are archived separately under
`build/embedded-simple-ui-2026-09-07-run1/launch-1` and `launch-2`.
Launch 1 has one ERROR-level early-display timing warning (`glfwInit` took 1.49 s),
then progresses normally; launch 2 has zero ERROR lines. Forge library metadata,
union-resource scheme, vanilla goat sounds/shader sampler, newly generated Forge
config defaults and Netty IPv6-property warnings remain visible. No fixture texture,
model or recipe parser failure was observed. The earlier invalid simulation-distance
QA option did not recur. These logs are not described as universally warning-free.

## Remaining gates and continuation

This cut accepts the **positive finite native data/UI workflow plus the corrupted
inventory negative**, not the entire UI test matrix. LB-04 remains PARTIAL.
`PUBLIC-CURRENT-E2E` must consolidate same-artifact evidence and qualify the
remaining unsupported-contract, missing-dependency, duplicate-modId, cancellation,
write-failure and interruption/recovery cases. Multi-selection/queue saturation,
scale/resize and persistent job-state gaps remain explicit UI qualification work.
Long metadata tooltips may clip horizontally; this run did not fix or certify them.

The prior dedicated-server and cacheless conversion results may be reused only
for their exact same artifact hashes and tested dimensions. They were not rerun
here. Audible sound playback, network-isolated offline gameplay, authenticated
multiplayer and automatic restart are NOT_RUN/unavailable, not implied PASS.
No new real mods were acquired. No new conversion functionality was enabled.
Networking's normal CLIENT_PROTOCOL failure remains open in LB-12.

Serial root build/test after the evidence/contract edits passed in 25 seconds:
68 tasks, 23 executed and 45 UP-TO-DATE. All Java test tasks were UP-TO-DATE;
488 pure PowerShell cases executed freshly. Contract verification checked
23 prompts / 77 cuts and the LB-06 cursor. These numbers do not count supported
mods. Packaging checks passed and the public JAR hash stayed unchanged.
`build/embedded-simple-ui-2026-09-07-run1/build-1.log` and its SHA-256 are in the
ledger. A separate read-only audit rehashed all 79 recorded artifacts/captures,
checked the two-file final mods set, three receipts and committed checkpoint.
Build is a consistency/regression check, not gameplay evidence.
