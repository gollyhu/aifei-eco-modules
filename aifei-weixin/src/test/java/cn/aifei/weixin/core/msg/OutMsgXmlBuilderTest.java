/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.core.msg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import cn.aifei.weixin.core.kit.XmlKit;
import org.junit.Test;

/**
 * 被动回复渲染（S2 验证标准 ②⑦）
 */
public class OutMsgXmlBuilderTest {

    @Test
    public void rendersTextReplyInExactOrder() {
        OutTextMsg msg = new OutTextMsg("你好");
        msg.setToUserName("openid_user");
        msg.setFromUserName("gh_official");
        msg.setCreateTime(1600000001L);

        assertEquals("<xml><ToUserName>openid_user</ToUserName><FromUserName>gh_official</FromUserName>"
                        + "<CreateTime>1600000001</CreateTime><MsgType>text</MsgType><Content>你好</Content></xml>",
                OutMsgXmlBuilder.build(msg));
    }

    @Test
    public void rendersNestedMediaAndNewsReplies() {
        OutImageMsg image = new OutImageMsg("MEDIA");
        image.setToUserName("u");
        image.setFromUserName("g");
        image.setCreateTime(1L);
        assertEquals("<xml><ToUserName>u</ToUserName><FromUserName>g</FromUserName><CreateTime>1</CreateTime>"
                + "<MsgType>image</MsgType><Image><MediaId>MEDIA</MediaId></Image></xml>",
                OutMsgXmlBuilder.build(image));

        OutNewsMsg news = new OutNewsMsg();
        news.setToUserName("u");
        news.setFromUserName("g");
        news.setCreateTime(1L);
        news.addArticle("t1", "d1", "p1", "u1").addArticle("t2", "d2", "p2", "u2");

        String xml = OutMsgXmlBuilder.build(news);
        assertEquals("<xml><ToUserName>u</ToUserName><FromUserName>g</FromUserName><CreateTime>1</CreateTime>"
                + "<MsgType>news</MsgType><ArticleCount>2</ArticleCount><Articles>"
                + "<item><Title>t1</Title><Description>d1</Description><PicUrl>p1</PicUrl><Url>u1</Url></item>"
                + "<item><Title>t2</Title><Description>d2</Description><PicUrl>p2</PicUrl><Url>u2</Url></item>"
                + "</Articles></xml>", xml);
    }

    @Test
    public void rendersMusicAndVideoReplies() {
        OutMusicMsg music = new OutMusicMsg();
        music.setToUserName("u");
        music.setFromUserName("g");
        music.setCreateTime(1L);
        music.setTitle("t");
        music.setDescription("d");
        music.setMusicUrl("http://m");
        music.setHqMusicUrl("http://hq");
        music.setThumbMediaId("thumb");
        String musicXml = OutMsgXmlBuilder.build(music);
        assertTrue(musicXml.contains("<Music><Title>t</Title><Description>d</Description>"
                + "<MusicUrl>http://m</MusicUrl><HQMusicUrl>http://hq</HQMusicUrl>"
                + "<ThumbMediaId>thumb</ThumbMediaId></Music>"));

        OutVideoMsg video = new OutVideoMsg("MV");
        video.setToUserName("u");
        video.setFromUserName("g");
        video.setCreateTime(1L);
        video.setTitle("vt");
        video.setDescription("vd");
        assertTrue(OutMsgXmlBuilder.build(video).contains(
                "<Video><MediaId>MV</MediaId><Title>vt</Title><Description>vd</Description></Video>"));

        OutVoiceMsg voice = new OutVoiceMsg("VV");
        voice.setToUserName("u");
        voice.setFromUserName("g");
        voice.setCreateTime(1L);
        assertTrue(OutMsgXmlBuilder.build(voice).contains("<Voice><MediaId>VV</MediaId></Voice>"));
    }

    /**
     * S2 验证标准 ⑦：业务文本含 & < > " ' 与 ]]> 时被正确转义，且渲染结果仍是合法 XML
     */
    @Test
    public void escapesInjectionPayload() {
        OutTextMsg msg = new OutTextMsg("a & b <c> \"d\" 'e' ]]>");
        msg.setToUserName("u");
        msg.setFromUserName("g");
        msg.setCreateTime(1L);

        String xml = OutMsgXmlBuilder.build(msg);
        assertFalse("不得出现未转义的 ]]>", xml.contains("]]>"));
        assertTrue(xml.contains("a &amp; b &lt;c&gt; &quot;d&quot; &apos;e&apos; ]]&gt;"));

        Map<String, String> parsed = XmlKit.parse(xml);
        assertEquals("a & b <c> \"d\" 'e' ]]>", parsed.get("Content"));
    }
}
