package com.babyphotos.archive.domain.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaScanRangeTest {

    @Test
    fun firstScanOrUserChangedStart_usesConfiguredOnly() {
        assertEquals(100L, computeEffectiveMediaScanLowerBound(100L, 0L, 500L))
        assertEquals(80L, computeEffectiveMediaScanLowerBound(80L, 100L, 500L))
    }

    @Test
    fun incremental_usesMaxOfConfiguredAndWatermark() {
        assertEquals(300L, computeEffectiveMediaScanLowerBound(100L, 100L, 300L))
        assertEquals(100L, computeEffectiveMediaScanLowerBound(100L, 100L, 50L))
    }
}
