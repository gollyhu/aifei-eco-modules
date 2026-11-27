/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms.internal.sms4j;

import cn.hg.aifei.tools.crypto.CryptoException;
import cn.hg.aifei.tools.crypto.CryptoUtil;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 阿里云短信状态报告回调验签唯一实现：HMAC-SHA1，纯 JDK，常量时间比较。
 *
 * <p>sms4j 只覆盖出站发送、不提供入站回执验签，故本类是职责边界补充，不替换发送内核。</p>
 */
public final class AliyunReceiptVerifier {

    private static final String SIGNATURE_KEY = "signature";
    private static final String SIGNATURE_KEY_ALT = "Signature";
    private static final String ALGORITHM = "HmacSHA1";

    /**
     * 校验回调签名。
     *
     * @param params          回调参数（含 signature 字段）
     * @param accessKeySecret 回执验签密钥（来自通道配置）
     * @return 验签是否通过；参数 / 密钥 / 签名缺失或算法异常均返回 {@code false}
     */
    public boolean verify(Map<String, String> params, String accessKeySecret) {
        if (params == null || accessKeySecret == null || accessKeySecret.trim().isEmpty()) {
            return false;
        }
        String provided = params.get(SIGNATURE_KEY);
        if (provided == null) {
            provided = params.get(SIGNATURE_KEY_ALT);
        }
        if (provided == null || provided.trim().isEmpty()) {
            return false;
        }
        try {
            List<String> keys = new ArrayList<String>(params.keySet());
            keys.remove(SIGNATURE_KEY);
            keys.remove(SIGNATURE_KEY_ALT);
            Collections.sort(keys);
            StringBuilder canonical = new StringBuilder();
            for (String key : keys) {
                String value = params.get(key);
                canonical.append(key).append(value == null ? "" : value);
            }
            return SignHolder.verify(accessKeySecret, canonical.toString(), provided);
        } catch (GeneralSecurityException e) {
            return false;
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }

    /**
     * 隔离 tools 异常类型引用：可选依赖缺失或验签异常一律收敛为「验签失败」。
     */
    private static final class SignHolder {
        private static boolean verify(String secret, String data, String provided)
                throws GeneralSecurityException {
            try {
                String expected = CryptoUtil.hmacSha1(secret, data);
                return CryptoUtil.constantTimeEquals(expected, provided);
            } catch (CryptoException e) {
                return false;
            }
        }
    }
}
