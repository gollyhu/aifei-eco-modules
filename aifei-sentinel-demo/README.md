# aifei-sentinel-demo

`aifei-sentinel` 的**最小接入示例**（**示例工程，不发布**）：自写 HIO（undertow）+ 一个 Just Service（`service` 包），现场演示 Sentinel 的**流控 / 熔断 / 热点参数**三种治理能力。

- 与 `aifei-websocket-demo` 结构对齐：`DemoApp` → `Aifei.start(DemoAppConfig)`，自写 HIO（`arch` 包）+ 一个 Just Service（`service` 包）。
- 业务代码零 Sentinel 类型：只注册 `SentinelInterceptor`、实现降级策略 `Out.fail(OutCode.RATE_LIMIT)`，规则与接入由 `aifei-sentinel` SDK 承担。
- 端口 18080，与 `aifei-websocket-demo` 的 18090/18091 错开。

## 端口

| 用途 | 端口 | 来源 |
| --- | --- | --- |
| HTTP（演示端点） | **18080** | `start-demo.bat` 的 `-Dundertow.port=18080` |

## 运行

```bat
start-demo.bat              :: 本地模式启动 demo（端口 18080），验证地址 http://localhost:18080/demo
start-dashboard.bat         :: 启动本地 Sentinel 控制台（端口 8858，账号 sentinel/sentinel），需自备 sentinel-dashboard jar
start-demo-dashboard.bat    :: Dashboard 模式启动 demo（控制台未起时会自动拉起并等待就绪）
stop-demo.bat               :: 按端口 18080 找到监听进程并结束
```

启动后访问 <http://localhost:18080/demo/index> 返回 `{"code":0,...}` 即为正常。

## 演示端点

所有端点都是 GET，返回 `{"code":0,"msg":...,"data":...}`（HTTP 恒 200，语义由 `code` 承载；`0` 为成功，限流/熔断返回对应业务码）。

| 端点 | 治理类型 | 规则（`sentinel-rules.json`） |
| --- | --- | --- |
| `GET /demo/index` | 流控（QPS） | `DemoService#index`，`count=2.0`（每秒放行 2 次，超出限流） |
| `GET /demo/slow` | 熔断（RT） | `DemoService#slow`，`count=500.0`（RT>500ms 触发，接口实际 sleep 800ms） |
| `GET /demo/detail?id=N` | 热点参数限流 | `DemoService#detail`，`paramIdx=0`（即 `id`），`count=5.0`（同一 id 每秒 5 次，超出限流） |

## 现场演示脚本（约 1 分钟）

1. `GET /demo/index` → `code=0`（正常）。
2. 用 `curl` 快速连发（间隔 < 1s）`/demo/index` 超过 2 次：前 2 次 `code=0`，第 3 次起返回**限流业务码**（`OutCode.RATE_LIMIT`）。
3. 连续访问 `/demo/slow`（每次 ~800ms）：达到 RT 熔断阈值后，后续请求直接返回**熔断降级码**，不再执行 `slow` 方法。
4. 对 `GET /demo/detail?id=1` 快速连发超过 5 次：前 5 次 `code=0`，第 6 次起返回**热点参数限流**业务码（按 `id=1` 统计）。

> 压测可用 `ab -n 20 -c 5 http://localhost:18080/demo/index` 之类工具快速触发限流。

## 两种运行模式（互斥）

由 `app-config*.txt` 是否配置 `sentinel.dashboard.server` 决定，规则来源不同：

| 模式 | 配置 | 规则来源 | 控制台 |
| --- | --- | --- | --- |
| 本地（默认） | `app-config.txt` | `sentinel-rules.json` 物理文件，`FileRefreshableDataSource` 秒级热读；改文件即时生效，无需重启 | 不需要 |
| Dashboard | `app-config-dashboard.txt` | 初始规则一次性加载，控制台推送并**合并回写** `sentinel-rules.json`（重启不丢） | 需先 `start-dashboard.bat`（端口 8858） |

切换：用 `start-demo-dashboard.bat`（自动拉起控制台）或改 `-Ddemo.config=app-config-dashboard.txt`。

## 规则文件（`sentinel-rules.json`）

首次启动由 `src/main/resources/sentinel-rules.json` 复制到工作目录（根 `sentinel-rules.json`），本地模式改它秒级热生效。三类规则：

```json
{
  "flowRules":      [{"resource": "...DemoService#index",  "grade": 1, "count": 2.0}],
  "degradeRules":   [{"resource": "...DemoService#slow",  "grade": 0, "count": 500.0, "timeWindow": 10}],
  "paramFlowRules": [{"resource": "...DemoService#detail", "grade": 1, "count": 5.0, "paramIdx": 0}]
}
```

资源名默认「全限定类名#方法名」，须与规则 JSON 的 `resource` 严格一致（见 `aifei-sentinel` 模块约定）。

## 依赖说明

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| `cn.aifei:aifei-all` | `1.1.1` | aifei 框架（demo 自带） |
| `io.github.gollyhu:aifei-sentinel` | `1.0.0` | 本 SDK |
| `com.alibaba.csp:sentinel-transport-simple-http` | `1.8.8` | **仅 Dashboard 模式**需要（连接官方控制台） |
| `io.undertow:undertow-core` | `2.2.39.Final` | 自写 HIO 的 Web 容器 |
| `org.apache.logging.log4j:log4j-core` | `2.26.0` | 日志实现 |

注：`aifei-sentinel` 内 `sentinel-transport-simple-http` 为 `provided`（仅反射探测），Dashboard 模式由本 demo 显式引入。
