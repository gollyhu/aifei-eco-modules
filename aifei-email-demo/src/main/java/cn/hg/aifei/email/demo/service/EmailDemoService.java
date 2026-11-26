/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email.demo.service;

import cn.aifei.core.Path;
import cn.aifei.util.Prop;
import cn.hg.aifei.email.MailClient;
import cn.hg.aifei.email.MailException;
import cn.hg.aifei.email.MailKit;
import cn.hg.aifei.email.MailMessage;
import cn.hg.aifei.email.MailResult;
import cn.hg.aifei.email.aifei.ConfigBridge;
import cn.hg.aifei.email.demo.arch.In;
import cn.hg.aifei.email.demo.arch.Out;
import cn.hg.aifei.email.internal.smtp.MailMockBridge;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * demo 演示服务（设计 §12.2 端点）：发送全链路真实走 {@code MailKit} → {@code SmtpMailSender} → mock 内核客户端；
 * 错误消息由库内 {@code MailErrors} 真实构造（配置错误态只做校验、不进装配，避免污染运行中的注册表）。
 */
@Path("/email/demo")
public class EmailDemoService {

    private static final String TEMPLATE_PREFIX = "template/";
    private static final String DEFAULT_TEMPLATE = "notice.html";
    private static final String SMS4J_VERSION = "3.3.6";

    /** 演示页视图。 */
    @Path("page")
    public String page() {
        return "/email/index.html";
    }

    /** 发送：通道 + 收件人 + 主题 + 正文类型（+ 可选模板名 / 附件），返回发送结果。 */
    @Path("send")
    public Out send(In in) {
        Map<String, Object> body = bodyOf(in);
        String channel = text(body.get("channel"));
        try {
            MailMessage message = messageOf(body);
            MailClient client = isBlank(channel) ? MailKit.defaultClient() : MailKit.use(channel);
            MailResult result = client.send(message);
            return Out.ok().data(resultOf(result));
        } catch (MailException e) {
            return Out.fail(e.getMessage());
        }
    }

