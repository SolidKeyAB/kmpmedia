package com.solidkey.painpoints.stage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import com.solidkey.painpoints.ai.OGJoint
import com.solidkey.painpoints.ai.OGPointSpec
import com.solidkey.painpoints.ai.OGRig
import com.solidkey.painpoints.ai.OGSceneSpec
import com.solidkey.painpoints.ai.expanded
import com.solidkey.painpoints.ai.toSpecColor
import com.solidkey.painpoints.motion.OGSpringSpec
import com.solidkey.painpoints.motion.OGSpringValue
import kotlinx.coroutines.isActive
import kotlin.math.abs

/**
 * POKE-TO-DEFORM — the live, touch-it-and-it-bends view for a model-authored scene. Where
 * [OGStageView] *plays* a scene (closed-form transforms the whole region rigidly), this *rigs* it: it
 * binds an [OGRig] over the scene's vertices once, grabs the nearest control point under your finger,
 * drags it, and warps the mesh around it — then lets each pin spring back home when you let go. It is
 * the one thing caching can't fake (a genuine per-frame re-tessellate), and the capability no other KMP
 * vector library has: a *described* illustration ("a smiling fox") you can reach in and pull.
 *
 * It wires the already-shipped pieces together with no new physics or maths:
 *  - control points come from [OGRig.sceneJoints] — one handle at each STRUCTURAL part, so you grab the
 *    arm, not a blind grid cell (pass your own [joints] to override, e.g. a rig you authored);
 *  - the warp is [OGRig]'s linear-blend skinning ([OGRig.deformScene]);
 *  - the settle-home is the shipped analytic [OGSpringValue] (one per pin), so the release has the same
 *    deterministic, platform-identical feel as the dynamics pack.
 *
 * Costs nothing until a finger lands: the frame ticker only advances while a pin is held or still
 * settling, and parks otherwise. Tuned for the small (tens-to-hundreds of parts) scenes the
 * describe→movie SVG front-end produces; for a very dense mesh prefer [OGRig.deformInto] on the hot path.
 *
 * **Code-driven mode** — pass a [pose] and the view renders exactly those joints each frame instead of
 * the finger/spring machinery, so your own code owns the deform: a gaze that follows a target, a squash
 * on a score, a scripted reaction. Animate the offsets however you like (the shipped [OGSpringValue] /
 * dynamics pack is built for this) and hand a fresh [pose] per frame; the rig binds once and only the
 * cheap re-tessellate runs. Leave [pose] null for the default touch-to-poke behaviour. The [pose] must be
 * the joints of THIS rig (same list the rig was bound to, offsets applied) — derive it from [joints] (or
 * an [com.solidkey.painpoints.ai.OGRigSpec]'s `restJoints`) so the rest positions line up.
 *
 * @param scene the scene to make pokeable (sanitized + scatter-expanded once internally).
 * @param joints control points; `null` derives semantic ones from [OGRig.sceneJoints]. If you pass a
 *   list, `remember` it so the rig isn't rebound every recomposition.
 * @param grabRadius how close (normalized) a touch must land to grab a pin.
 * @param spring the settle-home feel (bouncy `< 1` dampingRatio overshoots on release).
 * @param nearest influences per vertex for the skinning bind (higher = smoother, softer falloff).
 * @param showHandles draw a faint dot on each pin (useful while authoring / for the demo).
 * @param background a base coat behind the scene; `null` falls back to the scene's own background.
 * @param pose a code-driven pose: when non-null the view renders these joints (your animation owns the
 *   deform) and the touch + spring-home path is bypassed; when null, touch-to-poke as usual.
 */
@Composable
public fun OGSceneRigView(
    scene: OGSceneSpec,
    modifier: Modifier = Modifier,
    joints: List<OGJoint>? = null,
    grabRadius: Float = 0.12f,
    spring: OGSpringSpec = OGSpringSpec(stiffness = 220f, dampingRatio = 0.55f),
    nearest: Int = 4,
    showHandles: Boolean = false,
    background: Color? = null,
    pose: List<OGJoint>? = null,
) {
    val expanded = remember(scene) { scene.sanitized().expanded() }
    val baseJoints = remember(expanded, joints) { joints ?: OGRig.sceneJoints(expanded) }
    val rig = remember(expanded, baseJoints) { OGRig.bind(expanded, baseJoints, nearest = nearest) }
    // One spring per pin (x, y). Offsets live here; the posed joints are derived from them each frame.
    val springs = remember(rig) { Array(rig.jointCount) { OGSpringValue(0f) to OGSpringValue(0f) } }

    var grabbed by remember(rig) { mutableStateOf(-1) }
    var frame by remember(rig) { mutableStateOf(0L) } // bumped to invalidate the Canvas while anything moves

    fun posed(): List<OGJoint> = List(rig.jointCount) { i ->
        baseJoints[i].copy(offset = OGPointSpec(springs[i].first.value, springs[i].second.value))
    }

    // Settle ticker: pull every un-held pin toward home; park (stop bumping `frame`) once all are at rest.
    // Only runs in touch mode — in code-driven ([pose] != null) mode the caller owns the animation.
    LaunchedEffect(rig, pose == null) {
        if (pose != null) return@LaunchedEffect
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                if (dt > 0f) {
                    var moving = false
                    for (i in 0 until rig.jointCount) {
                        if (i == grabbed) { moving = true; continue } // held by the finger
                        val (sx, sy) = springs[i]
                        sx.update(0f, dt, spring)
                        sy.update(0f, dt, spring)
                        if (abs(sx.value) > 1e-4f || abs(sy.value) > 1e-4f ||
                            abs(sx.velocity) > 1e-4f || abs(sy.velocity) > 1e-4f
                        ) {
                            moving = true
                        } else {
                            sx.snapTo(0f); sy.snapTo(0f)
                        }
                    }
                    if (moving) frame = now
                }
            }
        }
    }

    Canvas(
        // Touch-to-poke only in interactive mode; a code-driven [pose] owns the deform itself.
        modifier.then(if (pose == null) Modifier.pointerInput(rig) {
            detectDragGestures(
                onDragStart = { pos ->
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    grabbed = rig.nearestJoint(posed(), OGPointSpec(pos.x / w, pos.y / h), grabRadius)
                    frame += 1
                },
                onDrag = { change, _ ->
                    val g = grabbed
                    if (g in 0 until rig.jointCount) {
                        change.consume()
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        // Snap the pin's spring straight to the finger; release continues from here.
                        springs[g].first.snapTo(change.position.x / w - baseJoints[g].rest.x)
                        springs[g].second.snapTo(change.position.y / h - baseJoints[g].rest.y)
                        frame += 1
                    }
                },
                onDragEnd = { grabbed = -1; frame += 1 },
                onDragCancel = { grabbed = -1; frame += 1 },
            )
        } else Modifier),
    ) {
        frame // read so spring/drag writes repaint
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        (background ?: expanded.background?.toSpecColor())?.let { drawRect(it) }
        val p = pose ?: posed()
        val deformed = rig.deformScene(expanded, p)
        val path = Path()
        for (s in deformed.shapes) drawOGSceneRegion(path, s, w, h)
        if (showHandles) {
            val r = 0.012f * w
            for (j in p) drawCircle(Color(0x66000000), radius = r, center = Offset(j.pose.x * w, j.pose.y * h))
        }
    }
}
