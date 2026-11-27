/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms;

import cn.aifei.util.Prop;
import cn.hg.aifei.sms.aifei.SmsPlugin;
import cn.hg.aifei.sms.internal.SmsClients;
import cn.hg.aifei.sms.support.MockBlendInstaller;
import cn.hg.aifei.sms.support.MockSmsBlend;
import org.dromara.sms4j.api.entity.SmsResponse;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 装配生命周期测试：start/stop 幂等、失败回滚、stop 后门面调用失败。
 */
public class PluginLifecycleTest {

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
    public void sendAvailableAfterStart() {
        installMock();
        plugin = new SmsPlugin(validProp());
        plugin.start();
        assertTrue(SmsClients.isStarted());
        assertTrue(SmsKit.defaultClient().send("13800138000", "SMS_001", null).isSuccess());
    }

    @Test
    public void stopThenSendFailsWithReadableMessage() {
        installMock();
        plugin = new SmsPlugin(validProp());
        plugin.start();
        plugin.stop();
        try {
            SmsKit.defaultClient().send("13800138000", "SMS_001", null);
            fail("stop 后发送应失败");
        } catch (SmsException e) {
            assertEquals("sms: plugin not started or stopped, call SmsPlugin.start() first", e.getMessage());
        }
    }

    @Test
    public void stopThenChannelsAndUseFail() {
        installMock();
        plugin = new SmsPlugin(validProp());
        plugin.start();
        plugin.stop();
        try {
            SmsKit.channels();
            fail("stop 后 channels 应失败");
        } catch (SmsException e) {
            assertTrue(e.getMessage().contains("plugin not started or stopped"));
        }
        try {
            SmsKit.use("aliyun");
            fail("stop 后 use 应失败");
        } catch (SmsException e) {
            assertTrue(e.getMessage().contains("plugin not started or stopped"));
        }
    }

    @Test
    public void repeatedStartAndStopAreIdempotent() {
        installMock();
        plugin = new SmsPlugin(validProp());
        plugin.start();
        plugin.start();
        assertEquals(1, SmsKit.channels().size());
        plugin.stop();
        plugin.stop();
        assertTrue(!SmsClients.isStarted());
    }

    @Test
    public void failedStartRollsBackAndCanRecover() {
        installMock();
        Prop invalid = validProp();
        invalid.getProperties().setProperty("sms.aliyun.access-key", "");
        SmsPlugin bad = new SmsPlugin(invalid);
        try {
            bad.start();
            fail("非法配置应启动失败");
        } catch (SmsException e) {
            assertTrue(e.getMessage().contains("sms.aliyun.access-key"));
        }
        assertTrue("失败后注册表应清空", !SmsClients.isStarted());

        plugin = new SmsPlugin(validProp());
        plugin.start();
        assertTrue(SmsKit.defaultClient().send("13800138000", "SMS_001", null).isSuccess());
    }

    private static void installMock() {
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
