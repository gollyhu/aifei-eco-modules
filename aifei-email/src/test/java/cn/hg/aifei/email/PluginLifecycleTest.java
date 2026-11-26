/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email;

import cn.aifei.aop.AopKit;
import cn.aifei.util.Prop;
import cn.hg.aifei.email.aifei.EmailPlugin;
import cn.hg.aifei.email.internal.DefaultMailClientProxy;
import cn.hg.aifei.email.internal.MailClients;
import cn.hg.aifei.email.internal.MailProviderRegistry;
import cn.hg.aifei.email.internal.smtp.TestHooks;
import cn.hg.aifei.email.support.MockSms4jMailClient;
import cn.hg.aifei.email.support.RecordingClientFactory;
import cn.hg.aifei.email.support.TestProps;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 装配生命周期（AC-04 / AC-05）：启动即可用、停止后一律拒绝、启动与停止幂等、失败无残留且可重装配、
 * 停止后重装配对注入客户端的即时刷新。
 */
public class PluginLifecycleTest {

    private static final String QQ_SERVER = "smtp.qq.test";
    private static final String CORP_SERVER = "smtp.corp.test";
    private static final String NOT_STARTED =
            "email: plugin not started or stopped, call EmailPlugin.start() first";

    private RecordingClientFactory factory;
    private Prop prop;
    private EmailPlugin plugin;

    /**
     * 准备未启动的装配入口（各用例自行控制启动 / 停止次序）。
     */
    @Before
    public void setUp() {
        MailClients.clear();
        MailProviderRegistry.reset();
        factory = new RecordingClientFactory();
        TestHooks.installClientFactory(factory);
        prop = TestProps.twoChannels(QQ_SERVER, CORP_SERVER);
        plugin = new EmailPlugin(prop);
    }

    /**
     * 清理共享状态与注入点。
     */
    @After
    public void tearDown() {
        if (plugin != null) {
            plugin.stop();
        }
        MailClients.clear();
        TestHooks.resetClientFactory();
        MailProviderRegistry.reset();
    }

    /** AC-04：启动后门面可用。 */
    @Test
    public void startExposesClients() {
        plugin.start();

        assertTrue(MailClients.isStarted());
        assertEquals("qq", MailKit.defaultClient().channelName());
        assertEquals("corp", MailKit.use("corp").channelName());
        assertEquals(2, MailKit.channels().size());
        assertEquals("两个通道各构建一次客户端", 2, factory.receivedConfigs().size());
    }

    /** AC-04：停止后门面调用与已注入客户端一律抛「插件未启动或已停止」。 */
    @Test
    public void stopRejectsFacadeAndInjectedClient() {
        plugin.start();
        final MailClient injected = new DefaultMailClientProxy();
        assertEquals("qq", injected.channelName());
        plugin.stop();

        assertMessage(NOT_STARTED, new Runnable() {
            @Override
            public void run() {
                MailKit.defaultClient();
            }
        });
        assertMessage(NOT_STARTED, new Runnable() {
            @Override
            public void run() {
                MailKit.send(plainMessage());
            }
        });
        assertMessage(NOT_STARTED, new Runnable() {
            @Override
            public void run() {
                MailKit.use("qq");
            }
        });
        assertMessage(NOT_STARTED, new Runnable() {
            @Override
            public void run() {
                MailKit.channels();
            }
        });
        assertMessage(NOT_STARTED, new Runnable() {
            @Override
            public void run() {
                injected.send(plainMessage());
            }
        });
        assertMessage(NOT_STARTED, new Runnable() {
            @Override
            public void run() {
                injected.channelName();
            }
        });
        assertEquals("停止后不应再触发发送", 0, factory.requireClientFor(QQ_SERVER).count());
    }

    /** AC-04：启动幂等——重复启动不重建客户端、不叠加状态。 */
    @Test
    public void startIsIdempotent() {
        plugin.start();
        plugin.start();

        assertEquals("重复启动不得重建客户端", 2, factory.receivedConfigs().size());
        assertEquals(2, MailKit.channels().size());
        assertEquals("qq", MailKit.defaultClient().channelName());
    }

