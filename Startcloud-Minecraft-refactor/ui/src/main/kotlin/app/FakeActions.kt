package cn.starcloud.ui.app

/**
 * 骨架阶段使用：让 Main.kt 在未接 core 前也能演示交互。
 * 接 core 后删除，被 LauncherBridge 替代。
 */
class FakeActions(
    private val onState: (LauncherState) -> Unit,
) : LauncherActions {

    private var current = LauncherState()

    override fun refresh() {
        current = current.copy(
            versions = listOf("1.21.1", "1.20.6", "1.20.1", "1.19.4", "1.16.5"),
            message = "已加载 5 个版本（假数据）",
        )
        onState(current)
    }

    override fun selectVersion(version: String) {
        current = current.copy(selectedVersion = version)
        onState(current)
    }

    override fun launch() {
        current = current.copy(isLaunching = true, message = "启动 ${current.selectedVersion} …（假数据）")
        onState(current)
    }
}