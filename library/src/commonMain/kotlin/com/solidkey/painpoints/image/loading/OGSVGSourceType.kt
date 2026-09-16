package com.solidkey.painpoints.image.loading

import com.solidkey.painpoints.source.OGSourceType

abstract class OGSvgSourceType(source: String, type: SourceType) : OGSourceType(source, type)
class OGSvgUrlType(url: String) : OGSvgSourceType(url, SourceType.URL)
class OGSvgFileType(filePath: String) : OGSvgSourceType(filePath, SourceType.FILE)
class OGSvgResourceFileType(resource: String) : OGSvgSourceType(resource, SourceType.RESOURCE)

