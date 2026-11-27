# aifei-i18n

面向 aifei 应用的国际化 SDK：**后端按 `key + locale` 取单条文案、前端按 `locale` 取整包语言包**，
并把「当前生效 Locale」贯通到 JDK 的数值 / 货币 / 日期格式化。文案来源**开放扩展**，模块自带
`properties` / `db` / `kv` 三个默认件，运行期可刷新。

- 坐标：`io.github.gollyhu:aifei-i18n:1.0.0-SNAPSHOT`
- Java 8（class major 52）；`aifei` 为 `provided`，消费方自带
- 零第三方 compile 依赖

## 一、它能做什么

> **模块定位与边界（基线）**：底座只做四件事，并做到极致、冻结边界——**locale 贯通的取文案 + 整包下发 + JDK 格式化 + 开放来源**；其余全部以扩展点形式交给业务方。
>
> - **做什么**：单条取文案（唯一回退链、缺译不 500）、整包语言包下发（四字段 JSON、`messages` 稳定排序、与单点同源）、Locale 贯通到 JDK 数值/百分比/货币/日期格式化（零自建规则数据）、来源开放扩展（实现 `MessageSource` 显式注册即可，零改模块代码）。
> - **不做什么**（交给业务方，底座只留挂点）：复数 / 性别 / 序数 / 语境形态、度量衡换算、前端 i18n 运行时与 TypeScript key 类型检查、语言切换 UI 与全局状态、URL 路径 / 子域名 / 浏览器语言识别、热重载触发编排。挂点分别是自定义 `MessageSource` 拆 key、`#lang(tag)` / `I18nKit.local(tag)` 显式注入、`Accept-Language` + `LocaleExtractor`、四字段 JSON 契约、业务侧显式 `I18nKit.refresh()`。
> - **冻结边界**：公共面精确 10 类型、恰好 4 个配置键、回退链唯一不可配、Java 8 class major 52。

1. 单条取文案，自带唯一回退链：`按 locale 逐来源 → 默认 locale 逐来源 → 返回 key 原文`，缺译永不 500。
2. 支持 `{0}` 位置占位插值（缺参占位符原样保留）。
3. Locale 四级优先级：`显式 > 上下文 > Accept-Language > i18n.defaultLocale`。
4. 前端整包语言包导出：四字段固定、`messages` key 稳定排序，与后端单点取文案同源同值。
5. 数值（含百分比）/ 货币 / 日期 / 日期时间格式化薄门面，档位 SHORT/MEDIUM/LONG/FULL（默认 MEDIUM）。
6. 来源开放扩展：实现 `MessageSource` 并显式注册即可，零改模块代码。

## 二、快速开始

### 1. 装配插件

在应用的 `config(Plugins plugins)` 中：

```java
plugins.add(new I18nPlugin(prop));
```

自定义来源（按稳定名在 `i18n.sources` 中声明顺序参与叠加）：

```java
plugins.add(new I18nPlugin(prop).addSource(new MyRemoteSource()));
```

### 2. 配置键（恰好 4 个，无启用开关）

| 配置键 | 含义 | 必填 / 默认值 |
| --- | --- | --- |
| `i18n.defaultLocale` | 回退链终点语种；无显式 / 上下文 / 请求头时的生效语种 | **必填**，缺失即启动失败 |
| `i18n.locales` | 支持语种列表（请求头匹配白名单） | 可选；未配置 = 不设白名单，接受任意合法 BCP 47 |
| `i18n.sources` | 来源稳定名有序清单，顺序即叠加与回退顺序 | 可选；默认仅 `properties` |
| `i18n.cache.ttlMillis` | db / kv 懒缓存 TTL（毫秒） | 可选；默认 `60000`；`≤ 0` 不缓存 |

> `i18n.locales` 仅用于请求头过滤，**不阻断显式 locale 的整包导出**；导出合法性只要求显式 locale 是合法 BCP 47。

### 3. 后端取文案

```java
String text  = I18nKit.local("zh-CN").message("login.title");     // 显式 locale
String text2 = I18nKit.local("zh-CN").message("hello", "Bob");    // {0} 插值 → "hello Bob"
String text3 = I18nKit.message("login.title");                    // 默认 local：走四级优先级
String text4 = I18nKit.message("hello", "Bob");                   // 默认 local + 插值
I18nKit.refresh();                                                // 后台改文案后全清缓存
```

未命中返回 key 原文（DEBUG 日志），不抛异常；来源运行期故障（DB 超时 / KV 不可用）WARN 后继续回退。

> **单引号陷阱**：插值走 JDK `MessageFormat`，文案原文中的单引号 `'` 是转义符——`don't` 会输出 `dont`、`{0}'s` 会丢掉 `'s`。需保留单引号时写双单引号 `''` 转义（如 `don''t`、`{0}''s`）。

### 4. Web 入口绑定（必须第一个 addHandler）

框架 `Input` 无 header 访问 API，消费方自定义 Input 暴露 header 后以 lambda 提供：

```java
settings.addHandler(new I18nHandler<InputImpl, OutputImpl>(
        input -> input.getHeader("Accept-Language")));   // 必须第一个 addHandler
plugins.add(new I18nPlugin(prop));
```

