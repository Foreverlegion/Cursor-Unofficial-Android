package com.cursorandroid.app.data.repo

import kotlin.math.max

/** Input/output limits for custom folder icons from photos. */
object FolderIconLimits {
    const val MAX_INPUT_BYTES: Long = 50L * 1024 * 1024
    const val MAX_MEGAPIXELS: Long = 100_000_000L
    const val TARGET_EDGE_PX: Int = 256
    const val TARGET_MAX_OUTPUT_BYTES: Int = 40 * 1024
    const val WEBP_QUALITY: Int = 85

    fun inputRejectMessage(bytes: Long?, width: Int, height: Int): String? {
        if (bytes != null && bytes >= 0 && bytes > MAX_INPUT_BYTES) {
            return "That photo is over 50 MB. Pick a smaller image."
        }
        if (width <= 0 || height <= 0) {
            return "Couldn't read that image."
        }
        val pixels = width.toLong() * height.toLong()
        if (pixels > MAX_MEGAPIXELS) {
            return "That photo is too large (over 100 megapixels)."
        }
        return null
    }

    /** Subsample so the longer edge is at most about [targetMaxEdge] before crop/scale. */
    fun computeInSampleSize(width: Int, height: Int, targetMaxEdge: Int = TARGET_EDGE_PX): Int {
        var sample = 1
        val maxDim = max(width, height)
        while (maxDim / sample > targetMaxEdge * 2) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }
}

sealed interface FolderIconImportResult {
    data class Success(val iconId: String) : FolderIconImportResult
    data object Cancelled : FolderIconImportResult
    data class Failure(val message: String) : FolderIconImportResult
}
