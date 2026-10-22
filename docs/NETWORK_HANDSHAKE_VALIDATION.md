# NET-QA-02: native handshake slice

Date: 2026-09-07. This is a separate runtime QA slice, not a new conversion
profile or an enlargement of `simple-content-v1`. NET-QA-01's comparison5 receipt
remains immutable and does not automatically close NET-QA-02.

NET-QA-02 is tracked as two obligations: **NET-QA-02-HANDSHAKE** (target positive
control and both-direction missing/protocol negatives), and
**NET-QA-02-COMPARISON** (original 1.16.5 versus converted 1.20.1 native behavior).
The handshake slice is implemented first because it directly exercises the
current target fixture/validator without altering its code or preparing an
unaudited source client. Neither obligation substitutes for the other.

## Exact source evidence

The actual merged source archives were rehashed and read as ZIP data:

| Source archive | SHA-256 |
| --- | --- |
| Minecraft 1.16.5 / Forge 36.2.42 mapped official sources | `bbe2d148f96694271c43fc2ba584463d75d9ffc490abd0469ae66b831293d376` |
| Minecraft 1.20.1 / Forge 47.4.23 mapped official sources | `4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932` |

Source `NetworkRegistry` 47/227 passes the absent sentinel String to the same
version predicate; target 48/225 stores a ChannelData and passes its version
String. The actual sentinel is `ABSENT` followed by a space and U+1F914; the test
does not treat it as a made-up empty/version-0 value. The original fixture's
literal-1 predicates and the converted bytes are unchanged.

Source `FMLHandshakeHandler` 158/190 returns a boolean result and disconnects on
client/server channel validation failure. Target `HandshakeHandler` 180/237 uses
a mismatched-channel map, records connection/mismatch data and disconnects; the
server also replies with mismatch information. `ClientHandshakePacketListenerImpl`
137 selects the actual Forge ModMismatchDisconnectedScreen when mismatch data
arrives, otherwise vanilla DisconnectedScreen. No fake screen or custom
ModLauncher service is introduced. The oracle requires the relevant side's
native NetworkRegistry DEBUG version decision, ERROR channel rejection and
HandshakeHandler ERROR termination, with exact role/thread/logger/text/order.

## Implemented scope and gates

The [standalone QA peer/observer](../fixtures/network-handshake-probe-1.20.1/README.md)
defines five finite conversion scenarios plus a separate
[platform-only control](NETWORK_HANDSHAKE_PLATFORM_CONTROL.md). The latter does
not satisfy a conversion gate. In each conversion negative, the original converted fixture
is installed only on the validating side. The other side either lacks the channel
or has a QA-only channel advertising 2 and accepting 1. No converted predicate,
mandatory dependency or JAR is patched to make a peer load.

Builds/inert oracle tests and native outcomes are recorded independently below.
The public artifact, original and converted baseline hashes are pinned by the
runner before launch. Ordinary locally prepared Forge installations are used;
no Gradle cache is on runtime classpaths and no original code is executed in
analysis/conversion. Original-version execution has not been performed here.

## Initial evidence (retained)

Status: **partial; cursor remains NET-QA-02-HANDSHAKE / CLIENT_ABSENT investigation**.
The [versioned ledger](evidence/network-handshake-2026-09-07.json) records exact
input, installation, native receipt, log, thread-sample and build hashes.

The standalone probe built with seven inert Java tests. Root build after the
marker correction passed in 79 seconds (66 tasks: 21 executed, 45 up-to-date).
It freshly ran 128 pure handshake oracle cases, six marker filesystem cases,
186 prior diagnostic cases and 81 installation cases. Existing root Java test
tasks were UP-TO-DATE. These are QA test cases, not supported mods or native passes.

| Case / fresh run | Actual native result |
| --- | --- |
| MATCHED / handshake-matched1 | FAILED: valid client snapshot, but finish-marker sharing violation |
| MATCHED / handshake-matched2 | PASSED: both native channel version decisions ACCEPTED; snapshot 1/427 applied once on client main thread; both exits 0, server saved, hashes intact; 84 seconds |
| CLIENT_ABSENT / handshake-client-absent1 | FAILED: disconnected without the required version/channel/termination decisions; no login/effect |
| CLIENT_ABSENT / handshake-client-absent2 | FAILED with the same missing decisions; owned-only thread samples captured; no login/effect |
| CLIENT_PROTOCOL | NOT_RUN; batch stopped on failure |
| SERVER_ABSENT | NOT_RUN |
| SERVER_PROTOCOL | NOT_RUN |

