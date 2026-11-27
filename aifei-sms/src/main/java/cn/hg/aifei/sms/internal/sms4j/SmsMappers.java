/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms.internal.sms4j;

import cn.hg.aifei.sms.SmsResult;
import cn.hg.aifei.sms.internal.SmsErrors;
import cn.hg.aifei.sms.spi.SmsReceipt;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.dromara.sms4j.api.entity.SmsResponse;

/**
 * 结果 / 回执映射适配：{@code SmsResponse} → {@link SmsResult}，厂商回调参数 → {@link SmsReceipt}。
 *
 * <p>字段来源与缺省处理遵循统一口径；映射只读取 sms4j 结果对象，不反向暴露其类型。</p>
 */
public final class SmsMappers {

    private static final String[] REQUEST_ID_KEYS = {"BizId", "MessageId", "RequestId", "bizId", "messageId"};
    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile(
            "(?:\\\"?(?:BizId|MessageId|RequestId)\\\"?\\s*[:=]\\s*\\\"?)([^\\\",}&\\s]+)");

    private SmsMappers() {
    }

    /**
     * sms4j 发送响应 → 发送结果。
     *
     * @param channel  通道标识
     * @param response sms4j 响应
     * @return 发送结果
     */
    public static SmsResult toResult(String channel, SmsResponse response) {
        if (response == null) {
            return new SmsResult(channel, false, null, null);
        }
        Object data = response.getData();
        String message = data == null ? null : String.valueOf(data);
        return new SmsResult(channel, response.isSuccess(), message, extractRequestId(data));
    }

    /**
     * 厂商回调参数 → 回执值对象。
     *
     * @param channel 路由得到的通道标识
     * @param params  回调参数（验签已通过）
     * @param rawBody 原始回调报文
     * @return 回执值对象
     * @throws cn.hg.aifei.sms.SmsException 回调参数为空（解析失败，保留 cause）
     */
    public static SmsReceipt toReceipt(String channel, Map<String, String> params, String rawBody) {
        if (params == null) {
            throw SmsErrors.parseFailed(channel, new IllegalArgumentException("callback params is null"));
        }
        String status = firstNonBlank(params.get("ResultCode"), params.get("Status"));
        String requestId = firstNonBlank(params.get("MessageId"), params.get("BizId"));
        String phone = firstNonBlank(params.get("PhoneNumber"), params.get("DestCode"));
        long reportTime = parseReportTime(firstNonBlank(params.get("ReportTime"), params.get("NotifyTime")));
        return new SmsReceipt(channel, blankIfNull(phone), blankIfNull(status),
                blankIfNull(requestId), reportTime, rawBody);
    }

    private static String extractRequestId(Object data) {
        if (data == null) {
            return null;
        }
        if (data instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) data;
            for (String key : REQUEST_ID_KEYS) {
                Object value = map.get(key);
                if (value != null && !String.valueOf(value).trim().isEmpty()) {
                    return String.valueOf(value);
                }
            }
            return null;
        }
        Matcher matcher = REQUEST_ID_PATTERN.matcher(String.valueOf(data));
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first;
        }
        return second != null && !second.trim().isEmpty() ? second : null;
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value;
    }

    private static long parseReportTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return System.currentTimeMillis();
        }
        String text = value.trim();
        if (text.matches("\\d+")) {
            long number = Long.parseLong(text);
            return number < 100000000000L ? number * 1000L : number;
        }
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date parsed = format.parse(text);
            return parsed == null ? System.currentTimeMillis() : parsed.getTime();
        } catch (ParseException e) {
            return System.currentTimeMillis();
        }
    }
}
