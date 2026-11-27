/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.pay.PayException;
import cn.hg.aifei.pay.PayKit;
import cn.hg.aifei.pay.support.PayTestCrypto;
import cn.hg.aifei.pay.support.PayTestProps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import org.junit.After;
import org.junit.Test;

/**
 * 配置解析与校验：九条启动期校验逐条落用例，外加全局键的取值与默认值。
 *
 * <p>判定口径：任一校验失败即抛 {@link PayException}，消息含**键名**且凭据值被脱敏为 {@code ***}；
 * 失败不留半装配态（由 {@code PayPluginTest} 从装配侧再断言一次）。</p>
 */
public class PayConfigTest {

    /**
     * 被测应用编号。
     */
    private static final String APP = PayTestProps.APP_MAIN;

    /**
     * 一个足够显眼、便于断言「未出现在消息里」的私钥占位文本。
     */
    private static final String SECRET_PLACEHOLDER = "TOPSECRET-PRIVATE-KEY-CONTENT-0123456789";

    /**
     * 清理临时文件。
     *
     * @throws IOException 删除失败
     */
    @After
    public void cleanup() throws IOException {
        if (tempFile != null) {
            Files.deleteIfExists(tempFile);
            tempFile = null;
        }
    }

    /**
     * 用例创建的临时凭据文件。
     */
    private Path tempFile;

    /**
     * 校验 1：没有配置任何应用 → 启动失败并指出键名前缀。
     */
    @Test
    public void shouldFailWhenNoApplicationConfigured() {
        PayException e = assertThrows(PayException.class,
                () -> PayConfig.parse(PayTestProps.fresh()));

        assertTrue("消息应含键名 pay.apps.：" + e.getMessage(), e.getMessage().contains("pay.apps."));
    }

    /**
     * 校验 1：应用编号段为空（{@code pay.apps..env}）→ 启动失败。
     */
    @Test
    public void shouldFailWhenAppIdSegmentIsEmpty() {
        Properties properties = PayTestProps.with(PayTestProps.fresh(),
                "pay.apps..env", "sandbox");

        PayException e = assertThrows(PayException.class, () -> PayConfig.parse(properties));

        assertTrue("消息应指出应用编号不能为空：" + e.getMessage(), e.getMessage().contains("应用编号"));
    }

    /**
     * 校验 1：应用级键缺少子键（{@code pay.apps.<appId>} 后无 {@code .}）→ 启动失败。
     */
    @Test
    public void shouldFailWhenApplicationKeyHasNoSubKey() {
        Properties properties = PayTestProps.with(PayTestProps.fresh(), "pay.apps." + APP, "sandbox");

        PayException e = assertThrows(PayException.class, () -> PayConfig.parse(properties));

        assertTrue("消息应提示缺少子键：" + e.getMessage(), e.getMessage().contains("子键"));
    }

    /**
     * 校验 2：{@code env} 未配置 → 启动失败，消息含完整键名。
     */
    @Test
    public void shouldFailWhenEnvMissing() {
        Properties properties = PayTestProps.without(PayTestProps.keyMode(APP),
                PayTestProps.key(APP, "env"));

        PayException e = assertThrows(PayException.class, () -> PayConfig.parse(properties));

        assertTrue("消息应含键名：" + e.getMessage(),
                e.getMessage().contains(PayTestProps.key(APP, "env")));
    }

    /**
     * 校验 2：{@code env} 取值非法 → 启动失败，消息含键名与合法值。
     */
    @Test
    public void shouldFailWhenEnvValueInvalid() {
        Properties properties = PayTestProps.with(PayTestProps.keyMode(APP),
                PayTestProps.key(APP, "env"), "staging");

        PayException e = assertThrows(PayException.class, () -> PayConfig.parse(properties));

        assertTrue("消息应含键名：" + e.getMessage(),
                e.getMessage().contains(PayTestProps.key(APP, "env")));
        assertTrue("消息应给出合法值：" + e.getMessage(), e.getMessage().contains("sandbox"));
        assertTrue("消息应给出合法值：" + e.getMessage(), e.getMessage().contains("production"));
    }

