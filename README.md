# Starcloud Launcher — Java 重构线

跨平台 Minecraft 启动器，采用清晰的分层架构重新实现。本仓库是 Java 重构线，与现有 .NET + WinUI 3 实现并行开发。

A cross-platform Minecraft launcher, rebuilt with a clean architecture. This repository is the Java refactor line, developed in parallel with the existing .NET + WinUI 3 implementation.

---

## 目录

- [项目定位](#项目定位)
- [架构](#架构)
- [技术栈](#技术栈)
- [快速开始](#快速开始)
- [开发规范](#开发规范)
- [状态](#状态)
- [许可证](#许可证)

---

## 项目定位

本仓库是 Starcloud Minecraft Launcher 的 Java 重构线，与现有 .NET + WinUI 3 实现并行开发。

### 重构目标

- 将平台无关的核心逻辑（认证、下载、校验、版本管理、启动参数构建）收敛到单一 `core` 模块，消除现有实现中平台层与核心层的隐性耦合。
- 桌面端使用 JavaFX 统一 Windows / Linux 的 UI 与品牌视觉。
- 安卓端使用 Kotlin 原生 UI，共享同一 `core`。
- 保留产品原有能力：微软设备码登录、离线登录、第三方登录（authlib-injector / Yggdrasil）、下载与校验、版本管理、启动。
- 在认证链路上改进旧实现的"令牌不落盘、每次重登"策略，改为"令牌持久化 + 静默刷新"，减少用户重复登录。

现有 .NET + WinUI 3 仓库继续维护，本仓库作为重构线独立推进。

现有仓库中的历史实现（Rust + Tauri、Uno 跨平台路线等）不进入本仓库。本仓库从零建立干净结构，不复制任何旧目录结构。

---

## Project Scope

This repository is the Java refactor line of Starcloud Minecraft Launcher, developed in parallel with the existing .NET + WinUI 3 implementation.

### Refactor Goals

- Consolidate platform-independent core logic (auth, download, verification, version management, launch argument building) into a single `core` module, removing implicit coupling between platform and core layers in the existing implementation.
- Unify Windows / Linux UI and brand visual with JavaFX on desktop.
- Use Kotlin native UI on Android, sharing the same `core`.
- Preserve existing product capabilities: Microsoft device code login, offline login, third-party login (authlib-injector / Yggdrasil), download and verification, version management, launch.
- Improve the authentication strategy from "tokens in memory only, re-login every session" to "persisted tokens + silent refresh", reducing repeated logins for users.

The existing .NET + WinUI 3 repository continues to be maintained. This repository advances independently as the refactor line.

Historical implementations in the existing repository (Rust + Tauri, Uno cross-platform route, etc.) do not enter this repository. This repository is built from a clean structure and does not copy any legacy directory layout.

---

## 架构

```text
android ──► core ◄── desktop
```

| 模块 | 职责 |
| --- | --- |
| `core` | 平台无关的核心逻辑：认证、下载、校验、版本管理、启动参数构建 |
| `desktop` | 桌面端 UI 与平台集成，基于 JavaFX |
| `android` | 安卓端 UI 与平台集成，基于 Kotlin（尚未纳入构建） |

### 依赖规则

- `core` 零平台依赖，不得引用 `javafx.*`、`android.*`、`java.awt.*` 或任何操作系统特定 API。
- `desktop` 与 `android` 只依赖 `core`，互不依赖。
- 没有任何模块依赖 `desktop` 或 `android`。
- 规则由 CI 检查，违反即阻断合并。

---

## Architecture

```text
android ──► core ◄── desktop
```

| Module | Responsibility |
| --- | --- |
| `core` | Platform-independent core logic: auth, download, verification, version management, launch argument building |
| `desktop` | Desktop UI and platform integration, built on JavaFX |
| `android` | Android UI and platform integration, built on Kotlin (not yet in the build) |

### Dependency Rules

- `core` has zero platform dependencies. It must not reference `javafx.*`, `android.*`, `java.awt.*`, or any OS-specific API.
- `desktop` and `android` depend only on `core` and not on each other.
- Nothing depends on `desktop` or `android`.
- These rules are enforced in CI. Violations block merging.

---

## 技术栈

| 层 | 技术 |
| --- | --- |
| 构建 | Gradle (Kotlin DSL) + Wrapper |
| 语言 | Java 21 |
| 桌面 UI | JavaFX 21 |
| 安卓 UI | Kotlin + Android SDK（规划中） |
| JSON | Gson |
| 测试 | JUnit 5（仅 `core`） |
| 打包 | `jlink` + `jpackage` |

### 版本要求

- JDK 21（构建与运行所需）。
- Gradle wrapper 已锁定，不需要本地安装 Gradle。

---

## Tech Stack

| Layer | Technology |
| --- | --- |
| Build | Gradle (Kotlin DSL) + Wrapper |
| Language | Java 21 |
| Desktop UI | JavaFX 21 |
| Android UI | Kotlin + Android SDK (planned) |
| JSON | Gson |
| Testing | JUnit 5 (`core` only) |
| Packaging | `jlink` + `jpackage` |

### Version Requirements

- JDK 21 (required for build and run).
- Gradle wrapper is pinned; no local Gradle installation needed.

---

## 快速开始

### 构建

```bash
./gradlew build
```

### 运行桌面端

```bash
./gradlew :desktop:run
```

### 运行测试

```bash
./gradlew test
```

### 打包当前平台

```bash
./gradlew packageApp
```

产物位于 `desktop/build/dist/Starcloud/`，运行方式：

```bash
./desktop/build/dist/Starcloud/bin/Starcloud
```

> `jpackage` 不支持交叉打包。Windows 产物必须在 Windows 上构建，或在 CI 的 Windows runner 上构建。

---

## Quick Start

### Build

```bash
./gradlew build
```

### Run Desktop

```bash
./gradlew :desktop:run
```

### Run Tests

```bash
./gradlew test
```

### Package for Current Platform

```bash
./gradlew packageApp
```

Output is written to `desktop/build/dist/Starcloud/`. Run it with:

```bash
./desktop/build/dist/Starcloud/bin/Starcloud
```

> `jpackage` cannot cross-build. Windows artifacts must be built on Windows, or in CI on a Windows runner.

---

## 开发规范

提交信息格式、PR 规范、架构规则、测试规则、评审规则、AI 生成代码限制，均见 `CONTRIBUTING.md`。

---

## Contributing

Commit message format, PR rules, architecture rules, testing rules, review rules, and AI-generated code restrictions are documented in `CONTRIBUTING.md`.

---

## 状态

- [x] Gradle 多模块骨架
- [x] `core` 零平台依赖约束
- [x] JavaFX 桌面端可运行
- [x] `jlink` + `jpackage` 打包链路
- [ ] 认证链路（离线 UUID、Microsoft 登录、第三方登录）
- [ ] 下载与校验
- [ ] 版本管理与启动
- [ ] 安卓端
- [ ] CI（构建、测试、依赖规则检查、跨平台打包）

---

## Status

- [x] Gradle multi-module skeleton
- [x] `core` zero platform dependency constraint
- [x] Runnable JavaFX desktop application
- [x] `jlink` + `jpackage` packaging pipeline
- [ ] Auth pipeline (offline UUID, Microsoft login, third-party login)
- [ ] Download and verification
- [ ] Version management and launch
- [ ] Android module
- [ ] CI (build, test, dependency rule check, cross-platform packaging)

---

## 许可证

本项目采用 MIT 许可证，并附带署名条款。完整条款见 `LICENSE`。

---

## License

This project is licensed under MIT, with an attribution clause. See `LICENSE` for the full terms.