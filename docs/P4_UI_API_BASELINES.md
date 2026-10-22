# P4 native UI and execution-test API baselines

Source audit: 2026-09-05. Target remains Minecraft 1.20.1 / Forge 47.4.23 /
Java 17. No new legacy conversion category is enabled by these UI APIs.
This is an API/evidence plan, not a record that the P4 runtime scenarios passed.

## Inspected source bytes

The target development source archive is
`${DEVELOPER_HOME}/.gradle/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.20.1-47.4.23_mapped_official_1.20.1/forge-1.20.1-47.4.23_mapped_official_1.20.1-sources.jar`,
SHA-256 `4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932`.
Line references below refer to entries in those exact local source bytes.
The prior [product API baseline](PRODUCT_API_BASELINES.md) also records the
legacy 1.16.5 source archive and explains why legacy `GuiScreenEvent` is not the
API used to implement a native 1.20.1 screen. The loader/JarJar audit remains
[separate](PACKAGING_ARCHITECTURE.md).

## First launch and title-screen discovery

`AccessibilityOnboardingScreen.init()` adds a normal button whose message is
`CommonComponents.GUI_CONTINUE`, translated by key `gui.continue`. Its callback
calls `onClose()`, which sets a native `TitleScreen` through
`closeAndSetScreen(...)`. That method changes the isolated instance's onboarding
option and saves it. A new QA instance must press that real button rather than
silently changing user options or skipping the title screen under test.

`Screen.java:314-343` initializes widgets, posts Forge's native init events and
exposes the ordinary `children()` event-listener collection. The title hook
uses `ScreenEvent.Init.Post` on `MinecraftForge.EVENT_BUS`, with physical
`Dist.CLIENT` isolation. Rebuild/resize also posts init events, so the menu
button's duplicate guard remains necessary. An original legacy JAR rejected
from `mods` before the menu cannot be rescued by this screen.

## Keyboard, focus and selection lists

- `Screen.java:124-164` processes Escape and focus-navigation keys, including
  Tab (`258`) and directional arrows. Escape invokes `onClose()` when allowed.
- `ContainerEventHandler.java:37-42` dispatches mouse clicks to children and
  updates focus after a handled click. `:73-88` dispatches key/character events
  to the focused child and exposes `getFocused()`/`setFocused(...)`.
- `EditBox.java:334-344` accepts characters only when visible, focused and
  editable. Testing `Screen.charTyped(...)` after an actual mouse focus change
  therefore exercises the public focus/input chain, rather than bypassing it
  with direct string assignment alone.
- `AbstractWidget` exposes `getMessage()`, `getX()`, `getY()`, dimensions,
  visibility and active state. The probe can locate a translated native button
  or edit box by its displayed message and click through native events.
- `AbstractSelectionList.java:81-106` exposes current selection, first entry and
  `children()`. `:454-466` exposes row left/right coordinates but keeps row top
  calculation protected. Forge-added `:517-522` exposes list dimensions and
  top/bottom/left/right bounds. Probes must not invent a public `getRowTop`.
- `ObjectSelectionList.Entry.java` is nested in `ObjectSelectionList.java:62-65`
  and supplies public `getNarration()`. This is an existing accessibility
  presentation surface, not a reason to add test-only getters to the product.

The native list and actual controller must own search/multiselection/queue
semantics. Probes may use the same public mouse, keyboard and narration APIs,
but must not reflect into private screen/controller state or define fake
platform classes. Direct `Button.onPress()` remains appropriate for checking a
known active button's action; mouse/keyboard tests are separate coverage.

## GUI scale and resize

`Options.java:856-858` returns `OptionInstance<Integer> guiScale()`.
`OptionInstance.java:117-130` exposes `get()` and validated `set(T)`.
`Minecraft.java:1268-1280` supplies public `resizeDisplay()`: it computes the
scale, updates the native window scale, calls the current screen's public
`resize(...)`, resizes Forge GUI layers and updates framebuffer/render sizing.
`Screen.java:429-432` updates logical dimensions and repositions widgets;
the default reposition path rebuilds them.

A bounded isolated probe can remember the previous GUI scale, choose supported
values, invoke `resizeDisplay()` on the client thread, check widget bounds and
state preservation, then restore the previous value. This tests actual
scale-induced rebuilds. It is not proof of every desktop window-resize mode,
display DPI, ultrawide layout, accessibility setting or IME. Probe code must not
persist arbitrary changes to a player's existing options/profile.

## Confirmation, reports and next-start activation

