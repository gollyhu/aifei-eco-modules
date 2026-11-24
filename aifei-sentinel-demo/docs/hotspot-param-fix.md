# demo.detail 热点参数拒绝恒为 0 —— 根因与修复结论

## 1. 现象

Sentinel 控制台中 `demo.detail` 「通过 QPS」有值、「拒绝 QPS」恒为 0.0；demo 页面「热点参数」面板连发后始终 `code 0`，未出现限流返回码 `10006`。

## 2. 根因结论

**确为代码缺陷，非规则未加载、非资源名不一致、非页面调用方式问题。**

### 2.1 直接原因：埋点未透传业务实参

`aifei-sentinel` 的 `SentinelInterceptor` 原实现为：

```java
try (Entry entry = SphU.entry(resourceOf(inv));) {
    inv.invoke();
}
```

`SphU.entry(String)` 内部等价于 `entry(name, EntryType.OUT, 1, OBJECTS0)`，传给 Sentinel 的是一个**空实参数组**。Sentinel 的 `ParamFlowChecker.passCheck` 中：

```java
if (args == null) return true;
int paramIdx = rule.getParamIdx();
if (args.length <= paramIdx) return true;   // 空数组 → 直接放行
```

规则 `paramIdx=0`，而 `args.length=0`，因此每次请求都被直接放行，热点规则永不命中 → 控制台「拒绝 QPS」恒 0、页面无 `10006`。

### 2.2 深层原因：AOP 路径实参晚于拦截器解析

不能简单改成 `inv.getArgs()` 交给 entry。aifei 1.1.2 `Invocation.invoke()` 的实现是「先跑完拦截器链，再解析实参并调用业务方法」，`routes.scan` 的 AOP 路径下拦截器进入时 `args` 尚未赋值（同仓库 `aifei-audit/docs/需求/aifei-audit.md` 的 PV-01 实证一致）。因此：

- Proxy 路径（`new Invocation(target, method, args, ...)`）：实参构造期就位，默认取 `inv.getArgs()` 即可。
- AOP 路径（demo 的 `routes.scan`）：必须提供扩展点，由消费方从 `Input` 读取参数值。

### 2.3 次要问题：规则工作副本与模板值不一致

工作副本 `aifei-sentinel-demo/sentinel-rules.json` 中热点规则 `count=2.0`，而 `src/main/resources` 模板与页面文案均为 `5.0/s`。本次一并对齐为 5.0。

### 2.4 已排除项

- 规则已正确加载：`paramFlowRules` 中 `resource=demo.detail`，与 `DemoService.detail` 的 `@SentinelResource("demo.detail")` 严格一致。
- 参数索引无误：页面固定请求 `/demo/detail?id=1`，`@Para(name="id")`，规则 `paramIdx=0`。
- 页面链路正常：按钮请求 URL、结果渲染、业务码判定均正确。

## 3. 修复方案

1. SDK `SentinelInterceptor` 新增可覆盖的 `protected Object[] argsOf(Invocation inv)`，默认返回方法实参（`null` 以空数组兜底）；埋点统一改为 `SphU.entry(resourceOf(inv), EntryType.IN, 1, argsOf(inv))`。仍用 try-with-resources，`exit` 在 `finally`，entry/exit 成对。
2. demo 新增 `DemoSentinelInterceptor extends SentinelInterceptor`，覆盖 `argsOf`：仅对 `demo.detail` 从 `inv.getInput().getInt("id")` 取热点值，其余资源返回空数组。
3. 工作副本热点规则 `count` 由 2.0 改为 5.0，与模板、页面文案一致。

保留原生 Sentinel 热点参数能力（`sentinel-parameter-flow-control` + `ParamFlowRule`），未用自研限流替代。

## 4. 修改/新增文件清单

SDK（aifei-sentinel）：

| 文件 | 变更 |
|---|---|
| `src/main/java/cn/hg/aifei/sentinel/aifei/SentinelInterceptor.java` | 埋点透传实参；新增 `argsOf` 扩展点；`EntryType.IN` |
| `src/test/java/cn/hg/aifei/sentinel/aifei/SentinelInterceptorTest.java` | 新增 3 个热点参数回归用例与测试桩 |
| `docs/design.md` | 同步埋点实参决策、5.4 契约、风险表 |
| `docs/testing.md` | 同步用例清单与未覆盖项 |
| `README.md` | 新增「热点参数」接线说明 |

Demo（aifei-sentinel-demo）：

| 文件 | 变更 |
|---|---|
| `src/main/java/cn/hg/aifei/sentinel/demo/aifei/DemoSentinelInterceptor.java` | 新增；覆盖 `argsOf` 从 HTTP 入参取 id |
| `src/main/java/cn/hg/aifei/sentinel/demo/common/DemoAppConfig.java` | 注册新拦截器 |
| `sentinel-rules.json` | 热点规则 count 2.0 → 5.0 |

## 5. 验证命令与结果

| 命令 | 结果 |
|---|---|
| `cd aifei-sentinel && mvn test` | **18 tests, 0 failures, 0 errors**（含 4 例门禁，新增 3 例热点回归） |
| `cd aifei-sentinel && mvn install -DskipTests` | BUILD SUCCESS（新 jar 装入本地仓库供 demo 依赖） |
| `cd aifei-sentinel-demo && mvn test` | BUILD SUCCESS，17 个源文件编译通过 |
| 端到端：启动 demo（本地模式，端口 18080），连发 12 次 `GET /demo/detail?id=1` | `code` 序列：`0,0,0,0,0,10006,10006,10006,10006,10006,10006,10006` |

端到端结果说明：前 5 次放行、后续 7 次被热点规则拒绝，与「同一 id 每秒放行 5 次、超出返回 10006」的期望一致，证明修复生效。启动日志显示 `Sentinel 模式: 本地(local)，规则文件: sentinel-rules.json`，stderr 无异常。

新增单测使用 `count=0` 的 `ParamFlowRule`（`tokenCount==0` 时直接拒绝），不依赖时间窗口，避免跨秒抖动；同时保留「无实参则放行」用例作为根因回归保护。

## 6. 剩余风险

- 控制台「拒绝 QPS」曲线需真实 Dashboard + transport 推送，本次以返回码 `10006` 端到端验证等价语义，未连接控制台实测该指标。
- demo 的 `argsOf` 从 HTTP 参数名 `id` 取值，若业务端点参数名变更，需同步修改覆盖类（属消费方定制代码，非 SDK 通用逻辑）。
- Dashboard 模式下热点规则由控制台推送并回写本地文件，工作副本 `count=5.0` 可能被控制台设置覆盖，属预期的模式语义。
- 如消费方覆盖 `SentinelInterceptor.intercept` 自行调用 `SphU.entry`，需自行透传 `argsOf(inv)`，否则仍会复现同类问题；已在 README 与 design 文档说明。

寇戴码
