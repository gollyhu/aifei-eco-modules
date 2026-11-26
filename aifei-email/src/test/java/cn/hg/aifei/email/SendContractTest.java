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
import cn.hg.aifei.email.support.MockSms4jMailClient;
import cn.hg.aifei.email.support.RecordingClientFactory;
import cn.hg.aifei.email.support.TestProps;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 发送契约（AC-01 / AC-02 / AC-03）：纯文本与 HTML 发送、多收件人 / 抄送密送、附件字节转换与临时文件清理、
 * 结果字段映射、多通道独立实例、换默认通道与换 SMTP 服务器只改配置。
 *
 * <p>全程零外网：内核客户端由模拟实现替代，"发送成功"即 {@code send} 不抛异常。</p>
 */
public class SendContractTest {

    private static final String QQ_SERVER = "smtp.qq.test";
    private static final String CORP_SERVER = "smtp.corp.test";

    private RecordingClientFactory factory;
    private Prop prop;
    private EmailPlugin plugin;

    /**
     * 装配双通道（模拟内核客户端），默认通道为 qq。
     */
    @Before
    public void setUp() {
        MailClients.clear();
        MailProviderRegistry.reset();
        factory = new RecordingClientFactory();
        TestHooks.installClientFactory(factory);
        prop = TestProps.twoChannels(QQ_SERVER, CORP_SERVER);
        plugin = new EmailPlugin(prop);
        plugin.start();
    }

    /**
     * 清空注册表、注入点与发现缓存，避免跨测试类串扰。
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

    /** AC-01：纯文本 + 多收件人 + 抄送 + 密送，返回成功结果与通道标识。 */
    @Test
    public void sendsPlainTextWithRecipientsAndCopies() {
        MailResult result = MailKit.send(MailMessage.builder()
                .to("x@qq.test", "y@qq.test")
                .cc("c@qq.test")
                .bcc("d@qq.test")
                .subject("hello")
                .body("plain body")
                .build());

        assertEquals("qq", result.getChannelName());
        assertTrue(result.isSuccess());
        assertEquals("sent", result.getMessage());

        MockSms4jMailClient.Sent sent = factory.requireClientFor(QQ_SERVER).last();
        assertNotNull(sent);
        assertEquals(Arrays.asList("x@qq.test", "y@qq.test"), sent.message().getMailAddress());
        assertEquals("hello", sent.message().getTitle());
        assertEquals("plain body", sent.message().getBody());
        assertEquals(Arrays.asList("c@qq.test"), sent.message().getCc());
        assertEquals(Arrays.asList("d@qq.test"), sent.message().getBcc());
        assertNull("纯文本邮件不应带 HTML 内容", sent.message().getHtmlContent());
        assertNull("纯文本邮件不应带模板流", sent.message().getHtmlInputStream());
    }

    /** AC-01：HTML 内容串直传内核，不做模板变量替换。 */
    @Test
    public void sendsHtmlContentDirectly() {
        MailKit.send(MailMessage.builder()
                .to("x@qq.test")
                .subject("html")
                .htmlContent("<b>#{name}</b>")
                .templateVariable("name", "should-be-ignored")
                .build());

        MockSms4jMailClient.Sent sent = factory.requireClientFor(QQ_SERVER).last();
        assertEquals("<b>#{name}</b>", sent.message().getHtmlContent());
        assertNull("HTML 内容串不产生模板流", sent.message().getHtmlInputStream());
        assertNull("不得改用按路径读取的模板 API", sent.message().getHtmlPath());
        assertTrue("templateVariables 仅对模板生效",
                sent.message().getHtmlValues() == null || sent.message().getHtmlValues().isEmpty());
    }

