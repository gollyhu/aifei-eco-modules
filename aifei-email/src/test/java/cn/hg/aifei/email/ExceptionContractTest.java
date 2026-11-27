/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email;

import cn.aifei.util.Prop;
import cn.hg.aifei.email.aifei.EmailPlugin;
import cn.hg.aifei.email.internal.MailClients;
import cn.hg.aifei.email.internal.MailErrors;
import cn.hg.aifei.email.internal.MailProviderRegistry;
import cn.hg.aifei.email.internal.smtp.TestHooks;
import cn.hg.aifei.email.support.IsolatedClassLoaders;
import cn.hg.aifei.email.support.RecordingClientFactory;
import cn.hg.aifei.email.support.TestProps;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 异常契约：逐字消息、cause 保留、cause 链判据、消息不含第三方类型名。
 *
 * <p>场景 10 / 11 的判据只依赖 cause 链类型：链上出现 {@link IOException}（含套接字 / 连接 / 超时异常）判网络错误，
 * 否则判 SMTP 返回错误。</p>
 */
public class ExceptionContractTest {

    private static final String QQ_SERVER = "smtp.qq.test";
    private static final int MAX_CHAIN_DEPTH = 16;

    private RecordingClientFactory factory;
    private Prop prop;
    private EmailPlugin plugin;

