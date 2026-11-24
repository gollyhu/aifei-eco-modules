# aifei-sentinel 热点参数修复 —— 独立回归测试报告

> 测试人：策市袁 ｜ 日期：2026-11-23 ｜ 方式：独立验证，不依赖开发自报结论

## 1. 结论

**通过（放行）**。自动化 18 例全绿；端到端连发证实热点参数按 id 独立限流；约束与 diff 审阅未发现源码缺陷，无需退回开发。

## 2. 用例统计

| 类别 | 总数 | 通过 | 失败 | 跳过 |
| --- | --- | --- | --- | --- |
| SDK 单元测试（含架构门禁） | 18 | 18 | 0 | 0 |
| demo 构建测试 | 1 | 1 | 0 | 0 |
| 端到端手工验证 | 3 | 3 | 0 | 0 |
| 静态约束核对 | 8 | 8 | 0 | 0 |
| diff 审阅检查点 | 5 | 5 | 0 | 0 |

## 3. 实际运行命令与真实结果

所有 Maven 命令均在各自模块根目录执行（`KernelBindingTest` 依赖相对路径 `src/main/java`）。

| 命令 | 真实结果 |
| --- | --- |
| `cd aifei-sentinel && mvn test` | `Tests run: 18, Failures: 0, Errors: 0, Skipped: 0`；`BUILD SUCCESS` |
| `cd aifei-sentinel && mvn install -DskipTests -q` | `exitCode 0`，新 jar 装入本地仓库成功 |
| `cd aifei-sentinel-demo && mvn test` | `BUILD SUCCESS`（`No tests to run`，编译通过） |
| 启动 demo（本地模式，端口 18085） | 日志：`[aifei-sentinel-demo] Sentinel 模式: 本地(local)，规则文件: sentinel-rules.json`；stderr 空 |
| `GET /demo/state` | `code:0, mode:local, rulesFile:sentinel-rules.json` |
| id=1 连发 12 次 `GET /demo/detail?id=1` | `0,0,0,0,0,10006,10006,10006,10006,10006,10006,10006` |
| id=2 连发 6 次 `GET /demo/detail?id=2`（紧接上一组） | `0,0,0,0,0,10006` |

SDK 18 例分布：`SentinelInterceptorTest` 10、`SentinelKitModeTest` 3、`SentinelRuleWatchTest` 1、`KernelBindingTest` 4。

## 4. 关键用例与预期

| 编号 | 前置/步骤 | 预期 | 实测 |
| --- | --- | --- | --- |
| TC-01 | 无规则调用 `index` | 业务执行、返回值透传 | 通过 |
| TC-02 | QPS=0 `FlowRule` 命中 `blocked` | 降级值写入、业务未执行 | 通过 |
| TC-03 | Proxy 路径带实参 + `ParamFlowRule(count=0, paramIdx=0)` | 热点命中、业务未执行 | 通过 |
| TC-04 | AOP 路径 args 为空、覆盖 `argsOf` 供参 | 热点命中、业务未执行 | 通过 |
| TC-05 | AOP 路径 args 为空、未覆盖 | Sentinel 直接放行、业务执行（根因回归保护） | 通过 |
| TC-06 | `KernelBindingTest` 4 例门禁 | aop/core 只在接入包、零 vip/undertow/sql、零 transport 硬引用 | 通过 |
| E2E-01 | `/demo/state` | 本地模式、规则文件 `sentinel-rules.json` | 通过 |
| E2E-02 | id=1 连发 12 次 | 前 5 次 `code 0`、后 7 次 `code 10006` | 通过 |
| E2E-03 | id=2 连发 6 次 | 前 5 次 `code 0`、第 6 次 `code 10006`（按 id 独立计数） | 通过 |

## 5. 约束核对（读文档 + 门禁实测）