Handler 在请求进入时解析首个受支持语种并绑定上下文，`finally` 无条件清理，线程复用不串语言。

### 5. 前端语言包下发

模块不提供 HTTP 端点。消费方 Just Service 一行把语言包写成 JSON：

```java
LanguageBundle bundle = I18nKit.bundle("zh-CN");   // 显式 locale，不受上下文影响
// 将 bundle.locale() / localeName() / dir() / messages() 写为 JSON 响应
```

JSON 契约（四字段固定，`messages` 为有序映射、value 恒为原文字符串、`{0}` 不替换）：

```json
{
  "locale": "zh-CN",
  "localeName": "中文（中国）",
  "dir": "ltr",
  "messages": { "login.title": "登录", "login.submit": "提交" }
}
```

- `locale`：规范化后的 BCP 47 tag（`zh_CN` → `zh-CN`、`zh-hant-tw` → `zh-Hant-TW`）。
- `localeName`：JDK `Locale#getDisplayName(该Locale)`，模块不含语言名译名表。
- `dir`：`ltr` / `rtl`，只由 JDK `ComponentOrientation` 判定，无自建方向表。

> **整包导出边界**：`bundle(locale)` 按来源可列举范围**原样输出、不补齐缺译**；「逐来源 → 默认 locale → key 原文」的唯一回退链**只在单条取文案路径**（`I18nKit.message` / `#i18n`）生效，不作用于整包导出。例如某 locale 仅收录少量 key，整包就只导出这些 key，不会用默认 locale 补齐。不可列举来源（如 `kv`）整包跳过 + WARN，单点取仍可用。

### 6. 格式化

```java
FormatKit.formatNumber(1234.5, "de-DE");                                  // 分隔符随 locale
FormatKit.formatPercent(0.85, "en-US");                                   // 85%
FormatKit.formatCurrency(1234.5, "JPY", "zh-CN");                         // 币种独立，小数位随币种
FormatKit.formatDate(new Date(), DateStyle.MEDIUM, "en-US");             // 不传档位默认 MEDIUM
FormatKit.formatDateTime(new Date(), TimeZone.getTimeZone("Asia/Shanghai"), "zh-CN");
```

维度正交：币种、时区是独立参数，不从 locale 反推；日期类四档与 JDK `FormatStyle` 一一对应；
默认 MEDIUM 为硬编码契约，不占配置键。非法币种 / 时区 / `null` 值 / `null` 档位抛 `IllegalArgumentException`。

## 三、三个默认来源

| 稳定名 | 依赖 | 全量列举 | 运行期刷新 | 说明 |
| --- | --- | --- | --- | --- |
| `properties` | 零第三方 | 支持 | 不支持（须重启） | classpath `i18n.properties`，启动期一次全量载入 |
| `db` | 框架内核（随 aifei） | 支持 | 支持 | 只读、参数化查询、不执行 DDL |
| `kv` | `aifei-cache`（optional） | 不支持 | 支持 | 不可列举时整包导出跳过 + WARN |

### properties 资源格式

classpath 根下 `i18n.properties`（UTF-8），每行 `<locale>|<key>=<原文>`：

```properties
# 注释以 # 或 ! 开头
zh-CN|login.title=登录
zh-CN|login.submit=提交
en|login.title=Login
```

> **重要：`properties` 来源改值须重启应用才生效**（启动期全量快照，`refresh()` 为空操作；
> 属既定行为而非缺陷）。`db` / `kv` 来源支持运行期刷新，但模块自身**无定时任务 / 文件监听 / 热重载**，
> 须由业务侧**显式调用 `I18nKit.refresh()`** 才生效（如管理后台保存译文后触发）。需要周期刷新时，
> 业务侧用 `aifei-job` 注册定时任务调 `I18nKit.refresh()` 即可——模块不新增配置键、公共面或跨模块依赖。

### db 来源建表 SQL（模块只读，DDL 由消费方执行）

```sql
CREATE TABLE IF NOT EXISTS i18n_message (
  locale VARCHAR(32)   NOT NULL COMMENT '规范化后的 BCP 47 locale',
  msg_key VARCHAR(255) NOT NULL COMMENT '文案 key',
  content VARCHAR(4096) NOT NULL COMMENT '文案原文（含 {0} 占位符）',
  PRIMARY KEY (locale, msg_key)
) COMMENT='i18n 文案表（模块只读，DDL 由消费方执行）';
```

### kv 来源依赖

`kv` 来源依赖 `io.github.gollyhu:aifei-cache`（`optional`，不向下游传递），消费方须显式引入：

```xml
<dependency>
  <groupId>io.github.gollyhu</groupId>
  <artifactId>aifei-cache</artifactId>
  <version>1.1.0</version>
</dependency>
```

已声明 `kv` 但 classpath 无 aifei-cache → 启动失败（消息含稳定名 `kv`），不静默退回 `properties`。
未声明 `kv` 时，即便 classpath 无 aifei-cache 也不影响启动。

## 四、接入自定义来源

