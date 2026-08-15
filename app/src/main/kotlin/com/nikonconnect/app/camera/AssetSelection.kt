package com.nikonconnect.app.camera

import com.nikonconnect.ptp.CameraAssetKind
import com.nikonconnect.ptp.PtpObjectInfo

internal class InitialAssetQuota(
    private val jpegLimit: Int,
    private val rawLimit: Int,
) {
    init {
        require(jpegLimit >= 0) { "jpegLimit must not be negative" }
        require(rawLimit >= 0) { "rawLimit must not be negative" }
    }

    private val accepted = mutableListOf<PtpObjectInfo>()
    var jpegCount: Int = 0
        private set
    var rawCount: Int = 0
        private set

    val isComplete: Boolean get() = jpegCount >= jpegLimit && rawCount >= rawLimit
    val items: List<PtpObjectInfo> get() = accepted

    fun offer(info: PtpObjectInfo): Boolean {
        if (info.isDirectory) return false
        return when (info.assetKind) {
            CameraAssetKind.JPEG -> {
                if (jpegCount >= jpegLimit) return false
                jpegCount++
                accepted += info
                true
            }
            CameraAssetKind.RAW -> {
                if (rawCount >= rawLimit) return false
                rawCount++
                accepted += info
                true
            }
            CameraAssetKind.PNG, CameraAssetKind.MOVIE, null -> false
        }
    }
}

/**
 * Object handles normally increase over time. Each storage is therefore reversed independently,
 * then the newest candidates from all cards are interleaved so one card cannot starve another.
 */
internal fun interleaveNewestHandles(handlesByStorage: List<List<UInt>>): List<UInt> {
    val newestFirst = handlesByStorage.map { it.asReversed() }
    val indexes = IntArray(newestFirst.size)
    val seen = LinkedHashSet<UInt>()
    var addedInRound: Boolean
    do {
        addedInRound = false
        newestFirst.forEachIndexed { storageIndex, handles ->
            val index = indexes[storageIndex]
            if (index < handles.size) {
                seen += handles[index]
                indexes[storageIndex] = index + 1
                addedInRound = true
            }
        }
    } while (addedInRound)
    return seen.toList()
}