Matched receipt SHA-256:
`c3d1e7d804e1e00758765736286be9c4698eb65ea5cc6c3509880d7d6fb82611`.
Public JAR SHA-256 remains
`8daae3478a1b59482c438b843311c4160b5bdc8e4721f8e6067a1a87b81bbf88`;
QA-only probe is `206422ba556b6a44a25e21108d73d62604cbbac52f6251ed6d566bb0a5a68746`.

`handshake-matched1` delivered sequence 1/value 427 on the actual client thread,
but failed the overall gate while publishing the server finish signal. The Java
observer saw the destination while PowerShell still held its exclusive writer,
causing a Windows sharing violation. Both actors closed and the failed run is
retained; its partial client observation is not an overall native PASS. The
runner now publishes each fixed marker by same-directory no-overwrite rename
**after** forced write and close, as the older command-based runner already did.
No sleep/retry suppression, converted predicate, recipe or adapter was changed.
Fresh labels must be used to validate the corrected orchestration.

Final serial root verification after the native attempts/documented cursor also
passed (73 seconds, 66 tasks: 21 executed/45 up-to-date; same fresh QA case counts,
Java tasks UP-TO-DATE). The final build log/hash is in the ledger. A separate
closeout rehashed the original, converted/public/new and old probes and prior
comparison5 receipt unchanged, checked documentation links/PowerShell AST and
confirmed no owned native Java process remained. This does not approve the failed
negative scenario.

The two CLIENT_ABSENT attempts retained four local observation receipts each,
but **no final handshake PASS receipt**. Their per-role `PASSED` fields certify
only the local observation (e.g. actual disconnect screen, no world/login/effect).
The independent native-log oracle deliberately rejects these as overall failures:
both servers logged 22 dispatches, neither client logged LoginWrapper receives
or the required version refusal. Approximately 30 seconds elapsed before the
client disconnected; no timeout cause or platform bug is established from that
timing alone. The second run's `jcmd <verified-owned-pid> Thread.print -l` samples
showed Netty threads in WEPoll and normally waiting/running game loops, not a
proven deadlock. No command-line/account dump, force kill or platform patch was
used. All owned actors stopped; all failed installations/logs remain preserved.

### Immediate investigation, before advancing this gate

Use the failed-run logs and thread samples first; do not keep retrying unchanged
inputs hoping to obtain a pass. Actual pinned `Connection.connect` 322-346 installs
the client login hook and native ReadTimeoutHandler(30); `channelActive` 95-110,
`setProtocol` 113-119, `sendPacket` 196-240 and `ConnectScreen.tick` 151-158 are
concrete inspection points, not proven faulty methods. The native Forge
`fmlloader` log4j2.xml defaults Mojang logs to INFO and explicitly warns that
DEBUG changes `isDebugEnabled` behavior. If additional packet/protocol observations
are needed, implement a bounded, explicit QA diagnostic mode and record its
artifact/configuration separately; do not silently change logging, protocol,
timeouts, class bodies, channel predicates or acceptance criteria. A new source
probe or a new public feature is not a workaround for this failed native gate.

Remaining parent gate: NET-QA-02-COMPARISON. NET-QA-03 queue/session/overload,
capabilities and all later planned categories remain pending. No new real-mod
downloads or publication are part of this stage.

## Diagnostic continuation, 2026-09-07

The [additional ledger](evidence/network-handshake-diagnostics-2026-09-07.json)
preserves the initial ledger rather than rewriting failed attempts into passes.
It binds exact opt-in configurations, builds, QA source revisions and native logs.
The public JAR, converted/original fixture, old two-client probe and comparison5
receipt remain unchanged. No converter recipe, runtime adapter or supported subset
was modified. The standalone handshake probe changed only for bounded diagnostics;
the earlier matched receipt does not certify that new QA hash.

Explicit modes distinguish DEFAULT from Mojang DEBUG, packet markers and a
read-only socket snapshot. A diagnostic oracle success is
`DIAGNOSTIC_ORACLE_PASSED`, **not a normal-mode pass**. Packet markers require
Mojang DEBUG. The socket snapshot can also run independently with default logging,
so that inspection need not enable Mojang debug branches. No timeout or channel
predicate is relaxed, and no platform class is transformed.

Pinned sources were re-read: Connection 126-176 identifies native
ReadTimeoutException handling; 196-240 performs protocol selection and native
writeAndFlush; PacketEncoder logs PACKET_SENT **before** Packet.write, whereas
PacketDecoder logs PACKET_RECEIVED after decoding. LoginWrapper logging is a
later observation. Twenty-two dispatch/encoder entries are not proof of bytes
leaving the socket. `javap -private` against local Netty 4.1.82.Final verified
ByteToMessageDecoder.cumulation, Channel.Unsafe.outboundBuffer and
ChannelOutboundBuffer.totalPendingWriteBytes. ConnectScreen 98-120 supplies the
actual connection; Varint21FrameDecoder 11-41 documents the three-byte length
prefix. These reads are QA observations, not mappings or new adapters.

