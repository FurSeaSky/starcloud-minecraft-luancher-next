## 变更说明 / Description

<!-- 这个 PR 做了什么，为什么做。一两句话即可。 -->
<!-- What does this PR do, and why. One or two sentences. -->

## 影响模块 / Affected Modules

- [ ] `core`
- [ ] `desktop`
- [ ] `android`
- [ ] 构建 / 依赖 / CI / Build / Dependencies / CI
- [ ] 文档 / Documentation

## 关键路径检查 / Critical Path Check

<!-- 如果本 PR 涉及以下任一区域，必须由第二人评审。 -->
<!-- If this PR touches any of the following, a second reviewer is required. -->

- [ ] 凭据 / 登录 / Credentials / Login
- [ ] 下载 / Download
- [ ] 校验 / Verification
- [ ] JVM 启动 / JVM Launch
- [ ] 不涉及关键路径 / Does not touch critical paths

## 测试 / Tests

<!-- 列出新增或修改的测试。关键路径的改动必须覆盖失败模式。 -->
<!-- List new or modified tests. Critical path changes must cover failure modes. -->

- [ ] 新增测试 / New tests
- [ ] 修改现有测试 / Modified existing tests
- [ ] 不需要测试（说明原因）/ No tests needed (explain why)

## AI 使用说明 / AI Usage

<!-- 如果使用了 AI 起草，说明用于哪部分，以及人工修改或验证了什么。 -->
<!-- If AI was used for drafting, describe where and what was changed or verified by a human. -->

- [ ] 未使用 AI / No AI used
- [ ] 使用 AI 起草，已由提交者逐行理解并验证 / AI-drafted, understood and verified line by line by the committer

## 检查清单 / Checklist

- [ ] 提交信息符合 `[type](target)-message` 格式 / Commit message follows `[type](target)-message` format
- [ ] `core` 未引入平台依赖 / `core` has no platform dependencies
- [ ] 依赖方向未被破坏 / Dependency direction is preserved
- [ ] 构建通过（`./gradlew build`）/ Build passes (`./gradlew build`)
- [ ] 测试通过（`./gradlew test`）/ Tests pass (`./gradlew test`)