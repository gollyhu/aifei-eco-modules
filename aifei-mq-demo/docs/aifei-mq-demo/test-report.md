# aifei-mq-demo 演示页面 · 契约与集成验收测试报告

> 验收标尺：`docs/baseline/aifei-mq-demo-baseline.md`（BASELINE: PASS）+ `docs/aifei-mq-demo/prd.md` §5/§6/§7 + `docs/aifei-mq-demo/ui-prototype.md` + `docs/aifei-mq-demo/design.md` §0/§4/§7.3/§9
> 被测范围：`aifei-mq-demo/src/main/java/cn/hg/aifei/mq/demo/**`（9 文件）+ `src/main/webapp/**`（6 文件）+ `src/main/resources/app-config.txt`
> 测试类型：契约测试（HTTP 端点矩阵）+ 静态硬约束检查 + 端到端冒烟（无头 Edge）
> 轮次：第 1 轮（上限 2 轮，本次为最后一轮验收）

---

## 1. 环境与命令

| 项 | 值 |
| --- | --- |
| 执行时间 | 2026-10-17 10:39 ~ 10:44（本机时区，UTC+8） |
| OS / Shell | Windows 11 (10.0.22631) / cmd.exe |
| JDK | Oracle JDK 21.0.11（编译目标 1.8） |
| Maven | Apache Maven 3.9.16（`C:\IDEs\apache-maven3`） |
| 应用/框架 | aifei 1.1.1、aifei-undertow 1.1.1、undertow 2.2.39.Final |
| 服务地址 | `http://localhost:18100`（`start-demo.bat`，`mq.type=memory`，`mq.names=demoA,demoB`） |
| 浏览器 | Microsoft Edge（`C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe`）+ Puppeteer headless |

实际执行的命令（按顺序）：

| # | 命令 | 说明 |
| --- | --- | --- |
| 1 | `cd /d c:\Workspace\github\aifei-eco-modules\aifei-mq-demo` + `mvn -B clean test` | A 节构建验收（模块根目录执行，exit code 记录于 A 节） |
| 2 | `netstat -ano \| findstr ":18100"` | 启动前确认端口空闲（无输出） |
| 3 | 以 `start-demo.bat` 后台启动（日志 `target/run.log`） | 首次启动，PID 20800 |
| 4 | `curl` 系列请求（B/C 节逐条，`-w` 记录 `http_code`/`content_type`/`size_download`） | 端点验收 |
| 5 | Puppeteer（Edge headless）导航 + DOM 断言 | E 节页面冒烟 |
| 6 | `stop-demo.bat` → 3s → `netstat` | 端口 18100 LISTENING 消失（仅 TIME_WAIT） |
| 7 | 再次 `start-demo.bat`（日志 `target/run2.log`，PID 21876）→ 重测 `/` 与 css | B 节重启验证 |
| 8 | `stop-demo.bat` → `netstat` | 最终关停：`SUCCESS: The process with PID 21876 has been terminated`，18100 无 LISTENING，端口已释放 |

---

## 2. 验收结果

### A. 构建（`mvn -B clean test`，模块根目录）

| 用例 | 期望 | 实测 | 结论 |
| --- | --- | --- | --- |
| A1 构建成功 | exit code = 0 且 `BUILD SUCCESS` | `EXITCODE=0`；`[INFO] BUILD SUCCESS`；`Total time: 4.899 s` | PASS |
| A2 Java 8 兼容 | 编译目标 1.8，无 Java 9+ 语法/API 报错 | `Compiling 21 source files with javac [debug parameters target 1.8]`，无 `[ERROR]`；仅 `-source 8 已过时` 类 `[WARNING]`（JDK 21 提示，非代码问题） | PASS |
| A3 无测试类时的最小门禁 | 模块无 `src/test/java` 时至少跑通 compile | 构建日志无 `maven-surefire-plugin` / `Tests run` 段落（本模块确实无测试类）；`target/classes` 正常产出 | PASS |

> Aifei 检查清单（静态抽查，源码级）：**Java 8 兼容 ✅**；**无分层污染 ✅**（无 Controller/Render/Repository/Mapper/DTO 新增）；**HIO 完整 ✅**（`In` / `Out` / `IoHandler` / `IoDispatcher` 均在应用侧 `cn.aifei.demo.common.arch`）；**SQL 安全 ✅**（本 demo 无库无表、无 SQL）；**生成器产物未被手改 ✅**（无 `common/db/base/`、无 `ModelSet.java`）；**事务用法 ✅**（无 `commit()` 调用，本 demo 无事务）；**能力边界 ✅**（未使用上传下载 / Cookie / Session / Excel / Admin 脚手架）。

