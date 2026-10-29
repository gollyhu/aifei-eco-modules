# aifei-mq-demo · E2 事件分发演示 · 独立验收报告

> 验收人：QA 工程师 严过关｜判定依据：`docs/aifei-mq-demo/design-e2.md`（区块契约为准）+ 现有 `README.md`（回归基线）+ 验收矩阵
> 结论摘要：**功能与契约全项 PASS（10/10），零阻断缺陷；发现 1 项 P1 文档收口缺口（T04 未完成）**

## 1. 执行环境

| 项 | 值 |
| --- | --- |
| 工作区 | `c:\Workspace\github\aifei-eco-modules`（Win32 / PowerShell） |
| 构建 | `mvn -B -f aifei-mq-demo\pom.xml clean compile` → `BUILD SUCCESS` / exit 0；`Compiling 24 source files with javac [debug parameters target 1.8]` |
| 编译告警 | 仅 4 条 javac 「source/target 8 已过时」选项告警（既有，非新增），无 error |
| 运行 | `stop-demo.bat` → `start-demo.bat`，HTTP 18100；启动日志：`装配期已订阅: __MAIN__ / aifei.mq.demo.topic + E2 域 [order-events, pay-events] × 6 条(按实例)` |
| 依赖 | 本地仓库 `aifei-mq:1.0.0` / `aifei-all:1.1.1`，未 install、未加依赖 |
| 环境说明 | **验收期间 18100 存在外部并发调用**（观察到非我发起的 `created/paid` 计数漂移与一次外部 clear，随后进程被外部 `stop-demo.bat` 杀掉）。为取得无噪声证据，另用同一 `target/classes` + 同配置在 **18101** 起独立实例复核矩阵 2–7，两轮结果一致；报告以孤立实例证据为准，并在涉及处标注。验收结束已 `stop-demo.bat` 清理（18100/18101 均无监听） |

## 2. 逐项矩阵

### 项 1 编译 —— PASS
- 命令：`mvn -B -f aifei-mq-demo\pom.xml clean compile`
- 实测：`BUILD SUCCESS`，退出码 0，24 个源文件编译完成（`target 1.8`），无新增 error。

### 项 2 三实例 state 订阅事实 —— PASS
- 命令：`curl -s "…/mq/demo/state?instance=<__MAIN__|demoA|demoB>"`
- 实测（18101 孤立环境）：
  - `__MAIN__`：`subscribedTopics=["aifei.mq.demo.topic","order-events","pay-events"]`、`subscribedTopicCount=3`
  - `demoA` / `demoB`：`subscribedTopics=["order-events","pay-events"]`、`subscribedTopicCount=2`
  - 三实例 `subscribers` 均含两条 `{"name":"E2EventListener","source":"startup"}`（`__MAIN__` 另有 `DemoMessageListener/startup`）
- 说明：矩阵括注「每实例 3 主题」与设计 §8.1「`demoA`/`demoB` 0→2」不一致；按**设计文档（契约）**判 PASS（`demoA/B=2` 正是 §8.1 的显式要求，`3` 仅对 `__MAIN__` 成立）。

### 项 3 `event/send` —— PASS
- 命令：`curl -s "…/mq/demo/event/send?instance=__MAIN__&domain=order-events&type=created&bizId=A1001"`
- 实测：`code=0`，`msg="已发送事件 created 到 order-events"`，`payload={"type":"created","version":1,"bizId":"A1001"}`，`payloadSize=46`，`messageId="1"`（非空），`delivered=1`，`totalReceived=1`，`hitCount=1`。
- 参数口径附加核对：`domain=&type=&bizId=`（空串）→ `code=0` 且回落 `order-events/created/A1001`；`domain=%20` → `10001 domain 不能为空`；`bizId=%20` → `10001 bizId 不能为空`（与 §7.1 口径一致）。

### 项 4 `event/dispatch` 计数 / 最近事件 / 实例隔离 —— PASS
- 命令：`curl -s "…/mq/demo/event/dispatch?instance=__MAIN__"`（并查 demoA/demoB）
- 实测：`totalReceived=1`、`unknownCount=0`、`rowCount=8`；`rows[0]={"domain":"order-events","type":"created","count":1,"lastBizId":"A1001","lastAt":"00:45:54.725"}`（`lastAt` 非空）；`domainTopics=["order-events","pay-events"]`、`defaultDomain=order-events`、`defaultType=created`。
- 隔离：同刻 `demoA` / `demoB` 的 `totalReceived=0`、全行 `count=0`。
- 反向隔离：向 `demoA` 发 `pay-events/refunded` 后 `demoA.totalReceived=1`（`pay-events/refunded count=1, lastBizId=P9002`），`__MAIN__` 未受影响；`clear __MAIN__` 后 `demoA` 仍为 1。

