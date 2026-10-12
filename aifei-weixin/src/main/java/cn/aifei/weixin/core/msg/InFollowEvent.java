/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.core.msg;

/**
 * 关注事件（Event = subscribe）。扫码关注时携带 eventKey(带 qrscene_ 前缀) 与 ticket。
 */
public class InFollowEvent extends InEvent {

    private String ticket;

    public String getTicket() {
        return ticket;
    }

    public void setTicket(String ticket) {
        this.ticket = ticket;
    }
}