    /** 多通道切换：返回已装配通道集合与默认通道。 */
    @Path("channels")
    public Out channels() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        try {
            Set<String> channels = MailKit.channels();
            List<String> names = new ArrayList<String>(channels);
            Collections.sort(names);
            data.put("channels", names);
            data.put("default", MailKit.defaultClient().channelName());
        } catch (MailException e) {
            data.put("channels", Collections.<String>emptyList());
            data.put("default", "");
        }
        return Out.ok().data(data);
    }

    /** 配置错误态：只调用配置解析（包级方法，经同包桥接），返回库内真实构造的消息；绝不触发装配。 */
    @Path("config-error")
    public Out configError() {
        Prop invalid = new Prop();
        invalid.getProperties().setProperty("email.qq.smtp-server", "smtp.qq.test");
        invalid.getProperties().setProperty("email.qq.port", "465");
        try {
            ConfigBridge.validate(invalid);
            return Out.fail("unexpected: invalid config accepted");
        } catch (MailException e) {
            return Out.of("error", e.getMessage());
        }
    }

    /** 依赖缺失态：以隔离类加载器真实触发依赖探测失败，返回库内真实构造的消息。 */
    @Path("dependency-missing")
    public Out dependencyMissing() {
        try {
            MailMockBridge.requireSms4j(new ClassLoader(null) {
            });
            return Out.fail("unexpected: dependency probe succeeded");
        } catch (MailException e) {
            return Out.of("error", e.getMessage());
        }
    }

    /** HTML 模板渲染：返回 demo 侧预览（同名变量替换）与真实发送结果。 */
    @Path("html-template")
    public Out htmlTemplate(In in) {
        Map<String, Object> body = bodyOf(in);
        String templateName = isBlank(text(body.get("templateName")))
                ? DEFAULT_TEMPLATE : text(body.get("templateName"));
        Map<String, String> variables = variablesOf(body.get("variables"));
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("templateName", templateName);
        data.put("renderedHtml", renderPreview(templateName, variables));

        Map<String, Object> sendBody = new LinkedHashMap<String, Object>(body);
        sendBody.put("bodyType", "template");
        sendBody.put("templateName", templateName);
        sendBody.put("variables", variables);
        // 该端点只要求模板名 + 变量（§12.2），主题与收件人缺失时用演示默认值兜底
        if (isBlank(text(sendBody.get("subject")))) {
            sendBody.put("subject", "模板邮件：" + templateName);
        }
        if (isBlank(text(sendBody.get("to")))) {
            sendBody.put("to", "buyer@example.com");
        }
        try {
            MailMessage message = messageOf(sendBody);
            String channel = text(body.get("channel"));
            MailClient client = isBlank(channel) ? MailKit.defaultClient() : MailKit.use(channel);
            MailResult result = client.send(message);
            data.putAll(resultOf(result));
        } catch (MailException e) {
            data.put("channel", "");
            data.put("success", Boolean.FALSE);
            data.put("message", e.getMessage());
        }
        return Out.ok().data(data);
    }

    /** 依赖与通道状态展示。 */
    @Path("state")
    public Out state() {
        boolean available;
        try {
            MailMockBridge.requireSms4j(EmailDemoService.class.getClassLoader());
            available = true;
        } catch (MailException e) {
            available = false;
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("sms4jAvailable", Boolean.valueOf(available));
        data.put("version", SMS4J_VERSION);
        data.put("mock", Boolean.TRUE);
        data.put("channels", channels().getData());
        return Out.ok().data(data);
    }

    private static Map<String, Object> bodyOf(In in) {
        Map<String, Object> body = in.getMap("");
        return body == null ? new LinkedHashMap<String, Object>() : body;
    }

    private static Map<String, Object> resultOf(MailResult result) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("channel", result.getChannelName());
        data.put("success", Boolean.valueOf(result.isSuccess()));
        data.put("message", result.getMessage());
        return data;
    }

    private static MailMessage messageOf(Map<String, Object> body) {
        String bodyType = text(body.get("bodyType"));
        MailMessage.Builder builder = MailMessage.builder()
                .to(recipientsOf(body.get("to")))
                .subject(text(body.get("subject")));

        if ("template".equals(bodyType)) {
            builder.htmlTemplate(text(body.get("templateName")))
                    .templateVariables(variablesOf(body.get("variables")));
        } else if ("html".equals(bodyType)) {
            builder.htmlContent(text(body.get("content")));
        } else {
            builder.body(text(body.get("content")));
        }

        String attachmentName = text(body.get("attachmentName"));
        String attachmentContent = text(body.get("attachmentContent"));
        if (!isBlank(attachmentName) && !isBlank(attachmentContent)) {
            builder.attachment(attachmentName, attachmentContent.getBytes(StandardCharsets.UTF_8));
        }
        return builder.build();
    }

    private static List<String> recipientsOf(Object value) {
        List<String> recipients = new ArrayList<String>();
        if (value != null) {
            for (String part : String.valueOf(value).split("[,;，；]")) {
                if (!isBlank(part)) {
                    recipients.add(part.trim());
                }
            }
        }
        return recipients;
    }

    private static Map<String, String> variablesOf(Object value) {
        Map<String, String> variables = new LinkedHashMap<String, String>();
        if (value instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                variables.put(String.valueOf(entry.getKey()),
                        entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
            }
        }
        return variables;
    }

    /**
     * demo 展示层预览：对模板串做同名 {@code #{变量名}} 替换（仅用于页面预览）。
     *
     * <p>属 demo 展示逻辑，不进入库公共面、不引模板引擎；库侧只承诺把变量交给内核的语义。</p>
     *
     * @param templateName classpath 模板名
     * @param variables    变量
     * @return 预览 HTML（模板缺失时返回空串）
     */
    private static String renderPreview(String templateName, Map<String, String> variables) {
        String template = readTemplate(templateName);
        if (template == null) {
            return "";
        }
        String rendered = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            rendered = rendered.replace("#{" + entry.getKey() + "}", entry.getValue());
        }
        return rendered;
    }

    private static String readTemplate(String templateName) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = EmailDemoService.class.getClassLoader();
        }
        InputStream in = loader.getResourceAsStream(TEMPLATE_PREFIX + templateName);
        if (in == null) {
            return null;
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read = in.read(buffer);
            while (read >= 0) {
                out.write(buffer, 0, read);
                read = in.read(buffer);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        } finally {
            closeQuietly(in);
        }
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) {
            // 关闭失败不影响已读入内存的模板内容
        }
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