### 项 5 错误码 + HTTP 恒 200 —— PASS
| 场景 | 状态行 | body |
| --- | --- | --- |
| 未知 domain（`bad-events`） | `HTTP/1.1 200 OK` | `{"code":10001,"msg":"未知业务域: bad-events，可用业务域: [order-events, pay-events]"}` |
| 空白 domain（`%20`） | `HTTP/1.1 200 OK` | `{"code":10001,"msg":"domain 不能为空"}` |
| 未知 type（`bad`） | `HTTP/1.1 200 OK` | `{"code":10001,"msg":"未知事件类型: bad，可用类型(order-events): [created, paid, cancelled]"}` |
| 未知 instance（`ghost`，dispatch） | `HTTP/1.1 200 OK` | `{"code":10001,"msg":"未知实例: ghost，可用实例: [__MAIN__, demoA, demoB]"}` |
| 未知 instance（`ghost`，dispatch/clear） | `HTTP/1.1 200 OK` | 同上 |

### 项 6 `event/dispatch/clear` 只清该实例统计 —— PASS
- 前置：自由 topic 发送 1 条（`delivered=1`，`messages.count=1`，`sentCount=2`）。
- 命令：`curl -s "…/mq/demo/event/dispatch/clear?instance=__MAIN__"`
- 实测：`code=0`，`msg="__MAIN__ 的事件分发命中统计已清空（订阅保留）"`，`totalReceived=0`、`unknownCount=0`、`rowCount=8`、8 行全 `count=0`、`lastPayload` 键省略。
- 互不污染：`/mq/demo/messages?instance=__MAIN__` 仍 `count=1`（原记录仍在）；`/state` 的 `sentCount=2`、`receivedCount=1`、`messageLogSize=1` 均不变；`demoA` 的 dispatch 仍为 1。

### 项 7 回归（既有 9 端点 + 两套状态隔离）—— PASS
- `/mq/demo/messages`：E2 事件**不混入** —— `__MAIN__` 发过 2 条 E2 事件后 `messages.count=0`；`demoA`/`demoB` 收过 E2 事件后 `messages.count=0`。（交叉点按设计 §1.2 复现：`demoB` 页面订阅 `order-events` 后发 E2 事件 → `delivered=1`（仍只数 E2 监听器）、`messages` 出现 1 条 `subscriber=PageDemoListener`、命中表只 +1，两套计数不叠加。）
- `/mq/demo/send`：`delivered=1`、`receivedCount` 递增；向 `order-events` 发原始 payload 时 `delivered=0`（无 `DemoMessageLog` 侧监听器）——与设计一致。
- `/mq/demo/send/message`：`delivered=1` 且额外返回 `payloadSize`。
- `/mq/demo/subscribe` / `unsubscribe`：`demoB` 订阅 `qa.tmp` → `count 2→3`（`source=page` 带 `×`）；取消 → `count 3→2`，`code=0`。
- `unsubscribe` 对装配期 E2 topic：`demoA + pay-events` → `10001 未登记该主题的页面订阅: pay-events`；对装配期默认 topic 同样 `10001`（README 现文语义一致）。
- `/mq/demo/instances`：`count=3`，`subscribedTopicCount` 依次 3/2/2，`ready=true`。
- `/mq/demo/state` 字段与 README 现文一致（无删改字段）。
- **README 现文未描述 3 个新端点，但既有 9 端点行为无一处与 README 矛盾** → 回归判 PASS（文档缺口见 §5）。

### 项 8 页面 `/mq/demo/page?instance=demoA` —— PASS
- HTTP `200 OK`；关键节点齐备：`#e2-card`(1)、`#e2-domain`、`#e2-type`、`#e2-bizid`、`.e2-table`、`.e2-preview`、`.e2-num`；无 `undefined` / `TODO` / 占位字面量（0 处）。
- 实测 DOM（浏览器实测）：
  - 卡片标题「事件分发（E2：固定 topic + payload 内 type）」，位置在发布卡之后、左列
  - 表恒 8 行（0 也显示）：`order-events: created/paid/cancelled/未知 type（兜底桶）` + `pay-events: paid/refunded/failed/未知 type（兜底桶）`
  - 命中次数列 `text-align=right`（`td` 与 `th.e2-num` 均 right）
  - 空值渲染「—」（未命中行的 `lastBizId · lastAt` 为 `—`）；命中行形如 `QA9001 · 00:13:16.962`
  - 域下拉 `["order-events","pay-events"]`，类型下拉随域联动（切到 `pay-events` 后自动重置为该域首个 `paid`）；`bizId=A1001`；预览 `{"type":"created","version":1,"bizId":"A1001"}`
  - 交叉点提示句、底部说明句（「…装配期已为每个实例订阅 2 个域 topic（不可取消）」）与 §6.2 文案一致
