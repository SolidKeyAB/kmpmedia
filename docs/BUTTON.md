# Any graphic → a button — KMPMedia 1.24.0

KMPMedia already clips a photo, SVG, GIF or video to any shape, and 1.15.0 made that shape
draggable (`Modifier.ogInteractive`). 1.24.0 adds the missing half: **make any graphic a real,
accessible button** with one modifier. KMPMedia ships no UI components and no theme system — on
purpose — so instead of a `Button` widget it ships the **bridge**: `Modifier.ogButton` turns whatever
you already drew (a photo, an SVG, a shape-clipped or lasso-cut cut-out, a raw `Canvas` drawing) into
a tappable button, with a pressed-state visual and screen-reader semantics.

It lives in the same `com.solidkey.painpoints.gesture` package as `ogInteractive` and is purely
additive — nothing existing changed, no new dependency, identical on Android & iOS.

## The tap twin of `ogInteractive`

`ogButton` is the **tap** half of the same idea `ogInteractive` is the **drag** half of, and both
reuse the same `OGHitArea`, so a tap is **shape-aware**:

```kotlin
OGImageView(
    source = photo,
    clipShape = headLasso,                           // the visible cut-out
    modifier = Modifier
        .size(160.dp)
        .ogButton(
            hitArea = OGHitArea.polygon(headLasso),  // only the head is tappable
            pressEffect = OGPressEffect.Brutalist(),
        ) { open() },
)
```

```kotlin
@Composable
fun Modifier.ogButton(
    hitArea: OGHitArea? = null,
    enabled: Boolean = true,
    pressEffect: OGPressEffect = OGPressEffect.Scale(),
    onLongClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    onClick: () -> Unit,
): Modifier
```

- `onClick` is the trailing lambda, so the call reads like any Compose click.
- `onLongClick` is optional and fires after the platform long-press timeout.
- `contentDescription` is the spoken label for TalkBack / VoiceOver.

## Shape-aware taps — tap the shape, not the box

A clipped composable is still a **rectangle**. Without a hit area, a tap anywhere in that rectangle
counts — including the empty corners of a triangle or a lasso cut-out. Pass an `OGHitArea` matching
the clip and a tap only registers **inside the silhouette**; a tap in a transparent corner is ignored
and **falls through** to whatever is behind it.

```kotlin
.ogButton(hitArea = OGHitArea.of(OGShapeType.DIAMOND)) { ... }
// or a free-form lasso, reusing the exact clip outline:
.ogButton(hitArea = OGHitArea.polygon(myLassoShape)) { ... }
```

`OGHitArea` is described entirely in **normalized `0..1`** coordinates and is pure data + pure math
(even-odd ray cast / analytic tests) — the same hit region type `ogInteractive` uses. With a `null`
`hitArea` the whole box is clickable.

## Press effects — the feedback *is* the indication

With no Material dependency there is no ripple, so the **press effect is the indication**. It rides
one `graphicsLayer` (plus one `drawBehind` for the shadow), so there is nothing per frame but the
short press/settle animation. `OGPressEffect` is a sealed interface:

| Effect | What it does |
|---|---|
| `Scale(scale = 0.94f)` | Shrinks slightly while pressed, springs back on release. The default. |
| `Dim(alpha = 0.6f)` | Fades while pressed, returns to opaque on release. |
| `Brutalist(offset = 3.dp, shadowColor)` | The flat / pop-art **hard-shadow push-in**: the modifier draws its own hard-offset silhouette shadow (no blur) behind the content at rest; on press the content slides onto it and the shadow collapses, so the graphic looks pressed into the page. The shadow traces the `hitArea` outline, so give it a matching `OGHitArea` (it falls back to the full rectangle otherwise). |
| `None` | No visual — just the click + semantics. |

## Accessibility

`ogButton` attaches `Role.Button`, an activatable click (and long-click) action, and `disabled()`
when `enabled = false`, so the button is announced and operable by TalkBack / VoiceOver. Pass a
`contentDescription` to give it a label.

## Notes & gotchas

- **No double-tap**, by design — recognizing one would add the double-tap timeout (~300ms) of latency
  to *every* press, which a button can't afford.
- **A disabled button still consumes the touch** inside its silhouette (as a no-op), so it never
  leaks a tap to content sitting behind it.
- **Don't apply `ogButton` and `ogInteractive` to the same node** — the press and the drag would
  fight over the same pointer. Use one or the other per node.
- **Taps survive an ancestor scroll.** Inside a `verticalScroll` / `LazyColumn`, a real finger always
  drifts a little; `ogButton` consumes that in-bounds movement so the scroll can't steal the tap past
  touch-slop (a tap stays a tap). The trade-off is deliberate: because the button owns its in-bounds
  movement, you **can't start a page scroll by dragging *on* a button** — scroll from the surrounding
  area instead. Sliding off the button still cancels the press and hands the gesture back to the parent.

## Tested & verified

- **Pure maths unit-tested on JVM + iOS** (11 tests): the press-depth transform for each effect and
  `OGHitArea.outlineNormalized()` (the outline the `Brutalist` shadow traces).
- **Live-verified on Android**: all three press effects render (the `Brutalist` hard shadow is visible
  behind the cut-out); shape-aware taps register inside the silhouette while a tap on a transparent
  corner of the same photo falls through; and — inside a `verticalScroll` — a *jittery* tap (a finger
  that drifts 30–60px, like a real one) still fires on every button, while the page still scrolls from
  non-button areas.

The tap-driving code is Compose UI (one file); the hit region and the press maths are shared,
testable `commonMain`.
