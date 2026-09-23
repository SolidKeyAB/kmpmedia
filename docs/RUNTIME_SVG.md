# Runtime-editable SVG — `com.solidkey.painpoints.image.svg` (KMPMedia 1.7.0+)

Most libraries render an SVG as a **static picture**. KMPMedia parses it into a live node tree,
so from 1.7.0 you can **address any node by its `id` and change its attributes at runtime**, bound
to Compose state. The SVG is parsed **once**; only the overrides change, and it redraws live —
turning a `.svg` into a **live template**: gauges, charts, progress rings, status icons, badges.
The same code runs on Android and iOS.

## The API

Pass an `overrides` map (node `id` → [`OGSvgNodeOverride`](../library/src/commonMain/kotlin/com/solidkey/painpoints/image/svg/OGSvgNodeOverride.kt))
to [`OGSVGView`](../library/src/commonMain/kotlin/com/solidkey/painpoints/image/svg/OGSVGView.kt):

```kotlin
import androidx.compose.ui.graphics.Color
import com.solidkey.painpoints.image.loading.OGSvgUrlType
import com.solidkey.painpoints.image.svg.OGSVGView
import com.solidkey.painpoints.image.svg.OGSvgNodeOverride

@Composable
fun Gauge(value: Float) {                       // value 0..100 from your state
    val angle = -90f + (value / 100f) * 180f
    val zone = if (value < 80f) Color(0xFF22C55E) else Color(0xFFEF4444)

    OGSVGView(
        source = OGSvgUrlType("gauge.svg"),
        width = 300f,
        height = 195f,
        overrides = mapOf(
            "needle" to OGSvgNodeOverride(rotation = angle, rotationCx = 100f, rotationCy = 105f),
            "arc"    to OGSvgNodeOverride(stroke = zone),
            "status" to OGSvgNodeOverride(fill = zone),
        ),
    )
}
```

Change the map (from Compose state) and only the affected nodes re-resolve — **no re-parse of the
source**. `overrides = emptyMap()` (the default) is exactly the old, static behaviour, so every
existing `OGSVGView` call is unchanged.

## `OGSvgNodeOverride`

Every field is optional; `null` means "keep the node's original value".

| Field | Effect |
|-------|--------|
| `fill: Color?` | Replace the node's fill |
| `stroke: Color?` | Replace the node's stroke |
| `strokeWidth: Float?` | Replace the stroke width |
| `translateX`, `translateY: Float?` | Translate the node (SVG user units) |
| `rotation: Float?` | Rotate the node (degrees), on top of its own transform |
| `rotationCx`, `rotationCy: Float?` | Pivot for rotation/scale (user units); default = viewBox centre |
| `scaleX`, `scaleY: Float?` | Scale the node about the pivot |
| `pathData: String?` *(since 1.8.0)* | Replace a `<path>` node's geometry with a new `d` string |

- **Paint** (`fill` / `stroke` / `strokeWidth`) is folded into the node's style at shape-prep time
  (reusing the parser's `OGSVGStyle.combine`).
- **Transforms** (`translate` / `rotation` / `scale`) are applied uniformly at draw time — so they
  work on **any** shape type (path, circle, rect, line, polygon, ellipse), independent of the
  renderer's per-shape transform handling.
- **Path geometry** (`pathData`) re-shapes a `<path>` node: the new `d` is re-parsed against the
  same viewBox (so it shares the source's user-space), and its `OGSVGPath` geometry is swapped in —
  paint and transform overrides on the node still apply on top. Ignored on non-path nodes. The
  source tree is never mutated, so switching back to a different (or no) `pathData` always
  re-resolves from the original `d`.

### Path geometry (`pathData`)

```kotlin
// One <path id="icon"> in the SVG; swap its geometry live between recognisable glyphs.
val play  = "M35 25 L75 50 L35 75 Z"
val pause = "M35 25 H47 V75 H35 Z M53 25 H65 V75 H53 Z"
val stop  = "M30 30 H70 V70 H30 Z"

OGSVGView(
    source = OGSvgUrlType("icon.svg"),
    overrides = mapOf("icon" to OGSvgNodeOverride(pathData = if (playing) pause else play)),
)
```

Because the whole geometry is runtime-supplied, your state can compute a `d` per frame (e.g. an
interpolation between two same-structure paths) and feed it here — the foundation for **path
morphing** (a first-class tween lands next on the roadmap).

## How to author the SVG

Just give the nodes you want to drive an `id`:

```svg
<svg viewBox="0 0 200 130" xmlns="http://www.w3.org/2000/svg">
  <path id="arc" d="M 24 105 A 76 76 0 0 1 176 105" fill="none" stroke="#22C55E" stroke-width="12"/>
  <path id="needle" d="M 96 105 L 100 40 L 104 105 Z" fill="#0F172A"/>
  <circle id="status" cx="100" cy="122" r="5" fill="#22C55E"/>
</svg>
```

## Notes

- Reactive & cheap: resolving overrides walks the (already parsed) tree and rebuilds the draw list;
  it does not re-parse or re-fetch the SVG.
- Backward-compatible and purely additive — `overrides` defaults to empty.
- Roadmap follow-up (see [ROADMAP.md](../ROADMAP.md)): a first-class **path morphing** tween that
  animates between two paths (the per-node `d` override above is its building block).

See the **Runtime-editable SVG** screen in the [demo app](https://github.com/SolidKeyAB/kmpmedia-demo)
for a live gauge driven by a slider.
