/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms.spi;

/**
 * 回执内容值对象（不可变）：供监听器读取，纯 JDK 字段、零第三方 / 零框架类型。
 */
public final class SmsReceipt {

    private final String channelName;
    private final String phoneNumber;
    private final String status;
    private final String requestId;
    private final long reportTime;
    private final String rawBody;

    /**
     * 构造回执内容。
     *
     * @param channelName 通道标识
     * @param phoneNumber 手机号（脱敏由消费方处理）
     * @param status      厂商状态码（如阿里云 OK / 失败码）
     * @param requestId   厂商流水号 / 回执 ID
     * @param reportTime  回执时间（毫秒时间戳；厂商报文无时间时用接收时刻）
     * @param rawBody     原始回调报文
     */
    public SmsReceipt(String channelName, String phoneNumber, String status, String requestId,
                      long reportTime, String rawBody) {
        this.channelName = channelName;
        this.phoneNumber = phoneNumber;
        this.status = status;
        this.requestId = requestId;
        this.reportTime = reportTime;
        this.rawBody = rawBody;
    }

    /**
     * @return 通道标识
     */
    public String getChannelName() {
        return channelName;
    }

    /**
     * @return 手机号
     */
    public String getPhoneNumber() {
        return phoneNumber;
    }

    /**
     * @return 厂商状态码
     */
    public String getStatus() {
        return status;
    }

    /**
     * @return 厂商流水号 / 回执 ID
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * @return 回执时间（毫秒时间戳）
     */
    public long getReportTime() {
        return reportTime;
    }

    /**
     * @return 原始回调报文
     */
    public String getRawBody() {
        return rawBody;
    }
}
