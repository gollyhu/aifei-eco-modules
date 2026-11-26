/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay;

import cn.hg.aifei.pay.internal.PayHolder;

/**
 * 静态门面：为非注入上下文（静态工具方法、脚本、回调入口）提供取用当前交易客户端的入口。
 *
 * <p>本类**有状态**——依赖插件 {@code start()} 完成装配；未装配或已停止即失败。因此命名用
 * {@code Kit} 而非 {@code Util}。</p>
 *
 * <p>只暴露 {@link #client()}，不复制 {@link AliPayClient} 的五个交易方法：双份契约必然漂移。</p>
 */
public final class AliPayKit {

    /**
     * 沙箱网关：{@code pay.apps.<appId>.gateway-url} 的合法取值来源，也用于环境一致性校验。
     */
    public static final String GATEWAY_SANDBOX = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";

    /**
     * 生产网关：{@code pay.apps.<appId>.gateway-url} 的合法取值来源，也用于环境一致性校验。
     */
    public static final String GATEWAY_PRODUCTION = "https://openapi.alipay.com/gateway.do";

    /**
     * 禁止创建门面实例。
     */
    private AliPayKit() {
    }

    /**
     * 取当前交易客户端。
     *
     * @return 与 {@code @Inject AliPayClient} 注入的**同一个对象**
     * @throws AliPayException 未装配或已停止
     */
    public static AliPayClient client() {
        return PayHolder.client();
    }
}
