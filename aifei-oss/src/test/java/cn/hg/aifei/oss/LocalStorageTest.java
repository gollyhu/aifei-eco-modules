/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.oss;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.aifei.aop.AopFactory;
import cn.aifei.aop.AopKit;
import cn.aifei.util.Prop;
import cn.hg.aifei.oss.aifei.OssConfigLoader;
import cn.hg.aifei.oss.aifei.OssPlugin;
import cn.hg.aifei.oss.internal.OssClients;
import cn.hg.aifei.oss.internal.OssConfig;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * 公共门面到本地存储平台的端到端契约测试（默认联网无关）。
 *
 * <p>以本地存储平台（{@code local}）+ 临时目录承载，走 {@link OssPlugin} 的真实装配链路，覆盖
 * {@code upload → exists → download → getFileInfo → delete → !exists} 完整链路，并断言上传后的对象路径与
 * 平台标识。定位全程只走 {@code FileInfo} 一族 API，<b>不</b>依赖 {@code FileRecorder}。</p>
 *
 * <p>为保持独立、幂等：每个用例前替换 AOP 工厂以隔离 {@code @Inject} 单例，用例结束后清空客户端登记表并
 * 由 {@link TemporaryFolder} 清理临时目录，不依赖执行顺序。</p>
 */
public class LocalStorageTest {

    /**
     * 承载本地存储根目录的临时目录。
     */
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    /**
     * 用于隔离 {@code @Inject} 单例的原始 AOP 工厂。
     */
    private AopFactory previousFactory;

    /**
     * 用例装配的插件，用于结束时停止。
     */
    private OssPlugin plugin;

    /**
     * 替换 AOP 工厂，隔离 {@code @Inject} 单例，避免用例间相互污染。
     */
    @Before
    public void isolateSingletons() {
        previousFactory = AopKit.get().getAopFactory();
        AopKit.get().setAopFactory(new AopFactory());
        OssClients.clear();
        plugin = null;
    }

    /**
     * 停止插件、清空客户端登记表并恢复 AOP 工厂；临时目录由规则自动清理。
     */
    @After
    public void restoreEnvironment() {
        if (plugin != null) {
            plugin.stop();
        }
        OssClients.clear();
        AopKit.get().setAopFactory(previousFactory);
    }

    /**
     * 本地存储的完整链路与失败语义。
     */
    @Test
    public void uploadDownloadDeleteExists() {
        String basePath = folder.getRoot().getAbsolutePath() + File.separator;
        Prop prop = prop(
                "oss.default-platform", "local",
                "oss.local.type", "local",
                "oss.local.base-path", basePath
        );
        // 未装配即调用：门面 fail-fast，不隐式 NPE
        assertThrows("尚未装配时 OssKit.defaultClient() 应抛 OssException",
                OssException.class, () -> OssKit.defaultClient());

        plugin = new OssPlugin(prop);
        plugin.start();

        // 装配结果：默认平台客户端与按平台取用为同一对象，登记表只含 local
        OssClient client = OssKit.defaultClient();
        assertSame("默认平台客户端应与按平台取用的一致", client, OssKit.use("local"));
        assertSame("默认平台客户端应与 @Inject 取到的一致", client, AopKit.get().getAopFactory()
                .get(OssClient.class));
        assertEquals("已注册平台集合", Collections.singleton("local"), OssKit.platforms());

        byte[] data = "aifei oss local round trip".getBytes(StandardCharsets.UTF_8);
        String path = "upload/2026/aifei.txt";

        // 上传
        OssFileInfo uploaded = client.upload(data, path, "text/plain");
        assertEquals("上传后对象路径应与写入路径一致", path, uploaded.getPath());
        assertEquals("上传后平台标识应为 local", "local", uploaded.getPlatform());
        assertEquals("上传后对象大小应等于字节数", data.length, uploaded.getSize());

        // 存在性
        assertTrue("上传后对象应存在", client.exists(path));

        // 下载（字节读取 + 流式写出）
        OssObject object = client.download(path);
        assertEquals("下载对象路径应与写入路径一致", path, object.getPath());
        assertEquals("下载对象大小应等于字节数", data.length, object.getContentLength());
        assertArrayEquals("下载内容应与上传内容一致", data, object.bytes());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        object.writeTo(out);
        assertArrayEquals("流式写出内容应与上传内容一致", data, out.toByteArray());

        // 元数据查询
        OssFileInfo info = client.getFileInfo(path);
        assertEquals("查询对象路径应与写入路径一致", path, info.getPath());
        assertEquals("查询对象大小应等于字节数", data.length, info.getSize());

        // 失败语义：不存在的对象下载抛异常、删除返回 false、非法路径抛异常、未知平台抛异常
        assertThrows(OssException.class, () -> client.download("upload/2026/missing.txt"));
        assertFalse("删除不存在的对象应返回 false", client.delete("upload/2026/missing.txt"));
        assertThrows(OssException.class, () -> client.exists("/bad.txt"));
        assertThrows(OssException.class, () -> client.upload(data, "/bad.txt", null));
        assertThrows(OssException.class, () -> OssKit.use("missing-platform"));
        assertThrows("copy 本期未接线，调用即抛 OssException",
                OssException.class, () -> client.copy(path, "upload/2026/copy.txt"));

        // 配置值对象脱敏：toString 不得输出 options（可能含 secret-key 等敏感项）
        OssConfig sensitive = new OssConfig("local", "local",
                Collections.singletonMap("secret-key", "top-secret-value"));
        assertFalse("OssConfig.toString() 不得输出敏感配置项",
                sensitive.toString().contains("top-secret-value"));
        assertTrue("OssConfig.toString() 应含平台标识与类型",
                sensitive.toString().contains("local"));

        // 删除
        assertTrue("删除存在的对象应返回 true", client.delete(path));
        assertFalse("删除后对象应不存在", client.exists(path));

        // 默认平台规则（D3）：多平台且未声明 oss.default-platform 必须失败
        Prop multiPlatform = prop("oss.a.type", "local", "oss.b.type", "local");
        assertThrows(OssException.class, () -> OssConfigLoader.defaultPlatform(
                multiPlatform, OssConfigLoader.load(multiPlatform)));
        assertThrows(OssException.class, () -> OssConfigLoader.load(prop()));
    }

    /**
     * 由键值对构造 {@link Prop}。
     *
     * @param keyValues 交替出现的键与值
     * @return 配置对象
     */
    private static Prop prop(String... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues must be key/value pairs");
        }
        Prop prop = new Prop();
        for (int i = 0; i < keyValues.length; i += 2) {
            prop.getProperties().setProperty(keyValues[i], keyValues[i + 1]);
        }
        return prop;
    }
}
