/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email;

import cn.hg.aifei.email.internal.MailClients;
import cn.hg.aifei.email.internal.MailProviderRegistry;
import cn.hg.aifei.email.internal.smtp.TestHooks;
import cn.hg.aifei.email.support.IsolatedClassLoaders;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 依赖缺失（AC-06 / R-04）：内核缺失时可读失败，不裸抛 {@code NoClassDefFoundError} / {@code ClassNotFoundException}。
 *
 * <p>以真实类加载行为屏蔽内核包触发探测失败，不改任何全局状态。</p>
 */
public class DependencyMissingTest {

    private static final String MISSING_MESSAGE =
            "email: org.dromara.sms4j:sms4j-Email-core:3.3.6 is required, add it to your dependencies";

    /**
     * 清理共享装配状态。
     */
    @After
    public void tearDown() {
        MailClients.clear();
        TestHooks.resetClientFactory();
        MailProviderRegistry.reset();
    }

    /** AC-06：内核不可见时抛可读异常，消息含依赖坐标与引入提示。 */
    @Test
    public void reportsMissingMailCoreAsReadableException() {
        try {
            TestHooks.requireSms4j(IsolatedClassLoaders.blockingMailCore());
            fail("内核缺失时依赖探测必须失败");
        } catch (Throwable error) {
            assertTrue("必须是模块统一异常而不是链接错误: " + error.getClass().getName(),
                    error instanceof MailException);
            MailException exception = (MailException) error;
            assertEquals(MISSING_MESSAGE, exception.getMessage());
            assertTrue("消息须含依赖坐标", exception.getMessage().contains("sms4j-Email-core:3.3.6"));
            assertTrue("消息须含引入提示", exception.getMessage().contains("add it to your dependencies"));
            assertEquals("不得泄漏链接错误类名", -1, exception.getMessage().indexOf("NoClassDefFoundError"));
            assertEquals("不得泄漏类未找到异常类名", -1, exception.getMessage().indexOf("ClassNotFoundException"));
        }
    }

    /** AC-06：内核可见时探测通过（防「恒失败」假通过）。 */
    @Test
    public void passesWhenMailCoreIsPresent() {
        TestHooks.requireSms4j(DependencyMissingTest.class.getClassLoader());
        assertNotNull("测试类路径上应能加载内核工厂", DependencyMissingTest.class.getClassLoader());
    }

    /** AC-06：屏蔽内核包的类加载器确实无法解析内核类（验证隔离手段本身有效）。 */
    @Test
    public void isolatedLoaderReallyHidesMailCore() {
        ClassLoader loader = IsolatedClassLoaders.blockingMailCore();
        try {
            Class.forName("org.dromara.email.core.factory.MailFactory", false, loader);
            fail("隔离类加载器必须屏蔽内核类");
        } catch (ClassNotFoundException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    /** AC-06：内核缺失不会表现为「装配静默成功」。 */
    @Test
    public void missingDependencyIsNeverSilent() {
        boolean failed = false;
        try {
            TestHooks.requireSms4j(IsolatedClassLoaders.blockingMailCore());
        } catch (MailException e) {
            failed = true;
        }
        assertTrue(failed);
        assertFalse("依赖缺失后不应留下装配状态", MailClients.isStarted());
    }
}
