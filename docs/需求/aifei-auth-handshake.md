# aifei-auth-handshake 模块需求

> 关联模块：`aifei-http`（出站 HTTP 通信 + 重试，本模块的传输底座）；`aifei-tools`（承载 KDF 派生能力，本模块经 `KdfKit` 取用，不自行实现）；`aifei-sa-token`（接入型鉴权模块，与本模块的自实现型分界，互补不重叠）。
> 公共 API 与契约细节由 `aifei-auth-handshake/docs/design.md` 定稿。

## 一、缺口与目标

用户描述了一种自定义接口认证流程：**请求认证 → 收到挑战码（challenge）→ 用 KDF(sn, challenge) 派生密钥 → 缓存密钥 → 将 challenge / auth_key 填入业务 JSON → 发业务请求 → 解析响应；鉴权失败时清缓存重握手（最多 2 次）**。

识别为「挑战-响应 + 密钥派生（KDF）」自定义握手认证：非标准命名协议，思想与 SCRAM / Kerberos / HTTP Digest 相通但更轻量——没有票据、没有域、没有多轮协商，只有「一次挑战 + 一次派生 + 一次缓存 + 失败重握」的最小闭环。

业务痛点：每个对接此类鉴权的下游服务都要各自抄一遍「发认证请求 → 取 challenge → 算密钥 → 填字段 → 失败重试」的状态机。其中 KDF 派生已由 `aifei-tools` 的 `KdfKit` 统一收口，**不再是缺口**；本模块补齐的是**编排层缺口**：把握手状态机、密钥缓存、字段填充、失败重握手收口为一个有状态客户端。

**三个目标**：

| # | 目标 | 具体含义 |
| --- | --- | --- |
| 1 | **握手闭环编排** | 一条 API 完成「认证 → 派生 → 缓存 → 填充 → 业务请求 → 解析」，业务不接触状态机细节 |
| 2 | **复用既有能力** | 出站通信与重试经 `aifei-http`；密钥派生经 `aifei-tools` 的 `KdfKit`，本模块不重复实现传输与 KDF |
| 3 | **家族首员** | 作为 `aifei-auth-*`（自实现型鉴权形态）首员落地，为未来 `aifei-auth-jwt` 立范式 |

## 二、非目标（能力边界）

| # | 边界 | 后果（README 与 `design.md` 必须声明） |
| --- | --- | --- |
| 1 | **不自行实现 KDF** | 密钥派生一律经 `aifei-tools` 的 `KdfKit.derive(...)`；门禁可断言本模块源码无 `javax.crypto.Mac` / `javax.crypto.SecretKeyFactory` 直接调用 |
| 2 | **不桥接 Sa-Token** | 与 `aifei-sa-token` 分工：后者是接入型（桥接既有 Sa-Token 框架），本模块是自实现型（自己实现握手协议状态机）；互补不重叠，README / AGENTS 须一句话点明 |
| 3 | **不做标准协议与非 HTTP 传输** | 不做多轮协商（SCRAM 三段）、票据体系（Kerberos TGT/TGS）、域 / realm、OAuth2 / OIDC；传输限 HTTP（经 `aifei-http`），不做 TCP 裸流握手 |
| 4 | **不抽 `aifei-auth-core`**（演进备忘，见 §三-5） | 待 handshake 与 jwt 均落地后按三次法则实证交集再提取；本轮不预先实现，不建空壳 |
| 5 | **本轮不做 `aifei-auth-handshake-demo`** | 待主交付稳定后按 `aifei-http-demo` 范式单独立项（本轮不立项），本轮不预置空壳 |
| 6 | **零第三方 / 零日志绑定 / 零 DB / 零 Web 面** | 见 §三-9；不引 okhttp、不引日志实现、不连数据源、不入站 HTTP |

## 三、需求约束（设计不得推翻）

