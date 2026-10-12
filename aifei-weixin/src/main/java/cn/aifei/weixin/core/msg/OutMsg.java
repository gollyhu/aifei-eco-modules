/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.core.msg;

/**
 * 被动回复基类。
 *
 * <p>toUserName / fromUserName / createTime 由 MsgKit 统一填充（反转入参 + 当前秒级时间戳），
 * 业务侧只需关注内容字段。
 */
public class OutMsg {

    private String toUserName;
    private String fromUserName;
    private long createTime;
    private final String msgType;

    protected OutMsg(String msgType) {
        this.msgType = msgType;
    }

    public String getMsgType() {
        return msgType;
    }

    public String getToUserName() {
        return toUserName;
    }

    public void setToUserName(String toUserName) {
        this.toUserName = toUserName;
    }

    public String getFromUserName() {
        return fromUserName;
    }

    public void setFromUserName(String fromUserName) {
        this.fromUserName = fromUserName;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }
}
