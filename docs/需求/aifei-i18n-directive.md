# aifei-i18n Enjoy 模板指令 增量需求文档

> ## ⛔ 已废弃归档声明（需求关，2026-11 修订）
>
> **本文已废弃，不再是需求事实来源，禁止作为设计/开发/测试依据。** 唯一现行版本为 [`aifei-i18n.md`](./aifei-i18n.md) **§十一「Enjoy 模板指令增量需求」**（2026-11-19 已并入）。本文件仅为历史留痕而保留，全文口径以合并版 §11 为准：
> - 公共面门禁为**精确 10 类型**（本文残留的「9 类型」为过期值）；
> - 取文案 API 为 `I18nKit.local(lang).message(key[, args...])` 链式入口与 `I18nKit.message(key[, args...])` 默认入口（本文残留的 `message(key, lang, args...)` 形态已随 2026-11-19 重构废弃，不存在该 public 重载）；
> - Enjoy 指令 API spike **已完成**（2026-11-19），无「待实测/待逐字核对」事项。
>
> 下列正文为废弃原文，仅作审计追溯，请勿据此实现。

> **文件定位（废弃原文）**：本文是对 `aifei-i18n.md`（第四轮定档 PRD）的**增量需求**——新增三条 Enjoy 模板指令，并**显式突破原 Q7 定档边界**。原 PRD 的 Q1~Q9 裁决与 16 条 P0 / 75 条 AC 除本文明确变更处外全部继续有效。
> **关联**：基线 `docs/需求/aifei-i18n.md`；实现模块 `aifei-i18n/`；模板内核 `aifei-enjoy`（框架自身模块，已内含于 `aifei-all:1.1.2`）。
> **状态**：Enjoy 指令 API spike **已实证成立**（2026-11-19，见 §七），方案不需回退；Demo 进入「先原型确认、后实现」两阶段（见 §九）。
> **实证证据**：本地仓库 `C:/IDEs/apache-maven-repository/cn/aifei/aifei-enjoy/1.1.2/`（`jar` + `sources.jar`）+ Gitee 官方源码 `jfinal/aifei` 的 `aifei-enjoy/src/main/java/cn/aifei/enjoy/`。

---

## 一、增量目标与范围

**一句话**：为服务端 Enjoy 模板提供 `#i18n(...)` / `#lang()` / `#dir()` 三条指令，让页面渲染既能用页面默认语言、也能对单条文案局部指定语言取文案，并输出页面的语言与方向元数据。

**给谁用**：使用 aifei + Enjoy 做服务端渲染的应用开发者。

**成功标准**（3 条）：
1. 三条指令在**真实 Enjoy 引擎**中跑通，`lang` 具名参数位置无关提取的规则单一、可测试；
2. 指令层只做「抽 `lang` + 切位置 + 分发到 `I18nKit` 既有重载」，零新增解析逻辑；
3. 模块公共面精确 10 类型与「公共签名零第三方类型」约束不变（原文「9 类型」为加 Enjoy 指令前的过期值，已按合并版 §11 与源码统一为 10）。

**本期做**：三条指令 + 参数解析规则 + 页面级/局部语言分层 + 真实 Enjoy 端到端用例（含 demo）。

**本期不做**：前端运行时指令（前端仍走语言包 JSON）、格式化指令（复用 `FormatKit` 由 Service 侧调用，模板层本期不暴露）、`lang=` 之外的具名参数、整包导出指令。

---

## 二、Q7 定档边界突破声明（核心变更）

原 PRD `Q7` / `D-02` 定档为「**不新建 demo 工程 + 不引入 Enjoy 依赖**」，以测试内模拟函数 `Function tpl = k -> I18nKit.local(locale).message(k)` 做等价输出断言（旧 `I18nKit.message(k, locale)` 形态已随 2026-11-19 重构废弃，不得再引用）。本增量**显式突破这两条**，理由与影响如下：

| 原定档 | 本增量决策 | 突破理由 | 影响面 |
| --- | --- | --- | --- |
| 不新建 demo，模板用例落模块测试内 | **新增 `aifei-i18n-demo` 示例工程**，以真实 Enjoy 渲染验证指令（端口与排期由设计关定） | 指令契约的核心是「具名参数与位置参数混排、位置无关提取」，模拟函数无法覆盖指令解析行为，必须真实引擎跑通 | D-02 升级；demo 为示例工程、不发布 |
| 不引入 Enjoy 依赖，零第三方 | **复用 `aifei-all:1.1.2` 已内含的 `aifei-enjoy`**（spike 实证：aifei-all 的 shade 配置已打包 aifei-enjoy），**零新增依赖坐标、零 pom 改动** | 真实指令必须依赖引擎才能注册与解析；框架已把引擎打进既有 provided 依赖，无需另引 | 「零第三方」口径明确为「零**非 aifei 生态**第三方」；Enjoy 类型仅在指令实现与 demo 内可见，**不进入任何公共 API 签名**，根包零第三方类型约束不变 |

