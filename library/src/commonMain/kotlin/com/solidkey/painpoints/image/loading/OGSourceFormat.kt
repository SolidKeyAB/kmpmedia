package com.solidkey.painpoints.image.loading

import com.solidkey.painpoints.source.OGSourceFormat

enum class OGImageFormat(override val extension: String): OGSourceFormat {
    PNG("png"),
    JPG("jpg"),
    JPEG("jpeg"),
    GIF("gif"),
    WEBP("webp"),  // ✅ Not supported on iOS but included
    BMP("bmp"),
    TIFF("tiff"),
    SVG("svg")
}

