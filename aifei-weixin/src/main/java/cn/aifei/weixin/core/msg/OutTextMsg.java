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
 * 文本回复
 */
public class OutTextMsg extends OutMsg {

    private String content;

    public OutTextMsg() {
        super(WxConsts.ReplyType.TEXT);
    }

    public OutTextMsg(String content) {
        this();
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
