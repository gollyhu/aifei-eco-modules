# aifei-mq 模块需求（任务输入）

> 本文件是**任务输入**，不是 PRD / 设计。新会话收到该开发类任务后，应按 `.codebuddy/rules/aifei-task-routing.mdc`
> 自动编排：主会话先 `ToolSearch(['team_create'])` 探测 → 命中即建队 `aifei-mq` → 派 `aifei-team-lead` 判型定档 →
> 按【路由计划】逐角色派发。**第三节「已拍板决策」为定档结论，团队不得重复询问或改动。**

## 一、背景

`aifei-eco-modules` 是 aifei 框架的生态模块集合（无根聚合 pom，各模块独立构建/发布）。
现有能力模块：`aifei-cache`（缓存/计数）、`aifei-sentinel`（流量治理）、`aifei-weixin`（微信）、
`aifei-sa-token`（认证鉴权）、`aifei-websocket`（WebSocket 双向通信）。

**缺口**：生态尚无任何消息队列 / 异步消息能力。本任务新建 `aifei-mq` 补齐。

## 二、目标

为 aifei 应用补齐**异步消息能力**（解耦、削峰）。业务代码只依赖接口，换 MQ 中间件只改配置、不改代码
（与 `aifei-cache` 换后端、`aifei-websocket` 换实现的思路一致）。

- 坐标：`io.github.gollyhu:aifei-mq:1.0.0`
- 包名：`cn.hg.aifei.mq`
- 技术基线：**Java 8**（class major 52）

## 三、已拍板设计决策（不可更改，无需再问）

1. **首批后端 = 内存/本地实现**：零第三方依赖，可在无中间件环境下完整单测跑通框架；
   真实后端（Kafka / RocketMQ 等）后续以 provider 扩展点追加。
2. **能力范围 = 仅基础收发**：普通消息 `send` + 订阅消费；顺序 / 延迟 / 事务消息留后续。
3. **消息体 = `byte[]` 透传**：另提供 `String` 便捷重载；零编解码白名单负担。
4. **装配形态 = 单个 `MqPlugin`**：同一 Plugin 同时注册 `Producer` / `Consumer` 单例，共用 `mq.*` 配置。

## 四、功能需求（P0，本次交付范围）

- **生产者**：同步发送普通消息，返回发送结果。能力面：`send(Message)`、`send(topic, byte[])`、`send(topic, String)`。
- **消费者**：按主题订阅，注册消息监听器消费。能力面：`subscribe(topic, MessageListener)`。
- **内存实现**：进程内消息总线，发布-订阅语义；投递的并发/有序模型由架构师定档（见第九节）。
- **配置**：`mq.type` 选择后端 + `mq.<type>.*` 后端配置；构造函数只持 `Prop` 引用，
  解析与校验全部在 `start()` 内完成，配置有误即启动失败、不留半装配状态。
- **门面**：`MqKit`（镜像 `CacheUtil` / `WebSocketKit` 的便捷入口范式）。
- **异常**：`MqException`（层次是否细分连接异常，交架构师定档）。

**不在本次范围（P1/P2）**：顺序 / 延迟 / 事务消息；真实 MQ 后端 provider；消费组与竞争消费；
死信与重试策略；消息持久化。

## 五、非功能需求 / 硬约束

- **Java 8**：语法与 API 双约束；主 JAR 内 class major 必须为 52。
- **公共 API 极简、实现无关**：零框架类型（`cn.aifei.*`）、零第三方类型；依赖一律 `optional` 或 `provided`。
- **每类规则唯一定义点**：校验 / 配置域常量 / 编解码 / 注册表等，实现类不得复制。
- **以 `docs/design.md` 为设计事实来源**：契约类行为（投递语义、异常类型、配置键名、公共面数量等）
  必须先在设计决策中定档，再写代码。
- **架构门禁 `ArchitectureBoundaryTest`**：源码文本扫描，须在模块根目录执行，不得放宽断言。
  最低要求：第三方类型只允许出现在实现包；框架类型只允许出现在 `aifei` 接入包；全模块禁止 `java.sql.*`；
  接入包存在 `MqPlugin`；根包公共类型数量精确钉死。
