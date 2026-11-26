/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import cn.aifei.log.Log;
import com.alipay.v3.util.AlipaySignature;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 验签的唯一入口：普通公钥 / 公钥证书、多凭据轮换、同步与异步两类报文同构处理。
 *
 * <p><b>纯函数</b>：不修改入参、不写缓存、不产生副作用，同一入参重复调用结果恒等。</p>
 *
 * <p><b>内核能力上限</b>（非本模块选择）：{@code sign_type} 仅 {@code RSA2}、{@code charset} 仅
 * {@code UTF-8}、值为空的字段不参与签名串（`docs/design.md` §2.4.4）。</p>
 *
 * <p><b>证书模式禁令</b>：不得把证书**内容**传给只接受**路径**的
 * {@code AlipaySignature.getCertSN/getAlipayPublicKey}，也不得使用已废弃的 {@code rsaCertCheckV1}——
 * 证书公钥由 {@link PayVerifyKey} 在解析期用 {@code AntCertificationUtil.getCertPublicKey(content)}
 * 提取，验签统一走 {@code rsaCheckV1}。</p>
 */
public final class PayVerifier {

    /**
     * 验签字符集：内核仅支持 UTF-8。
     */
    private static final String CHARSET = "UTF-8";

    /**
     * 验签签名类型：内核仅支持 RSA2。
     */
    private static final String SIGN_TYPE = "RSA2";

    /**
     * 报文键：签名值。
     */
    private static final String PARAM_SIGN = "sign";

    /**
     * 报文键：签名类型。
     */
    private static final String PARAM_SIGN_TYPE = "sign_type";

    /**
     * 报文键：支付宝公钥证书序列号（证书模式通知携带）。
     */
    private static final String PARAM_ALIPAY_CERT_SN = "alipay_cert_sn";

    private final Map<String, PayAppConfig> apps;

    /**
     * @param apps appId → 应用配置（装配期快照，本类只读）
     */
    public PayVerifier(Map<String, PayAppConfig> apps) {
        if (apps == null || apps.isEmpty()) {
            throw new IllegalArgumentException("apps 不能为空");
        }
        this.apps = apps;
    }

    /**
     * 验签：任一凭据通过即通过。
     *
     * @param appId  应用编号
     * @param params 报文参数（本方法不修改它）
     * @return 通过为 {@code true}；签名缺失、签名类型不受支持、凭据不匹配或校验不通过均为 {@code false}
     * @throws cn.hg.aifei.pay.AliPayException 内核依赖缺失、appId 未配置、报文为 {@code null}
     */
    public boolean verify(String appId, Map<String, String> params) {
        PayDeps.requireKernel();
        PayAppConfig app = apps.get(appId);
        if (app == null) {
            throw PayErrors.unknownApp(appId);
        }
        if (params == null) {
            throw PayErrors.verifyFailed(appId, "报文参数为 null");
        }
        Map<String, String> copy = PayForms.copy(params);
        if (!hasText(copy.get(PARAM_SIGN))) {
            return fail(appId);
        }
        String signType = copy.get(PARAM_SIGN_TYPE);
        if (hasText(signType) && !SIGN_TYPE.equalsIgnoreCase(signType.trim())) {
            return fail(appId);
        }
        List<PayVerifyKey> candidates = candidates(app, copy.get(PARAM_ALIPAY_CERT_SN));
        for (PayVerifyKey candidate : candidates) {
            if (matches(candidate, copy)) {
                return true;
            }
        }
        return fail(appId);
    }

    /**
     * 选出本次报文要尝试的凭据。
     *
     * <p>证书模式且报文带 {@code alipay_cert_sn} 时只尝试序列号相等者，无匹配即整体失败
     * （轮换期精确选证书，`docs/design.md` §14.1 A-4）；报文未带序列号时，仅当只有一张证书可直接使用，
     * 多张证书无法确定归属，交回失败。</p>
     *
     * @param app        应用配置
     * @param certSnText 报文中的证书序列号
     * @return 待尝试的凭据列表（空表示无法确定或不匹配）
     */
    private static List<PayVerifyKey> candidates(PayAppConfig app, String certSnText) {
        List<PayVerifyKey> plainKeys = new ArrayList<>();
        List<PayVerifyKey> certKeys = new ArrayList<>();
        for (PayVerifyKey key : app.verifyKeys()) {
            if (key.type() == PayVerifyKey.Type.CERT) {
                certKeys.add(key);
            } else {
                plainKeys.add(key);
            }
        }
        if (certKeys.isEmpty()) {
            return plainKeys;
        }
        String certSn = certSnText == null ? "" : certSnText.trim();
        if (certSn.isEmpty()) {
            if (certKeys.size() == 1) {
                plainKeys.add(certKeys.get(0));
            }
            return plainKeys;
        }
        boolean matched = false;
        for (PayVerifyKey certKey : certKeys) {
            if (certSn.equalsIgnoreCase(certKey.certSn())) {
                plainKeys.add(certKey);
                matched = true;
            }
        }
        return matched ? plainKeys : new ArrayList<PayVerifyKey>();
    }

    /**
     * 用单个凭据校验一次。
     *
     * <p>本方法固定使用 {@code rsaCheckV1(params, publicKey, charset, signType)}：上游虽在 3.1.89.ALL
     * 上把它标记为废弃，但设计 §2.4.4 明确它是普通公钥与证书模式统一的验签入口，且禁止改用
     * {@code rsaCertCheckV1/V2}（两者同样废弃且只接受证书路径）。</p>
     *
     * @param key     凭据
     * @param params  报文参数
     * @return 通过为 {@code true}
     */
    @SuppressWarnings("deprecation")
    private static boolean matches(PayVerifyKey key, Map<String, String> params) {
        try {
            return AlipaySignature.rsaCheckV1(PayForms.copy(params), key.publicKeyText(), CHARSET, SIGN_TYPE);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 验签失败：warn 一次（不含报文与凭据）后返回 {@code false}。
     *
     * @param appId 应用编号
     * @return 恒为 {@code false}
     */
    private static boolean fail(String appId) {
        Log.get(PayVerifier.class).warn("支付宝回调验签失败：appId=" + appId);
        return false;
    }

    /**
     * 判断文本非空白。
     *
     * @param value 文本
     * @return 非空白时 {@code true}
     */
    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
