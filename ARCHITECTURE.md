# ARCHITECTURE.md

## 1. What This Project Is / 项目定位

The Java refactor line of Starcloud Minecraft Launcher, developed in parallel with the existing .NET + WinUI 3 implementation. It does not replace or compete with the existing repository.

本项目是 Starcloud Minecraft Launcher 的 Java 重构线，与现有 .NET + WinUI 3 实现并行开发。它不替代、也不与现有仓库竞争。

The goal is not to translate old code, but to build a structure that is understandable, verifiable, and handover-ready. The old repository suffered from no one truly owning the core, multiple implementations coexisting, and build artifacts being committed. This document exists to ensure the new repository does not repeat those mistakes from day one.

目标不是翻译旧代码，而是构建一个可理解、可验证、可交接的结构。旧仓库的教训是：核心无人真正负责、多套实现并存、构建产物被提交进版本库。本文档的目的，是确保新仓库从第一天起就不重蹈覆辙。

## 2. Modules and Dependency Direction / 模块与依赖方向

```
android ──► core ◄── desktop
```

| Module / 模块 | Responsibility / 职责 | Must Not Do / 禁止事项 |
| --- | --- | --- |
| core | Platform-independent logic: auth, download, verification, version management, launch argument building | Must not import `javafx.*`, `android.*`, `java.awt.*`; must not depend on any platform module |
| desktop | JavaFX desktop UI and platform integration | Must not depend on android; must not put business logic in the UI layer |
| android | Kotlin Android UI and platform integration (not yet in the build) | Must not depend on desktop |

| 模块 | 职责 | 禁止事项 |
| --- | --- | --- |
| core | 平台无关逻辑：认证、下载、校验、版本管理、启动参数构建 | 不得 import `javafx.*`、`android.*`、`java.awt.*`；不得依赖任何平台模块 |
| desktop | JavaFX 桌面 UI 与平台集成 | 不得依赖 android；不得把业务逻辑放进 UI 层 |
| android | Kotlin Android UI 与平台集成（尚未纳入构建） | 不得依赖 desktop |

The dependency direction is `android → core ← desktop`. core does not know who uses it; desktop and android do not know each other exists.

依赖方向为 `android → core ← desktop`。core 不知道谁在使用它；desktop 与 android 互不知晓对方存在。

This rule is enforced by `ArchitectureTest` (ArchUnit) in CI. Violations block merging. It is a physical constraint, not a suggestion.

该规则由 CI 中的 `ArchitectureTest`（ArchUnit）强制执行，违规即阻断合并。这是物理约束，不是建议。

## 3. Package Structure of core / core 的包结构

Organize by feature, not by layer.

按功能组织，不按层组织。

```
cn.starcloud.core
├── LauncherCore.java     module entry: assemble services, expose interfaces, manage lifecycle
├── auth/                 auth: offline UUID, Microsoft, Yggdrasil
├── download/             download: Downloader, DownloadException
├── version/              DTOs for version manifest and version JSON
├── common/               cross-feature: OSEnum, path utilities
└── (launch/ to be added later)
```

Do not create layer-based packages such as `intf/`, `impl/`, `dto/`, `exception/`. The interface, implementation, DTOs, and exceptions of a feature all live in the same feature package. Reason: changing a feature means jumping within one package, not searching across four packages.

禁止创建 `intf/`、`impl/`、`dto/`、`exception/` 这类按层划分的包。一个功能的接口、实现、DTO、异常都放在同一个功能包内。理由：修改一个功能时只需在一个包内跳转，而不是横跨四个包搜索。

## 4. The Role of LauncherCore / LauncherCore 的职责

LauncherCore is the entry class of the core module. It has exactly three responsibilities:

LauncherCore 是 core 模块的入口类，只有三项职责：

1. Assemble: instantiate services in the constructor and wire them together.
2. Expose: return interface types through methods like `downloader()` and `manifest()`.
3. Manage lifecycle: own `HttpClient` and thread pools, responsible for creation and shutdown.

1. 装配：在构造函数中实例化服务并完成接线。
2. 暴露：通过 `downloader()`、`manifest()` 等方法返回接口类型。
3. 管理生命周期：持有 `HttpClient` 与线程池，负责创建与关闭。

LauncherCore contains no business logic. Its methods only return. No `if`, no `for`, no network requests, no file I/O. If working code appears in its methods, business logic has been placed in the wrong location.

LauncherCore 不包含任何业务逻辑。其方法只做返回——没有 `if`、没有 `for`、没有网络请求、没有文件 I/O。一旦方法里出现"干活的代码"，说明业务逻辑放错了位置。

LauncherCore is a `final` class with a private constructor, created via `LauncherCore.init(config)`. It does not need an interface like `IAuth` — it is not something with multiple implementations, but the single assembly entry point.

LauncherCore 是 `final` 类，构造函数私有，通过 `LauncherCore.init(config)` 创建。它不需要 `IAuth` 之类的接口——它不是有多种实现的东西，而是唯一的装配入口。

