# aifei-sentinel 开发约束

本文件适用于 `aifei-sentinel` 模块，供开发代理和项目贡献者使用；仓库级共同底线见根 `AGENTS.md`，两者冲突时以更严格者为准。

## 开发前提

- 开始设计或修改代码前，必须先阅读 `docs/design.md` 与 `docs/testing.md`。
- `docs/design.md` 是项目设计目标、约束和决策的事实来源。
- 如果需求与现有设计文档冲突，应先明确并更新设计决策，再修改代码。

## 硬性约束

- 保持 `SentinelKit` 门面极简，业务代码零 Sentinel 类型。
- 业务代码只注册 `SentinelInterceptor`、实现 `SentinelBlockHandler`，不引用 `Entry`、`FlowRule`、`BlockException` 等 Sentinel 类型。
- `cn.aifei.aop.*` 与 `cn.aifei.core.*` 只允许出现在 `cn.hg.aifei.sentinel.aifei` 包。
- `cn.aifei.vip.*`、`io.undertow.*`、`java.sql.*` 零引用。
- `sentinel-transport-*` 不得硬引用，只能在运行时反射探测。
- 两种运行模式互斥，不得同时启用规则文件热读与回写。
- `SentinelInterceptor` 中 `entry` 与 `entry.exit` 必须成对，`exit` 放在 `finally`。
- 项目代码、依赖和 API 必须兼容 Java 8。
- 未经设计决策，不得自行确定资源名规则、模式分流条件、fail-fast 行为或回写策略。

## 文档维护

- 面向使用者的定位、场景与用法维护在 `README.md`。
- 设计目标、行为契约与关键决策维护在 `docs/design.md`；测试策略与用例清单维护在 `docs/testing.md`。
- 新增或改变公共行为时，应同步更新设计文档和测试。

## 验证

**命令一律在模块目录内执行**（门禁 `KernelBindingTest` 依赖相对路径 `src/main/java`）：

```bat
cd aifei-sentinel
mvn test          :: 单元 + 门禁测试，必须 0 failure / 0 error
mvn clean verify  :: 发布前：打包并检查 JAR、源码包与 Java 8 字节码
```

测试全部离线，不启动 HTTP、不连 Dashboard、不连库；transport 缺失、Dashboard 端到端等分支由 `docs/testing.md` 第 5 节列明，需人工验证。

## 提交前检查清单

- [ ] 已在模块目录执行 `mvn test`（发布前 `mvn clean verify`），0 failure / 0 error。
- [ ] 业务代码零 Sentinel 类型；`cn.aifei.aop.*` / `cn.aifei.core.*` 仍只出现在 `cn.hg.aifei.sentinel.aifei` 包。
- [ ] 两种运行模式仍互斥；`sentinel-transport-*` 仍只经运行时反射探测，未新增硬引用。
- [ ] `SentinelInterceptor` 中 `entry` / `entry.exit` 仍成对，`exit` 在 `finally`。
- [ ] 未引入 Java 8 之后语法 / API，未新增未声明 `optional` 或 `provided` 的第三方依赖。
- [ ] 公共行为变化已同步 `docs/design.md` 与 `README.md`，并有测试覆盖。