- **Java 8 兼容**：SDK 与 demo 的 `pom.xml` 均 `source/target=1.8`；新增代码仅用 lambda、try-with-resources、`ClassValue`、`computeIfAbsent`，无 Java 8 之后语法/API。
- **业务代码零 Sentinel 类型**：`DemoService` 仅引用 SDK 自有注解 `@SentinelResource`，无 `Entry`/`FlowRule`/`BlockException` 等类型渗透。
- **`cn.aifei.aop.*` / `cn.aifei.core.*` 只在接入包**：`KernelBindingTest` 的 `aopImportOnlyInAifeiPackage` / `coreImportOnlyInAifeiPackage` 通过；demo 侧 `Invocation` 引用位于 `demo.aifei` 接入包，业务类 `DemoService` 未引用 aop/core。
- **`cn.aifei.vip.*` / `io.undertow.*` / `java.sql.*` 零引用**：SDK 侧 `noArchOrUndertowOrSqlReference` 门禁通过；demo 作为示例工程引用 Undertow 属既有设计，非 SDK 门面。
- **transport 只反射探测**：`noHardReferenceToTransportClasses` 门禁通过，SDK 零 `import com.alibaba.csp.sentinel.transport.*`。
- **两种模式互斥**：`SentinelKitModeTest` 3 例通过（本地热读不回写、Dashboard 不热读且回写、缺 `sentinel.app.name` fail-fast）。
- **entry/exit 成对且 exit 在 finally**：`SentinelInterceptor.intercept` 仍为 try-with-resources，`exit` 由 `Entry.close()` 在 finally 保证。
- **文档同步**：`docs/design.md` 新增 4.2 实参透传说明、5.4 契约与风险表条目；`docs/testing.md` 新增 3 个用例；`README.md` 新增热点参数接线说明，与代码行为一致。

## 6. diff 审阅（5 个检查点）

- **兜底有效**：`Object[] args = argsOf(inv)` 在 try 外取值，埋点用 `args != null ? args : EMPTY_ARGS`，即使 `inv.getArgs()` 为 null 也不会传 null 给 Sentinel，保护到位。
- **EntryType.IN 与规则匹配**：入站流量语义正确；Sentinel 流控/热点规则不按 EntryType 区分拒绝，不会因改为 IN 而不命中（端到端已实证）。
- **demo 覆盖类影响面**：`DemoSentinelInterceptor.argsOf` 仅对 `demo.detail` 从 `inv.getInput().getInt("id")` 取值，其余资源返回 `NO_ARGS`；`input` 为 null 或 id 缺失时以 0 兜底，无 NPE 风险。
- **新增测试有效性**：`testParamFlowHitWithMethodArgs` / `testParamFlowHitWithOverriddenArgs` 若埋点未透传实参会因 `count=0` 规则放行而使断言失败，能捕获回归；`testParamFlowPassWhenArgsMissing` 保护反向行为；用 `count=0` 规避时间窗口抖动。
- **count 一致性**：工作副本与 `src/main/resources` 模板的 `paramFlowRules[demo.detail].count` 均为 5.0，页面文案 `count 5.0/s` / `每秒放行 5 次` 一致；`flowRules[demo.index].count=2.0` 未被误改，仍与页面「每秒放行 2 次」一致。

## 7. 假通过排除设计

仅凭「id=1 连发出现 10006」不足以区分修复生效与「规则误配成全局 QPS 限流」。因此在 id=1 达到阈值后**立即切换 id=2 连发**：实测 id=2 仍获 5 次放行后才拒绝，证明 `argsOf` 透传的 id 真正进入 Sentinel 按值统计，且不同 id 独立计数——全局限流假通过被排除。

## 8. 遗留风险与建议

- 控制台「拒绝 QPS」曲线需真实 Dashboard + transport 推送，本次以返回码 `10006` 端到端验证等价语义，未实测控制台指标展示（与 `docs/testing.md` 第 5 节未覆盖项一致）。
- demo 的 `argsOf` 依赖 HTTP 参数名 `id`；若端点参数名变更需同步修改覆盖类，属消费方定制代码，非 SDK 通用逻辑，已在设计文档提示。
- 工作副本 `sentinel-rules.json` 现为 Sentinel 回写产生的单行紧凑格式，功能等价但可读性下降；Dashboard 模式下可能被控制台设置覆盖，属预期语义。
- 热点统计窗口为 1 秒，连发跨秒时放行/拒绝分布可能不同于 5+7，属 Sentinel 滑动窗口正常行为，非缺陷。

## 9. 环境清理

本次以端口 18085 启动 demo，验证后按端口反查结束监听进程（`KILL=22160`），`netstat` 复查端口已释放；未触碰 18080 上既有的其他进程。测试期间未修改任何产品源码，`target/` 下产物为构建与日志需要。

策市袁
