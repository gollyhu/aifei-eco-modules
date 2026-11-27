/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.pay.PayException;
import cn.hg.aifei.pay.PayKit;
import cn.hg.aifei.pay.support.PayTestCrypto;
import cn.hg.aifei.pay.support.PayTestProps;
import java.util.Properties;
import org.junit.Test;

/**
 * 沙箱 / 生产环境切换：网关由配置显式管理，且与环境标签的 host 一致。
 *
 * <p><b>可机械验证的边界</b>：① {@code env} 必填；
 * ② {@code gateway-url} 的 host 与 {@code env} 一致；③ 不同 appId 的网关互不串扰。
 * 「凭据本身归属哪个环境」不在可验证范围——见 {@link #shouldAcceptSameCredentialForBothEnvironments()}。</p>
 */
public class PayEnvTest {

    /**
     * 被测应用编号。
     */
    private static final String APP = PayTestProps.APP_MAIN;

    /**
     * {@code env=sandbox} 且未显式配置网关 → 使用沙箱官方网关。
     */
    @Test
    public void shouldUseSandboxGatewayWhenEnvIsSandbox() {
        PayAppConfig app = PayConfig.parse(PayTestProps.keyMode(APP)).apps().get(APP);

        assertEquals("沙箱环境应使用沙箱网关", PayKit.GATEWAY_SANDBOX, app.gatewayUrl());
    }

    /**
     * {@code env=production} 且未显式配置网关 → 使用生产官方网关。
     */
    @Test
    public void shouldUseProductionGatewayWhenEnvIsProduction() {
        Properties properties = PayTestProps.with(PayTestProps.keyMode(APP),
                PayTestProps.key(APP, "env"), "production");

        PayAppConfig app = PayConfig.parse(properties).apps().get(APP);

        assertEquals("生产环境应使用生产网关", PayKit.GATEWAY_PRODUCTION, app.gatewayUrl());
    }

    /**
     * 生产环境下显式配置生产网关 → 校验通过并原样保留。
     */
    @Test
    public void shouldKeepExplicitOfficialGatewayForProduction() {
        Properties properties = PayTestProps.with(PayTestProps.keyMode(APP),
                PayTestProps.key(APP, "env"), "production",
                PayTestProps.key(APP, "gateway-url"), PayKit.GATEWAY_PRODUCTION);

        PayAppConfig app = PayConfig.parse(properties).apps().get(APP);

        assertEquals(PayKit.GATEWAY_PRODUCTION, app.gatewayUrl());
    }

    /**
     * 非官方 host 即使 scheme 正确也视为与环境不一致 → 启动失败。
     */
    @Test
    public void shouldFailWhenGatewayHostIsNotOfficial() {
        String arbitrary = "https" + "://" + "pay.example.com/gateway.do";
        Properties properties = PayTestProps.with(PayTestProps.keyMode(APP),
                PayTestProps.key(APP, "gateway-url"), arbitrary);

        PayException e = assertThrows(PayException.class, () -> PayConfig.parse(properties));

        assertTrue("消息应指出不一致：" + e.getMessage(), e.getMessage().contains("网关与环境不一致"));
        assertTrue("消息应含实际 host：" + e.getMessage(), e.getMessage().contains("pay.example.com"));
    }

    /**
     * 下单请求指向的是配置的网关（伪内核断言收到的应用配置）。
     */
    @Test
    public void shouldRoutePrecreateToConfiguredGateway() {
        PayConfig config = PayConfig.parse(PayTestProps.keyMode(APP));
        PayKernelStub stub = new PayKernelStub();
        DefaultPayClient client = new DefaultPayClient(config, stub);

        client.precreate(APP, "T2026010100001", 1L, "测试订单", PayTestProps.NOTIFY_URL);

        assertEquals("扫码下单应把沙箱网关交给内核",
                PayKit.GATEWAY_SANDBOX, stub.lastCall().app().gatewayUrl());
    }

    /**
     * 生产配置的下单请求指向生产网关。
     */
    @Test
    public void shouldRoutePagePayToProductionGateway() {
        Properties properties = PayTestProps.withCallbacks(PayTestProps.with(PayTestProps.keyMode(APP),
                PayTestProps.key(APP, "env"), "production"));
        PayConfig config = PayConfig.parse(properties);
        PayKernelStub stub = new PayKernelStub();
        DefaultPayClient client = new DefaultPayClient(config, stub);

        client.pagePay(APP, "T2026010100001", 100L, "测试订单", null, null);

        assertEquals("页面下单应把生产网关交给内核",
                PayKit.GATEWAY_PRODUCTION, stub.lastCall().app().gatewayUrl());
    }