    /**
     * 校验 3：{@code env=sandbox} 却显式配置生产网关 → 启动失败，消息同时给出环境与期望 host。
     */
    @Test
    public void shouldFailWhenGatewayHostMismatchesEnv() {
        Properties properties = PayTestProps.with(PayTestProps.keyMode(APP),
                PayTestProps.key(APP, "gateway-url"), PayKit.GATEWAY_PRODUCTION);

        PayException e = assertThrows(PayException.class, () -> PayConfig.parse(properties));

        assertTrue("消息应指出网关与环境不一致：" + e.getMessage(), e.getMessage().contains("网关与环境不一致"));
        assertTrue("消息应含 env：" + e.getMessage(), e.getMessage().contains("env=sandbox"));
        assertTrue("消息应含期望 host：" + e.getMessage(), e.getMessage().contains("openapi-sandbox.dl.alipaydev.com"));
        assertTrue("消息应含实际 host：" + e.getMessage(), e.getMessage().contains("openapi.alipay.com"));
    }

    /**
     * 校验 3：网关 scheme 非 https → 启动失败。
     */
    @Test
    public void shouldFailWhenGatewaySchemeIsNotHttps() {
        String insecure = "http" + "://" + "openapi-sandbox.dl.alipaydev.com/gateway.do";
        Properties properties = PayTestProps.with(PayTestProps.keyMode(APP),
                PayTestProps.key(APP, "gateway-url"), insecure);

        PayException e = assertThrows(PayException.class, () -> PayConfig.parse(properties));

        assertTrue("消息应含键名：" + e.getMessage(),
                e.getMessage().contains(PayTestProps.key(APP, "gateway-url")));
        assertTrue("消息应指出 scheme 要求：" + e.getMessage(), e.getMessage().contains("scheme"));
    }

    /**
     * 校验 3：官方网关 host 大小写不敏感（{@code HTTPS://OPENAPI-...} 仍视为与 {@code sandbox} 一致）。
     */
    @Test
    public void shouldAcceptOfficialGatewayIgnoringHostCase() {
        String upperCase = PayKit.GATEWAY_SANDBOX.toUpperCase(Locale.ROOT);
        Properties properties = PayTestProps.with(PayTestProps.keyMode(APP),
                PayTestProps.key(APP, "gateway-url"), upperCase);

        PayConfig config = PayConfig.parse(properties);

        assertEquals("显式配置的官方网关应原样保留", upperCase, config.apps().get(APP).gatewayUrl());
    }

    /**
     * 校验 4：证书模式缺 {@code app-cert} → 启动失败。
     */
    @Test
    public void shouldFailWhenCertModeLacksAppCert() {
        Properties properties = PayTestProps.without(PayTestProps.certMode(APP),
                PayTestProps.key(APP, "app-cert"));

        PayException e = assertThrows(PayException.class, () -> PayConfig.parse(properties));

        assertTrue("消息应含 app-cert 键名：" + e.getMessage(),
                e.getMessage().contains(PayTestProps.key(APP, "app-cert")));
    }

    /**
     * 校验 4：证书模式缺 {@code root-cert} → 启动失败。
     */
    @Test
    public void shouldFailWhenCertModeLacksRootCert() {
        Properties properties = PayTestProps.without(PayTestProps.certMode(APP),
                PayTestProps.key(APP, "root-cert"));

        PayException e = assertThrows(PayException.class, () -> PayConfig.parse(properties));

        assertTrue("消息应含 root-cert 键名：" + e.getMessage(),
                e.getMessage().contains(PayTestProps.key(APP, "root-cert")));
    }

    /**
     * 校验 4（反向）：普通公钥模式不需要 {@code app-cert} / {@code root-cert}。
     */
    @Test
    public void shouldAcceptKeyModeWithoutCertificates() {
        PayAppConfig app = PayConfig.parse(PayTestProps.keyMode(APP)).apps().get(APP);

        assertFalse("普通公钥模式不应被判定为证书模式", app.isCertMode());
        assertNull("普通公钥模式下应用证书应为 null", app.appCert());
        assertNull("普通公钥模式下根证书应为 null", app.rootCert());
    }

