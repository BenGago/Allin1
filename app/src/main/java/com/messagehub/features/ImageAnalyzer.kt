package com.messagehub.features

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.messagehub.data.Attachment
import com.messagehub.data.ImageAnalysisResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "ImageAnalyzer"
    }
    
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
    )
    
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val imageLabeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    
    suspend fun analyzeImage(attachment: Attachment): ImageAnalysisResult = withContext(Dispatchers.IO) {
        try {
            val bitmap = loadBitmapFromUrl(attachment.url)
            if (bitmap == null) {
                return@withContext ImageAnalysisResult(
                    hasError = true,
                    errorMessage = "Failed to load image"
                )
            }
            
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            
            // Run all analyses in parallel
            val faceAnalysis = analyzeFaces(inputImage)
            val textAnalysis = analyzeText(inputImage)
            val labelAnalysis = analyzeLabels(inputImage)
            val nsfwAnalysis = analyzeNSFW(bitmap)
            
            val result = ImageAnalysisResult(
                faceCount = faceAnalysis.faceCount,
                hasSmiling = faceAnalysis.hasSmiling,
                detectedText = textAnalysis.text,
                isMeme = textAnalysis.isMeme,
                labels = labelAnalysis,
                isNSFW = nsfwAnalysis,
                suggestedResponse = generateSuggestedResponse(faceAnalysis, textAnalysis, labelAnalysis, nsfwAnalysis),
                confidence = calculateOverallConfidence(faceAnalysis, textAnalysis, labelAnalysis)
            )
            
            Log.d(TAG, "Image analysis completed: $result")
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "Image analysis failed", e)
            ImageAnalysisResult(
                hasError = true,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }
    
    private suspend fun analyzeFaces(image: InputImage): FaceAnalysisResult {
        return try {
            val faces = faceDetector.process(image).await()
            
            val faceCount = faces.size
            val hasSmiling = faces.any { face ->
                face.smilingProbability?.let { it > 0.7f } ?: false
            }
            
            FaceAnalysisResult(
                faceCount = faceCount,
                hasSmiling = hasSmiling,
                confidence = if (faces.isNotEmpty()) 0.9f else 0.0f
            )
        } catch (e: Exception) {
            Log.e(TAG, "Face detection failed", e)
            FaceAnalysisResult()
        }
    }
    
    private suspend fun analyzeText(image: InputImage): TextAnalysisResult {
        return try {
            val visionText = textRecognizer.process(image).await()
            val detectedText = visionText.text
            
            val isMeme = detectMemePatterns(detectedText)
            
            TextAnalysisResult(
                text = detectedText,
                isMeme = isMeme,
                confidence = if (detectedText.isNotEmpty()) 0.8f else 0.0f
            )
        } catch (e: Exception) {
            Log.e(TAG, "Text recognition failed", e)
            TextAnalysisResult()
        }
    }
    
    private suspend fun analyzeLabels(image: InputImage): List<String> {
        return try {
            val labels = imageLabeler.process(image).await()
            labels.filter { it.confidence > 0.7f }
                .map { it.text }
                .take(5)
        } catch (e: Exception) {
            Log.e(TAG, "Image labeling failed", e)
            emptyList()
        }
    }
    
    private fun analyzeNSFW(bitmap: Bitmap): Boolean {
        // Simple NSFW detection based on skin tone analysis
        // In production, you'd use a proper NSFW detection model
        return try {
            val skinPixelCount = countSkinPixels(bitmap)
            val totalPixels = bitmap.width * bitmap.height
            val skinRatio = skinPixelCount.toFloat() / totalPixels
            
            // If more than 30% skin-colored pixels, flag as potentially NSFW
            skinRatio > 0.3f
        } catch (e: Exception) {
            Log.e(TAG, "NSFW analysis failed", e)
            false
        }
    }
    
    private fun countSkinPixels(bitmap: Bitmap): Int {
        var skinPixels = 0
        val width = bitmap.width
        val height = bitmap.height
        
        // Sample every 10th pixel for performance
        for (x in 0 until width step 10) {
            for (y in 0 until height step 10) {
                val pixel = bitmap.getPixel(x, y)
                if (isSkinColor(pixel)) {
                    skinPixels++
                }
            }
        }
        
        return skinPixels * 100 // Multiply by sampling factor
    }
    
    private fun isSkinColor(pixel: Int): Boolean {
        val red = (pixel shr 16) and 0xFF
        val green = (pixel shr 8) and 0xFF
        val blue = pixel and 0xFF
        
        // Simple skin color detection (HSV-based would be better)
        return red > 95 && green > 40 && blue > 20 &&
                red > green && red > blue &&
                red - green > 15
    }
    
    private fun detectMemePatterns(text: String): Boolean {
        val memeKeywords = listOf(
            "when", "me:", "nobody:", "literally", "pov:", "that moment when",
            "drake pointing", "distracted boyfriend", "woman yelling at cat",
            "stonks", "big brain", "this is fine", "change my mind",
            "surprised pikachu", "expanding brain", "galaxy brain"
        )
        
        val lowercaseText = text.lowercase()
        return memeKeywords.any { keyword ->
            lowercaseText.contains(keyword)
        }
    }
    
    private fun generateSuggestedResponse(
        faceAnalysis: FaceAnalysisResult,
        textAnalysis: TextAnalysisResult,
        labels: List<String>,
        isNSFW: Boolean
    ): String {
        return when {
            isNSFW -> "Whoa there! 😳 That's quite the picture!"
            
            faceAnalysis.faceCount > 0 -> {
                when {
                    faceAnalysis.hasSmiling -> generateCompliment("smiling")
                    faceAnalysis.faceCount == 1 -> generateCompliment("selfie")
                    faceAnalysis.faceCount > 1 -> "Great group photo! 📸 Everyone looks amazing!"
                    else -> generateCompliment("photo")
                }
            }
            
            textAnalysis.isMeme -> {
                val memeResponses = listOf(
                    "LMAO this meme! 😂",
                    "Dead! 💀 This is so accurate",
                    "Why is this so true though? 🤣",
                    "Sending this to everyone! 😆",
                    "Meme game strong! 🔥",
                    "I can't even! 😭😂"
                )
                memeResponses.random()
            }
            
            labels.contains("Food") -> {
                val foodResponses = listOf(
                    "That looks delicious! 🤤",
                    "Now I'm hungry! 😋",
                    "Food goals! 🍽️",
                    "Recipe please! 👨‍🍳",
                    "My mouth is watering! 💧"
                )
                foodResponses.random()
            }
            
            labels.contains("Animal") || labels.contains("Cat") || labels.contains("Dog") -> {
                val animalResponses = listOf(
                    "Aww so cute! 🥰",
                    "I love this! 😍",
                    "Adorable! 🐾",
                    "My heart! 💕",
                    "So precious! ✨"
                )
                animalResponses.random()
            }
            
            labels.contains("Nature") || labels.contains("Sky") || labels.contains("Flower") -> {
                val natureResponses = listOf(
                    "Beautiful view! 🌅",
                    "So peaceful! 🌿",
                    "Nature is amazing! 🌸",
                    "Gorgeous! 📸",
                    "Love this scenery! 🏞️"
                )
                natureResponses.random()
            }
            
            else -> {
                val genericResponses = listOf(
                    "Nice pic! 📸",
                    "Cool! ✨",
                    "Love it! 😊",
                    "Thanks for sharing! 💕",
                    "Awesome! 👍"
                )
                genericResponses.random()
            }
        }
    }
    
    private fun generateCompliment(type: String): String {
        val compliments = when (type) {
            "smiling" -> listOf(
                "Your smile is everything! 😍",
                "That smile though! 🥰",
                "You look so happy! 😊",
                "Beautiful smile! ✨",
                "Smiling looks good on you! 💕"
            )
            "selfie" -> listOf(
                "Looking good! 🔥",
                "Gorgeous! 😍",
                "You're glowing! ✨",
                "Stunning as always! 💖",
                "Camera loves you! 📸"
            )
            else -> listOf(
                "Great photo! 📸",
                "Love this! 💕",
                "You look amazing! ✨",
                "Beautiful! 😍",
                "Perfect shot! 👌"
            )
        }
        return compliments.random()
    }
    
    private fun calculateOverallConfidence(
        faceAnalysis: FaceAnalysisResult,
        textAnalysis: TextAnalysisResult,
        labels: List<String>
    ): Float {
        val confidences = listOfNotNull(
            faceAnalysis.confidence.takeIf { it > 0 },
            textAnalysis.confidence.takeIf { it > 0 },
            if (labels.isNotEmpty()) 0.8f else null
        )
        
        return if (confidences.isNotEmpty()) {
            confidences.average().toFloat()
        } else {
            0.0f
        }
    }
    
    private fun loadBitmapFromUrl(url: String): Bitmap? {
        return try {
            if (url.startsWith("http")) {
                // For network URLs, you'd use Glide or similar
                // For now, return null for network images
                null
            } else {
                // For local files
                val uri = Uri.parse(url)
                val inputStream = context.contentResolver.openInputStream(uri)
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to load bitmap from URL: $url", e)
            null
        }
    }
}

data class FaceAnalysisResult(
    val faceCount: Int = 0,
    val hasSmiling: Boolean = false,
    val confidence: Float = 0f
)

data class TextAnalysisResult(
    val text: String = "",
    val isMeme: Boolean = false,
    val confidence: Float = 0f
)
