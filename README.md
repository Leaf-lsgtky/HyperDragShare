# DragShare

LSPosed module for the following HyperOS component:

- Taplus / Portal `4.2.1` (`com.miui.contentextension`)

Application Extension Service is deliberately not in the module scope. On
some devices, force-stopping that package also terminates injected apps.

## Behavior

After Taplus recognizes a long-pressed text or image, the module replaces its
float card with a compact preview above the active pointer. The simple and
portal-glow styles open a single-row share target strip at the bottom; the
circle-menu style can open a five-item semicircle from the left or right edge. Holding at a
scroll hot spot advances the available targets; releasing over a highlighted
target shares directly to that activity.

The module launcher provides Miuix settings for light/dark overlay colors, the
drag overlay style (simple, portal glow, or side semicircle menu), independent
text/image switches, the edge trigger distance, automatic scroll speed, and an
optional foreground-touch lock. Simple style also supports top/bottom/left/right
or tilt-selected placement, opacity, and corner radius. Closing the menu when
the pointer leaves its active region is shared by all three styles. Tilt placement
uses the lower side of the phone and locks that side when the menu first opens.
The home page reports root availability and whether the current APK build has
been injected into Taplus, using a green activated status card after both checks pass.
Two dedicated settings pages group share
targets by application for per-entry visibility and provide long-press drag
reordering; hidden targets are omitted from the ordering page. Changes are read at the next drag session and do not require adding
Application Extension Service to the LSPosed scope. The touch lock is
best-effort: on ROMs that expose the hidden gesture
monitor API to the portal UID it pilfers the current pointer stream, causing the
original window to receive `ACTION_CANCEL`; otherwise the module logs the
unsupported path and keeps the normal gesture behavior.

The visibility page uses expandable application cards. Each application has a
master switch, while each exported sharing activity remains independently
controllable in the indented child list. The toolbar menu can select, clear,
expand, or collapse every group. All settings pages render edge-to-edge while
Miuix handles status bars, display cutouts, and navigation-bar insets.

The portal-glow style follows the animation structure found in the JADX reference:
the bottom light layer is visible as soon as a drag starts, brightens with downward
travel, expands the tray at the bottom, and brings share targets in with staggered
spring motion. The optional full-screen tilt from the reference is intentionally
omitted because the module does not own the host app's Surface.

The circle-menu style follows the currently inspected Oplus ROM `circlemenuview`
classes and the observed app behavior: a 36-degree item arc opens only from the
left or right edge. Its edge and vertical anchor are captured when it expands;
later pointer movement selects targets without moving the menu.

Text is passed as `ACTION_SEND` / `text/plain`. Images are compressed below the
Binder transaction limit, staged in this module's cache, and shared through an
unguessable, short-lived content URI. The selected package receives an explicit
read grant; URI holders can also survive share relays that drop grant metadata.
Image menus put a built-in `保存到本地` action first. It writes to the system
Pictures collection with a second-precision name such as
`20260718_173012.jpg`; its accent-colored rounded tile contains the white Miuix
Download glyph, while app targets follow the saved order and visibility rules.

The implementation and compatibility decisions are documented in
[`docs/IMPLEMENTATION.md`](docs/IMPLEMENTATION.md).

## Build

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

The installable debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Enable

Install the APK, enable the module in LSPosed, select the Taplus scope, and
restart Taplus (a device reboot is the simplest option).

Useful log tags start with `DragShare/`. `RootTouchSource` is authoritative once
it has opened the direct touchscreen node: it scales the advertised ABS range,
maps display rotation, and follows the already-active physical gesture without
making the overlay touchable. `MiuiInputManager` remains a fallback only when
the root source is unavailable.
