/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.core.msg;

import cn.aifei.weixin.WxConsts;

/**
 * 语音回复
 */
public class OutVoiceMsg extends OutMsg {

    private String mediaId;

    public OutVoiceMsg() {
        super(WxConsts.ReplyType.VOICE);
    }

    public OutVoiceMsg(String mediaId) {
        this();
        this.mediaId = mediaId;
    }

    public String getMediaId() {
        return mediaId;
    }

    public void setMediaId(String mediaId) {
        this.mediaId = mediaId;
    }
}
