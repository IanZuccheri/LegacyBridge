# Networking validation: artifact-bound native and static evidence

Date: 2026-09-07. Status: experimental developer-only profile, **finite target
two-client scenario passed (NET-QA-01)**. Scope: [network-snapshot-v1](NETWORK_SNAPSHOT_PROFILE.md).
No whole real corpus mod is claimed newly convertible.
comparison5 completed the native scenario with the final public/converted JARs.
comparison3 contains an unexplained handshake failure; comparison4 exposed an
incorrect diagnostic expectation after both clients received snapshots. All
consumed runs are retained and must not be resumed as fresh. See current results
before the historical 2026-09-06 sections; their pending/resume text is historical.

Separate latest evidence: [NET-QA-02 handshake slice](NETWORK_HANDSHAKE_VALIDATION.md).
Matched target control passed; client-absent negotiation failed twice without
native version decisions. Other negatives and source comparison remain pending.
This does not rewrite or supersede comparison5's own scoped receipt.

## NET-QA-01 passed: comparison5, 2026-09-07

The final ordinary-installation scenario completed in 150 seconds, with 57
hash-bound command receipts and real native loopback transport. Each role had
the same public LegacyBridge JAR, converted fixture and test-only probe (not a
second mandatory public LegacyBridge dependency). Runtime classpaths used the
locally prepared ordinary Forge installation, not Gradle caches/userdev. This is
not a public-only GUI test: no screenshots or manual GUI interaction are claimed.

Observed behaviors, independent from the earlier static conversion:

- A received sequence 1/value 101; B received 1/202. Main-thread IDs matched the
  fixture's actual application thread. Server fixture handler counters stayed zero.
- Duplicate/gap and five malformed width/range/sequence payloads left A's value
  unchanged and produced exact rejection counts. A legitimate sequence 2/value 0
  remained distinguishable from a disconnected session.
- One unknown ID 255 produced exactly the source-verified Netty ERROR -> Render
  ERROR -> Render WARN sequence. A's next valid 3/303 arrived; B stayed at 1/202.
- A disconnected, clearing visible state; B continued to 2/222. A logged in again
  in the same process, refused sequence 2 for the fresh session, then accepted
  sequence 1/value 404. A ended with four accepted/eight rejected snapshots.
- An actual raw wrong-direction C2S injection made native Forge disconnect only
  A before the S2C handler. B then received 4/444, with four accepted/zero rejected.
- Client A PID 19452, B PID 29672 and server PID 26536 all exited 0. The server
  logged all-dimensions saved. All nine installed JAR hashes and the original,
  converted, public and probe inputs remained unchanged; no owned Java remained.

Final nested diagnostic receipt schema is 2 (total errors 2, Netty 1, render 1,
vanilla fallback 1, native direction-disconnect 1). This counts log stages for
one injected unknown packet, not two packets or additional supported mods.

| Evidence under `build/network-snapshot-v1/` | SHA-256 |
| --- | --- |
| `comparison5/network-receipt.json` | `5c624edff112284190d02ea9d787895d58ff72fb997a2a6425465057a0cbaa67` |
| `comparison5/preparation-receipt.json` | `51cdfdf2367af2090bf95e983fe219a99a8b920beb6c2c013565dd5ed2eb3d27` |
| `comparison5-attempt1.log` | `09012416e0ce5aaa890d41743d1101819ff63bfe0e0be6a15c30ea32b54b92d0` |
| `comparison5/client-a/qa-stdout.log` | `2b1823bc33892ee4d7393f6a556550d2ff230439af989c5e0e47af97f2b263e4` |
| `comparison5/client-b/qa-stdout.log` | `1af58fbf4932361cca4d9b894d5a410512f5800ee4623ae028cd828eada11f0d` |
| `comparison5/server/qa-stdout.log` | `523a0ef3970d3cd5e94690c73f6a09792e62253fed352b19e257ca8c90e2a1bc` |
| `network-native-replay-build.log` | `304a9157d6c433711c6e361e803211158465bbf60ad75ab5d79ec00f7fea19fc` |

The pre-native replay-oracle root build passed in 34 seconds, 65 tasks (20 executed, 45
up-to-date). Diagnostic helper: 186 fresh cases/377 assertions. Installation/
options helper: 76 fresh cases/254 assertions. Existing Java test tasks were
UP-TO-DATE; do not describe the historical 929-test aggregate as a fresh run.
Packaging, pinned APIs and installed CLI checks passed; public bytes stayed
`8daae3478a1b59482c438b843311c4160b5bdc8e4721f8e6067a1a87b81bbf88`.

