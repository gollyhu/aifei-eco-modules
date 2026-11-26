/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms;

import cn.aifei.util.Prop;
import cn.hg.aifei.sms.aifei.SmsPlugin;
import cn.hg.aifei.sms.internal.SmsClients;
import cn.hg.aifei.sms.internal.SmsErrors;
import cn.hg.aifei.sms.internal.sms4j.SmsMappers;
import cn.hg.aifei.sms.support.MockBlendInstaller;
import cn.hg.aifei.sms.support.MockSmsBlend;
import java.util.LinkedHashMap;
import java.util.Map;
import org.dromara.sms4j.api.entity.SmsResponse;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * 异常契约测试（AC-08 / R-06）：参数、厂商、网络、状态、验签、解析异常逐字消息与 cause 保留。
 */
public class ExceptionContractTest {

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
    public void blankPhoneNumberUsesExactMessage() {
        installSuccess();
        plugin = new SmsPlugin(validProp());
        plugin.start();
        try {
            SmsKit.defaultClient().send(null, "SMS_001", null);
            fail("手机号为 null 应失败");
        } catch (SmsException e) {
            assertEquals("sms: phoneNumber must not be blank", e.getMessage());
        }
    }

    @Test
    public void blankTemplateIdUsesExactMessage() {
        installSuccess();
        plugin = new SmsPlugin(validProp());
        plugin.start();
        try {
            SmsKit.defaultClient().send("13800138000", "  ", null);
            fail("模板 ID 空白应失败");
        } catch (SmsException e) {
            assertEquals("sms: templateId must not be blank", e.getMessage());
        }
    }

    @Test
    public void supplierErrorUsesVendorCodeAndMessage() {
        SmsResponse response = new SmsResponse();
        response.setSuccess(false);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("Code", "INVALID_PARAM");
        data.put("Message", "template not found");
        response.setData(data);
        MockBlendInstaller.install(config -> new MockSmsBlend(config.getConfigId(), config.getSupplier(), response));
        plugin = new SmsPlugin(validProp());
        plugin.start();
        try {
            SmsKit.defaultClient().send("13800138000", "SMS_001", null);
            fail("厂商失败应答应抛异常");
        } catch (SmsException e) {
            assertEquals("sms: supplier error on channel aliyun, code: INVALID_PARAM,"
                    + " message: template not found", e.getMessage());
            assertFalse("消息不得含第三方类型名", e.getMessage().contains("org.dromara")
                    || e.getMessage().contains("SmsBlendException"));
        }
    }

    @Test
    public void networkErrorKeepsCause() {
        RuntimeException network = new RuntimeException("connection reset");
        MockBlendInstaller.install(config -> new MockSmsBlend(config.getConfigId(), config.getSupplier(), network));
        plugin = new SmsPlugin(validProp());
        plugin.start();
        try {
            SmsKit.defaultClient().send("13800138000", "SMS_001", null);
            fail("底层网络异常应抛模块异常");
        } catch (SmsException e) {
            assertEquals("sms: network error on channel aliyun, cause: connection reset", e.getMessage());
            assertNotNull("cause 必须保留", e.getCause());
            assertEquals(network, e.getCause());
        }
    }

    @Test
    public void channelNotFoundUsesExactMessage() {
        installSuccess();
        plugin = new SmsPlugin(validProp());
        plugin.start();
        try {
            SmsKit.use("missing");
            fail("未知通道应失败");
        } catch (SmsException e) {
            assertEquals("sms: channel not found: missing", e.getMessage());
        }
    }

    @Test
    public void verificationAndParseMessagesAreExact() {
        assertEquals("sms: receipt signature verification failed on channel aliyun",
                SmsErrors.verificationFailed("aliyun").getMessage());
        try {
            SmsMappers.toReceipt("aliyun", null, "raw");
            fail("空回调参数应解析失败");
        } catch (SmsException e) {
            assertEquals("sms: receipt parse failed on channel aliyun, cause: callback params is null",
                    e.getMessage());
            assertNotNull(e.getCause());
        }
    }

    private static void installSuccess() {
        SmsResponse response = new SmsResponse();
        response.setSuccess(true);
        response.setData("OK");
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
