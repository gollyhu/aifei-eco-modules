# aifei-eco-modules
Aifei 项目的生态补充，提供多种生态能力模块。

本仓库是多个独立发布的 aifei 生态组件集合，各模块单独构建、单独发版，无父级聚合 pom，按需引入。

## 模块总览

| 模块 | 坐标 | 定位 |
| --- | --- | --- |
| [aifei-cache](aifei-cache/README.md) | `io.github.gollyhu:aifei-cache:1.0.0` | 缓存与计数组件：Caffeine / Redis 双后端，支持多实例 |
| [aifei-sentinel](aifei-sentinel/README.md) | `io.github.gollyhu:aifei-sentinel:1.0.0` | Sentinel 流量治理接入 SDK：流控、熔断、系统保护 |
| [aifei-sentinel-demo](aifei-sentinel-demo/README.md) | （示例工程，不发布） | aifei-sentinel 的完整接入示例 |
| [aifei-weixin](aifei-weixin/README.md) | `io.github.gollyhu:aifei-weixin:1.0.0` | 微信开发包：公众号 + 小程序 + 微信支付 V3 |
| [aifei-sa-token](aifei-sa-token/README.md) | `io.github.gollyhu:aifei-sa-token:1.0.0` | Sa-Token 认证鉴权接入 SDK：登录态、角色权限、注解鉴权 |
| [aifei-websocket](aifei-websocket/README.md) | `io.github.gollyhu:aifei-websocket:1.0.0` | WebSocket 双向能力：客户端出站连接 + 服务端接收连接，Java-WebSocket 后端可替换 |
| [aifei-websocket-demo](aifei-websocket-demo/README.md) | （示例工程，不发布） | aifei-websocket 的完整接入示例：本地回环演示建连 / 收发 / 断线自动重连 |
| [aifei-mq](aifei-mq/README.md) | `io.github.gollyhu:aifei-mq:1.0.0` | 异步消息能力：生产者发送 + 消费者订阅，内存后端开箱即用，真实 MQ 后端可扩展 |
| [aifei-mq-demo](aifei-mq-demo/README.md) | （示例工程，不发布） | aifei-mq 的完整接入示例：内存后端的发送 / 订阅消费闭环演示 |

### aifei-cache

面向 aifei 应用的缓存与计数组件。业务代码只依赖 `Cache` / `Counter` 两个接口，换后端只改配置、不改代码。

- 后端二选一或并用：`caffeine`（进程内）/ `jedis`（Redis，值默认 Fury 编解码）
- 多实例：主缓存 `__MAIN__` 恒存在，扩展实例由 `cache.names` 声明
- 名字分三层：实例名（连哪个存储）/ 缓存名（业务分区）/ 命名空间（区分应用）
- TTL 毫秒精度，支持 `putIfAbsent`、Loader 委托等常用模式
- 详细用法见 [aifei-cache/README.md](aifei-cache/README.md)，设计文档见 [docs/design.md](aifei-cache/docs/design.md)

### aifei-sentinel

把 Sentinel 的流控、熔断与系统保护能力接入 aifei，业务代码不出现任何 Sentinel 类型。

- 通过 `routes.scan(...)` 注册拦截器，五种治理能力共用同一处埋点
- 本地模式（规则文件热生效）与 Dashboard 模式（控制台推送）互斥切换，不改业务代码
- 降级接线由消费方实现，限流语义由业务码承载，HTTP 状态码恒 200
- 详细用法见 [aifei-sentinel/README.md](aifei-sentinel/README.md)，设计文档见 [docs/design.md](aifei-sentinel/docs/design.md)

### aifei-weixin

微信开发包：公众号协议内核 + 开放接口客户端 + 小程序 + 微信支付 V3 + aifei 接入层。

- 协议内核：AES-256-CBC 加解密、SHA-1 签名、安全 XML 解析
- 消息模型：接收消息 8 类、事件 10 类、被动回复 6 类，`MsgKit` 双入口分发
- Api 客户端：`ApiCaller` 及菜单 / 用户 / 素材 / 模板消息 / OAuth2 / 二维码 / JsTicket
- 小程序：`MiniAppApi`（code2Session / 订阅消息 / 手机号 / 小程序码）
- 微信支付 V3：`PayApi` 下单查单退款 + `PayNotifyKit` 回调验签解密，RSA / GCM / mTLS 纯 JDK 实现
- 接入前必读 [docs/design.md](aifei-weixin/docs/design.md)

