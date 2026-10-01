package com.solidkey.painpoints.compositor

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.plus
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.AVFoundation.AVAssetWriter
import platform.AVFoundation.AVAssetWriterInput
import platform.AVFoundation.AVAssetWriterInputPixelBufferAdaptor
import platform.AVFoundation.AVAssetWriterStatusFailed
import platform.AVFoundation.AVFileTypeMPEG4
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVVideoAverageBitRateKey
import platform.AVFoundation.AVVideoCodecKey
import platform.AVFoundation.AVVideoCodecTypeH264
import platform.AVFoundation.AVVideoCompressionPropertiesKey
import platform.AVFoundation.AVVideoHeightKey
import platform.AVFoundation.AVVideoWidthKey
import platform.CoreMedia.CMTimeMake
import platform.CoreVideo.CVPixelBufferCreate
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferRefVar
import platform.CoreVideo.CVPixelBufferRelease
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.CoreVideo.kCVReturnSuccess
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSThread
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.posix.memcpy

/**
 * iOS [OGMp4Encoder]: encodes frames with `AVAssetWriter` (H.264) fed through an
 * `AVAssetWriterInputPixelBufferAdaptor`. Each frame's pixels are converted to packed 32-BGRA in shared
 * code ([argbToBgra]) and `memcpy`'d row-by-row into a freshly-created `CVPixelBuffer` (respecting the
 * buffer's `bytesPerRow`), then appended at an exact presentation time (`frameIndex / fps`).
 *
 * Writing is file-based (`AVAssetWriter` targets a URL), so we write to a temp file in `NSTemporaryDirectory`
 * and [finish] reads it back and deletes it — the library still hands the caller bytes. `finishWriting` is
 * async, so we block on a dispatch semaphore to keep the shared `exportMp4` call synchronous like the GIF path.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual class OGMp4Encoder actual constructor(
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    bitRate: Int,
) {
    private val url: NSURL
    private val writer: AVAssetWriter
    private val videoInput: AVAssetWriterInput
    private val adaptor: AVAssetWriterInputPixelBufferAdaptor
    private var frameIndex = 0L

    init {
        val path = NSTemporaryDirectory() +
            "ogmp4_" + NSProcessInfo.processInfo.globallyUniqueString + ".mp4"
        url = NSURL.fileURLWithPath(path)

        val settings = mapOf<Any?, Any?>(
            AVVideoCodecKey to AVVideoCodecTypeH264,
            AVVideoWidthKey to NSNumber(int = width),
            AVVideoHeightKey to NSNumber(int = height),
            AVVideoCompressionPropertiesKey to mapOf<Any?, Any?>(
                AVVideoAverageBitRateKey to NSNumber(int = bitRate),
            ),
        )
        writer = AVAssetWriter(uRL = url, fileType = AVFileTypeMPEG4, error = null)
        videoInput = AVAssetWriterInput(mediaType = AVMediaTypeVideo, outputSettings = settings)
        videoInput.expectsMediaDataInRealTime = false
        adaptor = AVAssetWriterInputPixelBufferAdaptor(
            assetWriterInput = videoInput,
            sourcePixelBufferAttributes = null,
        )
        writer.addInput(videoInput)
        check(writer.startWriting()) { "AVAssetWriter.startWriting failed: ${writer.error?.localizedDescription}" }
        writer.startSessionAtSourceTime(CMTimeMake(value = 0, timescale = fps))
    }

    actual fun encodeFrame(argb: IntArray) {
        val bgra = argbToBgra(argb)
        val pixelBuffer = memScoped {
            val out = alloc<CVPixelBufferRefVar>()
            val status = CVPixelBufferCreate(
                null, width.convert(), height.convert(),
                kCVPixelFormatType_32BGRA, null, out.ptr,
            )
            check(status == kCVReturnSuccess) { "CVPixelBufferCreate failed ($status)" }
            out.value
        }

        CVPixelBufferLockBaseAddress(pixelBuffer, 0uL)
        val base = CVPixelBufferGetBaseAddress(pixelBuffer)!!.reinterpret<ByteVar>()
        val bytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer).toLong()
        bgra.usePinned { pinned ->
            val srcRowBytes = (width * 4).toLong()
            for (y in 0 until height) {
                memcpy(
                    base + y * bytesPerRow,
                    pinned.addressOf(y * width * 4),
                    srcRowBytes.convert(),
                )
            }
        }
        CVPixelBufferUnlockBaseAddress(pixelBuffer, 0uL)

        while (!videoInput.readyForMoreMediaData) NSThread.sleepForTimeInterval(0.004)
        val ok = adaptor.appendPixelBuffer(pixelBuffer, withPresentationTime = CMTimeMake(frameIndex, fps))
        CVPixelBufferRelease(pixelBuffer)
        frameIndex++
        check(ok) { "appendPixelBuffer failed: ${writer.error?.localizedDescription}" }
    }

    actual fun finish(): ByteArray {
        videoInput.markAsFinished()
        val semaphore = dispatch_semaphore_create(0)
        writer.finishWritingWithCompletionHandler { dispatch_semaphore_signal(semaphore) }
        dispatch_semaphore_wait(semaphore, DISPATCH_TIME_FOREVER)
        check(writer.status != AVAssetWriterStatusFailed) {
            "AVAssetWriter failed: ${writer.error?.localizedDescription}"
        }
        val data = NSData.dataWithContentsOfURL(url)
            ?: throw IllegalStateException("MP4 export produced no file")
        val bytes = data.toByteArray()
        removeTempFile()
        return bytes
    }

    actual fun abort() {
        runCatching { writer.cancelWriting() }
        removeTempFile()
    }

    private fun removeTempFile() {
        runCatching { NSFileManager.defaultManager.removeItemAtURL(url, error = null) }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray =
    this.bytes?.reinterpret<ByteVar>()?.readBytes(this.length.toInt()) ?: ByteArray(0)