## 5. Authentication Design / 认证设计

### 5.1 Three Auth Methods, Shared State Container, No Shared Login Signature / 三种认证方式、共享状态容器、不共享登录签名

- Offline: `OfflineAuth.login(username)`, synchronous, completes instantly.
- Yggdrasil: `YggdrasilAuth.login(serverUrl, username, password)`, one HTTP request.
- Microsoft: `MicrosoftAuth.startDeviceAuth()` + `pollForToken(deviceCode)`, two steps, requires polling.

- 离线：`OfflineAuth.login(username)`，同步，立即完成。
- Yggdrasil：`YggdrasilAuth.login(serverUrl, username, password)`，一次 HTTP 请求。
- Microsoft：`MicrosoftAuth.startDeviceAuth()` + `pollForToken(deviceCode)`，两步，需要轮询。

All three return an `AuthHandle`, but they do not share a login signature. Microsoft is two steps, offline is one step, and Yggdrasil is another kind of one step. Forcing them into one signature only produces an empty, awkward interface.

三者都返回 `AuthHandle`，但不共享登录签名。Microsoft 是两步，离线是一步，Yggdrasil 是另一种一步。强行统一签名，只会产出一个空洞而别扭的接口。

### 5.2 AuthHandle Is a State Container, Not Login Logic / AuthHandle 是状态容器，不是登录逻辑

`AuthHandle` is responsible for storing state, notifying listeners, delivering results, and supporting cancellation. It is not responsible for how to log in.

`AuthHandle` 负责存储状态、通知监听者、投递结果、支持取消。它不负责"如何登录"。

- `OfflineAuth`: `new DefaultAuthHandle()` → compute UUID → `update(Success)`.
- `YggdrasilAuth`: `new DefaultAuthHandle()` → send request → `update(Success)` or `update(Failed)`.
- `MicrosoftAuth`: `new DefaultAuthHandle()` → `update(WaitingForUser)` → poll → `update(Success)` or `update(Failed)`.

- `OfflineAuth`：`new DefaultAuthHandle()` → 计算 UUID → `update(Success)`。
- `YggdrasilAuth`：`new DefaultAuthHandle()` → 发请求 → `update(Success)` 或 `update(Failed)`。
- `MicrosoftAuth`：`new DefaultAuthHandle()` → `update(WaitingForUser)` → 轮询 → `update(Success)` 或 `update(Failed)`。

### 5.3 AuthState Is a Sealed Interface / AuthState 是密封接口

```java
public sealed interface AuthState
        permits AuthState.Pending, AuthState.WaitingForUser, AuthState.Success, AuthState.Failed {

    record Pending() implements AuthState {}

    record WaitingForUser(String instruction, String url, String code) implements AuthState {}

    record Success(AuthSession session) implements AuthState {}

    record Failed(AuthFailureReason reason, Throwable cause) implements AuthState {}
}
```

The switch over `AuthState` is exhaustive at compile time. Adding a new state forces every switch to handle it.

对 `AuthState` 的 `switch` 在编译期即穷尽。新增状态时，所有 switch 都会被强制要求处理。

### 5.4 AuthFailureReason Carries the Reason, Not the User Message / AuthFailureReason 携带原因码，而非用户文案

```java
public enum AuthFailureReason {
    INVALID_USERNAME_LENGTH,
    INVALID_USERNAME_CHARSET,
    NETWORK_ERROR,
    DEVICE_CODE_EXPIRED,
    INVALID_CREDENTIALS,
    CANCELLED,
    UNKNOWN
}
```

core returns a reason code. desktop and android translate the reason code into user-facing text. core has zero i18n dependency.

core 返回原因码，desktop 与 android 负责把原因码翻译成用户可见文案。core 零 i18n 依赖。

### 5.5 DefaultAuthHandle Is the Generic Implementation / DefaultAuthHandle 是通用实现

`DefaultAuthHandle` implements state storage, listener notification, result completion, and cancellation. All three auth methods use it. It is not the default login method; it is the shared state container.

`DefaultAuthHandle` 实现状态存储、监听通知、结果完成与取消。三种认证方式共用它。它不是"默认登录方式"，而是共享的状态容器。

`cancel()` changes the state to `Failed(CANCELLED)` and notifies listeners. After cancellation, `update` is ignored.

`cancel()` 将状态改为 `Failed(CANCELLED)` 并通知监听者。取消之后，`update` 被忽略。

## 6. Logging / 日志

core uses the SLF4J API. desktop and android provide the implementation (Logback on desktop). core does not bind to any logging implementation.

core 使用 SLF4J API。desktop 与 android 提供实现（桌面端用 Logback）。core 不绑定任何日志实现。

Log messages are in English. The readers of logs are developers, not users. User-facing text is handled by desktop and android through `AuthFailureReason`.

