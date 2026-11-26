# aifei-pay 发布说明（发布物清单 · 接入 · 验收 · 回滚）

> **文件定位**：本文是 `aifei-pay` 的**发布交付说明**——发布物清单、消费方接入片段、最小接入步骤、验收方法、
> 回滚路径、发布前检查单。版本变更见 [`../CHANGELOG.md`](../CHANGELOG.md)，实现契约见 [`design.md`](design.md)，
> 测试事实见 [`testing.md`](testing.md)。
>
> **本版结论**：`1.0.0-SNAPSHOT`，**未发布**（本地构建通过，待正式发布）。模块为 IJPay 多渠道聚合 SDK，删除全部 `AliPay*` 公共类型；V2.5 起 `qq` / `jdpay` / `unionpay` / `paypal` / `stripe` / `fuiou` / `xpay` 七渠道已真实落地，`wxpay` 仍待落地（与 `aifei-weixin` 二选一）。所有「构建成功」「产物存在」结论均来自 2026-11-26 的实跑输出，取证记录见 §1。

---

## 1. 构建验证记录（实跑，非推断）

### 1.1 构建环境

| 项 | 值 |
| --- | --- |
| Maven | Apache Maven 3.8.3（`D:\Apache\apache-maven-3.8.3`） |
| 运行 Maven 的 JDK | Java 19.0.1（Oracle，`C:\Program Files\Java\jdk-19`） |
| 编译目标 | `-source 1.8 -target 1.8`（`maven-compiler-plugin:3.15.0`），产物 class major = **52** |
| 操作系统 / 编码 | Windows 10 amd64 / UTF-8 |
| 构建约定 | **必须在 `aifei-pay/` 目录内执行**（门禁用例依赖模块根的相对路径 `src/main/java`，`surefire` 的 `workingDirectory` 已固定为 `${project.basedir}`） |

### 1.2 `mvn clean verify` 实跑结果

```
[INFO] Compiling 53 source files with javac [debug target 1.8] to target\classes
[INFO] Compiling 27 source files with javac [debug target 1.8] to target\test-classes
[INFO] Tests run: 174, Failures: 0, Errors: 0, Skipped: 0
[INFO] Building jar: ...\target\aifei-pay-1.0.0-SNAPSHOT.jar
[INFO] Building jar: ...\target\aifei-pay-1.0.0-SNAPSHOT-sources.jar
[INFO] Building jar: ...\target\aifei-pay-1.0.0-SNAPSHOT-javadoc.jar
[INFO] BUILD SUCCESS
[INFO] Total time:  19.283 s
```

| 指标 | 实测值 |
| --- | --- |
| 构建结论 | **BUILD SUCCESS** |
| `Tests run` | **174** |
| `Failures` / `Errors` / `Skipped` | **0 / 0 / 0** |
| 构建耗时 | 19.283 s（含编译、174 例用例、三产物打包） |

### 1.3 字节码基线核对

| 核对项 | 实测值 | 判定 |
| --- | --- | --- |
| `target/classes` 全部 `.class`（含嵌套枚举、`internal/channels` 七渠道适配器）的 `major version` | 全部为 **52** | 合规（Java 8 可加载） |
| `javap -verbose` 抽查（公共接口 / 值对象 / internal 实现 / 装配入口 / 嵌套枚举 / 内部工具） | `PayClient`（public interface）、`PayOrderResult`（public final class）、`DefaultPayClient`（implements `PayClient`）、`PayPlugin`（implements `cn.aifei.plugin.Plugin`）、`PayOrderResult$Channel`（extends `Enum`）、`PayVerifyKey` → **major 均为 52** | 合规 |
| 附加交叉验证：以 `javac --release 8` 重新编译全部 39 个主源文件 | **0 错误**，产出 44 个 class，major 全为 52 | 源码在**严格 JDK 8 API 视图**下可编译，未引用 Java 9+ API |

### 1.4 依赖树核对（`mvn dependency:tree` 实跑）

| 核对项 | 实测值 | 判定 |
| --- | --- | --- |
| 编译期节点数（`:compile`） | **40** | 新增七个 IJPay 渠道构件、`stripe-java` 与 `IJPay-Core` 传递链；无 `dom4j:dom4j` / `bcprov-jdk15on` |
| `cn.aifei:aifei-all` | `:jar:1.1.2:provided` | 合规（不传递） |
| `IJPay-AliPay-V3` / `alipay-sdk-java-v3` / `org.dom4j:dom4j` / `bcprov-jdk18on` | pom 中均为 `<optional>true</optional>` | 合规（不传递） |
| `dom4j:dom4j`（旧坐标，groupId=`dom4j`） | 树中**不存在** | 已排除 |
| `bcprov-jdk15on` | 树中**不存在**；仅 `org.bouncycastle:bcprov-jdk18on:1.86` | 无残留 |
| 排除项 `jackson-databind-nullable` | 树中不存在（其传递的 jackson 链亦未出现） | 已排除 |

