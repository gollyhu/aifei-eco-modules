/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email.internal.smtp;

import cn.hg.aifei.email.MailException;
import cn.hg.aifei.email.MailMessage;
import cn.hg.aifei.email.internal.MailErrors;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息映射适配：本模块 {@link MailMessage} → 内核消息对象，并完成附件与 HTML 模板的落地转换。
 *
 * <p>附件（字节）写入系统临时文件后转为内核要求的位置映射，临时文件清单随
 * {@link MappedMessage} 返回，由发送实现在 {@code finally} 按清单清理；HTML 模板由本类以
 * <b>上下文类加载器</b>从 classpath 的模板目录读取后以流形式交给内核（不使用内核的按路径读取 API，
 * 因内核按自身类加载器解析资源，库模块无法可靠读到消费方资源）。</p>
 */
public final class SmtpMappers {

    private static final String TEMPLATE_PREFIX = "template/";
    private static final String TEMP_FILE_PREFIX = "aifei-email-";
    private static final String TEMP_FILE_SUFFIX = ".attachment";

    private SmtpMappers() {
    }

    /**
     * 本模块消息 → 内核消息（含附件落盘与模板读取）。
     *
     * @param message 本模块消息（调用方已校验非空与互斥）
     * @return 映射结果（内核消息 + 本次产生的临时文件清单）
     * @throws MailException 附件写入临时文件失败、HTML 模板在 classpath 上不存在
     */
    public static MappedMessage toSms4jMessage(MailMessage message) {
        List<Path> tempFiles = new ArrayList<Path>();
        try {
            org.dromara.email.comm.entity.MailMessage.MailsBuilder builder =
                    org.dromara.email.comm.entity.MailMessage.Builder();
            builder.mailAddress(message.getTo());
            builder.title(message.getSubject());
            if (notBlank(message.getBody())) {
                builder.body(message.getBody());
            }
            if (!message.getCc().isEmpty()) {
                builder.cc(message.getCc());
            }
            if (!message.getBcc().isEmpty()) {
                builder.bcc(message.getBcc());
            }
            Map<String, String> files = writeAttachments(message.getAttachments(), tempFiles);
            if (!files.isEmpty()) {
                builder.files(files);
            }
            applyHtml(message, builder);
            return new MappedMessage(builder.build(), tempFiles);
        } catch (RuntimeException e) {
            cleanupTempFiles(tempFiles);
            throw e;
        }
    }

    /**
     * 按传入清单清理临时文件（成功 / 失败都调用；单项删除失败不影响主流程）。
     *
     * @param tempFiles 待清理的临时文件清单，{@code null} 时忽略
     */
    public static void cleanupTempFiles(List<Path> tempFiles) {
        if (tempFiles == null) {
            return;
        }
        for (Path path : tempFiles) {
            if (path == null) {
                continue;
            }
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // 临时文件删除失败不覆盖主流程结果：系统临时目录由运行环境定期回收
            }
        }
    }

    private static void applyHtml(MailMessage message,
                                  org.dromara.email.comm.entity.MailMessage.MailsBuilder builder) {
        String template = message.getHtmlTemplate();
        if (notBlank(template)) {
            builder.html(readTemplate(template));
            if (!message.getTemplateVariables().isEmpty()) {
                builder.htmlValues(message.getTemplateVariables());
            }
            return;
        }
        if (notBlank(message.getHtmlContent())) {
            builder.htmlContent(message.getHtmlContent());
        }
    }

    private static InputStream readTemplate(String templateName) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = SmtpMappers.class.getClassLoader();
        }
        InputStream raw = loader.getResourceAsStream(TEMPLATE_PREFIX + templateName);
        if (raw == null) {
            throw MailErrors.htmlTemplateNotFound(templateName);
        }
        try {
            return new ByteArrayInputStream(readAll(raw));
        } catch (IOException e) {
            throw MailErrors.htmlTemplateNotFound(templateName);
        } finally {
            closeQuietly(raw);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read = in.read(buffer);
        while (read >= 0) {
            out.write(buffer, 0, read);
            read = in.read(buffer);
        }
        return out.toByteArray();
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) {
            // 关闭失败不影响已读入内存的模板内容
        }
    }

    private static Map<String, String> writeAttachments(Map<String, byte[]> attachments, List<Path> tempFiles) {
        Map<String, String> files = new LinkedHashMap<String, String>();
        for (Map.Entry<String, byte[]> entry : attachments.entrySet()) {
            byte[] data = entry.getValue();
            if (data == null || data.length == 0) {
                continue;
            }
            Path tempFile = null;
            try {
                tempFile = Files.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX);
                tempFiles.add(tempFile);
                Files.write(tempFile, data);
                files.put(entry.getKey(), tempFile.toString());
            } catch (IOException e) {
                throw MailErrors.attachmentWriteFailed(entry.getKey(), e);
            }
        }
        return files;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
