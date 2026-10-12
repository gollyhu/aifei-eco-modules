/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.pay.core;

import cn.aifei.weixin.core.encrypt.RandomKit;
import cn.aifei.weixin.pay.PayMerchant;
import com.alibaba.fastjson2.JSON;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.util.Base64;

/**
 * V3 请求签名（SHA256withRSA）与 Authorization 头构造、小程序 / 公众号拉起支付的二次签名。
 *
 * <p>官方文档：https://pay.weixin.qq.com/docs/merchant/development/interface-rules/signature-generation.html
 */
public class PaySignature {

    public static final String AUTH_SCHEME = "WECHATPAY2-SHA256-RSA2048";

    private PaySignature() {
    }

    /**
     * SHA256withRSA 签名，Base64 输出
     */
    public static String sign(java.security.PrivateKey key, String message) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(key);
            signature.update(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new cn.aifei.weixin.common.WxApiException("SHA256withRSA 签名失败", e);
        }
    }

    /**
     * 构造 Authorization 头。canonicalUrl 为带 query 的路径（不含 host），body 为 null 时按空串参与签名。
     */
    public static String buildAuthorization(PayMerchant merchant, String method,
                                            String canonicalUrl, String body) {
        long timestamp = System.currentTimeMillis() / 1000;
        String nonce = RandomKit.randomStr(32);
        String message = method + "\n" + canonicalUrl + "\n" + timestamp + "\n" + nonce + "\n"
                + (body == null ? "" : body) + "\n";
        String signature = sign(merchant.getPrivateKey(), message);
        return AUTH_SCHEME
                + " mchid=\"" + merchant.getMchId() + "\""
                + ",nonce_str=\"" + nonce + "\""
                + ",timestamp=\"" + timestamp + "\""
                + ",serial_no=\"" + merchant.getSerialNo() + "\""
                + ",signature=\"" + signature + "\"";
    }

    /**
     * 构造前端 wx.requestPayment 所需的支付参数（prepay_id 二次签名）。
     *
     * <p>官方文档：https://pay.weixin.qq.com/docs/merchant/products/mini-program-payment/development.html
     */
    public static JsapiPayParams buildJsapiPayParams(String appId, PayMerchant merchant, String prepayId) {
        String timeStamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonceStr = RandomKit.randomStr(32);
        String packageValue = "prepay_id=" + prepayId;
        String message = appId + "\n" + timeStamp + "\n" + nonceStr + "\n" + packageValue + "\n";
        return new JsapiPayParams(appId, timeStamp, nonceStr, packageValue,
                sign(merchant.getPrivateKey(), message));
    }

    /**
     * 拉起支付参数（字段名与 wx.requestPayment 入参一致）
     */
    public static class JsapiPayParams {

        private final String appId;
        private final String timeStamp;
        private final String nonceStr;
        private final String packageValue;
        private final String paySign;

        private JsapiPayParams(String appId, String timeStamp, String nonceStr, String packageValue, String paySign) {
            this.appId = appId;
            this.timeStamp = timeStamp;
            this.nonceStr = nonceStr;
            this.packageValue = packageValue;
            this.paySign = paySign;
        }

        public String getAppId() {
            return appId;
        }

        public String getTimeStamp() {
            return timeStamp;
        }

        public String getNonceStr() {
            return nonceStr;
        }

        public String getPackageValue() {
            return packageValue;
        }

        public String getPaySign() {
            return paySign;
        }

        public String toJson() {
            return JSON.toJSONString(this);
        }
    }
}
