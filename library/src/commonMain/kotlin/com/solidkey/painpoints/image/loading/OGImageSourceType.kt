package com.solidkey.painpoints.image.loading

import com.solidkey.painpoints.source.OGSourceFormat
import com.solidkey.painpoints.source.OGSourceType

expect class OGImageFileType(
    source: String,
    format: OGSourceFormat,
    onUnsupportedFormat: ((OGSourceFormat) -> Unit)? = null
) : OGSourceType

class OGImageUrlType(source: String) : OGSourceType(source, OGSourceType.SourceType.URL)
expect class OGImageResourceFileType(
    source: String,
    format: OGSourceFormat,
    onUnsupportedFormat: ((OGSourceFormat) -> Unit)? = null
) : OGSourceType
