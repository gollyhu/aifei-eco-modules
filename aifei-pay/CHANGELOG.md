# 变更日志

本文件记录 `aifei-pay` 的版本变更。版本号遵循语义化版本（MAJOR.MINOR.PATCH），最新版本置顶。
条目只写**使用者可感知的变化**，不罗列源文件名。

---

## [1.0.0-SNAPSHOT] - 2026-11-26（V2.0 结构重构）

**破坏性变更**：模块由「支付宝单通道门面」重构为「基于 IJPay 的多支付通道聚合 SDK」。

- 公共面改为 `PayClient` / `PayKit` / `PayNotifyUtil` / `PayException`，新增 `model.PayNotifyRequest`、`spi.PayNotify` / `PayNotifyListener`、`aifei.PayHandler` / `PayIo`；全部 `AliPay*` 类型删除，不提供兼容层。
- 交易动作收敛为 `createOrder(... payType ...)` / `query` / `refund` / `channelName`；支付宝 `QRCODE` / `WAP` / `PAGE` 由 `payType` 选择。
- 配置改为 `pay.default-channel` + `pay.<name>.type` + `pay.<name>.*`；`PayTypes` 白名单覆盖 IJPay 全支付渠道，支付宝已完整迁移，微信及其它渠道按适配器逐项落地。
- 回调采用 `PayNotifyUtil.verify` 与 `PayHandler` + `PayNotifyListener` 共存，两者共用同一验签核心；`PayHandler` 按通道写成功/失败应答。
- 本模块保留 `wxpay` 通道，与 `aifei-weixin` 并存、用户二选一，模块间零 import 依赖。
- 本地校验：`mvn clean verify` → `Tests run: 161, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`。

## [1.0.0-SNAPSHOT] - 2026-11-26（V1.1 历史口径）

**状态：未发布（本地构建通过，待正式发布）**

- 坐标：`io.github.gollyhu:aifei-pay:1.0.0-SNAPSHOT`（`packaging=jar`）
- 公共包：`cn.hg.aifei.pay`；基线：Java 8（产物 class major = 52）
- 本地校验事实：`mvn clean verify` → `Tests run: 153, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`（19.010 s）
- `-SNAPSHOT` 后缀为本版本的有意保留：模块尚未正式发布到中央仓库

### 新特性

- **三种下单形态**，一个方法一把钥匙：
  - 当面付扫码下单（`precreate`）：产出二维码内容串，载荷类型恒为 `QRCODE`
  - 手机网站支付（`wapPay`）：产出可自提交的表单 HTML 或跳转 URL
  - 电脑网站支付（`pagePay`）：与 H5 同构、渠道不同
  - 三种形态统一返回「载荷 + 载荷类型」两张牌，业务侧无需判断内核返回了什么
- **交易查单**（`query`）：透出交易状态与实付金额；交易不存在时抛 `PayException` 且 `code()` 为 `ACQ.TRADE_NOT_EXIST`，业务可用一个错误码判定「订单不存在」
- **退款**（`refund`）：`outRequestNo` 作为幂等键**原样透传**给支付宝并回显，重试退款不会产生新退款单（去重责任在业务侧，模块只保证幂等键不丢失、不改写）
- **回调验签**，异步通知与同步跳转同一条路径：
  - 普通公钥模式与公钥证书模式**双支持**，同一 appId 可混用
  - **多凭据轮换**：同一 appId 可配置多组验签凭据（`verify.0` / `verify.1` / …），任一通过即通过 —— 证书/密钥轮换期新旧可并存，无需停服切换
  - 证书模式下报文带证书序列号时优先选用序列号匹配的凭据
  - 两种消费方式：布尔语义（`verify`）与「失败即抛异常」语义（`verifyAndParse`）；纯函数、不改入参、不写缓存
- **多应用隔离**：`appId` 是每个交易方法的第一参数，每个应用独立客户端实例、独立凭据、独立网关，互不串扰；传 `null` / 空白自动回退 `pay.default-app-id`
- **aifei 框架装配**：提供 `PayPlugin implements Plugin`，加入插件链即完成「解析配置 → 依赖探测 → 预建客户端 → 注册单例」；业务侧只需 `@Inject PayClient`，静态上下文用 `PayKit.client()` 取同一实例
- **配置即校验**：启动期一次性校验配置合法性（必填项、凭据可读性、网关与环境一致性、凭据序号连续性、证书序列号一致性），非法即失败且不留半装配态；错误消息含键名，凭据一律掩码为 `***（长度 n）`，不会把私钥写进日志
- **依赖缺失可读**：内核或 BouncyCastle 缺失时，装配期抛带补齐提示的 `PayException`，不让 `NoClassDefFoundError` 冒到业务代码
- **金额口径防错**：入参统一「分」（`long ...Fen`，必须 > 0），出参是内核原样透出的元字符串；换算只用 `BigDecimal`，模块内不出现 `double` / `float`

