/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal.channels;

import cn.hutool.json.JSONUtil;
import com.ijpay.core.kit.WxPayKit;
import com.ijpay.fuiyou.FuiYouPayApi;
import com.ijpay.fuiyou.FuiYouPayCore;
import com.ijpay.fuiyou.enums.FuiYouApiType;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 富友内核：显式组装签名报文并调用 {@code FuiYouPayApi.execution}，不触碰进程级配置。
 */
public final class FuiYouKernel implements ChannelKernel {

    private static final String VERSION = "2.5";

    @Override
    public Object precreate(ChannelConfig config, Map<String, String> params) {
        Map<String, String> request = new LinkedHashMap<String, String>();
        request.put("mchnt_cd", params.get("merchant_id"));
        request.put("order_type", config.value("order-type", "01"));
        request.put("order_amt", params.get(PARAM_AMOUNT_FEN));
        request.put("mchnt_order_no", params.get(PARAM_OUT_TRADE_NO));
        request.put("txn_begin_ts", now());
        request.put("goods_des", params.get(PARAM_SUBJECT));
        request.put("term_id", config.value("term-id", ""));
        request.put("term_ip", config.value("term-ip", "127.0.0.1"));
        request.put("notify_url", params.get(PARAM_NOTIFY_URL));
        request.put("random_str", WxPayKit.generateStr());
        request.put("version", VERSION);
        return execute(FuiYouApiType.UNIFIED_ORDER, request, config, "mchnt_cd", "order_type", "order_amt",
                "mchnt_order_no", "txn_begin_ts", "goods_des", "term_id", "term_ip", "notify_url", "random_str",
                "version");
    }

    @Override
    public String pagePay(ChannelConfig config, String payType, Map<String, String> params) {
        Map<String, String> response = ChannelResponses.json(FuiYouPayApi.execution(FuiYouApiType.WX_PRE_CREATE,
                JSONUtil.toJsonStr(params)));
        return ChannelResponses.text(response, "url");
    }

    @Override
    public Object query(ChannelConfig config, Map<String, String> params) {
        Map<String, String> request = new LinkedHashMap<String, String>();
        request.put("mchnt_cd", params.get("merchant_id"));
        request.put("order_type", config.value("order-type", "01"));
        request.put("mchnt_order_no", params.get(PARAM_OUT_TRADE_NO));
        request.put("term_id", config.value("term-id", ""));
        request.put("random_str", WxPayKit.generateStr());
        request.put("version", VERSION);
        return execute(FuiYouApiType.ORDER_QUERY, request, config, "mchnt_cd", "order_type", "mchnt_order_no",
                "term_id", "random_str", "version");
    }

    @Override
    public Object refund(ChannelConfig config, Map<String, String> params) {
        Map<String, String> request = new LinkedHashMap<String, String>();
        request.put("mchnt_cd", params.get("merchant_id"));
        request.put("order_type", config.value("order-type", "01"));
        request.put("mchnt_order_no", params.get(PARAM_OUT_TRADE_NO));
        request.put("refund_order_no", params.get(PARAM_OUT_REQUEST_NO));
        request.put("total_amt", params.get("total_amt"));
        request.put("refund_amt", params.get(PARAM_REFUND_FEN));
        request.put("term_id", config.value("term-id", ""));
        request.put("random_str", WxPayKit.generateStr());
        request.put("version", VERSION);
        return execute(FuiYouApiType.REFUND, request, config, "mchnt_cd", "order_type", "mchnt_order_no",
                "refund_order_no", "total_amt", "refund_amt", "term_id", "random_str", "version");
    }

    public static boolean verify(ChannelConfig config, Map<String, String> params) {
        String sign = params.get("sign");
        if (sign == null || sign.trim().isEmpty()) {
            return false;
        }
        return FuiYouPayCore.verifyNotify(sign, value(params, "mchnt_cd"), value(params, "mchnt_order_no"),
                value(params, "settle_order_amt"), value(params, "order_amt"), value(params, "txn_fin_ts"),
                value(params, "reserved_fy_settle_dt"), value(params, "random_str"), config.require("mchnt-key"));
    }

    private static Object execute(FuiYouApiType api, Map<String, String> request, ChannelConfig config,
                                  String... signFields) {
        String[] values = new String[signFields.length];
        for (int i = 0; i < signFields.length; i++) {
            values[i] = request.get(signFields[i]);
        }
        request.put("sign", FuiYouPayCore.createSign(config.require("mchnt-key"), values));
        return ChannelResponses.json(FuiYouPayApi.execution(api, JSONUtil.toJsonStr(request)));
    }

    private static String value(Map<String, String> params, String key) {
        String value = params.get(key);
        return value == null ? "" : value;
    }

    private static String now() {
        return new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
    }
}
