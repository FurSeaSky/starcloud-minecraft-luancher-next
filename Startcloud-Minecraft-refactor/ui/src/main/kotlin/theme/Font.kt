// ui/src/main/kotlin/cn/starcloud/ui/theme/Font.kt
package cn.starcloud.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

@Composable
fun StarcloudFontFamily(): FontFamily {
    val light    = Font(resource = "fonts/SarasaUiSC-Light.ttf",    weight = FontWeight.Light)
    val regular  = Font(resource = "fonts/SarasaUiSC-Regular.ttf",  weight = FontWeight.Normal)
    val semibold = Font(resource = "fonts/SarasaUiSC-SemiBold.ttf", weight = FontWeight.SemiBold)
    val bold     = Font(resource = "fonts/SarasaUiSC-Bold.ttf",     weight = FontWeight.Bold)


    return remember(light, regular, semibold, bold) {
        FontFamily(light, regular, semibold, bold)
    }
}