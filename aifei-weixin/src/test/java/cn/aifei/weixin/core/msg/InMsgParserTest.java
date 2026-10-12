/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.core.msg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 接收消息 / 事件解析（S2 验证标准 ①⑤）：8 类消息 + 10 类事件 + 未知类型
 */
public class InMsgParserTest {

    @Test
    public void parsesTextMessageWithCdataAndEntity() {
        InMsg msg = InMsgParser.parse(message("text",
                "<Content>a &amp; b &lt;c&gt;</Content><MsgId>1234567890123456</MsgId>"));

        assertTrue(msg instanceof InTextMsg);
        InTextMsg text = (InTextMsg) msg;
        assertEquals("gh_official", text.getToUserName());
        assertEquals("openid_user", text.getFromUserName());
        assertEquals(1600000000L, text.getCreateTime());
        assertEquals("text", text.getMsgType());
        assertEquals(1234567890123456L, text.getMsgId());
        assertEquals("a & b <c>", text.getContent());
    }

    @Test
    public void parsesImageVoiceVideoShortVideo() {
        InMsg image = InMsgParser.parse(message("image",
                "<PicUrl><![CDATA[http://pic]]></PicUrl><MediaId><![CDATA[media-image]]></MediaId>"));
        assertTrue(image instanceof InImageMsg);
        assertEquals("http://pic", ((InImageMsg) image).getPicUrl());
        assertEquals("media-image", ((InImageMsg) image).getMediaId());

        InMsg voice = InMsgParser.parse(message("voice",
                "<MediaId><![CDATA[media-voice]]></MediaId><Format><![CDATA[amr]]></Format><MediaId16K><![CDATA[m16k]]></MediaId16K>"));
        assertTrue(voice instanceof InVoiceMsg);
        assertEquals("amr", ((InVoiceMsg) voice).getFormat());
        assertEquals("m16k", ((InVoiceMsg) voice).getMediaId16K());

        InMsg video = InMsgParser.parse(message("video",
                "<MediaId><![CDATA[mv]]></MediaId><ThumbMediaId><![CDATA[mt]]></ThumbMediaId>"));
        assertTrue(video instanceof InVideoMsg);
        assertEquals("mt", ((InVideoMsg) video).getThumbMediaId());

        InMsg shortVideo = InMsgParser.parse(message("shortvideo",
                "<MediaId><![CDATA[ms]]></MediaId><ThumbMediaId><![CDATA[mst]]></ThumbMediaId>"));
        assertTrue(shortVideo instanceof InShortVideoMsg);
        assertEquals("mst", ((InShortVideoMsg) shortVideo).getThumbMediaId());
    }

    @Test
    public void parsesLocationLinkAndSpeechRecognition() {
        InMsg location = InMsgParser.parse(message("location",
                "<Location_X>23.137466</Location_X><Location_Y>113.352425</Location_Y>"
                        + "<Scale>119.385040</Scale><Label><![CDATA[广州]]></Label>"));
        assertTrue(location instanceof InLocationMsg);
        assertEquals("23.137466", ((InLocationMsg) location).getLocationX());
        assertEquals("113.352425", ((InLocationMsg) location).getLocationY());
        assertEquals("119.385040", ((InLocationMsg) location).getScale());
        assertEquals("广州", ((InLocationMsg) location).getLabel());

        InMsg link = InMsgParser.parse(message("link",
                "<Title><![CDATA[t]]></Title><Description><![CDATA[d]]></Description><Url><![CDATA[http://u]]></Url>"));
        assertTrue(link instanceof InLinkMsg);
        assertEquals("t", ((InLinkMsg) link).getTitle());
        assertEquals("d", ((InLinkMsg) link).getDescription());
        assertEquals("http://u", ((InLinkMsg) link).getUrl());

        InMsg speech = InMsgParser.parse(message("speech_recognition",
                "<MediaId><![CDATA[mr]]></MediaId><Format><![CDATA[amr]]></Format><Recognition><![CDATA[你好世界]]></Recognition>"));
        assertTrue(speech instanceof InSpeechRecognitionMsg);
        assertEquals("你好世界", ((InSpeechRecognitionMsg) speech).getRecognition());
    }

