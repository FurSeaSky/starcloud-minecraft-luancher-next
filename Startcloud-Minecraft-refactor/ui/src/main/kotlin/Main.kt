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

// 简单路由标记
sealed interface Config {
    data object Home : Config
    data object Settings : Config
}

// 根Component
class RootComponent(ctx: ComponentContext) : ComponentContext by ctx

@Composable
fun AppLayout() {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header 顶部通栏
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                "Starcloud Launcher",
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.headlineSmall
            )
        }

        // 中间一行：左固定面板 + 右侧操作区
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // 左侧主面板（固定宽）
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(280.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("左侧面板", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = {}) { Text("首页") }
                    Button(onClick = {}) { Text("设置") }
                }
            }

            // 右侧：路由出口（以后放 Decompose ChildStack）
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("右侧操作区（路由切换在这里）", style = MaterialTheme.typography.titleMedium)
                    Text("页面内容在这里替换")
                }
            }
        }

        // Footer 底部状态栏
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                "就绪 · Starcloud Minecraft Launcher",
                modifier = Modifier.padding(10.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

fun main() = application {
    init()
    val windowState = rememberWindowState(size = DpSize(1100.dp, 700.dp))

    Window(
        onCloseRequest = ::exitApplication,
        title = "Starcloud Launcher",
        state = windowState,
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