    /**
     * 不同 appId 的网关互不串扰（一个沙箱、一个生产）。
     */
    @Test
    public void shouldNotShareGatewayBetweenApps() {
        Properties properties = PayTestProps.keyMode(APP);
        PayTestProps.with(properties,
                PayTestProps.key(PayTestProps.APP_SUB, "env"), "production",
                PayTestProps.key(PayTestProps.APP_SUB, "sign-mode"), "key",
                PayTestProps.key(PayTestProps.APP_SUB, "private-key"), PayTestCrypto.PRIVATE_KEY_NEW,
                PayTestProps.key(PayTestProps.APP_SUB, "verify.0.type"), "key",
                PayTestProps.key(PayTestProps.APP_SUB, "verify.0.value"), PayTestCrypto.PUBLIC_KEY_NEW,
                "pay.default-app-id", APP);

        PayConfig config = PayConfig.parse(properties);
        PayKernelStub stub = new PayKernelStub();
        DefaultPayClient client = new DefaultPayClient(config, stub);

        client.precreate(APP, "T2026010100001", 1L, "测试订单", PayTestProps.NOTIFY_URL);
        assertEquals("主应用应走沙箱网关", PayKit.GATEWAY_SANDBOX, stub.lastCall().app().gatewayUrl());

        client.precreate(PayTestProps.APP_SUB, "T2026010100002", 1L, "测试订单", PayTestProps.NOTIFY_URL);
        assertEquals("副应用应走生产网关", PayKit.GATEWAY_PRODUCTION, stub.lastCall().app().gatewayUrl());
        assertEquals("两次调用的应用配置不应相同", PayTestProps.APP_SUB, stub.lastCall().app().appId());
    }

    /**
     * 边界声明：模块**无法**判定凭据归属哪个环境——同一套自签凭据可同时配置到沙箱与生产应用，
     * 校验只覆盖 {@code env} 与网关 host。该项由配置评审与凭据来源管理兜底（README 声明）。
     */
    @Test
    public void shouldAcceptSameCredentialForBothEnvironments() {
        Properties properties = PayTestProps.keyMode(APP);
        PayTestProps.with(properties,
                PayTestProps.key(PayTestProps.APP_SUB, "env"), "production",
                PayTestProps.key(PayTestProps.APP_SUB, "sign-mode"), "key",
                PayTestProps.key(PayTestProps.APP_SUB, "private-key"), PayTestCrypto.PRIVATE_KEY_OLD,
                PayTestProps.key(PayTestProps.APP_SUB, "verify.0.type"), "key",
                PayTestProps.key(PayTestProps.APP_SUB, "verify.0.value"), PayTestCrypto.PUBLIC_KEY_OLD);

        PayConfig config = PayConfig.parse(properties);

        assertEquals("同一私钥可同时用于两个环境，模块不做归属判定",
                PayTestCrypto.PUBLIC_KEY_OLD,
                config.apps().get(PayTestProps.APP_SUB).verifyKeys().get(0).publicKeyText());
        assertFalse("两个应用的网关仍应各自独立",
                config.apps().get(APP).gatewayUrl().equalsIgnoreCase(
                        config.apps().get(PayTestProps.APP_SUB).gatewayUrl()));
    }

    /**
     * {@code env} 大小写不敏感（{@code SANDBOX} 归一化为 {@code sandbox}）。
     */
    @Test
    public void shouldNormalizeEnvCaseInsensitively() {
        Properties properties = PayTestProps.with(PayTestProps.keyMode(APP),
                PayTestProps.key(APP, "env"), "SANDBOX");

        PayAppConfig app = PayConfig.parse(properties).apps().get(APP);

        assertEquals("环境标签应归一化为小写", "sandbox", app.env());
        assertEquals(PayKit.GATEWAY_SANDBOX, app.gatewayUrl());
    }

    /**
     * 沙箱与生产两个官方网关的 host 必须是不同值（否则一致性校验失去意义）。
     */
    @Test
    public void shouldHaveDistinctHostsForSandboxAndProduction() {
        PayConfig sandbox = PayConfig.parse(PayTestProps.keyMode(APP));
        Properties productionProps = PayTestProps.with(PayTestProps.keyMode(APP),
                PayTestProps.key(APP, "env"), "production");
        PayConfig production = PayConfig.parse(productionProps);

        assertFalse("两个官方网关必须不同",
                sandbox.apps().get(APP).gatewayUrl()
                        .equalsIgnoreCase(production.apps().get(APP).gatewayUrl()));
    }
}