    /**
     * 校验 5：凭据序号出现空洞（0、2）→ 启动失败并指出缺失序号。
     */
    @Test
    public void shouldFailWhenVerifyIndexHasGap() {
        Properties properties = PayTestProps.with(PayTestProps.keyMode(APP),
                PayTestProps.key(APP, "verify.2.type"), "key",
                PayTestProps.key(APP, "verify.2.value"), PayTestCrypto.PUBLIC_KEY_NEW);

        PayException e = assertThrows(PayException.class, () -> PayConfig.parse(properties));

        assertTrue("消息应指出缺少序号 1：" + e.getMessage(), e.getMessage().contains("缺少序号 1"));
    }

    /**
     * 校验 6：{@code verify.<n>.type} 存在但 {@code .value} 缺失 → 启动失败。
     */
    @Test
    public void shouldFailWhenVerifyTypeHasNoValue() {
        Properties properties = PayTestProps.without(PayTestProps.keyMode(APP),
                PayTestProps.key(APP, "verify.0.value"));

        PayException e = assertThrows(PayException.class, () -> PayConfig.parse(properties));

        assertTrue("消息应含 value 键名：" + e.getMessage(),
                e.getMessage().contains(PayTestProps.key(APP, "verify.0.value")));
    }

    /**
     * 校验 6：{@code verify.<n>.value} 存在但 {@code .type} 缺失 → 启动失败。
     */
    @Test
    public void shouldFailWhenVerifyValueHasNoType() {
        Properties properties = PayTestProps.without(PayTestProps.keyMode(APP),
                PayTestProps.key(APP, "verify.0.type"));

        PayException e = assertThrows(PayException.class, () -> PayConfig.parse(properties));

        assertTrue("消息应含 type 键名：" + e.getMessage(),
                e.getMessage().contains(PayTestProps.key(APP, "verify.0.type")));
    }

    /**
     * 校验 6：凭据类型取值非法 → 启动失败。
     */
    @Test
    public void shouldFailWhenVerifyTypeUnknown() {
        Properties properties = PayTestProps.with(PayTestProps.keyMode(APP),
                PayTestProps.key(APP, "verify.0.type"), "public-key");

        PayException e = assertThrows(PayException.class, () -> PayConfig.parse(properties));

        assertTrue("消息应含 type 键名：" + e.getMessage(),
                e.getMessage().contains(PayTestProps.key(APP, "verify.0.type")));
        assertTrue("消息应给出合法值：" + e.getMessage(), e.getMessage().contains("cert"));
    }

    /**
     * 校验 7：显式配置的证书序列号不是 32 位十六进制 → 启动失败。
     */
    @Test
    public void shouldFailWhenDeclaredCertSnIsNotHex() {
        Properties properties = PayTestProps.with(PayTestProps.certMode(APP),
                PayTestProps.key(APP, "verify.0.cert-sn"), "not-a-cert-sn");

        PayException e = assertThrows(PayException.class, () -> PayConfig.parse(properties));

        assertTrue("消息应含 cert-sn 键名：" + e.getMessage(),
                e.getMessage().contains(PayTestProps.key(APP, "verify.0.cert-sn")));
        assertTrue("消息应指出 32 位 hex 要求：" + e.getMessage(), e.getMessage().contains("32 位 hex"));
    }

    /**
     * 校验 7：显式配置的证书序列号与证书实际序列号不一致 → 启动失败（防止配错证书）。
     */
    @Test
    public void shouldFailWhenDeclaredCertSnDiffersFromCertificate() {
        Properties properties = PayTestProps.with(PayTestProps.certMode(APP),
                PayTestProps.key(APP, "verify.0.cert-sn"), PayTestCrypto.UNKNOWN_CERT_SN);

        PayException e = assertThrows(PayException.class, () -> PayConfig.parse(properties));

        assertTrue("消息应指出与证书不一致：" + e.getMessage(), e.getMessage().contains("不一致"));
    }

