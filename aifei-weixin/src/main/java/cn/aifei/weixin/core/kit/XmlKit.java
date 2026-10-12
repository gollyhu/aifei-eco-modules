/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.core.kit;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * 安全 XML 解析（零依赖，仅用 JDK DOM）。
 *
 * <pre>
 * 安全约束（XXE 防护，jfinal-weixin 曾为此发布 NO-XXE 分支）：
 *  1: 禁止 DOCTYPE 声明（disallow-doctype-decl），命中即抛异常
 *  2: 禁止外部普通实体与外部参数实体
 *  3: 禁止加载外部 DTD，禁止 XInclude，禁止实体展开
 * </pre>
 */
public class XmlKit {

    /**
     * 解析微信 XML 报文，返回根节点下「直接子节点名 → 文本」的有序映射。
     * 节点不存在时不出现在 map 中；XML 非法（含 DOCTYPE）时抛 IllegalArgumentException。
     */
    public static Map<String, String> parse(String xml) {
        if (xml == null || xml.trim().isEmpty()) {
            throw new IllegalArgumentException("XML 内容为空");
        }

        Document document;
        try {
            DocumentBuilder builder = newDocumentBuilderFactory().newDocumentBuilder();
            builder.setErrorHandler(null);
            document = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalArgumentException("XML 解析失败", e);
        }

        Element root = document.getDocumentElement();
        if (root == null) {
            throw new IllegalArgumentException("XML 缺少根节点");
        }

        Map<String, String> ret = new LinkedHashMap<>();
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String name = node.getNodeName();
            if (!ret.containsKey(name)) {     // 同名节点保留首个
                ret.put(name, node.getTextContent());
            }
        }
        return ret;
    }

    private static DocumentBuilderFactory newDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("XML 解析器不支持 XXE 防护特性", e);
        }
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (IllegalArgumentException ignore) {
            // 老版本 JDK 未实现这两个属性，前面的实体禁用在 JDK 8+ 已足够
        }
        return factory;
    }

    private XmlKit() {
    }
}
