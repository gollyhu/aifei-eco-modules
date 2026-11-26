/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay;

import cn.hg.aifei.pay.internal.PayErrors;
import cn.hg.aifei.pay.internal.PayForms;
import cn.hg.aifei.pay.internal.PayHolder;
import cn.hg.aifei.pay.internal.PayResults;
import cn.hg.aifei.pay.model.PayNotifyResult;
import java.util.Map;

/**
 * 回调验签工具：同步 {@code return_url}（GET query）与异步 {@code notify_url}（POST form）同构处理。
 *
 * <p>本类**无状态**——不持有字段、不写缓存、不修改入参，故命名用 {@code Util}。同一入参重复调用
 * 结果恒等；模块只透出幂等键，不做去重、不做状态机，业务必须自行按 {@code outTradeNo} /
 * {@code outRequestNo} 做前置校验。</p>
 *
 * <p>{@link #verify(String, Map)} 返回布尔语义，验签不通过不抛异常；{@link #verifyAndParse(String, Map)}
 * 在验签不通过时抛 {@link AliPayException}——「失败不静默」。</p>
 */
public final class AliPayNotifyUtil {

    /**
     * 报文键：退款金额（元），存在即为退款类通知。
     */
    private static final String PARAM_REFUND_FEE = "refund_fee";

    /**
     * 报文键：退款请求号（退款幂等键），存在即为退款类通知。
     */
    private static final String PARAM_OUT_REQUEST_NO = "out_request_no";

    /**
     * 禁止创建工具类实例。
     */
    private AliPayNotifyUtil() {
    }

    /**
     * 验签（Map 入参）。
     *
     * @param appId  应用编号
     * @param params 报文参数，本方法不修改它
     * @return 通过为 {@code true}；签名缺失、类型不受支持或校验不通过为 {@code false}
     * @throws AliPayException 未装配、appId 未配置、报文为 {@code null}、内核依赖缺失
     */
    public static boolean verify(String appId, Map<String, String> params) {
        return PayHolder.verifier().verify(appId, params);
    }

    /**
     * 验签（urlencoded 报文入参）。
     *
     * @param appId    应用编号
     * @param formBody urlencoded 报文（同步 GET 的 query string 或异步 POST 的 form body）
     * @return 通过为 {@code true}；签名缺失、类型不受支持或校验不通过为 {@code false}
     * @throws AliPayException 未装配、appId 未配置、报文为 {@code null}、内核依赖缺失
     */
    public static boolean verify(String appId, String formBody) {
        if (formBody == null) {
            throw PayErrors.verifyFailed(appId, "报文内容为 null");
        }
        return verify(appId, PayForms.parse(formBody));
    }

    /**
     * 验签并解析（Map 入参）。
     *
     * @param appId  应用编号
     * @param params 报文参数，本方法不修改它
     * @return 验签通过的结果（含交易与退款幂等键）
     * @throws AliPayException 验签不通过、未装配、appId 未配置、报文为 {@code null}、内核依赖缺失
     */
    public static PayNotifyResult verifyAndParse(String appId, Map<String, String> params) {
        if (PayHolder.verifier().verify(appId, params)) {
            Map<String, String> copy = PayForms.copy(params);
            return PayResults.toNotifyResult(notifyType(copy), copy);
        }
        throw PayErrors.verifyFailed(appId, "签名校验不通过");
    }

    /**
     * 验签并解析（urlencoded 报文入参）。
     *
     * @param appId    应用编号
     * @param formBody urlencoded 报文（同步 GET 的 query string 或异步 POST 的 form body）
     * @return 验签通过的结果（含交易与退款幂等键）
     * @throws AliPayException 验签不通过、未装配、appId 未配置、报文为 {@code null}、内核依赖缺失
     */
    public static PayNotifyResult verifyAndParse(String appId, String formBody) {
        if (formBody == null) {
            throw PayErrors.verifyFailed(appId, "报文内容为 null");
        }
        return verifyAndParse(appId, PayForms.parse(formBody));
    }

    /**
     * 判定回调类型：报文含退款字段即为退款类通知，否则为交易类通知。
     *
     * @param params 报文参数
     * @return 回调类型
     */
    private static PayNotifyResult.NotifyType notifyType(Map<String, String> params) {
        if (!isEmpty(params.get(PARAM_REFUND_FEE)) || !isEmpty(params.get(PARAM_OUT_REQUEST_NO))) {
            return PayNotifyResult.NotifyType.REFUND;
        }
        return PayNotifyResult.NotifyType.TRADE;
    }

    /**
     * 判断文本为空（{@code null} 或空白）。
     *
     * @param value 文本
     * @return 为空时 {@code true}
     */
    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