| # | 前提 | 需求含义 |
| --- | --- | --- |
| 1 | **模块定位** | `io.github.gollyhu:aifei-auth-handshake:1.0.0-SNAPSHOT`，包 `cn.hg.aifei.auth.handshake`；**挑战-响应握手鉴权编排客户端**——有状态、联网、含重试编排 |
| 2 | **依赖定档** | `io.github.gollyhu:aifei-http:1.0.0-SNAPSHOT`（compile，出站 HTTP 通信 + 重试）；`io.github.gollyhu:aifei-tools:1.0.0-SNAPSHOT`（compile，KDF 取用，已落地）；`cn.aifei:aifei-all:1.1.2`（provided，消费方自带）。**KDF 已在 `aifei-tools:1.0.0-SNAPSHOT` 落地，本模块不依赖任何更高版本** |
| 3 | **KDF 取用方式** | 经 `aifei-tools` 的 `KdfKit` 调用：`KdfKit.derive(KdfSpec.hmac(hash, keyLen), 字节化的 ikm, 字节化的 data)`；`hash`（SHA-256 / SHA-512）与 `keyLen` 由配置指定；本模块**只取用、不自行实现**派生算法 |
| 4 | **家族与分界** | 家族前缀 `aifei-auth-*`（自实现型鉴权形态）：首员 handshake，次员 `aifei-auth-jwt`（未来）。与 `aifei-sa-token`（接入型）分界须在 README / AGENTS 一句话点明 |
| 5 | **架构演进备忘（不预先实现）** | 不抽 `aifei-auth-core`：待 handshake + jwt 落地后按三次法则实证交集再提取；`aifei-sa-token` 大概率不进 core（接入型异构） |
| 6 | **装配形态** | 接入包提供 `HandshakePlugin`（配置前缀 `handshake.*`）+ 有状态门面 `HandshakeKit` + `@Inject` 取用；构造期只持 `Prop`，`start()` 内解析校验，失败不留半装配状态（对齐 `MqPlugin` / `HttpPlugin` 范式） |
| 7 | **握手闭环** | 认证请求 → 取 challenge → 调 `KdfKit` 派生 `auth_key` → 缓存 → 填 `challenge` / `auth_key` 进业务 JSON → 发业务请求 → 解析；鉴权失败清缓存重握手（上限默认 **2** 次） |
| 8 | **门禁 `ArchitectureBoundaryTest`** | 源码文本扫描、模块根目录执行：① 根包公共类型精确 N + 集合相等；② 全模块禁 `java.sql.`；③ `cn.aifei.*` 只在 `aifei` 接入包；④ 接入包存在 `HandshakePlugin`；⑤ 公共签名零第三方 / 框架类型。**N 值由 `design.md` 定稿** |
| 9 | **非功能硬约束** | Java 8（`source` / `target` 1.8，class major 52）；零第三方 compile / runtime 依赖；零日志绑定；零 DB；零 Web 面；源码 UTF-8、4 空格缩进、简体中文注释 |

## 四、功能需求

| 需求编号 | 能力 | 说明 |
| --- | --- | --- |
| `R-01` | 握手发起 | 按配置向认证端点发起认证请求，解析响应取出 `challenge`（解析位置可经配置指定响应头或响应体字段） |
| `R-02` | 密钥派生 | 以 `sn` + `challenge` 的字节表示调用 `KdfKit.derive(KdfSpec.hmac(hash, keyLen), ikm, data)` 派生 `auth_key`；`hash` / `keyLen` 由配置指定；不自行实现 KDF |
| `R-03` | 密钥缓存 | 派生结果按会话缓存，供同一会话内后续业务请求复用（默认进程内，可选 `aifei-cache` 共享） |
| `R-04` | 业务请求填充 | 将 `challenge` / `auth_key` 填入业务请求 JSON 的指定字段（字段名可经配置覆盖） |
| `R-05` | 业务请求发送与解析 | 经 `aifei-http` 发送业务请求并解析响应；通信与重试由 `aifei-http` 负责，本模块不自建 HTTP 栈 |
| `R-06` | 失败重握手 | 业务请求鉴权失败（支持 HTTP 状态码与业务码判定）→ 清除缓存 → 重新握手 → 重发业务请求；上限默认 2 次、可配，超限抛可读异常、不静默返回 |
| `R-07` | 装配与门面 | `HandshakePlugin`（`handshake.*`）完成 `@Inject` 主客户端注册与 `HandshakeKit` 静态引用写入；`stop()` 清除；重复 `start()` / `stop()` 幂等 |
| `R-08` | 配置校验 | 认证端点缺失、`sn` 缺失、KDF 档位非法、字段映射缺失、重握手次数非法等 → 启动失败，消息含键名与原值 |

