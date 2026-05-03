package com.babyphotos.archive.domain.recognizer

import com.babyphotos.archive.domain.model.BabyDetectionResult

interface BabyRecognizer {
    suspend fun recognize(base64Image: String): Result<BabyDetectionResult>
}