依赖树全量（实测，供比对）：`aifei-all:1.1.2:provided`（→ `fastjson2:2.0.66:provided`）、`IJPay-AliPay-V3:3.0.0`（→ `hutool-crypto` / `hutool-core` / `hutool-http` / `hutool-json:5.8.48`）、`alipay-sdk-java-v3:3.1.89.ALL`（→ 22 个传递节点，含 `okhttp:4.9.3` / `okio:2.8.0` / `kotlin-stdlib:1.4.10` / `gson:2.9.0` / `guava:28.1-jre` / `commons-logging:1.1.1`）、`org.dom4j:dom4j:2.1.5`、`bcprov-jdk18on:1.86`、`junit:4.13.2:test`（→ `hamcrest-core:1.3:test`）。

---

## 2. 发布物清单

| 产物文件名（位于 `target/`） | 类型 | 字节大小 | 用途 |
| --- | --- | --- | --- |
| `aifei-pay-1.0.0-SNAPSHOT.jar` | 主 jar（二进制） | **104,730** | 运行期依赖：含七渠道适配器全部 class + `META-INF/maven/**`。消费方唯一必须引入的产物 |
| `aifei-pay-1.0.0-SNAPSHOT-sources.jar` | sources jar | **79,423** | 源码包：53 个 `.java`。供 IDE 跳转调试与审计 |
| `aifei-pay-1.0.0-SNAPSHOT-javadoc.jar` | javadoc jar | **187,934** | API 文档包，覆盖公共包 |

三个产物均由 `mvn clean verify` 在同一次构建中生成（源/文档 jar 绑定在 `package` 阶段），**目录与磁盘实际产物已逐一核对**。

**可复现性实测**（两次 `mvn clean verify`，其间仅新增两份 Markdown 文档，未动源码与 pom）：
主 jar `104,730` 与 sources jar `79,423` 在最终构建中稳定产出；javadoc jar 不逐字节可复现。
因此 javadoc jar 的字节大小**不作为门禁判据**，只核对文件存在与条目数（91）。
复跑结论：`BUILD SUCCESS` / `Tests run: 166, Failures: 0, Errors: 0, Skipped: 0` / `Total time: 17.449 s`。

产物内的构建元信息（`META-INF/MANIFEST.MF`）：`Build-Jdk: 19.0.1`、`Created-By: Apache Maven 3.8.3`。
`Build-Jdk` 只记录构建机的 JDK，与「产物可在 Java 8 运行」不矛盾——运行期判据是 class major = 52（见 §1.3）。

---

## 3. 依赖接入片段（消费方 `pom.xml` 应粘贴的内容）

```xml
<dependencies>
    <!-- ① 生态统一底座：由应用侧提供（本模块为 provided，不传递） -->
    <dependency>
        <groupId>cn.aifei</groupId>
        <artifactId>aifei-all</artifactId>
        <version>1.1.2</version>
        <scope>provided</scope>
    </dependency>

    <!-- ② 本模块 -->
    <dependency>
        <groupId>io.github.gollyhu</groupId>
        <artifactId>aifei-pay</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>

    <!-- ③ 支付宝 V3 内核：必带 -->
    <dependency>
        <groupId>com.github.javen205</groupId>
        <artifactId>IJPay-AliPay-V3</artifactId>
        <version>3.0.0</version>
    </dependency>

    <!-- ④ 开放平台 SDK：必带，版本必须与本模块锁定值一致；三个 exclusion 不可移除 -->
    <dependency>
        <groupId>com.alipay.sdk</groupId>
        <artifactId>alipay-sdk-java-v3</artifactId>
        <version>3.1.89.ALL</version>
        <exclusions>
            <exclusion><groupId>dom4j</groupId><artifactId>dom4j</artifactId></exclusion>
            <exclusion><groupId>org.bouncycastle</groupId><artifactId>bcprov-jdk15on</artifactId></exclusion>
            <exclusion><groupId>org.openapitools</groupId><artifactId>jackson-databind-nullable</artifactId></exclusion>
        </exclusions>
    </dependency>

    <!-- ⑤ 证书解析：sign-mode=cert 或验签凭据 type=cert 时运行期必需 -->
    <dependency>
        <groupId>org.bouncycastle</groupId>
        <artifactId>bcprov-jdk18on</artifactId>
        <version>1.86</version>
    </dependency>

    <!-- ⑥ 与 SDK 的替换声明对齐，锁定在 Java 8 基线内（2.2.0 起字节码为 55） -->
    <dependency>
        <groupId>org.dom4j</groupId>
        <artifactId>dom4j</artifactId>
        <version>2.1.5</version>
    </dependency>
</dependencies>
```

