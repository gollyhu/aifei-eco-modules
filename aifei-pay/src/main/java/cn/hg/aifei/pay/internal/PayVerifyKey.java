/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import com.alipay.v3.util.AntCertificationUtil;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 一组验签凭据：类型 + 凭据文本 + 证书序列号。
 *
 * <p>证书序列号的计算与公钥提取只在本类发生。注意 {@code AlipaySignature.getCertSN(path)} /
 * {@code getAlipayPublicKey(path)} 只接受**路径**，本模块的凭据是**内容**，故必须直接调用
 * {@code AntCertificationUtil.getCertSN(content)} / {@code getCertPublicKey(content)}
 * （易踩坑点）。</p>
 */
public final class PayVerifyKey {

    /**
     * 凭据类型。
     */
    public enum Type {

        /**
         * 普通公钥模式：凭据文本即支付宝公钥。
         */
        KEY,

        /**
         * 公钥证书模式：凭据文本是支付宝公钥证书，需先取公钥与序列号。
         */
        CERT
    }

    /**
     * 证书序列号的合法形态：32 位 hex。
     */
    private static final Pattern CERT_SN_PATTERN = Pattern.compile("[0-9a-f]{32}");

    /**
     * 凭据类型配置值：证书。
     */
    private static final String TYPE_TEXT_CERT = "cert";

    /**
     * 凭据类型配置值：公钥。
     */
    private static final String TYPE_TEXT_KEY = "key";

    /**
     * 凭据配置键的取值后缀。
     */
    private static final String SUFFIX_VALUE = ".value";

    /**
     * 证书序列号配置键的取值后缀。
     */
    private static final String SUFFIX_CERT_SN = ".cert-sn";

    private final Type type;

    private final String content;

    private final String publicKeyText;

    private final String certSn;

    private PayVerifyKey(Type type, String content, String publicKeyText, String certSn) {
        this.type = type;
        this.content = content;
        this.publicKeyText = publicKeyText;
        this.certSn = certSn;
    }

    /**
     * 构造凭据：读取凭据文本，证书类型额外计算序列号并提取公钥。
     *
     * @param baseKey     凭据配置键前缀（如 {@code pay.apps.<appId>.verify.<n>}）
     * @param typeText    类型配置值 {@code cert} / {@code key}
     * @param rawValue    凭据原始配置值（内联 PEM 或 {@code @file:} 路径）
     * @param certSnText  显式配置的证书序列号，可为 {@code null}
     * @return 验签凭据
     * @throws cn.hg.aifei.pay.PayException 类型非法、凭据读取失败、序列号非法或与证书不一致、缺 BouncyCastle
     */
    public static PayVerifyKey of(String baseKey, String typeText, String rawValue, String certSnText) {
        Type type = typeOf(baseKey, typeText);
        String content = PayResource.read(baseKey + SUFFIX_VALUE, rawValue);
        if (type == Type.KEY) {
            return new PayVerifyKey(type, content, content, null);
        }
        PayDeps.requireBouncyCastle();
        String computed = AntCertificationUtil.getCertSN(content);
        String declared = certSnText == null ? null : certSnText.trim().toLowerCase(Locale.ROOT);
        if (declared != null && !declared.isEmpty()) {
            verifyDeclaredCertSn(baseKey + SUFFIX_CERT_SN, declared, computed);
        }
        String publicKey = AntCertificationUtil.getCertPublicKey(content);
        return new PayVerifyKey(type, content, publicKey, computed);
    }

    /**
     * @return 凭据类型
     */
    public Type type() {
        return type;
    }

    /**
     * @return 验签用公钥文本：{@code KEY} 类型即凭据本身，{@code CERT} 类型为从证书提取的公钥
     */
    public String publicKeyText() {
        return publicKeyText;
    }

    /**
     * @return 证书序列号；{@code KEY} 类型为 {@code null}
     */
    public String certSn() {
        return certSn;
    }

    /**
     * @return 凭据原始文本（{@code CERT} 类型为证书内容），供证书模式下单时注入 SDK
     */
    public String certContent() {
        return content;
    }

    /**
     * 解析类型配置值。
     *
     * @param baseKey  凭据配置键前缀
     * @param typeText 类型配置值
     * @return 凭据类型
     */
    private static Type typeOf(String baseKey, String typeText) {
        String text = typeText == null ? "" : typeText.trim();
        if (TYPE_TEXT_CERT.equalsIgnoreCase(text)) {
            return Type.CERT;
        }
        if (TYPE_TEXT_KEY.equalsIgnoreCase(text)) {
            return Type.KEY;
        }
        throw PayErrors.invalidConfig(baseKey + ".type", text,
                "合法值为 " + TYPE_TEXT_CERT + " / " + TYPE_TEXT_KEY);
    }

    /**
     * 校验显式配置的证书序列号：形态必须为 32 位 hex，且必须与证书实际序列号一致。
     *
     * @param certSnKey 证书序列号键名
     * @param declared  显式配置值（已转小写）
     * @param computed  实际计算值
     */
    private static void verifyDeclaredCertSn(String certSnKey, String declared, String computed) {
        if (!CERT_SN_PATTERN.matcher(declared).matches()) {
            throw PayErrors.invalidConfig(certSnKey, declared, "证书序列号必须为 32 位 hex");
        }
        if (computed == null || !declared.equalsIgnoreCase(computed)) {
            throw PayErrors.invalidConfig(certSnKey, declared,
                    "与凭据证书的实际序列号不一致（实际为 " + computed + "）");
        }
    }
}
