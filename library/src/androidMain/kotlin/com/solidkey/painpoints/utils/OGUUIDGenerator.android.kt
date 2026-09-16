package com.solidkey.painpoints.utils

actual object OGUUIDGenerator {
    actual fun generate(): String = java.util.UUID.randomUUID().toString()
}