### B. 静态资源可达性（本次最易翻车项）

| 用例 | 期望 | 实测 | 结论 |
| --- | --- | --- | --- |
| B1 `GET /` | 200 + `text/html` + 含「MQ 演示入口」与至少 1 个实例名 | `status=200`，`type=text/html; charset=utf-8`，`size=8106`；响应体 SHA256 `2EFE51B5…42F0` 与本地 `view/mq/index.html` **完全一致**，其中含 `<title>aifei-mq-demo · MQ 演示入口</title>`（L6）、`mq.names = demoA,demoB`（L17） | PASS |
| B2 `GET /mq/demo/page?instance=demoA` | 200 + html + 含「消息流」与「影响所有浏览器会话」 | `status=200`，`text/html; charset=utf-8`，`size=17140`；响应体 SHA256 `035B37FF…2387` 与本地 `view/mq/demo.html` 一致；含「消息流」（L183）、「⚠ 影响所有浏览器会话（全局副作用）」（L71） | PASS |
| B3 `GET /assets/css/mq-test.css` | 200 + `text/css` + 字节数 > 10000 | `status=200`，`text/css`，`size=23365`（源文件 23365 B） | PASS |
| B4 `GET /assets/js/mq-demo.js` | 200 + js + 字节数 > 30000 | `status=200`，`application/javascript`，`size=37431` | PASS |
| B5 `GET /assets/js/mq-index.js`、`GET /assets/lib/vue.global.prod.js` | 均 200 | 200 / `application/javascript` / 7680 B；200 / `application/javascript` / 170605 B | PASS |
| B6 重启后仍可达（排除「启动后新建目录未收录」） | `stop-demo.bat` 后重启，`/` 与 css 仍 200 | 停：PID 20800 终止，18100 无 LISTENING；启：PID 21876，`/` 200/8106、css 200/23365、`/mq/demo/page?instance=demoA` 200/17140、`mq-demo.js` 200/37431（与重启前字节数一致） | PASS |

> 重启后的启动日志（`target/run2.log`）：`[aifei-mq-demo] mq.type: memory，实例(3): [__MAIN__, demoA, demoB]，装配期已订阅: __MAIN__ / aifei.mq.demo.topic` + `Started in 1.09 s`。

### C. 端点契约矩阵（HTTP 恒 200，业务看 `code`）

> 说明：所有请求实测均 `http=200`（下表不再重复）。字段名逐一核对，缺字段/改名即缺陷。

