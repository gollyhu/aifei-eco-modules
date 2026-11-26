/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms.internal.sms4j;

import cn.hg.aifei.sms.SmsClient;
import cn.hg.aifei.sms.SmsException;
import cn.hg.aifei.sms.SmsResult;
import cn.hg.aifei.sms.internal.SmsClients;
import cn.hg.aifei.sms.internal.SmsErrors;
import java.util.LinkedHashMap;
import java.util.Map;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.api.entity.SmsResponse;

/**
 * {@link SmsClient} 的发送实现（适配层）：校验 → 插件状态校验 → 调 sms4j {@link SmsBlend} → 结果 / 异常翻译。
 *
 * <p>无可变共享状态，可并发调用；底层 {@code SmsBlend} 的线程安全由 sms4j 保证，模块不额外加锁。</p>
 */
public final class SmsSender implements SmsClient {

    private final String channelName;
    private final SmsBlend blend;

    /**
     * @param channelName 通道标识
     * @param blend       sms4j 发送实例
     */
    public SmsSender(String channelName, SmsBlend blend) {
        this.channelName = channelName;
        this.blend = blend;
    }

    @Override
    public SmsResult send(String phoneNumber, String templateId, LinkedHashMap<String, String> variables) {
        requireBlank(phoneNumber, true);
        requireBlank(templateId, false);
        requireStarted();
        try {
            SmsResponse response = blend.sendMessage(phoneNumber, templateId, variables);
            requireSuccess(response);
            return SmsMappers.toResult(channelName, response);
        } catch (RuntimeException e) {
            throw translate(e);
        }
    }

    @Override
    public SmsResult send(String phoneNumber, String message) {
        requireBlank(phoneNumber, true);
        requireStarted();
        try {
            SmsResponse response = blend.sendMessage(phoneNumber, message);
            requireSuccess(response);
            return SmsMappers.toResult(channelName, response);
        } catch (RuntimeException e) {
            throw translate(e);
        }
    }

    @Override
    public String channelName() {
        return channelName;
    }

    private void requireBlank(String value, boolean phone) {
        if (value == null || value.trim().isEmpty()) {
            throw phone ? SmsErrors.blankPhoneNumber() : SmsErrors.blankTemplateId();
        }
    }

    private void requireStarted() {
        if (!SmsClients.isStarted()) {
            throw SmsErrors.notStarted();
        }
    }

    private void requireSuccess(SmsResponse response) {
        if (response == null || !response.isSuccess()) {
            Object data = response == null ? null : response.getData();
            throw SmsErrors.supplierError(channelName, vendorCode(data), vendorMessage(data), null);
        }
    }

    private RuntimeException translate(RuntimeException e) {
        if (e instanceof SmsException) {
            return e;
        }
        return SmsErrors.networkError(channelName, e);
    }

    private static String vendorCode(Object data) {
        if (data instanceof Map) {
            Object code = ((Map<?, ?>) data).get("Code");
            return code == null ? null : String.valueOf(code);
        }
        return null;
    }

    private static String vendorMessage(Object data) {
        if (data == null) {
            return null;
        }
        if (data instanceof Map) {
            Object msg = ((Map<?, ?>) data).get("Message");
            return msg == null ? String.valueOf(data) : String.valueOf(msg);
        }
        return String.valueOf(data);
    }
}
