package com.solidkey.painpoints.utils

import platform.Foundation.NSUUID

actual object OGUUIDGenerator {
    actual fun generate(): String = NSUUID().UUIDString
}