package com.example.mouthguard.detection

import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import kotlin.math.hypot

data class MouthDetectionResult(
    val ratio: Float,
    val isOpen: Boolean,
    val faceRect: Rect
)

class MouthDetector {
    companion object {
        private const val TAG = "MouthGuard"
        // 口幅に対する唇隙間の比率で判定
        // 閉じた口: ratio ≈ 0.00〜0.05（唇が接触、隙間ほぼゼロ）
        // 開いた口: ratio ≈ 0.10+（明確な隙間）
        private const val THRESHOLD = 0.08f
    }

    fun detect(face: Face): MouthDetectionResult? {
        // 上唇の内側（下端）・下唇の内側（上端）・上唇の外側（上端＝口角の定義用）
        val upperInner = face.getContour(FaceContour.UPPER_LIP_BOTTOM)?.points ?: return null
        val lowerInner = face.getContour(FaceContour.LOWER_LIP_TOP)?.points ?: return null
        val upperOuter = face.getContour(FaceContour.UPPER_LIP_TOP)?.points ?: return null

        if (upperInner.size < 3 || lowerInner.size < 3 || upperOuter.size < 2) return null

        // 口角（上唇外側輪郭の始点と終点）で口の軸を定義
        val left = upperOuter.first()
        val right = upperOuter.last()
        val mouthDx = right.x - left.x
        val mouthDy = right.y - left.y
        val mouthWidth = hypot(mouthDx.toDouble(), mouthDy.toDouble()).toFloat()
        if (mouthWidth < 10f) return null

        // 口の軸に垂直な単位ベクトル（上唇→下唇方向が正）
        val perpX = -mouthDy / mouthWidth
        val perpY = mouthDx / mouthWidth

        // 上唇内側・下唇内側の中央3点の垂直座標を平均して隙間を算出
        val upperPerp = averagePerpProjection(upperInner, left.x, left.y, perpX, perpY)
        val lowerPerp = averagePerpProjection(lowerInner, left.x, left.y, perpX, perpY)

        // 符号付き隙間：正なら口が開いている、0〜負なら閉じている
        val gap = lowerPerp - upperPerp

        // 口幅で正規化（顔の大きさ・距離に依存しない）
        val ratio = gap / mouthWidth

        val isOpen = ratio > THRESHOLD
        Log.d(TAG, "gap=${"%.1f".format(gap)} w=${"%.1f".format(mouthWidth)} ratio=${"%.3f".format(ratio)} open=$isOpen")

        return MouthDetectionResult(
            ratio = ratio,
            isOpen = isOpen,
            faceRect = face.boundingBox
        )
    }

    /** 輪郭点リストの中央3点について、口の軸に垂直な成分の平均を返す */
    private fun averagePerpProjection(
        points: List<android.graphics.PointF>,
        originX: Float, originY: Float,
        perpX: Float, perpY: Float
    ): Float {
        val n = points.size
        val center = n / 2
        val from = maxOf(0, center - 1)
        val to = minOf(n - 1, center + 1)
        var sum = 0f
        var count = 0
        for (i in from..to) {
            sum += (points[i].x - originX) * perpX + (points[i].y - originY) * perpY
            count++
        }
        return sum / count
    }
}
