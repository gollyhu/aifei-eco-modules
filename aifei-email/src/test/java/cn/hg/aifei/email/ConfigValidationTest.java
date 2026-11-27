/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email;

import cn.aifei.util.Prop;
import cn.hg.aifei.email.aifei.EmailPlugin;
import cn.hg.aifei.email.internal.MailClients;
import cn.hg.aifei.email.internal.MailProviderRegistry;
import cn.hg.aifei.email.internal.smtp.TestHooks;
import cn.hg.aifei.email.support.IsolatedClassLoaders;
import cn.hg.aifei.email.support.RecordingClientFactory;
import cn.hg.aifei.email.support.TestProps;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * 配置校验（异常场景 3 / 4 / 7 / 9 / 13）：通道发现、必填项、整数项、默认通道、Provider 缺失。
 *
 * <p>配置解析方法为包内可见，故本类经装配入口间接验证。</p>
 */
public class ConfigValidationTest {

    private static final String QQ_SERVER = "smtp.qq.test";
    private static final String CORP_SERVER = "smtp.corp.test";

    private RecordingClientFactory factory;

    /**
     * 安装模拟内核客户端，避免装配期真实联网。
     */
    @Before
    public void setUp() {
        MailClients.clear();
        MailProviderRegistry.reset();
        factory = new RecordingClientFactory();
        TestHooks.installClientFactory(factory);
    }

    /**
     * 清理共享状态与注入点。
     */
    @After
    public void tearDown() {
        MailClients.clear();
        TestHooks.resetClientFactory();
        MailProviderRegistry.reset();
    }

    /** 场景 4：未声明任何通道。 */
    @Test
    public void rejectsMissingChannel() {
        assertStartMessage(TestProps.empty(),
                "email: config email.<name>.smtp-server is required but was: <empty>");
    }

    /** 场景 4：必填项缺失（消息含完整键名与原值占位）。 */
    @Test
    public void rejectsMissingRequiredKey() {
        Prop prop = TestProps.singleChannel(QQ_SERVER);
        TestProps.remove(prop, "email.qq.from-address");
        assertStartMessage(prop, "email: config email.qq.from-address is required but was: <empty>");
    }

    /** 场景 4：必填项为空白值（原值显示占位符）。 */
    @Test
    public void rejectsBlankRequiredKey() {
        Prop prop = TestProps.singleChannel(QQ_SERVER);
        TestProps.set(prop, "email.qq.username", "   ");
        assertStartMessage(prop, "email: config email.qq.username is required but was: <empty>");
    }

    /** 场景 9：整数项格式非法（重试间隔）。 */
    @Test
    public void rejectsNonNumericRetryInterval() {
        Prop prop = TestProps.singleChannel(QQ_SERVER);
        TestProps.set(prop, "email.qq.retry-interval", "five");
        assertStartMessage(prop, "email: config email.qq.retry-interval must be an integer but was: five");
    }

    /** 场景 9：整数项格式非法（最大重试次数）。 */
    @Test
    public void rejectsNonNumericMaxRetries() {
        Prop prop = TestProps.singleChannel(QQ_SERVER);
        TestProps.set(prop, "email.qq.max-retries", "3.5");
        assertStartMessage(prop, "email: config email.qq.max-retries must be an integer but was: 3.5");
    }

    /** 场景 7：多通道未声明默认通道。 */
    @Test
    public void rejectsMultipleChannelsWithoutDefault() {
        Prop prop = TestProps.twoChannels(QQ_SERVER, CORP_SERVER);
        TestProps.remove(prop, "email.default-channel");
        assertStartMessage(prop, "email: default-channel <empty> is invalid or missing");
    }

    /** 场景 7：默认通道指向未声明的通道。 */
    @Test
    public void rejectsUnknownDefaultChannel() {
        Prop prop = TestProps.twoChannels(QQ_SERVER, CORP_SERVER);
        TestProps.set(prop, "email.default-channel", "unknown");
        assertStartMessage(prop, "email: default-channel unknown is invalid or missing");
    }

    /** 单通道可缺省默认通道，取唯一通道。 */
    @Test
    public void acceptsSingleChannelWithoutDefault() {
        start(TestProps.singleChannel(QQ_SERVER));

        assertEquals("qq", MailClients.defaultChannelName());
        assertEquals(1, MailKit.channels().size());
    }

    /** 场景 13：Provider 缺失（服务声明资源不可见时真实触发），并断言正常类路径下可发现。 */
    @Test
    public void rejectsMissingProvider() {
        MailProviderRegistry.setDiscoveryClassLoader(IsolatedClassLoaders.hidingMailProviderServices());
        assertStartMessage(TestProps.singleChannel(QQ_SERVER),
                "email: no MailProvider found for type smtp,"
                        + " check META-INF/services/cn.hg.aifei.email.spi.MailProvider");

        MailProviderRegistry.reset();
        assertNotNull("正常类路径下必须能发现 smtp Provider", MailProviderRegistry.getProvider("smtp"));
        assertEquals("smtp", MailProviderRegistry.getProvider("smtp").type());
        assertFalse("失败后不应留下装配状态", MailClients.isStarted());
    }

    /** 通道标识含点号会造成键归属歧义，此类键被忽略。 */
    @Test
    public void ignoresChannelNameWithDot() {
        Prop prop = TestProps.empty();
        TestProps.appendValidChannel(prop, "a.b", QQ_SERVER, "465", "a@qq.test");
        assertStartMessage(prop, "email: config email.<name>.smtp-server is required but was: <empty>");
    }

    /** 保留字 default-channel 不得被当作通道标识。 */
    @Test
    public void ignoresReservedChannelName() {
        Prop prop = TestProps.empty();
        TestProps.appendValidChannel(prop, "default-channel", QQ_SERVER, "465", "a@qq.test");
        assertStartMessage(prop, "email: config email.<name>.smtp-server is required but was: <empty>");
    }

    /** 未识别键忽略（透传优先）：拼写错误的附加键不影响合法装配。 */
    @Test
    public void ignoresUnknownOptionKeys() {
        Prop prop = TestProps.singleChannel(QQ_SERVER);
        TestProps.set(prop, "email.qq.smtp-servr", "typo.example.test");
        TestProps.set(prop, "email.qq.some-future-option", "value");
        start(prop);

        assertEquals(QQ_SERVER, factory.configFor(QQ_SERVER).getSmtpServer());
        assertEquals("qq", MailKit.defaultClient().channelName());
    }

    /** 缺省项按设计默认值填充（SSL / 认证 true、重试 5、最大重试 0）。 */
    @Test
    public void appliesConfigDefaults() {
        start(TestProps.singleChannel(QQ_SERVER));

        org.dromara.email.comm.config.MailSmtpConfig config = factory.configFor(QQ_SERVER);
        assertEquals("true", config.getIsSSL());
        assertEquals("true", config.getIsAuth());
        assertEquals(5, config.getRetryInterval());
        assertEquals(0, config.getMaxRetries());
    }

    private void start(Prop prop) {
        EmailPlugin plugin = new EmailPlugin(prop);
        plugin.start();
    }

    private void assertStartMessage(Prop prop, String expected) {
        try {
            new EmailPlugin(prop).start();
            fail("配置非法 / 依赖缺失时启动必须失败: " + expected);
        } catch (MailException e) {
            assertEquals(expected, e.getMessage());
        }
        assertFalse("启动失败后不得留下装配状态", MailClients.isStarted());
    }
}