**为什么必须自己声明**：`aifei-pay` 把 ③④⑤⑥ 全部声明为 `optional`，`aifei-all` 声明为 `provided`。
Maven 语义下二者都**不向下游传递**——不显式声明，编译期就找不到内核类型。

- `optional` ≠ 不需要：它只表示「不由本模块代你传递」，消费方**必须自己写出来**
- `provided` 的含义：「容器/应用自带，本模块不打包不传递」；`aifei-all` 版本需与应用其它 aifei 模块一致
- 只做「普通公钥模式」也建议带上 `bcprov-jdk18on`：验签走 `type=cert` 时同样需要它
- **不要**再引入 `bcprov-jdk15on`（与 `bcprov-jdk18on` 同名类冲突，行为不可预期）
- 升级 `alipay-sdk-java-v3` 时必须重新验证 `jackson-databind-nullable` 的排除前提（现结论基于「该 SDK 版本被引用 0 次」的实测）
- 使用七渠道时须额外声明对应 `optional` 构件：`IJPay-QQ` / `IJPay-JDPay` / `IJPay-UnionPay` / `IJPay-PayPal` / `IJPay-Stripe` + `stripe-java:34.1.0` / `IJPay-FuiYou` / `IJPay-XPay`（均 `3.0.0`）；未声明时装配期抛带坐标的 `PayException`。`wxpay` 本期不实现。

---

## 4. 最小接入步骤

### 步骤 1：注册插件（`AppConfig.configPlugin`）

```java
public class AppConfig implements AifeiConfig {
    @Override
    public void configPlugin(Plugins plugins) {
        Prop prop = new Prop("alipay.properties");   // 或复用应用既有配置源
        plugins.add(new PayPlugin(prop));
    }
}
```

`start()` 内部顺序：解析 `pay.*` → 探测内核依赖 →（证书模式）探测 BouncyCastle → 为每个 appId 预建客户端 →
注册 `@Inject PayClient` 单例 → 写入门面实例。配置非法时 `start()` 抛 `PayException` 且**不留半装配态**。

### 步骤 2：配置 `pay.*` 键

**全局键**（均可省略）：

| 键 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `pay.default-app-id` | 否 | 无 | `appId` 参数为 `null`/空白时的回退值；不配则必须每次显式传 appId |
| `pay.connect-timeout` | 否 | `5000` | 连接超时（毫秒） |
| `pay.read-timeout` | 否 | `15000` | 读取超时（毫秒） |
| `pay.page-http-method` | 否 | `POST` | `POST` → 载荷为表单 HTML；`GET` → 载荷为跳转 URL |
| `pay.notify-url` / `pay.return-url` | 否 | 无 | 全局默认异步 / 同步回调地址 |

**单应用键**（`<appId>` 为支付宝应用编号，`<n>` 为从 0 起的凭据序号）：

| 键 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `pay.apps.<appId>.env` | **是** | — | `sandbox` / `production`，决定默认网关并校验网关一致性 |
| `pay.apps.<appId>.gateway-url` | 否 | 环境对应官方网关 | 显式配置时 scheme 必须 `https`，host 必须等于环境对应官方网关 host |
| `pay.apps.<appId>.sign-mode` | 否 | `cert` | `cert` / `key` |
| `pay.apps.<appId>.private-key` | **是** | — | 应用私钥（RSA PKCS#8，允许裸 Base64） |
| `pay.apps.<appId>.app-cert` | `cert` 模式必填 | — | 应用公钥证书 |
| `pay.apps.<appId>.root-cert` | `cert` 模式必填 | — | 支付宝根证书 |
| `pay.apps.<appId>.verify.<n>.type` | **是**（至少 `n=0`） | — | `cert` / `key` |
| `pay.apps.<appId>.verify.<n>.value` | **是** | — | `cert` → 支付宝公钥证书；`key` → 支付宝公钥 |
| `pay.apps.<appId>.verify.<n>.cert-sn` | 否 | 自动计算 | 显式配置时必须与证书实际序列号一致，否则启动失败 |
| `pay.apps.<appId>.charset` | 否 | `UTF-8` | 仅支持 UTF-8 |
| `pay.apps.<appId>.sign-type` | 否 | `RSA2` | 仅支持 RSA2 |