    @Test
    public void parsesAllEventTypes() {
        InMsg subscribe = InMsgParser.parse(event("subscribe",
                "<EventKey><![CDATA[qrscene_123]]></EventKey><Ticket><![CDATA[ticket-1]]></Ticket>"));
        assertTrue(subscribe instanceof InFollowEvent);
        assertEquals("qrscene_123", ((InFollowEvent) subscribe).getEventKey());
        assertEquals("ticket-1", ((InFollowEvent) subscribe).getTicket());

        assertTrue(InMsgParser.parse(event("unsubscribe", null)) instanceof InUnFollowEvent);

        InMsg scan = InMsgParser.parse(event("SCAN",
                "<EventKey><![CDATA[123]]></EventKey><Ticket><![CDATA[ticket-2]]></Ticket>"));
        assertTrue(scan instanceof InQrCodeEvent);
        assertEquals("ticket-2", ((InQrCodeEvent) scan).getTicket());

        InMsg eventLocation = InMsgParser.parse(event("LOCATION",
                "<Latitude>23.137466</Latitude><Longitude>113.352425</Longitude><Precision>119.385040</Precision>"));
        assertTrue(eventLocation instanceof InLocationEvent);
        assertEquals("23.137466", ((InLocationEvent) eventLocation).getLatitude());
        assertEquals("119.385040", ((InLocationEvent) eventLocation).getPrecision());

        InMsg click = InMsgParser.parse(event("CLICK",
                "<EventKey><![CDATA[KEY]]></EventKey><MenuId>123</MenuId>"));
        assertTrue(click instanceof InMenuEvent);
        assertEquals("KEY", ((InMenuEvent) click).getEventKey());
        assertEquals("123", ((InMenuEvent) click).getMenuId());

        assertTrue(InMsgParser.parse(event("VIEW", "<EventKey><![CDATA[http://aifei.cn]]></EventKey>")) instanceof InMenuEvent);

        InMsg template = InMsgParser.parse(event("TEMPLATESENDJOBFINISH",
                "<MsgID>987654321</MsgID><Status><![CDATA[success]]></Status>"));
        assertTrue(template instanceof InTemplateEvent);
        assertEquals("987654321", ((InTemplateEvent) template).getMessageId());
        assertEquals("success", ((InTemplateEvent) template).getStatus());

        InMsg mass = InMsgParser.parse(event("MASSSENDJOBFINISH",
                "<MsgID>888</MsgID><Status><![CDATA[sending]]></Status><TotalCount>100</TotalCount>"
                        + "<FilterCount>95</FilterCount><SentCount>90</SentCount><ErrorCount>5</ErrorCount>"));
        assertTrue(mass instanceof InMassEvent);
        assertEquals(100, ((InMassEvent) mass).getTotalCount());
        assertEquals(95, ((InMassEvent) mass).getFilterCount());
        assertEquals(90, ((InMassEvent) mass).getSentCount());
        assertEquals(5, ((InMassEvent) mass).getErrorCount());

        InMsg custom = InMsgParser.parse(event("kf_create_session",
                "<FromKfAccount><![CDATA[kf1]]></FromKfAccount><ToKfAccount><![CDATA[kf2]]></ToKfAccount>"));
        assertTrue(custom instanceof InCustomEvent);
        assertEquals("kf1", ((InCustomEvent) custom).getFromKfAccount());
        assertEquals("kf2", ((InCustomEvent) custom).getToKfAccount());

        assertTrue(InMsgParser.parse(event("qualification_verify_success", null)) instanceof InVerifyEvent);
        assertTrue(InMsgParser.parse(event("naming_verify_fail", null)) instanceof InVerifyEvent);
    }

    @Test
    public void returnsNullForUnknownMsgTypeOrEvent() {
        assertNull(InMsgParser.parse(message("unknown_type", "<Content>x</Content>")));
        assertNull(InMsgParser.parse(event("NOT_A_REAL_EVENT", null)));
        assertNull(InMsgParser.parse("<xml><Foo>bar</Foo></xml>"));
    }

    private static String message(String msgType, String extra) {
        return "<xml>"
                + "<ToUserName><![CDATA[gh_official]]></ToUserName>"
                + "<FromUserName><![CDATA[openid_user]]></FromUserName>"
                + "<CreateTime>1600000000</CreateTime>"
                + "<MsgType><![CDATA[" + msgType + "]]></MsgType>"
                + extra
                + "</xml>";
    }

    private static String event(String event, String extra) {
        return "<xml>"
                + "<ToUserName><![CDATA[gh_official]]></ToUserName>"
                + "<FromUserName><![CDATA[openid_user]]></FromUserName>"
                + "<CreateTime>1600000000</CreateTime>"
                + "<MsgType><![CDATA[event]]></MsgType>"
                + "<Event><![CDATA[" + event + "]]></Event>"
                + (extra == null ? "" : extra)
                + "</xml>";
    }
}
