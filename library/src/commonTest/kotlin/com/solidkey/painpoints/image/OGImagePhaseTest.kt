package com.solidkey.painpoints.image

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pure-logic tests for [ogImagePhase], the little state machine behind OGImageView's
 * loading / loaded / error slots. No Compose runtime needed — same intent as the library's
 * other pure-math tests (hit-test, clamps, colour parsing, GIF clock).
 */
class OGImagePhaseTest {

    @Test
    fun noPainterAndNoFailure_isLoading() {
        assertEquals(OGImagePhase.Loading, ogImagePhase(hasPainter = false, failed = false))
    }

    @Test
    fun painterArrived_isSuccess() {
        assertEquals(OGImagePhase.Success, ogImagePhase(hasPainter = true, failed = false))
    }

    @Test
    fun failedWithNoPainter_isError() {
        assertEquals(OGImagePhase.Error, ogImagePhase(hasPainter = false, failed = true))
    }

    @Test
    fun failureWinsOverAStalePainter_isError() {
        // The loaders hand back a solid colour fallback painter AND fire onError on failure, so a
        // painter can be present while failed is true. Error must win so the error slot shows.
        assertEquals(OGImagePhase.Error, ogImagePhase(hasPainter = true, failed = true))
    }
}
