package com.babyphotos.archive.domain.model

enum class MediaType {
    IMAGE,
    VIDEO
}

data class ScannedPhoto(
    val id: Long,
    val path: String,
    val dateAdded: Long,
    val mimeType: String,
    val size: Long,
    val mediaType: MediaType = MediaType.IMAGE
)
