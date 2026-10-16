# aifei-oss 设计文档

> 本文是 `aifei-oss` 的**设计事实来源**：契约、决策与验收以本文为准。
> 文档分工：`README.md` 面向使用者（安装 / 用法 / 配置）；本文面向设计者与实现者；`AGENTS.md` 面向开发代理与贡献者；`docs/testing.md` 面向测试维护者。

## 1. 模块定位与设计原则

### 1.1 定位

`aifei-oss`（坐标 `io.github.gollyhu:aifei-oss:1.0.0`）为 aifei 应用补齐**多平台对象 / 文件存储**能力：以统一接口把文件存到本地磁盘、FTP / SFTP、以及阿里云 OSS、腾讯云 COS、华为云 OBS、MinIO、AWS S3 等 30+ 存储平台。

### 1.2 基于 X File Storage 二次封装（非自实现）

本模块**不自行实现任何存储协议、签名与分片逻辑**，而是基于成熟的 [X File Storage](https://x-file-storage.dromara.org)（Maven 坐标 `org.dromara.x-file-storage`，dromara 开源组织，Apache-2.0）二次封装。x-file-storage 已覆盖各厂商协议的适配、签名、分片上传、迁移与缩略图等能力。

本模块只做四件事：

1. **装配**：实现 `Plugin`，在 `start()` 读取 `oss.*` 配置并构建 x-file-storage 的 `FileStorageService`。
2. **薄门面**：对外暴露 aifei 风格的极简 API（`OssClient` / `OssKit`）。
3. **类型收敛**：x-file-storage 的类型（`FileStorageService`、`FileInfo`、各平台 `FileStorage` 等）**不得**出现在公共 API 面。
4. **框架接线**：经 `@Inject` 注入客户端、经 `app-config.txt` 承载配置，与 aifei 既有装配范式一致。

### 1.3 设计原则

- **公共 API 极简、实现无关**：根包公共类型固定，不暴露 x-file-storage、云厂商 SDK、Spring 等任何实现类型。
- **每类规则唯一定义点**：配置键名、字段校验、类型映射、异常翻译各只有一处实现，实现类不得复制。
- **设计决策先于代码**：契约类行为（空值语义、异常类型、配置键名、默认平台选择等）未经本文决策不得自定。
- **可替换、可测试**：门面依赖适配层，测试可用本地存储或内存伪实现跑通，无需联网。

## 2. 术语表

| 术语 | 含义 |
| --- | --- |
| **存储平台**（platform） | 一个可独立寻址的存储目标，由 `oss.<name>.*` 配置块声明，`<name>` 即平台标识（如 `aliyun-oss-1`） |
| **默认平台**（default-platform） | 未显式指定平台时使用的平台，由 `oss.default-platform` 声明；`@Inject OssClient` 对应默认平台 |
| **门面**（facade） | `OssClient`（实例，默认平台）与 `OssKit`（静态，按平台取用） |
| **适配层**（adapter） | 位于 `internal` 包，把门面调用翻译为 x-file-storage 调用，并做类型映射与异常翻译；属**非承诺 API** |
| **接入层**（bridge） | 位于 `aifei` 包，唯一允许引用 `cn.aifei.*` 的层，含 `Plugin` 与配置加载器 |
| **对象路径**（path） | 业务侧用于定位对象的逻辑路径 / key，与平台内的物理 key 对应 |

## 3. 依赖策略决策（**必须先读**）

### 3.1 X File Storage 版本与 Java 8 兼容性（**已实测通过**）

- **事实澄清**：x-file-storage `2.3.0` 的**根 pom** 声明 `<java.version>8</java.version>`，且 `maven.compiler.source / target / release` 均锁定为 `8`，即**发布产物的字节码为 Java 8（class major 52）**。其官方文档中「JDK 11+」指的是**构建 x-file-storage 自身**的环境要求（`maven-enforcer-plugin` 强制 JDK `[11,)`），**不影响以 Maven 依赖方式消费其发布 jar 的下游项目**。
- **定档版本**：`2.3.0`（**已实测通过**，2026-10-16）。
- **P0 门禁（已执行并通过）**：
  1. `mvn dependency:get -Dartifact=org.dromara.x-file-storage:x-file-storage-core:2.3.0` ✓
  2. `javap -v` 实测 `x-file-storage-core-2.3.0.jar` 内 class 的 `major version = 52` ✓（Java 8 字节码）
  3. 传递依赖（`compile`，随本模块下发）：`cn.hutool:hutool-core` / `org.apache.tika:tika-core` / `net.coobird:thumbnailator`，均 Java 8 兼容；**无** `org.springframework.*`；各云厂商 SDK 为 `provided + optional`（消费方自带）✓
- **冻结契约**：一旦定档，升级版本须先更新本节并重跑 P0 门禁。

### 3.2 依赖范围：`compile` 例外（**设计决策**）

- `x-file-storage-core` 是本模块的**实现内核**，作为 **`compile`** 依赖引入（非 `optional` / `provided`）。
- 这是仓库根 `AGENTS.md`「第三方依赖一律 `optional` 或 `provided`」的**显式例外**。理由：门面能力完全建立在 x-file-storage 之上，脱离它本模块无任何功能；若标 `optional`，消费方每次都必须手动声明 x-file-storage，徒增负担且无收益。
- `README.md` 与 `AGENTS.md` 必须同步声明该例外。

### 3.3 仅依赖 `core`，排除 `spring` / `solon`（**设计决策**）

- aifei 是**非 Spring** 环境，**只引入 `x-file-storage-core`**（Java 8 产物、无框架绑定）。
- **禁止**引入 `x-file-storage-spring` / `x-file-storage-solon`：这两者是框架集成层，`spring` 模块基于更高 JDK / Spring Boot 基线，且与 aifei 的 `Plugin` 装配范式冲突。
- `FileStorageService` 一律用 **`FileStorageServiceBuilder` 编程式构建**，不使用任何 Spring 注解 / 自动配置。

### 3.4 aifei 框架依赖

- 唯一 aifei 依赖为 `cn.aifei:aifei-all:1.1.1`，保持 **`provided`**（提供 `Plugin` / `@Inject` / `Prop`，消费方自带，不向下游传递）。

### 3.5 各云厂商 SDK 由消费方自带

- x-file-storage 对**各云厂商 SDK**（`aliyun-sdk-oss`、`aws-java-sdk-s3`、`minio`、`cos_api`、`esdk-obs-java` 等）均为 `provided + optional` 依赖，**本模块不代为引入**。
- 消费方按需在自己工程引入所使用平台的 SDK；仅用本地存储时无需引入任何云 SDK。

## 4. 架构分层与包边界

### 4.1 包结构

| 包 | 性质 | 内容 |
| --- | --- | --- |
| `cn.hg.aifei.oss` | **公共 API** | 根包公共类型（见 §5） |
| `cn.hg.aifei.oss.internal` | **非承诺 API** | x-file-storage 适配层：`FileStorageService` 构建（`OssStorageFactory`）、类型映射（`OssMappers`）、路径换算（`OssPaths`）、异常翻译（`OssErrors`）、注册表（`OssClients`）、客户端实现（`XfsOssClient` / `XfsOssObject`） |
| `cn.hg.aifei.oss.aifei` | **接入层** | `OssPlugin`（实现 `cn.aifei.plugin.Plugin`）、`OssConfigLoader` |

### 4.2 架构门禁（`ArchitectureBoundaryTest`）

源码文本扫描 `src/main/java` 下全部 `.java`，断言：

| 类型族 | 允许出现的包 | 门禁断言 |
| --- | --- | --- |
| `org.dromara.x.file.storage.*`（x-file-storage） | **仅** `cn.hg.aifei.oss.internal` | `xfsOnlyInAdapterPackage` |
| `cn.aifei.*`（框架类型，除 `cn.hg.aifei.oss.*`） | **仅** `cn.hg.aifei.oss.aifei` | `frameworkOnlyInBridgePackage` |
| `java.sql.*` / `io.undertow.*` | **全模块禁止** | `noSqlOrUndertowReference` |
| 根包公共类型数量 | **精确 6 个**（`internal` 与 `aifei` 不计入） | `publicSurfaceExact` |

`ArchitectureBoundaryTest` 依赖相对路径 `src/main/java`，**必须在模块根目录执行**。不得为兼容错误用法而放宽断言。

## 5. 公共接口契约

根包公共类型**固定 6 个**：`OssClient` / `OssKit` / `OssConfig` / `OssFileInfo` / `OssObject` / `OssException`。新增须先过设计决策。

### 5.1 `OssClient`（门面接口，默认平台）

```java
public interface OssClient {
    OssFileInfo upload(InputStream in, String path, String contentType); // 上传流，返回元数据
    OssFileInfo upload(byte[] data, String path, String contentType);    // 上传字节数组
    OssObject download(String path);                                     // 下载，返回内容 + 元数据
    boolean delete(String path);                                         // 删除，返回是否已存在并删除
    boolean exists(String path);                                         // 存在性判断
    OssFileInfo getFileInfo(String path);                                // 查询元数据
    String generatePresignedUrl(String path, long ttlSeconds);           // 生成限时访问 URL
    boolean copy(String sourcePath, String destPath);                    // 同平台内复制（本期未实现，见 §7）
}
```

- 由 `OssPlugin` 注册为单例，业务经 `@Inject OssClient` 取用（对应默认平台）。
- 全部方法线程安全。
- 上传固定文件名（`saveFilename` = 对象路径末段），使「按路径再次定位」成立。

### 5.2 `OssKit`（静态门面，多平台）

```java
public final class OssKit {
    public static OssClient defaultClient();            // 默认平台客户端
    public static OssClient use(String platformName);   // 按平台标识取用；未知平台抛 OssException
    public static Set<String> platforms();              // 已注册平台名集合（只读快照）
}
```

- 未装配即调用 → 抛 `OssException`（fail-fast，避免隐式 NPE）。

### 5.3 `OssConfig`（配置值对象 + 校验唯一定义点）

- 持有**单个平台**的配置：`platform`、`type`、`options`（平台配置项键值对）。
- 字段级校验（`platform` / `type` 非空白）的**唯一定义点**在本类；实现类不得复制校验。
- `toString` 不输出 `options`（可能含 `secret-key` 等敏感项）。

### 5.4 `OssFileInfo`（上传 / 查询返回的元数据）

- 字段：`path`、`size`、`contentType`、`url`（若平台可生成访问地址）、`platform`、`metadata`（不可变 Map）。
- 为**不暴露 x-file-storage 的 `FileInfo`** 而设的自有值对象。

### 5.5 `OssObject`（下载返回）

- 封装对象内容与元数据：`String getPath()`、`long getContentLength()`、`String getContentType()`、`byte[] bytes()`（一次性读入）、`void writeTo(OutputStream out)`（流式写出）。
- 底层下载器为**回调式**（不直接暴露 `InputStream`），故本接口不含 `getInputStream()`；流在其实现内部按次开启与关闭，调用方无需管理流生命周期。

### 5.6 `OssException`

- 本模块唯一公共异常；x-file-storage / 云 SDK 的异常一律在适配层翻译为本类型后再上浮。
- 异常消息**不得**包含第三方类型名。

## 6. 配置模型（`oss.*`）

在 `app-config.txt`（`PropKit` 载入）中声明，前缀 `oss.`（对齐既有 `cache.*` / `websocket.*`）：

| 键 | 必填 | 说明 |
| --- | --- | --- |
| `oss.default-platform` | 多平台时必填 | 默认平台标识；缺省取首个平台 |
| `oss.<name>.type` | 是 | 平台类型（如 `local` / `aliyun-oss` / `tencent-cos` / `minio` / `amazon-s3` / `huawei-obs` 等） |
| `oss.<name>.<key>` | 否 | 平台配置项，键名与 x-file-storage 平台配置字段一致（kebab→camel 映射），如 `access-key` / `secret-key` / `end-point` / `bucket-name` / `base-path` / `domain` |

- **平台列表由 `oss.<name>.type` 推导**（`Prop.getProperties()` 枚举键），无需额外声明 `oss.names`。
- 配置键名与解析的**唯一定义点**在 `aifei.OssConfigLoader`；平台类型与配置项到 x-file-storage 配置对象的映射（kebab→camel）唯一定义点在 `internal.OssStorageFactory`。

## 7. 行为契约

- **上传**：`upload` 成功返回元数据；失败抛 `OssException`。同名路径**跟随 x-file-storage 默认覆盖语义**（同名覆盖）。
- **下载**：`download` 对不存在的对象抛 `OssException`；返回的 `OssObject` 内容按需读取，无需关闭。
- **删除**：`delete` 对不存在对象返回 `false`（不抛异常）；删除成功返回 `true`。
- **存在性**：`exists` 对不存在对象返回 `false`。
- **签名 URL**：`generatePresignedUrl` 的可用性取决于平台能力，不支持时抛 `OssException`。
- **复制**：`copy` **本期未实现**（底层复制编排待设计决策），调用即抛 `OssException`；跨平台复制不在本期范围。
- **平台选择**：`@Inject OssClient` = 默认平台；多平台经 `OssKit.use(name)`。
- **装配失败即启动失败**：未配置任何平台、平台类型不支持或平台 SDK 未引入，表现为应用启动失败，不留半装配状态。

## 8. 测试与验收

- **判据**：`mvn clean verify` 必须 0 failure / 0 error。
- **单元 / 契约测试**（默认联网无关）：以 x-file-storage **本地存储（`local`）** 配临时目录承载，覆盖上传 / 下载 / 存在性 / 元数据 / 删除的成功与失败语义（`LocalStorageTest`）。
- **集成测试**（默认关闭）：以 **MinIO**（S3 兼容）验证真实对象存储，开关 `-Doss.integration=true`（命名对齐 cache 的 `redis.integration`）；开启后连不上必须失败，不得静默跳过。
- **门禁**：`ArchitectureBoundaryTest` 四组断言（见 §4.2）。
- **字节码约束**：主 JAR 内全部 class 的 major version 必须为 **52**（`ClassFileVersionTest`）。

详细测试分层与用例清单见 `docs/testing.md`。

## 9. 有序任务列表（含依赖）

| 批 | 名称 | 依赖 | 状态 |
| --- | --- | --- | --- |
| **P0** | XFS 版本 Java 8 兼容性实测（major=52） | 无 | ✅ 完成 |
| **B1** | 骨架 + 公共面（`pom.xml`、根包 6 类型） | P0 | ✅ 完成 |
| **B2** | 适配层（`internal`：构建 / 映射 / 路径 / 异常 / 注册表 / 客户端） | B1 | ✅ 完成 |
| **B3** | 接入层（`aifei`：`OssPlugin` / `OssConfigLoader`） | B2 | ✅ 完成 |
| **B4** | 测试与门禁（`ArchitectureBoundaryTest` / `ClassFileVersionTest` / `LocalStorageTest`） | B1 / B3 | ✅ 完成 |
| **B5** | 文档四件套（`README.md` / `AGENTS.md` / `docs/testing.md`） | B4 | ✅ 完成 |

## 10. 待确认项与已定档决策

### 10.1 已定档

| 项 | 结论 |
| --- | --- |
| 实现路径 | 基于 X File Storage 二次封装，非自实现协议 |
| 依赖范围 | `x-file-storage-core` 为 `compile`（`optional/provided` 规则的显式例外） |
| 框架集成 | 仅用 `core` + `FileStorageServiceBuilder`，禁 `spring` / `solon` 模块 |
| 公共面 | 根包固定 6 个公共类型，不暴露 x-file-storage 类型 |
| 配置前缀 | `oss.*`；平台列表由 `oss.<name>.type` 推导 |
| 多平台 | `@Inject OssClient` = 默认平台；`OssKit.use(name)` = 指定平台 |

### 10.2 待确认（后续演进须裁决）

1. `generatePresignedUrl` 在各平台的可用性与统一实现方式（当前直传 x-file-storage 的 presigned 能力）。
2. `copy` 的实现方式（底层复制 / 服务端复制）与跨平台复制的取舍。
3. 是否需要 `upload` 的「禁止覆盖 / if-absent」变体（当前跟随默认覆盖）。
4. 文件列表 / 分页列举（`listFiles`）是否纳入公共 API。
5. 是否提供 `aifei-oss-demo` 示例工程。