| 用例 | 期望 | 实测 | 结论 |
| --- | --- | --- | --- |
| C1 `/mq/demo/instances` | `count=3`；`[0].instanceName=__MAIN__`、`main=true`；随后 demoA、demoB；每条含 8 字段 | `count:3`；`[{__MAIN__,main:true,configPrefix:"mq",ready:true,subscribedTopicCount:1,messageLogSize:0,messageLogCapacity:200,type:"memory"},{demoA,…,configPrefix:"mq.demoA",ready:true,…},{demoB,…,configPrefix:"mq.demoB",ready:true,…}]`，顺序与 `mq.names` 一致 | PASS |
| C2 `/mq/demo/state`（不带 instance，向后兼容回归） | `code=0`，行为同改造前 | `code:0`，`instance:"__MAIN__"`，`subscribedTopics:["aifei.mq.demo.topic"]`，`subscribers[0].{name:"DemoMessageListener",source:"startup"}`，旧字段齐：`mqType/configPrefix/sentCount/receivedCount/messageLogSize/messageLogCapacity/producerInjected/consumerInjected` | PASS |
| C3 `/mq/demo/state?instance=demoA` | `code=0`，`instance=demoA` | `code:0`，`instance:"demoA"`，`configPrefix:"mq.demoA"`，`subscribedTopics:[]`，计数全 0 | PASS |
| C4 `/mq/demo/send?instance=demoA&topic=t1&text=hello` 后查 messages | `delivered` 数值合理；messages 能查到该条 | `delivered:0`、`receivedCount:0`（demoA/t1 当时无订阅者 → 符合 PRD §7.1「无订阅者发送仍成功、消息被丢弃」），messages `count:0`。**用例口径需修正**：该断言隐含「已订阅」前置条件 | PASS（口径修正，见 C4′） |
| C4′ 先订阅再发送的可查性（补齐前置条件） | 订阅后发送能被 `/messages` 查到且字段合规 | `subscribe` → `subscribedTopics:["t1"]`、`subscribers[0].source:"page"`；`send` → `delivered:1`、`receivedCount:1`；`messages` → `count:1`，`{sequence:1,topic:"t1",subscriber:"PageDemoListener",text:"hello",payloadSize:5,receivedAt:"10:40:04.424"}`，**无 `messageId`**（符合契约） | PASS |
| C5 隔离性 | demoB 与主实例均不含 demoA 的 t1 记录 | 同一 topic `t1`：demoA `count:1`（text=hello），demoB `count:1`（text=demoB-hello），主实例 `count:0`；demoB 的 `sequence` 与 `messageId` 各自从 1 起（每实例自增） | PASS |
| C6 `/mq/demo/messages/clear?instance=demoA` | demoA 归零；demoB 不受影响 | demoA `messageLogSize:0`、`sentCount:0`、`receivedCount:0`，订阅 `t1` **保留**；demoB 仍 `count:1` 且记录内容不变 | PASS |
| C7 `/mq/demo/send/message?instance=demoA&topic=t2&text=hello` | 返回含 `payloadSize` | `code:0`，`payloadSize:5`，另有 `messageId/topic/text/delivered/deliveredRefTopic/receivedCount/instance` | PASS |
| C8 `/mq/demo/subscribe?instance=demoA&topic=t2` + 重复订阅 | `subscribedTopics` 含 t2，`subscribers[].source=page`；重复 → `alreadySubscribed=true` | 首调：`subscribedTopics:["t1","t2"]`、`subscribers[].source:"page"`、`alreadySubscribed:false`；二调：`alreadySubscribed:true`、`msg:"该主题已订阅（本页）"`、`subscribedTopicCount` 仍为 2 | PASS |
| C9 `/mq/demo/unsubscribe` 两条路径 | demoA/t2 成功；对装配期主题 → `10001`「未登记该主题的页面订阅」 | demoA/t2：`code:0`、`subscribedTopics:["t1"]`；`__MAIN__/aifei.mq.demo.topic`：`code:10001`、`msg:"未登记该主题的页面订阅: aifei.mq.demo.topic"` | PASS |
| C10 未知实例 | `/mq/demo/state?instance=nope` → `code=10001`，msg 含「未知实例」+ 可用实例清单 | `{"code":10001,"msg":"未知实例: nope，可用实例: [__MAIN__, demoA, demoB]"}`；`instance=a.b` → 同为 10001（含 `.`） | PASS |
| C10′ `instance=` 空串 | 按 PRD §5「非法值：空串 → 10001」 | **`code:0`，回退主实例**（`instance:"__MAIN__"`，数据完整返回）。对照 `instance=%20`（空格）→ `10001` | **FAIL**（缺陷 D-02） |
| C11a 参数非法（subscribe 空 topic） | `10001`「topic 不能为空」 | `{"code":10001,"msg":"topic 不能为空"}` | PASS |
| C11b 参数非法（send 空 topic） | `/mq/demo/send?instance=demoA&topic=&text=x` → `10001` | **`code:0`，topic 被折叠为默认 `aifei.mq.demo.topic` 并真实发送**：`{"topic":"aifei.mq.demo.topic","messageId":"4","text":"x","delivered":0}` | **FAIL**（缺陷 D-01） |
| C12 发送后计数按实例独立变化 | `sentCount`/`receivedCount`/`messageLogSize` 随实例独立 | 起：demoA `3/0/0`、demoB `1/1/1`；向 demoB 发送一次后：demoA **仍为** `3/0/0`，demoB 变 `2/2/2` | PASS |

补充验证（超出 C 节清单，用于判定「是否属缺陷」）：

