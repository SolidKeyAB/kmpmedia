package com.solidkey.painpoints.compositor

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

/**
 * Android [OGMp4Encoder]: encodes frames with a hardware/software `MediaCodec` AVC encoder in
 * **flexible YUV420** buffer mode and muxes the H.264 stream into an `.mp4` via `MediaMuxer`.
 *
 * Buffer mode (not an input `Surface`) is deliberate: it lets us set an **exact presentation timestamp**
 * per frame (`frameIndex / fps`) with no EGL/GL boilerplate. Each frame's pixels are converted to I420 in
 * shared code ([argbToI420]) and copied into the codec's input `Image`, honouring each plane's
 * `rowStride`/`pixelStride` so the same code works whether the encoder wants planar or semi-planar YUV.
 * Output is muxed to a temp file which [finish] reads back and deletes, so the library still returns bytes.
 */
internal actual class OGMp4Encoder actual constructor(
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    bitRate: Int,
) {
    private val codec: MediaCodec = MediaCodec.createEncoderByType(MIME)
    private val outFile: File = File.createTempFile("ogmp4_", ".mp4")
    private val muxer: MediaMuxer =
        MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val bufferInfo = MediaCodec.BufferInfo()
    private var trackIndex = -1
    private var muxerStarted = false
    private var frameIndex = 0L
    private var released = false

    init {
        val format = MediaFormat.createVideoFormat(MIME, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
            )
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
    }

    actual fun encodeFrame(argb: IntArray) {
        val i420 = argbToI420(argb, width, height)
        val inIndex = dequeueInput()
        val image = codec.getInputImage(inIndex)
            ?: throw IllegalStateException("MediaCodec returned no input image")
        fillImage(image, i420)
        val pts = frameIndex * 1_000_000L / fps
        codec.queueInputBuffer(inIndex, 0, width * height * 3 / 2, pts, 0)
        frameIndex++
        drain(endOfStream = false)
    }

    actual fun finish(): ByteArray {
        val inIndex = dequeueInput()
        codec.queueInputBuffer(
            inIndex, 0, 0, frameIndex * 1_000_000L / fps,
            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
        )
        drain(endOfStream = true)
        release()
        val bytes = outFile.readBytes()
        outFile.delete()
        return bytes
    }

    actual fun abort() {
        release()
        outFile.delete()
    }

    /** Block (interleaving output drains to avoid a full-buffer deadlock) until an input buffer is free. */
    private fun dequeueInput(): Int {
        while (true) {
            val index = codec.dequeueInputBuffer(TIMEOUT_US)
            if (index >= 0) return index
            drain(endOfStream = false) // no input free yet → make room by draining output
        }
    }

    /** Pump encoded output to the muxer. With [endOfStream], keep pumping until the EOS flag arrives. */
    private fun drain(endOfStream: Boolean) {
        while (true) {
            when (val outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return // else spin, waiting for EOS
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    trackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                else -> if (outIndex >= 0) {
                    val encoded = codec.getOutputBuffer(outIndex)
                    // The codec-config (SPS/PPS) buffer is folded into the muxer track format — don't mux it.
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) bufferInfo.size = 0
                    if (bufferInfo.size > 0 && muxerStarted && encoded != null) {
                        encoded.position(bufferInfo.offset)
                        encoded.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, encoded, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private fun release() {
        if (released) return
        released = true
        runCatching { codec.stop() }
        runCatching { codec.release() }
        if (muxerStarted) runCatching { muxer.stop() }
        runCatching { muxer.release() }
    }

    /** Copy the three I420 planes into the codec's input [image], respecting each plane's strides. */
    private fun fillImage(image: android.media.Image, i420: OGI420) {
        val planes = image.planes
        copyPlane(planes[0], i420.y, width, height)
        copyPlane(planes[1], i420.u, width / 2, height / 2)
        copyPlane(planes[2], i420.v, width / 2, height / 2)
    }

    private fun copyPlane(plane: android.media.Image.Plane, src: ByteArray, w: Int, h: Int) {
        val buf = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        var srcPos = 0
        if (pixelStride == 1 && rowStride == w) {
            buf.put(src, 0, w * h) // tightly packed → one bulk copy
            return
        }
        for (y in 0 until h) {
            var dst = y * rowStride
            for (x in 0 until w) {
                buf.put(dst, src[srcPos++])
                dst += pixelStride
            }
        }
    }

    private companion object {
        const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
        const val TIMEOUT_US = 10_000L
    }
}
