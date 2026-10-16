/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.oss;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import cn.aifei.aop.AopFactory;
import cn.aifei.aop.AopKit;
import cn.aifei.util.Prop;
import cn.hg.aifei.oss.aifei.OssPlugin;
import cn.hg.aifei.oss.internal.OssClients;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 云厂商 SDK 缺失时的装配期行为契约测试（<b>默认关闭</b>）。
 *
 * <p>固化设计 §7 的契约：<b>云 SDK 缺失在装配期即失败</b>——底层构建文件存储服务时会立即实例化该平台的存储客户端，
 * 因此 {@code OssPlugin.start()} 即抛 {@link OssException}（底层 {@code Error} 与运行时异常在适配层统一翻译），
 * <b>不</b>会泄漏 {@code NoClassDefFoundError}；失败后不留半装配状态。</p>
 *
 * <p>本用例<b>不需要</b>任何云厂商 SDK 与真实对象存储服务：故意声明一个 {@code minio} 类型平台但不引入
 * {@code io.minio:minio}，因此可在任何环境运行。</p>
 *
 * <p>开启方式（默认关闭，对齐 {@code aifei-cache} 的 {@code -Dredis.integration}）：</p>
 *
 * <pre>
 * mvn verify -Doss.integration=true
 * </pre>
 */
public class CloudSdkMissingTest {

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
     * 停止插件、清空客户端登记表并恢复 AOP 工厂。
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
     * 云 SDK 缺失时的失败语义：装配期失败、统一抛 {@link OssException}，且不留半装配状态。
     */
    @Test
    public void cloudSdkMissingFailsAtStartup() {
        assumeTrue("默认关闭：需 -Doss.integration=true 开启", Boolean.getBoolean("oss.integration"));

        Prop prop = prop(
                "oss.default-platform", "minio-1",
                "oss.minio-1.type", "minio",
                "oss.minio-1.end-point", "http://127.0.0.1:9000",
                "oss.minio-1.access-key", "minioadmin",
                "oss.minio-1.secret-key", "minioadmin",
                "oss.minio-1.bucket-name", "aifei-oss-test"
        );
        plugin = new OssPlugin(prop);

        assertThrows("云 SDK 缺失应在装配期抛 OssException", OssException.class, plugin::start);
        assertTrue("装配失败后不得留半装配状态", OssKit.platforms().isEmpty());
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