### aifei-sa-token

把 Sa-Token 的认证鉴权能力接入 aifei + Undertow，消费方无需改造自有 HIO。

- 上下文接线在 `Dispatcher` 层完成：请求头、Cookie、请求方法、请求路径与响应写回一次到位
- 装配只改一行（包装 `Dispatcher`）+ 注册插件 + 挂鉴权拦截器
- 两种鉴权方式：路径规则（`SaTokenInterceptor`）或方法注解（`SaAnnotationInterceptor`）
- 鉴权失败默认置 401 / 403，也可换成「HTTP 恒 200 + 业务码」语义
- 多实例会话：换用基于 `aifei-cache` 的持久层实现，登录态跨实例共享
- 详细用法见 [aifei-sa-token/README.md](aifei-sa-token/README.md)，设计文档见 [docs/design.md](aifei-sa-token/docs/design.md)

### aifei-websocket

为 aifei 应用补齐框架未覆盖的 WebSocket 双向基础能力，含客户端与服务端两个独立子能力。

- 客户端出站连接第三方 WS 服务（心跳保活 / 自动重连），服务端接收 WS 连接（会话管理 / 广播），两者分别装配、各自独立
- 公共 API 零第三方类型、零框架类型；换后端只改配置 `websocket.{client|server}.type`，默认 `javawebsocket`
- 心跳与自动重连**默认关闭**（`heartbeat-seconds = 0` / `auto-reconnect = false`），需显式开启
- 不提供发送队列 / 背压：非 `OPEN` 时 `send*` 抛 `IllegalStateException` 且消息被丢弃，缓冲与重放由调用方处理
- 消费方需自带 `Java-WebSocket`（模块内 `optional` 不传递）与 `slf4j-api` + 一个日志绑定
- 详细用法见 [aifei-websocket/README.md](aifei-websocket/README.md)，设计文档见 [docs/design.md](aifei-websocket/docs/design.md)

### aifei-mq

为 aifei 应用补齐框架未覆盖的异步消息能力（解耦、削峰）。业务代码只依赖公共接口，换 MQ 中间件只改配置、不改代码。

- 公共面固定 7 个类型：`Message` / `Producer` / `Consumer` / `MessageListener` / `SendResult` / `MqKit` / `MqException`
- 同步投递、单主题 FIFO、多订阅者按登记顺序回调；无订阅者视为成功，监听器运行时异常就地隔离
- 首批后端为**内存实现**（零第三方运行依赖），真实后端（Kafka / RocketMQ 等）以 provider 扩展点追加
- 取用二选一：`@Inject Producer` / `@Inject Consumer`，或静态门面 `MqKit`
- 详细用法见 [aifei-mq/README.md](aifei-mq/README.md)，设计文档见 [docs/design.md](aifei-mq/docs/design.md)

## 构建

所有模块编译目标为 **Java 8**，需要 Maven 3.6+。各模块独立构建：

```bash
cd aifei-cache     && mvn clean install
cd aifei-sentinel  && mvn clean install
cd aifei-weixin    && mvn clean install
cd aifei-sa-token  && mvn clean install
cd aifei-websocket && mvn clean install
cd aifei-mq        && mvn clean install
```

发布 Maven Central 的模块使用 `mvn -Pcentral-release clean deploy`（见各模块 pom 的 profile）。

## 依赖说明

- `aifei-cache`：强依赖 `cn.aifei:aifei`；Caffeine、Jedis、Fury 为可选依赖，用哪个后端加哪个
- `aifei-sentinel`：`aifei-all` 为 `provided`，消费方应用自带
- `aifei-weixin`：`aifei-all` 为 `provided`，消费方应用自带（`Handler`/`Input`/`Output`/`Log` 与 fastjson2 由它提供）
- `aifei-sa-token`：`aifei-all` 与 `undertow-core` 为 `provided`，`sa-token-core` 为 `compile`
- `aifei-websocket`：`aifei-all` 为 `provided`，`Java-WebSocket` 为 `optional`（消费方自带，另需自带 `slf4j-api` 与日志绑定）
- `aifei-mq`：`aifei-all` 为 `provided`，消费方应用自带；无第三方运行依赖

## 许可证

[Apache License 2.0](LICENSE)
