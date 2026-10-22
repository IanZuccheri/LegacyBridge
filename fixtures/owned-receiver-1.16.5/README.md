# Authored bound-receiver fixture (Forge 36.2.42)

Private original source fixture for LB-08 / OWNED_RECEIVER_HANDLES, not a player
download. Built using the pinned ForgeGradle 6.0.54 / official 1.16.5 mappings,
Java 17 compiler with release 8, followed by genuine Forge reobfuscation.
No original mod classes are loaded/executed by the analyzer or converter.

Three classes / nine methods: one registered Item, an owned final Counter helper
and the Forge entrypoint. The helper's public operation() returns this::score as
IntUnaryOperator. Genuine bytecode uses H_INVOKEVIRTUAL, one exact Counter capture,
and (I)I for both SAM and instantiated signature. There is no synthetic static
implementation or Objects.requireNonNull in this factory.

Future isolated source/target oracle (NOT_RUN):

- max stack size 16; registry name legacybridge_receiver:count_token.
- score(n) increments this helper's calls once and returns 3*n+1 using Java int
  overflow. A second independently constructed helper has independent state.
- operation() captures that initialized helper, without calling score yet.
- isFoil creates the callback before calling vanilla super. Vanilla foil true
  short-circuits callback invocation; otherwise odd stack counts produce foil.
- Counter is per Item instance, not per ItemStack/player. Do not treat renderer
  invocation counts as stable. Callback allocation/identity is not promised.
- recipe/tag/model and en_us/pt_br resources retain exact bytes after conversion.

The private candidate is serialized/reopened, reported by hash and discarded.
No public conversion profile, install permission or native compatibility follows.
The existing profiles and older private candidates must reject this input.
Original assets use vanilla texture references; no game binaries/sources included.

Build from repository root, Java 17, serially:

```powershell
.\gradlew.bat '-Djavax.net.ssl.trustStore=NONE' '-Djavax.net.ssl.trustStoreType=Windows-ROOT' -p fixtures/owned-receiver-1.16.5 build --max-workers=1
```
