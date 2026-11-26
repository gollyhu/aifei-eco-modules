/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import com.alipay.api.internal.util.AlipaySignature;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 支付宝 V2 验签器：使用 alipay-sdk-java 4.x 的 {@code rsaCheckV1}。
 */
public final class AliPayV2Verifier implements PayChannelVerifier {

    private static final String CHARSET = "UTF-8";
    private static final String SIGN_TYPE = "RSA2";
    private static final String PARAM_SIGN = "sign";
    private static final String PARAM_SIGN_TYPE = "sign_type";
    private static final String PARAM_ALIPAY_CERT_SN = "alipay_cert_sn";

    private final Map<String, PayAppConfig> apps;

    public AliPayV2Verifier(Map<String, PayAppConfig> apps) {
        if (apps == null || apps.isEmpty()) {
            throw new IllegalArgumentException("apps 不能为空");
        }
        this.apps = apps;
    }

    @Override
    public boolean verify(String merchantId, Map<String, String> params) {
        PayDeps.requireV2Kernel();
        PayAppConfig app = apps.get(merchantId);
        if (app == null) {
            throw PayErrors.unknownApp(merchantId);
        }
        if (params == null) {
            throw PayErrors.verifyFailed(merchantId, "报文参数为 null");
        }
        Map<String, String> copy = PayForms.copy(params);
        if (!hasText(copy.get(PARAM_SIGN))) {
            return false;
        }
        String signType = copy.get(PARAM_SIGN_TYPE);
        if (hasText(signType) && !SIGN_TYPE.equalsIgnoreCase(signType.trim())) {
            return false;
        }
        for (PayVerifyKey key : candidates(app, copy.get(PARAM_ALIPAY_CERT_SN))) {
            try {
                if (AlipaySignature.rsaCheckV1(copy, key.publicKeyText(), CHARSET, SIGN_TYPE)) {
                    return true;
                }
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    private static List<PayVerifyKey> candidates(PayAppConfig app, String certSnText) {
        List<PayVerifyKey> plainKeys = new ArrayList<PayVerifyKey>();
        List<PayVerifyKey> certKeys = new ArrayList<PayVerifyKey>();
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
        for (PayVerifyKey certKey : certKeys) {
            if (certSn.equalsIgnoreCase(certKey.certSn())) {
                plainKeys.add(certKey);
            }
        }
        return plainKeys;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
