/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.support;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x509.TBSCertificate;
import org.bouncycastle.asn1.x509.Time;
import org.bouncycastle.asn1.x509.V3TBSCertificateGenerator;

/**
 * 测试凭据工厂：运行期生成 2048 位 RSA 密钥对与对应的自签 X.509 证书，并提供签名侧工具。
 *
 * <p><b>为什么运行期生成而不是入库二进制夹具</b>：证书与私钥只有 1~2 KB，但二进制/长文本入库会让
 * 「谁生成的、有效期到何时、是否属真实商户」这些问题长期不可审计。改为代码生成后，夹具的来源、算法
 * 与有效期全部在源码中可读，仓库内不存放任何凭据。</p>
 *
 * <p><b>用途限定</b>：这里的密钥、证书与签名向量**只用于验证验签算法与凭据轮换**，与任何真实商户无关，
 * 不得用于生产（同一声明见 {@code docs/testing.md}）。</p>
 *
 * <p><b>签名侧独立实现</b>：{@link #signContent(Map)} 按支付宝公开的待签串规范独立实现（键名升序、
 * 跳过空键空值、排除 {@code sign} 与 {@code sign_type}），不复用被测模块的代码，因此它是验签用例的
 * 独立判据，而不是「用被测实现验证被测实现」。</p>
 */
public final class PayTestCrypto {

    /**
     * 证书生效时间：2020-01-01 00:00:00 UTC，固定值保证同一进程内生成的证书字节稳定。
     */
    private static final long NOT_BEFORE_MILLIS = 1577836800000L;

    /**
     * 证书失效时间：2040-01-01 00:00:00 UTC。
     */
    private static final long NOT_AFTER_MILLIS = 2208988800000L;

    /**
     * 旧凭据的证书序列号（轮换用例的「旧」）。
     */
    private static final long SERIAL_OLD = 20260101001L;

    /**
     * 新凭据的证书序列号（轮换用例的「新」）。
     */
    private static final long SERIAL_NEW = 20261231002L;

    /**
     * PEM 每行长度（与 openssl 输出一致，便于肉眼比对）。
     */
    private static final int PEM_LINE_LENGTH = 64;

    /**
     * 旧密钥对：模拟轮换前的支付宝密钥。
     */
    public static final KeyPair KEY_PAIR_OLD = generateKeyPair();

    /**
     * 新密钥对：模拟轮换后的支付宝密钥。
     */
    public static final KeyPair KEY_PAIR_NEW = generateKeyPair();

    /**
     * 旧凭据的应用私钥（PKCS#8 PEM 文本）。
     */
    public static final String PRIVATE_KEY_OLD = privateKeyPem(KEY_PAIR_OLD);

    /**
     * 新凭据的应用私钥（PKCS#8 PEM 文本）。
     */
    public static final String PRIVATE_KEY_NEW = privateKeyPem(KEY_PAIR_NEW);

    /**
     * 旧凭据的支付宝公钥（裸 Base64，普通公钥模式的配置形态）。
     */
    public static final String PUBLIC_KEY_OLD = publicKeyBase64(KEY_PAIR_OLD);

    /**
     * 新凭据的支付宝公钥（裸 Base64）。
     */
    public static final String PUBLIC_KEY_NEW = publicKeyBase64(KEY_PAIR_NEW);

    /**
     * 旧凭据的自签证书（PEM 文本，证书模式的配置形态）。
     */
    public static final String CERT_OLD = selfSignedCertPem(KEY_PAIR_OLD, "alipay-public-old", SERIAL_OLD);

    /**
     * 新凭据的自签证书（PEM 文本）。
     */
    public static final String CERT_NEW = selfSignedCertPem(KEY_PAIR_NEW, "alipay-public-new", SERIAL_NEW);

    /**
     * 一个「配置里存在但报文不会命中」的证书序列号（32 位十六进制）。
     */
    public static final String UNKNOWN_CERT_SN = "0123456789abcdef0123456789abcdef";

    /**
     * 禁止创建工具类实例。
     */
    private PayTestCrypto() {
    }

