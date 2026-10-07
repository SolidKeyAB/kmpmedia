# Academic foundations

KMPMedia is deliberately **small and dependency-free** — but its procedural features are not ad-hoc.
Each one is grounded in published computer-graphics research and implemented as **pure, deterministic,
cross-platform `commonMain` math** that runs on the 60fps path with no new dependency. This page maps
**feature → algorithm → source**, so the "solid background" behind the library is visible at a glance.

> Design rule throughout: *parse / generate once, cheap per frame, GPU-composited, and frame-identical
> on Android & iOS.* The references below are the techniques that let a zero-dependency library do this.

---

## Curves & shape geometry

- **Centripetal Catmull-Rom splines** — rounds a faceted outline through every vertex without cusps or
  self-intersections. Used for `OGPolygonShape(points, smoothing)`, the `smooth` style op, `OGMorphShape`
  resampling, and the `OGSlash` centreline.
  - Catmull, E. & Rom, R., *A class of local interpolating splines*, 1974.
  - Yuksel, C., Schaefer, S. & Keyser, J., *Parameterization and applications of Catmull-Rom curves*,
    Computer-Aided Design 43(7), 2011 — the centripetal (α = 0.5) choice. <https://doi.org/10.1016/j.cad.2010.08.008>

- **Cubic Bézier curves / de Casteljau's algorithm** — the smoothed outlines are emitted as cubic
  Béziers, and the SVG renderer evaluates path `C`/`S`/`Q` segments. Pierre Bézier & Paul de Casteljau
  (1959–62).

- **Superellipse (Lamé curve) / squircle** — `OGParametric.superellipse`, the smooth square↔circle knob.
  Gabriel Lamé (1818); popularised as the "squircle" by Piet Hein.

## Perceptual colour

- **OKLab** — the `OGSlash` gradient is interpolated in OKLab (via `OGFxColor`) rather than sRGB, so a
  ramp stays perceptually even instead of dipping through a muddy middle.
  - Ottosson, B., *A perceptual color space for image processing (Oklab)*, 2020.
    <https://bottosson.github.io/posts/oklab/>

## Procedural noise & motion

- **Simplex noise** — the organic `OGSlash` edge (and future FX) use 2D simplex noise: no axis-aligned
  artefacts, a smooth gradient everywhere, cheap. Implemented in `OGFxNoise`.
  - Perlin, K., *Improving Noise*, SIGGRAPH 2002.
  - Gustavson, S., *Simplex Noise Demystified*, 2005.
    <https://cgvr.cs.uni-bremen.de/teaching/cg_literatur/simplexnoise.pdf>

- **Fractal Brownian motion (fBm)** — summing octaves of noise for multi-scale richness (`OGFxNoise.fbm`,
  and the lightning branches). Mandelbrot, B., *The Fractal Geometry of Nature*, 1982.

- **Flow noise, curl noise & domain warping** — the design lineage for the flowing water/flame edge (we
  implement it as time-sampled simplex fBm; these are the techniques it descends from).
  - Perlin, K. & Neyret, F., *Flow Noise*, SIGGRAPH Technical Sketches, 2001.
    <https://morpho.inrialpes.fr/Publications/2001/PN01/>
  - Bridson, R., Hourihan, J. & Nordenstam, M., *Curl-Noise for Procedural Fluid Flow*, SIGGRAPH 2007.
    <https://www.cs.ubc.ca/~rbridson/docs/bridson-siggraph2007-curlnoise.pdf>
  - Quílez, I., *Domain warping*. <https://iquilezles.org/articles/warp/>

- **Value (hash-gradient) noise** — the original hand-drawn "living line" jitter in the `boil` style op
  (`OGBoil`), a deterministic integer-hash noise.

## Fractal generation

- **Midpoint displacement / fBm + stochastic branching** — the forked lightning channels of a `bolt`
  slash (`slashBranches`): a main channel that jags fractally and spawns tapering sub-branches.
  - Fournier, A., Fussell, D. & Carpenter, L., *Computer rendering of stochastic models*, CACM 25(6),
    1982 (midpoint displacement / diamond-square). <https://doi.org/10.1145/358523.358553>
  - Niemeyer, L., Pietronero, L. & Wiesmann, H.J., *Fractal dimension of dielectric breakdown*, 1984 —
    the branching lightning model.

## Image encoding & quantization (GIF export)

The pure-Kotlin `OGGifEncoder` (compositor `exportGif`) is three classic algorithms end to end:

- **Median-cut colour quantization** — building the ≤256-colour palette. Heckbert, P.,
  *Color Image Quantization for Frame Buffer Display*, SIGGRAPH '82. <https://doi.org/10.1145/965145.801294>
- **Floyd–Steinberg error-diffusion dithering** — band-free gradients (default since v1.26.0).
  Floyd, R.W. & Steinberg, L., *An adaptive algorithm for spatial greyscale*, Proc. SID, 1976.
- **LZW compression** — the GIF bitstream. Ziv, J. & Lempel, A. (LZ78), 1978; Welch, T.,
  *A Technique for High-Performance Data Compression*, IEEE Computer, 1984.

## Segmentation → vector (auto-cutout)

The mask → lasso tracer (`OGSegmenter` output → `OGPolygonShape`) is:

- **Moore-neighbour boundary tracing** — walking a binary mask into an ordered contour.
- **Douglas–Peucker line simplification** — reducing that contour to a few vertices. Douglas, D. &
  Peucker, T., *Algorithms for the reduction of the number of points required to represent a digitized
  line or its caricature*, Cartographica, 1973.

## Deterministic randomness & integration

- **Xorshift PRNG** — the particle system seeds a tiny, allocation-free xorshift so a burst is identical
  on every platform from a seed. Marsaglia, G., *Xorshift RNGs*, Journal of Statistical Software, 2003.
- **Explicit (forward) Euler integration** — the per-frame particle physics step (velocity, gravity,
  drag). The standard first-order numerical integrator.

## The glow ceiling (researched, opt-in path)

A true gaussian bloom needs an offscreen render target + a platform shader (Android `RenderEffect`,
Skiko SkSL), which is API-floored and not frame-identical — so the shipped glow is a layered additive
approximation and the "soft" version is an opt-in. The research that defines the efficient algorithm:

- Jimenez, J., *Next Generation Post Processing in Call of Duty: Advanced Warfare*, SIGGRAPH 2014.
  <https://www.iryoku.com/next-generation-post-processing-in-call-of-duty-advanced-warfare/>
- Bjørge, M., *Bandwidth-Efficient Rendering* (dual Kawase blur), ARM @ SIGGRAPH 2015.
- Green, C. (Valve), *Improved Alpha-Tested Magnification for Vector Textures and Special Effects*
  (signed-distance-field outlines & glow), SIGGRAPH 2007.

---

*Everything above is implemented in shared `commonMain` as pure math — no native graphics dependency,
no per-frame allocation on the hot paths, and the same result on Android and iOS. That is the point:
a small library can stand on a deep, well-established foundation.*
