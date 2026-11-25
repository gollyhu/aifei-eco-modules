# aifei-i18n-demo

aifei-i18n 的示例工程（**示例工程，不发布**，版本 `1.0.0`），用**真实 Enjoy 引擎**渲染演示三条指令与语言包下发，全程离线（端口 18170）。

> 启动前先把库安装到本地仓库：`cd ..\aifei-i18n && mvn -q -DskipTests clean install`。

## 启动

```bat
start-demo.bat        :: 编译 + 组 classpath，HTTP 监听 18170
stop-demo.bat         :: 按端口 18170 结束进程
```

验证地址：<http://localhost:18170/>（自动进入 `/i18n/demo/page`）

## 端点

| 端点 | 说明 |
| --- | --- |
| `GET /i18n/demo/page?lang=xx` | 演示主页面（真实 Enjoy 渲染），`lang` 可选（首屏落 `i18n.defaultLocale=zh-CN`） |
| `GET /i18n/demo/bundle?locale=xx` | 语言包四字段 JSON（`locale`/`localeName`/`dir`/`messages`），前端控件文案来源 |
| `GET /i18n/demo/rtl` | RTL iframe 页，模板顶部 `#lang('ar-SA')` → `#i18n(key)` → `#dir()` |
| `GET /i18n/demo/money?locale=xx&amount=yy` | `FormatKit` 货币展示，符号/小数位由 JDK 产出（英文显 `$`，不写死） |
| `GET /i18n/demo/header`（头 `Accept-Language: ja-JP`） | 请求头经 `I18nHandler` 绑定 locale，文案自动切换（demo 只用公开 API） |
| `GET /i18n/demo/editable?locale=xx` | Db 中该 locale 已有 key 列表 `{locale, items:[{key,value}]}`，供编辑区回填 |
| `POST /i18n/demo/save` | JSON `{locale,key,value}`：只 UPDATE 已有行，命中后 `I18nKit.refresh()`；未命中/参数空白返回 fail |

## Db 热重载

`i18n.sources = db,properties`：db 优先、properties 兜底；`I18nDbSeed` 启动时把 `i18n.properties` 迁入 H2 内存库 `i18n_message`，首屏文案与纯 properties 模式一致。主页面底部编辑区选中 key、改值保存后整页重载即可看到新文案。

- `/editable` 只列 Db 表中已有 key；`/save` 只执行 `UPDATE`，不新增 key。
- UPDATE 成功后立即 `I18nKit.refresh()` 清 TTL 缓存，否则 60 秒内仍读旧值。
- 内存库 `DB_CLOSE_DELAY=-1` 随 JVM 存活、**重启复位**；需要持久化时把 `I18nDbSeed` 的 JDBC URL 改为文件库（如 `jdbc:h2:./i18n-demo-db;MODE=MySQL;DATABASE_TO_LOWER=TRUE`），建表与迁移逻辑不变。
- SQL 全部参数化；若手工写 SQL 字面量，单引号须用 `''` 转义。

## 三条指令演示

```text
#i18n('hello', lang='ar-SA', 'Bob')   → 局部覆盖：固定阿拉伯语，不改页面默认值
#i18n('hello', 'Bob')                  → 页面级 #lang('xxx') 设值后跟随
#lang() / #lang('zh-CN')                → 页面语言读 / 写
#dir() / #dir('rtl')                    → 页面方向读 / 显式覆盖（仅 ltr/rtl）
```

语言取值唯一优先级链：`#i18n(..., lang='xxx')` > `#lang('xxx')` > `#lang()`（当前生效 locale）。

## 演示点核对

| 观察点 | 实现位置 |
| --- | --- |
| 首屏中文，取 `i18n.defaultLocale=zh-CN` | `app-config.txt` + 模板 `#lang(lang)`（lang 落 defaultLocale） |
| 醒目区 `locale`/`localeName`/`dir` 随切换更新 | 状态卡：`#lang()` / `${localeName}`（Service 放 model）/ `#dir()` |
| 中/英/日下拉整页真实重渲染 | 下拉 `onchange → ?lang=xxx`，模板顶部 `#lang(lang)` 设页面级 |
| iframe 内中东文字 RTL | `/rtl` 页面 `#lang('ar-SA')` → `#i18n(key)`，`<html dir="rtl">`，`#dir()`=rtl |
| 表单 8 项 + 表格 3 项文案随切换；英文显美元符；表格仅外观 | 控件文案走 `/bundle` JSON 填 `data-i18n`；货币符走 `/money`（FormatKit+JDK）；查询/重置仅弹 toast |
| 局部 lang > 页面级 > 无参 #lang()；局部不污染 | 三卡并列展示，局部卡固定 ar-SA，其余随页面切换 |

## 技术约束落点

- 三条指令注册于 `arch/IoHandler` 构造引擎时（`DirectiveRegistrar.register(engine)`，一次）。
- `render` 前后 try/finally 调 `DirectivePageState.clear()`（渲染前先清 + finally 清），同线程连续渲染不串页（AC-INC-12）。
- 语言包为 classpath 本地资源 `i18n.properties`（zh-CN/en-US/ja-JP 全量，ar-SA 少量，其余走回退链）；零外网。
- 日志工厂为 demo 自带 `ConsoleLogFactory`（System.out），零日志绑定。

端口 18170 已避开仓库其它 demo。
