/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.core.msg;

/**
 * 接收消息基类（公共字段）。
 *
 * <p>接收时 {@code ToUserName} 为公众号、{@code FromUserName} 为用户 openid；
 * 被动回复时由 MsgUtil 统一反转。以下 getter / setter 语义均同字段名，不逐方法重复说明。</p>
 */
public class InMsg {

    /** 接收方（公众号）原始账号。 */
    private String toUserName;
    /** 发送方（用户 openid）。 */
    private String fromUserName;
    /** 消息创建时间（秒级时间戳）。 */
    private long createTime;
    /** 消息类型（text / image / voice / event 等）。 */
    private String msgType;
    /** 消息 id（部分事件消息可能为 0）。 */
    private long msgId;

    /** @return 接收方（公众号）原始账号 */
    public String getToUserName() {
        return toUserName;
    }

    /** @param toUserName 接收方（公众号）原始账号 */
    public void setToUserName(String toUserName) {
        this.toUserName = toUserName;
    }

    /** @return 发送方（用户 openid） */
    public String getFromUserName() {
        return fromUserName;
    }

    /** @param fromUserName 发送方（用户 openid） */
    public void setFromUserName(String fromUserName) {
        this.fromUserName = fromUserName;
    }

    /** @return 消息创建时间（秒级时间戳） */
    public long getCreateTime() {
        return createTime;
    }

    /** @param createTime 消息创建时间（秒级时间戳） */
    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    /** @return 消息类型 */
    public String getMsgType() {
        return msgType;
    }

    /** @param msgType 消息类型 */
    public void setMsgType(String msgType) {
        this.msgType = msgType;
    }

    /** @return 消息 id（部分事件消息可能为 0） */
    public long getMsgId() {
        return msgId;
    }

    /** @param msgId 消息 id */
    public void setMsgId(long msgId) {
        this.msgId = msgId;
    }
}
