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

enum class ScanPhase {
    SCANNING_MEDIA,
    ANALYZING,
    CLASSIFYING
}

data class ScanProgress(
    val phase: ScanPhase,
    val current: Int,
    val total: Int
) {
    val fraction: Float get() = if (total > 0) current.toFloat() / total else 0f
    val percent: Int get() = (fraction * 100).toInt()
}
