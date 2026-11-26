/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms;

import cn.hg.aifei.sms.internal.SmsClients;
import java.util.LinkedHashMap;
import java.util.Set;

/**
 * 短信静态门面（有状态 Kit）：<b>须先完成 {@code SmsPlugin.start()} 装配</b>方可调用。
 *
 * <p>未装配或已停止时调用抛 {@link SmsException}（插件未启动或已停止）。</p>
 */
public final class SmsKit {

    private SmsKit() {
    }

    /**
     * 取默认通道客户端（由 {@code sms.default-channel} 指定，单通道时取唯一通道）。
     *
     * @return 默认通道客户端
     * @throws SmsException 未装配 / 已停止
     */
    public static SmsClient defaultClient() {
        return SmsClients.defaultClient();
    }

    /**
     * 按通道标识取命名通道客户端。
     *
     * @param channelName 通道标识（{@code sms.<name>} 的 {@code <name>}）
     * @return 命名通道客户端
     * @throws SmsException 未装配 / 已停止 / 通道不存在
     */
    public static SmsClient use(String channelName) {
        return SmsClients.use(channelName);
    }

    /**
     * 返回已装配的通道标识集合（不可变）。
     *
     * @return 通道标识集合
     * @throws SmsException 未装配 / 已停止
     */
    public static Set<String> channels() {
        return SmsClients.channels();
    }

    /**
     * 便捷模板化发送，等价于 {@code defaultClient().send(...)}。
     *
     * @param phoneNumber 手机号
     * @param templateId  模板 ID
     * @param variables   模板变量（可为 {@code null} 或空 Map）
     * @return 发送结果
     * @throws SmsException 未装配 / 已停止 / 参数非法 / 厂商错误 / 网络异常
     */
    public static SmsResult send(String phoneNumber, String templateId, LinkedHashMap<String, String> variables) {
        return defaultClient().send(phoneNumber, templateId, variables);
    }
}
