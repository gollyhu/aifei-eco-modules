# aifei-sentinel 测试文档

> 版本：1.0.0 ｜ 关联：`docs/design.md`（设计依据）、`README.md`（使用说明）

## 1. 测试原则

- 单元测试**不启动 HTTP、不连接 Dashboard、不连库**，可在任意环境离线执行。
- 用**真实**的 aifei `Invocation`（构造器直连）+ **真实**的 Sentinel 规则管理器驱动，不用 mock 替代被测行为。
- 架构约束交给门禁测试守住，不依赖人工 review。

## 2. 测试分层

| 测试类 | 层次 | 覆盖 |
|---|---|---|
| `KernelBindingTest` | 架构门禁（源码扫描） | 内核绑定收敛、零硬引用 transport |
| `SentinelInterceptorTest` | 行为（接入层） | 未命中规则放行透传、命中限流走降级 |
| `SentinelRuleWatchTest` | 热生效（core） | `watch` 后改文件自动刷新规则 |
| `SentinelKitModeTest` | 模式分流（core） | 本地模式热读且不回写、Dashboard 模式不热读且回写、缺 `app.name` fail-fast |

## 3. 用例清单

### 3.1 架构门禁 `KernelBindingTest`

对 `src/main/java` 下全部 `.java` 做源码文本扫描：

| 用例 | 断言 |
|---|---|
| `noArchOrUndertowOrSqlReference` | 不存在 `cn.aifei.vip.` / `io.undertow.` / `java.sql.` 引用 |
| `aopImportOnlyInAifeiPackage` | `import cn.aifei.aop.` 只出现在 `cn/aifei/sentinel/aifei/` 包 |
| `coreImportOnlyInAifeiPackage` | `import cn.aifei.core.` 只出现在接入层包 |
| `noHardReferenceToTransportClasses` | 不存在 `import com.alibaba.csp.sentinel.transport.`（消费方未引 transport 时不得 `NoClassDefFoundError`） |

门禁还自带前置校验：源码目录存在、扫描结果非空。

### 3.2 行为测试 `SentinelInterceptorTest`

以「真实 `Invocation` + 真实 Sentinel」驱动，不启动 HTTP：

| 用例 | 断言 |
|---|---|
| `testPassThroughWhenNoRule` | 无规则时业务方法被执行且返回值透传 |
| `testBlockedWhenFlowRuleHit` | 命中 `FlowRule`（QPS=0）时降级返回值被写入、业务方法**未执行** |

### 3.3 热生效测试 `SentinelRuleWatchTest`

| 用例 | 断言 |
|---|---|
| `testRuleHotReload` | 写入 QPS=0 规则 → 命中限流；改文件为 QPS=1000 → 等待刷新后放行（无需重启） |

用例结束在 `finally` 中关闭数据源并删除临时文件。

### 3.4 模式分流测试 `SentinelKitModeTest`

| 用例 | 断言 |
|---|---|
| `localModeRegistersHotReadAndDoesNotWriteBack` | 注册 5 个热读数据源；内存规则变更后规则文件**保持原样**（不回写） |
| `dashboardModeWritesBackOnRuleChange` | 注册 0 个热读数据源；内存规则变更后文件出现 `flowRules` 与对应资源名 |
| `dashboardModeWithoutAppNameFailsFast` | 配了 Dashboard 但缺 `sentinel.app.name` → 抛 `IllegalStateException`，消息含 `sentinel.app.name` |

用例通过反射读取 `SentinelKit.DATA_SOURCES` 校验数据源数量；`setUp` / `tearDown` 调用 `SentinelKit.shutdown()` 并清理系统属性（`csp.sentinel.dashboard.server` / `project.name`），保证用例间无状态残留。

## 4. 运行方式

```shell
# 模块根目录（aifei-sentinel）执行
mvn test

# 发布前验证：打包 + 检查实际 JAR、源码包与 Java 8 字节码
mvn clean verify
```

- `KernelBindingTest` 依赖相对路径 `src/main/java`，**必须在模块根目录执行**。
- 测试全部离线，无外部服务依赖；Dashboard 相关行为通过系统属性与内存规则验证，不真连 Dashboard。

## 5. 未覆盖项与人工验证

| 项 | 说明 |
|---|---|
| transport 缺失的 fail-fast 分支 | 配了 Dashboard 但 classpath 无 transport 时抛 `IllegalStateException`，当前无自动用例，需人工构造缺依赖场景 |
| Dashboard 端到端 | 起官方控制台 → 控制台改规则 → 本地 `sentinel-rules.json` 更新 → 重启规则仍在，由 demo 人工验证（`start-dashboard.bat` / `start-demo-dashboard.bat`） |
| transport 推送链路 | `sentinel-transport-simple-http` 的命令处理与推送依赖真实控制台，单测不覆盖 |
| 系统保护 / 热点参数 / 黑白名单 | 依赖机器负载、`ContextUtil.enter(origin)` 等运行时上下文，当前仅覆盖流控路径，其余共用同一埋点与加载链路 |

## 6. 变更时的测试要求

- 新增或改变公共行为（配置键、资源名规则、模式分流、回写策略）时，同步更新本文件的用例清单与 `docs/design.md`。
- 触及内核绑定边界（`cn.aifei.aop.*` 引用位置、新增对 arch / undertow / sql / transport 的引用）时，必须先让 `KernelBindingTest` 通过。
