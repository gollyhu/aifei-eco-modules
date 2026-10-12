/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin;

/**
 * 微信协议常量。
 *
 * <p>官方文档：https://developers.weixin.qq.com/doc/offiaccount/Message_Management/Receiving_standard_messages.html
 */
public class WxConsts {

    /** 无被动回复时的响应体（空串会被微信判定超时并重试 3 次） */
    public static final String RESP_SUCCESS = "success";

    /** 验签失败等非法请求的响应体 */
    public static final String RESP_FAIL = "fail";

    /** 43 字符 EncodingAESKey 的长度 */
    public static final int ENCODING_AES_KEY_LENGTH = 43;

    /** 消息类型（MsgType） */
    public static class MsgType {
        public static final String TEXT = "text";
        public static final String IMAGE = "image";
        public static final String VOICE = "voice";
        public static final String VIDEO = "video";
        public static final String SHORT_VIDEO = "shortvideo";
        public static final String LOCATION = "location";
        public static final String LINK = "link";
        /** 语音识别结果（Recognition） */
        public static final String SPEECH_RECOGNITION = "speech_recognition";
        /** 事件消息的 MsgType 恒为 event，真实类型看 Event 字段 */
        public static final String EVENT = "event";

        private MsgType() {
        }
    }

    /** 回复消息类型（被动回复的 MsgType） */
    public static class ReplyType {
        public static final String TEXT = "text";
        public static final String IMAGE = "image";
        public static final String VOICE = "voice";
        public static final String VIDEO = "video";
        public static final String MUSIC = "music";
        public static final String NEWS = "news";

        private ReplyType() {
        }
    }

    /**
     * 事件类型（Event）。
     *
     * <p>10 类事件 = 8 个单值事件 + 2 个事件族（客服会话 kf_*、微信认证 *_verify_*），
     * 对应 MsgHandler 的 9 个事件钩子（CLICK 与 VIEW 共用菜单钩子）。
     */
    public static class EventType {
        public static final String SUBSCRIBE = "subscribe";
        public static final String UNSUBSCRIBE = "unsubscribe";
        public static final String SCAN = "SCAN";
        public static final String LOCATION = "LOCATION";
        public static final String CLICK = "CLICK";
        public static final String VIEW = "VIEW";
        /** 模板消息送达结果 */
        public static final String TEMPLATE_SEND_FINISH = "TEMPLATESENDJOBFINISH";
        /** 群发结果 */
        public static final String MASS_SEND_FINISH = "MASSSENDJOBFINISH";

        /** 客服会话事件：kf_create_session / kf_close_session / kf_switch_session */
        public static final String[] CUSTOM = {"kf_create_session", "kf_close_session", "kf_switch_session"};

        /** 微信认证事件：qualification_verify_success/fail、naming_verify_success/fail */
        public static final String[] VERIFY = {
                "qualification_verify_success", "qualification_verify_fail",
                "naming_verify_success", "naming_verify_fail"
        };

        private EventType() {
        }
    }

    /** 请求参数 encrypt_type 的取值 */
    public static class EncryptType {
        /** 安全模式（消息加密） */
        public static final String AES = "aes";

        private EncryptType() {
        }
    }

    /**
     * event 参数是否属于客服会话事件族
     */
    public static boolean isCustomEvent(String event) {
        return contains(EventType.CUSTOM, event);
    }

    /**
     * event 参数是否属于微信认证事件族
     */
    public static boolean isVerifyEvent(String event) {
        return contains(EventType.VERIFY, event);
    }

    private static boolean contains(String[] candidates, String value) {
        if (value == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (candidate.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private WxConsts() {
    }
}
