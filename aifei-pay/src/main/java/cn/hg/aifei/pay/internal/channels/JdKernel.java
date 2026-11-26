/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal.channels;

import com.ijpay.jdpay.JdPayApi;
import com.ijpay.jdpay.model.QueryOrderModel;
import com.ijpay.jdpay.model.RefundModel;
import com.ijpay.jdpay.model.UniOrderModel;
import com.ijpay.jdpay.util.VerifySignatureUtl;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 京东支付内核：模型显式生成加密 XML，签名原文由回调字段按字典序拼接。
 */
public final class JdKernel implements ChannelKernel {

    @Override
    public Object precreate(ChannelConfig config, Map<String, String> params) {
        String version = config.require("version");
        String merchant = params.get("merchant_id");
        String xml = UniOrderModel.builder()
                .version(version)
                .merchant(merchant)
                .tradeNum(params.get(PARAM_OUT_TRADE_NO))
                .tradeName(params.get(PARAM_SUBJECT))
                .amount(params.get(PARAM_AMOUNT_FEN))
                .tradeTime(now())
                .orderType(config.value("order-type", "1"))
                .currency(params.get(PARAM_CURRENCY))
                .notifyUrl(params.get(PARAM_NOTIFY_URL))
                .build()
                .genReqXml(config.require("rsa-private-key"), config.require("des-key"), version, merchant);
        return ChannelResponses.xml(JdPayApi.uniOrder(xml));
    }

    @Override
    public String pagePay(ChannelConfig config, String payType, Map<String, String> params) {
        String version = config.require("version");
        String merchant = params.get("merchant_id");
        Map<String, String> signed = UniOrderModel.builder()
                .version(version)
                .merchant(merchant)
                .tradeNum(params.get(PARAM_OUT_TRADE_NO))
                .tradeName(params.get(PARAM_SUBJECT))
                .amount(params.get(PARAM_AMOUNT_FEN))
                .tradeTime(now())
                .orderType(config.value("order-type", "1"))
                .currency(params.get(PARAM_CURRENCY))
                .notifyUrl(params.get(PARAM_NOTIFY_URL))
                .build()
                .createSign(config.require("rsa-private-key"), config.require("des-key"));
        StringBuilder url = new StringBuilder(JdPayApi.PC_SAVE_ORDER_URL).append('?');
        for (Map.Entry<String, String> entry : signed.entrySet()) {
            if (url.charAt(url.length() - 1) != '?') {
                url.append('&');
            }
            url.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return url.toString();
    }

    @Override
    public Object query(ChannelConfig config, Map<String, String> params) {
        String version = config.require("version");
        String merchant = params.get("merchant_id");
        String xml = QueryOrderModel.builder()
                .version(version)
                .merchant(merchant)
                .tradeNum(params.get(PARAM_OUT_TRADE_NO))
                .build()
                .genReqXml(config.require("rsa-private-key"), config.require("des-key"), version, merchant);
        return ChannelResponses.xml(JdPayApi.queryOrder(xml));
    }

    @Override
    public Object refund(ChannelConfig config, Map<String, String> params) {
        String version = config.require("version");
        String merchant = params.get("merchant_id");
        String xml = RefundModel.builder()
                .version(version)
                .merchant(merchant)
                .tradeNum(params.get(PARAM_OUT_TRADE_NO))
                .oTradeNum(params.get(PARAM_OUT_REQUEST_NO))
                .amount(params.get(PARAM_REFUND_FEN))
                .currency(params.get(PARAM_CURRENCY))
                .tradeTime(now())
                .build()
                .genReqXml(config.require("rsa-private-key"), config.require("des-key"), version, merchant);
        return ChannelResponses.xml(JdPayApi.refund(xml));
    }

    public static boolean verify(ChannelConfig config, Map<String, String> params) {
        String sign = params.get("sign");
        if (sign == null || sign.trim().isEmpty()) {
            return false;
        }
        List<String> keys = new ArrayList<String>(params.keySet());
        Collections.sort(keys);
        StringBuilder source = new StringBuilder();
        for (String key : keys) {
            if ("sign".equals(key)) {
                continue;
            }
            if (source.length() > 0) {
                source.append('&');
            }
            source.append(key).append('=').append(params.get(key));
        }
        try {
            return VerifySignatureUtl.decryptMerchant(source.toString(), sign, config.require("rsa-public-key"));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String now() {
        return new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
    }
}