**变更效力**：Q7 裁决在本增量范围内**失效**；其余 Q1~Q9 裁决、16 条 P0 / 75 条 AC 保持不变。原「同一入口、同一回退语义」的根本约束继承——所有指令最终都走 `I18nKit` 既有重载，不另起解析。

---

## 三、指令契约（精简）

### 3.1 `#i18n(...)` —— 取文案

**参数解析规则（单一、位置无关）**：
1. 扫描所有实参，抽出唯一的具名参数 `lang=`；
2. 剩余实参按原顺序：**第一个 = key，第二个起 = args...**（`{0}` 位置参数）；
3. 未给 `lang` → 用当前页面/请求 locale。

**三式等价**（必须全部成立）：

```
#i18n('hello', lang='zh-CN', 'Bob')
#i18n(lang='zh-CN', 'hello', 'Bob')
#i18n('hello', 'Bob', lang='zh-CN')
→ 均为 key=hello, lang=zh-CN, args=[Bob]
```

**分发映射**（薄封装，零新逻辑）：

| 解析结果 | 分发到（现行 `I18nKit` 链式入口，旧 `message(key, lang, ...)` 形态已废弃） |
| --- | --- |
| 有 lang | `I18nKit.local(lang).message(key[, args...])` |
| 无 lang（当前 locale） | `I18nKit.local(effLang).message(key[, args...])`（`effLang` 为当前生效 locale） |

> 说明：旧表四式 `message(key, lang, args...)` / `message(key, lang)` / `message(key, null, args...)` / `message(key)` 系 2026-11-19 重构前口径，public API 中并无 `message(key, lang, ...)` 重载（仅内部 `I18nEngine.message(key, localeTag, args...)`）；现行两入口为 `I18nKit.local(tag).message(key[, args...])` 与 `I18nKit.message(key[, args...])`（默认 local）。以合并版 §11.3.1 分发映射为准。

**边界**：`lang` 重复 → IAE；`lang` 非法（`''` / `zh_CN_` 等）→ IAE（沿用 AC-08-7 口径），`zh_CN` 合法并规范化为 `zh-CN`；缺 key → IAE；字符串字面量中的 `lang=` 文本不得被误判为具名参数。

### 3.2 `#lang()` / `#lang('xxx')` —— 页面语言（读写对称）

- `#lang()` → 读当前渲染页面生效 locale；
- `#lang('zh-CN')` → **设置页面级默认语言**，其后无 `lang` 的 `#i18n` 均跟随该值；非法 BCP 47 → IAE。

### 3.3 `#dir()` / `#dir('xxx')` —— 页面方向（读写对称）

- `#dir()` → 读当前 dir（`ltr` / `rtl`）；
- `#dir('rtl')` → **显式覆盖页面方向**，仅接受 `ltr` / `rtl`，其它 → IAE；判定只走 JDK 公开 API，禁自建方向表（沿用 Q9）。

**分层（关键）**：`#lang('xxx')` / `#dir('xxx')` 是**页面级默认**；`#i18n(..., lang='xxx')` 是**单条局部覆盖**，不改变页面默认值。二者互补，不可相互替代。

---

## 四、场景举例（精简）

**场景 1｜中文页嵌入一条阿拉伯语文案**（局部覆盖，页面仍是中文）：

```
#lang('zh-CN')                          // 页面默认中文
<h1>#i18n('page.title')</h1>            // 中文（跟随页面默认）
<aside dir="rtl">#i18n('hello', lang='ar-SA', 'Bob')</aside>  // 仅此条阿拉伯语
```

**场景 2｜带 `{0}` 参数、lang 位置无关**：

```
#i18n('hello', lang='zh-CN', 'Bob')   // "你好，Bob"
#lang('zh-CN') → #i18n('hello', 'Bob', lang='zh-CN')  // 与上逐字等价
```

---

## 五、增量 P0 需求与验收标准

