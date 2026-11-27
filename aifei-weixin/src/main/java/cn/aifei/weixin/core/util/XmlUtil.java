/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.core.util;

import cn.hg.aifei.tools.xml.XmlException;
import java.util.Map;

/**
 * 安全 XML 解析（委托 aifei-tools xml，XXE 硬化与遍历规则由 tools 唯一定义）。
 *
 * <pre>
 * 安全约束（XXE 防护，jfinal-weixin 曾为此发布 NO-XXE 分支）：
 *  1: 禁止 DOCTYPE 声明（disallow-doctype-decl），命中即抛异常
 *  2: 禁止外部普通实体与外部参数实体
 *  3: 禁止加载外部 DTD，禁止 XInclude，禁止实体展开
 * </pre>
 */
public class XmlUtil {

    private static final String MISSING_TOOLS =
            "缺少可选依赖 io.github.gollyhu:aifei-tools:1.1.0-SNAPSHOT；使用本能力时消费方须显式声明";

    /**
     * 解析微信 XML 报文，返回根节点下「直接子节点名 → 文本」的有序映射。
     * 节点不存在时不出现在 map 中；XML 非法（含 DOCTYPE）时抛 IllegalArgumentException。
     */
    public static Map<String, String> parse(String xml) {
        return parseViaTools(xml);
    }

    private static Map<String, String> parseViaTools(String xml) {
        try {
            return XmlHolder.parse(xml);
        } catch (NoClassDefFoundError e) {
            throw new IllegalStateException(MISSING_TOOLS, e);
        }
    }

    /**
     * 隔离 tools 异常类型引用；按消息前缀将 tools 异常逐字映射回来源模块既有异常口径。
     */
    private static final class XmlHolder {
        private static Map<String, String> parse(String xml) {
            try {
                return cn.hg.aifei.tools.xml.XmlUtil.parse(xml);
            } catch (XmlException xe) {
                String message = xe.getMessage() == null ? "" : xe.getMessage();
                if (message.contains("content must not be null or empty")) {
                    throw new IllegalArgumentException("XML 内容为空");
                }
                if (message.contains("failed to parse content")) {
                    throw new IllegalArgumentException("XML 解析失败", xe.getCause());
                }
                if (message.contains("missing root element")) {
                    throw new IllegalArgumentException("XML 缺少根节点");
                }
                if (message.contains("XXE protection feature not supported")) {
                    throw new IllegalStateException("XML 解析器不支持 XXE 防护特性", xe.getCause());
                }
                throw new IllegalArgumentException("XML 解析失败", xe);
            }
        }
    }

    private XmlUtil() {
    }
}
