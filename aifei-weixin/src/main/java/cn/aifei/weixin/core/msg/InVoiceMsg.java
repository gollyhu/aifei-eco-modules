/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.core.msg;

/**
 * 语音消息
 */
public class InVoiceMsg extends InMsg {

    private String mediaId;
    private String format;
    /** 16K 采样率语音媒体 id（Format 为 amr/speex 时返回） */
    private String mediaId16K;

    public String getMediaId() {
        return mediaId;
    }

    public void setMediaId(String mediaId) {
        this.mediaId = mediaId;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getMediaId16K() {
        return mediaId16K;
    }

    public void setMediaId16K(String mediaId16K) {
        this.mediaId16K = mediaId16K;
    }
}
