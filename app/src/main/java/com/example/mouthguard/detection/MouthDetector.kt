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
    private val threshold: Float = 0.08f
) {

    fun calculateMouthOpenRatio(face: Face): Float? {
        val upperLipBottom = face.getContour(FaceContour.UPPER_LIP_BOTTOM)?.points
        val lowerLipTop = face.getContour(FaceContour.LOWER_LIP_TOP)?.points

        if (upperLipBottom.isNullOrEmpty() || lowerLipTop.isNullOrEmpty()) return null

        // Use the center point of each contour
        val upperCenter = upperLipBottom[upperLipBottom.size / 2]
        val lowerCenter = lowerLipTop[lowerLipTop.size / 2]

        val mouthGap = Math.abs(lowerCenter.y - upperCenter.y)
        val faceHeight = face.boundingBox.height().toFloat()

        if (faceHeight <= 0f) return null

        return mouthGap / faceHeight
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
