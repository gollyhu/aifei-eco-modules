/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.pay.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import cn.aifei.weixin.common.WxApiException;
import cn.aifei.weixin.support.PayTestKeys;
import org.junit.Test;

/**
 * P0：AES-256-GCM 解密（官方回调 resource / 平台证书密文同算法）
 */
public class AesGcmTest {

    private static final String NONCE = "abcdef123456";
    private static final String AAD = "transaction";

    @Test
    public void decryptRoundtrip() {
        String plaintext = "{\"out_trade_no\":\"ORDER-1\",\"amount\":{\"payer_total\":100}}";
        String ciphertext = PayTestKeys.encrypt(PayTestKeys.API_V3_KEY, NONCE, AAD, plaintext);

        assertEquals(plaintext,
                AesGcm.decrypt(PayTestKeys.API_V3_KEY, NONCE, ciphertext, AAD));
    }

    @Test
    public void decryptWithoutAssociatedData() {
        String plaintext = "plain-text";
        String ciphertext = PayTestKeys.encrypt(PayTestKeys.API_V3_KEY, NONCE, null, plaintext);

        assertEquals(plaintext, AesGcm.decrypt(PayTestKeys.API_V3_KEY, NONCE, ciphertext, null));
    }

    @Test
    public void wrongKeyOrTamperedCiphertextThrows() {
        String ciphertext = PayTestKeys.encrypt(PayTestKeys.API_V3_KEY, NONCE, AAD, "data");
        String otherKey = "OtherKeyOtherKeyOtherKeyOtherKey12";

        try {
            AesGcm.decrypt(otherKey, NONCE, ciphertext, AAD);
            fail();
        } catch (WxApiException ignore) {
        }

        byte[] bytes = java.util.Base64.getDecoder().decode(ciphertext);
        bytes[bytes.length - 1] ^= 0x01;
        try {
            AesGcm.decrypt(PayTestKeys.API_V3_KEY, NONCE,
                    java.util.Base64.getEncoder().encodeToString(bytes), AAD);
            fail("密文被篡改应解密失败");
        } catch (WxApiException ignore) {
        }
    }
}
