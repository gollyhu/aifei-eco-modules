# aifei-sms-demo

`aifei-sms` 的演示工程（**不发布**）：端口 **18180**，离线可跑、零外网、零真实短信。发送经 mock `SmsBlend` 返回预设 `SmsResponse`，回执经真实 `SmsHandler` 验签解析。

## 启动

先安装库模块到本地仓库：

```bash
cd ..\aifei-sms && mvn -q -DskipTests clean install
```

再双击 `start-demo.bat`（或命令行执行），访问：

```
http://localhost:18180/sms/demo/page
```

停止用 `stop-demo.bat`。依赖树中 demo 以 `compile` 自带 `sms4j-core` 与 `aifei`，验证库的 `optional` / `provided` 口径，不改变库本体零强制依赖口径。

## 六场景

| 场景 | 页面入口 | 说明 |
| --- | --- | --- |
| 1 模板化发送 | 「发送（mock）」 | 真实走 `SmsKit` → `SmsSender` → mock `SmsBlend`，展示状态与流水号 |
| 2 多通道切换 | 通道下拉 | 切换后发送代码不变、结果落点（通道标识 / 流水号）不同 |
| 3 配置错误态 | 「触发必填项缺失」 | 由 `SmsConfigLoader` + `SmsErrors` 真实构造消息，含键名与原值 |
| 4 依赖缺失态 | 「模拟未引入 sms4j」 | 由 `SmsDeps` 隔离探测 + `SmsErrors` 真实构造坐标消息 |
| 5 回执接收 | 「模拟厂商回调」 | 生成阿里云签名 → POST `/sms/callback/{channel}` → `SmsHandler` 验签解析 → 监听器记录；可切换错误签名观察 403 |
| 6 空态 | 进入页面 / 「清空回执」 | 首次加载自动清空，列表显示占位文案 |

## 端点

信封 `code` / `data` / `msg`，HTTP 状态恒 200（回执 Handler 端点除外，按 §8 响应契约返回 200/403/404/500）：

- `GET /sms/demo/page` — 页面
- `POST /sms/demo/send` — body `{channel, phone, templateId, variables}`
- `GET /sms/demo/channels` — `{channels, default}`
- `GET /sms/demo/config-error` — `{error}`
- `GET /sms/demo/dependency-missing` — `{error}`
- `POST /sms/callback/{channel}` — 真实回执 Handler（form 或 JSON body + `signature`）
- `GET /sms/demo/receipts` — `{receipts: [...]}`
- `GET /sms/demo/state` — `{sms4jAvailable, version, channels}`
- `POST /sms/demo/reset` / `POST /sms/demo/receipt-sign` — 空态复位 / 回执签名辅助

## 零外网说明

页面资源全部本地（无 CDN）；发送由 mock 注入点返回预设响应；`receipt-sign` 仅用 JDK HMAC-SHA1 生成演示签名，验签由真实 `SmsHandler` 完成。无任何厂商 HTTP 调用。
