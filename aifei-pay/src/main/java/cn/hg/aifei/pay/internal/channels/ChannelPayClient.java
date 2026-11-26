/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal.channels;

import cn.hg.aifei.pay.PayClient;
import cn.hg.aifei.pay.PayException;
import cn.hg.aifei.pay.internal.PayAmounts;
import cn.hg.aifei.pay.internal.PayConfig;
import cn.hg.aifei.pay.internal.PayErrors;
import cn.hg.aifei.pay.internal.PayResults;
import cn.hg.aifei.pay.model.PayOrderResult;
import cn.hg.aifei.pay.model.PayQueryResult;
import cn.hg.aifei.pay.model.PayRefundResult;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 七渠道共用的交易客户端：参数校验 + 编排 + 异常收敛，内核调用委托给 {@link ChannelKernel}。
 */
public final class ChannelPayClient implements PayClient {

    private static final int MAX_TRADE_NO_LENGTH = 64;
    private static final int MAX_SUBJECT_LENGTH = 256;

    private final ChannelConfig config;
    private final ChannelKernel kernel;

    ChannelPayClient(ChannelConfig config, ChannelKernel kernel) {
        if (config == null || kernel == null) {
            throw new IllegalArgumentException("config / kernel 不能为 null");
        }
        this.config = config;
        this.kernel = kernel;
    }

    @Override
    public PayOrderResult createOrder(String merchantId, String outTradeNo, long amountFen, String subject,
                                      String payType, String returnUrl, String notifyUrl,
                                      Map<String, String> options) {
        String type = PayConfig.isBlank(payType) ? "QRCODE" : payType.trim().toUpperCase();
        requireText(outTradeNo, "outTradeNo", MAX_TRADE_NO_LENGTH);
        PayAmounts.requirePositive(amountFen, "amountFen");
        requireText(subject, "subject", MAX_SUBJECT_LENGTH);

        Map<String, String> params = base(merchantId);
        params.put(ChannelKernel.PARAM_OUT_TRADE_NO, outTradeNo.trim());
        params.put(ChannelKernel.PARAM_AMOUNT_FEN, String.valueOf(amountFen));
        params.put(ChannelKernel.PARAM_SUBJECT, subject.trim());
        params.put(ChannelKernel.PARAM_NOTIFY_URL, callback(config.value("notify-url"), notifyUrl));
        if (!PayConfig.isBlank(returnUrl)) {
            params.put(ChannelKernel.PARAM_RETURN_URL, returnUrl.trim());
        } else if (config.value("return-url") != null) {
            params.put(ChannelKernel.PARAM_RETURN_URL, config.value("return-url"));
        }
        if (options != null) {
            params.putAll(options);
        }

        if ("QRCODE".equals(type) || "NATIVE".equals(type)) {
            Object response;
            try {
                response = kernel.precreate(config, params);
            } catch (PayException e) {
                throw e;
            } catch (Exception e) {
                throw PayErrors.channelKernelFailure("扫码下单", e);
            }
            return PayResults.toOrderResult(PayOrderResult.Channel.QRCODE, outTradeNo.trim(), "POST", response);
        }

        PayOrderResult.Channel channel = "PAGE".equals(type) ? PayOrderResult.Channel.PAGE
                : PayOrderResult.Channel.WAP;
        String payload;
        try {
            payload = kernel.pagePay(config, type, params);
        } catch (PayException e) {
            throw e;
        } catch (Exception e) {
            throw PayErrors.channelKernelFailure("页面下单", e);
        }
        return PayResults.toOrderResult(channel, outTradeNo.trim(), "GET", payload);
    }

    @Override
    public PayQueryResult query(String merchantId, String outTradeNo) {
        requireText(outTradeNo, "outTradeNo", MAX_TRADE_NO_LENGTH);
        Map<String, String> params = base(merchantId);
        params.put(ChannelKernel.PARAM_OUT_TRADE_NO, outTradeNo.trim());
        Object response;
        try {
            response = kernel.query(config, params);
        } catch (PayException e) {
            throw e;
        } catch (Exception e) {
            throw PayErrors.channelKernelFailure("查单", e);
        }
        return PayResults.toQueryResult(outTradeNo.trim(), response);
    }

    @Override
    public PayRefundResult refund(String merchantId, String outTradeNo, String outRequestNo, long refundFen,
                                  String refundReason) {
        requireText(outTradeNo, "outTradeNo", MAX_TRADE_NO_LENGTH);
        requireText(outRequestNo, "outRequestNo", MAX_TRADE_NO_LENGTH);
        PayAmounts.requirePositive(refundFen, "refundFen");
        Map<String, String> params = base(merchantId);
        params.put(ChannelKernel.PARAM_OUT_TRADE_NO, outTradeNo.trim());
        params.put(ChannelKernel.PARAM_OUT_REQUEST_NO, outRequestNo.trim());
        params.put(ChannelKernel.PARAM_REFUND_FEN, String.valueOf(refundFen));
        if (!PayConfig.isBlank(refundReason)) {
            params.put(ChannelKernel.PARAM_REFUND_REASON, refundReason.trim());
        }
        Object response;
        try {
            response = kernel.refund(config, params);
        } catch (PayException e) {
            throw e;
        } catch (Exception e) {
            throw PayErrors.channelKernelFailure("退款", e);
        }
        return PayResults.toRefundResult(outTradeNo.trim(), outRequestNo.trim(), response);
    }

    @Override
    public String channelName() {
        return config.channelName();
    }

    private Map<String, String> base(String merchantId) {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("merchant_id", PayConfig.isBlank(merchantId) ? config.merchantId() : merchantId.trim());
        params.put(ChannelKernel.PARAM_CURRENCY, config.value("currency", "CNY"));
        return params;
    }

    private static String callback(String configured, String explicit) {
        String value = PayConfig.isBlank(explicit) ? configured : explicit.trim();
        if (PayConfig.isBlank(value)) {
            throw PayErrors.missingCallback("下单", "notify-url");
        }
        return value.trim();
    }

    private static void requireText(String value, String name, int max) {
        if (PayConfig.isBlank(value)) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(name + " 长度不能超过 " + max + "，实际为 " + trimmed.length());
        }
    }
}