| 用例 | 期望 | 实测 | 结论 |
| --- | --- | --- | --- |
| X1 主实例 `send` 无参 | 默认 `aifei.mq.demo.topic` + `hello`，`delivered=1` | `{"topic":"aifei.mq.demo.topic","text":"hello","delivered":1,"receivedCount":1}` | PASS |
| X2 同 topic 双 listener 叠加（设计 R3/R4） | 页面再订阅装配期主题后，`delivered=2`、同消息两条记录且 subscriber 可区分 | 订阅后 `subscribers` 出现 startup+page 两条；发送 → `delivered:2`；messages 出现 `PageDemoListener` 与 `DemoMessageListener` 各一条（`sequence` 2、3） | PASS（符合设计 R3/R4，非遗留缺陷） |
| X3 `unsubscribe` 后装配期仍生效文案（设计 §4.9） | `code=0` + msg 追加「代码：装配期订阅仍生效」，`subscribedTopics` 仍保留该主题 | `msg:"已取消订阅 aifei.mq.demo.topic（影响该实例的所有浏览器会话）；代码：装配期订阅仍生效"`，`subscribers` 只剩 startup | PASS |
| X4 取消未订阅主题 | `10001`「未登记该主题的页面订阅」 | `{"code":10001,"msg":"未登记该主题的页面订阅: never.subscribed"}` | PASS |
| X5 空白 vs 空串（定性根因） | 仅「空串」异常，「空白」应正常报错 | `topic=%20` → `10001 topic 不能为空`；`text=%20` → `10001 text 不能为空`；`topic=`/`text=` → 静默取默认值（见 D-01） | 记录（支撑 D-01 定位） |

### D. 硬约束静态检查（grep 源码）

| 用例 | 期望 | 实测 | 结论 |
| --- | --- | --- | --- |
| D1 固定警示条 | 两页 HTML 均含「内存后端 · 同步投递 · 无真实 broker」 | `view/mq/index.html:35` 与 `view/mq/demo.html:56` 文案**逐字一致**（含「—— 消息不过进程边界，重启即清空；无 QoS / retain / 通配符」） | PASS |
| D2 禁用词扫描 | 两个 js + 两个 html 不得出现「已连接/断开连接/重连/连接中/QoS/Retain/Keep Alive/Client ID/证书」（警示条内 QoS/retain 例外） | `assets/js/*`：「已连接|断开|重连|连接中|QoS|Retain|Keep Alive|Client ID|证书|主机|端口」**0 命中**；`view/mq/*`：仅两页警示条各 1 处「无 QoS / retain / 通配符」（基线 §3.2 强制文案，属允许例外）；「连接」在 html/js 中 0 命中；「主机/端口/证书/Client ID/Keep Alive」0 命中 | PASS |
| D3 绝对路由 | 跳转不得用 `demo.html`/`index.html` 相对路径 | `mq-index.js:14` `DEMO_PAGE="/mq/demo/page"`，`:108` `window.location.href = DEMO_PAGE + "?instance=" + encodeURIComponent(name)`；`mq-demo.js:650` `window.location.href = "/"`；`demo.html:16` `href="/"`。`demo.html`/`index.html` 仅出现在文件头注释 | PASS |
| D4 装配期 chip 无取消按钮 | `source === 'startup'` 的 chip 不渲染 × | `mq-demo.js:775` `canRemove = sources.indexOf("page") >= 0`；`demo.html:109` `v-if="c.canRemove"`；浏览器实测 `__MAIN__` 的 chip：`x-btn=0`、`is-locked=true`、`title` 为基线原文 | PASS |
| D5 容量不硬编码 200 | `messageLogCapacity`/`maxEntries` 必须读接口 | 运行时一律读接口：`demo.html:40` `{{view.messageLogSize}} / {{view.messageLogCapacity}}`、`:195` `上限 {{view.messageLogCapacity}}`、`index.html:88` `{{inst.messageLogCapacity}}`；`mq-demo.js`/`mq-index.js` 中 `200` 仅出现在「HTTP 恒 200」注释。⚠ 观察项：`demo.html:266` 说明抽屉文案写死「上限 200 条」（见 D-03） | PASS（附 P2 观察项） |
| D6 无外链资源 | 两个 html 无 `http(s)://` 的 src/href | `view/mq/**` 中 `https?://` **0 命中**；`demo.html` 的 `src/href` 全部为 `/assets/css/mq-test.css`、`/assets/lib/vue.global.prod.js`、`/assets/js/mq-demo.js`、`/`（`index.html` 同构） | PASS |

### E. 页面冒烟（已执行，Edge + Puppeteer headless）

