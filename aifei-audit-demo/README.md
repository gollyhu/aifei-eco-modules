# aifei-audit-demo

`aifei-audit` 的示例工程：注解采集、手动埋点、脱敏对照、落点切换、失败事件、`traceId` 关联、空态与错误态，全部在**单页**上可点可看。

- 端口：**18160**（HTTP）｜离线可跑｜零 CDN、零构建工具
- 依赖：`aifei` + `aifei-audit` + `aifei-trace` + `aifei-sa-token` + `aifei-mq`（示例工程行为，库模块零生态依赖）

## 运行

```bat
cd aifei-audit      && mvn -q -DskipTests clean install   :: 先把 4 个被演示的库依次装进本地仓库
cd ../aifei-trace   && mvn -q -DskipTests clean install
cd ../aifei-sa-token && mvn -q -DskipTests clean install
cd ../aifei-mq      && mvn -q -DskipTests clean install
cd ../aifei-audit-demo
start-demo.bat                                           :: 编译 + 启动，18160
```

也可先在仓库根按 [根 README 构建小节](../README.md#构建) 安装全部模块，再回到本目录启动。

页面：`http://localhost:18160/`（根路径经跳转页进入 `/audit/demo/page`）｜停服：`stop-demo.bat`

日志：`src/main/resources/log4j2.xml` 只把 `cn.hg.aifei.audit` 放开到 **WARN** 输出到控制台，根 logger 保持 `error`（框架内部日志不刷屏）。故**场景 8 可在控制台看到模块 WARN**，例如：

```
15:17:54.357 WARN  [cn.hg.aifei.audit.internal.SafeInvoker] - [aifei-audit] 落点交付失败，已按失败隔离吞掉: java.lang.IllegalStateException: demo-sink-failure
```

## 端点（全部 GET，HTTP 恒 200，语义由 code 承载）

| 端点 | 作用 | 场景 |
| --- | --- | --- |
| `/audit/demo/state` | 落点、配置、计数、`traceId`、操作人 | 顶部状态条 |
| `/audit/demo/events?since=` | 增量事件（seq 单调递增） | 事件流 |
| `/audit/demo/annotated` | `@Audit(action="order.cancel")` 业务动作 | 1 |
| `/audit/demo/unannotated` | 同包未标注动作（对照，零事件） | 1 |
| `/audit/demo/manual?times=3` | `AuditKit.record` 循环埋点 | 2 |
| `/audit/demo/masked?keep=&secret=` | 记录入参 + `mask={1}` | 3 |
| `/audit/demo/masked/off?keep=&secret=` | 同一动作不记录入参 | 3 |
| `/audit/demo/sink/switch?name=` | 切换 memory / console / mq 落点 | 4 |
| `/audit/demo/failure` | 必抛 `IllegalStateException` 的动作 | 5 |
| `/audit/demo/failure/evidence` | 异常实例与冒泡实例是否同一 + 最近失败事件 | 5 |
| `/audit/demo/sink/fail?on=true` | 落点抛异常开关（失败隔离） | 8 |
| `/audit/demo/login?userId=alice` · `/logout` | 操作人来源（Sa-Token） | 操作人 |
| `/audit/demo/reset` | 清空展示数据与计数 | 7 |

## 接线要点（照抄即可）

```java
// 审计拦截器必须第一个传入；biz 包被审计，service 包（页面端点）不挂拦截器
routes.scan("cn.hg.aifei.audit.demo.biz", new AuditInterceptor(), new AuditedOrderService.ThrownCaptureInterceptor());
routes.scan("cn.hg.aifei.audit.demo.service");

// 容器内唯一的落点是「可切换委托 sink」：切换只改内部委托，业务代码零改动
AopKit.get().addSingletonObject(AuditSink.class, SwitchableAuditSink.create(new MemoryAuditSink(), new ConsoleAuditSink(), new MqAuditSink()));
AopKit.get().addSingletonObject(AuditActorProvider.class, new SaTokenActorProvider());
```

`cn.aifei.demo.common.arch` 为逐字同步自官方示例的 HIO（保留官方包名，未本地化改写）。

## 与设计的两处差异（均只影响示例工程）

1. **脱敏对照用两条路径**（`/masked` 与 `/masked/off`）：`@Audit.args` 是编译期固定的注解属性，同一路径的查询参数无法切换注解配置。
2. **业务异常由框架收敛为 `code=500` 响应**：审计拦截器必须让异常原样上抛才能记失败事件，故「同一实例」证据改由 `/failure/evidence` 读取（观察型拦截器记录冒泡实例，与业务抛出实例比对）。
