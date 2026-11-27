/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.xml;

import cn.hg.aifei.tools.xml.internal.XmlErrors;
import cn.hg.aifei.tools.xml.internal.XmlParser;

import java.util.Map;

/**
 * XXE 安全解析与文本实体转义的无状态工具，全部方法均为静态方法。
 *
 * <p>解析结果为保序映射（JDK 自带实现），只取根节点的直接子元素；
 * 转义为纯函数。失败统一抛 {@link XmlException}，公共方法不声明受检异常。
 * 两类方法均可多线程并发调用。</p>
 */
public final class XmlUtil {

    private XmlUtil() {
    }

    /**
     * 以禁用外部实体的方式解析 XML，取根节点直接子元素名到文本内容的映射。
     *
     * <p>返回保序映射，同名子元素只保留首个；文本、注释等非元素节点跳过。
     * 文档类型声明会被拒绝而不展开；节点名不写死任何协议约定。</p>
     *
     * @param xml 待解析的 XML 文本，不得为 null 或全空白
     * @return 保序的元素名到文本内容映射，根节点无子元素时为空映射
     * @throws XmlException 内容为 null 或空白、解析失败、缺少根节点，或硬化特性不可用
     */
    public static Map<String, String> parse(String xml) {
        return XmlParser.parse(xml);
    }

    /**
     * 转义文本节点中的五个实体字符：与号、小于号、大于号、双引号与单引号。
     *
     * <p>逐字符单次替换，与号优先处理，不会造成二次转义；其余字符原样保留。</p>
     *
     * @param text 待转义文本，不得为 null，空串合法
     * @return 转义后的文本，入参为空串时返回空串
     * @throws XmlException text 为 null
     */
    public static String escape(String text) {
        if (text == null) {
            throw XmlErrors.nullText();
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&':
                    out.append("&amp;");
                    break;
                case '<':
                    out.append("&lt;");
                    break;
                case '>':
                    out.append("&gt;");
                    break;
                case '"':
                    out.append("&quot;");
                    break;
                case '\'':
                    out.append("&apos;");
                    break;
                default:
                    out.append(c);
            }
        }
        return out.toString();
    }
}
