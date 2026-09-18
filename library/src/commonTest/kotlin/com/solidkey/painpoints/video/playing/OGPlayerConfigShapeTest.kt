package com.solidkey.painpoints.video.playing

import com.solidkey.painpoints.shape.OGPolygonShape
import com.solidkey.painpoints.shape.OGShapeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Pins [OGPlayerConfig.effectiveShape] — the shape a video is actually clipped to. This mirrors
 * [com.solidkey.painpoints.image.OGImageView]'s `clipShape` override so video reaches shape parity
 * with images: a non-null [OGPlayerConfig.clipShape] (e.g. a free-form OGPolygonShape lasso) must
 * win over the built-in [OGPlayerConfig.displayShape], and with no override the built-in shape is
 * used unchanged (so existing callers are unaffected).
 */
class OGPlayerConfigShapeTest {

    @Test
    fun defaultsToBuiltInShapeWhenNoClipShape() {
        val config = OGPlayerConfig()
        // No lasso set → the effective clip is exactly the built-in displayShape/cornerRadius shape.
        assertEquals(config.shape, config.effectiveShape)
    }

    @Test
    fun builtInDisplayShapeHonoredWhenNoOverride() {
        val config = OGPlayerConfig(displayShape = OGShapeType.CIRCLE)
        assertEquals(config.shape, config.effectiveShape)
    }

    @Test
    fun clipShapeOverridesDisplayShape() {
        val lasso = OGPolygonShape.of(0.5f to 0f, 1f to 1f, 0f to 1f)
        val config = OGPlayerConfig(displayShape = OGShapeType.CIRCLE, clipShape = lasso)
        // The lasso must take precedence over the built-in circle.
        assertSame(lasso, config.effectiveShape)
    }
}
