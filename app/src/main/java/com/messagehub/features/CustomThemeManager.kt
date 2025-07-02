package com.messagehub.features

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.google.gson.Gson
import com.messagehub.data.PersonalityProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomThemeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("custom_themes", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    private val _currentTheme = MutableStateFlow(getDefaultTheme())
    val currentTheme: StateFlow<CustomTheme> = _currentTheme.asStateFlow()
    
    private val _availableThemes = MutableStateFlow(getBuiltInThemes())
    val availableThemes: StateFlow<List<CustomTheme>> = _availableThemes.asStateFlow()
    
    fun generateThemeFromPersonality(personality: PersonalityProfile): CustomTheme {
        val baseColors = when (personality.energyLevel) {
            "high" -> HighEnergyColors()
            "low" -> CalmColors()
            else -> BalancedColors()
        }
        
        val accentColor = when (personality.humorType) {
            "playful" -> Color(0xFF4CAF50) // Green
            "sarcastic" -> Color(0xFF9C27B0) // Purple
            "sweet" -> Color(0xFFE91E63) // Pink
            else -> Color(0xFF2196F3) // Blue
        }
        
        return CustomTheme(
            id = "personality_${System.currentTimeMillis()}",
            name = "${personality.communicationStyle.capitalize()} ${personality.energyLevel.capitalize()}",
            description = "Generated from your personality",
            primaryColor = accentColor,
            backgroundColor = baseColors.background,
            surfaceColor = baseColors.surface,
            isGenerated = true,
            personalityBased = true
        )
    }
    
    fun applyTheme(theme: CustomTheme) {
        _currentTheme.value = theme
        saveCurrentTheme(theme)
    }
    
    fun createCustomTheme(
        name: String,
        primaryColor: Color,
        backgroundColor: Color,
        surfaceColor: Color
    ): CustomTheme {
        val theme = CustomTheme(
            id = "custom_${System.currentTimeMillis()}",
            name = name,
            description = "Custom theme",
            primaryColor = primaryColor,
            backgroundColor = backgroundColor,
            surfaceColor = surfaceColor,
            isGenerated = false,
            personalityBased = false
        )
        
        val themes = _availableThemes.value.toMutableList()
        themes.add(theme)
        _availableThemes.value = themes
        saveCustomThemes(themes.filter { !it.isBuiltIn })
        
        return theme
    }
    
    private fun getBuiltInThemes(): List<CustomTheme> {
        return listOf(
            CustomTheme(
                id = "default",
                name = "Default",
                description = "Classic blue theme",
                primaryColor = Color(0xFF2196F3),
                backgroundColor = Color(0xFFF5F5F5),
                surfaceColor = Color.White,
                isBuiltIn = true
            ),
            CustomTheme(
                id = "romantic",
                name = "Romantic",
                description = "Soft pink and red tones",
                primaryColor = Color(0xFFE91E63),
                backgroundColor = Color(0xFFFCE4EC),
                surfaceColor = Color(0xFFF8BBD9),
                isBuiltIn = true
            ),
            CustomTheme(
                id = "nature",
                name = "Nature",
                description = "Green and earth tones",
                primaryColor = Color(0xFF4CAF50),
                backgroundColor = Color(0xFFE8F5E8),
                surfaceColor = Color(0xFFC8E6C9),
                isBuiltIn = true
            ),
            CustomTheme(
                id = "sunset",
                name = "Sunset",
                description = "Orange and purple gradient",
                primaryColor = Color(0xFFFF9800),
                backgroundColor = Color(0xFFFFF3E0),
                surfaceColor = Color(0xFFFFE0B2),
                isBuiltIn = true
            ),
            CustomTheme(
                id = "ocean",
                name = "Ocean",
                description = "Deep blue and teal",
                primaryColor = Color(0xFF00BCD4),
                backgroundColor = Color(0xFFE0F2F1),
                surfaceColor = Color(0xFFB2DFDB),
                isBuiltIn = true
            ),
            CustomTheme(
                id = "dark_mode",
                name = "Dark Mode",
                description = "Dark theme for night usage",
                primaryColor = Color(0xFF2196F3),
                backgroundColor = Color(0xFF121212),
                surfaceColor = Color(0xFF1E1E1E),
                isBuiltIn = true,
                isDark = true
            )
        )
    }
    
    private fun getDefaultTheme(): CustomTheme {
        val savedTheme = prefs.getString("current_theme", null)
        return if (savedTheme != null) {
            gson.fromJson(savedTheme, CustomTheme::class.java)
        } else {
            getBuiltInThemes().first()
        }
    }
    
    private fun saveCurrentTheme(theme: CustomTheme) {
        prefs.edit().putString("current_theme", gson.toJson(theme)).apply()
    }
    
    private fun saveCustomThemes(themes: List<CustomTheme>) {
        prefs.edit().putString("custom_themes", gson.toJson(themes)).apply()
    }
    
    private data class ThemeColors(
        val background: Color,
        val surface: Color
    )
    
    private fun HighEnergyColors() = ThemeColors(
        background = Color(0xFFFFF9C4), // Light yellow
        surface = Color(0xFFFFF59D)
    )
    
    private fun CalmColors() = ThemeColors(
        background = Color(0xFFE3F2FD), // Light blue
        surface = Color(0xFFBBDEFB)
    )
    
    private fun BalancedColors() = ThemeColors(
        background = Color(0xFFF5F5F5), // Light gray
        surface = Color.White
    )
}

data class CustomTheme(
    val id: String,
    val name: String,
    val description: String,
    val primaryColor: Color,
    val backgroundColor: Color,
    val surfaceColor: Color,
    val isBuiltIn: Boolean = false,
    val isGenerated: Boolean = false,
    val personalityBased: Boolean = false,
    val isDark: Boolean = false
)
