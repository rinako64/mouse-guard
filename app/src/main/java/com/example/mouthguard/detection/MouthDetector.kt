package com.example.mouthguard.detection

import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import kotlin.math.hypot

data class MouthDetectionResult(
    val ratio: Float,
    val maxRatio: Float,
    val isOpen: Boolean,
    val faceRect: Rect
)

class MouthDetector {
    companion object {
        private const val TAG = "MouthGuard"
        // 口幅に対する唇隙間の比率で判定
        // 閉じた口: ratio ≈ 0.00〜0.03（唇が接触、隙間ほぼゼロ）
        // お口ぽかん: ratio ≈ 0.04〜0.08（軽く開いている）
        // 大きく開いた口: ratio ≈ 0.10+（明確な隙間）
        private const val THRESHOLD_AVG = 0.08f   // 平均ギャップの閾値
        private const val THRESHOLD_MAX = 0.10f   // 最大ギャップの閾値（片側だけ開いているケース）
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

        // 上唇内側・下唇内側の中央5点の垂直座標を使って隙間を算出
        val avgGap = computeAverageGap(upperInner, lowerInner, left.x, left.y, perpX, perpY)
        val maxGap = computeMaxGap(upperInner, lowerInner, left.x, left.y, perpX, perpY)

        // 口幅で正規化（顔の大きさ・距離に依存しない）
        val avgRatio = avgGap / mouthWidth
        val maxRatio = maxGap / mouthWidth

        // 平均ギャップ OR 最大ギャップのどちらかが閾値を超えたら「開いている」
        val isOpen = avgRatio > THRESHOLD_AVG || maxRatio > THRESHOLD_MAX
        Log.d(TAG, "avgGap=${"%.1f".format(avgGap)} maxGap=${"%.1f".format(maxGap)} w=${"%.1f".format(mouthWidth)} avgR=${"%.3f".format(avgRatio)} maxR=${"%.3f".format(maxRatio)} open=$isOpen")

        return MouthDetectionResult(
            ratio = avgRatio,
            maxRatio = maxRatio,
            isOpen = isOpen,
            faceRect = face.boundingBox
        )
    }

    /** 中央5点の平均ギャップを算出 */
    private fun computeAverageGap(
        upperPoints: List<android.graphics.PointF>,
        lowerPoints: List<android.graphics.PointF>,
        originX: Float, originY: Float,
        perpX: Float, perpY: Float
    ): Float {
        val upperPerp = averagePerpProjection(upperPoints, originX, originY, perpX, perpY)
        val lowerPerp = averagePerpProjection(lowerPoints, originX, originY, perpX, perpY)
        return maxOf(0f, lowerPerp - upperPerp)
    }

    /** 対応する点ペアの中で最大のギャップを返す */
    private fun computeMaxGap(
        upperPoints: List<android.graphics.PointF>,
        lowerPoints: List<android.graphics.PointF>,
        originX: Float, originY: Float,
        perpX: Float, perpY: Float
    ): Float {
        val uN = upperPoints.size
        val lN = lowerPoints.size
        val uCenter = uN / 2
        val lCenter = lN / 2
        val sampleRange = 2  // 中央±2 = 最大5点
        var maxGap = 0f

        for (offset in -sampleRange..sampleRange) {
            val ui = (uCenter + offset).coerceIn(0, uN - 1)
            val li = (lCenter + offset).coerceIn(0, lN - 1)
            val uPerp = (upperPoints[ui].x - originX) * perpX + (upperPoints[ui].y - originY) * perpY
            val lPerp = (lowerPoints[li].x - originX) * perpX + (lowerPoints[li].y - originY) * perpY
            val gap = lPerp - uPerp
            if (gap > maxGap) maxGap = gap
        }
        return maxGap
    }

    /** 輪郭点リストの中央5点について、口の軸に垂直な成分の平均を返す */
    private fun averagePerpProjection(
        points: List<android.graphics.PointF>,
        originX: Float, originY: Float,
        perpX: Float, perpY: Float
    ): Float {
        val n = points.size
        val center = n / 2
        val from = maxOf(0, center - 2)
        val to = minOf(n - 1, center + 2)
        var sum = 0f
        var count = 0
        for (i in from..to) {
            sum += (points[i].x - originX) * perpX + (points[i].y - originY) * perpY
            count++
        }
        return sum / count
    }
}