**配置能力诉求**（键名与默认值由 `design.md` 定稿）：

| 能力 | 默认 | 约束 |
| --- | --- | --- |
| 认证端点 URL | 无 | **必填**，缺失启动失败 |
| `sn` | 无 | **必填** |
| KDF 哈希档位 `hash` | SHA-256 | SHA-256 / SHA-512；非法值启动失败 |
| 派生密钥长度 `keyLen` | 由 `design.md` 定稿 | ≥ 1 |
| 业务字段名 `challenge` / `auth_key` / `sn` | `challenge` / `auth_key` / `sn` | 可经配置覆盖 |
| `challenge` 解析位置 | 响应体字段 | 响应头 / 响应体字段二选一 |
| 鉴权失败判定 | HTTP 401 | 支持状态码与业务码判定 |
| 重握手上限 | **2** | 可配，非法值启动失败 |

**P1（本次不做）**：demo；`aifei-auth-core`；标准协议与非 HTTP 传输；自行实现 KDF。

## 五、验收要点

每条均**可机械验证**：能被单测、门禁断言或 `mvn dependency:tree` / `javap` 类命令固定判定的。

| # | 判定条件（可度量） | 验证方式 |
| --- | --- | --- |
| 1 | **握手闭环**：认证 → 取 challenge → 派生 `auth_key` → 缓存 → 填业务 JSON → 发业务请求 → 解析，完整跑通（经 `aifei-http` 回环端点模拟认证服务与业务服务） | 集成测试（回环端点） |
| 2 | **失败重握手 ≤ 2**：业务请求鉴权失败时清缓存 → 重握 → 重发，上限 2；超过仍失败抛可读异常、不静默返回 | 集成测试（端点计数） |
| 3 | **缓存命中不重复握手**：同会话连续业务请求复用缓存 `auth_key`，认证端点握手次数保持 1（未被鉴权失败触发时） | 集成测试（端点计数） |
| 4 | **依赖**：`mvn dependency:tree` 出现 `aifei-http:1.0.0-SNAPSHOT`（compile）、`aifei-tools:1.0.0-SNAPSHOT`（compile）、`aifei-all:1.1.2`（provided），无其它第三方 compile / runtime 依赖 | `dependency:tree` 断言 |
| 5 | **KDF 取用**：派生经 `KdfKit.derive(...)`；门禁断言本模块源码无 `javax.crypto.Mac` / `javax.crypto.SecretKeyFactory` 直接调用（派生算法只在 `aifei-tools`） | 门禁源码扫描 + 集成测试 |
| 6 | **装配**：`HandshakePlugin.start()` 后 `@Inject` 主客户端非 null、可发握手请求；`stop()` 后 `HandshakeKit` 调用抛可读异常（不静默返回）；重复 `start()` / `stop()` 幂等 | 集成测试（容器装配） |
| 7 | **配置非法**：认证端点 / `sn` 缺失、KDF 档位非法、字段映射缺失、重握手次数非法 → 启动失败，消息含键名与原值 | 单元测试 |
| 8 | **门禁全绿**：`ArchitectureBoundaryTest` 全绿（根包公共类型精确 N + 集合相等、禁 `java.sql.`、`cn.aifei.*` 只在接入包、接入包存在 `HandshakePlugin`、公共签名零第三方 / 框架类型） | 门禁单测（模块根目录执行） |
| 9 | **Java 8**：主 JAR 全 class major == 52；源码无 `var` / `record` / `switch` 表达式 / text block / `List.of(` / `Map.of(` | `javap -verbose` + 门禁正则扫描 |
| 10 | **零联网单测**：经 `aifei-http` 回环端点（`com.sun.net.httpserver.HttpServer` 绑 `127.0.0.1` 随机端口）模拟认证 / 业务服务，断网环境全绿；测试源码 URL host 仅 `127.0.0.1` / `localhost` | 门禁源码扫描 + CI 断网跑 `mvn clean verify` |
| 11 | **文档四件套 + 分界**：`README.md` / `docs/design.md` / `docs/testing.md` / 模块 `AGENTS.md` 齐备，根 `README.md` 模块总览补条目，且与 `aifei-sa-token` 分界一句话点明 | 文档清点 |