    /** AC-01：HTML 模板经上下文类加载器从 classpath 模板目录读取，变量映射到内核变量表。 */
    @Test
    public void sendsHtmlTemplateWithVariables() {
        MailKit.send(MailMessage.builder()
                .to("x@qq.test")
                .subject("template")
                .htmlTemplate("notice.html")
                .templateVariables(twoVariables())
                .build());

        MockSms4jMailClient.Sent sent = factory.requireClientFor(QQ_SERVER).last();
        assertNotNull("模板必须以流形式交给内核", sent.message().getHtmlInputStream());
        assertNull("不得改用按路径读取的模板 API", sent.message().getHtmlPath());
        assertNull("模板邮件不设置 HTML 内容串", sent.message().getHtmlContent());

        Map<String, String> values = sent.message().getHtmlValues();
        assertEquals("Aifei", values.get("name"));
        assertEquals("9527", values.get("code"));

        String template = readStream(sent.message().getHtmlInputStream());
        assertTrue("模板内容应由上下文类加载器读取到: " + template, template.contains("#{name}"));
        assertTrue("模板内容应由上下文类加载器读取到: " + template, template.contains("#{code}"));
    }

    /** AC-01 / D14：附件字节写入临时文件供内核使用，发送返回后临时文件已清理。 */
    @Test
    public void convertsAttachmentsAndCleansTempFiles() {
        byte[] payload = new byte[] {1, 2, 3, 4, 5};
        MailKit.send(MailMessage.builder()
                .to("x@qq.test")
                .subject("attachment")
                .body("see attachment")
                .attachment("a.txt", payload)
                .build());

        MockSms4jMailClient.Sent sent = factory.requireClientFor(QQ_SERVER).last();
        assertEquals(1, sent.tempFilePaths().size());
        assertEquals("发送时刻临时文件必须真实存在", Boolean.TRUE, sent.tempFileExistedAtSend().get("a.txt"));
        assertArrayEquals("内核读到的附件内容必须与消费方字节一致", payload, sent.attachedBytes().get("a.txt"));

        for (String path : sent.tempFilePaths()) {
            assertFalse("发送结束后临时文件必须被清理: " + path, new File(path).exists());
        }
    }

    /** AC-01：空字节 / null 附件被忽略，不产生临时文件。 */
    @Test
    public void ignoresEmptyAttachments() {
        MailKit.send(MailMessage.builder()
                .to("x@qq.test")
                .subject("empty attachment")
                .body("body")
                .attachment("empty.txt", new byte[0])
                .attachment("null.txt", null)
                .build());

        MockSms4jMailClient.Sent sent = factory.requireClientFor(QQ_SERVER).last();
        assertTrue("空附件不应产生临时文件", sent.tempFilePaths().isEmpty());
        assertTrue(sent.message().getFiles() == null || sent.message().getFiles().isEmpty());
    }

    /** AC-02：两通道各自独立实例，命名通道只落到自己的客户端。 */
    @Test
    public void namedChannelsUseIndependentClients() {
        MailResult result = MailKit.use("corp").send(plainMessage());

        assertEquals("corp", result.getChannelName());
        assertEquals(1, factory.requireClientFor(CORP_SERVER).count());
        assertEquals("qq 通道不应收到 corp 通道的邮件", 0, factory.requireClientFor(QQ_SERVER).count());
        assertEquals(2, MailKit.channels().size());
        assertTrue(MailKit.channels().containsAll(Arrays.asList("qq", "corp")));
        assertNotNull("每通道独立构建一次客户端", factory.clientFor(QQ_SERVER));
        assertNotNull("每通道独立构建一次客户端", factory.clientFor(CORP_SERVER));
    }

    /** AC-02 / AC-03：换默认通道只改配置，业务发送代码零改动。 */
    @Test
    public void switchingDefaultChannelOnlyChangesConfig() {
        assertEquals("qq", MailKit.defaultClient().channelName());

        plugin.stop();
        TestProps.set(prop, "email.default-channel", "corp");
        plugin.start();

        assertEquals("corp", MailKit.defaultClient().channelName());
        MailResult result = MailKit.send(plainMessage());
        assertEquals("corp", result.getChannelName());
        assertEquals(1, factory.requireClientFor(CORP_SERVER).count());
        assertEquals(0, factory.requireClientFor(QQ_SERVER).count());
    }

    /** AC-03：换 SMTP 服务器只改配置，业务发送代码零改动仍能发送。 */
    @Test
    public void switchingSmtpServerOnlyChangesConfig() {
        plugin.stop();
        String newServer = "smtp.qq.migrated.test";
        TestProps.set(prop, "email.qq.smtp-server", newServer);
        plugin.start();

        MailResult result = MailKit.send(plainMessage());
        assertEquals("qq", result.getChannelName());
        assertNotNull("新配置的 SMTP 服务器必须被用于构建客户端", factory.clientFor(newServer));
        assertEquals(1, factory.requireClientFor(newServer).count());
    }

