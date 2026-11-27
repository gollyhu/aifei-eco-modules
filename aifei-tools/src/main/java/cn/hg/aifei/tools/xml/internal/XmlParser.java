/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.xml.internal;

import java.io.IOException;
import java.io.StringReader;
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
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * XXE 硬化与 DOM 解析的唯一定义点；每次解析新建解析器实例，不缓存共享对象。
 */
public final class XmlParser {

    private XmlParser() {
    }

    /**
     * 解析 XML 并按文档顺序取根节点的直接子元素。
     *
     * @param xml 待解析文本，不得为 null 或全空白
     * @return 保序的元素名到文本内容映射，同名保留首个
     */
    public static Map<String, String> parse(String xml) {
        if (xml == null || xml.trim().isEmpty()) {
            throw XmlErrors.nullOrEmptyContent();
        }
        DocumentBuilder builder = newBuilder();
        try {
            Document document = builder.parse(new InputSource(new StringReader(xml)));
            Element root = document.getDocumentElement();
            if (root == null) {
                throw XmlErrors.missingRootElement();
            }
            Map<String, String> result = new LinkedHashMap<String, String>();
            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                String name = node.getNodeName();
                if (!result.containsKey(name)) {
                    result.put(name, node.getTextContent());
                }
            }
            return result;
        } catch (SAXException e) {
            throw XmlErrors.parseFailed(e);
        } catch (IOException e) {
            throw XmlErrors.parseFailed(e);
        }
    }

    private static DocumentBuilder newBuilder() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            try {
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            } catch (IllegalArgumentException ignored) {
                // 老 JDK 不支持的 XMLConstants 属性忽略，前六项在 JDK 8+ 已足够
            }
            return factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw XmlErrors.xxeFeatureNotSupported(e);
        }
    }
}