Current is NET-QA-02-HANDSHAKE's client-absent investigation, then remaining
negative cases and NET-QA-02-COMPARISON. NET-QA-03 queue/disconnect, saturation/flood/session ownership and
separate WAN/soak, offline-game and client visual/UI gates remain open. A passing
comparison5 does not identify or prove a fix for comparison3's handshake failure.
No capability profile, C2S gameplay, profile composition or whole real-mod port
was enabled. The frozen corpus version retains its historical stage counts;
this new fixture evidence lives in the separate dated ledger.

Receipt erratum: comparison5's `identityArgumentsPersisted=false` is broader
than what the harness proved. Safe argument files contain no identity flags;
captured console launcher-argument lines are redacted, but native Forge debug
logs retain synthetic player identity. Their token field was masked. No real
account was read, no real authentication credential supplied, and no real-secret
exposure is claimed. The original receipt/hash is retained unchanged with this
erratum in the dated ledger. Hash-verified copies of its executed orchestrator
and both helpers are retained as `comparison5/executed-*.ps1`.

Future success receipts use outer schema 2 and five precisely scoped identity
booleans instead of the ambiguous field. This metadata-only correction is
covered by pure tests; it did not change the game/probe/converted/public bytes,
transport or oracle, and does not imply another native run was performed.
Final root verification after that correction passed in 27 seconds (65 tasks,
20 executed/45 up-to-date), with 186 diagnostic cases/377 assertions and 81
installation/options/identity cases/330 assertions freshly executed. Java test
tasks remained UP-TO-DATE. Log `network-continuation-final-build-2026-09-07.log`,
SHA-256 `daf8a7341694c6c052f3f4b94cb81127e75f2296c597b9457fceebf0fe611b31`.

## 2026-09-07 continuation: pinned native diagnostic replay

`comparison4` reached both real player connections and distinct native snapshots
A=101/B=202. Duplicate/gap and five malformed payload checks advanced with the
expected unchanged state/counters. It aborted at the unknown-ID injection after
28 successful control receipts, before reconnection/wrong-direction checks.
The failure receipt reports no remaining owned processes and no runtime pass.
No native client option-parser error for simulation distance remains.

Source review proved the oracle defect, not a converter defect. For unknown ID
255, `IndexedMessageCodec` logs and returns without marking the packet handled.
`ClientPacketListener` calls this hook before the thread check, so `PacketUtils`
dispatches the same packet on the render thread, where the hook sees a fresh
buffer copy and logs again. Vanilla then logs the unknown custom identifier.
The native stdout lines 74-76 exactly match Netty ERROR -> Render ERROR ->
Render WARN. The revised oracle requires one of each, in order, with exact
thread/logger/severity/role/window/channel, and rejects duplicates of a stage.
It does not tolerate an arbitrary second error or discard unknown packets silently.

Retained evidence under `build/network-snapshot-v1/`:

| File | SHA-256 |
| --- | --- |
| `comparison4-attempt1.log` | `26f6b99d0da9c1df7d01770a13901aae1135a30f7e5181cc075963a8314137b2` |
| `comparison4/network-failure.json` | `33608c39c87f30035329119cbbdd8eeb7c02973e58d123c9b4580beeb1a9e03d` |
| `comparison4/preparation-receipt.json` | `e826c9435d92d32be6fde3b350b719d296e5c85319e5c413f0dd4b56469a8045` |
| `comparison4/client-a/qa-stdout.log` | `9852ffe735bab9430f0c8f24e20246953c29cabe3e8d15fe0d506bc088646c1d` |
| `comparison4/client-b/qa-stdout.log` | `746ad2e883b9040b4d7b79a711540056d448f5a6a346571a397bf9cd4e979351` |
| `comparison4/server/qa-stdout.log` | `a2d6ad521b59db7d329445f45c23545f16bfc89f56c79a22609d2bab23f83ba5` |

The preceding root build passed in 2m55s, 65 tasks (20 executed/45 up-to-date),
fresh pure QA suites 90 diagnostic cases/273 assertions and 76 installation
cases/254 assertions; Java tests were UP-TO-DATE. Log
`network-options-build-2026-09-07.log`, SHA-256
`59d207eb1ad6b364ddf87bcec5a12a6d27e55d9916de75663f4cf2d1003c1098`.
That build predates the replay-oracle correction; it is not its validation.

