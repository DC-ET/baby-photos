package com.babyphotos.archive.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "image_analysis",
    indices = [Index(value = ["path"], unique = true)]
)
data class ImageAnalysisEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "path")
    val path: String,

    @ColumnInfo(name = "contains_baby")
    val containsBaby: Boolean,

    @ColumnInfo(name = "confidence")
    val confidence: Int,

    @ColumnInfo(name = "reason")
    val reason: String,

    @ColumnInfo(name = "action")
    val action: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "moved_to")
    val movedTo: String? = null
)
