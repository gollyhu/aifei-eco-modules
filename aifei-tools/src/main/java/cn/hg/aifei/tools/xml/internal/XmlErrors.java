/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.xml.internal;

import cn.hg.aifei.tools.xml.XmlException;

/**
 * xml 异常消息与异常构造的唯一定义点。
 */
public final class XmlErrors {

    private XmlErrors() {
    }

    /**
     * 待解析内容为 null 或全空白。
     *
     * @return 统一异常
     */
    public static XmlException nullOrEmptyContent() {
        return new XmlException("xml: content must not be null or empty");
    }

    /**
     * 文档解析失败，含文档类型声明命中与外部实体解析失败。
     *
     * @param cause 根因
     * @return 统一异常
     */
    public static XmlException parseFailed(Throwable cause) {
        return new XmlException("xml: failed to parse content, cause: " + detail(cause), cause);
    }

    /**
     * 文档缺少根节点。
     *
     * @return 统一异常
     */
    public static XmlException missingRootElement() {
        return new XmlException("xml: missing root element");
    }

    /**
     * 底层解析器不支持硬化特性。
     *
     * @param cause 根因
     * @return 统一异常
     */
    public static XmlException xxeFeatureNotSupported(Throwable cause) {
        return new XmlException("xml: XXE protection feature not supported, cause: "
                + detail(cause), cause);
    }

    /**
     * 待转义文本为 null。
     *
     * @return 统一异常
     */
    public static XmlException nullText() {
        return new XmlException("xml: text must not be null");
    }

    private static String detail(Throwable cause) {
        return cause == null ? "unknown" : String.valueOf(cause.getMessage());
    }
}
