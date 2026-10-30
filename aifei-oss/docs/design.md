# aifei-oss 设计文档

> 本文是 `aifei-oss` 的**设计事实来源**：契约、决策与验收以本文为准。
> 文档分工：`README.md` 面向使用者（安装 / 用法 / 配置）；本文面向设计者与实现者；`AGENTS.md` 面向开发代理与贡献者；`docs/testing.md` 面向测试维护者。

## 1. 模块定位与设计原则

### 1.1 定位

`aifei-oss`（坐标 `io.github.gollyhu:aifei-oss:1.0.0`）为 aifei 应用补齐**多平台对象 / 文件存储**能力：以统一接口把文件存到本地磁盘、FTP / SFTP、以及阿里云 OSS、腾讯云 COS、华为云 OBS、MinIO、AWS S3 等 30+ 存储平台。

### 1.2 基于 X File Storage 二次封装（非自实现）

本模块**不自行实现任何存储协议、签名与分片逻辑**，而是基于成熟的 [X File Storage](https://x-file-storage.dromara.org)（`org.dromara.x-file-storage`，dromara 开源组织，Apache-2.0）二次封装。

本模块只做四件事：

1. **装配**：实现 `Plugin`，在 `start()` 读取 `oss.*` 配置并构建 x-file-storage 的 `FileStorageService`。
2. **薄门面**：对外暴露 aifei 风格的极简 API（`OssClient` / `OssKit`）。
3. **类型收敛**：x-file-storage 的类型**不得**出现在公共 API 面。
4. **框架接线**：经 `@Inject` 注入客户端、经 `app-config.txt` 承载配置。

### 1.3 设计原则

- **公共 API 极简、实现无关**：根包公共类型固定，不暴露 x-file-storage、云厂商 SDK、Spring 等实现类型。
- **每类规则唯一定义点**：配置键名、字段校验、类型映射、异常翻译各只有一处实现。
- **设计决策先于代码**：契约类行为未经本文决策不得自定。
- **可替换、可测试**：门面依赖适配层，测试可用本地存储或集成环境跑通。

## 2. 术语表

| 术语 | 含义 |
| --- | --- |
| **存储平台**（platform） | 一个可独立寻址的存储目标，由 `oss.<name>.*` 配置块声明 |
| **默认平台**（default-platform） | 未显式指定平台时使用的平台；`@Inject OssClient` 对应默认平台 |
| **门面**（facade） | `OssClient`（实例）与 `OssKit`（静态） |
| **适配层**（adapter） | 位于 `internal` 包，把门面调用翻译为 x-file-storage 调用；非承诺 API |
| **接入层**（bridge） | 位于 `aifei` 包，唯一允许引用 `cn.aifei.*` 的层 |
| **对象路径**（path） | 业务侧用于定位对象的逻辑路径 / key |

## 3. 依赖策略决策（**必须先读**）

### 3.1 X File Storage 版本与 Java 8 兼容性（**已实测通过**）

- **事实澄清**：x-file-storage `2.3.0` 的根 pom 声明 `<java.version>8</java.version>`，`source / target / release` 均为 `8`，即发布产物为 Java 8 字节码（major 52）。官方文档「JDK 11+」指**构建 x-file-storage 自身**的环境要求，不影响下游消费。
- **定档版本**：`2.3.0`（已实测，2026-10-16）。
- **P0 门禁（已执行并通过）**：
  1. `mvn dependency:get -Dartifact=org.dromara.x-file-storage:x-file-storage-core:2.3.0` ✓
  2. `javap -v` 实测 class `major version = 52` ✓
  3. 传递依赖（`compile`）：`hutool-core` / `tika-core` / `thumbnailator` / `slf4j-api:1.7.36`，均 Java 8 兼容；无 `org.springframework.*`；云厂商 SDK 为 `provided + optional` ✓
  4. 测试期：`slf4j-simple:1.7.36`（`test` scope），不随发布 ✓

### 3.2 依赖范围：`compile` 例外（**设计决策**）

- `x-file-storage-core` 作为 **`compile`** 依赖（仓库「第三方一律 `optional` / `provided`」的显式例外，理由：门面能力完全建立在其上）。

### 3.3 仅依赖 `core`，排除 `spring` / `solon`

- 只引入 `x-file-storage-core`；**禁止** `x-file-storage-spring` / `-solon`。
- `FileStorageService` 用 `FileStorageServiceBuilder.create(properties).useDefault().build()` 编程式构建；`useDefault()` 不可省。

### 3.4 aifei 框架依赖

- `cn.aifei:aifei-all:1.1.1` 保持 **`provided`**。

### 3.5 各云厂商 SDK 由消费方自带

- 云厂商 SDK 均为 `provided + optional`，本模块不代为引入；仅用本地存储时无需任何云 SDK。

## 4. 架构分层与包边界

### 4.1 包结构

| 包 | 性质 | 内容 |
| --- | --- | --- |
| `cn.hg.aifei.oss` | **公共 API** | `OssClient` / `OssKit` / `OssFileInfo` / `OssObject` / `OssPage` / `OssException`（6 个） |
| `cn.hg.aifei.oss.internal` | **非承诺 API** | `OssConfig` / `OssStorageFactory` / `OssMappers` / `OssPaths` / `OssErrors` / `OssClients` / `XfsOssClient` / `XfsOssObject` |
| `cn.hg.aifei.oss.aifei` | **接入层** | `OssPlugin` / `OssConfigLoader` |

### 4.2 架构门禁（`ArchitectureBoundaryTest`）

| 类型族 | 允许出现的包 | 门禁断言 |
| --- | --- | --- |
| `org.dromara.x.file.storage.*` | **仅** `cn.hg.aifei.oss.internal` | `xfsOnlyInAdapterPackage` |
| `cn.aifei.*` | **仅** `cn.hg.aifei.oss.aifei` | `frameworkOnlyInBridgePackage` |
| `java.sql.*` / `io.undertow.*` | **全模块禁止** | `noSqlOrUndertowReference` |
| 根包公共类型数量 | **精确 6 个** | `publicSurfaceExact` |

## 5. 公共接口契约

根包公共类型**固定 6 个**：`OssClient` / `OssKit` / `OssFileInfo` / `OssObject` / `OssPage` / `OssException`。

### 5.1 `OssClient`

```java
public interface OssClient {
    OssFileInfo upload(InputStream in, String path, String contentType);
    OssFileInfo upload(byte[] data, String path, String contentType);
    OssFileInfo uploadIfAbsent(InputStream in, String path, String contentType); // 非原子
    OssFileInfo uploadIfAbsent(byte[] data, String path, String contentType);    // 非原子
    OssObject download(String path);                                            // 惰性
    boolean delete(String path);                                                // 幂等
    boolean exists(String path);
    OssFileInfo getFileInfo(String path);
    List<OssFileInfo> listFiles(String path);                                   // 一次性
    OssPage listFiles(String path, int maxFiles, String marker);                // 分页
    String generatePresignedUrl(String path, long ttlSeconds);
    boolean copy(String sourcePath, String destPath);                           // 同平台
    boolean copyTo(String destPlatform, String sourcePath, String destPath);    // 跨平台
}
```

- 由 `OssPlugin` 注册为单例，经 `@Inject OssClient` 取用（默认平台）；多平台经 `OssKit.use(name)`。
- 全部方法线程安全；上传固定文件名，使「按路径再次定位」成立。

### 5.2 `OssKit`

```java
public final class OssKit {
    public static OssClient defaultClient();
    public static OssClient use(String platformName);
    public static Set<String> platforms();
}
```

### 5.3 `OssFileInfo` / `OssObject` / `OssPage`

- `OssFileInfo`：`path`、`size`、`contentType`、`url`、`platform`、`metadata`（不可变）。
- `OssObject`：`getPath()`、`getContentLength()`、`getContentType()`、`byte[] bytes()`、`void writeTo(OutputStream)`；惰性，无需关闭。
- `OssPage`：`getFiles()`（对象列表）、`getDirectories()`（子目录路径）、`getNextMarker()`、`isTruncated()`；不可变。

### 5.4 `OssException`

- 唯一公共异常；底层异常统一翻译；消息不含第三方类型名。

## 6. 配置模型（`oss.*`）

| 键 | 必填 | 说明 |
| --- | --- | --- |
| `oss.default-platform` | 多平台时必填 | 默认平台；单平台缺省取该平台 |
| `oss.<name>.type` | 是 | 平台类型（合法值见 `OssStorageFactory.supportedTypes()`，共 20 项） |
| `oss.<name>.<key>` | 否 | 平台配置项（kebab-case → camelCase 字段），如 `access-key` / `secret-key` / `end-point` / `bucket-name` / `base-path` / `domain` |

- 平台列表由 `oss.<name>.type` 推导；配置键名与解析唯一定义点：`aifei.OssConfigLoader`。

## 7. 行为契约

- **上传**：失败抛 `OssException`；同名路径跟随底层默认覆盖语义。
- **if-absent 上传**：对象已存在时**不覆盖**并返回既有元数据；**非原子**（并发下可能被后写覆盖）。
- **下载**：返回惰性 `OssObject`；读取内容（`bytes()` / `writeTo()`）时若不存在抛 `OssException`；无需关闭。
- **删除**：**幂等**（不存在也返回 `true`，不抛异常）。
- **存在性**：不存在返回 `false`。
- **列举**：`listFiles(path)` 一次性返回；`listFiles(path, maxFiles, marker)` 分页返回 `OssPage`（含对象、子目录、下一页游标、是否截断）；平台不支持列举时返回空结果。
- **复制**：`copy` 同平台（`CopyMode.AUTO`：服务端复制优先、不支持时降级下载+上传）；`copyTo` 跨平台（`CopyMode.CROSS`：下载源再上传到目标平台）。
- **签名 URL**：可用性取决于平台能力，不支持时抛 `OssException`。
- **装配失败即启动失败**：未配置平台、类型未知、必填项缺失或云 SDK 缺失，均使应用启动失败并回滚。

## 8. 测试与验收

- **判据**：`mvn clean verify` 必须 0 failure / 0 error。
- **单元 / 契约**：本地存储（`local-plus`）覆盖上传 / if-absent / 下载 / 存在性 / 元数据 / 删除 / 复制 / 列举（一次性 + 分页）/ 缺失对象语义（`LocalStorageTest`）。
- **配置契约**：字段校验、平台推导、默认平台规则（`OssConfigTest`）。
- **门禁**：`ArchitectureBoundaryTest` 四组断言。
- **字节码**：主 JAR 全部 class major = 52（`ClassFileVersionTest`）。
- **集成（默认关闭）**：MinIO（`-Doss.integration=true`，`MinioIntegrationTest`）；云 SDK 缺失装配契约（`CloudSdkMissingTest`）。

详细分层与用例清单见 `docs/testing.md`。

## 9. 任务状态

| 批 | 名称 | 状态 |
| --- | --- | --- |
| **P0** | XFS 版本 Java 8 兼容性实测 | ✅ |
| **B1** | 骨架 + 公共面 | ✅ |
| **B2** | 适配层 | ✅ |
| **B3** | 接入层 | ✅ |
| **B4** | 测试与门禁 | ✅ |
| **B5** | 文档四件套 | ✅ |
| **B6** | `copy`（AUTO 降级）、`listFiles`、MinIO 集成测试 | ✅ |
| **B7** | `aifei-oss-demo` | ✅ |
| **B8** | 跨平台复制 `copyTo`、分页列举 `OssPage`、`uploadIfAbsent`、目录信息 | ✅ |

## 10. 已定档与待演进

### 10.1 已定档

| 项 | 结论 |
| --- | --- |
| 实现路径 | 基于 X File Storage 二次封装 |
| 依赖范围 | `x-file-storage-core` 为 `compile`（显式例外） |
| 框架集成 | 仅 `core` + `FileStorageServiceBuilder`，禁 `spring` / `solon` |
| 公共面 | 根包固定 **6** 个公共类型 |
| 配置 | `oss.*`，平台列表由 `oss.<name>.type` 推导 |
| copy | 同平台 `AUTO`（服务端优先/降级）；跨平台 `copyTo` 用 `CROSS` |
| if-absent | 非原子（先 `exists` 再上传） |
| 列举 | 一次性 + 分页（`OssPage` 含子目录与游标） |

### 10.2 待演进

1. `uploadIfAbsent` 的原子化（依赖平台条件写能力）。
2. 递归列举（当前不递归；可用 `OssPage.getDirectories()` 逐层下探）。
3. 批量操作（批量删除 / 批量下载）。
