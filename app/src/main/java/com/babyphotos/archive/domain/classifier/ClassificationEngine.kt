package com.babyphotos.archive.domain.classifier

import com.babyphotos.archive.domain.model.BabyDetectionResult
import com.babyphotos.archive.domain.model.ClassificationAction
import com.babyphotos.archive.domain.model.ClassificationDecision
import com.babyphotos.archive.domain.model.ScannedPhoto

class ClassificationEngine(
    private val autoAddThreshold: Int = 80,
    private val confirmThreshold: Int = 50
) {
    fun classify(photo: ScannedPhoto, result: BabyDetectionResult): ClassificationDecision {
        val action = when {
            !result.containsBaby -> ClassificationAction.IGNORE
            result.confidence >= autoAddThreshold -> ClassificationAction.AUTO_ADD
            result.confidence >= confirmThreshold -> ClassificationAction.NEEDS_CONFIRM
            else -> ClassificationAction.IGNORE
        }
        return ClassificationDecision(photo, result, action)
    }

    fun classifyBatch(results: List<Pair<ScannedPhoto, BabyDetectionResult>>): List<ClassificationDecision> {
        return results.map { (photo, result) -> classify(photo, result) }
    }
}
