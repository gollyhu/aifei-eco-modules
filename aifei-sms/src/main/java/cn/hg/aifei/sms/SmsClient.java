/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms;

import java.util.LinkedHashMap;

/**
 * 短信发送门面：绑定某一通道的同步发送实例。
 *
 * <p>由 {@code SmsPlugin} 装配并注册；默认通道经 {@code @Inject SmsClient} 取得，命名通道经
 * {@link SmsKit#use(String)} 取得。实现无可变共享状态，可并发调用；同步阻塞至厂商返回。</p>
 */
public interface SmsClient {

    /**
     * 模板化发送：按模板 ID 与变量同步发送，阻塞至厂商返回。
     *
     * @param phoneNumber 手机号
     * @param templateId  模板 ID
     * @param variables   模板变量（可为 {@code null} 或空 Map，视为无变量）
     * @return 发送结果（含通道标识、状态与厂商流水号）
     * @throws SmsException 参数非法 / 依赖缺失 / 插件未启动 / 厂商错误 / 网络异常
     */
    SmsResult send(String phoneNumber, String templateId, LinkedHashMap<String, String> variables);

    /**
     * 内容直发：同步发送一段内容（由厂商按模板内容发送）。
     *
     * @param phoneNumber 手机号
     * @param message     发送内容
     * @return 发送结果
     * @throws SmsException 参数非法 / 依赖缺失 / 插件未启动 / 厂商错误 / 网络异常
     */
    SmsResult send(String phoneNumber, String message);

    /**
     * 返回本实例绑定的通道标识（{@code sms.<name>} 的 {@code <name>}）。
     *
     * @return 通道标识
     */
    String channelName();
}
