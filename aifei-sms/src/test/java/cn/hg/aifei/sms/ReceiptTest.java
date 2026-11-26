/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms;

import cn.aifei.core.Input;
import cn.aifei.core.Output;
import cn.aifei.util.Prop;
import cn.hg.aifei.sms.aifei.SmsHandler;
import cn.hg.aifei.sms.aifei.SmsIo;
import cn.hg.aifei.sms.aifei.SmsPlugin;
import cn.hg.aifei.sms.internal.SmsClients;
import cn.hg.aifei.sms.spi.SmsReceipt;
import cn.hg.aifei.sms.support.MockBlendInstaller;
import cn.hg.aifei.sms.support.MockSmsBlend;
import cn.hg.aifei.sms.support.RecordingReceiptListener;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.dromara.sms4j.api.entity.SmsResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 回执测试（AC-07 / R-05）：真实 SmsHandler 验签分流、回执解析、监听器交付与响应契约。
 */
public class ReceiptTest {

    private static final String SECRET = "receipt-secret";

    private SmsPlugin plugin;
    private MemorySmsIo io;
    private RecordingReceiptListener listener;
    private SmsHandler<Input, Output> handler;

    @Before
    public void setUp() {
        SmsResponse response = new SmsResponse();
        response.setSuccess(true);
        MockBlendInstaller.install(config -> new MockSmsBlend(config.getConfigId(), config.getSupplier(), response));
        Prop prop = new Prop();
        prop.getProperties().setProperty("sms.aliyun.type", "alibaba");
        prop.getProperties().setProperty("sms.aliyun.access-key", "ak");
        prop.getProperties().setProperty("sms.aliyun.secret-key", "sk");
        prop.getProperties().setProperty("sms.aliyun.signature", "sig");
        prop.getProperties().setProperty("sms.aliyun.access-key-secret", SECRET);
        plugin = new SmsPlugin(prop);
        plugin.start();
        io = new MemorySmsIo();
        listener = new RecordingReceiptListener();
        List<cn.hg.aifei.sms.spi.SmsReceiptListener> listeners =
                new ArrayList<cn.hg.aifei.sms.spi.SmsReceiptListener>();
        listeners.add(listener);
        handler = new SmsHandler<Input, Output>(io, listeners);
    }

    @After
    public void tearDown() {
        if (plugin != null) {
            plugin.stop();
        }
        SmsClients.clear();
        MockBlendInstaller.reset();
    }

    @Test
    public void verifiedReceiptIsParsedAndDelivered() throws Exception {
        Map<String, String> params = signedParams();
        io.params = params;
        io.body = "ResultCode=OK&MessageId=900619";
        handler.handle("/sms/callback/aliyun", null, null);

        assertEquals(200, io.status);
        assertEquals("{\"Status\":\"SUCCESS\"}", io.json);
        assertEquals(1, listener.receipts().size());
        SmsReceipt receipt = listener.receipts().get(0);
        assertEquals("aliyun", receipt.getChannelName());
        assertEquals("OK", receipt.getStatus());
        assertEquals("900619", receipt.getRequestId());
        assertEquals("13800138000", receipt.getPhoneNumber());
        assertTrue(receipt.getReportTime() > 0L);
        assertEquals(io.body, receipt.getRawBody());
    }

    @Test
    public void badSignatureIsRejectedWithoutDelivery() throws Exception {
        Map<String, String> params = signedParams();
        params.put("signature", "invalid-signature");
        io.params = params;
        io.body = "raw";
        handler.handle("/sms/callback/aliyun", null, null);

        assertEquals(403, io.status);
        assertEquals("{\"Status\":\"FAIL\"}", io.json);
        assertTrue(listener.receipts().isEmpty());
    }

    @Test
    public void missingSecretIsRejected() throws Exception {
        Map<String, String> options = new LinkedHashMap<String, String>();
        options.put("access-key", "ak");
        options.put("secret-key", "sk");
        options.put("signature", "sig");
        SmsClients.register("aliyun", SmsKit.use("aliyun"),
                new cn.hg.aifei.sms.internal.SmsChannelConfig("aliyun", "alibaba", options));
        io.params = signedParams();
        io.body = "raw";
        handler.handle("/sms/callback/aliyun", null, null);
        assertEquals(403, io.status);
        assertTrue(listener.receipts().isEmpty());
    }

    @Test
    public void unknownChannelReturns404() throws Exception {
        io.params = signedParams();
        io.body = "raw";
        handler.handle("/sms/callback/unknown", null, null);
        assertEquals(404, io.status);
        assertEquals("{\"Status\":\"FAIL\"}", io.json);
        assertTrue(listener.receipts().isEmpty());
    }

    @Test
    public void listenerFailureReturns500() throws Exception {
        io.params = signedParams();
        io.body = "raw";
        listener.setThrowOnReceipt(true);
        handler.handle("/sms/callback/aliyun", null, null);
        assertEquals(500, io.status);
        assertEquals("{\"Status\":\"FAIL\"}", io.json);
        assertNotNull(handler.lastListenerError());
    }

    @Test
    public void nonCallbackPathIsDelegatedToNext() throws Exception {
        handler.handle("/other/path", null, null);
        assertEquals("未命中回调路径不应写回执响应", 0, io.status);
    }

    private static Map<String, String> signedParams() throws Exception {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("ResultCode", "OK");
        params.put("MessageId", "900619");
        params.put("PhoneNumber", "13800138000");
        params.put("ReportTime", "2026-01-01 10:00:00");
        params.put("signature", sign(params, SECRET));
        return params;
    }

    private static String sign(Map<String, String> params, String secret) throws Exception {
        List<String> keys = new ArrayList<String>(params.keySet());
        keys.remove("signature");
        Collections.sort(keys);
        StringBuilder canonical = new StringBuilder();
        for (String key : keys) {
            canonical.append(key).append(params.get(key));
        }
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return Base64.getEncoder().encodeToString(
                mac.doFinal(canonical.toString().getBytes(StandardCharsets.UTF_8)));
    }

    /** 内存传输桥接：不接触任何网络，直接记录响应状态码与响应体。 */
    private static final class MemorySmsIo implements SmsIo<Input> {

        private Map<String, String> params;
        private String body;
        private int status;
        private String json;

        @Override
        public Map<String, String> params(Input in) {
            return params;
        }

        @Override
        public String body(Input in) {
            return body;
        }

        @Override
        public void reply(Input in, int status, String json) {
            this.status = status;
            this.json = json;
        }
    }
}
