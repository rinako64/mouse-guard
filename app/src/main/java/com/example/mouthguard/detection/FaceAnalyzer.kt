package com.example.mouthguard.detection

import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

class FaceAnalyzer(
    private val onResult: (MouthDetectionResult?) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "MouthGuard"
        private const val THROTTLE_MS = 500L
    }

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .build()
    )

    private val mouthDetector = MouthDetector()
    private var lastAnalyzedTimestamp = 0L

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastAnalyzedTimestamp < THROTTLE_MS) {
            imageProxy.close()
            return
        }
        lastAnalyzedTimestamp = now

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    onResult(null)
                } else {
                    val result = mouthDetector.detect(faces[0])
                    if (result != null) {
                        Log.d(TAG, "Mouth ratio: ${result.ratio}, open: ${result.isOpen}")
                    }
                    onResult(result)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Face detection failed", e)
                onResult(null)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}
