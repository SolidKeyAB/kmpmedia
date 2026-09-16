package com.solidkey.painpoints.audio.loading

import com.solidkey.painpoints.source.OGSourceFormat

enum class OGAudioFormat(override val extension: String) : OGSourceFormat {
    MP3("mp3"),
    WAV("wav"),
    AAC("aac"),
    M4A("m4a"),
    OGG("ogg")
}