日志消息用英文。日志的读者是开发者，不是用户。用户可见文案由 desktop / android 依据 `AuthFailureReason` 处理。

Never log tokens, passwords, or credentials. Log the failure reason and the URL, not the secret.

绝不打日志记录 token、密码或凭据。记录失败原因与 URL，不记录秘密。

## 7. Testing Rules / 测试规则

- Every new public API in core must have at least one test.
- Pure functions (UUID generation, version comparison, hash verification, argument building) must be tested directly, including boundary cases.
- Tests must not depend on network access, the file system outside `build/`, the current time zone, or a display.
- A test that cannot fail is not a test. If a test passes no matter what the implementation does, rewrite it or delete it.
- Critical paths (credentials/login, download, verification, JVM launch) must cover failure modes, not just the happy path.

- core 中每个新增的 public API 至少有一个测试。
- 纯函数（UUID 生成、版本比较、哈希校验、参数构建）必须直接测试，包括边界情况。
- 测试不得依赖网络、`build/` 之外的文件系统、当前时区或显示器。
- 一个不可能失败的测试不是测试。若测试无论实现如何都通过，要么重写，要么删除。
- 关键路径（凭据/登录、下载、校验、JVM 启动）必须覆盖失败模式，而不只是 happy path。

## 8. Build and Packaging / 构建与打包

| Operation / 操作 | Command / 命令 |
| --- | --- |
| Build / 构建 | `./gradlew build` |
| Run desktop / 运行桌面端 | `./gradlew :desktop:run` |
| Run tests / 运行测试 | `./gradlew test` |
| Package for current platform / 打包当前平台 | `./gradlew packageApp` |

`jpackage` cannot cross-build. Windows artifacts must be built on Windows, or in CI on a Windows runner.

`jpackage` 不能交叉构建。Windows 产物必须在 Windows 上构建，或在 CI 的 Windows runner 上构建。

core requires JDK 21. The Gradle wrapper is pinned.

core 要求 JDK 21。Gradle wrapper 已固定版本。

## 9. Forbidden Practices / 禁止行为

- Do not import `javafx.*`, `android.*`, `java.awt.*` in core.
- Do not let desktop and android depend on each other.
- Do not put business logic in LauncherCore.
- Do not use `Map<String, String>` to represent nested structures. Use named `record`s.
- Do not name interfaces with `Intf` or implementations with `Impl`.
- Do not start field names with uppercase.
- Do not use `System.out.println`. Use SLF4J.
- Do not log tokens, passwords, or credentials.
- Do not perform blocking operations on the JavaFX Application Thread.
- Do not commit code with placeholder comments such as `TODO: implement`.
- Do not introduce third-party dependencies without human review and justification in the commit message.

- 在 core 中 import `javafx.*`、`android.*`、`java.awt.*`。
- 让 desktop 与 android 互相依赖。
- 在 LauncherCore 中放业务逻辑。
- 用 `Map<String, String>` 表示嵌套结构——用命名的 `record`。
- 用 `Intf` 命名接口，或用 `Impl` 命名实现。
- 字段名以大写字母开头。
- 使用 `System.out.println`——用 SLF4J。
- 打日志记录 token、密码或凭据。
- 在 JavaFX Application Thread 上执行阻塞操作。
- 提交带 `TODO: implement` 之类占位注释的代码。
- 未经人工评审并在提交信息中说明理由，就引入第三方依赖。

## 10. Why These Rules Exist / 规则背后的原因

These rules are not style preferences. Each one addresses a specific failure mode observed in the old repository:

这些规则不是风格偏好。每一条都针对旧仓库中实际观察到的失败模式：

| Rule / 规则 | Failure Mode in the Old Repository / 旧仓库的失败模式 |
| --- | --- |
| Zero platform dependencies in core / core 零平台依赖 | Platform code mixed into the core, making it impossible to reuse or test / 平台代码混入 core，导致无法复用、无法测试 |
| Feature-based packages / 按功能分包 | One feature scattered across four layer packages, making changes expensive / 一个功能散落在四个层包里，修改成本高昂 |
| LauncherCore without business logic / LauncherCore 无业务逻辑 | A God Class that was impossible to change safely / 存在一个无法安全修改的上帝类 |
| Sealed AuthState / 密封 AuthState | String-based state, missed cases at runtime / 用字符串表示状态，运行时遗漏分支 |
| Reason codes instead of user text / 原因码而非用户文案 | Hardcoded user-facing strings in the core layer, blocking i18n and reuse / 核心层硬编码用户文案，阻碍 i18n 与复用 |
| English logs / 英文日志 | Mixed-language logs that were hard to search and analyze / 多语言混杂，难以搜索与分析 |
| Tests that can fail / 能失败的测试 | Tests that always passed, providing false confidence / 测试永远通过，提供虚假信心 |

The goal is not elegance. The goal is a codebase that a new contributor can understand, verify, and change without fear.

目标不是优雅，而是一个新贡献者能理解、能验证、能放心修改的代码库。