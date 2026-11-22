package cn.starcloud.ui.app

data class LauncherState(
    val versions: List<String> = emptyList(),
    val selectedVersion: String? = null,
    val downloadProgress: Float = 0f,
    val isLaunching: Boolean = false,
    val message: String? = null,
)