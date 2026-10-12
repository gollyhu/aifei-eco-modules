/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.core.kit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Map;
import org.junit.Test;

/**
 * 安全 XML 解析（S1 验证标准 ①）：XXE 回归 + CDATA / 实体 / 空节点
 */
public class XmlKitTest {

    @Test
    public void parsesCdataEntityAndEmptyNode() {
        String xml = "<xml><ToUserName><![CDATA[gh_1]]></ToUserName>"
                + "<Content>a &amp; b</Content><Empty></Empty><Nested><A>1</A></Nested></xml>";

        Map<String, String> data = XmlKit.parse(xml);
        assertEquals("gh_1", data.get("ToUserName"));
        assertEquals("a & b", data.get("Content"));
        assertEquals("", data.get("Empty"));
        assertEquals("1", data.get("Nested"));
    }

    @Test
    public void rejectsDoctypeToBlockXxe() {
        String xml = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE x [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + "<xml><Content>&xxe;</Content></xml>";
        try {
            XmlKit.parse(xml);
            fail("含 DOCTYPE / 外部实体的 XML 必须被拒绝");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("XML 解析失败"));
        }
    }

    @Test
    public void rejectsBlankAndMalformedInput() {
        try {
            XmlKit.parse("");
            fail("空内容必须抛异常");
        } catch (IllegalArgumentException expected) {
            // 预期
        }
        try {
            XmlKit.parse("<xml><a></xml>");
            fail("非法 XML 必须抛异常");
        } catch (IllegalArgumentException expected) {
            // 预期
        }
    }

    @Test
    public void xmlBuilderEscapesEverything() {
        assertEquals("a &amp; b &lt;c&gt; &quot;d&quot; &apos;e&apos;",
                XmlBuilder.escape("a & b <c> \"d\" 'e'"));
        assertEquals("]]&gt;", XmlBuilder.escape("]]>"));
        assertEquals("<xml><A><![CDATA[]]]]><![CDATA[>]]></A></xml>", new XmlBuilder().cdata("A", "]]>").build());
        assertEquals("<xml><A>x</A></xml>", new XmlBuilder().text("A", "x").build());
        assertEquals("<xml><A>1</A><B>2</B></xml>", new XmlBuilder().text("A", "1").text("B", 2).build());
    }
}
