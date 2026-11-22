package cn.starcloud.ui.app

/**
 * UI 与平台层（core bridge）之间的契约。
 * 将来 Android 接入时，实现同一接口即可复用全部 Compose UI。
 */
interface LauncherActions {
    fun refresh()
    fun selectVersion(version: String)
    fun launch()
}