    /**
     * 装配单通道（默认通道缺省取唯一通道），安装模拟内核客户端。
     */
    @Before
    public void setUp() {
        MailClients.clear();
        MailProviderRegistry.reset();
        factory = new RecordingClientFactory();
        TestHooks.installClientFactory(factory);
        prop = TestProps.singleChannel(QQ_SERVER);
        plugin = new EmailPlugin(prop);
        plugin.start();
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

    /** 场景 1：发送消息为空。 */
    @Test
    public void scenario1MessageMustNotBeNull() {
        assertMessage("email: message must not be null", new Runnable() {
            @Override
            public void run() {
                MailKit.send(null);
            }
        });
    }

    /** 场景 2：收件人为空（构建期校验）。 */
    @Test
    public void scenario2RecipientListMustNotBeEmpty() {
        assertMessage("email: recipient list must not be empty", new Runnable() {
            @Override
            public void run() {
                MailMessage.builder().subject("hello").body("body").build();
            }
        });
    }

    /** 场景 3：主题为空白（构建期校验）。 */
    @Test
    public void scenario3SubjectMustNotBeBlank() {
        assertMessage("email: subject must not be blank", new Runnable() {
            @Override
            public void run() {
                MailMessage.builder().to("x@qq.test").subject("  ").body("body").build();
            }
        });
    }

    /** 场景 4：通道已声明但必填项值为空（键保留、值置空 → 通道被发现后报必填缺失）。 */
    @Test
    public void scenario4RequiredConfigMissing() {
        Prop broken = TestProps.singleChannel(QQ_SERVER);
        TestProps.set(broken, "email.qq.smtp-server", "");
        assertStartMessage(broken, "email: config email.qq.smtp-server is required but was: <empty>");
    }

    /** 场景 4：通道已声明但另一必填项缺失（键不存在）。 */
    @Test
    public void scenario4RequiredKeyMissing() {
        Prop broken = TestProps.singleChannel(QQ_SERVER);
        TestProps.remove(broken, "email.qq.username");
        assertStartMessage(broken, "email: config email.qq.username is required but was: <empty>");
    }

    /** 场景 4：未声明任何通道时以通道键占位提示。 */
    @Test
    public void scenario4NoChannelDeclared() {
        assertStartMessage(TestProps.empty(),
                "email: config email.<name>.smtp-server is required but was: <empty>");
    }

    /** 场景 5：插件已停止时调用门面。 */
    @Test
    public void scenario5PluginStopped() {
        plugin.stop();
        assertMessage("email: plugin not started or stopped, call EmailPlugin.start() first", new Runnable() {
            @Override
            public void run() {
                MailKit.send(plainMessage());
            }
        });
    }

    /** 场景 6：命名通道不存在。 */
    @Test
    public void scenario6ChannelNotFound() {
        assertMessage("email: channel not found: nope", new Runnable() {
            @Override
            public void run() {
                MailKit.use("nope");
            }
        });
    }

    /** 场景 7：默认通道指向未声明的通道。 */
    @Test
    public void scenario7InvalidDefaultChannel() {
        Prop broken = TestProps.twoChannels(QQ_SERVER, "smtp.corp.test");
        TestProps.set(broken, "email.default-channel", "ghost");
        assertStartMessage(broken, "email: default-channel ghost is invalid or missing");
    }

    /** 场景 8：内核依赖缺失。 */
    @Test
    public void scenario8DependencyMissing() {
        try {
            TestHooks.requireSms4j(IsolatedClassLoaders.blockingMailCore());
            fail("内核缺失时依赖探测必须失败");
        } catch (MailException e) {
            assertEquals("email: org.dromara.sms4j:sms4j-Email-core:3.3.6 is required,"
                    + " add it to your dependencies", e.getMessage());
        }
    }

    /** 场景 9：整数配置项格式非法。 */
    @Test
    public void scenario9InvalidInteger() {
        Prop broken = TestProps.singleChannel(QQ_SERVER);
        TestProps.set(broken, "email.qq.max-retries", "many");
        assertStartMessage(broken, "email: config email.qq.max-retries must be an integer but was: many");
    }

    /** 场景 10：SMTP 返回错误（认证失败）判为 SMTP 错误，原因链完整保留。 */
    @Test
    public void scenario10SmtpError() {
        final AuthenticationFailedException root = new AuthenticationFailedException("535 auth failed");
        final org.dromara.email.comm.errors.MailException kernelError =
                new org.dromara.email.comm.errors.MailException(root);
        factory.requireClientFor(QQ_SERVER).failWith(kernelError);

        MailException exception = catchMailException(new Runnable() {
            @Override
            public void run() {
                MailKit.send(plainMessage());
            }
        });

        assertEquals("email: smtp error on channel qq, cause: 535 auth failed", exception.getMessage());
        assertSame("cause 必须是被捕获的原异常", kernelError, exception.getCause());
        assertSame("原因链不得被截断", root, exception.getCause().getCause());
        assertNoThirdPartyTypeName(exception.getMessage());
    }

    /** 场景 11：底层网络异常判为网络错误，原因链完整保留。 */
    @Test
    public void scenario11NetworkError() {
        final SocketException root = new SocketException("connection reset");
        final org.dromara.email.comm.errors.MailException kernelError =
                new org.dromara.email.comm.errors.MailException(root);
        factory.requireClientFor(QQ_SERVER).failWith(kernelError);

        MailException exception = catchMailException(new Runnable() {
            @Override
            public void run() {
                MailKit.send(plainMessage());
            }
        });

        assertEquals("email: network error on channel qq, cause: connection reset", exception.getMessage());
        assertSame("cause 必须是被捕获的原异常", kernelError, exception.getCause());
        assertTrue("原因链中应可遍历到网络异常",
                chainContains(exception, SocketException.class));
        assertNoThirdPartyTypeName(exception.getMessage());
    }

    /** 判据边界：原因链为普通运行时异常（无网络类）时按 SMTP 错误归类。 */
    @Test
    public void scenario10Or11ClassificationWithoutNetworkCause() {
        factory.requireClientFor(QQ_SERVER).failWith(
                new org.dromara.email.comm.errors.MailException("mailbox rejected"));

        MailException exception = catchMailException(new Runnable() {
            @Override
            public void run() {
                MailKit.send(plainMessage());
            }
        });

        assertEquals("email: smtp error on channel qq, cause: mailbox rejected", exception.getMessage());
    }

    /**
     * 场景 11：next-only 形态的嵌套链（{@code setNextException} 构造，构造器不预设 cause）。
     *
     * <p>覆盖定位：这是真实 SMTP 故障在邮件协议里的典型形态。注意 {@code javax.mail 1.6.2} 已把
     * {@code MessagingException#getCause()} 重写为返回 {@code next}，故删掉实现的 next 显式遍历分支时
     * 本用例仍会通过——它证明的是<b>该形态可被正确归类</b>，而非「next 分支必要」。</p>
     */
    @Test
    public void scenario11NetworkErrorInNestedException() {
        final SocketTimeoutException root = new SocketTimeoutException("read timed out");
        final MessagingException nested = new MessagingException("send failed");
        nested.setNextException(root);
        final org.dromara.email.comm.errors.MailException kernelError =
                new org.dromara.email.comm.errors.MailException(nested);
        factory.requireClientFor(QQ_SERVER).failWith(kernelError);

        MailException exception = catchMailException(new Runnable() {
            @Override
            public void run() {
                MailKit.send(plainMessage());
            }
        });

        assertEquals("email: network error on channel qq, cause: read timed out", exception.getMessage());
        assertSame("cause 必须是被捕获的原异常", kernelError, exception.getCause());
        assertTrue("next 链上的网络异常应可遍历到",
                chainContains(exception, SocketTimeoutException.class));
        assertTrue("nextException 链必须被显式遍历到",
                nextChainContains(kernelError, SocketTimeoutException.class));
        assertNoThirdPartyTypeName(exception.getMessage());
    }

    /**
     * 场景 10：next-only 形态的嵌套链（{@code setNextException} 构造），嵌套为认证失败仍判 SMTP 错误。
     *
     * <p>覆盖定位同场景 11：证明该形态归类正确，不断言 next 分支的必要性。</p>
     */
    @Test
    public void scenario10SmtpErrorInNestedException() {
        final AuthenticationFailedException root = new AuthenticationFailedException("535 auth failed");
        final MessagingException nested = new MessagingException("send failed");
        nested.setNextException(root);
        final org.dromara.email.comm.errors.MailException kernelError =
                new org.dromara.email.comm.errors.MailException(nested);
        factory.requireClientFor(QQ_SERVER).failWith(kernelError);

        MailException exception = catchMailException(new Runnable() {
            @Override
            public void run() {
                MailKit.send(plainMessage());
            }
        });

        assertEquals("email: smtp error on channel qq, cause: 535 auth failed", exception.getMessage());
        assertSame("cause 必须是被捕获的原异常", kernelError, exception.getCause());
        assertTrue("嵌套的认证失败异常应可遍历到",
                chainContains(exception, AuthenticationFailedException.class));
        assertTrue("nextException 链必须被显式遍历到",
                nextChainContains(kernelError, AuthenticationFailedException.class));
        assertNoThirdPartyTypeName(exception.getMessage());
    }

    /** 场景 12：HTML 内容串与模板名互斥。 */
    @Test
    public void scenario12MutuallyExclusiveHtml() {
        final MailMessage message = MailMessage.builder()
                .to("x@qq.test")
                .subject("conflict")
                .htmlContent("<b>x</b>")
                .htmlTemplate("notice.html")
                .build();

        assertMessage("email: htmlContent and htmlTemplate are mutually exclusive", new Runnable() {
            @Override
            public void run() {
                MailKit.send(message);
            }
        });
        assertEquals("互斥校验应在发送前拦截，不调用内核", 0, factory.requireClientFor(QQ_SERVER).count());
    }

    /** 场景 13：找不到通道 Provider。 */
    @Test
    public void scenario13ProviderMissing() {
        MailProviderRegistry.setDiscoveryClassLoader(IsolatedClassLoaders.hidingMailProviderServices());
        assertStartMessage(TestProps.singleChannel(QQ_SERVER),
                "email: no MailProvider found for type smtp,"
                        + " check META-INF/services/cn.hg.aifei.email.spi.MailProvider");
    }

    /**
     * 场景 14：附件临时文件写入失败的逐字消息与原因保留。
     *
     * <p>覆盖层级说明：真实 IO 失败依赖 {@code java.io.tmpdir} 与临时目录静态缓存，在共享测试 JVM 中不可靠重放，
     * 故此处直接断言消息工厂的逐字措辞与 cause 保留；附件正路的落盘 / 清理由 {@code SendContractTest} 实测覆盖。</p>
     */
    @Test
    public void scenario14AttachmentWriteFailureMessage() {
        IOException cause = new IOException("No space left on device");
        MailException exception = MailErrors.attachmentWriteFailed("a.txt", cause);

        assertEquals("email: failed to write attachment a.txt to temp file, cause: No space left on device",
                exception.getMessage());
        assertSame("原因必须保留", cause, exception.getCause());
        assertNoThirdPartyTypeName(exception.getMessage());
    }

    /** 场景 15：HTML 模板在 classpath 上不存在。 */
    @Test
    public void scenario15HtmlTemplateNotFound() {
        final MailMessage message = MailMessage.builder()
                .to("x@qq.test")
                .subject("missing template")
                .htmlTemplate("no-such-template.html")
                .build();

        assertMessage("email: html template not found: no-such-template.html on classpath template/",
                new Runnable() {
                    @Override
                    public void run() {
                        MailKit.send(message);
                    }
                });
        assertEquals("模板缺失时不得调用内核", 0, factory.requireClientFor(QQ_SERVER).count());
    }

    /** 场景 16：装配期构建客户端失败（包装内核构建异常的 cause）。 */
    @Test
    public void scenario16BuildClientFailed() {
        plugin.stop();
        final MessagingException root = new MessagingException("boom");
        TestHooks.installClientFactory(new java.util.function.Function<
                org.dromara.email.comm.config.MailSmtpConfig, org.dromara.email.api.MailClient>() {
            @Override
            public org.dromara.email.api.MailClient apply(org.dromara.email.comm.config.MailSmtpConfig config) {
                throw new org.dromara.email.comm.errors.MailException(root);
            }
        });

        try {
            new EmailPlugin(TestProps.singleChannel(QQ_SERVER)).start();
            fail("构建失败时启动必须失败");
        } catch (MailException e) {
            assertEquals("email: failed to build smtp client for channel qq, cause: boom", e.getMessage());
            assertTrue("原因应为内核构建异常", e.getCause() instanceof MessagingException);
            assertNoThirdPartyTypeName(e.getMessage());
        }
        assertFalse("构建失败后不得留下装配状态", MailClients.isStarted());
    }

    /** 表外消息（经批准）：装配入口配置源为空。 */
    @Test
    public void propNullMessage() {
        try {
            new EmailPlugin(null);
            fail("配置源为空时必须立即失败");
        } catch (MailException e) {
            assertEquals("email: prop can not be null", e.getMessage());
        }
    }

    /** 所有消息统一前缀，且不含第三方 / 框架类名。 */
    @Test
    public void allMessagesSharePrefixAndLeakNoTypeNames() {
        String[] messages = new String[] {
            MailErrors.messageNull().getMessage(),
            MailErrors.emptyRecipients().getMessage(),
            MailErrors.blankSubject().getMessage(),
            MailErrors.requiredConfig("email.qq.port", null).getMessage(),
            MailErrors.notStarted().getMessage(),
            MailErrors.channelNotFound("ghost").getMessage(),
            MailErrors.invalidDefaultChannel("ghost").getMessage(),
            MailErrors.sms4jMissing().getMessage(),
            MailErrors.invalidInteger("email.qq.port", "x").getMessage(),
            MailErrors.smtpError("qq", new MessagingException("boom")).getMessage(),
            MailErrors.networkError("qq", new SocketException("boom")).getMessage(),
            MailErrors.mutuallyExclusiveHtml().getMessage(),
            MailErrors.providerMissing("smtp").getMessage(),
            MailErrors.attachmentWriteFailed("a.txt", new IOException("boom")).getMessage(),
            MailErrors.htmlTemplateNotFound("t.html").getMessage(),
            MailErrors.buildClientFailed("qq", new MessagingException("boom")).getMessage()
        };
        for (String message : messages) {
            assertNotNull(message);
            assertTrue("消息必须以统一前缀开头: " + message, message.startsWith("email: "));
            assertNoThirdPartyTypeName(message);
        }
    }

    private static void assertMessage(String expected, Runnable action) {
        MailException exception = catchMailException(action);
        assertEquals(expected, exception.getMessage());
    }

    private static MailException catchMailException(Runnable action) {
        try {
            action.run();
            fail("应抛出 MailException");
            return null;
        } catch (MailException e) {
            return e;
        }
    }

    private static void assertStartMessage(Prop prop, String expected) {
        try {
            new EmailPlugin(prop).start();
            fail("启动必须失败: " + expected);
        } catch (MailException e) {
            assertEquals(expected, e.getMessage());
        }
    }

    /** 判定某类型是否出现在 {@code getCause()} 或 {@code getNextException()} 两条链上。 */
    private static boolean chainContains(Throwable error, Class<? extends Throwable> type) {
        return chainContains(error, type, 0);
    }

    private static boolean chainContains(Throwable error, Class<? extends Throwable> type, int depth) {
        if (error == null || depth > MAX_CHAIN_DEPTH) {
            return false;
        }
        if (type.isInstance(error)) {
            return true;
        }
        if (error instanceof MessagingException
                && chainContains(((MessagingException) error).getNextException(), type, depth + 1)) {
            return true;
        }
        return chainContains(error.getCause(), type, depth + 1);
    }

    /**
     * 只沿 {@code getNextException()} 链判定类型存在性（先经 cause 链定位到邮件协议异常）。
     *
     * <p>用于显式断言「next 链被覆盖」这一形态，不用于断言实现的 next 分支必要性。</p>
     *
     * @param error 起始异常
     * @param type  目标类型
     * @return next 链上是否存在该类型
     */
    private static boolean nextChainContains(Throwable error, Class<? extends Throwable> type) {
        MessagingException messaging = firstMessagingException(error, 0);
        if (messaging == null) {
            return false;
        }
        Exception next = messaging.getNextException();
        int depth = 0;
        while (next != null && depth <= MAX_CHAIN_DEPTH) {
            if (type.isInstance(next)) {
                return true;
            }
            next = next instanceof MessagingException
                    ? ((MessagingException) next).getNextException() : null;
            depth++;
        }
        return false;
    }

    private static MessagingException firstMessagingException(Throwable error, int depth) {
        if (error == null || depth > MAX_CHAIN_DEPTH) {
            return null;
        }
        if (error instanceof MessagingException) {
            return (MessagingException) error;
        }
        return firstMessagingException(error.getCause(), depth + 1);
    }

    private static void assertNoThirdPartyTypeName(String message) {
        assertFalse("消息不得含内核类型名: " + message, message.contains("org.dromara.email"));
        assertFalse("消息不得含邮件协议类型名: " + message, message.contains("javax.mail"));
        assertFalse("消息不得含邮件协议实现类名: " + message, message.contains("com.sun.mail"));
    }

    private static MailMessage plainMessage() {
        return MailMessage.builder().to("x@qq.test").subject("hello").body("plain body").build();
    }
}
