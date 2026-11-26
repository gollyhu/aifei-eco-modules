/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms;

import cn.aifei.util.Prop;
import cn.hg.aifei.sms.aifei.SmsPlugin;
import cn.hg.aifei.sms.internal.SmsClients;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 配置校验测试（AC-05）：无通道、必填项缺失、类型未知、默认通道非法，消息均含键名与原值。
 */
public class ConfigValidationTest {

    @After
    public void tearDown() {
        SmsClients.clear();
    }

    @Test
    public void noChannelDeclaredFails() {
        try {
            new SmsPlugin(new Prop()).start();
            fail("无通道应启动失败");
        } catch (SmsException e) {
            assertEquals("sms: config sms.<name>.type is required but was: <empty>", e.getMessage());
        }
    }

    @Test
    public void missingRequiredKeyShowsFullKeyAndEmpty() {
        Prop prop = validProp();
        prop.getProperties().setProperty("sms.aliyun.signature", "");
        try {
            new SmsPlugin(prop).start();
            fail("必填项缺失应启动失败");
        } catch (SmsException e) {
            assertEquals("sms: config sms.aliyun.signature is required but was: <empty>", e.getMessage());
        }
    }

    @Test
    public void unknownTypeShowsKeyAndValue() {
        Prop prop = validProp();
        prop.getProperties().setProperty("sms.aliyun.type", "unknown-vendor");
        try {
            new SmsPlugin(prop).start();
            fail("未知类型应启动失败");
        } catch (SmsException e) {
            assertEquals("sms: unknown channel type for sms.aliyun.type: unknown-vendor", e.getMessage());
        }
    }

    @Test
    public void invalidDefaultChannelFails() {
        Prop prop = validProp();
        prop.getProperties().setProperty("sms.backup.type", "alibaba");
        prop.getProperties().setProperty("sms.backup.access-key", "ak-b");
        prop.getProperties().setProperty("sms.backup.secret-key", "sk-b");
        prop.getProperties().setProperty("sms.backup.signature", "sig-b");
        prop.getProperties().setProperty("sms.default-channel", "not-exists");
        try {
            new SmsPlugin(prop).start();
            fail("默认通道指向未知通道应失败");
        } catch (SmsException e) {
            assertEquals("sms: default-channel not-exists is invalid or missing", e.getMessage());
        }
    }

    @Test
    public void multiChannelWithoutDefaultFails() {
        Prop prop = validProp();
        prop.getProperties().setProperty("sms.backup.type", "alibaba");
        prop.getProperties().setProperty("sms.backup.access-key", "ak-b");
        prop.getProperties().setProperty("sms.backup.secret-key", "sk-b");
        prop.getProperties().setProperty("sms.backup.signature", "sig-b");
        try {
            new SmsPlugin(prop).start();
            fail("多通道缺默认通道应失败");
        } catch (SmsException e) {
            assertEquals("sms: default-channel <empty> is invalid or missing", e.getMessage());
        }
    }

    @Test
    public void nullPropFails() {
        try {
            new SmsPlugin(null);
            fail("prop 为空应失败");
        } catch (SmsException e) {
            assertEquals("sms: prop can not be null", e.getMessage());
        }
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