`ConfirmScreen.java:26-35` accepts an explicit callback and custom affirmative
and negative components. `:51-55` wires real buttons to `callback.accept(true)`
and `callback.accept(false)`. Its Escape handling calls the negative callback.
The P4 add-only apply path must require this explicit confirmation and must not
interpret a finished conversion as installation consent.

Reports are data, and result JARs are not executable files to open through an
OS association. The product's typed path categories distinguish report files
from the containing result folder. The UI test must verify report existence,
bounded content and accessible details separately from an OS file-opener test.
Opening a text editor/file manager is not required to prove a valid report was
written, and a clipboard/path display alone does not prove report contents.

The loading mod list and registries belong to the already-started Forge session.
Successful apply can only report `PENDING_RESTART` here. A later, separately
started ordinary Forge process is required to prove discovery and native content
behavior. No test helper may hot-load originals or mutate the active Forge mod
set to simulate that later startup.

## Framebuffer screenshots and bounded execution

`Screenshot.grab(File, String, RenderTarget, Consumer<Component>)` is present in
the exact target source. It obtains the framebuffer image on the render thread
(or records a render call if invoked elsewhere) and performs PNG writing on
`Util.ioPool()`. Its callback reports native `screenshot.success` or failure;
Forge can also cancel/replace a screenshot. The probe uses unique operation-owned
filenames beneath the isolated instance's `screenshots` directory, waits via
nonblocking completion polling and never stops the process before the callback.
`Minecraft.getMainRenderTarget()` supplies the actual target framebuffer.

`TickEvent.ClientTickEvent` and `RenderTickEvent` expose `phase`; the END events
can count observed ticks/frames during queued work without blocking the render
thread. Such counts demonstrate the observed test interval, not a general
performance or memory guarantee. Heavy fixture generation, hashing, filesystem
enumeration, reports and engine waits belong on a bounded QA coordinator/engine
worker, not those callbacks. An overall timeout is separate from cooperative
engine cancellation and must not claim that a publication already committed was
cancelled.

## P4 fixture and execution boundaries

The separate validation source set remains absent from `LegacyBridge.jar`.
`-Dlegacybridge.product.p4=true` selects P4 and disables the original P3 probe
subscribers. The QA mod's converted-fixture dependency is optional so a fresh
P4 instance can start with only the public product and test harness installed.
The P3 probes still explicitly assert fixture presence when selected.

The preflight worker expects the existing positive 1.16.5 fixture staged by the
test runner in `legacybridge/input`, then creates only bounded original QA bytes:
corrupt non-ZIP input, an unsupported legacy network reference, multiple declared
mod IDs, target-version metadata, ambiguous metadata and an independent duplicate
ID pair. Generated classes carry an initializer marker which must remain unset;
ASM emits/inspects their bytes without defining or executing them. Generated
fixtures never enter `mods`, the bootstrap classpath or the public artifact.
Existing nonmatching files are not overwritten. Source hashes are rechecked after
the scenario.

The runtime vertical slice targets native inventory/search, actionable versus
incompatible rows, queued positive/unsupported outcomes, continued usable UI,
details/reports, GUI-scale/focus rebuilding, explicit add-only confirmation and
pending-restart status without hot loading. Deterministic queue saturation,
resource ceilings, crash windows and race-controlled cancellation require unit
tests in addition to any observed client scenario. Closing during a tiny job
does not by itself prove cancellation won its publication race; evidence must
distinguish cancelled, already committed and pending cleanup outcomes.

The native client probe logs `LEGACYBRIDGE_P4_CLIENT_OK` only after its file
worker rechecks all original hashes and every published staged JAR after the
close/reopen scenario. It permits one additional complete baseline publication
if conversion won that last cancellation race; it never counts that as cancelled.
The screenshot callback must also have succeeded before the completion marker.

For next-start activation, `P4ServerActivationProbe` requires both
`-Dlegacybridge.product.p4=true` and
`-Dlegacybridge.product.p4.relaunch=true`. The isolated ordinary server must
receive the exact Apply-installed filename and bytes, plus the public product
and separate probe, not the original input fixtures. The probe reuses the real
P3 registry/property/tag/native-recipe assertions and separately hashes the
installed file. Only then can it log `LEGACYBRIDGE_P4_ACTIVATION_OK`. This is a
second process and a dedicated-server behavior check, not an automated launcher
restart or client asset/rendering proof. The absence of these markers in an
execution record leaves the respective runtime gates pending.