- **模块内验证**：`aifei-mq` 目录内 `mvn clean verify` 必须 **0 failure / 0 error**。
- **文档分工**：`README.md`（用法/配置）、`docs/design.md`（契约，事实来源）、`docs/testing.md`（测试策略）、
  `AGENTS.md`（开发约束）。公共行为变化须同步。
- **注释规范**：简体中文；公共类型与公共成员必须写全 javadoc；原创文件使用统一版权头。

## 六、必须交付的固定配套（对齐既有模块）

| 类别 | 交付物 |
| --- | --- |
| 构建 | `pom.xml`（Java 8、`cn.aifei:aifei-all` 为 `provided`、无第三方运行依赖） |
| 公共面 | 根包接口/门面/异常（类型清单与数量先过设计决策） |
| 内部 | `internal` 层：校验、配置对象与装载、provider 扩展点与注册表、抽象基类 |
| 实现 | `memory` 实现包：内存 provider / 消息总线 / producer / consumer |
| 接入 | `aifei` 接入包：`MqPlugin`（唯一 import 框架类型的类） |
| 测试 | `ArchitectureBoundaryTest` 门禁 + 契约测试（基类 + 内存实现继承）+ 单元测试 |
| 文档 | `docs/design.md`、`docs/testing.md`、`README.md`、`AGENTS.md` |

## 七、参考资产（可直接复制改造的模板）

- **门禁骨架**：`aifei-websocket/src/test/java/cn/hg/aifei/websocket/ArchitectureBoundaryTest.java`
- **公共不可变类范式**：`aifei-websocket/src/main/java/cn/hg/aifei/websocket/Message.java`
- **Plugin 装配范式**：`aifei-websocket/src/main/java/cn/hg/aifei/websocket/aifei/WebSocketClientPlugin.java`
- **provider 扩展点范式**：`aifei-cache/src/main/java/cn/hg/aifei/cache/internal/`
  （`CacheProvider` + `CacheProviderRegistry` + `AbstractCacheProvider`）
- **门面范式**：`aifei-cache/.../CacheUtil.java`、`aifei-websocket/.../WebSocketKit.java`
- **全体底线**：仓库根 `AGENTS.md`；模块约束范例：`aifei-websocket/AGENTS.md`、`aifei-cache/AGENTS.md`
- **模块总览**：`CODEBUDDY.md`
- **注意**：`aifei-websocket` 已删除 provider/SPI/type 配置（纯 JDK 自实现路线），
  其 provider 路线**不可照搬**；mq 的后端适配须学 `aifei-cache` 的 provider 模式。

## 八、验收标准

- [ ] `aifei-mq` 目录内 `mvn clean verify` 通过（0 failure / 0 error）。
- [ ] `ArchitectureBoundaryTest` 全部断言通过。
- [ ] 内存实现可完整单测跑通「生产 → 订阅 → 消费」闭环。
- [ ] 公共面零框架类型、零第三方类型；依赖未新增非 `optional`/`provided` 项。
- [ ] `docs/design.md` / `docs/testing.md` / `README.md` / `AGENTS.md` 齐备且与实现一致。
- [ ] 根 `README.md` 模块总览补充 `aifei-mq` 条目（如涉及）。

## 九、待团队细化（不预设答案，交 PM / 架构师定档）

1. 内存实现的**投递语义**：同步投递还是异步有序投递；有序范围是单主题还是全局。
2. **公共面类型清单与数量**（须精确钉死并写入 design.md 与门禁断言）。
3. **provider 抽象粒度**：provider 是否负责创建 producer / consumer，还是暴露统一客户端。
4. **配置键命名**：`mq.type` 取默认值还是必填；内存实现是否需要 `mq.memory.*` 键。
5. **异常层次**：是否细分连接异常 / 校验异常。
6. 内存实现的**关闭语义**：`stop()` 时是否 drain 在途消息、监听器异常如何处理。

## 十、执行方式

- 本文件为**任务输入**。新会话收到该开发类任务后按 `.codebuddy/rules/aifei-task-routing.mdc` 自动编排。
- 编排顺序：**探测 → 建队 `aifei-mq` → 派 `aifei-team-lead` 判型定档 → 按路由计划逐角色派发**；
  建队成功前不得写任何项目文件。
- 每个 `task` 调用必须带 `description` 与 `mode='bypassPermissions'`。
- 第三节已拍板决策不再重复询问。