## 2026-09-07 continuation: first actual connection attempt

The RAM gate was available (12.87 GiB measured before invocation), and retained
`comparison3` was resumed without overwriting its installation or earlier
preflight logs. The native server PID 20360 and clients A/B 4272/24064 started.
All three reached READY. A logged in and its EXPECT receipt confirmed a native
loopback connection, no integrated server and zero fixture counters. B initiated
CONNECT but did not finish the native login handshake within the 90-second
observer deadline. Its failure snapshot reports `logins=0`, `connected=false`
and all fixture counters zero. No fixture snapshot packet was sent; only five
successful control receipts preceded failure. Cleanup left no owned processes.

Server DEBUG logs show the 22 native FML login payloads sent for B without
acknowledgements. B's peer disconnected approximately 30 seconds after channel
initialization. The pinned native `Connection` installs a 30-second read timeout;
the timing is consistent with it, but the exact underlying cause was not proved.
The unchanged `ClientTickEvent.END` route is not demonstrated faulty, and no
tick-phase workaround or converter change was justified. A read-only attach
diagnostic attempted during cleanup was denied and produced no thread dump.

Retained evidence (all under `build/network-snapshot-v1/`):

| File | SHA-256 |
| --- | --- |
| `comparison3-attempt3.log` | `df06a837fce64b92b53e7aaebfe90df44ecbf769d8b3e11dd1e53bbeb22bdd14` |
| `comparison3/network-failure.json` | `fc2919d0e3d6799fc13dc25d03fcac948ffcde39ce4fccf46eea5199bbd699cb` |
| `comparison3/control/b/receipt-0001.json` | `e299e471afe157bac11346915e03a340dc4fb30365b95cc77f2fcc69d4338219` |
| `comparison3/client-a/qa-stdout.log` | `b743c4c0cc1ce2f0e5f9f0ac1f2443788a70ce6986e04f5fae9295157570d2e3` |
| `comparison3/client-b/qa-stdout.log` | `2b255d387d61a92120b82a99f278bf405feabaec5263cf9c97b660d917e6a095` |
| `comparison3/server/qa-stdout.log` | `4f051ab1a158c5589ac96003252347d8f5c0a1931d81259484ed549843048aab` |

An independent confirmed QA defect was corrected: `Options.java` requires client
simulation distance >=5, whereas the prior runner used 2 (a server setting with
a different valid range). Native logs reported invalid 2 and defaulted instead.
`Get-NetworkClientOptions` now uses 5, with five new pure contract tests; the full
installation/options suite passed 76 cases/254 assertions. Source archive hash
remains `4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932`.
This correction is not a claim to have fixed the unproven handshake cause.

The test-only observer now includes native screen/overlay names, client ticks and
connection-request state in command/deadline snapshots without manipulating
connections or changing deadlines. Its separate build passed in 40 seconds,
six inert tests; log `network-probe-observation-build.log`, SHA-256
`96cc9b3e8c08b9c19c72ebaf5141176d918063ec2509ab1114e98cbb8f0a5d29`.
New probe SHA-256:
`70ba8f078fb79f815118407fcb4bfc597401dfb2fae1f84a4154cf33ff810f04`.
Public runtime, original and converted JARs are unchanged. comparison3 is now
consumed native evidence and must never be reused as a fresh run. All 2026-09-06
sections below are historical, including their then-current resume instructions.

## Evidence already obtained

- Original Forge 36.2.42 fixture: final resource build passed, five inert tests;
  `build/network-snapshot-v1/source-fixture-final-build.log`. Its SHA-256 is
  `a5843c3dc1a0364a56220cceb9b1d3de8b0511d4941b2463e5a75ce5f838a33e`.
- Target Forge 47.4.23 native probe: build passed, six inert tests;
  `build/network-snapshot-v1/target-probe-build.log`. Its SHA-256 is
  `41128f79b22a218f8889d5e44371009fe1a78cc9a31e16a8db576a46d33c6082`.
  Neither test set executes original fixture classes or proves packet delivery.
- Engine compilation passed. Initial 22 networking callback/linkage tests and
  four recipe boundary tests passed. Integration failures are retained in
  `network-initial-tests.log` and `network-tests3.log`; these partial counts are
  not substituted for a final full build.
