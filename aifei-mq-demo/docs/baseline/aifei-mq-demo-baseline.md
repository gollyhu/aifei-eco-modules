# aifei-mq-demo 演示页面 · 基线（已锁定）

> 状态：`BASELINE: PASS`（2026-10-17，产品经理 2 轮 + QA 2 轮评审）
> 事实来源：`docs/aifei-mq-demo/prd.md`（200 行）+ `docs/aifei-mq-demo/ui-prototype.md`（451 行，含两轮评审段）
> 本文件只做**索引与硬约束清单**，与上述两份冲突时以上述两份正文为准并回报调度人。

## 1. 范围

在示例工程 `aifei-mq-demo` 内新增两个静态页面与配套端点：

1. **实例入口页**（`GET /`）：按配置列出全部 MQ 实例，点击进入该实例的演示页。
2. **实例演示页**（`GET /mq/demo/page?instance=`）：参考 bchrt MQTT WebSocket 客户端测试工具的布局（连接参数 / 订阅 / 发布 / 消息流），风格对齐 `aifei-websocket-demo`。

不改 `aifei-mq` 库本身。

## 2. 用户已拍板（不可推翻）

| # | 决策 |
| --- | --- |
| D1 | 演示页**提供**「订阅 / 取消订阅」控件，UI **必须**显式标注「影响所有浏览器会话（全局副作用）」 |
| D2 | 入口页展示 **3 张实例卡**：`__MAIN__`（主实例）+ `demoA` + `demoB`；配置补 `mq.names = demoA,demoB` |

## 3. 硬约束（实现与验收门禁）

1. **不假装有 broker**：`aifei-mq` 为内存后端、同步投递；页面**禁止**出现「已连接 / 断开连接 / 重连 / 连接中 / QoS / Retain / Keep Alive / Client ID / 主机 / 端口 / 证书」。状态只表达「就绪 / 未装配」。
2. **固定警示条**（两页都出现）：`内存后端 · 同步投递 · 无真实 broker —— 消息不过进程边界，重启即清空；无 QoS / retain / 通配符`。
3. **`instance` 参数**：统一默认值 `__MAIN__`；省略即旧行为（向后兼容）；未知实例返回 `code=10001` 且 `msg` 含可用实例清单。
4. **按实例隔离**：消息记录 / 发送接收计数 / 订阅登记均须按实例分片。现状 `DemoMessageLog` 为**全局静态单例**（全 `static`，共 200 条），本需求要改造，**方案由架构师定**。
5. **页面跳转一律绝对路由**：`/` 与 `/mq/demo/page?instance=<name>`；禁止 `demo.html` / `index.html` 相对跳转（会 404）。
6. **`@Path` Service 返回的 view 模板名必须以 `/` 开头**：模板根为 `webapp/view`，故为 `/mq/index.html` 与 `/mq/demo.html`（不带 `view/`，不写前缀会被 targetPath 二次拼接）。
7. **装配期订阅的 chip 不可取消**：`Consumer.unsubscribe(topic, listener)` 按 listener 实例身份移除，装配期 listener 在 `DemoAppConfig` 内，页面无法取回 → 对其调 `unsubscribe` 返回 `code=10001`「未登记该主题的页面订阅」，UI 不得承诺成功。
8. **静态资源目录必须在容器启动前落盘**：启动后新建 `webapp/` 不会被资源管理器收录，静态请求一律 404。
9. **页面订阅用独立 listener**（不与装配期 `DemoMessageListener` 复用）：同主题重复订阅会导致同一消息被记录多次、`delivered` 叠加，UI 须提示。

## 4. 页面与文件清单（基线）

| 路径 | 内容 |
| --- | --- |
| `src/main/webapp/view/mq/index.html` | 实例入口页 |
| `src/main/webapp/view/mq/demo.html` | 实例演示页 |
| `src/main/webapp/assets/css/mq-test.css` | 两页共用样式（沿用 `ws-test.css` 的 token 与组件类名） |
| `src/main/webapp/assets/js/mq-index.js` | 入口页脚本（只调 `/mq/demo/instances`） |
| `src/main/webapp/assets/js/mq-demo.js` | 演示页脚本 |
| `src/main/webapp/assets/lib/vue.global.prod.js` | 本地全局 Vue（与 `ws-demo` 同源同版本） |

## 5. 端点契约（摘要，详见 PRD §5）

| 端点 | 参数 | 说明 |
| --- | --- | --- |
| `GET /` | — | 入口页视图，返回 `/mq/index.html` |
| `GET /mq/demo/page` | `instance`（仅前端读） | 演示页视图，返回 `/mq/demo.html` |
| `GET /mq/demo/instances` | — | 新增；实例清单（`__MAIN__` 在首，其后按 `mq.names` 顺序） |
| `GET /mq/demo/state` | `instance` | 扩展 |
| `GET /mq/demo/messages` | `instance` | 扩展 |
| `GET /mq/demo/messages/clear` | `instance` | 扩展；只清该实例 |
| `GET /mq/demo/send` | `instance` `topic` `text` | 扩展 |
| `GET /mq/demo/send/message` | `instance` `topic` `text` | 扩展 |
| `GET /mq/demo/subscribe` | `instance` `topic` | 新增 |
| `GET /mq/demo/unsubscribe` | `instance` `topic` | 新增；listener 由后端登记表取回 |

`instances[]` 字段：`instanceName` / `type` / `main` / `configPrefix` / `ready` / `subscribedTopicCount` / `messageLogSize` / `messageLogCapacity`。
`messages[]` 字段：`sequence` / `topic` / `subscriber` / `text` / `payloadSize` / `receivedAt`（接收侧无 `messageId`）。

## 6. 待架构师实测落定的事项

1. `@Path("/")` 上的 `index()` 能否直接映射 `GET /`（否则改用别的写法）。
2. `DemoMessageLog` 由全局单例改为按实例分片的方案（含容量口径：改造后每实例 200 条）。
3. 订阅登记表（topic → listener 实例 + 来源标记）的放置位置与线程安全。
4. 实例枚举来源：`MqPlugin.instanceNames()` 为 private、`MqHolder` 无枚举 API → demo 侧重解析 `mq.names`（复用 `internal.MqConfig`，标注为同厂 internal 依赖，不扩散为公共契约）。