    /**
     * 校验 7（正向）：显式配置正确的证书序列号 → 解析通过，且与自动计算值一致。
     */
    @Test
    public void shouldAcceptDeclaredCertSnMatchingCertificate() {
        String actualCertSn = PayConfig.parse(PayTestProps.certMode(APP)).apps().get(APP)
                .verifyKeys().get(0).certSn();
        Properties properties = PayTestProps.with(PayTestProps.certMode(APP),
                PayTestProps.key(APP, "verify.0.cert-sn"), actualCertSn);

        PayAppConfig app = PayConfig.parse(properties).apps().get(APP);

        assertEquals("显式序列号应与自动计算值一致", actualCertSn, app.verifyKeys().get(0).certSn());
    }

    /**
     * 校验 8：{@code @file:} 指向的文件不存在 → 启动失败，消息含键名与路径。
     */
    @Test
    public void shouldFailWhenCredentialFileMissing() {
        String location = "@file:" + System.getProperty("java.io.tmpdir").replace('\\', '/')
                + "/aifei-pay-not-exists-" + System.nanoTime() + ".pem";
        Properties properties = PayTestProps.with(PayTestProps.keyMode(APP),
                PayTestProps.key(APP, "private-key"), location);

        PayException e = assertThrows(PayException.class, () -> PayConfig.parse(properties));

        assertTrue("消息应含键名：" + e.getMessage(),
                e.getMessage().contains(PayTestProps.key(APP, "private-key")));
        assertTrue("消息应含路径提示：" + e.getMessage(), e.getMessage().contains("aifei-pay-not-exists-"));
    }

    /**
     * 校验 8：凭据文件内容为空白 → 启动失败。
     *
     * @throws IOException 写临时文件失败
     */
    @Test
    public void shouldFailWhenCredentialFileIsEmpty() throws IOException {
        tempFile = Files.createTempFile("aifei-pay-empty-", ".pem");
        Files.write(tempFile, "   \n".getBytes(StandardCharsets.UTF_8));
        Properties properties = PayTestProps.with(PayTestProps.keyMode(APP),
                PayTestProps.key(APP, "private-key"), "@file:" + tempFile.toString().replace('\\', '/'));

        PayException e = assertThrows(PayException.class, () -> PayConfig.parse(properties));

        assertTrue("消息应指出内容为空：" + e.getMessage(), e.getMessage().contains("内容为空"));
    }

    /**
     * 全局键：连接超时不是正整数 → 启动失败。
     */
    @Test
    public void shouldFailWhenConnectTimeoutIsNotPositive() {
        Properties properties = PayTestProps.with(PayTestProps.keyMode(APP),
                "pay.connect-timeout", "0");

        PayException e = assertThrows(PayException.class, () -> PayConfig.parse(properties));

        assertTrue("消息应含键名：" + e.getMessage(), e.getMessage().contains("pay.connect-timeout"));
    }

    /**
     * 全局键：读取超时不是正整数 → 启动失败。
     */
    @Test
    public void shouldFailWhenReadTimeoutIsNotNumeric() {
        Properties properties = PayTestProps.with(PayTestProps.keyMode(APP),
                "pay.read-timeout", "15s");

        PayException e = assertThrows(PayException.class, () -> PayConfig.parse(properties));

        assertTrue("消息应含键名：" + e.getMessage(), e.getMessage().contains("pay.read-timeout"));
    }

    /**
     * 全局键：{@code pay.page-http-method} 取值非法 → 启动失败。
     */
    @Test
    public void shouldFailWhenPageHttpMethodInvalid() {
        Properties properties = PayTestProps.with(PayTestProps.keyMode(APP),
                "pay.page-http-method", "PUT");

        PayException e = assertThrows(PayException.class, () -> PayConfig.parse(properties));

        assertTrue("消息应含键名：" + e.getMessage(), e.getMessage().contains("pay.page-http-method"));
    }

    /**
     * 单应用键：内核仅支持 UTF-8，配置其它字符集 → 启动失败。
     */
    @Test
    public void shouldFailWhenCharsetIsNotUtf8() {
        Properties properties = PayTestProps.with(PayTestProps.keyMode(APP),
                PayTestProps.key(APP, "charset"), "GBK");

        PayException e = assertThrows(PayException.class, () -> PayConfig.parse(properties));

        assertTrue("消息应含键名：" + e.getMessage(),
                e.getMessage().contains(PayTestProps.key(APP, "charset")));
    }

