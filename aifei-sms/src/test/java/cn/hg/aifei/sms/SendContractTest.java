/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms;

import cn.hg.aifei.sms.aifei.SmsPlugin;
import cn.hg.aifei.sms.internal.SmsClients;
import cn.hg.aifei.sms.support.MockBlendInstaller;
import cn.hg.aifei.sms.support.MockSmsBlend;
import cn.aifei.util.Prop;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.dromara.sms4j.api.entity.SmsResponse;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 发送契约测试（AC-01 / AC-02 / AC-03 / Q-08）：模板化发送、多通道独立实例、
 * 换厂商业务代码零改动、并发无状态污染；全部经 mock 注入，零外网。
 */
public class SendContractTest {

    private SmsPlugin plugin;

    @After
    public void tearDown() {
        if (plugin != null) {
            plugin.stop();
        }
        SmsClients.clear();
        MockBlendInstaller.reset();
    }

    @Test
    public void templateSendReturnsResultWithRequestId() {
        installResponse("9006190001", "OK");
        plugin = new SmsPlugin(validProp());
        plugin.start();

        LinkedHashMap<String, String> variables = new LinkedHashMap<String, String>();
        variables.put("code", "123456");
        SmsResult result = SmsKit.defaultClient().send("13800138000", "SMS_001", variables);

        assertEquals("aliyun", result.getChannelName());
        assertTrue(result.isSuccess());
        assertEquals("9006190001", result.getRequestId());
        assertTrue(result.getMessage().contains("OK"));
    }

    @Test
    public void contentSendUsesTwoArgApi() {
        installResponse("9006190002", "sent");
        plugin = new SmsPlugin(validProp());
        plugin.start();

        SmsResult result = SmsKit.send("13800138000", "hello", null);
        assertTrue(result.isSuccess());
        assertEquals("9006190002", result.getRequestId());
    }

    @Test
    public void namedChannelsAreIndependentInstances() {
        installResponse("9006190003", "OK");
        Prop prop = validProp();
        prop.getProperties().setProperty("sms.tencent.type", "alibaba");
        prop.getProperties().setProperty("sms.tencent.access-key", "ak-t");
        prop.getProperties().setProperty("sms.tencent.secret-key", "sk-t");
        prop.getProperties().setProperty("sms.tencent.signature", "sig-t");
        prop.getProperties().setProperty("sms.default-channel", "aliyun");
        plugin = new SmsPlugin(prop);
        plugin.start();

        SmsClient first = SmsKit.use("aliyun");
        SmsClient second = SmsKit.use("tencent");
        assertNotNull(first);
        assertNotNull(second);
        assertTrue(first != second);
        assertEquals("aliyun", first.channelName());
        assertEquals("tencent", second.channelName());
        Set<String> channels = SmsKit.channels();
        assertEquals(2, channels.size());
        assertTrue(channels.contains("aliyun") && channels.contains("tencent"));
        assertEquals("aliyun", SmsKit.defaultClient().channelName());
    }

    @Test
    public void defaultChannelSwitchChangesOnlyConfiguration() {
        installResponse("9006190004", "OK");
        Prop prop = validProp();
        prop.getProperties().setProperty("sms.backup.type", "alibaba");
        prop.getProperties().setProperty("sms.backup.access-key", "ak-b");
        prop.getProperties().setProperty("sms.backup.secret-key", "sk-b");
        prop.getProperties().setProperty("sms.backup.signature", "sig-b");
        prop.getProperties().setProperty("sms.default-channel", "backup");
        plugin = new SmsPlugin(prop);
        plugin.start();

        assertEquals("backup", SmsKit.defaultClient().channelName());
        assertEquals("aliyun", SmsKit.use("aliyun").channelName());
    }

    @Test
    public void concurrentSendHasNoStatePollution() throws Exception {
        installResponse("9006190005", "OK");
        plugin = new SmsPlugin(validProp());
        plugin.start();

        final SmsClient client = SmsKit.defaultClient();
        final int threads = 8;
        final CountDownLatch latch = new CountDownLatch(threads);
        final AtomicInteger success = new AtomicInteger();
        Throwable[] errors = new Throwable[threads];
        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            final int index = i;
            workers[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        SmsResult result = client.send("13800138000", "SMS_CONC", null);
                        if (result.isSuccess() && "9006190005".equals(result.getRequestId())) {
                            success.incrementAndGet();
                        }
                    } catch (Throwable t) {
                        errors[index] = t;
                    } finally {
                        latch.countDown();
                    }
                }
            });
            workers[i].start();
        }
        latch.await();
        for (Throwable error : errors) {
            assertEquals("并发发送不应抛异常", null, error);
        }
        assertEquals(threads, success.get());
    }

    private static void installResponse(String requestId, String message) {
        final SmsResponse response = new SmsResponse();
        response.setSuccess(true);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("BizId", requestId);
        data.put("Message", message);
        response.setData(data);
        response.setConfigId("aliyun");
        MockBlendInstaller.install(config -> new MockSmsBlend(config.getConfigId(), config.getSupplier(), response));
    }

    private static Prop validProp() {
        Prop prop = new Prop();
        prop.getProperties().setProperty("sms.aliyun.type", "alibaba");
        prop.getProperties().setProperty("sms.aliyun.access-key", "ak");
        prop.getProperties().setProperty("sms.aliyun.secret-key", "sk");
        prop.getProperties().setProperty("sms.aliyun.signature", "sig");
        return prop;
    }
}
