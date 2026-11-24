/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.pay.core;

import cn.aifei.weixin.core.encrypt.RandomUtil;
import cn.aifei.weixin.pay.PayMerchant;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.annotation.JSONField;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.util.Base64;

/**
 * V3 请求签名（SHA256withRSA）与 Authorization 头构造、小程序 / 公众号拉起支付的二次签名。
 *
 * <p>官方文档：https://pay.weixin.qq.com/docs/merchant/development/interface-rules/signature-generation.html
 */
public class PaySignature {

    /**
     * V3 Authorization 头的认证方案标识，作为头值的第一个 token（其后为 mchid / nonce_str 等参数）。
     */
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
        String nonce = RandomUtil.randomStr(32);
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
        String nonceStr = RandomUtil.randomStr(32);
        String packageValue = "prepay_id=" + prepayId;
        String message = appId + "\n" + timeStamp + "\n" + nonceStr + "\n" + packageValue + "\n";
        return new JsapiPayParams(appId, timeStamp, nonceStr, packageValue,
                sign(merchant.getPrivateKey(), message));
    }

    /**
     * 拉起支付参数，{@link #toJson()} 输出 appId / timeStamp / nonceStr / package / signType / paySign 六个键，
     * 与小程序 / 公众号 {@code wx.requestPayment} 的入参一一对应，可直接透传给前端。
     */
    public static class JsapiPayParams {

        /** 对应 wx.requestPayment 的 appId。 */
        private final String appId;
        /** 对应 wx.requestPayment 的 timeStamp（秒级时间戳字符串）。 */
        private final String timeStamp;
        /** 对应 wx.requestPayment 的 nonceStr。 */
        private final String nonceStr;
        /** 对应 wx.requestPayment 的 package，形如 {@code prepay_id=xxx}。 */
        @JSONField(name = "package")
        private final String packageValue;
        /** 对应 wx.requestPayment 的 signType，固定 RSA。 */
        private final String signType = "RSA";
        /** 对应 wx.requestPayment 的 paySign。 */
        private final String paySign;

        private JsapiPayParams(String appId, String timeStamp, String nonceStr, String packageValue, String paySign) {
            this.appId = appId;
            this.timeStamp = timeStamp;
            this.nonceStr = nonceStr;
            this.packageValue = packageValue;
            this.paySign = paySign;
        }

        /** @return 公众号 appId */
        public String getAppId() {
            return appId;
        }

        /** @return 秒级时间戳字符串 */
        public String getTimeStamp() {
            return timeStamp;
        }

        /** @return 随机串 */
        public String getNonceStr() {
            return nonceStr;
        }

        /** @return 形如 {@code prepay_id=xxx} 的 package 值 */
        public String getPackageValue() {
            return packageValue;
        }

        /** @return 签名类型，固定 RSA */
        public String getSignType() {
            return signType;
        }

        /** @return 二次签名 paySign */
        public String getPaySign() {
            return paySign;
        }

        /**
         * 序列化为 wx.requestPayment 可直接消费的 JSON。
         *
         * @return 含 appId / timeStamp / nonceStr / package / signType / paySign 六个键的 JSON 字符串
         */
        public String toJson() {
            return JSON.toJSONString(this);
        }
    }
}
