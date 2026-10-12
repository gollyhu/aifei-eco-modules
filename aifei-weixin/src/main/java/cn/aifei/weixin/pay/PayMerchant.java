/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.pay;

import cn.aifei.weixin.common.WxApiException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * 微信支付 V3 商户凭据（不可变值对象）。
 *
 * <p>官方文档：https://pay.weixin.qq.com/docs/merchant/development/interface-rules/private-key-and-certificate.html
 */
public class PayMerchant {

    private final String mchId;
    private final PrivateKey privateKey;
    /** 商户 API 证书序列号（请求头 Wechatpay-Serial 的取值来源） */
    private final String serialNo;
    /** APIv3 密钥（32 字符，用于平台证书 / 回调 resource 解密） */
    private final String apiV3Key;

    private PayMerchant(String mchId, PrivateKey privateKey, String serialNo, String apiV3Key) {
        this.mchId = mchId;
        this.privateKey = privateKey;
        this.serialNo = serialNo;
        this.apiV3Key = apiV3Key;
    }

    public static PayMerchant of(String mchId, PrivateKey privateKey, String serialNo, String apiV3Key) {
        requireNotBlank(mchId, "mchId");
        if (privateKey == null) {
            throw new IllegalArgumentException("privateKey 不能为 null");
        }
        requireNotBlank(serialNo, "serialNo");
        if (apiV3Key == null || apiV3Key.trim().length() != 32) {
            throw new IllegalArgumentException("apiV3Key 必须为 32 字符");
        }
        return new PayMerchant(mchId.trim(), privateKey, serialNo.trim(), apiV3Key.trim());
    }

    /**
     * 从 PKCS#8 PEM 私钥文本构造（apiclient_key.pem 原文或仅 Base64 均可）
     */
    public static PayMerchant ofPem(String mchId, String privateKeyPem, String serialNo, String apiV3Key) {
        if (privateKeyPem == null || privateKeyPem.trim().isEmpty()) {
            throw new IllegalArgumentException("privateKeyPem 不能为空");
        }
        return of(mchId, parsePem(privateKeyPem), serialNo, apiV3Key);
    }

    static PrivateKey parsePem(String pem) {
        try {
            String base64 = pem.replaceAll("-----[A-Z ]*-----", "").replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (IllegalArgumentException e) {
            throw new WxApiException("商户私钥 PEM Base64 非法", e);
        } catch (Exception e) {
            throw new WxApiException("商户私钥解析失败（须为 RSA PKCS#8）", e);
        }
    }

    private static void requireNotBlank(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }

    public String getMchId() {
        return mchId;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public String getSerialNo() {
        return serialNo;
    }

    public String getApiV3Key() {
        return apiV3Key;
    }

    /** 私钥与 APIv3 密钥不进 toString，防日志泄漏 */
    @Override
    public String toString() {
        return "PayMerchant{mchId=" + mchId + ", serialNo=" + serialNo + "}";
    }

    static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
