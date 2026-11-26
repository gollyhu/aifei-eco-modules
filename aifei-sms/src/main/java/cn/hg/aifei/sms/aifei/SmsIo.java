/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms.aifei;

import java.util.Map;

/**
 * 回执传输桥接（接入层非承诺构件）：把厂商回调的 HTTP 细节抽象出来，供 {@link SmsHandler} 读写交换。
 *
 * <p>框架 {@code cn.aifei.core.Output} 为空接口、不提供写响应能力，故由消费方（demo / 应用）
 * 提供本桥接实现；泛型无上界，本接口零第三方 / 零框架类型（对齐 {@code WeixinIo} 先例）。</p>
 *
 * @param <I> 具体传输层请求对象类型
 */
public interface SmsIo<I> {

    /**
     * 读取回调参数（含验签签名字段）。
     *
     * @param in 请求对象
     * @return 参数映射，键为参数名
     */
    Map<String, String> params(I in);

    /**
     * 读取原始回调报文。
     *
     * @param in 请求对象
     * @return 原始 body
     */
    String body(I in);

    /**
     * 写响应并结束本次交换。
     *
     * @param in     请求对象
     * @param status HTTP 状态码
     * @param json   响应体（JSON 文本）
     */
    void reply(I in, int status, String json);
}