- `network-tests5.log`: 69 selected tests passed (67 new network tests and two
  existing checks). Genuine CLI/service conversions and private planning agree;
  both source/target offline linkage pass, 18 original executable bodies survive,
  altered guards/dependencies abort and accepted cancellation publishes nothing.
- `network-full-build1.log`: root build passed in 6m39s; 917 discovered tests,
  915 passed, two existing Windows symlink skips. Converter 689, corpus 84,
  product 82, runtime 30, workflow 32. This build includes the first 18 raw
  allocation tests; this intermediate build predates annotation-depth hardening.
- `network-full-build2.log`: final code root build passed in 10m46s; 924 tests,
  922 passed, the same two skips. Converter 696 (including all 92 network tests),
  corpus 84, product 82, runtime 30, workflow 32. Public artifact audit passed:
  one runtime identity, no duplicate platform/logging classes or private mapping data.
- Final conversion: `build/network-snapshot-v1/conversion.json`, schema 2,
  108 committed logged changes, two owned classes and all 18 original methods
  preserved, zero replaced classes, source/target offline linkage and ASM PASSED.
  Report SHA-256 `529f972bbeb23f6f3ab56921f53dc098c389b15021e6b636b30b13a294ee66ae`.
  Converted JAR: 5,531 bytes, SHA-256
  `18b00406492e6fb7ccba4ae799bda0165446b66fd538444619c1eeed69a3a91f`.
  Static conversion truthfully retains runtimeValidation=NOT_RUN.
- Cacheless CLI/service check passed in an isolated temporary installation with
  empty user/Gradle/temp homes, explicit copied bundle, Java 17 and 512 MiB heap.
  Three canonical report pairs and converted bytes agree, original unchanged;
  zero forbidden-file, network, subprocess or exit attempts. Evidence:
  `cacheless-reports/cacheless-engine-evidence.json`, SHA-256
  `2d3547efa6bdd962ab3296b61be94463c1557ebfd5dac14c99121556cdbd3c7b`.
  The test-only deprecated SecurityManager tripwire is not a production sandbox;
  this is offline engine validation, not an offline game test.
- Six final installed-CLI regressions passed, preserving all original inputs
  and exact converted JAR bytes: simple, logic, lifecycle explicit/inferred,
  food and datapack. Receipt:
  `regressions/comparison1/regression-receipt.json`, SHA-256
  `f75092ce381719724b72d7526619a66148604bb69a23832c9e6658aa83948f6d`.
- Final public `legacy-product/build/libs/LegacyBridge.jar`: 1,190,628 bytes,
  SHA-256 `8daae3478a1b59482c438b843311c4160b5bdc8e4721f8e6067a1a87b81bbf88`.
  Adapter ABI remains legacybridge_runtime 0.1.1-SNAPSHOT; these are new named
  offline operations, not fabricated old Forge classes or a second public JAR.
- Explicit local mapping bundle preparation passed without downloads:
  `build/network-snapshot-v1/mapping-bundle/manifest.json`, SHA-256
  `9aaea9e90a95afe3a8ba1c359230841c8badaa833106d37e21f9de3bd4b7bcf4`.
  The mixed-origin bundle is local-only, not approved for redistribution.

## Failures found and corrected/pending

The first source build ran before `pack.mcmeta` was added. The second serial
fixture build includes the source format-6 pack metadata and is the final
fixture hash above. The initial engine integration was deliberately blocked by
an empty reviewed-recipe set and absent explicit data profile. Approval now
records independently reviewed original instruction recipes; it does not learn
from input JARs at conversion time.

`conversion-attempt1.json` correctly aborts without a JAR because the original
offline gate did not admit the package-private opaque return of public
`SimpleChannel.registerMessage`. Both pins declare that type non-public; the
fixture immediately discards the return with POP. The narrow access recipe now
passes positive/negative linkage tests; no public API flags are fabricated. One Gradle invocation also placed
the task-specific `--tests` option after installDist; it failed before running
tests and was corrected in the following invocation. `network-tests4.log` also
retains a test-only name collision between a namespace constant and ASM's NEW
opcode, corrected before `network-tests5.log` passed.

Review found that malformed Code maxima could request oversized frame
allocations before recipe admission. The new raw cursor now runs before any ASM
reader, bounds containers/maxima and refuses noncanonical modified UTF-8 that
could disguise the Code attribute. Further annotation depth/count limits run
before ASM recursion; this is scoped network parser hardening, not a universal
heap sandbox. A specific nesting StackOverflowError becomes a structured refusal;
there is no generic Error/OOM catch or silent exception suppression.