    /** 配置项 → 内核配置映射：必填项直传、缺省项填充默认值（D7 关闭重试）。 */
    @Test
    public void mapsConfigToCoreConfigWithDefaults() {
        org.dromara.email.comm.config.MailSmtpConfig qq = factory.configFor(QQ_SERVER);
        assertEquals(QQ_SERVER, qq.getSmtpServer());
        assertEquals("465", qq.getPort());
        assertEquals("a@qq.test", qq.getFromAddress());
        assertEquals("a@qq.test", qq.getUsername());
        assertEquals("secret", qq.getPassword());
        assertEquals("Aifei", qq.getNickName());
        assertEquals("true", qq.getIsSSL());
        assertEquals("true", qq.getIsAuth());
        assertEquals("重试间隔默认 5", 5, qq.getRetryInterval());
        assertEquals("最大重试默认 0（不重试）", 0, qq.getMaxRetries());

        org.dromara.email.comm.config.MailSmtpConfig corp = factory.configFor(CORP_SERVER);
        assertEquals("587", corp.getPort());
        assertEquals("false", corp.getIsSSL());
        assertEquals(3, corp.getMaxRetries());
    }

    /** §7.3 并发：多线程同时发送互不串扰，临时文件各自清理、无泄漏。 */
    @Test
    public void sendsConcurrentlyWithoutCrossTalk() throws Exception {
        final int threads = 8;
        final CountDownLatch ready = new CountDownLatch(threads);
        final CountDownLatch go = new CountDownLatch(1);
        final List<Throwable> errors = new ArrayList<Throwable>();
        final List<String> subjects = new ArrayList<String>();
        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            final int index = i;
            subjects.add("subject-" + index);
            workers[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    ready.countDown();
                    try {
                        go.await();
                        MailKit.send(MailMessage.builder()
                                .to("x@qq.test")
                                .subject("subject-" + index)
                                .body("body-" + index)
                                .attachment("file-" + index + ".bin", payloadOf(index))
                                .build());
                    } catch (Throwable e) {
                        synchronized (errors) {
                            errors.add(e);
                        }
                    }
                }
            });
            workers[i].start();
        }
        ready.await();
        go.countDown();
        for (int i = 0; i < threads; i++) {
            workers[i].join(15000L);
        }

        assertTrue("并发发送不应有异常: " + errors, errors.isEmpty());
        List<MockSms4jMailClient.Sent> records = factory.requireClientFor(QQ_SERVER).all();
        assertEquals(threads, records.size());

        Set<String> seenSubjects = new HashSet<String>();
        for (MockSms4jMailClient.Sent sent : records) {
            seenSubjects.add(sent.message().getTitle());
            assertEquals("每次调用只应带自己的附件", 1, sent.attachedBytes().size());
            String name = sent.message().getFiles().keySet().iterator().next();
            int index = Integer.parseInt(sent.message().getTitle().substring("subject-".length()));
            assertArrayEquals("附件内容不得串扰", payloadOf(index), sent.attachedBytes().get(name));
            for (String path : sent.tempFilePaths()) {
                assertFalse("并发场景下临时文件同样必须清理: " + path, new File(path).exists());
            }
        }
        assertEquals("每个线程的邮件各出现一次", new HashSet<String>(subjects), seenSubjects);
    }

    private static Map<String, String> twoVariables() {
        Map<String, String> variables = new LinkedHashMap<String, String>();
        variables.put("name", "Aifei");
        variables.put("code", "9527");
        return variables;
    }

    private static MailMessage plainMessage() {
        return MailMessage.builder().to("x@qq.test").subject("hello").body("plain body").build();
    }

    private static byte[] payloadOf(int index) {
        byte[] payload = new byte[4];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (index + i);
        }
        return payload;
    }

    private static String readStream(InputStream in) {
        StringBuilder text = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        try {
            String line = reader.readLine();
            while (line != null) {
                text.append(line).append('\n');
                line = reader.readLine();
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取模板流失败", e);
        }
        return text.toString();
    }
}
