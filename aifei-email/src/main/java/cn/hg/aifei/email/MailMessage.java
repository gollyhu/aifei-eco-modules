/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email;

import cn.hg.aifei.email.internal.MailErrors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 发送消息值对象（不可变，Builder 模式）：承载收件人、主题、正文 / HTML 内容或模板、模板变量与附件字节。
 *
 * <p>构建期（{@link Builder#build()}）校验收件人非空与主题非空白；{@code htmlContent} 与
 * {@code htmlTemplate} 的互斥校验在发送期由发送实现执行。{@code templateVariables} 仅对
 * {@code htmlTemplate} 生效，HTML 内容串不做事后替换。</p>
 */
public final class MailMessage {

    private final List<String> to;
    private final List<String> cc;
    private final List<String> bcc;
    private final String subject;
    private final String body;
    private final String htmlContent;
    private final String htmlTemplate;
    private final Map<String, String> templateVariables;
    private final Map<String, byte[]> attachments;

    /**
     * 由 Builder 组装的包内构造器：全部字段按不可变副本保存，附件字节在构建期即刻复制。
     *
     * @param builder 已校验的构建器
     */
    MailMessage(Builder builder) {
        this.to = immutableList(builder.to);
        this.cc = immutableList(builder.cc);
        this.bcc = immutableList(builder.bcc);
        this.subject = builder.subject;
        this.body = builder.body;
        this.htmlContent = builder.htmlContent;
        this.htmlTemplate = builder.htmlTemplate;
        this.templateVariables = immutableMap(builder.templateVariables);
        this.attachments = immutableAttachments(builder.attachments);
    }

    /**
     * 创建消息构建器。
     *
     * @return 新的构建器实例
     */
    public static MailMessage.Builder builder() {
        return new MailMessage.Builder();
    }

    /**
     * @return 收件人列表（不可变副本，至少一个元素）
     */
    public List<String> getTo() {
        return to;
    }

    /**
     * @return 抄送列表（不可变副本，无抄送时为空列表）
     */
    public List<String> getCc() {
        return cc;
    }

    /**
     * @return 密送列表（不可变副本，无密送时为空列表）
     */
    public List<String> getBcc() {
        return bcc;
    }

    /**
     * @return 邮件主题（非空白）
     */
    public String getSubject() {
        return subject;
    }

    /**
     * @return 纯文本正文，未设置时为 {@code null}
     */
    public String getBody() {
        return body;
    }

    /**
     * @return HTML 内容串，未设置时为 {@code null}；与 {@link #getHtmlTemplate()} 互斥
     */
    public String getHtmlContent() {
        return htmlContent;
    }

    /**
     * @return HTML 模板名（classpath 的 {@code template/} 下资源名），未设置时为 {@code null}
     */
    public String getHtmlTemplate() {
        return htmlTemplate;
    }

    /**
     * @return 模板变量（不可变副本，无变量时为空 Map）
     */
    public Map<String, String> getTemplateVariables() {
        return templateVariables;
    }

    /**
     * 附件（显示名 → 字节）；每次调用逐项复制字节数组，防止外部篡改内部状态。
     *
     * @return 附件 Map（不可变结构，值为字节副本，无附件时为空 Map）
     */
    public Map<String, byte[]> getAttachments() {
        Map<String, byte[]> copy = new LinkedHashMap<String, byte[]>();
        for (Map.Entry<String, byte[]> entry : attachments.entrySet()) {
            byte[] data = entry.getValue();
            copy.put(entry.getKey(), data == null ? null : data.clone());
        }
        return Collections.unmodifiableMap(copy);
    }

    private static List<String> immutableList(List<String> source) {
        return Collections.unmodifiableList(new ArrayList<String>(source));
    }

    private static Map<String, String> immutableMap(Map<String, String> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<String, String>(source));
    }

    private static Map<String, byte[]> immutableAttachments(Map<String, byte[]> source) {
        Map<String, byte[]> copy = new LinkedHashMap<String, byte[]>();
        for (Map.Entry<String, byte[]> entry : source.entrySet()) {
            byte[] data = entry.getValue();
            copy.put(entry.getKey(), data == null ? null : data.clone());
        }
        return Collections.unmodifiableMap(copy);
    }

    /**
     * 消息构建器（内部类，不增加根包公共类型计数）。
     *
     * <p>非线程安全；{@link #build()} 校验收件人非空与主题非空白，附件字节为空或 {@code null} 时忽略该项。</p>
     */
    public static final class Builder {

        private final List<String> to = new ArrayList<String>();
        private final List<String> cc = new ArrayList<String>();
        private final List<String> bcc = new ArrayList<String>();
        private final Map<String, String> templateVariables = new LinkedHashMap<String, String>();
        private final Map<String, byte[]> attachments = new LinkedHashMap<String, byte[]>();

        private String subject;
        private String body;
        private String htmlContent;
        private String htmlTemplate;

        Builder() {
        }

        /**
         * 追加收件人（可变参数）。
         *
         * @param addresses 收件人地址
         * @return 本构建器
         */
        public Builder to(String... addresses) {
            addAll(to, addresses);
            return this;
        }

        /**
         * 追加收件人（列表）。
         *
         * @param addresses 收件人地址列表，{@code null} 时忽略
         * @return 本构建器
         */
        public Builder to(List<String> addresses) {
            addAll(to, addresses);
            return this;
        }

        /**
         * 追加抄送（可变参数）。
         *
         * @param addresses 抄送地址
         * @return 本构建器
         */
        public Builder cc(String... addresses) {
            addAll(cc, addresses);
            return this;
        }

        /**
         * 追加抄送（列表）。
         *
         * @param addresses 抄送地址列表，{@code null} 时忽略
         * @return 本构建器
         */
        public Builder cc(List<String> addresses) {
            addAll(cc, addresses);
            return this;
        }

        /**
         * 追加密送（可变参数）。
         *
         * @param addresses 密送地址
         * @return 本构建器
         */
        public Builder bcc(String... addresses) {
            addAll(bcc, addresses);
            return this;
        }

        /**
         * 追加密送（列表）。
         *
         * @param addresses 密送地址列表，{@code null} 时忽略
         * @return 本构建器
         */
        public Builder bcc(List<String> addresses) {
            addAll(bcc, addresses);
            return this;
        }

        /**
         * 设置主题。
         *
         * @param value 主题，不得为空白
         * @return 本构建器
         */
        public Builder subject(String value) {
            this.subject = value;
            return this;
        }

        /**
         * 设置纯文本正文。
         *
         * @param value 正文，HTML 邮件时可为 {@code null}
         * @return 本构建器
         */
        public Builder body(String value) {
            this.body = value;
            return this;
        }

        /**
         * 设置 HTML 内容串（与模板名互斥）。
         *
         * @param value 完整 HTML 内容
         * @return 本构建器
         */
        public Builder htmlContent(String value) {
            this.htmlContent = value;
            return this;
        }

        /**
         * 设置 HTML 模板名（与 HTML 内容串互斥）。
         *
         * @param templateName classpath 的 {@code template/} 下资源名
         * @return 本构建器
         */
        public Builder htmlTemplate(String templateName) {
            this.htmlTemplate = templateName;
            return this;
        }

        /**
         * 批量设置模板变量。
         *
         * @param variables 变量名到值的映射，{@code null} 时忽略
         * @return 本构建器
         */
        public Builder templateVariables(Map<String, String> variables) {
            if (variables != null) {
                for (Map.Entry<String, String> entry : variables.entrySet()) {
                    templateVariable(entry.getKey(), entry.getValue());
                }
            }
            return this;
        }

        /**
         * 设置单个模板变量。
         *
         * @param key   变量名，空白时忽略
         * @param value 变量值，{@code null} 时忽略
         * @return 本构建器
         */
        public Builder templateVariable(String key, String value) {
            if (key != null && !key.trim().isEmpty() && value != null) {
                this.templateVariables.put(key, value);
            }
            return this;
        }

        /**
         * 追加单个附件。
         *
         * @param name 附件显示名，空白时忽略
         * @param data 附件字节，{@code null} 或空数组时忽略
         * @return 本构建器
         */
        public Builder attachment(String name, byte[] data) {
            if (name != null && !name.trim().isEmpty() && data != null && data.length > 0) {
                this.attachments.put(name, data.clone());
            }
            return this;
        }

        /**
         * 批量追加附件。
         *
         * @param values 显示名到字节的映射，{@code null} 时忽略
         * @return 本构建器
         */
        public Builder attachments(Map<String, byte[]> values) {
            if (values != null) {
                for (Map.Entry<String, byte[]> entry : values.entrySet()) {
                    attachment(entry.getKey(), entry.getValue());
                }
            }
            return this;
        }

        /**
         * 构建不可变消息对象。
         *
         * @return 消息值对象
         * @throws MailException 收件人为空或主题为空白
         */
        public MailMessage build() {
            if (to.isEmpty()) {
                throw MailErrors.emptyRecipients();
            }
            if (subject == null || subject.trim().isEmpty()) {
                throw MailErrors.blankSubject();
            }
            return new MailMessage(this);
        }

        private Builder addAll(List<String> target, String... addresses) {
            if (addresses != null) {
                for (String address : addresses) {
                    add(target, address);
                }
            }
            return this;
        }

        private Builder addAll(List<String> target, List<String> addresses) {
            if (addresses != null) {
                for (String address : addresses) {
                    add(target, address);
                }
            }
            return this;
        }

        private static void add(List<String> target, String address) {
            if (address != null && !address.trim().isEmpty()) {
                target.add(address.trim());
            }
        }
    }
}
