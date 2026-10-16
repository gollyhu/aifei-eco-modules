# aifei-oss

为 [aifei](https://aifei.cn) 应用补齐**多平台对象 / 文件存储**能力的接入 SDK，基于成熟的 [X File Storage](https://x-file-storage.dromara.org) 二次封装。

一行配置即可把文件存到本地磁盘、FTP / SFTP，或阿里云 OSS、腾讯云 COS、华为云 OBS、MinIO、AWS S3 等 30+ 存储平台；业务代码只依赖统一接口，换存储平台不改代码。

## 特性

- **多平台**：本地 / FTP / SFTP / 阿里云 OSS / 腾讯云 COS / 华为云 OBS / 七牛 / MinIO / AWS S3 / Azure / Google Cloud 等（由 X File Storage 提供）。
- **统一接口**：`OssClient` 提供上传 / 下载 / 删除 / 存在性 / 元数据 / 签名 URL / 复制。
- **多平台共存**：`@Inject OssClient` 用默认平台，`OssKit.use("name")` 按平台取用。
- **实现无关**：公共 API 不暴露 X File Storage 与云厂商 SDK 类型。
- **Java 8**：编译目标与产物均为 Java 8。

## 依赖

```xml
<dependency>
    <groupId>io.github.gollyhu</groupId>
    <artifactId>aifei-oss</artifactId>
    <version>1.0.0</version>
</dependency>
```

- **`x-file-storage-core` 由本模块传递引入**（`compile` 依赖），消费方无需再声明可正常使用。
- **各云厂商 SDK 需消费方按需自带**（例如阿里云 `com.aliyun.oss:aliyun-sdk-oss`、AWS `com.amazonaws:aws-java-sdk-s3`、MinIO `io.minio:minio`）。仅使用本地存储时无需引入任何云 SDK。
- aifei 框架（`cn.aifei:aifei-all`）为 `provided`，由应用自带。

> **Java 8 说明**：X File Storage 官方文档中的「JDK 11+」指其**自身构建环境**要求；其发布 jar 的字节码为 Java 8，可被 Java 8 应用直接依赖运行。aifei-oss 与消费方均无需升级 JDK。

## 快速开始

### 1. 配置 `app-config.txt`

```properties
# 默认平台（多平台时必填）
oss.default-platform = local-1

# 本地存储（无需任何云 SDK，适合开发与测试）
oss.local-1.type = local
oss.local-1.base-path = ./data/files

# 阿里云 OSS 示例（需自带 aliyun-sdk-oss）
oss.oss-1.type = aliyun-oss
oss.oss-1.access-key = your-access-key
oss.oss-1.secret-key = your-secret-key
oss.oss-1.end-point = oss-cn-hangzhou.aliyuncs.com
oss.oss-1.bucket-name = your-bucket
oss.oss-1.domain = https://your-bucket.oss-cn-hangzhou.aliyuncs.com/
```

### 2. 装配插件

```java
public class DemoAppConfig extends AppConfig {
    @Override
    public void config(Plugins plugins) {
        Prop prop = PropKit.use("app-config.txt");
        plugins.add(new OssPlugin(prop)); // 读取 oss.* 并注册 OssClient / OssKit
    }
}
```

### 3. 使用

```java
public class FileService {
    @Inject OssClient oss; // 默认平台

    public String save(InputStream in, String name) {
        OssFileInfo info = oss.upload(in, "upload/" + name, "image/png");
        return info.getUrl();
    }

    public void remove(String path) {
        oss.delete(path);
    }

    // 多平台：按平台标识取用
    public void backup(InputStream in, String path) {
        OssKit.use("oss-1").upload(in, path, "application/octet-stream");
    }
}
```

## 配置项

| 键 | 必填 | 说明 |
| --- | --- | --- |
| `oss.default-platform` | 多平台时必填 | 默认平台标识 |
| `oss.<name>.type` | 是 | 平台类型（`local` / `aliyun-oss` / `tencent-cos` / `minio` / `s3` / `huawei-obs` 等） |
| `oss.<name>.access-key` | 视平台 | 访问密钥 |
| `oss.<name>.secret-key` | 视平台 | 访问密钥 |
| `oss.<name>.end-point` | 视平台 | 服务端点 |
| `oss.<name>.bucket-name` | 视平台 | 存储桶 |
| `oss.<name>.domain` | 否 | 自定义访问域名 |
| `oss.<name>.<平台特有键>` | 否 | 透传给对应平台配置 |

各平台可用的完整配置键以 [X File Storage 文档](https://x-file-storage.dromara.org) 为准。

## 常见问题

- **启动报错「未装配 / 未知平台」**：确认已 `plugins.add(new OssPlugin(prop))` 且 `oss.default-platform` 指向已声明的平台。
- **运行报 `NoClassDefFoundError`（某云 SDK）**：该平台的云厂商 SDK 未引入，请按需自带。
- **`generatePresignedUrl` 抛异常**：当前平台不支持签名 URL。

## 文档

- `docs/design.md`：设计契约与决策（设计事实来源）。
- `AGENTS.md`：开发约束与验证命令。
- `docs/testing.md`：测试策略与用例清单（实现阶段补）。
