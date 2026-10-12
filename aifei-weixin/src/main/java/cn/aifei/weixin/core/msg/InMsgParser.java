/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.core.msg;

import cn.aifei.weixin.WxConsts;
import cn.aifei.weixin.core.kit.XmlKit;
import java.util.Map;

/**
 * 接收消息 / 事件 XML 解析器。
 *
 * <p>官方文档：https://developers.weixin.qq.com/doc/offiaccount/Message_Management/Receiving_standard_messages.html
 */
public class InMsgParser {

    /**
     * 解析明文 XML。未知 MsgType 返回 null（由 MsgKit 响应 success），
     * XML 非法时抛 IllegalArgumentException。
     */
    public static InMsg parse(String xml) {
        Map<String, String> data = XmlKit.parse(xml);
        String msgType = data.get("MsgType");
        if (msgType == null) {
            return null;
        }

        InMsg msg;
        if (WxConsts.MsgType.EVENT.equals(msgType)) {
            msg = parseEvent(data);
        } else {
            msg = parseMessage(msgType, data);
        }
        if (msg == null) {
            return null;
        }

        msg.setToUserName(data.get("ToUserName"));
        msg.setFromUserName(data.get("FromUserName"));
        msg.setCreateTime(parseLong(data.get("CreateTime")));
        msg.setMsgId(parseLong(data.get("MsgId")));
        msg.setMsgType(msgType);
        return msg;
    }

    private static InMsg parseMessage(String msgType, Map<String, String> data) {
        switch (msgType) {
            case WxConsts.MsgType.TEXT: {
                InTextMsg msg = new InTextMsg();
                msg.setContent(data.get("Content"));
                return msg;
            }
            case WxConsts.MsgType.IMAGE: {
                InImageMsg msg = new InImageMsg();
                msg.setPicUrl(data.get("PicUrl"));
                msg.setMediaId(data.get("MediaId"));
                return msg;
            }
            case WxConsts.MsgType.VOICE: {
                InVoiceMsg msg = new InVoiceMsg();
                msg.setMediaId(data.get("MediaId"));
                msg.setFormat(data.get("Format"));
                msg.setMediaId16K(data.get("MediaId16K"));
                return msg;
            }
            case WxConsts.MsgType.VIDEO: {
                InVideoMsg msg = new InVideoMsg();
                msg.setMediaId(data.get("MediaId"));
                msg.setThumbMediaId(data.get("ThumbMediaId"));
                return msg;
            }
            case WxConsts.MsgType.SHORT_VIDEO: {
                InShortVideoMsg msg = new InShortVideoMsg();
                msg.setMediaId(data.get("MediaId"));
                msg.setThumbMediaId(data.get("ThumbMediaId"));
                return msg;
            }
            case WxConsts.MsgType.LOCATION: {
                InLocationMsg msg = new InLocationMsg();
                msg.setLocationX(data.get("Location_X"));
                msg.setLocationY(data.get("Location_Y"));
                msg.setScale(data.get("Scale"));
                msg.setLabel(data.get("Label"));
                return msg;
            }
            case WxConsts.MsgType.LINK: {
                InLinkMsg msg = new InLinkMsg();
                msg.setTitle(data.get("Title"));
                msg.setDescription(data.get("Description"));
                msg.setUrl(data.get("Url"));
                return msg;
            }
            case WxConsts.MsgType.SPEECH_RECOGNITION: {
                InSpeechRecognitionMsg msg = new InSpeechRecognitionMsg();
                msg.setMediaId(data.get("MediaId"));
                msg.setFormat(data.get("Format"));
                msg.setRecognition(data.get("Recognition"));
                return msg;
            }
            default:
                return null;
        }
    }

    private static InEvent parseEvent(Map<String, String> data) {
        String event = data.get("Event");
        if (event == null) {
            return null;
        }

        InEvent ret;
        switch (event) {
            case WxConsts.EventType.SUBSCRIBE: {
                InFollowEvent e = new InFollowEvent();
                e.setTicket(data.get("Ticket"));
                ret = e;
                break;
            }
            case WxConsts.EventType.UNSUBSCRIBE:
                ret = new InUnFollowEvent();
                break;
            case WxConsts.EventType.SCAN: {
                InQrCodeEvent e = new InQrCodeEvent();
                e.setTicket(data.get("Ticket"));
                ret = e;
                break;
            }
            case WxConsts.EventType.LOCATION: {
                InLocationEvent e = new InLocationEvent();
                e.setLatitude(data.get("Latitude"));
                e.setLongitude(data.get("Longitude"));
                e.setPrecision(data.get("Precision"));
                ret = e;
                break;
            }
            case WxConsts.EventType.CLICK:
            case WxConsts.EventType.VIEW: {
                InMenuEvent e = new InMenuEvent();
                e.setMenuId(data.get("MenuId"));
                ret = e;
                break;
            }
            case WxConsts.EventType.TEMPLATE_SEND_FINISH: {
                InTemplateEvent e = new InTemplateEvent();
                e.setMessageId(data.get("MsgID"));
                e.setStatus(data.get("Status"));
                ret = e;
                break;
            }
            case WxConsts.EventType.MASS_SEND_FINISH: {
                InMassEvent e = new InMassEvent();
                e.setMessageId(data.get("MsgID"));
                e.setStatus(data.get("Status"));
                e.setTotalCount(parseInt(data.get("TotalCount")));
                e.setFilterCount(parseInt(data.get("FilterCount")));
                e.setSentCount(parseInt(data.get("SentCount")));
                e.setErrorCount(parseInt(data.get("ErrorCount")));
                ret = e;
                break;
            }
            default:
                if (WxConsts.isCustomEvent(event)) {
                    InCustomEvent e = new InCustomEvent();
                    e.setFromKfAccount(data.get("FromKfAccount"));
                    e.setToKfAccount(data.get("ToKfAccount"));
                    ret = e;
                } else if (WxConsts.isVerifyEvent(event)) {
                    ret = new InVerifyEvent();
                } else {
                    return null;
                }
        }

        ret.setEvent(event);
        ret.setEventKey(data.get("EventKey"));
        return ret;
    }

    private static long parseLong(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static int parseInt(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private InMsgParser() {
    }
}
