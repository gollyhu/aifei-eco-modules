/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.pay.PayException;
import cn.hg.aifei.pay.PayKit;
import cn.hg.aifei.pay.support.PayTestCrypto;
import cn.hg.aifei.pay.support.PayTestProps;
import com.alipay.v3.ApiClient;
import java.util.Properties;
import org.junit.Before;
import org.junit.Test;

/**
 * 多应用（AC-16）：显式 {@code appId} 首参 + {@code pay.default-app-id} 回退，不同应用的配置与内核实例互不串扰。
 */
public class PayMultiAppTest {

    /**
     * 主应用编号（沙箱）。
     */
    private static final String APP_A = PayTestProps.APP_MAIN;

    /**
     * 副应用编号（生产）。
     */
    private static final String APP_B = PayTestProps.APP_SUB;

    /**
     * 内核替身。
     */
    private PayKernelStub stub;

    /**
     * 双应用配置。
     */
    private PayConfig config;

    /**
     * 装配双应用配置（A 沙箱 / B 生产），默认应用为 A。
     */
    @Before
    public void setUp() {
        Properties properties = PayTestProps.keyMode(APP_A);
        PayTestProps.with(properties,
                PayTestProps.key(APP_B, "env"), "production",
                PayTestProps.key(APP_B, "sign-mode"), "key",
                PayTestProps.key(APP_B, "private-key"), PayTestCrypto.PRIVATE_KEY_NEW,
                PayTestProps.key(APP_B, "verify.0.type"), "key",
                PayTestProps.key(APP_B, "verify.0.value"), PayTestCrypto.PUBLIC_KEY_NEW,
                "pay.default-app-id", APP_A);
        config = PayConfig.parse(properties);
        stub = new PayKernelStub();
    }

    /**
     * AC-16：两个 appId 各自使用对应配置，结果互不串扰。
     */
    @Test
    public void shouldResolveEachApplicationIndependently() {
        DefaultPayClient client = new DefaultPayClient(config, stub);

        client.precreate(APP_A, "T-A-0001", 1L, "订单A", PayTestProps.NOTIFY_URL);
        PayKernelStub.Call callA = stub.lastCall();
        client.precreate(APP_B, "T-B-0001", 2L, "订单B", PayTestProps.NOTIFY_URL);
        PayKernelStub.Call callB = stub.lastCall();

        assertEquals("A 的应用编号", APP_A, callA.app().appId());
        assertEquals("A 走沙箱网关", PayKit.GATEWAY_SANDBOX, callA.app().gatewayUrl());
        assertEquals("B 的应用编号", APP_B, callB.app().appId());
        assertEquals("B 走生产网关", PayKit.GATEWAY_PRODUCTION, callB.app().gatewayUrl());
        assertNotSame("两次调用应拿到不同的应用配置快照", callA.app(), callB.app());
    }

    /**
     * AC-16：{@code appId} 为 {@code null} / 空白时回退 {@code pay.default-app-id}。
     */
    @Test
    public void shouldFallbackToDefaultAppIdWhenAppIdBlank() {
        DefaultPayClient client = new DefaultPayClient(config, stub);

        client.query(null, "T-A-0001");
        assertEquals("null 应回退默认应用", APP_A, stub.lastCall().app().appId());

        client.query("", "T-A-0002");
        assertEquals("空串应回退默认应用", APP_A, stub.lastCall().app().appId());

        client.query("   ", "T-A-0003");
        assertEquals("空白应回退默认应用", APP_A, stub.lastCall().app().appId());
    }

    /**
     * AC-16：显式指定的 appId 优先于默认应用。
     */
    @Test
    public void shouldPreferExplicitAppIdOverDefault() {
        DefaultPayClient client = new DefaultPayClient(config, stub);

        client.query(APP_B, "T-B-0001");

        assertEquals("显式 appId 应优先", APP_B, stub.lastCall().app().appId());
    }

    /**
     * AC-16：未配置的 appId → 抛 {@link PayException}，消息含该 appId。
     */
    @Test
    public void shouldThrowWhenAppIdNotConfigured() {
        DefaultPayClient client = new DefaultPayClient(config, stub);

        PayException e = assertThrows(PayException.class,
                () -> client.query("2021999999999999", "T-0001"));

        assertTrue("消息应含 appId：" + e.getMessage(), e.getMessage().contains("2021999999999999"));
        assertEquals("未知应用不应触达内核", 0, stub.callCount());
    }

    /**
     * AC-16：既未传 appId 又未配置默认应用 → 抛 {@link PayException}，消息含默认应用键名。
     */
    @Test
    public void shouldThrowWhenNoDefaultAppIdAndAppIdBlank() {
        DefaultPayClient client = new DefaultPayClient(PayConfig.parse(PayTestProps.keyMode(APP_A)), stub);

        PayException e = assertThrows(PayException.class, () -> client.query(null, "T-0001"));

        assertTrue("消息应含默认应用键名：" + e.getMessage(),
                e.getMessage().contains(PayConfig.KEY_DEFAULT_APP_ID));
    }

    /**
     * AC-16：连续交替调用不同应用不会残留上一个应用的配置。
     */
    @Test
    public void shouldNotLeakApplicationAcrossSequentialCalls() {
        DefaultPayClient client = new DefaultPayClient(config, stub);

        client.query(APP_A, "T-A-0001");
        client.query(APP_B, "T-B-0001");
        client.query(APP_A, "T-A-0002");
        client.query(APP_B, "T-B-0002");

        assertEquals(4, stub.callCount());
        assertEquals(APP_A, stub.calls().get(0).app().appId());
        assertEquals(APP_B, stub.calls().get(1).app().appId());
        assertEquals(APP_A, stub.calls().get(2).app().appId());
        assertEquals(APP_B, stub.calls().get(3).app().appId());
        assertEquals("每次调用的订单号应对应各自应用",
                "T-B-0002", stub.calls().get(3).param("out_trade_no"));
    }

    /**
     * AC-16 / AC-15：每个 appId 拥有独立的 SDK 客户端实例，且网关与超时按各自配置注入并缓存复用。
     */
    @Test
    public void shouldBuildIsolatedSdkClientPerApplication() {
        PayClients clients = new PayClients(config);

        ApiClient clientA = clients.apiClient(APP_A);
        ApiClient clientB = clients.apiClient(APP_B);

        assertNotSame("不同应用必须使用不同客户端实例", clientA, clientB);
        assertSame("同一应用应复用缓存实例", clientA, clients.apiClient(APP_A));
        assertEquals("A 的网关", PayKit.GATEWAY_SANDBOX, clientA.getBasePath());
        assertEquals("B 的网关", PayKit.GATEWAY_PRODUCTION, clientB.getBasePath());
        assertEquals("A 的连接超时", 5000, clientA.getConnectTimeout());
        assertEquals("A 的读取超时", 15000, clientA.getReadTimeout());
    }

    /**
     * AC-16：页面下单接口实例同样按 appId 隔离（不得共用进程级默认客户端）。
     */
    @Test
    public void shouldBuildIsolatedPageApiPerApplication() {
        PayClients clients = new PayClients(config);

        assertNotSame("不同应用的页面接口实例必须不同",
                clients.pageApi(APP_A), clients.pageApi(APP_B));
        assertSame("同一应用应复用页面接口实例", clients.pageApi(APP_A), clients.pageApi(APP_A));
    }
}
