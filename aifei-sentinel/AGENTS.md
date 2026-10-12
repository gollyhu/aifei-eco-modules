# aifei-sentinel 开发约束

本文件适用于整个仓库，供开发代理和项目贡献者使用。

## 开发前提

- 开始设计或修改代码前，必须先阅读 `docs/design.md` 与 `docs/testing.md`。
- `docs/design.md` 是项目设计目标、约束和决策的事实来源。
- 如果需求与现有设计文档冲突，应先明确并更新设计决策，再修改代码。

## 硬性约束

- 保持 `SentinelKit` 门面极简，业务代码零 Sentinel 类型。
- 业务代码只注册 `SentinelInterceptor`、实现 `SentinelBlockHandler`，不引用 `Entry`、`FlowRule`、`BlockException` 等 Sentinel 类型。
- `cn.aifei.aop.*` 与 `cn.aifei.core.*` 只允许出现在 `cn.aifei.sentinel.aifei` 包。
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
