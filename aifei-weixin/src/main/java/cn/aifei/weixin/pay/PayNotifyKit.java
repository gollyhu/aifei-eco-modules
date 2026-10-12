/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.pay;

import cn.aifei.weixin.common.WxApiException;
import cn.aifei.weixin.pay.core.AesGcm;
import cn.aifei.weixin.pay.core.PayVerifier;
import cn.aifei.weixin.pay.core.PlatformKeySource;
import com.alibaba.fastjson2.JSONObject;
import java.util.Map;

/**
 * 支付回调通知处理：验签 + 解密 resource。不做幂等（微信会重复投递，业务按
 * out_trade_no + 订单状态前置校验保证），不感知租户（notify_url 租户段由应用侧解析）。
 *
 * <p>官方文档：https://pay.weixin.qq.com/docs/merchant/apis/jsapi-payment/payment-notice.html
 */
public class PayNotifyKit {

    public static final String HEADER_TIMESTAMP = "Wechatpay-Timestamp";
    public static final String HEADER_NONCE = "Wechatpay-Nonce";
    public static final String HEADER_SIGNATURE = "Wechatpay-Signature";
    public static final String HEADER_SERIAL = "Wechatpay-Serial";

    private PayNotifyKit() {
    }

    /**
     * 验签：message = timestamp\n nonce\n body\n，按 Wechatpay-Serial 取平台公钥。
     * 缺任一签名头即 false。
     */
    public static boolean verify(PlatformKeySource keys, Map<String, String> headers, String body) {
        if (headers == null) {
            return false;
        }
        return PayVerifier.verify(keys,
                header(headers, HEADER_SERIAL),
                header(headers, HEADER_TIMESTAMP),
                header(headers, HEADER_NONCE),
                body,
                header(headers, HEADER_SIGNATURE));
    }

    /**
     * 解密回调 resource（transaction / refund 通用），返回解密后的 JSON 明文
     */
    public static JSONObject decryptResource(PayMerchant merchant, String body) {
        JSONObject resource = requireResource(body);
        String plain = AesGcm.decrypt(merchant.getApiV3Key(), resource.getString("nonce"),
                resource.getString("ciphertext"), resource.getString("associated_data"));
        return JSONObject.parseObject(plain);
    }

    /**
     * 便捷入口：验签失败抛 WxApiException（应用侧应捕获并回 4xx + FAIL），
     * 成功返回解密后的 resource JSON。
     */
    public static JSONObject verifyAndDecrypt(PlatformKeySource keys, PayMerchant merchant,
                                              Map<String, String> headers, String body) {
        if (!verify(keys, headers, body)) {
            throw new WxApiException("微信支付回调验签失败");
        }
        return decryptResource(merchant, body);
    }

    private static String header(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static JSONObject requireResource(String body) {
        JSONObject json;
        try {
            json = JSONObject.parseObject(body);
        } catch (RuntimeException e) {
            throw new WxApiException("支付回调报文非 JSON", e);
        }
        JSONObject resource = json == null ? null : json.getJSONObject("resource");
        if (resource == null || resource.getString("ciphertext") == null) {
            throw new WxApiException("支付回调报文缺少 resource.ciphertext");
        }
        return resource;
    }
}