    /**
     * 单应用键：内核仅支持 RSA2，配置其它签名类型 → 启动失败。
     */
    @Test
    public void shouldFailWhenSignTypeIsNotRsa2() {
        Properties properties = PayTestProps.with(PayTestProps.keyMode(APP),
                PayTestProps.key(APP, "sign-type"), "RSA");

        PayException e = assertThrows(PayException.class, () -> PayConfig.parse(properties));

        assertTrue("消息应含键名：" + e.getMessage(),
                e.getMessage().contains(PayTestProps.key(APP, "sign-type")));
    }

    /**
     * 默认值：超时、页面方式、字符集、签名类型、签名方式按默认值生效。
     */
    @Test
    public void shouldApplyDocumentedDefaults() {
        PayConfig config = PayConfig.parse(PayTestProps.keyMode(APP));
        PayAppConfig app = config.apps().get(APP);

        assertEquals("默认连接超时", 5000, app.connectTimeout());
        assertEquals("默认读取超时", 15000, app.readTimeout());
        assertEquals("默认页面下单方式", "POST", app.pageHttpMethod());
        assertEquals("默认字符集", "UTF-8", app.charset());
        assertEquals("默认签名类型", "RSA2", app.signType());
        assertEquals("默认环境为沙箱", "sandbox", app.env());
        assertEquals("沙箱默认网关", PayKit.GATEWAY_SANDBOX, app.gatewayUrl());
        assertNull("未配置默认应用时为空", config.defaultAppId());
    }

    /**
     * 默认签名方式是公钥证书：未写 {@code sign-mode} 时按证书模式校验。
     */
    @Test
    public void shouldDefaultToCertModeWhenSignModeMissing() {
        Properties properties = PayTestProps.without(PayTestProps.certMode(APP),
                PayTestProps.key(APP, "sign-mode"));

        PayAppConfig app = PayConfig.parse(properties).apps().get(APP);

        assertTrue("未配置 sign-mode 时应默认证书模式", app.isCertMode());
    }

    /**
     * 配置非法时消息含键名，但**不得**出现私钥明文。
     */
    @Test
    public void shouldNotLeakCredentialValueInConfigError() {
        Properties properties = PayTestProps.with(PayTestProps.certMode(APP),
                PayTestProps.key(APP, "private-key"), SECRET_PLACEHOLDER);
        PayTestProps.without(properties, PayTestProps.key(APP, "app-cert"));

        PayException e = assertThrows(PayException.class, () -> PayConfig.parse(properties));

        assertTrue("消息应含键名：" + e.getMessage(),
                e.getMessage().contains(PayTestProps.key(APP, "app-cert")));
        assertFalse("消息不得含私钥明文：" + e.getMessage(), e.getMessage().contains(SECRET_PLACEHOLDER));
    }

    /**
     * {@code properties} 为 {@code null} 属调用方写错。
     */
    @Test
    public void shouldRejectNullProperties() {
        assertThrows(IllegalArgumentException.class, () -> PayConfig.parse(null));
    }

    /**
     * 多应用：两个应用各自解析出独立配置（同一份 {@code Properties} 中互不干扰）。
     */
    @Test
    public void shouldParseMultipleApplicationsIndependently() {
        Properties properties = PayTestProps.keyMode(APP);
        PayTestProps.with(properties,
                PayTestProps.key(PayTestProps.APP_SUB, "env"), "production",
                PayTestProps.key(PayTestProps.APP_SUB, "sign-mode"), "key",
                PayTestProps.key(PayTestProps.APP_SUB, "private-key"), PayTestCrypto.PRIVATE_KEY_NEW,
                PayTestProps.key(PayTestProps.APP_SUB, "verify.0.type"), "key",
                PayTestProps.key(PayTestProps.APP_SUB, "verify.0.value"), PayTestCrypto.PUBLIC_KEY_NEW);

        PayConfig config = PayConfig.parse(properties);

        assertEquals("应解析出两个应用", 2, config.apps().size());
        assertEquals(PayKit.GATEWAY_SANDBOX, config.apps().get(APP).gatewayUrl());
        assertEquals(PayKit.GATEWAY_PRODUCTION, config.apps().get(PayTestProps.APP_SUB).gatewayUrl());
    }
}
