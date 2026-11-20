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
 * <p>toUserName / fromUserName / createTime 由 MsgUtil 统一填充（反转入参 + 当前秒级时间戳），
 * 业务侧只需关注内容字段。以下 getter / setter 语义均同字段名，不逐方法重复说明。</p>
 */
public class OutMsg {

    /** 接收方（用户 openid），由 MsgUtil 填充。 */
    private String toUserName;
    /** 发送方（公众号），由 MsgUtil 填充。 */
    private String fromUserName;
    /** 回复创建时间（秒级时间戳），由 MsgUtil 填充。 */
    private long createTime;
    /** 回复消息类型，构造时由子类固定。 */
    private final String msgType;

    protected OutMsg(String msgType) {
        this.msgType = msgType;
    }

    /** @return 回复消息类型 */
    public String getMsgType() {
        return msgType;
    }

    /** @return 接收方（用户 openid） */
    public String getToUserName() {
        return toUserName;
    }

    /** @param toUserName 接收方（用户 openid） */
    public void setToUserName(String toUserName) {
        this.toUserName = toUserName;
    }

    /** @return 发送方（公众号） */
    public String getFromUserName() {
        return fromUserName;
    }

    /** @param fromUserName 发送方（公众号） */
    public void setFromUserName(String fromUserName) {
        this.fromUserName = fromUserName;
    }

    /** @return 回复创建时间（秒级时间戳） */
    public long getCreateTime() {
        return createTime;
    }

    /** @param createTime 回复创建时间（秒级时间戳） */
    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }
}
