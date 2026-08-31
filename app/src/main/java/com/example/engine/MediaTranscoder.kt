package com.example.engine

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Local post-processing for completed downloads, using Media3 Transformer
 * (Android's own codec pipeline - no bundled native binary, unlike ffmpeg-kit,
 * which was pulled from Maven Central in April 2025 and has no maintained
 * replacement).
 */
object MediaTranscoder {

    data class Probe(val hasVideo: Boolean, val hasAudio: Boolean, val height: Int)

    fun probe(file: File): Probe {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            Probe(hasVideo, hasAudio, height)
        } catch (e: Exception) {
            Probe(hasVideo = false, hasAudio = false, height = 0)
        } finally {
            retriever.release()
        }
    }

    suspend fun extractAudio(context: Context, inputFile: File, outputFile: File): Boolean {
        val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(inputFile)))
            .setRemoveVideo(true)
            .build()
        return runTransform(context, editedMediaItem, outputFile)
    }

    suspend fun downscale(context: Context, inputFile: File, outputFile: File, targetHeight: Int): Boolean {
        val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(inputFile)))
            .setEffects(Effects(emptyList<AudioProcessor>(), listOf(Presentation.createForHeight(targetHeight))))
            .build()
        return runTransform(context, editedMediaItem, outputFile)
    }

    // Transformer requires a thread with a prepared Looper for its callbacks.
    private suspend fun runTransform(
        context: Context,
        editedMediaItem: EditedMediaItem,
        outputFile: File
    ): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val transformer = Transformer.Builder(context)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (cont.isActive) cont.resume(true, onCancellation = { _, _, _ -> })
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        if (cont.isActive) cont.resume(false, onCancellation = { _, _, _ -> })
                    }
                })
                .build()

            cont.invokeOnCancellation { transformer.cancel() }

            try {
                transformer.start(editedMediaItem, outputFile.absolutePath)
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(false, onCancellation = { _, _, _ -> })
            }
        }
    }
}