凭据取值两种形态：`@file:/path/to/x.pem`（UTF-8 读取）或内联 PEM 文本；**不支持 classpath 引用**。
最小可用配置是「**一个 appId** + `env` + `private-key` + `verify.0.type` + `verify.0.value`」（`sign-mode=key` 时）。

### 步骤 3：使用交易动作

```java
@Inject
private PayClient pay;                 // 单例，与 PayKit.defaultClient() 是同一对象

PayOrderResult qr = pay.precreate(appId, "ORDER-1", 1L, "会员年费", null);   // 扫码，payloadType=QRCODE
PayQueryResult  q  = pay.query(appId, "ORDER-1");
PayRefundResult r = pay.refund(appId, "ORDER-1", "REFUND-1", 1L, "用户取消");
```

### 步骤 4：回调验签

```java
// 异步通知（form body 字符串）与同步跳转（query string）走同一条路径
PayNotify notify = PayNotifyUtil.verifyAndParse(appId, formBody);   // 失败抛 PayException
// 业务处理成功后回 "success"
boolean ok = PayNotifyUtil.verify(appId, paramsMap);                      // 需要布尔语义时
```

**模块不做去重、不做状态机**：业务必须自行按 `outTradeNo` / `outRequestNo` + `tradeStatus` 做前置校验。

---

## 5. 验收方法（如何在消费方确认接入成功）

按下列顺序逐级验证，任一步失败即停在该级排查：

1. **依赖解析**：`mvn dependency:tree` 在消费方工程内应能看到 `aifei-pay`、`IJPay-AliPay-V3:3.0.0`、
   `alipay-sdk-java-v3:3.1.89.ALL`、`bcprov-jdk18on:1.86`、`org.dom4j:dom4j:2.1.5`，
   且**看不到** `dom4j:dom4j` 与 `bcprov-jdk15on`。
2. **装配成功（机械判据）**：应用启动不抛 `PayException`；调用 `PayKit.defaultClient()` 返回**非 null**。
   （模块只在内核失败与验签失败两处告警，装配成功**没有专属日志行**，因此不要靠日志文本判断。）
3. **装配失败可读性**：故意写错一个必需键（如删掉 `env`）重启，应得到含键名、凭据被掩码为 `***（长度 n）` 的
   `PayException`，且 `PayKit.defaultClient()` 仍为空（证明不留半装配态）。验完恢复配置。
4. **单笔沙箱下单**：用沙箱 appId（`env=sandbox`）调 `precreate`，应得到非空 `payload()` 且
   `payloadType() == QRCODE`；随后调 `query` 查同一 `outTradeNo`，应返回该笔交易（未支付时为等待付款状态）。
5. **回调链路**：把沙箱的一笔真实 notify 报文喂给 `PayNotifyUtil.verifyAndParse`，应验签通过并解析出
   `outTradeNo` / `tradeStatus` / `totalAmount`；再把报文中任一字段改动一个字符，应验签失败。
   这一步同时可闭环 `docs/testing.md` §6 的未验证项 V-1（`alipay_cert_sn` 真实字段名）。
6. **日志绑定**：确认应用已装配日志工厂——模块的两处 `warn` 依赖它，完全无日志绑定的裸环境会在此抛异常。

> 未做上述第 4~5 步的消费方，不能宣称「支付链路可用」：本轮发布物只在**离线**条件下验证
> （166 例用例零联网），真实链路的 4 个未验证项见 `CHANGELOG.md`「未验证项」。

---

## 6. 回滚路径

本版是模块**首个版本**，没有「上一版本坐标」可回退，因此回滚 = **拆掉接入**。三种情形分列：

| 情形 | 回滚动作 | 必须同步处理 |
| --- | --- | --- |
| A. 已引入 `1.0.0-SNAPSHOT`，需撤下 | 从 `pom.xml` 删除 ②③④⑤⑥ 五个依赖；**同时删除** `AppConfig.configPlugin` 中的 `plugins.add(new PayPlugin(prop))` | 只删依赖不删插件注册 → 类加载失败或启动抛异常 |
| B. 已有其它可用版本（后续发布后适用） | 把 `<version>` 改回上一可用版本，其余依赖不动 | 上一版本若要求不同内核版本，需一并回退 ③④⑥ 的版本 |
| C. 仅想临时停用支付能力 | 移除插件注册，保留依赖（jar 仍在本地仓库，便于快速恢复） | 见下方「残留项」 |

**回滚时必须注意的配置项残留**（移除代码后这些键会变成无主配置，虽不报错但会误导后人）：

