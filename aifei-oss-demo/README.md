# aifei-oss-demo

`aifei-oss` 的示例工程：演示多平台对象存储接入（默认使用**本地存储**，无需任何云厂商 SDK）。

## 运行

先确保 `aifei-oss` 已装入本地仓库：

```bat
cd ..\aifei-oss
mvn clean install
```

再启动 demo（HTTP 端口 **18092**）：

```bat
cd ..\aifei-oss-demo
start-demo.bat
```

停止：

```bat
stop-demo.bat
```

## 演示端点（均为 GET，HTTP 恒 200，业务状态由 `code` 承载）

| 端点 | 说明 |
| --- | --- |
| `/oss/demo/state` | 已注册平台列表 |
| `/oss/demo/upload?path=&text=` | 上传文本 |
| `/oss/demo/upload-if-absent?path=&text=` | 仅当对象不存在时上传（不覆盖） |
| `/oss/demo/download?path=` | 下载文本 |
| `/oss/demo/exists?path=` | 判断存在 |
| `/oss/demo/list?path=` | 列举目录（`local-plus` 支持列举） |
| `/oss/demo/page?path=&maxFiles=&marker=` | 分页列举（含子目录、下一页游标与截断标记） |
| `/oss/demo/copy?src=&dest=` | 同平台复制（服务端优先，不支持时降级） |
| `/oss/demo/delete?path=` | 删除 |
| `/oss/demo/url?path=&ttl=` | 生成限时访问地址（平台不支持时返回失败） |

快速验证：

```bash
curl "http://localhost:18092/oss/demo/state"
curl "http://localhost:18092/oss/demo/upload?path=demo/a.txt&text=hello"
curl "http://localhost:18092/oss/demo/download?path=demo/a.txt"
curl "http://localhost:18092/oss/demo/list?path=demo"
curl "http://localhost:18092/oss/demo/copy?src=demo/a.txt&dest=demo/b.txt"
curl "http://localhost:18092/oss/demo/delete?path=demo/b.txt"
```

## 配置

`src/main/resources/app-config.txt`：

```properties
oss.default-platform = local-1
oss.local-1.type = local-plus
oss.local-1.base-path = data/oss-demo/
oss.local-1.domain = http://localhost:18092/files/
```

> `local-plus` 会把 `base-path` 拼进返回的访问 `url`（示例中 `url` 形如
> `http://localhost:18092/files/data/oss-demo/demo/a.txt`）。本 demo 未提供静态文件服务，
> `url` 仅作字段演示；如需可访问，请让 `domain` 指向 `base-path` 对应的 Web 根。

切换为云平台时，只需增加 `oss.<name>.type` 与对应配置项，并在消费方工程引入该平台的云厂商 SDK。
