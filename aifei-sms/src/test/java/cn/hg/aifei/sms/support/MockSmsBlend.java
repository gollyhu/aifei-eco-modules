/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms.support;

import java.util.LinkedHashMap;
import java.util.List;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.api.callback.CallBack;
import org.dromara.sms4j.api.entity.SmsResponse;

/**
 * 测试用 {@link SmsBlend} mock：同步发送返回预设响应或抛出预设异常，全程零外网。
 */
public final class MockSmsBlend implements SmsBlend {

    private final String configId;
    private final String supplier;
    private final SmsResponse response;
    private final RuntimeException failure;

    /**
     * @param configId 通道标识
     * @param supplier 厂商字面量
     * @param response 预设发送响应
     */
    public MockSmsBlend(String configId, String supplier, SmsResponse response) {
        this.configId = configId;
        this.supplier = supplier;
        this.response = response;
        this.failure = null;
    }

    /**
     * @param configId 通道标识
     * @param supplier 厂商字面量
     * @param failure  同步发送时抛出的预设异常
     */
    public MockSmsBlend(String configId, String supplier, RuntimeException failure) {
        this.configId = configId;
        this.supplier = supplier;
        this.response = null;
        this.failure = failure;
    }

    @Override
    public String getConfigId() {
        return configId;
    }

    @Override
    public String getSupplier() {
        return supplier;
    }

    @Override
    public SmsResponse sendMessage(String phone, String content) {
        return result();
    }

    @Override
    public SmsResponse sendMessage(String phone, LinkedHashMap<String, String> variables) {
        return result();
    }

    @Override
    public SmsResponse sendMessage(String phone, String templateId, LinkedHashMap<String, String> variables) {
        return result();
    }

    @Override
    public SmsResponse massTexting(List<String> phones, String content) {
        return result();
    }

    @Override
    public SmsResponse massTexting(List<String> phones, String templateId, LinkedHashMap<String, String> variables) {
        return result();
    }

    @Override
    public void sendMessageAsync(String phone, String content, CallBack callback) {
        result();
    }

    @Override
    public void sendMessageAsync(String phone, String content) {
        result();
    }

    @Override
    public void sendMessageAsync(String phone, String templateId,
                                 LinkedHashMap<String, String> variables, CallBack callback) {
        result();
    }

    @Override
    public void sendMessageAsync(String phone, String templateId, LinkedHashMap<String, String> variables) {
        result();
    }

    @Override
    public void delayedMessage(String phone, String content, Long delayTime) {
        result();
    }

    @Override
    public void delayedMessage(String phone, String templateId,
                               LinkedHashMap<String, String> variables, Long delayTime) {
        result();
    }

    @Override
    public void delayMassTexting(List<String> phones, String content, Long delayTime) {
        result();
    }

    @Override
    public void delayMassTexting(List<String> phones, String templateId,
                                 LinkedHashMap<String, String> variables, Long delayTime) {
        result();
    }

    private SmsResponse result() {
        if (failure != null) {
            throw failure;
        }
        return response;
    }
}
