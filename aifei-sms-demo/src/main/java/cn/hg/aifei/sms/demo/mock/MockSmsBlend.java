/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms.demo.mock;

import java.util.LinkedHashMap;
import java.util.List;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.api.callback.CallBack;
import org.dromara.sms4j.api.entity.SmsResponse;

/** demo 用 SmsBlend mock：预设响应，绝不真实发短信、零外网。 */
public final class MockSmsBlend implements SmsBlend {

    private final String configId;
    private final String supplier;
    private final SmsResponse response;

    /**
     * @param configId 通道标识
     * @param supplier 厂商字面量
     * @param response 预设响应
     */
    public MockSmsBlend(String configId, String supplier, SmsResponse response) {
        this.configId = configId;
        this.supplier = supplier;
        this.response = response;
    }

    @Override public String getConfigId() { return configId; }
    @Override public String getSupplier() { return supplier; }
    @Override public SmsResponse sendMessage(String phone, String content) { return response; }
    @Override public SmsResponse sendMessage(String phone, LinkedHashMap<String, String> variables) { return response; }
    @Override public SmsResponse sendMessage(String phone, String templateId, LinkedHashMap<String, String> variables) {
        return response;
    }
    @Override public SmsResponse massTexting(List<String> phones, String content) { return response; }
    @Override public SmsResponse massTexting(List<String> phones, String templateId, LinkedHashMap<String, String> variables) {
        return response;
    }
    @Override public void sendMessageAsync(String phone, String content, CallBack callback) { }
    @Override public void sendMessageAsync(String phone, String content) { }
    @Override public void sendMessageAsync(String phone, String templateId,
                                           LinkedHashMap<String, String> variables, CallBack callback) { }
    @Override public void sendMessageAsync(String phone, String templateId, LinkedHashMap<String, String> variables) { }
    @Override public void delayedMessage(String phone, String content, Long delayTime) { }
    @Override public void delayedMessage(String phone, String templateId,
                                         LinkedHashMap<String, String> variables, Long delayTime) { }
    @Override public void delayMassTexting(List<String> phones, String content, Long delayTime) { }
    @Override public void delayMassTexting(List<String> phones, String templateId,
                                           LinkedHashMap<String, String> variables, Long delayTime) { }
}
