package cn.starcloud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import cn.starcloud.ui.theme.StarcloudFontFamily
import com.arkivanov.decompose.ComponentContext

fun init() {
    println(
        "sarasa font classpath check: " +
            Thread.currentThread().contextClassLoader.getResource("fonts/SarasaUiSC-Regular.ttf")
    )
}


@Composable
fun AppLayout() {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Card3s
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(5f),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
            }
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .weight(2f),
                color = MaterialTheme.colorScheme.surface
            ) {

            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
        }
    }
}

fun main() = application {
    init()

    Window(
        onCloseRequest = ::exitApplication,
        title = "Starcloud Launcher",
        state = rememberWindowState(size = DpSize(10000.dp, 8000.dp)),
    ) {
        MaterialTheme(
            typography = Typography().withFont(StarcloudFontFamily()),
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                AppLayout()
            }
        }
    }
}

private fun Typography.withFont(family: FontFamily): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)
