# aifei-oss 设计文档

> 本文是 `aifei-oss` 的**设计事实来源**：契约、决策与验收以本文为准。
> 文档分工：`README.md` 面向使用者（安装 / 用法 / 配置）；本文面向设计者与实现者；`AGENTS.md` 面向开发代理与贡献者；`docs/testing.md` 面向测试维护者。

## 1. 模块定位与设计原则

### 1.1 定位

`aifei-oss`（坐标 `io.github.gollyhu:aifei-oss:1.0.0`）为 aifei 应用补齐**多平台对象 / 文件存储**能力：以统一接口把文件存到本地磁盘、FTP / SFTP、以及阿里云 OSS、腾讯云 COS、华为云 OBS、MinIO、AWS S3 等 30+ 存储平台。

> **发布状态**：本模块**尚未对外发布**（本仓库当前仅 `aifei-cache` 具备 Maven Central 发布能力），上列坐标为预留，尚无发布产物。

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

- **事实澄清**：官方文档中的「JDK 11+」指的是**构建 x-file-storage 自身**的环境要求（其源码根 pom 用 `maven-enforcer-plugin` 强制 JDK `[11,)`），**不影响以 Maven 依赖方式消费其发布 jar 的下游项目**。**注意**：Maven Central 上的发布 pom 是 flatten 产物，**不含任何 Java 版本属性**，因此兼容性结论**只能以发布 jar 的字节码实测为准**，不得据发布 pom 断言。
- **定档版本**：`2.3.0`（**已实测通过**，2026-10-16）。
- **P0 门禁（已执行并通过）**：
  1. `mvn dependency:get -Dartifact=org.dromara.x-file-storage:x-file-storage-core:2.3.0` ✓
  2. 字节码实测：`x-file-storage-core-2.3.0.jar` 内 **250 个 class 全部 `major version = 52`** ✓（Java 8 字节码）；`2.2.1` 亦为 223/223 = 52
  3. 传递依赖（`compile`，随本模块下发）：`cn.hutool:hutool-core:5.8.28` / `org.apache.tika:tika-core:2.4.1` / `net.coobird:thumbnailator:0.4.20`，均 Java 8 兼容 ✓；`org.springframework:spring-core` 仅作为 `bce-java-sdk` 的 `<exclusions>` 出现，**不构成传递依赖**；各云厂商 SDK（含 2.3.0 新增的 `software.amazon.awssdk:s3` / `mongodb-driver-sync` / `ve-tos-java-sdk`）均为 `provided + optional`（消费方自带）✓
- **冻结契约**：一旦定档，升级版本须先更新本节并重跑 P0 门禁。

### 3.2 依赖范围：`compile` 例外（**设计决策**）

- `x-file-storage-core` 是本模块的**实现内核**，作为 **`compile`** 依赖引入（非 `optional` / `provided`）。
- 这是仓库根 `AGENTS.md`「第三方依赖一律 `optional` 或 `provided`」的**显式例外**。理由：门面能力完全建立在 x-file-storage 之上，脱离它本模块无任何功能；若标 `optional`，消费方每次都必须手动声明 x-file-storage，徒增负担且无收益。
- `README.md` 与 `AGENTS.md` 必须同步声明该例外。

### 3.3 仅依赖 `core`，排除 `spring` / `solon`（**设计决策**）

- aifei 是**非 Spring** 环境，**只引入 `x-file-storage-core`**（Java 8 产物、无框架绑定）。
- **禁止**引入 `x-file-storage-spring` / `x-file-storage-solon`：这两者是框架集成层，`spring` 模块基于更高 JDK / Spring Boot 基线，且与 aifei 的 `Plugin` 装配范式冲突。
- `FileStorageService` 一律用 **`FileStorageServiceBuilder` 编程式构建**，不使用任何 Spring 注解 / 自动配置。
- 构建固定走 `FileStorageServiceBuilder.create(properties).useDefault().build()`：只有 `useDefault()` 会装配默认文件记录器、Tika 工厂与**全部 `FileWrapperAdapter`**；缺了适配器，`of(InputStream)` / `of(byte[])` 的上传路径不可用。
- 平台族只在 `FileStorageProperties` 对应列表非空时构建，未配置的平台族不受影响；但**云 SDK 缺失不会在启动期被发现**（平台客户端惰性建连），见 §7。

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
| `cn.hg.aifei.oss.internal` | **非承诺 API** | x-file-storage 适配层：`FileStorageService` 构建（`OssStorageFactory`）、类型映射（`OssMappers`）、路径换算（`OssPaths`）、异常翻译（`OssErrors`）、注册表（`OssClients`）、客户端实现（`XfsOssClient` / `XfsOssObject`）、**平台配置值对象与其字段级校验（`OssConfig`，见 §5.3）** |
| `cn.hg.aifei.oss.aifei` | **接入层** | `OssPlugin`（实现 `cn.aifei.plugin.Plugin`：`start()` 解析配置并装配、`stop()` 注销单例与注册表，两者幂等，对齐 `aifei-mq` 的 `MqPlugin` 范式）、`OssConfigLoader` |