### 依赖与约束

- `cn.aifei:aifei-all:1.1.2` 为 `provided`：由应用侧自带，不向下游传递
- `IJPay-AliPay-V3:3.0.0`、`alipay-sdk-java-v3:3.1.89.ALL`、`org.dom4j:dom4j:2.1.5`、`org.bouncycastle:bcprov-jdk18on:1.86` 全部为 `optional`：**不引入则不传递**，消费方须显式声明（接入片段见 `docs/release.md`）
- `alipay-sdk-java-v3` 的三个排除项不可移除：
  - `dom4j:dom4j`（旧坐标，与 `org.dom4j:dom4j` 冲突）
  - `bcprov-jdk15on`（与 `bcprov-jdk18on` 同名类冲突）
  - `jackson-databind-nullable`（该 SDK 版本中被引用 0 次；**升级 SDK 版本时必须重新验证**）
- `dom4j` 必须锁 `2.1.5`（2.2.0 起字节码为 55，超出 Java 8 基线）
- 运行期仅支持 `charset=UTF-8` 与 `sign_type=RSA2`（内核限制，其它值启动失败）
- `sign-mode=cert` 或验签凭据 `type=cert` 时，**运行期强依赖 `bcprov-jdk18on`**（装配期即探测）
- 模块两处告警日志依赖应用侧已装配日志工厂；在完全没有日志绑定的裸环境中取日志对象会失败，属框架既有契约
- 交易数据零落库：模块无数据库、无状态机、无补单/轮询/对账

### 已知限制

- **`stop()` 后已注入字段仍持旧实例**：`PayKit.client()` 会立即失败，但框架没有注销 API，已注入的 `PayClient` 字段不会自动置空。请避免在应用生命周期内反复装配
- **凭据归属环境无法机械判定**：模块只能校验 `pay.apps.<appId>.env` 必填合法、以及 `gateway-url` 的 host 与环境对应官方网关一致；「这把私钥属于沙箱还是生产」无法由代码判定，需靠配置评审与凭据来源管理兜底（同一套凭据可被同时配置到沙箱与生产应用，模块不会拦截）
- **证书模式强依赖 BouncyCastle**：缺少 `bcprov-jdk18on` 时证书模式一律失败（装配期即报错，不会延迟到首笔请求）
- **不做幂等、不做状态机**：模块只透出 `out_trade_no` / `out_request_no` / `tradeStatus`，去重与状态流转由业务负责；模块不记录已处理通知
- **不做收银台页面与 Servlet 适配**：只产出支付参数与跳转载荷，回调报文以 `String` / `Map` 入参
- **同步跳转报文可能为空**：用户中途放弃支付时不会带回参数，业务需容错
- **异常消息不含 cause 文本**：`PayException` 保留 `cause` 但不把其文本拼进消息，避免凭据或报文外泄

### 未验证项

以下各项**未在真实沙箱环境验证**，需持沙箱凭据时补测（依据 `docs/testing.md` §6）：

- 回调报文中证书序列号字段名是否真为 `alipay_cert_sn`：模块按该字段名固化行为，字段名真实性需实机抓取 notify 报文确认
- 交易不存在时内核是否稳定返回 `ACQ.TRADE_NOT_EXIST` 且非 2xx：模块侧已保证「内核给出该错误码即透出」，真实报文待沙箱调用一次
- `pay.connect-timeout` / `pay.read-timeout` 是否被 okhttp 执行路径实际消费：配置值写入 SDK 客户端已核，实际生效需真实请求
- `pay.page-http-method=GET` 时内核返回的是否为可直接跳转的 URL：模块侧已保证返回值被标记为 `URL`，内核真实返回形态待沙箱
- 真实沙箱环境下的首笔下单/查单/退款联调（含 `*Content` 与 `*Path` 两种凭据注入形态的等价性：后者已在代码级与装配实测闭环）
