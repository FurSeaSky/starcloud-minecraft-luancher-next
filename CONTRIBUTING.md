# Contributing Guide

> This document defines how this project is developed, reviewed, and maintained.
> It applies to everyone who contributes: current maintainers, future
> developers, and community contributors.

---

## 目录 / Table of Contents

- [提交信息格式 / Commit Message Format](#提交信息格式--commit-message-format)
- [架构规则 / Architecture Rules](#架构规则--architecture-rules)
- [构建与运行 / Build and Run](#构建与运行--build-and-run)
- [测试规则 / Testing Rules](#测试规则--testing-rules)
- [评审规则 / Review Rules](#评审规则--review-rules)
- [AI 生成代码 / AI-Generated Code](#ai-生成代码--ai-generated-code)
- [文档规则 / Documentation Rules](#文档规则--documentation-rules)
- [分支与合并 / Branches and Merging](#分支与合并--branches-and-merging)

---

## 提交信息格式 / Commit Message Format

所有提交信息使用以下格式：

```
[type](target)-message
```

| 字段 | 说明 | Description |
| --- | --- | --- |
| `type` | 改动类型 | Type of change |
| `target` | 受影响的模块或区域 | Module or area affected |
| `message` | 一行祈使句，不加句号 | One-line imperative, no trailing period |

### 允许的 `type` / Allowed `type` values

| 值 | 含义 | Meaning |
| --- | --- | --- |
| `init` | 首次提交或项目初始化 | First commit or project bootstrap |
| `feat` | 新功能 | New functionality |
| `fix` | 修复缺陷 | Bug fix |
| `refactor` | 既不修 bug 也不加功能的代码调整 | Code change that neither fixes a bug nor adds a feature |
| `docs` | 仅文档 | Documentation only |
| `test` | 仅测试 | Tests only |
| `build` | 构建系统或依赖变更 | Build system or dependency changes |
| `chore` | 不涉及源码的维护 | Maintenance that does not touch src |

### 允许的 `target` / Allowed `target` values

| 值 | 含义 | Meaning |
| --- | --- | --- |
| `project`, `repo` | 仓库级变更 | Repository-level changes |
| `core` | 核心模块 | The core module |
| `desktop` | 桌面端模块 | The desktop module |
| `android` | 安卓模块（存在时） | The android module (when it exists) |
| `ci` | 持续集成 | Continuous integration |
| `docs` | 文档 | Documentation |

### 示例 / Examples

```text
[init](project)-init project
[feat](core)-add offline uuid generation
[fix](desktop)-handle missing display on startup
[refactor](core)-split auth into device, xbox, minecraft services
[docs](repo)-add contributing guide
[build](desktop)-wire jlink and jpackage tasks
```

> 一次改动涉及多个目标时，选受影响最大的那个。不要叠加类型。一次提交，一个类型。
>
> If a change spans multiple targets, pick the most affected one. Do not stack
> types. One commit, one type.

---

## 架构规则 / Architecture Rules

以下规则是强制的，不是建议。违反规则的改动会被 CI 阻断，不得合并。

The following rules are enforced, not suggested. Changes that violate them are
blocked in CI and must not be merged.

### 依赖方向 / Dependency Direction

```
android ──► core ◄── desktop
```

- `desktop` 和 `android` 依赖 `core`。
- `core` 不依赖任何平台模块。
- `desktop` 与 `android` 互不依赖。
- 没有任何模块依赖 `desktop` 或 `android`。

- `desktop` and `android` depend on `core`.
- `core` depends on no platform module.
- `desktop` and `android` do not depend on each other.
- Nothing depends on `desktop` or `android`.

### `core` 的零平台依赖 / Zero Platform Dependencies in `core`

`core` 必须保持平台无关。禁止出现：

`core` must remain platform-independent. The following are forbidden:

- `javafx.*`
- `android.*`
- `java.awt.*`
- 任何操作系统特定 API / any OS-specific API

### 关键路径 / Critical Paths

以下区域的改动需要第二人评审：

Changes in the following areas require a second reviewer:

- 凭据与登录 / Credentials and login
- 下载 / Download
- 校验 / Verification
- JVM 启动 / JVM launch

---

## 构建与运行 / Build and Run

所有命令在仓库根目录使用 Gradle wrapper 执行。

All commands are run from the repository root with the Gradle wrapper.

### 构建 / Build

```bash
./gradlew build
```

### 运行桌面端（开发模式）/ Run Desktop (Development)

```bash
./gradlew :desktop:run
```

### 运行测试 / Run Tests

在提交代码前格式化代码:

Format code before committing:

```bash

./gradlew spotlessApply

```

全部测试：

All tests:

```bash
./gradlew test

```

单模块测试：

Single module:

```bash
./gradlew :core:test
```

单个测试类：

Single test class:

```bash
./gradlew :core:test --tests "cn.starcloud.core.auth.OfflineUuidTest"
```

### 打包 / Package

为当前平台生成自包含桌面应用：

Build a self-contained desktop app image for the current platform:

```bash
./gradlew packageApp
```

产物位于 `desktop/build/dist/Starcloud/`，运行方式：

Output is written to `desktop/build/dist/Starcloud/`. Run it with:

```bash
./desktop/build/dist/Starcloud/bin/Starcloud
```

### 说明 / Notes

- 构建需要 JDK 21。Gradle wrapper 已锁定版本，升级前请先更新本文档。
- Builds require JDK 21. The Gradle wrapper is pinned; update this document
  before upgrading it.
- `jpackage` 不支持交叉打包。`packageApp` 只为当前平台产出。Windows 产物必须在
  Windows 上构建，或在 CI 的 Windows runner 上构建。
- `jpackage` cannot cross-build. `packageApp` produces an artifact for the
  current platform only. Windows artifacts must be built on Windows, or in CI
  on a Windows runner.
- `build/` 下的构建产物不纳入 git 跟踪。
- Build outputs under `build/` are not tracked by git.

---

## 测试规则 / Testing Rules

### 覆盖要求 / Coverage Requirements

- `core` 中每个新增 public API 至少有一个测试。
- 纯函数（UUID 生成、版本比较、哈希校验、参数构建）必须直接测试，包含边界情况。
- 关键路径（凭据/登录、下载、校验、JVM 启动）必须覆盖失败模式，而不只是正常路径。

- Every new public API in `core` must have at least one test.
- Pure functions (UUID generation, version comparison, hash verification,
  argument building) must be tested directly, including boundary cases.
- Critical paths (credentials/login, download, verification, JVM launch) must
  cover failure modes, not just the happy path.

### 独立性 / Independence

- 测试不得依赖网络。
- 测试不得依赖 `build/` 之外的文件系统。
- 测试不得依赖当前时区。
- 测试不得依赖显示器。JavaFX UI 测试放在 `desktop`，必须显式标记，并尽可能支持无头运行。

- Tests must not depend on network access.
- Tests must not depend on the file system outside `build/`.
- Tests must not depend on the current time zone.
- Tests must not require a display. JavaFX UI tests live in `desktop`, must be
  explicitly tagged, and must be runnable headless where possible.

### 有效性 / Effectiveness

- 不会失败的测试不是测试。如果无论实现如何改测试都通过，重写或删除它。
- 不要 mock 自己拥有的代码。优先使用小型假实现，而不是 mocking 框架。

- A test that cannot fail is not a test. If a test passes no matter what the
  implementation does, rewrite it or delete it.
- Do not mock what you own. Prefer small fake implementations over mocking
  frameworks.

---

## 评审规则 / Review Rules

- 关键路径的改动必须有第二人评审。
- 其余改动评审可选。作者有把握即可合并。
- 评审意见是提问或建议，不是判决。作者应回应，或解释为什么不采纳。

- Changes on critical paths require a second reviewer.
- Review is optional for everything else. Authors may merge when confident.
- A review comment is a question or a suggestion, not a verdict. Authors should
  answer it or explain why it is not adopted.

---

## AI 生成代码 / AI-Generated Code

AI 是起草工具，不是作者。提交者对结果负责。

AI is a drafting tool, not an author. The person who commits owns the result.

### 责任 / Responsibility

- 使用 AI 生成代码，哪怕只是为了同步风格，提交者同样对产出的代码负全部责任。
  评审、测试、维护标准与手写代码完全一致。
- “这是 AI 生成的”永远不能作为缺陷、缺失测试或不符合项目设计的解释。

- If you use AI to generate code, even only to match style, you are fully
  responsible for the produced code. Review, testing, and maintenance standards
  are identical to hand-written code.
- "The AI generated it" is never an acceptable explanation for a defect, a
  missing test, or a design that does not fit the project.

### 允许的用途 / Allowed Uses

- 起草样板代码、DTO、getter/setter、简单适配器。
- 解释不熟悉的代码、生成测试骨架、提出重构方案供人评审。
- 起草文档，由人编辑和核实。
- 将 AI 作为思维辅助，用于串联思路、查询 API、探索备选方案。设计决策仍由人做出。

- Drafting boilerplate, DTOs, getters/setters, simple adapters.
- Explaining unfamiliar code, generating test skeletons, proposing refactors
  for human review.
- Drafting documentation for a human to edit and verify.
- Using AI as a thinking aid to connect ideas, look up APIs, or explore
  alternatives. Design decisions remain human.

### 需人工验证后方可使用 / Requires Human Verification

- 关键路径上的任何内容：凭据/登录、下载、校验、JVM 启动、token 存储、敏感数据日志。
- 安全敏感代码：加密、凭据持久化、权限检查、更新签名校验。
- 引入依赖：每个新增第三方依赖必须经人工评审，并在提交信息中说明理由。
- 修改构建脚本。

- Anything on critical paths: credentials/login, download, verification, JVM
  launch, token storage, logging of sensitive data.
- Security-sensitive code: cryptography, credential persistence, permission
  checks, update signature verification.
- Dependency introduction: every new third-party dependency must be reviewed by
  a human and justified in the commit message.
- Build script modifications.

### 禁止 / Prohibited

- 直接使用 AI 生成核心区域的核心代码。核心逻辑必须由人构思和编写。AI 可以辅助
  串联思路、查询 API、探索方案，但不得代替人的设计构思。
- 提交无法逐行解释的 AI 输出。
- 未经第二人评审，使用 AI 生成涉及凭据处理的代码。
- 未经人工批准，让 AI 引入依赖或修改构建脚本。
- 使用 AI 通过弱化断言或删除测试来“修复”失败的测试。
- 提交带有 `TODO: implement`、`generated by AI` 等占位注释的 AI 生成代码。

- Generating core code for core areas directly from AI. Core logic must be
  conceived and written by a human. AI may assist with connection, API lookup,
  and exploration, but must not replace human design thinking.
- Committing AI output that the committer cannot explain line by line.
- Using AI to generate code that touches credential handling without a second
  human review.
- Letting AI introduce dependencies or modify build scripts without human
  approval.
- Using AI to "fix" failing tests by weakening assertions or deleting tests.
- Committing AI-generated code with placeholder comments such as
  `TODO: implement` or `generated by AI` left in place.

### AI 辅助提交的要求 / Requirements for AI-Assisted Commits

- 提交者必须能解释代码做了什么、边界在哪、为什么这样写。
- AI 生成的测试必须验证其在实现被破坏时会失败。永远通过的测试不是测试。
- 如果保留了 AI 输出，提交信息中应说明 AI 用于起草，以及人工修改或验证了什么。

- The committer must be able to explain what the code does, what its boundaries
  are, and why it is written this way.
- AI-generated tests must be verified to fail when the implementation is broken.
  A test that always passes is not a test.
- If AI output is kept, the commit message should note that AI was used for
  drafting, and what the human changed or verified.

---

## 文档规则 / Documentation Rules

- `README.md` 说明如何构建、如何运行、以及依赖方向。
- 架构决策记录放在 `docs/adr/`，一页即可。
- 不要写与代码重复的文档。记录意图和约束，而不是语法。

- `README.md` explains how to build, how to run, and the dependency direction.
- Architecture decisions go in `docs/adr/` as short notes. One page is enough.
- Do not write documentation that duplicates the code. Document intent and
  constraints, not syntax.

---

## 分支与合并

- `main` 始终可构建、可发布。
- 在短生命周期分支上工作，改动完整后合并回 `main`。
- 大改动使用提交格式拆分为小提交。
- PR 合并后删除分支。

### 分支命名

使用 `type/short-description`：

- `feat/core-offline-uuid`
- `fix/desktop-display-error`
- `chore/ci-spotless`
- `docs/readme-update`

`type` 与提交类型一致。描述使用小写、连字符分隔、简短。

---

## Branches and Merging

- `main` is always buildable and releasable.
- Work on short-lived branches. Merge back into `main` when the change is coherent.
- Large changes should be split into small commits using the commit format above.
- Delete the branch after merging.

### Branch naming

Use `type/short-description`:

- `feat/core-offline-uuid`
- `fix/desktop-display-error`
- `chore/ci-spotless`
- `docs/readme-update`

The `type` matches the commit type. Keep the description lowercase, hyphen-separated, and short.

---

## 有疑问时 / When in Doubt

提问。五分钟的提问比两天的重写便宜。

Ask. A five-minute question is cheaper than a two-day rewrite.