### 4.2 架构门禁（`ArchitectureBoundaryTest`）

源码文本扫描 `src/main/java` 下全部 `.java`，断言：

| 类型族 | 允许出现的包 | 门禁断言 |
| --- | --- | --- |
| `org.dromara.x.file.storage.*`（x-file-storage） | **仅** `cn.hg.aifei.oss.internal` | `xfsOnlyInAdapterPackage` |
| `cn.aifei.*`（框架类型，除 `cn.hg.aifei.oss.*`） | **仅** `cn.hg.aifei.oss.aifei` | `frameworkOnlyInBridgePackage` |
| `java.sql.*` / `io.undertow.*` | **全模块禁止** | `noSqlOrUndertowReference` |
| 根包公共类型数量 | **精确 5 个**（`internal` 与 `aifei` 不计入），且文件名集合精确相等 | `publicSurfaceExact` |

`ArchitectureBoundaryTest` 依赖相对路径 `src/main/java`，**必须在模块根目录执行**。不得为兼容错误用法而放宽断言。

## 5. 公共接口契约

根包公共类型**固定 5 个**：`OssClient` / `OssKit` / `OssFileInfo` / `OssObject` / `OssException`。新增须先过设计决策。**`OssConfig` 不在公共面上**——它未出现在任何公共 API 签名中，仅被适配层消费，已下移 `internal`（见 §5.3 与 §10.1）。

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

- 未装配即调用、或平台标识未注册 → 抛 `OssException`（fail-fast，避免隐式 NPE）。**口径说明**：`aifei-cache` 的 `CacheUtil` 在此分别抛 `IllegalStateException` / `IllegalArgumentException`；本模块统一收敛为 `OssException`，属**有意差异**（是否保留见 §10.2）。

### 5.3 `OssConfig`（**非公共 API**：平台配置值对象 + 校验唯一定义点）

- **归属**：位于 `cn.hg.aifei.oss.internal`；列在此处仅为其契约说明，它**不出现在任何公共 API 签名中**，消费方不得引用（见 §10.1）。
- 持有**单个平台**的配置：`platform`、`type`、`options`（平台配置项键值对）。
- 字段级校验（`platform` / `type` 非空白）的**唯一定义点**在本类；实现类（含 `aifei.OssConfigLoader`）不得复制校验。
- `toString` 不输出 `options`（可能含 `secret-key` 等敏感项）。

### 5.4 `OssFileInfo`（上传 / 查询返回的元数据）

- 字段：`path`、`size`、`contentType`、`url`（若平台可生成访问地址）、`platform`、`metadata`（不可变 Map）。
- 为**不暴露 x-file-storage 的 `FileInfo`** 而设的自有值对象。

### 5.5 `OssObject`（下载返回）

- 封装对象内容与元数据：`String getPath()`、`long getContentLength()`、`String getContentType()`、`byte[] bytes()`（一次性读入）、`void writeTo(OutputStream out)`（流式写出）。
- 底层下载器为**回调式**（不直接暴露 `InputStream`），故本接口不含 `getInputStream()`；流在其实现内部按次开启与关闭，调用方无需管理流生命周期。是否声明 `AutoCloseable`（当前结论：不声明）见 §10.2。

### 5.6 `OssException`

- 本模块唯一公共异常；x-file-storage / 云 SDK 的异常一律在适配层翻译为本类型后再上浮。
- 异常消息**不得**包含第三方类型名。

## 6. 配置模型（`oss.*`）

在 `app-config.txt`（`PropKit` 载入）中声明，前缀 `oss.`（对齐既有 `cache.*` / `websocket.*`）：