    /**
     * 生成 2048 位 RSA 密钥对。
     *
     * @return 密钥对
     */
    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048, new SecureRandom());
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("生成 RSA 密钥对失败", e);
        }
    }

    /**
     * 取公钥的裸 Base64 文本（X.509 SubjectPublicKeyInfo 编码，不带 PEM 头尾）。
     *
     * @param keyPair 密钥对
     * @return 裸 Base64 公钥文本
     */
    public static String publicKeyBase64(KeyPair keyPair) {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    /**
     * 取私钥的 PKCS#8 PEM 文本。
     *
     * @param keyPair 密钥对
     * @return PEM 文本
     */
    public static String privateKeyPem(KeyPair keyPair) {
        return pem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
    }

    /**
     * 生成自签 X.509 证书（v3、SHA256withRSA、无扩展项），返回 PEM 文本。
     *
     * @param keyPair    密钥对，证书公钥取自此密钥对的公钥
     * @param commonName 证书主体与签发者的 CN
     * @param serial     证书序列号（不同凭据用不同序列号，便于轮换用例区分）
     * @return 证书 PEM 文本
     */
    public static String selfSignedCertPem(KeyPair keyPair, String commonName, long serial) {
        try {
            AlgorithmIdentifier signatureAlgorithm = new AlgorithmIdentifier(
                    PKCSObjectIdentifiers.sha256WithRSAEncryption, DERNull.INSTANCE);
            X500Name subject = new X500Name("CN=" + commonName + ",O=aifei-pay-test");
            V3TBSCertificateGenerator generator = new V3TBSCertificateGenerator();
            generator.setSerialNumber(new ASN1Integer(BigInteger.valueOf(serial)));
            generator.setIssuer(subject);
            generator.setSubject(subject);
            generator.setStartDate(new Time(new Date(NOT_BEFORE_MILLIS)));
            generator.setEndDate(new Time(new Date(NOT_AFTER_MILLIS)));
            generator.setSignature(signatureAlgorithm);
            generator.setSubjectPublicKeyInfo(SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded()));
            TBSCertificate tbsCertificate = generator.generateTBSCertificate();
            byte[] tbsDer = tbsCertificate.getEncoded(ASN1Encoding.DER);
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(keyPair.getPrivate());
            signer.update(tbsDer);
            byte[] signature = signer.sign();
            Certificate certificate = new Certificate(tbsCertificate, signatureAlgorithm,
                    new DERBitString(signature));
            return pem("CERTIFICATE", certificate.getEncoded(ASN1Encoding.DER));
        } catch (Exception e) {
            throw new IllegalStateException("生成自签证书失败", e);
        }
    }

    /**
     * 计算待签串（支付宝公开规范：键名升序拼接 {@code k=v}，跳过空键空值，排除 {@code sign} 与
     * {@code sign_type}）。
     *
     * @param params 报文参数
     * @return 待签串
     */
    public static String signContent(Map<String, String> params) {
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);
        StringBuilder content = new StringBuilder();
        int index = 0;
        for (String key : keys) {
            if (key == null || key.isEmpty()) {
                continue;
            }
            String value = params.get(key);
            if (value == null || value.isEmpty()) {
                continue;
            }
            if ("sign".equals(key) || "sign_type".equals(key)) {
                continue;
            }
            if (index > 0) {
                content.append('&');
            }
            content.append(key).append('=').append(value);
            index++;
        }
        return content.toString();
    }

    /**
     * 用给定密钥对的私钥对待签串做 SHA256withRSA 签名。
     *
     * @param params  报文参数
     * @param keyPair 签名用的密钥对
     * @return Base64 签名值
     */
    public static String sign(Map<String, String> params, KeyPair keyPair) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(keyPair.getPrivate());
            signature.update(signContent(params).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("生成签名失败", e);
        }
    }

    /**
     * 给报文补上签名与签名类型，模拟支付宝发出的合法报文。
     *
     * @param params  报文参数（就地写入 {@code sign} / {@code sign_type}）
     * @param keyPair 支付宝侧的密钥对
     * @return 入参本身，便于链式使用
     */
    public static Map<String, String> signNotify(Map<String, String> params, KeyPair keyPair) {
        String sign = sign(params, keyPair);
        params.put("sign_type", "RSA2");
        params.put("sign", sign);
        return params;
    }

    /**
     * 构造 PEM 文本。
     *
     * @param type 内容类型，如 {@code CERTIFICATE}
     * @param der  DER 编码字节
     * @return PEM 文本
     */
    private static String pem(String type, byte[] der) {
        String base64 = Base64.getMimeEncoder(PEM_LINE_LENGTH, new byte[]{'\n'}).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----";
    }
}
