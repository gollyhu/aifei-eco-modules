/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.xml;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * xml 解析与转义测试（设计 §11.3.3，测试策略 §3.9）：保序、同名取首、非元素节点跳过、
 * DOCTYPE/外部实体拒绝、null/空语义、五实体转义与并发无串扰。样本均为内嵌字符串，零联网。
 */
public class XmlUtilTest {

    @Test
    public void parse_directChildrenOrdered() throws Exception {
        String xml = "<root><b>2</b><a>1</a><c>3</c></root>";
        Map<String, String> actual = XmlUtil.parse(xml);
        assertTrue("返回类型须为 LinkedHashMap", actual instanceof LinkedHashMap);
        assertEquals(Arrays.asList("b", "a", "c"), new ArrayList<String>(actual.keySet()));
        assertEquals("2", actual.get("b"));
        assertEquals("1", actual.get("a"));
        assertEquals("3", actual.get("c"));

        // 并发解析不同文档，验证每次新建解析器、结果无串扰
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<String>> futures = new ArrayList<Future<String>>();
        try {
            for (int i = 0; i < 60; i++) {
                final int index = i;
                futures.add(pool.submit(new Callable<String>() {
                    public String call() {
                        Map<String, String> parsed = XmlUtil.parse(
                                "<root><item>item-" + index + "</item></root>");
                        return parsed.get("item");
                    }
                }));
            }
            for (int i = 0; i < 60; i++) {
                assertEquals("并发解析串扰: " + i, "item-" + i, futures.get(i).get());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void parse_duplicateNameKeepsFirst() {
        Map<String, String> actual = XmlUtil.parse("<root><k>first</k><k>second</k></root>");
        assertEquals(1, actual.size());
        assertEquals("first", actual.get("k"));
    }

    @Test
    public void parse_nonElementNodesSkipped() {
        Map<String, String> actual = XmlUtil.parse(
                "<root> leading<!-- a comment --><a>A</a> trailing</root>");
        assertEquals(1, actual.size());
        assertEquals("A", actual.get("a"));
    }

    @Test
    public void parse_nullOrEmpty_readableException() {
        assertXmlException("xml: content must not be null or empty", null);
        assertXmlException("xml: content must not be null or empty", "");
        assertXmlException("xml: content must not be null or empty", "  \t\n  ");
    }

    @Test
    public void parse_doctypeRejected() {
        String xml = "<!DOCTYPE root [<!ENTITY secret \"EXPANDED_SECRET\">]>"
                + "<root><a>&secret;</a></root>";
        try {
            XmlUtil.parse(xml);
            fail("DOCTYPE 输入须抛异常，不得展开");
        } catch (XmlException e) {
            assertTrue("须为解析失败消息: " + e.getMessage(),
                    e.getMessage().startsWith("xml: failed to parse content, cause: "));
            assertFalse("不得展开被声明的实体内容", e.getMessage().contains("EXPANDED_SECRET"));
            assertNotNull("须保留底层根因", e.getCause());
        }
    }

    @Test
    public void parse_externalEntityNotExpanded() throws Exception {
        File temp = File.createTempFile("aifei-xml-xxe-", ".txt");
        try {
            FileOutputStream out = new FileOutputStream(temp);
            try {
                out.write("SECRET_MARKER".getBytes(StandardCharsets.UTF_8));
            } finally {
                out.close();
            }
            String uri = temp.toURI().toString();
            String[] samples = {
                    "<!DOCTYPE root [<!ENTITY xxe SYSTEM \"" + uri + "\">]>"
                            + "<root><a>&xxe;</a></root>",
                    "<!DOCTYPE root [<!ENTITY % ext SYSTEM \"" + uri + "\">%ext;]>"
                            + "<root><a>ok</a></root>"
            };
            for (String xml : samples) {
                try {
                    XmlUtil.parse(xml);
                    fail("外部实体输入须抛异常，不得读取文件内容");
                } catch (XmlException e) {
                    assertTrue("须为解析失败消息: " + e.getMessage(),
                            e.getMessage().startsWith("xml: failed to parse content, cause: "));
                    assertFalse("不得读取外部实体内容", e.getMessage().contains("SECRET_MARKER"));
                    assertNotNull("须保留底层根因", e.getCause());
                }
            }
        } finally {
            if (!temp.delete() && temp.exists()) {
                temp.deleteOnExit();
            }
        }
    }

    @Test
    public void escape_fiveEntities() {
        assertEquals("&amp;&lt;&gt;&quot;&apos;", XmlUtil.escape("&<>\"'"));
        assertEquals("", XmlUtil.escape(""));
        assertEquals("普通文本 a1 中文 原样保留", XmlUtil.escape("普通文本 a1 中文 原样保留"));
        try {
            XmlUtil.escape(null);
            fail("null 应抛 XmlException");
        } catch (XmlException e) {
            assertEquals("xml: text must not be null", e.getMessage());
        }
    }

    @Test
    public void escape_noDoubleEscaping() {
        assertEquals("&amp;amp;", XmlUtil.escape("&amp;"));
        assertEquals("&amp;lt;", XmlUtil.escape("&lt;"));
    }

    private static void assertXmlException(String expected, String xml) {
        try {
            XmlUtil.parse(xml);
            fail("应抛 XmlException: " + expected);
        } catch (XmlException e) {
            assertEquals(expected, e.getMessage());
        }
    }
}