## Native preflight, not a game failure

The first invocation of `Test-NetworkRuntime.ps1 -RunLabel comparison1` exited 1
before any game started: `At least 6 GiB currently free RAM is required for the
bounded three-process run.` A subsequent read-only Windows measurement was
5.66 GiB free. All three mods directories remained empty, with zero native logs,
zero command receipts and no project Java process. Preparation (7,455 hashed
ordinary-installation files) remains intact. No process belonging to the user
was terminated and the two-client 6 GiB gate was not lowered.

This is an execution-resource block, not evidence that networking failed in the
game. The separately labelled server-only check below validates discovery and
startup at its own 3 GiB gate, without publishing a packet-delivery pass.

## Actual ordinary dedicated-server execution

`Test-NetworkRuntime.ps1 -RunLabel comparison1 -ServerOnly` passed in 156 seconds
on the final public/converted/probe hashes above. The mode was added only to the
QA runner; it does not alter converter rules, the probe or the public artifact.
`network-final-build3.log` passed before execution in 1m06s, retaining the same
924-test state (tests up-to-date) and public hash.

- Forge discovery: `comparison1/server/logs/debug.log` lines 70, 72, 74 identify
  legacybridge_network 1.0.0, the test probe and legacybridge_runtime 0.1.1-SNAPSHOT.
  Lines 339–347 show actual mod container creation/subscriber processing.
- `comparison1/server/qa-stdout.log:17` confirms runtime loading; line 125 is
  `Done (45.758s)`. The isolated world was saved and the process exited 0.
- Native receipts: READY with the discovered fixture version, EXPECT_PEERS and
  STOP. Server thread ID 45 observed zero players, sequence/value/accepted/rejected
  all zero. STOP receipt was durable before shutdown. These getters are a runtime
  observation of the authorized converted fixture, not analysis-time execution.
- No client started and no snapshot packet was sent. Empty client mod directories
  remain untouched. Original and installed artifacts were rehashed unchanged;
  no owned test Java process remained after completion. Initial user/Gradle homes
  were empty and the ordinary Forge classpath did not use the development cache.
- Native warnings are retained: fresh Forge defaults, library mods.toml notices,
  union resource URLs, noninteractive terminal and explicitly offline-mode server.
  No runtime ERROR was found in captured stdout; stderr was empty. This is not an
  authenticated connection test or a claim that the game was run without internet.

The receipt `comparison1/network-server-receipt.json` has status
`SINGLE_SERVER_ONLY`, and explicitly marks packetDelivery, behavior,
nativeTwoClientS2c and nativeMainThreadApplication NOT_RUN. Its SHA-256 is
`405ba30afcc295a7a4b972fc0ae937f0f9b15ebc369c6de33b0dbce9f4867b72`.
Server stdout SHA-256:
`deaf60fcbe888286f67b4c04c3d16877145e2457be307358ef9a8567bb043f93`.
Server discovery debug log SHA-256:
`761a6802a8267c8b2cf051320df873649f0b0e122ef0d79e3f06c9a9a3a12192`.

## Two-client attempt: preparation defect retained

The fresh `comparison2` run passed the unchanged 6 GiB preflight and launched
the dedicated server (PID 26976) and native clients A/B (34788/31708). Both
clients exited before READY: `MinecraftLocator` could not find `fmlcore` in
their isolated `libraries` directory. One server READY receipt was completed;
no packet-delivery pass was produced. `network-failure.json` records no remaining
owned processes and `runtimePassClaimed=false`, SHA-256
`65100106fc395acb907cfd8f48d74eedf280ef892f2bf4ab0c40b33a1991e63c`.
Client A/B stderr hashes are respectively
`5beac79879fb3ed1aacaa1fb4edc4c57998ac40c6962dd47aba5ee43a836ea0a`
and `c486787ee385bfb2994d4688c1a3309f492187d06c1eddc68fb7a0588063afc8`.
The failed run and prior comparison1 remain untouched.