| 编号 | 需求 | AC（Given / When / Then 要点） |
| --- | --- | --- |
| **REQ-INC-P0-01** | 具名参数位置无关 | AC-INC-01：Given §3.1 三式写法；When 各取同一 key；Then 三者输出逐字相等<br>AC-INC-02：Given `lang` 置于首/中/尾；When 解析；Then 均提取到 lang、剩余参数顺序不变 |
| **REQ-INC-P0-02** | 参数分发与插值 | AC-INC-03：Given `hello {0}` + args=[Bob]；When 取文案；Then 返回 `hello Bob`，缺参占位符原样保留（沿用 AC-01-2）<br>AC-INC-04：Given 无 lang；When 取文案；Then 走当前页面/请求 locale |
| **REQ-INC-P0-03** | 页面级与局部语言分层 | AC-INC-05：Given `#lang('zh-CN')`；When 取无 lang 文案；Then 跟随 zh-CN<br>AC-INC-06：Given 页面 lang=zh-CN 且单条 `lang='ar-SA'`；When 两者都取；Then 单条为阿拉伯语且 `#lang()` 仍为 zh-CN（页面值未被污染） |
| **REQ-INC-P0-04** | `#dir()` 读写与降级 | AC-INC-07：Given `#dir('rtl')`；When `#dir()`；Then 返回 rtl；非法值 → IAE<br>AC-INC-08：Given dir 判定能力不可用；When 渲染；Then dir=ltr + WARN，不抛（沿用 AC-14-5） |
| **REQ-INC-P0-05** | 异常与边界 | AC-INC-09：Given `lang` 重复 / 非法 / 缺 key；When 渲染；Then IAE，消息可定位<br>AC-INC-10：Given args 中含字符串字面量 `"lang='x'"`；When 解析；Then 该串视为普通参数、不被当作具名参数 |
| **REQ-INC-P0-06** | 真实引擎端到端 | AC-INC-11：Given demo 内真实 Enjoy 渲染 §4 场景；When 发起多语言请求；Then 输出与预期逐字相等，零联网<br>AC-INC-12：Given 同线程两次不同语言请求；When 渲染；Then 各自语言正确、不串（沿用 AC-08-5 finally 清理） |

---

## 六、关键约束与唯一定义点

- **薄封装**：指令只抽 `lang`、切位置、分发到 `I18nKit` 新 API（`local` 链式 / `message` 默认 local，旧「三重载」表述已废弃）；回退链、locale 规范化、locale 解析优先级、消息插值各仍只有原唯一定义点，指令层不得复制。
- **dir 数据源**：只走 JDK `ComponentOrientation`，禁语言码→方向映射表；`#dir('xxx')` 的显式覆盖仅作用于本次页面渲染的元数据输出，**不改变** `I18nKit.bundle(...)` 导出语言包的 `dir` 值。
- **公共面不变**：指令类置于 `internal/`（非承诺 API，可 public 供注册），不计入根/web/aifei 三包的公共面 10 类型门禁（原文「9 类型」为过期值）；Enjoy 类型不出现在任何公共签名。
- **依赖口径**：Enjoy 仅作指令实现与 demo 的依赖，不向消费方传递；不引非 aifei 生态第三方。
- **异常语义**：全部沿用既有 IAE / WARN / DEBUG 口径，不为模板另设一套。

---

## 七、Enjoy 指令 API spike 实证结论（已完成，2026-11-19）

> 取证方式：只读调研——本地 `aifei-enjoy-1.1.2-sources.jar` + Gitee 官方源码 `jfinal/aifei` 的 `aifei-enjoy` 模块。**结论：§3 契约全部成立，无需退回 `#i18nArgs` 方案；spike 已完成（2026-11-19）。**（说明：Gitee 取的是 main 分支；`Assign.getId()` 需求源自 jfinal.com/share/379、`RenderDirective` 为长期标准指令，1.1.x 与 main 在此处一致。1.1.2 版本逐字核对已随合并版 §11.7 取证路径闭环——本地 `D:/Apache/MavenRepository/cn/aifei/aifei-enjoy/1.1.2/` 的 `sources.jar` + Gitee `jfinal/aifei`，不再列为待办。）

| spike 问题 | 结论 | 源码证据 |
| --- | --- | --- |
| ① 能否声明并取到具名参数 `lang=` | ✅ 成立 | `Directive.java` javadoc 明写「使用赋值表达式可实现参数传递」，示例 `#render("_hot.html", title="热门新闻", list=newsList)`；具名参数 = `Assign` 赋值表达式，原生支持 |
| ② 能否拿到混合参数及其顺序 | ✅ 成立 | `exprList` 保留书写顺序；`RenderDirective.setExprList` 用 `exprList.getExpr(i) instanceof Assign` 逐个判别；`exprList.evalExprList(scope)` 按序求值返回 `Object[]` |
| ③ 字符串字面量会否被误判为具名参数 | ✅ 不会 | `"lang='x'"` 是字符串常量表达式、非 `Assign` 节点，天然不误判（满足 AC-INC-10） |
| ④ 指令基类与注册入口 | ✅ 明确 | 基类 `cn.aifei.enjoy.Directive extends Stat`；注册 `Engine.addDirective(name, Class<? extends Directive>, keepLineBlank)`（另有两参重载）；取具名参数名用 `Assign.getId()`（javadoc：「在自定义指令中得到 id 值……有助于扩展」）、取值用 `Assign.getRight().eval(scope)` |

