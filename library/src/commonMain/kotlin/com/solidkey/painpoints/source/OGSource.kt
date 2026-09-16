package com.solidkey.painpoints.source

sealed class OGSource {
    data class Url(val url: String) : OGSource() {
        override fun toString() = "URL: $url"
    }

    data class FilePath(val path: String, val extension: String? = null) : OGSource() {
        override fun toString() = "File Path: $path${extension?.let { ".$it" } ?: ""}"
    }

    data class Resource(val resource: String, val extension: String? = null) : OGSource() {
        override fun toString() = "Resource: $resource${extension?.let { ".$it" } ?: ""}"
    }
}