| 用例 | 期望 | 实测 | 结论 |
| --- | --- | --- | --- |
| E1 入口页渲染 | 3 张实例卡 | `cards=3`，`names=["__MAIN__","demoA","demoB"]`，`主实例` pill = 1，三卡状态「● 就绪」，记录列为 `3/200`、`0/200`、`2/200`（读接口值） | PASS |
| E2 点击卡片跳转 | URL 为绝对路由 `/mq/demo/page?instance=demoA` | 点击 demoA 卡片后 `location.href = http://localhost:18100/mq/demo/page?instance=demoA` | PASS |
| E3 演示页信息条 | 出现实例名 | `document.title = "MQ 演示 · demoA"`；metrics `订阅1 主题 / 发送3 / 接收0 / 记录0 / 200`，与后端 `state` 完全一致 | PASS |
| E4 控制台无 JS 报错 | `error` / `unhandledrejection` 均无 | 注入监听后 reload，入口页、demoA 演示页、`__MAIN__` 演示页、`?instance=nope` 降级页 4 次采集 `errs=[]` | PASS |
| E5 全局副作用徽标 | 订阅卡含「影响所有浏览器会话（全局副作用）」 | `.side-effect-badge` 文本「⚠ 影响所有浏览器会话（全局副作用）」 | PASS |
| E6 消息流渲染 | 最新在上 + 区分 listener 名 | `__MAIN__` 页 `.log-line=3`，首行 `10:40:46.515 ▼收 aifei.mq.demo.topic hello 5B · PageDemoListener`，次行同内容 `· DemoMessageListener` | PASS |
| E7 未知实例整页降级 | 错误条 + 按钮禁用 + 不自动跳转 | `?instance=nope`：err-bar「未知实例：nope（可用实例: [__MAIN__, demoA, demoB]） 返回实例列表」，订阅按钮 `disabled=true`，URL 保持 `nope`（未跳转） | PASS |
| E8 空 Topic 从 UI 发起（D-01 可达性取证） | 应提示「topic 不能为空」 | 清空 Topic 输入后点「发送」→ 回执 `code=0 已发送到主题 aifei.mq.demo.topic … 该主题当前无订阅者，消息已被丢弃`，`发送` 计数 3→4 | **FAIL**（支撑 D-01 定级 P1：UI 可达） |

### 汇总计数

| 节 | 用例数 | PASS | FAIL |
| --- | --- | --- | --- |
| A 构建 | 3 | 3 | 0 |
| B 静态资源可达性 | 6 | 6 | 0 |
| C 端点契约矩阵 | 15 | 13 | 2（C10′、C11b） |
| D 硬约束静态检查 | 6 | 6 | 0 |
| E 页面冒烟 | 8 | 7 | 1（E8，与 C11b 同根因） |
| **合计** | **38** | **35** | **3**（去重后 2 个缺陷：D-01、D-02） |

---

## 3. 缺陷清单

### D-01（P1）空串 `topic` / `text` 被 `@Para` 默认值折叠，静默改发到默认主题/默认文本

- **现象**：`GET /mq/demo/send?instance=demoA&topic=&text=x` 返回 `code=0` 且**真实发送到** `aifei.mq.demo.topic`；`GET /mq/demo/send?instance=demoA&topic=t1&text=` 返回 `text:"hello"`；`GET /mq/demo/send/message?...&topic=&text=x` 同；前端清空 Topic 后点「发送」即可触发（E8）。
- **契约依据**：设计 §4.6/§4.7「`topic` 空白 → `10001`『topic 不能为空』；`text` 空白 → `10001`『text 不能为空』」；测试任务 C 节第 11 项。
- **对照证据**：`topic=%20` / `text=%20`（空白）**正确**返回 `10001`，说明仅「恰好空串」这一路径异常。
- **根因**：`cn.aifei.demo.common.arch.InParser#getStringValue`（第 66-69 行）把空串统一转 `null`（注释本意是「表单域无输入值 → null」），但该转换同时作用于 **query 参数**（`parseQueryPara` 第 58 行）；随后 `@Para(defaultValue = …)` 只在 `null` 时取默认值（`BasicArguments.StrArgument`）。设计 §0 A2 的前提「`?instance=`（空串）返回 `""` 而不是 null」与实现不符。
- **受影响文件**：`src/main/java/cn/hg/aifei/mq/demo/service/MqDemoService.java`（`send` / `sendMessage` 的 `topic`/`text`）；可选 `src/main/java/cn/aifei/demo/common/arch/InParser.java`；`src/main/webapp/assets/js/mq-demo.js`（`send()` 无本地非空校验，`canSend` 仅判 `locked/busy`）。
- **修正建议（择一，推荐 a）**：
  - **a（推荐，最小侵入）**：`send` / `sendMessage` 保留 `@Para` 默认值以兼容「省略参数 = 旧行为」，同时引入 `In` 参数区分「缺省」与「空串」：`if (in.has("topic") && StrUtil.isBlank(topic)) return Out.fail(OutCode.INVALID_PARAM).msg("topic 不能为空");`（`In.has` 用 `containsKey`，空串在 query 里 key 存在、值为 null，可判定）。
  - **b**：把 `InParser#getStringValue` 的「空串→null」限定为 form 数据，query 参数保留 `""`（符合注释本意，但需回归 `aifei-websocket-demo` 等既有 demo）。
  - **c（治标，不单独采用）**：前端补非空校验；API 契约仍不符。
  - 无论选哪条，都需同步修订设计 §0 A2 的错误前提陈述。

