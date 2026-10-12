/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.pay.core;

import cn.aifei.weixin.common.WxApiException;
import cn.aifei.weixin.pay.WxPayConfig;
import cn.aifei.weixin.pay.PayMerchant;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import java.io.ByteArrayInputStream;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.spec.X509EncodedKeySpec;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 平台证书 / 公钥管理：GET /v3/certificates 下载 + APIv3 密钥解密 + 过期淘汰。
 *
 * <p>无后台定时任务，采取惰性刷新：bySerial 未命中或全部过期时触发一次刷新，
 * 60 秒内不重复刷新（防未知序列号反复触发下载）。单实例持有一个商户凭据。
 *
 * <p>官方文档：https://pay.weixin.qq.com/docs/merchant/apis/certificate-transfer-privatization/api-certificate/api-certificate-list.html
 */
public class PlatformCertManager implements PlatformKeySource {

    static final String CERTIFICATES_PATH = "v3/certificates";

    /** 提前 5 分钟视为过期，避免边界失效 */
    private static final long EXPIRE_AHEAD_MILLIS = 5 * 60 * 1000L;

    /** 两次刷新之间的最小间隔 */
    private static final long REFRESH_MIN_INTERVAL_MILLIS = 60 * 1000L;

    private static class Entry {
        final PublicKey key;
        final long expireAtMillis;

        Entry(PublicKey key, long expireAtMillis) {
            this.key = key;
            this.expireAtMillis = expireAtMillis;
        }
    }

    private final PayMerchant merchant;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private volatile long lastRefreshAtMillis;

    public PlatformCertManager(PayMerchant merchant) {
        this.merchant = merchant;
    }

    @Override
    public PublicKey bySerial(String serialNo) {
        PublicKey key = validKey(serialNo);
        if (key != null) {
            return key;
        }
        refresh();
        return validKey(serialNo);
    }

    /**
     * 强制刷新平台证书缓存（多实例部署仍各自持有，证书序列号全局一致，无需共享）
     */
    public synchronized void refresh() {
        long now = System.currentTimeMillis();
        if (now - lastRefreshAtMillis < REFRESH_MIN_INTERVAL_MILLIS && !entries.isEmpty()) {
            return;
        }
        lastRefreshAtMillis = now;

        String url = cn.aifei.weixin.pay.PayApi.API_BASE + CERTIFICATES_PATH;
        PayHttpResponse response = WxPayConfig.http().get(url, authorizationHeaders("GET", CERTIFICATES_PATH, ""));
        if (!response.is2xx()) {
            throw new WxApiException("下载平台证书失败 [HTTP " + response.getStatusCode() + "] " + response.getBody());
        }
        JSONArray data = JSONObject.parseObject(response.getBody()).getJSONArray("data");
        if (data == null) {
            return;
        }
        long nowMillis = now;
        for (int i = 0; i < data.size(); i++) {
            JSONObject item = data.getJSONObject(i);
            String serial = item.getString("serial_no");
            JSONObject encrypted = item.getJSONObject("encrypt_certificate");
            String pem = AesGcm.decrypt(merchant.getApiV3Key(), encrypted.getString("nonce"),
                    encrypted.getString("ciphertext"), encrypted.getString("associated_data"));
            long expireAt = parseExpireAt(item.getString("expire_time"), nowMillis);
            entries.put(serial, new Entry(parsePublicKey(pem), expireAt));
        }
    }

    /**
     * 请求头：验签依赖 Wechatpay-Serial，此处仅需 Authorization
     */
    Map<String, String> authorizationHeaders(String method, String canonicalUrl, String body) {
        Map<String, String> headers = new ConcurrentHashMap<>();
        headers.put("Authorization",
                PaySignature.buildAuthorization(merchant, method, "/" + canonicalUrl, body));
        headers.put("Accept", "application/json");
        return headers;
    }

    private PublicKey validKey(String serialNo) {
        Entry entry = serialNo == null ? null : entries.get(serialNo);
        return entry != null && System.currentTimeMillis() < entry.expireAtMillis ? entry.key : null;
    }

    private static long parseExpireAt(String expireTime, long fallbackMillis) {
        if (expireTime == null || expireTime.isEmpty()) {
            return fallbackMillis + 12 * 60 * 60 * 1000L;
        }
        return OffsetDateTime.parse(expireTime).toInstant().toEpochMilli() - EXPIRE_AHEAD_MILLIS;
    }

    /**
     * 解密内容支持 X.509 证书 PEM 与公钥 PEM（微信支付公钥 pub_key.pem）两种格式
     */
    public static PublicKey parsePublicKey(String pem) {
        try {
            String base64 = pem.replaceAll("-----[A-Z ]*-----", "").replaceAll("\\s", "");
            if (pem.contains("BEGIN CERTIFICATE")) {
                CertificateFactory factory = CertificateFactory.getInstance("X.509");
                return factory.generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(base64)))
                        .getPublicKey();
            }
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new WxApiException("平台证书 / 公钥解析失败", e);
        }
    }
}