- 交互实测：点「发送事件」→ 回执 `code=0 delivered=1 hitCount=1 totalReceived=2 messageId=2` + `payload=…`，命中表即时 +1，右列消息流仍为空（E2 不写入）。
- 点「清空命中统计」（`confirm` 文案含实例名）→ 回执 `code=0 demoA 的事件分发命中统计已清空（订阅保留）`，表全 0，空态提示出现。
- **定时器**：脚本中 `setInterval` 出现 **0** 次；轮询仅 `pollTimer` 一套（`pollOnce → Promise.all([fetchState, fetchMessages, fetchDispatch])`）。实测 6.5s 内 4 轮 × 3 请求（`state`/`messages`/`event/dispatch` 各 4 次），无第二条轮询链路；`setTimeout` 另 2 处为既有 `copyTimer` / `noticeTimer` 一次性闪现定时器。

### 项 9 硬约束 —— PASS
- `cn/aifei/demo/common/arch/**` 零改动（`git status` 该目录为空输出）；四件套 `In/Out/IoHandler/IoDispatcher` 齐备（目录另有 `InParser/OutCode` 等应用侧配套）。
- `aifei-mq/**` **无任何 Java / pom 改动**；`git diff` 仅有 `AGENTS.md`/`README.md`/`docs/design.md` 共 105 行文档新增（内容为库侧 §5.8「E2 定档」文档，属上一轮库侧任务，`design-e2.md` 头部即将其列为输入）；本轮 demo 实现产物与其零交集。
- `pom.xml` 差异为空（`git diff --stat -- *.xml` 无输出），无新增依赖/插件。
- 无运行期动态 topic 机制：新类与 `DemoAppConfig` 中无 `declare/createTopic` 一类调用；`subscribe/unsubscribe` 仅出现在既有页面端点（未改语义）。
- `git status` 改动集合与设计 §2 文件清单逐一对应：新增 `E2Topics/E2DispatchTable/E2EventListener` + `docs/aifei-mq-demo/design-e2.md`；修改 `DemoAppConfig`、`MqDemoService`、`demo.html`、`mq-demo.js`、`mq-test.css`。工作区另有 `DemoMessageLog/DemoMessageListener/MqInstanceCatalog` 等改动，经 diff 核对属**上一轮多实例改造**（per-instance 分片、构造器注入实例名），非本轮 E2 增量。
- Java 8 兼容：编译 `target 1.8` 通过；新类仅用 `java.time` / `LinkedHashMap` / `ConcurrentHashMap`，无 `var`/`record`/文本块。分层污染：0（无 Controller/Render/Repository/Mapper/DTO）。SQL：本轮无 SQL。事务：无 `commit()`。

### 项 10 补充核对（实现者自报偏差）
| # | 偏差 | 判定 | 依据 |
| --- | --- | --- | --- |
| ① | `dispatch/clear` 的 `data` 是 dispatch 的**同构超集**（多 `domainTopics/typeOptions/defaultDomain/defaultType/lastPayload`） | **可接受** | 设计 §7 对 clear 列出的字段（`instance/totalReceived/unknownCount/rowCount/rows`）全部存在且为全 0，属纯附加；且前端 `applyDispatch(env.data)` 依赖 `domainTopics/typeOptions` 保持下拉候选，回落「最小集」会清空下拉。字段名复用 `putDispatch` 单一定义点，未新造字段名、未返回裸 Map |
| ② | `unknownCount` = 所有 unknown 行 `count` 之和 | **可接受（正确）** | 实测：向 `order-events` 发未知 type 原始 payload 后 `totalReceived=2`、`unknownCount=1`、`order-events/unknown count=1`（其余 6 行 0），`totalReceived` 亦等于全行之和；代码上 `record()` 只在「命中兜底桶」分支 `unknownCount++` 且同分支给该 unknown 行 +1，未知域防御分支同理，故恒等 |
| ③ | `pay-events` 类型为 `paid\|refunded\|failed`（3 个，共 6 类型） | **可接受，但需文档同步** | 设计 §9 Q2 写「2 域 5 类型」（`pay-events: paid/refunded`），实现多一个 `failed`；`rows` 由 7 行变 8 行。契约层不破：`typeOptions`/`rows` 由 `E2Topics` 单点驱动，前端与端点自动适配；`aifei-mq` §5.8.5 只给示例类型、未固化清单，故不违库侧定档。**但 `design-e2.md` §9 Q2 与实现的枚举不一致**，须在文档里改口径（成本一行） |
| ④ | 后端 JSON 省略 `null` 键、前端「缺失 = —」 | **可接受** | 实测未命中行形如 `{"domain":…,"type":…,"count":0}`（无 `lastBizId/lastAt`）、`lastPayload` 在清空后被省略；DOM 渲染为「—」，符合 §6.3「未命中过显示 `—`」；前端 `textOf`/`e2LastText` 对缺失与 null 同处理，无 `undefined` 泄漏 |

