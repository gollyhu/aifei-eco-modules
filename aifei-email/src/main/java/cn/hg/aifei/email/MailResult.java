/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email;

/**
 * 发送结果值对象（不可变）：承载通道标识与发送状态。
 *
 * <p>底层内核的发送方法无返回值，成功无异常即视为发送成功，故 {@link #isSuccess()} 当前恒为
 * {@code true}：发送失败一律抛 {@link MailException}，不存在失败结果对象。保留该字段是为结果对象
 * 将来扩展（例如异步回执）预留语义完整性。</p>
 */
public final class MailResult {

    private final String channelName;
    private final boolean success;
    private final String message;

    /**
     * 构造发送结果。
     *
     * @param channelName 通道标识
     * @param success     发送是否成功
     * @param message     结果描述（成功时为固定文案），可为 {@code null}
     */
    public MailResult(String channelName, boolean success, String message) {
        this.channelName = channelName;
        this.success = success;
        this.message = message;
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
     * @return 结果描述，可为 {@code null}
     */
    public String getMessage() {
        return message;
    }
}