    /** AC-04：停止幂等——重复停止无副作用。 */
    @Test
    public void stopIsIdempotent() {
        plugin.start();
        plugin.stop();
        plugin.stop();

        assertFalse(MailClients.isStarted());
        assertTrue(MailClients.snapshot().isEmpty());
        assertMessage(NOT_STARTED, new Runnable() {
            @Override
            public void run() {
                MailKit.defaultClient();
            }
        });
    }

    /** AC-05：启动失败无残留，修正配置后可用同一装配入口重新装配。 */
    @Test
    public void failedStartLeavesNoResidueAndCanRecover() {
        TestProps.remove(prop, "email.qq.password");
        try {
            plugin.start();
            fail("配置非法时启动必须失败");
        } catch (MailException e) {
            assertEquals("email: config email.qq.password is required but was: <empty>", e.getMessage());
        }

        assertFalse("失败后装配状态必须复位", MailClients.isStarted());
        assertTrue("失败后注册表必须清空", MailClients.snapshot().isEmpty());
        assertNull("失败后不应留下默认通道", MailClients.defaultChannelName());
        assertMessage(NOT_STARTED, new Runnable() {
            @Override
            public void run() {
                MailKit.send(plainMessage());
            }
        });

        TestProps.set(prop, "email.qq.password", "secret");
        TestProps.set(prop, "email.corp.password", "secret");
        plugin.start();

        assertTrue(MailClients.isStarted());
        assertEquals("qq", MailKit.send(plainMessage()).getChannelName());
        assertEquals(1, factory.requireClientFor(QQ_SERVER).count());
    }

    /** AC-05 / P1 #10：停止后重装配，动态委托代理自动指向新装配的实例（配置变更即时生效）。 */
    @Test
    public void stopThenStartRefreshesDynamicProxy() {
        plugin.start();
        final MailClient injected = new DefaultMailClientProxy();
        assertEquals("qq", injected.channelName());
        MockSms4jMailClient qqBeforeRestart = factory.requireClientFor(QQ_SERVER);
        assertEquals("qq", MailKit.send(plainMessage()).getChannelName());
        assertEquals(1, qqBeforeRestart.count());

        plugin.stop();
        TestProps.set(prop, "email.default-channel", "corp");
        plugin.start();

        assertEquals("重装配后代理必须指向新默认通道", "corp", injected.channelName());
        assertEquals("旧实例不应再被使用", 1, qqBeforeRestart.count());
        MockSms4jMailClient corpAfterRestart = factory.requireClientFor(CORP_SERVER);
        assertEquals("重装配后的 corp 客户端尚未发送", 0, corpAfterRestart.count());

        MailResult result = injected.send(plainMessage());
        assertEquals("corp", result.getChannelName());
        assertEquals(1, corpAfterRestart.count());
        assertEquals("重装配后的 qq 客户端不应收到默认通道邮件", 0,
                factory.requireClientFor(QQ_SERVER).count());
    }

    /** AC-04 / P1 #10：框架注入的单例同样随重装配刷新（若框架可取到单例则断言刷新语义）。 */
    @Test
    public void injectedSingletonFollowsCurrentDefaultChannel() {
        plugin.start();
        MailClient injected = AopKit.get().getAopFactory().get(MailClient.class);
        assertNotNull("装配后应可取得注入的客户端单例", injected);
        assertTrue("注入单例应为动态委托代理", injected instanceof DefaultMailClientProxy);
        assertEquals("qq", injected.channelName());

        plugin.stop();
        TestProps.set(prop, "email.default-channel", "corp");
        plugin.start();

        assertEquals("重装配后注入单例必须指向新默认通道", "corp", injected.channelName());
        assertEquals("corp", injected.send(plainMessage()).getChannelName());
    }

    private static void assertMessage(String expected, Runnable action) {
        try {
            action.run();
            fail("应抛出 MailException: " + expected);
        } catch (MailException e) {
            assertEquals(expected, e.getMessage());
        }
    }

    private static MailMessage plainMessage() {
        return MailMessage.builder().to("x@qq.test").subject("hello").body("plain body").build();
    }
}
