package com.babyphotos.archive.domain.model

data class BabyDetectionResult(
    val containsBaby: Boolean,
    val confidence: Int,
    val reason: String
)