## 3. 偏差与契约对齐小结

- **BASELINE（区块契约 §6 + 端点契约 §7）：对齐**。§6.1 位置/标题、§6.2 控件与绑定/缺省/联动/回车发送/二次确认、§6.3 四列与渲染（`unknown` 文案、`count` 右对齐、`—` 空值）、§6.4 三态（骨架 + 禁用、空态提示、`err-bar.is-inline` 保留上次数据、busy 文案与失败回执、整页降级禁用）、§6.5 轮询合并（无新定时器）、§7 三端点的成功字段/失败码/文案（`10001` 三类文案逐字一致）全部核对通过。
- 唯一字段级偏离：`pay-events` 类型多一个 `failed`（项 10③）→ 可接受，需改 `design-e2.md` §9 Q2 口径。

## 4. 回归结论

既有 9 端点语义与 README 现文一致，无回归：`state`（`subscribedTopicCount` 0→2/3 为设计预期）、`messages`（E2 不混入）、`messages/clear`、`send`、`send/message`、`subscribe`、`unsubscribe`（装配期订阅一律 `10001`，页面订阅可取消）、`instances`、`page`。两套状态（`DemoMessageLog` 与 `E2DispatchTable`）互不污染，实例维度隔离完好。

## 5. 已知问题（非阻断）

| # | 级别 | 问题 | 影响 | 归属 |
| --- | --- | --- | --- | --- |
| 1 | **P1** | **T04 文档收口未完成**：① `aifei-mq-demo/README.md` 端点表缺 `event/send`／`event/dispatch`／`event/dispatch/clear` 三行，演示脚本缺 E2 步骤；② `docs/aifei-mq-demo/ui-prototype.md` 无 E2 区块登记（`grep E2/事件分发` 零命中）；③ `docs/aifei-mq-demo/design.md` 仍保留已失效断言：第 731 行「`demoA` → `subscribedTopics:[]`」、第 741 行「取消后 `subscribedTopics:[]`」、第 763 行 R10「本设计**不做**多实例装配期订阅」——三者均已被 §8.1 有意偏差推翻且未补记 | 功能无影响；读者按旧文档验收会误判 `demoA` 订阅数为 0 | `aifei-service-engineer`（T04） |
| 2 | P2 | `design-e2.md` §9 Q2 的「2 域 5 类型」与实现 6 类型不一致（项 10③） | 文档口径与实现漂移 | 同上（随 T04 一并改） |
| 3 | 环境 | 验收期间 18100 被外部会话并发驱动（非我发起的发送与清空、并有一次 `stop-demo.bat` 杀掉进程）；已在 18101 孤立实例复核，结论一致。不影响代码判定，但**同一 18100 端口上并行验收会互相污染计数与消息流** | 后续验收须独占端口 | 调度人（团队约定） |

## 6. 最终路由判定

- **功能/契约层：NoOne**（矩阵 1–10 全 PASS，零源码缺陷、零测试缺陷；未发现断言与 PRD/设计冲突之处，故无回退实现成员的代码缺陷单）
- **文档层：Engineer —— `aifei-service-engineer`**（补 T04 三处：README 端点表 + 演示脚本、`ui-prototype.md` E2 区块登记、`design.md` §7.3/§R10 失效断言 + §9 Q2 类型口径）
- **阻断性**：无。文档修正为 P1 可并行的收尾项，不影响 E2 基线锁定与演示可用性。

## 7. Aifei 检查清单

- Java 8 兼容：✅（`target 1.8` 编译通过；无 9+ 语法/API）
- 无分层污染：✅（新增仅协议/统计/监听器三件 + Service 三方法，无 Controller/Repository/DTO）
- HIO 完整：✅（arch 目录零改动，`In/Out/IoHandler/IoDispatcher` 齐备且为应用侧实现）
- SQL 安全：✅（本轮无 SQL，无字符串拼接）
- 生成器产物未被手改：✅（未触及 `common/db/base/` 与 `ModelSet.java`）
- 事务用法：✅（无事务、无 `commit()`）
- 能力边界：✅（仅用 `Producer/Consumer/Message/SendResult/Json` 等公开 API；未使用上传下载/Cookie/Session/Excel/Admin 等未公开能力）