| 键 | 必填 | 说明 |
| --- | --- | --- |
| `oss.default-platform` | 多平台时必填 | 默认平台标识。**已定档**：**单平台时**可省略（缺省即该平台，唯一即首个，与哈希序无关）；**平台数 ≥ 2 时必填**，缺失或指向未声明平台 → 启动失败。底层 `FileStorageProperties.defaultPlatform` 的默认值是 `local`，本模块**一律显式设置**，不得静默沿用 |
| `oss.<name>.type` | 是 | 平台类型，合法取值**已定档为 20 项**（`FileStorageProperties` 平台族字段 camel→kebab 机械派生）：`local` / `local-plus` / `huawei-obs` / `aliyun-oss` / `qiniu-kodo` / `tencent-cos` / `baidu-bos` / `upyun-uss` / `minio` / `amazon-s3` / `amazon-s3-v2` / `ftp` / `sftp` / `webdav` / `google-cloud-storage` / `fastdfs` / `azure-blob` / `mongo-gridfs` / `go-fastdfs` / `volcengine-tos`。未列入者 → 启动失败。**注意**：火山引擎的正确键是 `volcengine-tos`，`ve-tos` 在 2.3.0 中**不存在** |
| `oss.<name>.<key>` | 否 | 平台配置项，键名与 x-file-storage 平台配置字段一致（kebab→camel 映射），如 `access-key` / `secret-key` / `end-point` / `bucket-name` / `base-path` / `domain`；键名无法映射到平台配置字段、或取值无法转换为字段类型 → 启动失败 |

- **平台列表由 `oss.<name>.type` 推导**（`Prop.getProperties()` 枚举键），无需额外声明 `oss.names`。
- **实例名 ≠ 平台类型**：`oss.<name>.*` 中的 `<name>` 是平台标识（写入平台配置的 `platform` 字段，供 `OssKit.use(name)` 取用），`type` 才是平台族的类型键。
- 配置键名与解析的**唯一定义点**在 `aifei.OssConfigLoader`；平台类型与配置项到 x-file-storage 配置对象的映射（kebab→camel）唯一定义点在 `internal.OssStorageFactory`（20 项类型表 + 平台族归组 + 按字段类型转换写入）。

## 7. 行为契约

- **对象定位（硬约束）**：x-file-storage 的 `getFileInfoByUrl(url)` / `delete(String url)` / `exists(String url)` / `download(String url)` 一律委托 `FileRecorder`，而默认 `DefaultFileRecorder.getByUrl()` **恒抛异常**（「尚未实现 FileRecorder 接口」），默认记录器不落任何存储。因此本模块**禁用**上述 String url 一族 API：所有定位操作必须自行构造 `FileInfo`（`platform` / `basePath` / `path` / `filename` 与写入时逐字一致），再调用 `FileInfo` 一族（`delete(FileInfo)` / `exists(FileInfo)` / `download(FileInfo)`）。换算规则的唯一定义点是 `internal.OssPaths`。
- **`path` 语义与切分口径（已定档）**：`path` 是平台 `base-path` **之下的完整逻辑对象 key**（如 `upload/2026/a.png`），**不含** `base-path` 前缀。切分规则：`dir` = 末个 `/` 之前的部分**（含尾 `/`；无 `/` 时为空串）**，`filename` = 末个 `/` 之后的部分；即底层物理 key = `basePath + path + filename` 的**逐字拼接**（`FileStorage#getFileKey` 不补分隔符，故尾 `/` 必须保留，否则会粘连成 `upload2026/a.png` 之类）。上传以 `setPath(dir).setSaveFilename(filename)` 写入（`saveFilename` = 末段），使「按 path 再次定位」成立。边界校验（同属 `internal.OssPaths` 唯一点）：`path` 空白 / 以 `/` 开头 / 含 `..` / 以 `/` 结尾 → `OssException`。
- **上传**：`upload` 成功返回元数据；失败抛 `OssException`。同名路径**跟随 x-file-storage 默认覆盖语义**（同名覆盖）。
- **下载**：`download` 对不存在的对象抛 `OssException`；返回的 `OssObject` 内容按需读取，无需关闭。
- **删除**：`delete` 对不存在对象返回 `false`（不抛异常）；删除成功返回 `true`。
- **存在性**：`exists` 对不存在对象返回 `false`。
- **签名 URL**：`generatePresignedUrl` 的可用性取决于平台能力，不支持时抛 `OssException`。
- **复制**：`copy` **本期未接线**——调用即抛 `OssException`。底层能力**已存在**：x-file-storage 2.3.0 提供 `copy(FileInfo)` / `copy(String url)`（返回 `CopyPretreatment`）与 `isSupportSameCopy()` 支持度探测；接线与否待定档（见 §10.3 第 2 项）。跨平台复制不在本期范围。
- **平台选择**：`@Inject OssClient` = 默认平台；多平台经 `OssKit.use(name)`。
- **装配失败即启动失败**：未配置任何平台、平台类型未知或必填配置缺失，在 `OssPlugin.start()` 即抛异常使应用启动失败，不留半装配状态。**注意**：云厂商 SDK 缺失属**运行期**故障——x-file-storage 的平台客户端惰性建连，通常延迟到首次使用该平台时才抛 `NoClassDefFoundError`（见 `README.md`「常见问题」），本模块不在启动期强制加载各平台 SDK。

