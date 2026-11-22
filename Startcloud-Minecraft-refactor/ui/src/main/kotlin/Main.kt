package cn.starcloud.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import cn.starcloud.ui.app.FakeActions
import cn.starcloud.ui.app.LauncherApp
import cn.starcloud.ui.app.LauncherState

fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1100.dp, 700.dp))

    Window(
        onCloseRequest = ::exitApplication,
        title = "Starcloud Launcher",
        state = windowState,
    ) {
        // 骨架阶段：假 state；接 core 时替换成 bridge.state.collectAsState()
        var state by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(LauncherState())
        }
        LauncherApp(state = state, actions = FakeActions { newState -> state = newState })
    }
}