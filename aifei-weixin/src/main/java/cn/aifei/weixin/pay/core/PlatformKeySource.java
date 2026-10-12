/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.pay.core;

import java.security.PublicKey;

/**
 * 平台验签公钥数据源：按应答 / 回调头 {@code Wechatpay-Serial} 取平台公钥。
 *
 * <p>生产实现为 {@link PlatformCertManager}（自动下载平台证书）；也可直接持有
 * 微信支付公钥（pub_key.pem）做静态实现。
 */
public interface PlatformKeySource {

    /**
     * @return 未知序列号返回 null（调用方以验签失败处理）
     */
    PublicKey bySerial(String serialNo);
}