- `pay.*` 全部键（全局 + `pay.apps.<appId>.*`，尤其 `private-key` / `app-cert` / `root-cert` / `verify.<n>.*`）
  —— 建议随代码回滚一并删除，避免凭据文件路径与「看似已启用」的配置长期滞留
- 外部凭据文件（`/etc/alipay/*.pem|crt`）与密钥托管条目：回滚后应评估是否需要继续保留访问权限
- 若应用其它模块也从 `aifei-all` / `bcprov-jdk18on` 取值，**不要**顺手删这两个依赖——它们不是支付专属
- 回滚后请重跑一次应用启动与 `mvn dependency:tree`，确认没有残留 `aifei-pay` 节点

---

## 7. 发布前检查单

- [ ] 版本后缀：`pom.xml` 中 `<version>` 为 `1.0.0-SNAPSHOT`，**`-SNAPSHOT` 未被去掉**（本模块未正式发布，去掉即违规）
- [ ] 坐标一致：`io.github.gollyhu:aifei-pay:1.0.0-SNAPSHOT`，`packaging=jar`
- [ ] 构建：`cd aifei-pay && mvn clean verify` → `BUILD SUCCESS`
- [ ] 用例：`Tests run: 174, Failures: 0, Errors: 0, Skipped: 0`
- [ ] 字节码：`target/classes` 全部 class major = **52**，含嵌套枚举与七渠道适配器
- [ ] 依赖 scope：`aifei-all` = `provided`；支付宝 / 七渠道 IJPay 构件 / `stripe-java` / 官方 SDK = `optional`
- [ ] 依赖树：编译期节点 **40** 个；无 `dom4j:dom4j`、无 `bcprov-jdk15on`
- [ ] 产物：主 jar / `-sources.jar` / `-javadoc.jar` 三件齐全，文件名与大小与 §2 一致
- [ ] 违禁词：`README.md` / `AGENTS.md` / `docs/**/*.md` / `CHANGELOG.md` 按团队违禁词清单扫描（大小写不敏感）**0 命中**
- [ ] 容器化产物：模块内无 `Dockerfile` / `docker-compose*` / `*.dockerfile` / `k8s/` / `kubernetes/` / `helm/` / `charts/`
- [ ] 无 `public static void main`（`src/main/java`）
- [ ] 发布纪律：本轮**未**执行 `mvn deploy` / `mvn -Pcentral-release deploy`，**未**执行 `git add` / `commit` / `tag`，未触碰中央仓库凭据
- [ ] 发布元数据齐备：`url` / `licenses` / `developers` / `scm` / `distributionManagement`（snapshotRepository）+ `central-release` profile（GPG 签名 + `central-publishing-maven-plugin`）
- [ ] 正式发布时（另立发布动作）：需先补 `releaseRepository` 或确认走 `central-publishing-maven-plugin` bundle 通道；去 `-SNAPSHOT` 与发布动作**同批**完成

---

## 8. 本轮遗留观察项（不阻塞，供后续处理）

| # | 观察项 | 影响 | 建议 |
| --- | --- | --- | --- |
| 1 | `maven-javadoc-plugin` 的 `excludePackageNames=cn.hg.aifei.pay.internal.*` 只匹配 `internal` 的**子包**，`internal` 包本身未被排除：`-javadoc.jar` 内含 15 个内部实现类的 HTML 页面 | 仅文档产物范围超预期，不影响主 jar 与运行期；内部实现会暴露在发布文档中 | 改为 `cn.hg.aifei.pay.internal,cn.hg.aifei.pay.internal.*`（两个都写）。**本轮未改 pom**，仅记录 |
| 2 | 构建在 JDK 19 上以 `-source/-target 1.8` 编译，未设 `-bootclasspath`，日志有 `[WARNING] 未与 -source 8 一起设置引导类路径` | 编译期无法机械拦截「引用 Java 9+ API」；本轮已用 `javac --release 8` 附加验证（0 错误）补齐证据 | 后续把 `maven.compiler.release=8`（或引入 `animal-sniffer`）固化进 pom，可让构建自带该断言 |
| 3 | `MANIFEST.MF` 记录 `Build-Jdk: 19.0.1` | 个别审计场景会误读为「需要 JDK 19」 | 发布说明中已澄清；如需可加 `maven-jar-plugin` 的 manifest 条目定制 |
| 4 | 主 jar 内 `cn.hg.aifei.pay.internal.PayVerifyKey` 为 `public` 类 | 公共 API 面无影响（`internal` 包语义即为内部），但会被 javadoc/反射看到 | 与观察项 1 同源，建议由开发工程师确认是否改为包级可见 |
