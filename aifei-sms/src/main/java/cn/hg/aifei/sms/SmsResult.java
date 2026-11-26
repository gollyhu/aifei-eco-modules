/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms;

/**
 * 发送结果值对象（不可变）：承载通道标识、发送状态、厂商描述与流水号。
 */
public final class SmsResult {

    private final String channelName;
    private final boolean success;
    private final String message;
    private final String requestId;

    /**
     * 构造发送结果。
     *
     * @param channelName 通道标识
     * @param success     发送是否成功
     * @param message     厂商描述 / 失败原因，可空
     * @param requestId   厂商流水号 / 回执 ID，可空
     */
    public SmsResult(String channelName, boolean success, String message, String requestId) {
        this.channelName = channelName;
        this.success = success;
        this.message = message;
        this.requestId = requestId;
    }

    /**
     * @return 通道标识
     */
    public String getChannelName() {
        return channelName;
    }

    /**
     * @return 发送是否成功
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * @return 厂商描述 / 失败原因，可空
     */
    public String getMessage() {
        return message;
    }

    /**
     * @return 厂商流水号 / 回执 ID，可空
     */
    public String getRequestId() {
        return requestId;
    }
}
