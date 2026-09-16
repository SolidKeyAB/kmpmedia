package com.solidkey.painpoints.video.loading

import com.solidkey.painpoints.source.OGSourceFormat
import com.solidkey.painpoints.source.OGSourceType

expect class OGVideoFileType(
    source: String,
    onUnsupportedFormat: ((OGSourceFormat) -> Unit)? = null
) : OGSourceType

class OGVideoUrlType(source: String) : OGSourceType(source, OGSourceType.SourceType.URL)
expect class OGIVideoResourceFileType(
    source: String,
    onUnsupportedFormat: ((OGSourceFormat) -> Unit)? = null
) : OGSourceType