## 8. 测试与验收

- **判据**：`mvn clean verify` 必须 0 failure / 0 error。
- **单元 / 契约测试**（默认联网无关）：以 x-file-storage **本地存储（`local`）** 配临时目录承载，覆盖上传 / 下载 / 存在性 / 元数据 / 删除的成功与失败语义（`LocalStorageTest`）。
- **集成测试**（**本期只落开关与参数透传，用例未实现**——见 §10.3）：以 **MinIO**（S3 兼容）验证真实对象存储，开关 `-Doss.integration=true`（默认 `false`，命名对齐 cache 的 `redis.integration`）；开启后连不上必须失败，不得静默跳过。连接参数**已定档**：`-Doss.endpoint`（默认 `http://127.0.0.1:9000`）/ `-Doss.access-key`、`-Doss.secret-key`（默认 `minioadmin`）/ `-Doss.bucket`（默认 `aifei-oss-test`），经 surefire `systemPropertyVariables` 透传。
- **门禁**：`ArchitectureBoundaryTest` 四组断言（见 §4.2）。
- **字节码约束**：主 JAR 内全部 class 的 major version 必须为 **52**（`ClassFileVersionTest`）。

详细测试分层与用例清单见 `docs/testing.md`。

## 9. 有序任务列表（含依赖）

| 批 | 名称 | 依赖 | 状态 |
| --- | --- | --- | --- |
| **P0** | XFS 版本 Java 8 兼容性实测（major=52） | 无 | ✅ 完成（已复核通过） |
| **B1** | 骨架 + 公共面（`pom.xml`、根包 5 类型） | P0 | ✅ 完成 |
| **B2** | 适配层（`internal`：构建 / 映射 / 路径 / 异常 / 注册表 / 客户端 / 平台配置值对象） | B1 | ✅ 完成 |
| **B3** | 接入层（`aifei`：`OssPlugin` / `OssConfigLoader`） | B2 | ✅ 完成 |
| **B4** | 测试与门禁（`ArchitectureBoundaryTest` / `ClassFileVersionTest` / `LocalStorageTest`） | B1 / B3 | ✅ 完成（`mvn clean verify` 6 项用例 0 failure / 0 error） |
| **B5** | 文档四件套（`README.md` / `AGENTS.md` / `docs/testing.md`） | B4 | ✅ 完成（随实现同步，含公共面 6→5、`ve-tos`→`volcengine-tos`、§10 决策回填） |

> 状态以**仓库实际产物**为准：模块内已有 `pom.xml` + `src/main/java`（根包 5 个公共类型、`internal` 8 个、`aifei` 2 个）+ `src/test/java`（3 支测试）。

## 10. 已定档决策与后续演进

### 10.1 已定档

