# aifei-oss 测试文档

本文件面向测试维护者：测试分层、用例清单、运行方式与新增约定。契约与设计决策见 `docs/design.md`。

## 1. 测试分层

| 层 | 覆盖对象 | 是否联网 | 所在用例 |
| --- | --- | --- | --- |
| 单元 / 契约 | 公共门面到本地存储平台全链路（上传 / if-absent / 下载 / 存在性 / 元数据 / 删除 / 复制 / 列举 / 缺失对象） | 否 | `LocalStorageTest` |
| 配置契约 | 字段校验、平台推导、默认平台规则 | 否 | `OssConfigTest` |
| 架构门禁 | 源码文本的分层边界与公共面收敛 | 否 | `ArchitectureBoundaryTest` |
| 字节码约束 | 主 JAR 内全部 class 的 major version = 52 | 否 | `ClassFileVersionTest` |
| 装配契约（默认关闭） | 云厂商 SDK 缺失时装配期失败 | 否 | `CloudSdkMissingTest` |
| 集成（默认关闭） | 真实对象存储（MinIO）上传 / 下载 / 列举 / 复制 / 删除 | 是 | `MinioIntegrationTest` |

## 2. 用例清单

### 2.1 `ArchitectureBoundaryTest`（4 项门禁）

| 用例 | 断言 |
| --- | --- |
| `xfsOnlyInAdapterPackage` | `org.dromara.x.file.storage.*` 只出现在 `cn.hg.aifei.oss.internal` |
| `frameworkOnlyInBridgePackage` | `cn.aifei.*` 只出现在 `cn.hg.aifei.oss.aifei` |
| `noSqlOrUndertowReference` | 全模块无 `java.sql.*` / `io.undertow.*` |
| `publicSurfaceExact` | 根包公共类型精确 6 个 |

### 2.2 `ClassFileVersionTest`（1 项）

- `mainClassesAreJava8`：`target/classes` 下全部 `.class` 的 major version 必须为 52。

### 2.3 `LocalStorageTest`（5 项，默认运行，`local-plus` 平台）

- `uploadDownloadDeleteExists`：`upload → exists → download → getFileInfo → delete → !exists`。
- `copyAndListFiles`：复制成功，且 `listFiles` 非空（`local-plus` 支持列举）。
- `missingObjectSemantics`：下载不存在对象（读取时）抛 `OssException`；删除幂等；空目录列举为空。
- `uploadIfAbsentDoesNotOverwrite`：重复 `uploadIfAbsent` 不覆盖既有内容。
- `pagedListFilesReturnsPage`：`listFiles(path, maxFiles, marker)` 返回非空页且子目录列表非 `null`。

### 2.4 `OssConfigTest`（7 项，默认运行）

- 平台标识 / 类型空白被拒；无平台配置、多平台缺默认平台、默认平台指向未声明平台均抛 `OssException`。
- 单平台缺省即自身；配置项按 kebab-case 原样保留。

### 2.5 `CloudSdkMissingTest`（1 项，默认关闭）

- `cloudSdkMissingFailsAtStartup`：声明 `huawei-obs` 但不引入 OBS SDK，`OssPlugin.start()` 抛 `OssException` 且不留半装配状态。

### 2.6 `MinioIntegrationTest`（1 项，默认关闭）

- `uploadDownloadListCopyDelete`：真实 MinIO 的上传 / 下载 / 列举 / 复制 / 删除；开启后连不上必须失败。

## 3. 运行

**命令一律在模块根目录执行**（门禁依赖相对路径 `src/main/java`）：

```bash
cd aifei-oss && mvn clean verify          # 必须 0 failure / 0 error
mvn test -Dtest=ArchitectureBoundaryTest  # 单跑门禁
mvn test -Dtest=LocalStorageTest#uploadDownloadDeleteExists
```

开启集成 / 装配契约测试（默认关闭）：

```bash
mvn verify -Doss.integration=true
```

MinIO 连接参数可用系统属性覆盖：`oss.minio.endpoint` / `oss.minio.access-key` / `oss.minio.secret-key` / `oss.minio.bucket`，默认 `http://127.0.0.1:9000`、桶 `aifei-oss-test`（需预先创建）。

## 4. 新增约定

- 公共行为变更必须同步更新对应用例；已修复的缺陷必须固化为**回归测试**。
- 测试须**独立且幂等**：`LocalStorageTest` 使用临时目录并在结束时 `OssClients.clear()` 与清理临时目录，不得依赖执行顺序。
- 涉及真实外部服务（MinIO 等）的用例默认关闭；一旦开启，连不上必须使测试失败，不得静默跳过（命名对齐 `aifei-cache` 的 `redis.integration`）。
- 新增平台实现时，应补一组「上传 / 下载 / 删除」的集成用例，并保证不联网的本地存储用例仍可独立运行。
