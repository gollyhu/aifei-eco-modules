/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.core.util;

/**
 * 极简 XML 构建器（微信报文结构极简，不引入 XML 库）。
 *
 * <p>文本节点统一做实体转义（含 {@code & < > " '}），因此业务内容中的 {@code ]]>} 等
 * 注入片段会被安全转义而非破坏报文结构。
 */
public class XmlBuilder {

    private static final String MISSING_TOOLS =
            "缺少可选依赖 io.github.gollyhu:aifei-tools；使用本能力时消费方须显式声明";

    private final StringBuilder sb = new StringBuilder(160);

    public XmlBuilder() {
        sb.append("<xml>");
    }

    /**
     * 输出 {@code <name>escaped</name>}；value 为 null 时不输出该节点
     */
    public XmlBuilder text(String name, Object value) {
        if (value == null) {
            return this;
        }
        sb.append('<').append(name).append('>')
                .append(escape(String.valueOf(value)))
                .append("</").append(name).append('>');
        return this;
    }

    /**
     * 输出 CDATA 节点；value 为 null 时不输出该节点，内容中的 {@code ]]>} 会被安全拆分
     */
    public XmlBuilder cdata(String name, String value) {
        if (value == null) {
            return this;
        }
        sb.append('<').append(name).append("><![CDATA[")
                .append(value.replace("]]>", "]]]]><![CDATA[>"))
                .append("]]></").append(name).append('>');
        return this;
    }

    public XmlBuilder open(String name) {
        sb.append('<').append(name).append('>');
        return this;
    }

    public XmlBuilder close(String name) {
        sb.append("</").append(name).append('>');
        return this;
    }

    /**
     * 追加已构建好的片段
     */
    public XmlBuilder raw(String fragment) {
        sb.append(fragment);
        return this;
    }

    public String build() {
        return sb.toString() + "</xml>";
    }

    /**
     * XML 文本节点实体转义
     */
    public static String escape(String str) {
        return escapeViaTools(str);
    }

    private static String escapeViaTools(String str) {
        if (str == null) {
            throw new NullPointerException();
        }
        try {
            return cn.hg.aifei.tools.xml.XmlUtil.escape(str);
        } catch (NoClassDefFoundError e) {
            throw new IllegalStateException(MISSING_TOOLS, e);
        }
    }
}
