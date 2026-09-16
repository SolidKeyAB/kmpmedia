package com.solidkey.painpoints.source

import androidx.compose.runtime.Composable


open class OGSourceType(
    private var source: String,
    private val type: SourceType
) {
    enum class SourceType {
        URL, FILE, RESOURCE
    }

    fun set(newSource: String) {
        source = newSource
    }

    @Composable
    open fun getLocation(): String? = source

    fun getType(): SourceType = type // ✅ Provides access to type without exposing internals
}
