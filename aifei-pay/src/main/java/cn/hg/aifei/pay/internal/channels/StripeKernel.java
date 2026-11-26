/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal.channels;

import com.ijpay.stripe.StripeApi;
import com.ijpay.stripe.StripeApiConfig;
import com.stripe.StripeClient;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stripe 内核：通过显式 {@link StripeApiConfig} 取得 {@link StripeClient}，不使用进程级配置。
 */
public final class StripeKernel implements ChannelKernel {

    @Override
    public Object precreate(ChannelConfig config, Map<String, String> params) {
        PaymentIntent intent = createIntent(config, params);
        Map<String, String> result = new LinkedHashMap<String, String>();
        result.put("out_trade_no", params.get(PARAM_OUT_TRADE_NO));
        result.put("trade_no", intent.getId());
        result.put("trade_status", intent.getStatus());
        result.put("qr_code", intent.getClientSecret() == null ? "" : intent.getClientSecret());
        return result;
    }

    @Override
    public String pagePay(ChannelConfig config, String payType, Map<String, String> params) {
        PaymentIntent intent = createIntent(config, params);
        return intent.getClientSecret() == null ? "" : intent.getClientSecret();
    }

    @Override
    public Object query(ChannelConfig config, Map<String, String> params) {
        String id = first(params.get("payment_intent_id"), params.get(PARAM_OUT_TRADE_NO));
        PaymentIntent intent;
        try {
            intent = client(config).v1().paymentIntents().retrieve(id);
        } catch (com.stripe.exception.StripeException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        Map<String, String> result = new LinkedHashMap<String, String>();
        result.put("out_trade_no", params.get(PARAM_OUT_TRADE_NO));
        result.put("trade_no", intent.getId());
        result.put("trade_status", intent.getStatus());
        result.put("total_amount", String.valueOf(intent.getAmount()));
        return result;
    }

    @Override
    public Object refund(ChannelConfig config, Map<String, String> params) {
        String paymentIntent = first(params.get("payment_intent_id"), params.get(PARAM_OUT_TRADE_NO));
        RefundCreateParams request = RefundCreateParams.builder()
                .setPaymentIntent(paymentIntent)
                .setAmount(Long.parseLong(params.get(PARAM_REFUND_FEN)))
                .build();
        Refund refund;
        try {
            refund = client(config).v1().refunds().create(request);
        } catch (com.stripe.exception.StripeException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        Map<String, String> result = new LinkedHashMap<String, String>();
        result.put("out_trade_no", params.get(PARAM_OUT_TRADE_NO));
        result.put("trade_no", refund.getId());
        result.put("out_request_no", params.get(PARAM_OUT_REQUEST_NO));
        result.put("refund_fee", String.valueOf(refund.getAmount()));
        return result;
    }

    public static boolean verify(ChannelConfig config, Map<String, String> params) {
        String body = params.get("body");
        String signature = params.get("stripe-signature");
        if (body == null || signature == null) {
            return false;
        }
        try {
            client(config).constructEvent(body, signature, config.require("webhook-secret"));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static PaymentIntent createIntent(ChannelConfig config, Map<String, String> params) {
        PaymentIntentCreateParams request = PaymentIntentCreateParams.builder()
                .setAmount(Long.parseLong(params.get(PARAM_AMOUNT_FEN)))
                .setCurrency(params.get(PARAM_CURRENCY))
                .putMetadata("out_trade_no", params.get(PARAM_OUT_TRADE_NO))
                .build();
        try {
            return client(config).v1().paymentIntents().create(request);
        } catch (com.stripe.exception.StripeException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static StripeClient client(ChannelConfig config) {
        StripeApiConfig stripeConfig = new StripeApiConfig();
        stripeConfig.setApiKey(config.require("api-key"));
        stripeConfig.setWebhookSecret(config.value("webhook-secret"));
        return StripeApi.getStripeClient(stripeConfig);
    }

    private static String first(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
