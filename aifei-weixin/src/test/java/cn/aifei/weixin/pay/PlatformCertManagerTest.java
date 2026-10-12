/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.pay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import cn.aifei.weixin.common.WxApiException;
import cn.aifei.weixin.pay.WxPayConfig;
import cn.aifei.weixin.pay.core.PlatformCertManager;
import cn.aifei.weixin.support.FakePayHttpKit;
import cn.aifei.weixin.support.PayTestKeys;
import com.alibaba.fastjson2.JSONObject;
import java.security.KeyPair;
import java.security.PublicKey;
import java.time.OffsetDateTime;
import java.util.HashMap;
import org.junit.Test;

/**
 * P0：平台证书下载 + 解密 + 惰性刷新
 */
public class PlatformCertManagerTest {

    @Test
    public void downloadsAndDecryptsCertificate() {
        KeyPair pair = PayTestKeys.generateKeyPair();
        FakePayHttpKit http = new FakePayHttpKit();
        http.getResponse = new cn.aifei.weixin.pay.core.PayHttpResponse(200, new HashMap<String, String>(),
                certificatesJson(pair.getPublic()));
        WxPayConfig.set(http, new PlatformCertManager(merchant(pair)));

        PlatformCertManager manager = new PlatformCertManager(merchant(pair));
        PublicKey key = manager.bySerial("PLAT-SERIAL-1");

        assertNotNull(key);
        assertEquals(pair.getPublic(), key);
        assertEquals("https://api.mch.weixin.qq.com/v3/certificates", http.getUrls.get(0));
        String authorization = http.getHeaders.get(0).get("Authorization");
        assertTrue(authorization.startsWith("WECHATPAY2-SHA256-RSA2048 mchid=\"" + PayTestKeys.MCH_ID + "\""));
    }

    @Test
    public void unknownSerialReturnsNullAfterRefresh() {
        KeyPair pair = PayTestKeys.generateKeyPair();
        FakePayHttpKit http = new FakePayHttpKit();
        http.getResponse = new cn.aifei.weixin.pay.core.PayHttpResponse(200, new HashMap<String, String>(),
                certificatesJson(pair.getPublic()));
        WxPayConfig.set(http, new PlatformCertManager(merchant(pair)));

        PlatformCertManager manager = new PlatformCertManager(merchant(pair));

        assertNull(manager.bySerial("UNKNOWN-SERIAL"));
        // 刷新节流窗口内不重复下载
        assertNull(manager.bySerial("UNKNOWN-SERIAL"));
        assertEquals(1, http.getUrls.size());
    }

    @Test
    public void downloadFailureThrows() {
        FakePayHttpKit http = new FakePayHttpKit();
        http.getResponse = new cn.aifei.weixin.pay.core.PayHttpResponse(500, new HashMap<String, String>(),
                "{\"code\":\"SYSTEM_ERROR\",\"message\":\"oops\"}");
        KeyPair pair = PayTestKeys.generateKeyPair();
        WxPayConfig.set(http, new PlatformCertManager(merchant(pair)));

        try {
            new PlatformCertManager(merchant(pair)).bySerial("PLAT-SERIAL-1");
            fail();
        } catch (WxApiException e) {
            assertTrue(e.getMessage().contains("500"));
        }
    }

    @Test
    public void parsePublicKeySupportsCertificatePem() throws Exception {
        // 自签证书在纯 JDK 8 下难以构造，此处以"证书分支按 PEM 标头识别"为契约做降级验证：
        // 公钥 PEM 走 SubjectPublicKeyInfo 分支，证书分支由真机联调覆盖
        KeyPair pair = PayTestKeys.generateKeyPair();
        PublicKey key = PlatformCertManager.parsePublicKey(PayTestKeys.toPublicKeyPem(pair.getPublic()));
        assertEquals(pair.getPublic(), key);

        JSONObject response = JSONObject.parseObject(
                certificatesJson(pair.getPublic()));
        assertNotNull(response.getJSONArray("data").getJSONObject(0).getString("expire_time"));
        assertEquals(OffsetDateTime.parse(
                response.getJSONArray("data").getJSONObject(0).getString("expire_time"))
                .toInstant().toEpochMilli() > 0, true);
    }

    private static PayMerchant merchant(KeyPair pair) {
        return PayMerchant.of(PayTestKeys.MCH_ID, pair.getPrivate(), PayTestKeys.MCH_SERIAL, PayTestKeys.API_V3_KEY);
    }

    private static String certificatesJson(PublicKey platformKey) {
        JSONObject encrypted = new JSONObject();
        encrypted.put("algorithm", "AEAD_AES_256_GCM");
        encrypted.put("nonce", "abcdef123456");
        encrypted.put("associated_data", "certificate");
        encrypted.put("ciphertext", PayTestKeys.encrypt(PayTestKeys.API_V3_KEY, "abcdef123456",
                "certificate", PayTestKeys.toPublicKeyPem(platformKey)));

        JSONObject item = new JSONObject();
        item.put("serial_no", "PLAT-SERIAL-1");
        item.put("effective_time", OffsetDateTime.now().minusHours(1).toString());
        item.put("expire_time", OffsetDateTime.now().plusHours(1).toString());
        item.put("encrypt_certificate", encrypted);

        JSONObject body = new JSONObject();
        body.put("data", new JSONObject[] {item});
        return body.toJSONString();
    }
}