**派生结论**：
- **零依赖改动**：`aifei-all-1.1.2.pom` 的 maven-shade-plugin `artifactSet` 已包含 `cn.aifei:aifei-enjoy`（第 129–135 行），本模块现有 `aifei-all(provided)` 已提供全部 Enjoy 类型。§2 表格据此修正。
- **实现路径清晰**：指令继承 `Directive` → `setExprList` 中用 `instanceof Assign && getId().equals("lang")` 抽 lang（重复则 IAE）、其余按位置切 key/args → `exec` 调 `I18nKit` 对应重载 → `writer.write(...)` 输出。与 §3 分发映射完全吻合。
- **具名参数语义提醒**：`Assign.eval(scope)` 会把值写入 scope（ wisdom/local/global 三种赋值模式）。指令内取 `lang` 值应走 `getRight().eval(scope)` 读值，避免具名参数意外污染页面作用域；`#render` 用 `Ctrl.setLocalAssignment()` 包裹求值正是为此。

## 八、待明确事项（降级为设计细节，不阻塞）

1. **demo 端口与排期**：`aifei-i18n-demo` 端口、启动脚本，与仓库现有 demo 端口不冲突；按 §九先出原型、确认后再实现。
2. **`#dir('xxx')` 与导出 dir 的一致性**：本文已定档「显式覆盖仅影响页面渲染、不影响 bundle 导出」；若需改变须显式裁决并回写。
3. ~~**1.1.2 版本逐字核对**：agent 模式下解压本地 `aifei-enjoy-1.1.2-sources.jar` 复核 `Assign.getId()` / `getRight()` 存在性~~ **— 已闭环（2026-11-19，不再待办）**：spike 已据本地 1.1.2 sources jar（`D:/Apache/MavenRepository/cn/aifei/aifei-enjoy/1.1.2/`）+ Gitee `jfinal/aifei` 官方源码逐字确认逻辑成立；结论见合并版 §11.7。

---

## 九、Demo 交付流程（新增要求：先原型确认，后实现）

`aifei-i18n-demo` 不再直接编码，改为**两阶段交付**——原型确认是 Demo 实现的前置门禁，未确认不得进入编码：

**阶段一｜页面原型（本次先做）**
- 用 UI/UX 原型能力生成 demo 页面原型，覆盖指令能力的可视化演示场景：
  1. 页面默认语言切换（对应 `#lang('xxx')`）——同一页面在中文 / 阿拉伯语下整体渲染差异（含 RTL 镜像）；
  2. 单条局部语言覆盖（对应 `#i18n(..., lang='xxx')`）——中文页中嵌入阿拉伯语/英文的个别文案，页面其余部分不变；
  3. `{0}` 参数插值 + `lang` 位置无关——带人名的问候语，展示三种等价写法输出一致；
  4. 语言包元数据展示——`locale` / `localeName` / `dir` 的呈现（对应 `#lang()` / `#dir()` 读值）。
- 原型产物：页面结构、布局、状态（语言切换前/后）、文案占位，供用户确认。
- **确认门禁**：用户确认原型后，才进入阶段二；原型被修改则回写本文档的场景清单。

**阶段二｜Demo 实现（原型确认后）**
- 按确认的原型实现 `aifei-i18n-demo`（真实 Enjoy 渲染，示例工程不发布）；
- 端口与启动脚本对齐仓库现有 demo 风格，零外网依赖；
- 验收：AC-INC-11 / AC-INC-12 在 demo 中通过。

**本阶段产物状态**：本次仅落盘「阶段一」的原型需求（本节）与 spike 实证结论（§七）；原型本身待生成，Demo 编码待原型确认。

---

## 十、交付物（增量）

- 增量设计文档（含 spike 实证记录、指令类清单、参数分发伪码、AC↔实现映射）；
- **阶段一产物**：demo 页面原型（待用户确认）；
- **阶段二产物**：`aifei-i18n-demo` 示例工程（原型确认后实现，真实 Enjoy 渲染验证，示例工程不发布）；
- `aifei-i18n` 模块内新增指令实现 + 测试，更新 `README.md` / `docs/design.md` / `docs/testing.md`；
- 模块目录内 `mvn clean verify` 0 failure / 0 error；demo 真实渲染用例通过。
