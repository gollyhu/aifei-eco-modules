/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.pay;

import cn.aifei.weixin.common.WxApiException;
import cn.aifei.weixin.pay.core.PlatformKeySource;

/**
 * 支付链路配置入口：静态 {@code PayApi} / {@code PlatformCertManager} 由此取得
 * {@link PayHttpKit} 与 {@link PlatformKeySource}。
 *
 * <pre>
 * 使用约定（模式对齐公众号 WxApiConfig）：
 *  1: 应用启动时装配一次 WxPayConfig.set(http, keySource)
 *  2: keySource 常为 PlatformCertManager（同一商户实例），或持有微信支付公钥的静态实现
 *  3: 允许重复调用覆盖，以最后一次为准（仅供测试场景隔离使用）
 *  4: 未装配即调用 → 抛 WxApiException，避免隐式 NPE
 * </pre>
 */
public class WxPayConfig {

    private static volatile PayHttpKit http;
    private static volatile PlatformKeySource keySource;

    public static void set(PayHttpKit httpKit, PlatformKeySource platformKeySource) {
        http = httpKit;
        keySource = platformKeySource;
    }

    public static PayHttpKit http() {
        PayHttpKit ret = http;
        if (ret == null) {
            throw new WxApiException("WxPayConfig 未初始化，请先在应用启动时调用 WxPayConfig.set(http, keySource)");
        }
        return ret;
    }

    public static PlatformKeySource keySource() {
        PlatformKeySource ret = keySource;
        if (ret == null) {
            throw new WxApiException("WxPayConfig 未初始化，请先在应用启动时调用 WxPayConfig.set(http, keySource)");
        }
        return ret;
    }

    private WxPayConfig() {
    }
}