### D-02（P2）`?instance=`（空串）回退主实例，未返回 `10001`

- **现象**：`GET /mq/demo/state?instance=` → `code=0`、`instance:"__MAIN__"`；对照 `?instance=%20`（空格）→ `10001`。
- **契约依据**：PRD §5「`instance` 参数语义 · 非法值：未注册名 / **空串** / 含 `.`、`=`、`:` 或空白 → `code=10001`，msg 含「未知实例」+ 可用实例清单」；基线 §3.3「省略即旧行为 / 空串非法」两条同时成立。
- **降级理由（诚实边界）**：设计 §9 A2 已**预授权**该退化（「若框架把空串折叠为默认值，则空串行为退化为 `__MAIN__`（宽松），不影响其它契约」），故定 P2 —— 属于「实现遵从设计、但设计偏离 PRD/基线」的文档—契约不一致。
- **修正建议**：与 D-01 同根同修（方案 a 对 `instance` 同样适用：`if (in.has("instance") && StrUtil.isBlank(instance)) return 10001`）；若决定保留宽松行为，则必须在 `prd.md` §5 与 `baseline` §3.3 登记为**已知偏差**，并同步修订设计 §0 A2。

### D-03（P2，文档级）说明抽屉硬编码「上限 200 条」

- **现象**：`view/mq/demo.html:266` `<tr><td>GET /mq/demo/messages</td><td>消息记录（最新在前，上限 200 条）</td></tr>`。
- **影响**：仅说明文字；运行时渲染（`:40`、`:195`、`index.html:88`）均读接口字段，符合 PRD Q8 / 设计 R9 的本意，故不阻塞。
- **修正建议**：改为「上限见接口返回 `messageLogCapacity`」，避免库常量变更后文案失同步。

---

## 4. 未执行项清单（明确声明，未声称已验证）

1. **多浏览器会话并发实测**（R2「订阅/取消/清空影响同一实例的所有会话」）：仅用单会话核对代码与文案，未开两个真实会话互相观察。
2. **200 条容量边界压测**（写满 200 条后丢弃最旧一条、`sequence` 连续性）：核对 `MAX_ENTRIES=200` 与接口返回 `messageLogCapacity=200`，未做满量写入。
3. **轮询降频实测**（2000ms → 连续 3 次失败 → 5000ms → 成功恢复）：仅代码走查（`POLL_NORMAL/POLL_SLOW`），未做断网注入。
4. **`-DprintAction=true` 路由打印复核**：根路径已由 B1/E1 端到端证实可达，未额外开打印。
5. **跨浏览器兼容**：仅 Edge（headless）验证，未测 Chrome/Firefox。
6. **`copyLogs`/`navigator.clipboard` 回退路径**：未在无剪贴板权限场景验证。
7. **进程环境说明**：本报告为同一工程 + 统一 Maven 的**同环境**验收（含 1 次 stop/start 重启），未做独立数据/独立构建的隔离评测；防绕过依靠「Aifei 专属检查清单 + 对照锁定基线」双兜底（已在 A/D 节列出）。

> 关停确认：测试结束已执行 `stop-demo.bat`，`SUCCESS: The process with PID 21876 has been terminated`；`netstat -ano | findstr ":18100"` 无 `LISTENING` 记录（仅客户端 `TIME_WAIT`），**端口 18100 已释放**。

IS_PASS: NO（复审前）

## 复审（第 2 轮）

