# aifei-email 模块约束（改代码前必读）

本文件适用于 `aifei-email/` 内的全部代码。它**补充并收紧**仓库根 `AGENTS.md`，不得与之冲突；两者不一致时以更严格者为准。契约事实来源是 [docs/design.md](docs/design.md)，本文件只给「改代码时必须遵守什么、怎么验证」。

## 一、文档分工

| 文件 | 面向 | 内容 |
| --- | --- | --- |
| `README.md` | 使用者 | 怎么装、怎么配、怎么发（由浅入深 7 节） |
| `docs/design.md` | 设计者 / 实现者 | 契约、决策、门禁、AC（**事实来源，不得为本模块实现改写**） |
| `docs/testing.md` | 实现者 | 测试策略、用例清单与 AC 映射、已知局限 |
| `AGENTS.md`（本文件） | 开发代理 / 贡献者 | 改代码的硬约束与验证命令 |
| `LICENSE` / `NOTICE` | 合规 | Apache-2.0 全文 + 第三方（javax.mail 等）声明 |

## 二、包边界（四层，门禁断言，不得放宽）

```text
cn.hg.aifei.email                 根包：精确 5 个公共类型，零第三方 / 零框架类型
cn.hg.aifei.email.spi             通道扩展 SPI：精确 1 个公共类型（MailProvider）
cn.hg.aifei.email.aifei           接入层：EmailPlugin / EmailConfigLoader —— 唯一允许 import cn.aifei.*
cn.hg.aifei.email.internal        适配层（非承诺）：注册表 / 错误构造 / Provider 发现 / 动态代理 / 通道配置
   └─ internal.smtp               唯一允许 import org.dromara.email.* / javax.mail.* / com.sun.mail.*
```

- 依赖方向单向：根包 / spi 只依赖 JDK 与本模块类型；`internal` 不直接引用内核类型（经 `internal.smtp`）；`internal.smtp` 不得引用 `cn.aifei.*`。
- 全模块禁止 `import java.sql.` / `import io.undertow.`（零存储 / 零 Web 容器）与 `import org.slf4j.`（零日志绑定）。
- 新增公共类型、跨层引用、公共面框架类型例外，一律先过设计决策并在根 `AGENTS.md` 登记，不得由本模块单方面声明。

## 三、技术路线（锁定，不得推翻）

- 发送内核一律走 `org.dromara.sms4j:sms4j-Email-core:3.3.6`：`MailSmtpConfig.builder()...build()` → `MailBuild.build(config)` → 内核 `MailClient.send(...)`。
- **禁** `sms4j-spring-boot-starter` / `sms4j-solon-plugin` / 任何 starter 或 solon 插件；**禁**自研 SMTP 协议实现或任何「等价替身」顶替内核。
- 不使用内核的静态注册表 API（`MailFactory.put` / `createMailClient`），避免停止时残留全局状态。
- 内核类型只在 `internal.smtp` 出现；`SmtpMailProvider` 的**类签名**不得含内核类型（保证内核缺失时 `ServiceLoader` 仍可加载）。

## 四、Java 8 双约束

- `source` / `target` = `1.8`；主 JAR 全部 class `major version` = **52**。
- 禁语法：`var`、`record`、`switch` 表达式、text block（`"""`）、`sealed`、`instanceof` 模式匹配。
- 禁 API：`List.of` / `Map.of` / `Set.of` 等 Java 9+ 集合工厂、`Optional.orElseThrow()` 无参版等。
- 源码 UTF-8、缩进 4 空格、行尾 CRLF（`core.autocrlf=true`），批量改写按字节读写且带命中次数断言。

## 五、公共面冻结（Q7）

| 包 | 精确类型 | 数量 |
| --- | --- | --- |
| 根包 | `MailClient` / `MailKit` / `MailMessage` / `MailResult` / `MailException` | 5（集合精确相等） |
| `spi` | `MailProvider` | 1（集合精确相等） |

- `MailMessage.Builder` 是内部类，不增加顶层 `.java` 计数；`EmailPlugin` / `EmailConfigLoader` 属接入构件，不计数。
- 公共方法**不声明 `throws` 子句**（`MailException` 为非受检），javadoc 必须写全 `@param` / `@return` / `@throws MailException`（简体中文）。
- 公共签名不得出现 `org.dromara.email.*` / `javax.mail.*` / `com.sun.mail.*` / `cn.aifei.*`。
- 变更公共面须先改 `docs/design.md` §5 并过设计决策；本模块 **不得**为兼容错误用法放宽门禁。

## 六、六个唯一定义点（实现类不得复制）