| 项 | 结论 |
| --- | --- |
| 实现路径 | 基于 X File Storage 二次封装，非自实现协议 |
| 依赖范围 | `x-file-storage-core` 为 `compile`（`optional/provided` 规则的显式例外；**已登记于仓库根 `AGENTS.md`「已登记的 `compile` 例外」条款**） |
| 框架集成 | 仅用 `core` + `FileStorageServiceBuilder`，禁 `spring` / `solon` 模块 |
| 公共面 | 根包固定 **5 个**公共类型（`OssClient` / `OssKit` / `OssFileInfo` / `OssObject` / `OssException`），不暴露 x-file-storage 类型；**`OssConfig` 下移 `internal`**（不属公共 API） |
| 配置前缀 | `oss.*`；平台列表由 `oss.<name>.type` 推导 |
| 多平台 | `@Inject OssClient` = 默认平台；`OssKit.use(name)` = 指定平台 |
| 平台类型全集 | **20 项**（`FileStorageProperties` 平台族字段 camel→kebab 机械派生）；未知类型 → 启动失败；火山引擎正确键为 `volcengine-tos` |
| `default-platform` | **单平台可省略**（缺省即该平台）；**平台数 ≥ 2 必填**，缺失或指向未声明平台 → 启动失败；一律显式 `setDefaultPlatform(...)` |
| `path` 语义 | 平台 `base-path` 之下的完整逻辑对象 key；`dir` 保留尾 `/`（底层逐字拼接不补分隔符） |
| `OssObject` 关闭语义 | **不声明** `AutoCloseable`（底层回调式下载，调用方无需关闭） |
| 异常口径 | `OssKit` 未装配 / 未知平台**统一抛 `OssException`**，与 `aifei-cache` 的分层口径差异属**有意为之** |
| `copy` | 本期**未接线**，调用即抛 `OssException` |
| 集成测试参数 | `oss.integration` / `oss.endpoint` / `oss.access-key` / `oss.secret-key` / `oss.bucket`（surefire 透传） |

### 10.2 前次裁决记录（2026-10-16 拍板）

原「开工前须裁决」7 项已全部定档，结论如下（实现与文档均已按此收口）：

1. **平台类型取值全集与映射**：`type` 合法值 = `FileStorageProperties` 平台族字段名 camel→kebab **机械派生，共 20 项**；映射表固化为 `internal.OssStorageFactory` 的静态表，未知类型 → `OssException`。**火山引擎正确键为 `volcengine-tos`**（2.3.0 无 `veTos` 字段，原 `ve-tos` 写法已废弃），`README.md` 与本文均已修正。另确认**实例名 ≠ 平台类型**：`oss.<name>.type` 的 `<name>` 是平台标识（`platform` 字段），`type` 才是平台族键。
2. **`oss.default-platform` 缺省规则**：**单平台 → 缺省即该平台**（唯一解，不涉哈希序）；**平台数 ≥ 2 → 必填**，缺失或指向未声明平台即启动失败；一律显式设置，不静默沿用底层 `local`。
3. **`path` 语义与换算口径**：见 §7「`path` 语义与切分口径（已定档）」，唯一定义点 `internal.OssPaths`。
4. **`OssConfig` 归属**：**下移 `internal`，公共面取 5**（对齐 `aifei-cache` 配置域在 `internal.Config` 的范式；模块尚未发布，改动零兼容成本）。已同步 §4.1 / §4.2 / §5 / §6、`AGENTS.md`、`docs/testing.md` 与门禁断言 `publicSurfaceExact`。其「字段级校验唯一定义点」不变量保持。
5. **`OssObject` 是否声明 `AutoCloseable`**：**不声明**，并在 javadoc 写清「无需关闭，`bytes()` / `writeTo(OutputStream)` 按次读取，流由实现内部管理」。
6. **集成测试连接参数命名**：`-Doss.integration=true`（开关）/ `-Doss.endpoint`（默认 `http://127.0.0.1:9000`）/ `-Doss.access-key`、`-Doss.secret-key`（默认 `minioadmin`）/ `-Doss.bucket`（默认 `aifei-oss-test`）；取短名 `endpoint` 以区别于平台配置键 `end-point`。
7. **异常口径**：**保留统一 `OssException`**，视为有意差异（本模块公共异常只有一种，调用方只需 catch 一种）。

### 10.3 后续演进须裁决

1. `generatePresignedUrl` 在各平台的可用性与统一实现方式（当前直传 x-file-storage 的 presigned 能力）。
2. `copy` 是否接线：底层 x-file-storage 2.3.0 **已提供** `copy(FileInfo)` / `CopyPretreatment` 与 `isSupportSameCopy()` 支持度探测，本期的公共方法「调用即抛 `OssException`」属占位；接线 / 从公共面移除 / 维持现状三选一。
3. 是否需要 `upload` 的「禁止覆盖 / if-absent」变体（当前跟随默认覆盖）。
4. 文件列表 / 分页列举（`listFiles`）是否纳入公共 API。
5. 是否提供 `aifei-oss-demo` 示例工程。
