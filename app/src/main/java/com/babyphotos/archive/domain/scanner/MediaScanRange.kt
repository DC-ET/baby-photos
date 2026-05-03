package com.babyphotos.archive.domain.scanner

/**
 * 计算 MediaStore 查询的 [DATE_ADDED] 下界（秒，含等号查询）。
 *
 * - 若用户改过「扫描起始时间」（与上次成功扫描结束时的快照不一致），则严格从当前配置的起始时间扫。
 * - 否则从 [maxOf](配置起始, 上次已覆盖的 dateAdded 水位) 扫，减少重复遍历。
 */
fun computeEffectiveMediaScanLowerBound(
    configuredStartEpochSec: Long,
    snapshotAtLastScan: Long,
    lastDateAddedWatermark: Long
): Long {
    require(configuredStartEpochSec > 0L) { "configuredStartEpochSec must be positive" }
    val settingsChanged = configuredStartEpochSec != snapshotAtLastScan
    return if (settingsChanged) {
        configuredStartEpochSec
    } else {
        maxOf(configuredStartEpochSec, lastDateAddedWatermark)
    }
}
