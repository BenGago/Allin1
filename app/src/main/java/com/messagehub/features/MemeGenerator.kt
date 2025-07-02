package com.messagehub.features

import android.content.Context
import android.graphics.*
import android.util.Log
import com.messagehub.data.EnhancedMessage
import com.messagehub.network.OpenAIService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemeGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val openAIService: OpenAIService
) {
    
    companion object {
        private const val TAG = "MemeGenerator"
    }
    
    private val memeTemplates = listOf(
        MemeTemplate(
            id = "drake",
            name = "Drake Pointing",
            topText = "Top rejection text",
            bottomText = "Bottom approval text",
            templateResource = "drake_template"
        ),
        MemeTemplate(
            id = "distracted_boyfriend",
            name = "Distracted Boyfriend",
            topText = "Boyfriend label",
            bottomText = "Girlfriend vs New girl labels",
            templateResource = "distracted_boyfriend_template"
        ),
        MemeTemplate(
            id = "woman_yelling_cat",
            name = "Woman Yelling at Cat",
            topText = "Woman's angry text",
            bottomText = "Cat's confused response",
            templateResource = "woman_cat_template"
        ),
        MemeTemplate(
            id = "expanding_brain",
            name = "Expanding Brain",
            topText = "Simple idea",
            bottomText = "Galaxy brain idea",
            templateResource = "expanding_brain_template"
        ),
        MemeTemplate(
            id = "this_is_fine",
            name = "This is Fine",
            topText = "Everything is chaos",
            bottomText = "This is fine",
            templateResource = "this_is_fine_template"
        )
    )
    
    suspend fun generateMemeFromConversation(
        messages: List<EnhancedMessage>,
        template: MemeTemplate? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val memeIdea = generateMemeIdea(messages)
            val selectedTemplate = template ?: selectBestTemplate(memeIdea)
            
            val memeTexts = generateMemeTexts(memeIdea, selectedTemplate)
            
            createMemeImage(selectedTemplate, memeTexts)
        } catch (e: Exception) {
            Log.e(TAG, "Meme generation failed", e)
            null
        }
    }
    
    suspend fun generateMemeFromMessage(
        message: EnhancedMessage,
        template: MemeTemplate? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val memeIdea = generateMemeIdeaFromSingleMessage(message)
            val selectedTemplate = template ?: selectBestTemplate(memeIdea)
            
            val memeTexts = generateMemeTexts(memeIdea, selectedTemplate)
            
            createMemeImage(selectedTemplate, memeTexts)
        } catch (e: Exception) {
            Log.e(TAG, "Single message meme generation failed", e)
            null
        }
    }
    
    private suspend fun generateMemeIdea(messages: List<EnhancedMessage>): String {
        val conversationText = messages.takeLast(10).joinToString("\n") { 
            "${it.sender}: ${it.content}" 
        }
        
        val prompt = """
            Analyze this conversation and suggest a funny meme idea:
            
            $conversationText
            
            Create a meme concept that captures the humor or irony in this conversation.
            Focus on relatable situations, contradictions, or funny moments.
            
            Respond with just the meme idea in 1-2 sentences.
        """.trimIndent()
        
        val response = openAIService.generateResponse(prompt)
        return response.choices?.firstOrNull()?.message?.content?.trim() 
            ?: "When you're trying to be funny but it doesn't work"
    }
    
    private suspend fun generateMemeIdeaFromSingleMessage(message: EnhancedMessage): String {
        val prompt = """
            Create a funny meme idea based on this message:
            "${message.content}"
            
            Make it relatable and humorous. Focus on common situations people can relate to.
            
            Respond with just the meme idea in 1-2 sentences.
        """.trimIndent()
        
        val response = openAIService.generateResponse(prompt)
        return response.choices?.firstOrNull()?.message?.content?.trim()
            ?: "When someone sends you a message like this"
    }
    
    private fun selectBestTemplate(memeIdea: String): MemeTemplate {
        // Simple template selection based on keywords
        val idea = memeIdea.lowercase()
        
        return when {
            idea.contains("choice") || idea.contains("prefer") || idea.contains("vs") -> 
                memeTemplates.find { it.id == "drake" }!!
            idea.contains("distract") || idea.contains("tempt") || idea.contains("new") -> 
                memeTemplates.find { it.id == "distracted_boyfriend" }!!
            idea.contains("angry") || idea.contains("argue") || idea.contains("confused") -> 
                memeTemplates.find { it.id == "woman_yelling_cat" }!!
            idea.contains("smart") || idea.contains("brain") || idea.contains("idea") -> 
                memeTemplates.find { it.id == "expanding_brain" }!!
            idea.contains("fine") || idea.contains("chaos") || idea.contains("disaster") -> 
                memeTemplates.find { it.id == "this_is_fine" }!!
            else -> memeTemplates.random()
        }
    }
    
    private suspend fun generateMemeTexts(
        memeIdea: String,
        template: MemeTemplate
    ): MemeTexts {
        val prompt = """
            Create meme text for the "${template.name}" template based on this idea:
            "$memeIdea"
            
            Template format: ${template.topText} / ${template.bottomText}
            
            Generate appropriate text for each part of the meme.
            Keep text short, punchy, and funny.
            Use internet meme language and style.
            
            Format your response as:
            TOP: [top text]
            BOTTOM: [bottom text]
        """.trimIndent()
        
        val response = openAIService.generateResponse(prompt)
        val content = response.choices?.firstOrNull()?.message?.content ?: ""
        
        return parseMemeTexts(content, template)
    }
    
    private fun parseMemeTexts(response: String, template: MemeTemplate): MemeTexts {
        val lines = response.split("\n")
        var topText = ""
        var bottomText = ""
        
        lines.forEach { line ->
            when {
                line.startsWith("TOP:", ignoreCase = true) -> 
                    topText = line.substring(4).trim()
                line.startsWith("BOTTOM:", ignoreCase = true) -> 
                    bottomText = line.substring(7).trim()
            }
        }
        
        // Fallback if parsing fails
        if (topText.isEmpty() || bottomText.isEmpty()) {
            topText = "When you try to make a meme"
            bottomText = "But the AI doesn't cooperate"
        }
        
        return MemeTexts(topText, bottomText)
    }
    
    private fun createMemeImage(
        template: MemeTemplate,
        texts: MemeTexts
    ): String {
        // Create a simple text-based meme since we don't have actual image templates
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Background
        canvas.drawColor(Color.WHITE)
        
        // Paint for text
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 48f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        
        // Draw template name
        paint.textSize = 32f
        paint.color = Color.GRAY
        canvas.drawText(template.name, 400f, 50f, paint)
        
        // Draw top text
        paint.textSize = 48f
        paint.color = Color.BLACK
        drawMultilineText(canvas, texts.topText, 400f, 150f, paint, 700)
        
        // Draw bottom text
        drawMultilineText(canvas, texts.bottomText, 400f, 450f, paint, 700)
        
        // Save to file
        val fileName = "meme_${System.currentTimeMillis()}.png"
        val file = File(context.getExternalFilesDir(null), fileName)
        
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        
        return file.absolutePath
    }
    
    private fun drawMultilineText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        paint: Paint,
        maxWidth: Int
    ) {
        val words = text.split(" ")
        var line = ""
        var currentY = y
        
        for (word in words) {
            val testLine = if (line.isEmpty()) word else "$line $word"
            val bounds = Rect()
            paint.getTextBounds(testLine, 0, testLine.length, bounds)
            
            if (bounds.width() > maxWidth && line.isNotEmpty()) {
                canvas.drawText(line, x, currentY, paint)
                line = word
                currentY += paint.textSize + 10
            } else {
                line = testLine
            }
        }
        
        if (line.isNotEmpty()) {
            canvas.drawText(line, x, currentY, paint)
        }
    }
    
    fun getAvailableTemplates(): List<MemeTemplate> = memeTemplates
}

data class MemeTemplate(
    val id: String,
    val name: String,
    val topText: String,
    val bottomText: String,
    val templateResource: String
)

data class MemeTexts(
    val topText: String,
    val bottomText: String
)
