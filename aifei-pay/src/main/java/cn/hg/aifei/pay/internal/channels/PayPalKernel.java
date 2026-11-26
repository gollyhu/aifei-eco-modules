/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal.channels;

import cn.hg.aifei.pay.internal.PayConfig;
import com.ijpay.paypal.PayPalApi;
import com.ijpay.paypal.PayPalApiConfig;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * PayPal 内核：显式 {@link PayPalApiConfig} 构造请求；Webhook 用平台证书验签。
 */
public final class PayPalKernel implements ChannelKernel {

    @Override
    public Object precreate(ChannelConfig config, Map<String, String> params) {
        String currency = params.get(PARAM_CURRENCY);
        StringBuilder data = new StringBuilder("{\"intent\":\"CAPTURE\",\"purchase_units\":[{\"amount\":{")
                .append("\"currency_code\":\"").append(currency).append("\",")
                .append("\"value\":\"").append(yuan(params.get(PARAM_AMOUNT_FEN))).append("\"}}]}");
        return ChannelResponses.body(PayPalApi.createOrder(apiConfig(config), data.toString()));
    }

    @Override
    public String pagePay(ChannelConfig config, String payType, Map<String, String> params) {
        String currency = params.get(PARAM_CURRENCY);
        String data = "{\"intent\":\"CAPTURE\",\"purchase_units\":[{\"amount\":{\"currency_code\":\""
                + currency + "\",\"value\":\"" + yuan(params.get(PARAM_AMOUNT_FEN)) + "\"}}]}";
        Map<String, String> response = ChannelResponses.body(PayPalApi.createOrder(apiConfig(config), data));
        return ChannelResponses.text(response, "id");
    }

    @Override
    public Object query(ChannelConfig config, Map<String, String> params) {
        String id = first(params.get("paypal_order_id"), params.get(PARAM_OUT_TRADE_NO));
        return ChannelResponses.body(PayPalApi.queryOrder(apiConfig(config), id));
    }

    @Override
    public Object refund(ChannelConfig config, Map<String, String> params) {
        String captureId = first(params.get("capture_id"), params.get(PARAM_OUT_TRADE_NO));
        String currency = params.get(PARAM_CURRENCY);
        String data = "{\"amount\":{\"currency_code\":\"" + currency + "\",\"value\":\""
                + yuan(params.get(PARAM_REFUND_FEN)) + "\"}}";
        return ChannelResponses.body(PayPalApi.refund(apiConfig(config), captureId, data));
    }

    public static boolean verify(ChannelConfig config, Map<String, String> params) {
        String signature = params.get("paypal-transmission-sig");
        String transmissionId = params.get("paypal-transmission-id");
        String transmissionTime = params.get("paypal-transmission-time");
        String body = params.get("body");
        if (PayConfig.isBlank(signature) || PayConfig.isBlank(transmissionId)
                || PayConfig.isBlank(transmissionTime) || body == null) {
            return false;
        }
        try {
            String message = transmissionId + "|" + transmissionTime + "|" + config.require("webhook-id")
                    + "|" + crc32(body);
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey(config.require("webhook-cert")));
            verifier.update(message.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(java.util.Base64.getDecoder().decode(signature));
        } catch (Exception e) {
            return false;
        }
    }

    private static PayPalApiConfig apiConfig(ChannelConfig config) {
        PayPalApiConfig apiConfig = new PayPalApiConfig();
        apiConfig.setClientId(config.require("client-id"));
        apiConfig.setSecret(config.require("secret"));
        apiConfig.setSandBox(PayConfig.ENV_SANDBOX.equals(config.value("env", PayConfig.ENV_SANDBOX)));
        return apiConfig;
    }

    private static String yuan(String fen) {
        return new BigDecimal(fen).movePointLeft(2).toPlainString();
    }

    private static String first(String value, String fallback) {
        return PayConfig.isBlank(value) ? fallback : value;
    }

    private static long crc32(String body) {
        CRC32 crc = new CRC32();
        crc.update(body.getBytes(StandardCharsets.UTF_8));
        return crc.getValue();
    }

    private static PublicKey publicKey(String certificate) throws Exception {
        String normalized = certificate.replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "").replaceAll("\\s", "");
        byte[] der = java.util.Base64.getDecoder().decode(normalized);
        return CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der))
                .getPublicKey();
    }
}
