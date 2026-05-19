package com.babyphotos.archive.domain.model

import android.net.Uri
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class BabyAlbumMedia(
    val id: String,
    val mediaType: MediaType,
    val createdAtMillis: Long,
    val contentUri: Uri,
    val path: String?
) {
    val isVideo: Boolean get() = mediaType == MediaType.VIDEO
}

data class BabyAlbumDateSection(
    val id: String,
    val title: String,
    val items: List<BabyAlbumMedia>
)

object BabyAlbumDateGrouper {
    private val formatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

    fun group(items: List<BabyAlbumMedia>, zoneId: ZoneId = ZoneId.systemDefault()): List<BabyAlbumDateSection> {
        val sorted = items.sortedByDescending { it.createdAtMillis }
        if (sorted.isEmpty()) return emptyList()

        val today = LocalDate.now(zoneId)
        val yesterday = today.minusDays(1)
        val sections = mutableListOf<BabyAlbumDateSection>()
        var currentDay: LocalDate? = null
        var bucket = mutableListOf<BabyAlbumMedia>()

        fun flush() {
            val day = currentDay ?: return
            if (bucket.isEmpty()) return
            sections += BabyAlbumDateSection(
                id = day.toEpochDay().toString(),
                title = sectionTitle(day, today, yesterday),
                items = bucket.toList()
            )
            bucket = mutableListOf()
        }

        for (item in sorted) {
            val day = Instant.ofEpochMilli(item.createdAtMillis).atZone(zoneId).toLocalDate()
            if (currentDay != day) {
                flush()
                currentDay = day
            }
            bucket += item
        }
        flush()
        return sections
    }

    private fun sectionTitle(day: LocalDate, today: LocalDate, yesterday: LocalDate): String =
        when (day) {
            today -> "今天"
            yesterday -> "昨天"
            else -> day.format(formatter)
        }
}