This was a QA preparation defect, not evidence of a codec/converter failure.
The copier had omitted all five FML-discovered libraries that are absent from
`version.json`'s bootstrap list. The original P6 preparer already handled that
distinction. Reviewed `CommonClientLaunchHandler` and `ForgeClientLaunchHandler`
sources confirm the contract; fmlloader 47.4.23 sources SHA-256:
`7917b9e169ed8d1f34299ebd6c8136f310a176754b52b1e492b86638aa059667`.
The corrected preparation reads their canonical paths, sizes and SHA-1 values
from the pinned local official installer. Runtime independently verifies every
required discovery artifact before any actor starts. No implicit download,
bootstrap-classpath workaround, product change or conversion-rule change was made.

The exact native log oracle was also hardened: ID, channel, player, process and
injection window must match, with one observation each. Consumed asynchronous
log tasks advance before a rejecting check, so cleanup cannot reread the same
rejected line. Pure diagnostic tests passed (90 cases/273 assertions), as did
the new installation-inventory tests (71 cases/152 assertions). These numbers
describe QA-helper checks, not newly supported mods or native packet tests.
The earlier `network-oracle-build1.log` root build passed in 23 seconds, with
Java tests up-to-date and the diagnostic tests freshly executed. Its SHA-256:
`9510f42c1293df7055073d3c80eb63bff71ce4095ed65f647d012f3e3db7be61`.

The subsequent full root build `network-installation-build2.log` passed in 1m01s:
65 tasks, 20 executed, 45 up-to-date; both QA-helper suites executed and passed.
Java test tasks were up-to-date (the existing aggregate remains 929 tests,
927 passed and two platform skips, not 929 fresh executions). Packaging and
the exact Forge API baseline audit passed. Build log SHA-256:
`85d765772c6d9420ebd526b5da5c6090c350078855b78ee950281929dc5de5f3`.

`comparison3` preparation finished with 7,465 hashed installation files, including
the ten previously missing client copies. Receipt SHA-256:
`e2e3e0f9e690d7dd2de83ee6ca017d624812c3bfca295de9dcf1cc7e16217a44`.
The next runtime invocation passed preparation-inventory hash checks but refused to start at
the 6 GiB RAM gate: required 6,291,456 KiB, observed 4,496,180 KiB. Log
`comparison3-attempt1.log` SHA-256:
`6b939ccfbf1402c4cbf08ca70a400e8059f2a60f9bd6e5b47625957077054503`.
All three mods directories and the control directory remain empty; no native
logs or process were created for comparison3. The separate native discovery-file
check in the runner is after the RAM gate, so it was not reached in that attempt;
do not confuse its implemented presence with execution. It remains usable for a later
runtime invocation without re-preparing or overwriting anything. The unchanged
original, converted and public hashes above were rechecked. No user process was
terminated or launcher/profile modified to free resources.

## Requested finalization: no background retry

The final comparison3 invocation on 2026-09-06 also stopped at the unchanged RAM
gate: 5,850,796 KiB free, 6,291,456 KiB required. Retained
`comparison3-attempt2.log` SHA-256:
`6c8fdfb9eb9ae0d543f1c147b19c460a6910b82a4078368615ebf48255562332`.
No game launched, and mods/control directories remain empty with no native logs.
This is a finalization with an explicit external resource gate, not an indefinite
monitoring task or a fabricated native pass. Nothing will automatically launch
after handoff.

A separate read-only check of all ten prepared discovery-library copies passed
official SHA-1 and size checks in this finalization. The runner's post-RAM native
discovery-file preflight was not reached; discovery and packet execution remain
separate from that file check. The original, converted, probe and public artifact
hashes were rechecked unchanged. Historical failed/server-only evidence is
preserved, no user process was terminated and no new compatibility profile was
enabled. The root verification log for this handoff is
`build/network-snapshot-v1/network-requested-closeout-build.log`.

## Gates still pending in this record

Two real clients with the dedicated server; selected
recipient isolation, reconnect, malformed/unknown IDs, duplicate/gap/out-of-order
and wrong-direction tests. No client or server run is claimed by the build logs.

Protocol-mismatch/absent-peer native runs, original-version native comparison,
queue exhaustion/flooding, explicit pending-work disconnect race and generalized
session behavior remain separate gates. No cross-version networking or
server-mutating packet support is promised.

To resume this prepared run, free at least 6 GiB (preferably with additional
headroom), then run without a concurrent owned build/game operation:

```powershell
.\scripts\Test-NetworkRuntime.ps1 -RunLabel comparison3
```

Do not reuse comparison1 or comparison2: their native worlds/logs/receipts are
evidence. Do not prepare over comparison3, which stopped before game startup.
No new conversion category should be enabled to bypass the pending runtime gate.