| # | 规则 | 唯一定义点 | 门禁 |
| --- | --- | --- | --- |
| 1 | 异常消息措辞（`email: ` 前缀，16 条逐字） | `internal/MailErrors.java` | ⑨ |
| 2 | 依赖探测（全模块唯一 `Class.forName`） | `internal/smtp/MailDeps.java` | ⑧ |
| 3 | 配置键名解析（`email.` 前缀、默认通道键、kebab 键全集） | `aifei/EmailConfigLoader.java` | ⑬ |
| 4 | Provider 发现（`ServiceLoader` + 同 type 去重） | `internal/MailProviderRegistry.java` | — |
| 5 | 客户端构建（配置项 → 内核配置 → 内核客户端 → 模块发送实现、mock 注入点） | `internal/smtp/SmtpClientFactory.java` | — |
| 6 | 附件映射与临时文件清理（`byte[]` → 临时文件 → 显示名映射；`finally` 按本次清单清理） | `internal/smtp/SmtpMappers.java` + `MappedMessage` | — |

配套硬性约定：

- 附件临时文件清单随 `MappedMessage` 逐次传递，**禁静态字段 / `ThreadLocal` 跟踪**。
- HTML 模板必须走**上下文类加载器**读 classpath `template/<name>` 并以 `html(InputStream)` 交给内核；**禁**用内核的 `html(String)` 路径读取。
- `DefaultMailClientProxy` **不缓存**具体客户端，每次经 `MailClients.defaultClient()` 现取。
- `start()` 失败先 `MailClients.clear()` 再上抛；`start/stop` `synchronized` + 幂等。
- 单例注册用**类级双检**（`synchronized (EmailPlugin.class)`）：`singletonRegistered` 为静态而方法锁为实例锁，多插件实例并发装配时不得重复注册（框架 `addSingletonObject` 不幂等）。

### 实现说明：§7.5 场景 10 / 11 的异常链判据（cause 链 + nextException 链）

**事实前提（已 `javap` 核实）**：`javax.mail 1.6.2` 的 `MessagingException#getCause()` 被重写为返回内部字段 `next`，因此 **cause 链天然包含 next 链**；真实 SMTP 故障挂在 `next` 上时，仅走 cause 链也能命中。

实现同时显式遍历 `getNextException()` 链，定位为**防御性等价实现**：

- 对未重写 `getCause()` 的邮件协议实现或未来版本仍能正确归类，使判据不依赖库实现细节；
- 该遍历**不改变分类结果**，**16 条消息措辞、cause 保留、公共面签名均不变**；
- 用例以 `new MessagingException("send failed")` + `setNextException(...)` 构造 **next-only 链**覆盖该形态（超时 → 场景 11；认证失败 → 场景 10），并显式断言 next 链可遍历；**该用例不用于证明 next 分支必要**（删除该分支后仍会通过）；
- 遍历把 cause 链与 next 链统一为异常链遍历，带深度上限 16 防环；实现只在 `internal/smtp/SmtpMailSender`（门禁 ② 允许该子包引用 `javax.mail.*`）。

### 已知的防御分支（不得当作主路径断言）

- `SmtpClientFactory` 的整数解析二次防线用**裸选项键**报错（如 `max-retries`），不带 `email.<name>.` 前缀——完整键名前缀字面量出现在 `internal.smtp` 会触发门禁 ⑬。该分支在 `EmailConfigLoader` 校验之后**不可达**，主路径（含完整键名）由场景 9 覆盖。

## 七、依赖纪律

| 依赖 | 范围 | 约束 |
| --- | --- | --- |
| `cn.aifei:aifei:1.1.2` | `provided` | 消费方自带，仅 `aifei` 包引用 |
| `org.dromara.sms4j:sms4j-Email-core:3.3.6` | **`optional`** | 唯一内核；**不是** `compile` 例外，根 `AGENTS.md` 的 compile 例外清单不变 |
| `junit:junit:4.13.2` | `test` | 测试与门禁；`hamcrest-core` 为其受限传递项 |
| `com.alibaba.fastjson2:fastjson2:2.0.65` | `provided` | **受限传递依赖**：由 `aifei` 底座传递，`provided` 不向下游传递；**非**本模块直接声明，不构成第三方 compile / runtime 依赖（AC-13 判定不受影响，`aifei-sms` 有同款先例）。`design.md` §2.3 未列该项，属设计文档补登遗留 |

- 不声明任何日志绑定实现（`slf4j-simple` / `logback` / `log4j`）；内核传递的 `slf4j-api` 仅门面。
- **许可证留痕**：内核传递依赖 `com.sun.mail:javax.mail:1.6.2` 为 **CDDL 1.1 + GPL 2.0 with Classpath Exception**（另有 `javax.activation`），法务已确认 CPE 豁免可接受；声明落地在 `LICENSE` / `NOTICE` / `README.md`，并在根 `AGENTS.md` 登记。改动依赖版本须同步更新这三处。

