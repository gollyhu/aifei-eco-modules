/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.pay.core;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

/**
 * V3 应答 / 回调验签：message = timestamp\n nonce\n body\n，按 Wechatpay-Serial 取平台公钥验证。
 *
 * <p>官方文档：https://pay.weixin.qq.com/docs/merchant/development/interface-rules/signature-verification.html
 */
public class PayVerifier {

    private PayVerifier() {
    }

    /**
     * 校验微信支付 V3 应答 / 回调签名：以 {@code timestamp\nnonce\nbody\n} 为待签串，
     * 按 {@code Wechatpay-Serial} 取对应平台公钥做 SHA256withRSA 验证。
     *
     * <p><b>fail-closed 语义</b>：本方法在任何失败路径下都返回 {@code false} 而<b>不抛异常</b>，包括
     * 任一入参为 {@code null}、{@code serialNo} 查不到平台公钥、{@code signatureBase64} 不是合法
     * Base64 或签名不匹配。调用方<b>必须检查返回值</b>；仅用 try/catch 而不判断返回值会把伪造的
     * 回调当成合法请求处理。</p>
     *
     * @param keys            平台验签公钥数据源，按序列号取公钥；为 {@code null} 时返回 {@code false}
     * @param serialNo        应答 / 回调头 {@code Wechatpay-Serial} 的平台证书序列号
     * @param timestamp       应答 / 回调头 {@code Wechatpay-Timestamp}
     * @param nonce           应答 / 回调头 {@code Wechatpay-Nonce}
     * @param body            应答 / 回调的原始报文体（不含外层引号）
     * @param signatureBase64 应答 / 回调头 {@code Wechatpay-Signature} 的 Base64 值
     * @return 验签通过返回 {@code true}；参数缺失、序列号未找到对应平台公钥、签名格式非法或验签失败
     *         均返回 {@code false}（不抛异常），调用方必须检查返回值
     */
    public static boolean verify(PlatformKeySource keys, String serialNo,
                                 String timestamp, String nonce, String body, String signatureBase64) {
        if (keys == null || serialNo == null || timestamp == null || nonce == null
                || body == null || signatureBase64 == null) {
            return false;
        }
        PublicKey key = keys.bySerial(serialNo);
        if (key == null) {
            return false;
        }
        String message = timestamp + "\n" + nonce + "\n" + body + "\n";
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(key);
            signature.update(message.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (Exception e) {
            return false;
        }
    }
}