| Fresh run / configuration | Result and what it actually establishes |
| --- | --- |
| handshake-ca-debug1 / MOJANG_DEBUG | FAILED oracle; client debug log explicitly records Timeout and ReadTimeoutException. This establishes the immediate exception for this run, not the cause of all earlier stalls. |
| handshake-ca-packets1 / MOJANG_DEBUG_PACKET_MARKERS | FAILED oracle; server logs 22 native LOGIN custom-query encoder entries, client no decoded login replies, then ReadTimeoutException. Serialization completion/wire delivery remain unproven. |
| handshake-ca-socket1 / MOJANG_DEBUG_PACKET_SOCKET | DIAGNOSTIC_ORACLE_PASSED in 61 seconds: exact server ABSENT rejection sequence, real client ModMismatchDisconnectedScreen, zero logins/effects, clean exits/save. No socket sample was produced because rejection preceded the five-second timer. |
| handshake-client-absent3 / DEFAULT | FAILED oracle again, using the new probe; actual DisconnectedScreen but no native version/channel/termination decisions. The normal batch stopped; its matched/protocol/server-missing cases were not launched. |
| handshake-ca-socket-default1 / SOCKET_DEFAULT_LOGGING | FAILED oracle. Actual client event-loop sample: LOGIN, active/open/writable, autoRead=true, pending outbound bytes 0, splitter/decoder buffered bytes 0. No server sample in this probe revision. |
| handshake-ca-socket-both1 / SOCKET_DEFAULT_LOGGING | FAILED oracle. Both actual event-loop samples have those same transport states/zero counts; server is a real dedicated process and the common observer resolved without client-only classes. Samples are observations, not delivery/handshake success. |

The probe before socket diagnostics was
`206422ba556b6a44a25e21108d73d62604cbbac52f6251ed6d566bb0a5a68746`;
the client-diagnostic probe was
`66ac3c8766ecaed9caa6494db99cac10a455c47c65540bc002bf10ebbb8aba99`.
The final two-endpoint diagnostic probe is
`b57801796088088ecd65426c3fd7e28232554a9a4e448b1a226cbfab22bd0257`.
Ten inert Java tests passed, including two frame-prefix parser tests and an
archive check for client-class references from the shared server observer. The native
debug success followed by normal failure is **not a proven timing diagnosis or
fix**. Do not rerun unchanged normal cases until an evidence-backed change or
diagnostic question justifies it. CLIENT_PROTOCOL, SERVER_ABSENT, SERVER_PROTOCOL
and the original-version native comparison remain NOT_RUN.

Both-endpoint observation uses MinecraftServer.getConnection (1194-1195),
ServerConnectionListener.getConnections (169-170) and its native synchronized-list
monitor (60/134); it selects at most one live loopback connection and never holds
that monitor while running the event-loop inspection. Both failed default-logging
diagnostic runs retained real diagnostic.json files, unlike the fast DEBUG pass.
Their final oracle failed only after the runner's clean-exit/save checks; no native
process remains. The failed directories, logs and originals are preserved.

Next diagnostic question: where do the native LOGIN query bytes stop between the
server encoder entry and client decoder? The samples do not show an autoRead=false,
partial VarInt frame or pending Netty outbound buffer **at those instants**, but do
not measure cumulative byte delivery or earlier transients. Investigate the pinned
pipeline and transport boundaries before selecting any further bounded diagnostic;
do not change firewall/security settings, force read/flush, extend timeouts, patch
platform classes or change the fixture to get a pass. No claim of a proven Forge,
Windows, transport or converter defect follows from this evidence. NET-QA-02-HANDSHAKE
remains active; the next normal validation requires a justified change, not retries.

## Resume after the handshake slice

The subsequent [JFR/selector investigation](NETWORK_HANDSHAKE_TRANSPORT_DIAGNOSTICS.md)
records additional diagnostics and a controlled Netty interleaving reproduction.
It does not replace the above failed normal attempts or authorize advancing the gate.

NET-QA-02-COMPARISON must audit a separately authored Forge 36.2.42/Java 8 native
probe and isolated source client/server installation; the target-only probe is
not source evidence. Keep the original fixture bytes/hash unchanged. Compare
the same observable contract (delivery, values/counters, native thread, refusal
before play and clean shutdown), not raw version-specific class names or thread
IDs. Extend the comparison in finite cases using the NET-QA-01 payload/session
scenarios; one matching positive snapshot alone must not close that whole gate.
Execution occurs only in this explicit runtime step, never static conversion.
