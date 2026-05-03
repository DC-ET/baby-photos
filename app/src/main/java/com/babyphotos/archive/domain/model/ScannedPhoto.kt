package com.babyphotos.archive.domain.model

data class ScannedPhoto(
    val id: Long,
    val path: String,
    val dateAdded: Long,
    val mimeType: String,
    val size: Long
)
