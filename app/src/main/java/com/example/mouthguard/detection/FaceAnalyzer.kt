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
        private const val THROTTLE_MS = 200L
    }

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
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

        // CameraXが算出した正しい回転値をそのまま使う
        val rotation = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                Log.d(TAG, "Faces: ${faces.size}, rotation: $rotation")
                if (faces.isEmpty()) {
                    onResult(null)
                } else {
                    val result = mouthDetector.detect(faces[0])
                    if (result != null) {
                        Log.d(TAG, "avgR=${result.ratio} maxR=${result.maxRatio} open=${result.isOpen}")
                    } else {
                        Log.d(TAG, "Contour not available")
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