> 复审范围：仅验证第 1 轮遗留缺陷 **D-01（P1）**、**D-03（P2）** 的修复闭环 + 回归底线最小集；**未重跑**全量 38 用例。
> 执行时间：2026-10-17（复审轮）；环境同第 1 轮（Windows / Edge headless via Puppeteer / `start-demo.bat`，18100）。

### R-1 缺陷闭环表

| 缺陷 | 验证方式 | 静态证据（行号） | 实测结果 | 结论 |
| --- | --- | --- | --- | --- |
| **D-01（P1）** 空 topic/text 静默改发默认主题 | 代码走查 + 无头浏览器实测（含网络层请求计数） | `mq-demo.js:481-501`：`send()` 内、`requestEnvelope`（`:503`）**之前**的前置校验；topic `:481` `trim()` 后判空（`:487-488`），text `:489` `trim()` 后判空（`:490`）；失败分支 `:492-500` 置 `receipt.cls = "is-fail"`、文案 `code=10001 发送失败：topic 不能为空` / `text 不能为空`，随后 `return` **不发请求**。双入口收敛：发送按钮 `demo.html:161` `@click="send"`；消息体 `demo.html:157` `@keydown="onTextKeydown"` → `mq-demo.js:555-560` Ctrl/⌘+Enter → `send()` | 安装 fetch 间谍统计 `/mq/demo/send*` 请求后实测 4 项：① 空 Topic 点发送 → 红框「code=10001 发送失败：topic 不能为空」，请求 0 次；② 空 text 点发送 → 红框「code=10001 发送失败：text 不能为空」，请求 0 次；③ Ctrl+Enter 空 Topic → 红框「topic 不能为空」，请求 0 次；④ Ctrl+Enter 正常内容（topic=t-recheck）→ 绿框「code=0 已发送到主题 t-recheck」含 messageId=2，恰 1 次真实请求 | **CLOSED** |
| **D-03（P2）** demo.html 写死「上限 200 条」 | grep 源码 + 逐条判读 | `demo.html:266` 已改为「消息记录（最新在前，上限读接口的 `<code>messageLogCapacity</code>`）」；全文件 grep `200` 仅剩 `:274`「HTTP 恒 200，业务结果看响应里的 code」（接口说明注释，属正常命中），运行时动态值与 CSS 数值无异常命中 | 文案口径与 PRD Q8 / 设计 R9 一致 | **CLOSED** |

> 复审过程说明（诚实记录）：初次用 Puppeteer `fill` 清空 Topic 后点发送曾出现绿色回执（code=0），核查确认系**测试工装问题**——`fill` 未触发 Vue `v-model` 的 `input` 事件（输入框实际值仍为默认主题），该次误发不计缺陷；改用原生 value setter + `dispatchEvent(new Event('input'))` 后复测，上述 4 项全部符合预期。

### R-2 回归底线（最小集）

| # | 检查项 | 实测 | 结论 |
| --- | --- | --- | --- |
| 1 | `GET /` 200 且含关键文案 | `ROOT:200`，含 `aifei-mq-demo · MQ 演示入口`、`mq.names = demoA,demoB` | PASS |
| 2 | `GET /mq/demo/page?instance=demoA` 200 且含关键文案 | `PAGE:200`，含「消息流」「⚠ 影响所有浏览器会话（全局副作用）」 | PASS |
| 3 | 正常发送仍成功 | 复审 R-1 ④：绿框 `code=0`，回执含 `messageId=2 / delivered=0`（demoA/t-recheck 无订阅者，丢弃符合 PRD §7.1） | PASS |
| 4 | `node --check` 两个 js | `mq-demo.js`、`mq-index.js` 均通过（`SYNTAX_OK`） | PASS |

### R-3 关停确认

`stop-demo.bat` → `SUCCESS: The process with PID 19556 has been terminated`；`netstat -ano | findstr ":18100"` 无 `LISTENING` 记录（仅客户端 `TIME_WAIT`），**端口 18100 已释放**。复审期间向 demoA 内存实例发送的测试消息随进程终止清空，无残留。

### 复审结论

- D-01（P1）：**闭环**（前端前置校验拦截于请求之前，双入口收敛，网络层 0 请求取证）。
- D-03（P2）：**闭环**（写死文案已改为「上限读接口的 messageLogCapacity」口径）。
- 回归底线 4 项全部 PASS。
- 路由判定：**NoOne**（全部通过，无需再派发）。

IS_PASS: YES
