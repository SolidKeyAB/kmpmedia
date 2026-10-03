package com.solidkey.painpoints.image.loading

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class OGStableHashTest {

    @Test
    fun isDeterministic() {
        val url = "https://example.com/a/photo.jpg?v=3"
        assertEquals(OGStableHash.hex(url), OGStableHash.hex(url))
    }

    @Test
    fun is32LowercaseHexChars() {
        val h = OGStableHash.hex("https://example.com/portrait.jpg")
        assertEquals(32, h.length)
        assertTrue(h.all { it in "0123456789abcdef" }, "non-hex char in $h")
    }

    @Test
    fun differentInputsDiffer() {
        assertNotEquals(OGStableHash.hex("https://a.com/1.jpg"), OGStableHash.hex("https://a.com/2.jpg"))
        assertNotEquals(OGStableHash.hex(""), OGStableHash.hex(" "))
        // near-identical URLs must not collide
        assertNotEquals(OGStableHash.hex("abc"), OGStableHash.hex("abd"))
    }

    @Test
    fun emptyStringHashesCleanly() {
        val h = OGStableHash.hex("")
        assertEquals(32, h.length)
    }
}
