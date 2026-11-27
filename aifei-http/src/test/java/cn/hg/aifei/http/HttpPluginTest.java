/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.aifei.aop.Aop;
import cn.aifei.aop.AopFactory;
import cn.aifei.aop.AopKit;
import cn.aifei.util.Prop;
import cn.hg.aifei.http.aifei.HttpPlugin;
import cn.hg.aifei.http.internal.HttpHolder;
import java.io.IOException;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 装配生命周期：容器注册、门面写入、幂等、失败不留半装配、JVM 全局属性不变。
 */
public class HttpPluginTest {

    /**
     * 未监听的回环端口：连接拒绝即时返回，证明请求确实走到了传输层。
     */
    private static final String CLOSED_LOOPBACK_URL = "http://127.0.0.1:1/x";

    /**
     * 被替换前的 AOP 工厂，用例结束后恢复。
     */
    private AopFactory previousFactory;

    /**
     * 当前用例的插件实例。
     */
    private HttpPlugin plugin;

    /**
     * 替换 AOP 工厂以隔离单例，并清空门面实例。
     */
    @Before
    public void replaceAopFactory() {
        previousFactory = AopKit.get().getAopFactory();
        AopKit.get().setAopFactory(new AopFactory());
        HttpHolder.clear();
        plugin = null;
    }

    /**
     * 恢复环境：停止插件、清空门面并还原 AOP 工厂。
     */
    @After
    public void restoreEnvironment() {
        if (plugin != null) {
            plugin.stop();
        }
        HttpHolder.clear();
        AopKit.get().setAopFactory(previousFactory);
    }

    /**
     * 构造参数为 {@code null} 时失败。
     */
    @Test
    public void shouldRejectNullProp() {
        assertThrows(IllegalArgumentException.class, () -> new HttpPlugin(null));
    }

    /**
     * 拦截器为 {@code null} 属调用方写错。
     */
    @Test
    public void shouldRejectNullInterceptor() {
        plugin = new HttpPlugin(prop());

        assertThrows(IllegalArgumentException.class,
                () -> plugin.addInterceptor(null));
    }

    /**
     * 启动后不再接受拦截器注册（链顺序是启动期的快照）。
     */
    @Test
    public void shouldRejectInterceptorAfterStart() {
        plugin = new HttpPlugin(prop());
        plugin.start();

        assertThrows(HttpException.class, () -> plugin.addInterceptor(attempt -> { }));
    }

    /**
     * 启动后 {@code @Inject} 单例非空且与门面取到的是同一个对象，请求确实走到传输层。
     */
    @Test
    public void shouldRegisterClientSingleton() {
        plugin = new HttpPlugin(prop());
        plugin.addInterceptor(attempt -> { });
        plugin.start();

        HttpClient injected = Aop.get(HttpClient.class);

        assertNotNull("@Inject HttpClient 不应为空", injected);
        assertSame(injected, HttpKit.client());

        HttpException e = assertThrows(HttpException.class, () -> HttpKit.get(CLOSED_LOOPBACK_URL));
        assertEquals("请求失败: GET " + CLOSED_LOOPBACK_URL, e.getMessage());
        assertEquals("应能给出尝试次数", 1, e.attempts());
        assertTrue("cause 应为底层 IOException", e.getCause() instanceof IOException);
    }

    /**
     * 配置非法 → 启动失败，且不留半装配（门面仍不可用）。
     */
    @Test
    public void shouldFailFastOnInvalidConfig() {
        plugin = new HttpPlugin(prop("http.connect-timeout", "0"));

        HttpException e = assertThrows(HttpException.class, plugin::start);

        assertTrue("消息应含键名：" + e.getMessage(), e.getMessage().contains("http.connect-timeout"));
        assertThrows(HttpException.class, () -> HttpKit.get(CLOSED_LOOPBACK_URL));
    }

    /**
     * 重复 {@code start()} / {@code stop()} 幂等无异常；停止后门面调用失败。
     */
    @Test
    public void shouldBeIdempotent() {
        plugin = new HttpPlugin(prop());
        plugin.start();
        plugin.start();

        assertNotNull(HttpKit.client());

        plugin.stop();
        plugin.stop();

        HttpException e = assertThrows(HttpException.class, HttpKit::client);
        assertEquals("HTTP 客户端未装配或已停止", e.getMessage());
    }

    /**
     * 停止后再次启动复用首次装配的客户端，不重复向容器注册（否则容器会抛单例冲突）。
     */
    @Test
    public void shouldRestartAfterStop() {
        plugin = new HttpPlugin(prop());
        plugin.start();
        HttpClient first = HttpKit.client();
        plugin.stop();

        plugin.start();

        assertSame("再次启动应复用首次装配的实例", first, HttpKit.client());
        assertSame(first, Aop.get(HttpClient.class));
    }

    /**
     * 用例前后 JVM 全局 HTTP 属性取值不变（模块只读不写）。
     */
    @Test
    public void shouldNotWriteJvmGlobalProperties() {
        String maxConnectionsKey = "http.maxConnections";
        String keepAliveKey = "http.keepAlive";
        String beforeMaxConnections = System.getProperty(maxConnectionsKey);
        String beforeKeepAlive = System.getProperty(keepAliveKey);

        plugin = new HttpPlugin(prop());
        plugin.start();
        assertThrows(HttpException.class, () -> HttpKit.get(CLOSED_LOOPBACK_URL));
        plugin.stop();

        assertEquals("http.maxConnections 不得被改写", beforeMaxConnections, System.getProperty(maxConnectionsKey));
        assertEquals("http.keepAlive 不得被改写", beforeKeepAlive, System.getProperty(keepAliveKey));
    }

    /**
     * 构造配置源；交替出现的键与值。
     *
     * @param keyValues 交替出现的键与值
     * @return 配置源
     */
    private static Prop prop(String... keyValues) {
        Prop prop = new Prop();
        Properties properties = prop.getProperties();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            properties.setProperty(keyValues[i], keyValues[i + 1]);
        }
        return prop;
    }
}