1. 实现 `MessageSource`：`name()` 返回唯一稳定名（不与 `properties` / `db` / `kv` 冲突）；
   `get(key, localeTag)` 返回译文（`null` = 无译文，空串 = 命中）；按需覆写 `list()`（可列举）/ `refresh()`。
2. 装配：`plugins.add(new I18nPlugin(prop).addSource(new MySource()))`。
3. 在 `i18n.sources` 中按需要的顺序声明稳定名，如 `i18n.sources=mySource,properties`。
4. 模块源码与 pom 零改动；仅 `list()` 缺失时整包导出跳过该来源 + WARN。

## 五、locale 规范化与格式化一致性

- **规范化**：任何 locale 入参先 `_`→`-`、语言码小写、地区码大写、脚本码首字母大写，再参与匹配 / 缓存 / 导出。
- **显式非法 locale**（如 `zh_CN_`、空串）→ `IllegalArgumentException`；**请求头非法片段** → 静默忽略并继续回退。
- **跨 JDK 差异**：同一 JDK 内格式化稳定；跨 JDK 差异由 JDK 自带 locale 数据决定，模块不承诺字节级一致。
  Java 8 默认 locale provider 为 JRE，Java 9+ 默认 CLDR——**推荐统一指定**：

  ```bash
  -Djava.locale.providers=CLDR
  ```

- **jlink 裁剪提示**：`dir` 判定使用 `java.awt.ComponentOrientation`（位于 `java.desktop` 模块）。
  Java 9+ 用 jlink 裁剪运行时镜像时**须保留 `java.desktop`**，否则 `dir` 判定不可用，模块会降级为 `ltr` + WARN，
  不阻断导出；Java 8 无模块系统不受影响。

## 六、Enjoy 模板指令（可选，非承诺 API）

面向使用 Enjoy 做服务端渲染的应用，模块在 `enjoy/directive/` 提供三条指令。它们依赖
`aifei` 已内含的 `cn.aifei:aifei-enjoy`，**零新增依赖坐标**；Enjoy 类型不进入任何公共签名。
构造引擎后注册一次即可：

```java
DirectiveRegistrar.register(engine);   // 一次注册 i18n / lang / dir，keepLineBlank=true
```

| 指令 | 用法 | 语义 |
| --- | --- | --- |
| `#i18n(...)` | `#i18n('hello', lang='ar-SA', 'Bob')` | 取文案；`lang=` 具名参数位置无关（可置首/中/尾），其余首个为 key、其后为 `{0}` 参数；lang 非法 / 重复 / 缺 key → IAE |
| `#lang()` / `#lang('xxx')` | 无参读、单参写 | 读当前生效 locale；写页面级默认语言（其后无 `lang` 的 `#i18n` 跟随） |
| `#dir()` / `#dir('xxx')` | 无参读、单参写 | 读页面方向（`ltr`/`rtl`）；显式写只接受 `ltr`/`rtl`，其它 → IAE；判定只走 JDK，无自建方向表 |

**语言取值唯一优先级链**：`#i18n(..., lang='xxx')`（单条局部） > `#lang('xxx')`（页面级默认） > `#lang()`（当前生效 locale）。
局部覆盖只影响该条文案、不改写页面默认值；三条指令最终都分发到 `I18nKit` 新 API（`local` 链式 / `message` 默认 local），回退 / 规范化 / 插值零复制。

### ⚠ 页面级状态的 clear 责任（必须遵守）

`#lang('xxx')` / `#dir('xxx')` 把页面级状态存在独立的 `DirectivePageState` ThreadLocal（**不动请求级
`LocaleContext`**）。指令是散点调用、无统一结束点，因此**消费方必须在渲染入口负责清理**，否则线程池复用线程会串页：

```java
DirectivePageState.clear();                 // 渲染前先清，防上次残留
try {
    DirectivePageState.setPageLang(reqLang); // 或由模板顶部 #lang(lang) 设置
    engine.getTemplate(view).render(data, out);
} finally {
    DirectivePageState.clear();              // 无条件清理，异常路径同样生效
}
```

`DirectiveRegistrar` / `DirectivePageState` / `DirectiveSupport` 与三条指令类均为 `enjoy.directive`
下的非承诺 API（可 public 供注册，不计入公共面 10 类型）。完整可运行示例见 `aifei-i18n-demo`。

## 七、公开 API

根包 7 个公共类型：`I18nKit` / `Localizer` / `FormatKit` / `MessageSource` / `LanguageBundle` / `DateStyle` / `I18nException`；
`web` 包 2 个：`I18nHandler` / `LocaleExtractor`；`aifei` 包 1 个：`I18nPlugin`，合计 10 个。
`I18nKit.local(localeTag)` 返回不可变 `Localizer`，链式 `message(key[, args])` 绑定显式 locale；`I18nKit.message(key[, args])` 为默认 local 入口。
公共签名只出现 JDK 与本模块类型（框架类型仅出现在 `web` 接入与 `aifei` 装配包、以及 `enjoy.directive` 指令实现）。

## 八、构建与验证

```bash
cd aifei-i18n
mvn clean verify      # 0 failure / 0 error，含两套架构门禁测试（enjoy 仅允许 enjoy/directive）
```

