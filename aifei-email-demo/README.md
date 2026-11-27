# aifei-email-demo

`aifei-email` 的演示工程（**不发布**）：端口 **18190**，离线可跑、零外网、零真实邮件。发送经 mock 内核客户端（`send` 不抛异常即成功），**全链路真实**走 `MailKit` → `SmtpMailSender` → 消息映射 → 内核客户端。

## 启动

先安装库模块到本地仓库：

```bash
cd ..\aifei-email && mvn -q -DskipTests clean install
```

再双击 `start-demo.bat`（或命令行执行），访问：

```
http://localhost:18190/email/demo/page
```

停止用 `stop-demo.bat`（按端口 18190 结束进程）。

依赖口径：demo 以 `compile` 自带 `aifei` 与 `org.dromara.sms4j:sms4j-Email-core`，用于验证库本体 `provided` / `optional` 的声明；这**不改变**库的零强制依赖口径。

## 六场景

| 场景 | 页面入口 | 说明 |
| --- | --- | --- |
| 1 发送邮件 | 「发送（mock）」 | 真实走 `MailKit.use(channel)` → `SmtpMailSender` → mock 内核；支持纯文本 / HTML 内容 / HTML 模板 + 附件字节，展示 `channel` / `success` / `message` |
| 2 多通道切换 | 通道下拉 + 「刷新通道」 | 同一段发送代码，切换 `qq` / `corp` 后结果中的通道标识不同 |
| 3 配置错误态 | 「触发必填项缺失」 | 只调用配置校验（同包桥接，**不触发装配**），消息由库内 `MailErrors` 真实构造，含键名与原值 |
| 4 依赖缺失态 | 「模拟未引入 sms4j-Email-core」 | 隔离类加载器触发依赖探测失败，消息含坐标与引入提示 |
| 5 HTML 模板渲染 | 「渲染预览并发送」 | 模板在 classpath `template/notice.html`，占位符 `#{变量名}`；预览由 demo 展示层同名替换生成，发送仍走真实链路 |
| 6 空态 | 进入页面 / 「清空展示」 | 首次加载自动复位为占位文案，不残留上一次数据 |

## 端点

信封 `code` / `data` / `msg`，HTTP 状态恒 200（语义由 `code` 承载，`0` 为成功）：

- `GET /email/demo/page` — 演示页
- `POST /email/demo/send` — body `{channel, to, subject, bodyType(text|html|template), content, templateName, variables, attachmentName, attachmentContent}`；返回 `data: {channel, success, message}`
- `GET /email/demo/channels` — `data: {channels: [...], default: "..."}`
- `GET /email/demo/config-error` — `data: {error: "email: config email.qq.from-address is required but was: <empty>"}`（校验真实触发，不改全局状态）
- `GET /email/demo/dependency-missing` — `data: {error: "email: org.dromara.sms4j:sms4j-Email-core:3.3.6 is required, add it to your dependencies"}`
- `POST /email/demo/html-template` — body `{templateName, variables, channel?, to?}`；`data: {templateName, renderedHtml, channel, success, message}`
- `GET /email/demo/state` — `data: {sms4jAvailable, version, mock, channels}`

> `/config-error` 与 `/dependency-missing` 取的是库内**真实构造**的消息（分别经配置校验与依赖探测），不是页面硬编码字符串。

## 实现要点（为什么这样接）

- **mock 注入**：`cn/hg/aifei/email/internal/smtp/MailMockBridge.java` 与库适配层**同包**（split package），直接调用包级注入点 `SmtpClientFactory.setClientFactory(...)`，**不用反射**；`send` 不抛异常即模拟成功。
- **配置错误态隔离**：`cn/hg/aifei/email/aifei/ConfigBridge.java` 与配置解析同包，只调用 `EmailConfigLoader.load(prop)`（校验，不装配）。若走 `EmailPlugin.start()` 失败路径会 `MailClients.clear()`，污染正在运行的注册表，使后续发送全部失败——故本端点**绝不**进 `start()`。
- **依赖缺失态**：同一桥接类调用包级 `MailDeps.requireSms4j(隔离 ClassLoader)`，隔离加载器对 `org.dromara.email.*` 抛 `ClassNotFoundException`，得到真实消息。
- **模板预览**：`renderedHtml` 由 demo 展示层自行做同名 `#{变量名}` 替换（仅页面预览）；库侧只承诺「把变量交给内核」的语义，不经库公共面、不引模板引擎。

## 零外网说明

页面与脚本资源全部本地（无 CDN、无外链）；发送由 mock 内核客户端接管，**不建立任何 SMTP 连接**；页面无任何外部字体 / 图标 / 图片请求。

## 目录

```
aifei-email-demo/
├── pom.xml / start-demo.bat / stop-demo.bat / README.md
└── src/main/
    ├── java/cn/hg/aifei/email/
    │   ├── demo/            DemoApp / DemoAppConfig / arch（自写 HIO）/ console（自带日志）/ mock / service
    │   ├── internal/smtp/   MailMockBridge（同包桥接：mock 注入 + 依赖探测）
    │   └── aifei/           ConfigBridge（同包桥接：配置校验）
    ├── resources/           app-config.txt（两个通道）/ template/notice.html
    └── webapp/              view/email/index.html + assets/{css,js}
```
