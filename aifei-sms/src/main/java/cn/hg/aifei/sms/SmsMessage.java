/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms;

import cn.hg.aifei.sms.internal.SmsErrors;
import java.util.LinkedHashMap;

/**
 * 发送消息值对象（不可变）：承载手机号、模板 ID 与模板变量。
 *
 * <p>发送主路径使用 {@link SmsClient#send(String, String, LinkedHashMap)} 三参形态；本类保留消息
 * 语义完整性与未来扩展能力，消费方可先构造再取其字段传入。</p>
 */
public final class SmsMessage {

    private final String phoneNumber;
    private final String templateId;
    private final LinkedHashMap<String, String> variables;

    /**
     * 构造消息并校验必填项。
     *
     * @param phoneNumber 手机号，不得为空白
     * @param templateId  模板 ID，不得为空白
     * @param variables   模板变量，可为 {@code null}（内部按空处理）
     * @throws SmsException 手机号或模板 ID 为空白
     */
    public SmsMessage(String phoneNumber, String templateId, LinkedHashMap<String, String> variables) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            throw SmsErrors.blankPhoneNumber();
        }
        if (templateId == null || templateId.trim().isEmpty()) {
            throw SmsErrors.blankTemplateId();
        }
        this.phoneNumber = phoneNumber;
        this.templateId = templateId;
        this.variables = variables == null
                ? new LinkedHashMap<String, String>() : new LinkedHashMap<String, String>(variables);
    }

    /**
     * @return 手机号
     */
    public String getPhoneNumber() {
        return phoneNumber;
    }

    /**
     * @return 模板 ID
     */
    public String getTemplateId() {
        return templateId;
    }

    /**
     * @return 模板变量的防御性副本（永不为 {@code null}）
     */
    public LinkedHashMap<String, String> getVariables() {
        return new LinkedHashMap<String, String>(variables);
    }
}
