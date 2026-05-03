package com.babyphotos.archive.domain.model

enum class ClassificationAction {
    AUTO_ADD,
    NEEDS_CONFIRM,
    IGNORE
}

data class ClassificationDecision(
    val photo: ScannedPhoto,
    val detectionResult: BabyDetectionResult,
    val action: ClassificationAction
)

data class ScanSummary(
    val totalScanned: Int,
    val newlyAnalyzed: Int,
    val autoAdded: Int,
    val needsConfirmation: Int,
    val confirmationItems: List<ClassificationDecision>
)

data class PreprocessedImage(
    val originalPath: String,
    val base64Data: String,
    val compressedSize: Int
)