## 八、架构门禁（14 例，不得删减 / 放宽）

`src/test/java/cn/hg/aifei/email/ArchitectureBoundaryTest.java`，按源码文本断言（注释剥离、路径归一化）：

| # | 方法 | 断言 |
| --- | --- | --- |
| ① | `sms4jEmailOnlyInAdapter` | `org.dromara.email.*` 只在 `internal/smtp/` |
| ② | `javaxMailOnlyInAdapter` | `javax.mail.*` / `com.sun.mail.*` 只在 `internal/smtp/` |
| ③ | `frameworkOnlyInBridgePackage` | `cn.aifei.*` 只在 `aifei/` |
| ④ | `noSqlOrUndertowInLibrary` | 全模块无 `java.sql.` / `io.undertow.`；pom 无 JDBC 坐标 |
| ⑤ | `noSlf4jBinding` | 全模块（含测试）无 `org.slf4j.`；pom 无日志绑定 |
| ⑥ | `publicSurfaceExact` | 根包 5、spi 1，文件名集合精确相等 |
| ⑦ | `publicSignaturesNoThirdParty` | 根包 + spi 无第三方 / 框架类型字面量 |
| ⑧ | `classForNameOnlyInDeps` | `Class.forName` 仅在 `MailDeps.java` |
| ⑨ | `errorMessagesOnlyInErrors` | `email: ` 前缀仅在 `MailErrors.java` |
| ⑩ | `java8Compatibility` | 无 Java 8 之后的语法 / API |
| ⑪ | `noStarterDependency` | pom 无 starter / solon / Spring |
| ⑫ | `pluginBridgeContract` | `aifei` 包两个接入构件存在且引用框架包 |
| ⑬ | `configKeysOnlyInLoader` | 配置前缀 / 默认通道键仅在 `EmailConfigLoader.java` |
| ⑭ | `testSourcesOfflineOnly` | 测试源码无外网 host（仅 `127.0.0.1` / `localhost`） |

## 九、测试纪律

- 全部单测**零外网**：内核发送客户端用模拟实现（`send` 不抛异常即成功），不真实发邮件；不提交二进制或以外网地址为依赖的用例。
- 每个测试类 `@After` 必须 `MailClients.clear()` + `TestHooks.resetClientFactory()` + `MailProviderRegistry.reset()`（三者均为进程内静态状态，串扰会让失败变成随机）。
- 不得开启 surefire 并行执行测试类（静态状态共享）。
- 门禁失败**只能**修测试实现细节（注释剥离 / 路径归一化），不得删减断言或放宽口径。
- 修复缺陷须固化为回归测试；新增异常路径须同时补「逐字消息 + cause 保留」断言。
- 已知覆盖局限（不得用弱化断言掩盖）：异常场景 14（附件临时文件写入失败）在共享测试 JVM 中无法可靠重放真实 IO 失败，当前覆盖为「消息逐字 + cause 保留」，附件正路由发送契约用例实测覆盖；细节见 [docs/testing.md](docs/testing.md)。

## 十、验证命令（在 `aifei-email/` 目录内执行）

```bash
mvn -q -DskipTests clean compile                 # 编译（Java 8，产物 major 52）
mvn test -Dtest=ArchitectureBoundaryTest         # 架构门禁 14 例
mvn clean verify                                 # 全量：83 个测试，必须 0 failure / 0 error
mvn dependency:tree                               # 仅 aifei(provided) / sms4j-Email-core(optional) / junit(test)
javap -verbose -classpath target/classes cn.hg.aifei.email.MailClient | findstr /C:"major version"   # 期望 52
```

门禁外的构建期断言：主 JAR 全部 class major = 52（抽查 `MailClient` / `MailKit` / `EmailPlugin` / `MailDeps`）；`dependency:tree` 只出现允许清单。

## 十一、提交前检查清单

- [ ] `mvn clean verify` 0 failure / 0 error（83 例含门禁 14 例）。
- [ ] 未引入 Java 8 之后的语法 / API；未新增未声明的第三方依赖（`optional` / `provided` 之外一律先回报）。
- [ ] 未改动公共面（根包 5 + spi 1）与 16 条异常消息措辞；如确需改动，已先更新 `docs/design.md` 与测试断言。
- [ ] 六个唯一定义点未被复制；附件临时文件无静态字段 / `ThreadLocal` 跟踪。
- [ ] 未放宽门禁、未提交构建产物 / IDE 文件 / 日志 / 凭据；`git status` 无 `AD` 残留。
- [ ] 公共行为变化已同步 `docs/design.md`、`README.md`、`docs/testing.md`。
- [ ] 新增类型后缀符合根 `AGENTS.md` 命名约定（无状态工具 `Util`、有状态门面 `Kit`），文件头版权块未被改写。
