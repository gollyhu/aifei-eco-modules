# aifei-i18n 阶段一页面原型

纯前端静态原型，演示指令能力的可视化效果，不实现后端、不写 Java。

## 打开方式

直接双击 `index.html` 用浏览器打开（`file://` 协议即可），零外网依赖：

- 字体通过 `@import` 引入 Google Fonts，断网时回退到 `'JetBrains Mono', Consolas, monospace` 与 `'IBM Plex Sans', 'Segoe UI', sans-serif`，渲染不受影响。
- RTL 区域用 `<iframe srcdoc>` 内嵌，无外部页面；全文件无外链 JS / CSS、无框架、无第三方库。

## 交互入口

顶部「界面语言」下拉为唯一语言切换入口，含中文 / 英文 / 日文三项。切换会统一重渲染全页文案、货币符号与 P-06 卡片值。

## P-01 ~ P-06 覆盖对照

| 编号 | 需求要点 | 原型实现位置 | 如何观察验证 |
| --- | --- | --- | --- |
| **P-01** | 首屏默认取 `i18n.defaultLocale`，未选择时中文；配置可见 | 顶部配置徽标 `i18n.defaultLocale = zh-CN`；脚本末行 `render('zh-CN')` | 首次打开全文中文，徽标显示 `i18n.defaultLocale = zh-CN`；未动下拉前 `#lang()` 读回值即为 zh-CN |
| **P-02** | 醒目位置同时展示 `locale` / `localeName` / `dir`，切换后同步，dir 与方向一致 | 顶部「页面元数据」卡片内三张 metric（`m-locale` / `m-localeName` / `m-dir`） | 三值固定同屏可见；切到 English 变为 `en-US / English / ltr`；`dir` 取自语言包且与 `<html dir>` 同步 |
| **P-03** | 中 / 英 / 日下拉切换，整体文案（含 P-05）同步，体现页面级默认语言设为所选项 | 「界面语言」下拉 `#langSelect` + 统一 `render(locale)` | 选 English 后标题、状态卡、表单、表格、P-06 全部变英文；下拉旁提示「页面级默认语言 = 所选项」 |
| **P-04** | iframe 内中东文字 RTL；按口径先 `#lang('ar-SA')` 再 `#i18n(key)`；以徽标标注口径说明 | 「中东文字 RTL 展示」区 `#rtl-frame`（`srcdoc`，`<html dir="rtl" lang="ar-SA">`）+「RTL 实现方式」说明徽标 | iframe 内阿文自右起排列，`dir=rtl`；可见 `#lang('ar-SA') → #i18n(key)` 口径注释 |
| **P-05** | 表单 8 项 + 表格 3 项文案随语言切换；货币符 en `$` / zh `¥` / ja `￥`；表格仅外观、查询不真实执行 | 「查询条件」表单 +「数据列表」表格；文案全部经 `data-i18n` / `data-i18n-placeholder` 走语言包 | 切语言逐项核对：①placeholder ②请选择 ③选择日期 ④复选框 ⑤单选 ⑥开关 ⑦货币符 ⑧查询/重置；表格表头/空提示/分页同步；点查询/重置出 toast 且无后端请求 |
| **P-06** | 三卡并列展示局部 `lang=` > 页面级 `#lang('xxx')` > 无参 `#lang()`；局部不污染页面默认 | 「语言取值优先级链」三张卡片 + 模板代码示例 | 切页面语言时①局部卡固定为阿拉伯语 `مرحباً، Bob!` 不变，②页面级与③无参卡随语言变化；代码示例给出三种写法 |

## 设计系统

- 深色开发者工具风（Dark Mode OLED / Documentation Dashboard），配色严格采用需求给定 CSS 变量。
- 标题与代码用 JetBrains Mono，正文用 IBM Plex Sans；内联 SVG 线性图标（无 emoji）。
- 键盘焦点环、hover 过渡 180ms、极简 glow；`prefers-reduced-motion` 下关闭动画。
- 响应式覆盖 375 / 768 / 1024 / 1440px；状态卡与优先级卡随宽度重排。

