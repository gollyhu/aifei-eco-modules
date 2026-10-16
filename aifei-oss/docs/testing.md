# aifei-oss 测试文档

本文件面向测试维护者：测试分层、用例清单、运行方式与新增约定。契约与设计决策见 `docs/design.md`。

## 1. 测试分层

| 层 | 覆盖对象 | 是否联网 | 所在用例 |
| --- | --- | --- | --- |
| 单元 / 契约 | 公共门面到本地存储平台的完整链路（上传 / 下载 / 存在性 / 元数据 / 同平台复制 / 删除） | 否 | `LocalStorageTest` |
| 架构门禁 | `src/main/java` 源码文本的分层边界与公共面收敛 | 否 | `ArchitectureBoundaryTest` |
| 字节码约束 | 主 JAR 内全部 class 的 major version = 52 | 否 | `ClassFileVersionTest` |
| 装配期契约（默认关闭） | 云 SDK 缺失时的装配期失败语义（**无需**真实服务，也**不引入**任何云 SDK） | 否 | `CloudSdkMissingTest` |
| 集成（默认关闭） | 真实对象存储（MinIO，S3 兼容） | 是 | **未落**（受「测试不引入任何云 SDK」约束阻塞，见 §4） |

## 2. 用例清单

### 2.1 `ArchitectureBoundaryTest`（4 项门禁）

| 用例 | 断言 |
| --- | --- |
| `xfsOnlyInAdapterPackage` | `org.dromara.x.file.storage.*` 只出现在 `cn.hg.aifei.oss.internal` |
| `frameworkOnlyInBridgePackage` | `cn.aifei.*` 只出现在 `cn.hg.aifei.oss.aifei` |
| `noSqlOrUndertowReference` | 全模块无 `java.sql.*` / `io.undertow.*` |
| `publicSurfaceExact` | 根包公共类型精确 5 个，且文件名集合精确相等 |

### 2.2 `ClassFileVersionTest`（1 项）

- `mainClassesAreJava8`：`target/classes` 下全部 `.class` 的 major version 必须为 52。

### 2.3 `LocalStorageTest`（1 项）

- `uploadDownloadDeleteExists`：以本地存储平台（临时目录）验证 `upload → exists → download → getFileInfo → copy → delete → !exists` 完整链路，并断言上传后的对象路径与平台标识；`copy` 断言同平台复制的目标存在、内容与源一致且源保留；全程**不得依赖 `FileRecorder`**（定位一律走 `FileInfo` 一族 API）。

### 2.4 `CloudSdkMissingTest`（1 项，默认关闭）

- `cloudSdkMissingFailsAtStartup`：故意声明一个 `minio` 类型平台但**不引入任何云厂商 SDK**，断言 `OssPlugin.start()` 抛 `OssException`（**不**泄漏 `NoClassDefFoundError`）且**不留半装配状态**。固化设计 §7「云 SDK 缺失在装配期即失败」这条**已实测**的契约（原文档的「运行期惰性」声明已被本用例推翻并订正）。**不需要**真实服务与任何云 SDK，任何环境均可运行。

## 3. 运行

**命令一律在模块根目录执行**（门禁依赖相对路径 `src/main/java`）：

```bash
cd aifei-oss && mvn clean verify          # 必须 0 failure / 0 error（默认跑全部非集成用例）
mvn test -Dtest=ArchitectureBoundaryTest  # 单跑门禁
mvn test -Dtest=LocalStorageTest#uploadDownloadDeleteExists
```

开启默认关闭的用例（**云 SDK 缺失装配期契约**；真实对象存储集成用例**未落**）：

```bash
mvn verify -Doss.integration=true
```

## 4. 新增约定

- 公共行为变更必须同步更新对应用例；已修复的缺陷必须固化为**回归测试**。
- 用例不得依赖 `FileRecorder`：`DefaultFileRecorder.getByUrl()` 恒抛异常，定位一律走 `FileInfo` 一族 API。
- 测试须**独立且幂等**：用例以临时目录承载，并在结束时 `OssClients.clear()` 与清理临时目录，不得依赖执行顺序。
- 默认关闭的用例以 `-Doss.integration=true` 开启（命名对齐 `aifei-cache` 的 `redis.integration`）；**一旦开启，失败必须使构建失败，不得静默跳过**（`CloudSdkMissingTest` 即按此约定：断言装配期失败，实际行为若变化则构建失败）。
- **本模块测试不引入任何云厂商 SDK**（`AGENTS.md` 硬约束），故真实对象存储（MinIO 等）的集成用例**无法在本模块内落地**——需先放宽该约束（引入 `io.minio:minio` 的 `test` 依赖）方可补。已定档透传的连接参数：`-Doss.endpoint`（默认 `http://127.0.0.1:9000`）/ `-Doss.access-key`、`-Doss.secret-key`（默认 `minioadmin`）/ `-Doss.bucket`（默认 `aifei-oss-test`），取值用短名 `endpoint`，**刻意区别于**平台配置键 `end-point`。
- 新增平台实现时，应补一组「上传 / 下载 / 删除」的集成用例，并保证不联网的本地存储用例仍可独立运行。
