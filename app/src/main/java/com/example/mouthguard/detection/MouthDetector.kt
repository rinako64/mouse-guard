package com.example.mouthguard.detection

import android.graphics.Rect
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour

data class MouthDetectionResult(
    val ratio: Float,
    val isOpen: Boolean,
    val faceRect: Rect
)

class MouthDetector(
    private val threshold: Float = 0.04f
) {

    fun calculateMouthOpenRatio(face: Face): Float? {
        val upperLipBottom = face.getContour(FaceContour.UPPER_LIP_BOTTOM)?.points
        val lowerLipTop = face.getContour(FaceContour.LOWER_LIP_TOP)?.points

        if (upperLipBottom.isNullOrEmpty() || lowerLipTop.isNullOrEmpty()) return null

        // Use average Y of all points for stability
        val upperAvgY = upperLipBottom.map { it.y }.average().toFloat()
        val lowerAvgY = lowerLipTop.map { it.y }.average().toFloat()

        val mouthGap = Math.abs(lowerAvgY - upperAvgY)

        // Normalize by mouth width instead of face height for better accuracy
        val mouthWidth = Math.abs(upperLipBottom.last().x - upperLipBottom.first().x)
        if (mouthWidth <= 0f) return null

        return mouthGap / mouthWidth
    }

    fun isMouthOpen(face: Face): Boolean {
        val ratio = calculateMouthOpenRatio(face) ?: return false
        return ratio > threshold
    }

    fun detect(face: Face): MouthDetectionResult? {
        val ratio = calculateMouthOpenRatio(face) ?: return null
        return MouthDetectionResult(
            ratio = ratio,
            isOpen = ratio > threshold,
            faceRect = face.boundingBox
        )
    }
}
