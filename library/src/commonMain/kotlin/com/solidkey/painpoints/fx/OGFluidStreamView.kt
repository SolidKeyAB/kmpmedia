package com.solidkey.painpoints.fx

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.solidkey.painpoints.ai.toSpecColor
import com.solidkey.painpoints.shape.OGPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun OGFluidStreamView(
    spec: OGFluidStreamSpec,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
) {
    val s = remember(spec) { spec.sanitized() }
    val coreColor = remember(s) { s.coreColor.toSpecColor() ?: Color(0xFF81D4FA) }
    val edgeColor = remember(s) { s.edgeColor.toSpecColor() ?: Color(0xFF01579B) }
    val foamColor = remember(s) { s.foamColor.toSpecColor() ?: Color(0xFFFFFFFF) }

    var elapsed by remember(s) { mutableStateOf(0f) }
    LaunchedEffect(s, playing) {
        if (!playing) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) withFrameMillis { elapsed = (it - start).toFloat() }
    }

    Canvas(modifier.clipToBounds()) {
        drawOGFluidStreamFrame(s, elapsed, coreColor, edgeColor, foamColor)
    }
}

internal fun DrawScope.drawOGFluidStreamFrame(
    spec: OGFluidStreamSpec,
    elapsedMs: Float,
    coreColor: Color,
    edgeColor: Color,
    foamColor: Color,
) {
    val cycle = spec.durationMs
    if (cycle <= 0f) return
    val idx = if (spec.loop) (elapsedMs / cycle).toInt() else 0
    val t = if (spec.loop) elapsedMs - idx * cycle else elapsedMs.coerceIn(0f, cycle)
    
    val p = (t / cycle).coerceIn(0f, 1f)
    if (p <= 0f || p >= 1f) return
    
    // Blast progression: starts at x1,y1 and extends to x2,y2. 
    val w = size.width; val h = size.height
    val minSide = minOf(w, h)
    
    val a = OGPoint(spec.endpointA().x * w, spec.endpointA().y * h)
    val b = OGPoint(spec.endpointB().x * w, spec.endpointB().y * h)
    
    val dx = b.x - a.x; val dy = b.y - a.y
    val len = sqrt(dx * dx + dy * dy)
    if (len < 1f) return
    
    val nx = dx / len; val ny = dy / len
    val px = -ny; val py = nx // Perpendicular
    
    // Advance progress with a fast shoot out, slow dwell, and fast retract
    val streamProgress = if (p < 0.2f) {
        p / 0.2f
    } else if (p < 0.8f) {
        1f
    } else {
        1f - (p - 0.8f) / 0.2f
    }
    val currentLength = len * streamProgress
    val currentTip = OGPoint(a.x + nx * currentLength, a.y + ny * currentLength)
    
    val baseW = minSide * spec.width
    val timeSec = elapsedMs / 1000f
    
    // Draw the fluid column by constructing a path with animated sine waves along the perpendicular
    val steps = 20
    val streamPath = Path()
    val streamPathInner = Path()
    
    for (i in 0..steps) {
        val frac = i.toFloat() / steps
        val ptX = a.x + nx * (currentLength * frac)
        val ptY = a.y + ny * (currentLength * frac)
        
        // Taper at origin and tip
        val taper = (1f - (frac - 0.5f) * (frac - 0.5f) * 4f).coerceAtLeast(0f)
        val widthAtPt = baseW * taper
        
        // Flowing sine waves
        val phase1 = timeSec * 15f + frac * 8f
        val phase2 = timeSec * 22f + frac * 13f
        
        val waveOffset = (sin(phase1) * 0.5f + cos(phase2) * 0.5f) * spec.turbulence * widthAtPt * 0.4f
        
        val edgeX = ptX + px * (widthAtPt * 0.5f + waveOffset)
        val edgeY = ptY + py * (widthAtPt * 0.5f + waveOffset)
        
        if (i == 0) streamPath.moveTo(edgeX, edgeY) else streamPath.lineTo(edgeX, edgeY)
    }
    
    for (i in steps downTo 0) {
        val frac = i.toFloat() / steps
        val ptX = a.x + nx * (currentLength * frac)
        val ptY = a.y + ny * (currentLength * frac)
        
        val taper = (1f - (frac - 0.5f) * (frac - 0.5f) * 4f).coerceAtLeast(0f)
        val widthAtPt = baseW * taper
        
        val phase1 = timeSec * 18f - frac * 9f
        val phase2 = timeSec * 25f - frac * 11f
        
        val waveOffset = (sin(phase1) * 0.5f + cos(phase2) * 0.5f) * spec.turbulence * widthAtPt * 0.4f
        
        val edgeX = ptX - px * (widthAtPt * 0.5f + waveOffset)
        val edgeY = ptY - py * (widthAtPt * 0.5f + waveOffset)
        
        streamPath.lineTo(edgeX, edgeY)
    }
    streamPath.close()
    
    // Draw outer edge and inner core
    val alphaScale = (streamProgress * 1.5f).coerceAtMost(1f)
    drawPath(streamPath, edgeColor.copy(alpha = 0.9f * alphaScale))
    drawPath(streamPath, coreColor.copy(alpha = 0.9f * alphaScale), style = Stroke(width = baseW * 0.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    
    // Foam streaks along the stream
    val foamRng = kotlin.random.Random(spec.seed)
    for (i in 0..4) {
        val startFrac = foamRng.nextFloat() * 0.8f
        val lengthFrac = 0.1f + foamRng.nextFloat() * 0.2f
        if (startFrac > streamProgress) continue
        
        val fStart = startFrac
        val fEnd = minOf(startFrac + lengthFrac, streamProgress)
        
        val lateralOffset = (foamRng.nextFloat() - 0.5f) * 0.8f
        
        val fPath = Path()
        val fSteps = 5
        for (j in 0..fSteps) {
            val f = fStart + (fEnd - fStart) * (j.toFloat() / fSteps)
            val ptX = a.x + nx * (currentLength * (f / streamProgress))
            val ptY = a.y + ny * (currentLength * (f / streamProgress))
            
            val taper = (1f - (f - 0.5f) * (f - 0.5f) * 4f).coerceAtLeast(0f)
            val widthAtPt = baseW * taper
            val streamLateral = lateralOffset * widthAtPt
            
            // moving foam
            val flowPhase = timeSec * 10f
            val flowX = nx * (flowPhase % 1f) * len * 0.1f
            val flowY = ny * (flowPhase % 1f) * len * 0.1f
            
            val finalX = ptX + px * streamLateral - flowX
            val finalY = ptY + py * streamLateral - flowY
            
            if (j == 0) fPath.moveTo(finalX, finalY) else fPath.lineTo(finalX, finalY)
        }
        drawPath(fPath, foamColor.copy(alpha = 0.8f * alphaScale), style = Stroke(width = baseW * 0.1f, cap = StrokeCap.Round))
    }
    
    // Splashing particles at the impact site
    if (streamProgress > 0.8f && spec.splashParticles > 0) {
        val splashP = (p - 0.2f) / 0.6f // normalize splash phase during the dwell
        val splashRng = kotlin.random.Random(spec.seed + idx)
        
        for (i in 0 until spec.splashParticles) {
            val angle = splashRng.nextFloat() * 2f * PI.toFloat()
            val speed = (0.5f + splashRng.nextFloat()) * baseW * 8f
            val life = 0.3f + splashRng.nextFloat() * 0.7f
            val particleT = (splashP + splashRng.nextFloat()) % 1f // loop during dwell
            
            if (particleT < life) {
                val px = currentTip.x + cos(angle) * speed * particleT
                val py = currentTip.y + sin(angle) * speed * particleT + (particleT * particleT * baseW * 15f) // simple gravity
                
                val pSize = baseW * 0.15f * (1f - (particleT / life))
                val isFoam = splashRng.nextBoolean()
                drawCircle(
                    color = if (isFoam) foamColor.copy(alpha = 0.9f) else edgeColor.copy(alpha = 0.9f),
                    radius = pSize,
                    center = Offset(px, py)
                )
            }
        }
    }
}
