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
 * 图片回复
 */
public class OutImageMsg extends OutMsg {

    private String mediaId;

    public OutImageMsg() {
        super(WxConsts.ReplyType.IMAGE);
    }

    public OutImageMsg(String mediaId) {
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
