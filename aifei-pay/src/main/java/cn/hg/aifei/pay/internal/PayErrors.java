/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import cn.aifei.log.Log;
import cn.hg.aifei.pay.AliPayException;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 异常构造与消息格式的唯一入口：全模块对外抛出的 {@link AliPayException} 都必须经本类创建。
 *
 * <p>消息模板见设计 §6.6；拼接配置值只允许经 {@link PayMask}。内核异常在收敛前 warn 一次，
 * 日志内容只含接口语义与错误码，不含报文与凭据。</p>
 */
public final class PayErrors {

    /**
     * 未装配 / 已停止的固定消息（设计 §6.2）。
     */
    public static final String NOT_INSTALLED = "支付宝客户端未装配或已停止";

    /**
     * 从内核报文文本中抽取错误码，如 {@code {"code":"ACQ.TRADE_NOT_EXIST"}}。
     */
    private static final Pattern JSON_CODE = Pattern.compile("\"code\"\\s*:\\s*\"([^\"]{1,128})\"");

    /**
     * 从内核报文文本中抽取错误描述。
     */
    private static final Pattern JSON_MSG = Pattern.compile("\"msg\"\\s*:\\s*\"([^\"]{0,512})\"");

    /**
     * 内核消息在异常文本中的长度上限，避免把整段响应体塞进消息。
     */
    private static final int MAX_KERNEL_MESSAGE = 512;

    /**
     * 超长消息的截断标记。
     */
    private static final String TRUNCATED = "...";

    /**
     * 内核未提供消息时的占位文本。
     */
    private static final String NO_KERNEL_MESSAGE = "（内核未提供消息）";

    /**
     * 禁止创建工具类实例。
     */
    private PayErrors() {
    }

    /**
     * 配置缺失。
     *
     * @param key 缺失的键名
     * @return 统一异常
     */
    public static AliPayException missingConfig(String key) {
        return new AliPayException("支付宝配置缺失：" + key);
    }

    /**
     * 配置非法。
     *
     * @param key    键名
     * @param value  原始值（按 {@link PayMask} 规则脱敏后进消息）
     * @param reason 失败原因
     * @return 统一异常
     */
    public static AliPayException invalidConfig(String key, String value, String reason) {
        return new AliPayException("支付宝配置非法：" + key + "=" + PayMask.mask(key, value) + "，" + reason);
    }

    /**
     * 回调地址缺失（下单期而非装配期的配置缺失，用同一「配置非法」模板表达）。
     *
     * @param action 动作语义，如 {@code precreate}
     * @param key    回调地址键名
     * @return 统一异常
     */
    public static AliPayException missingCallback(String action, String key) {
        return invalidConfig(key, "", action + " 缺少回调地址，请显式传入或配置 " + key);
    }

    /**
     * 网关与环境不一致。
     *
     * @param env          环境标签
     * @param expectedHost 环境对应的官方网关 host
     * @param actualHost   配置的网关 host
     * @return 统一异常
     */
    public static AliPayException gatewayEnvMismatch(String env, String expectedHost, String actualHost) {
        return new AliPayException("支付宝网关与环境不一致：env=" + env + "，gateway-url 的 host 必须为 "
                + expectedHost + "，实际为 " + actualHost);
    }

    /**
     * 未知 appId。
     *
     * @param appId 应用编号
     * @return 统一异常
     */
    public static AliPayException unknownApp(String appId) {
        return new AliPayException("支付宝应用未配置：appId=" + appId);
    }

    /**
     * 未装配 / 已停止。
     *
     * @return 统一异常，消息固定为 {@link #NOT_INSTALLED}
     */
    public static AliPayException notInstalled() {
        return new AliPayException(NOT_INSTALLED);
    }

    /**
     * 可选依赖缺失。
     *
     * @param groupId    缺失构件坐标
     * @param artifactId 缺失构件坐标
     * @param version    缺失构件坐标
     * @param cause      原始异常，可为 {@code null}
     * @return 统一异常，{@code code()} 为 {@code null}
     */
    public static AliPayException missingDependency(String groupId, String artifactId, String version,
                                                    Throwable cause) {
        return new AliPayException("缺少支付宝依赖：" + artifactId + "，请在使用方声明 optional 依赖 "
                + groupId + ":" + artifactId + ":" + version, cause);
    }

    /**
     * 内核调用失败：先按脱敏口径 warn 一次，再构造带错误码与成因的异常。
     *
     * @param action        接口语义，如「扫码预下单」
     * @param code          内核错误码，可为 {@code null}
     * @param kernelMessage 内核消息（调用方应先用 {@link #kernelMessageOf(Throwable)} 归一）
     * @param cause         原始异常，仅作 {@code cause}，不进入消息
     * @return 统一异常
     */
    public static AliPayException kernelFailure(String action, String code, String kernelMessage,
                                                Throwable cause) {
        Log.get(PayErrors.class).warn("支付宝接口调用失败：action=" + action + "，code=" + code);
        return new AliPayException("支付宝接口调用失败：" + action + "，code=" + code + "，message="
                + kernelMessage, code, cause);
    }

    /**
     * 内核调用失败的便捷入口：错误码与消息由本类从原始异常中提取。
     *
     * @param action 接口语义
     * @param cause  内核抛出的原始异常
     * @return 统一异常
     */
    public static AliPayException kernelFailure(String action, Throwable cause) {
        return kernelFailure(action, errorCodeOf(cause), kernelMessageOf(cause), cause);
    }

    /**
     * 回调验签失败。
     *
     * @param appId  应用编号
     * @param reason 失败原因（不得含报文内容与凭据）
     * @return 统一异常
     */
    public static AliPayException verifyFailed(String appId, String reason) {
        return new AliPayException("支付宝回调验签失败：appId=" + appId + "，原因=" + reason);
    }

    /**
     * 从内核异常中提取业务错误码。
     *
     * <p>本类不得 import 内核类型（`docs/design.md` §10 G-04），故按「方法名 + 返回类型」反射提取：
     * ① 优先取返回 {@code String} 的 {@code getCode()}（自检型桩可据此携带业务码）；
     * ② 否则从 {@code getResponseBody()} 的 JSON 中取 {@code "code"}；
     * ③ 最后从异常消息文本中取同名 JSON 字段（SDK 的 {@code getMessage()} 会带上响应体）。</p>
     *
     * @param cause 内核异常，可为 {@code null}
     * @return 业务错误码；无法提取时为 {@code null}
     */
    public static String errorCodeOf(Throwable cause) {
        if (cause == null) {
            return null;
        }
        String direct = stringMethod(cause, "getCode");
        if (direct != null) {
            return direct;
        }
        String fromBody = jsonField(stringMethod(cause, "getResponseBody"), JSON_CODE);
        if (fromBody != null) {
            return fromBody;
        }
        return jsonField(cause.getMessage(), JSON_CODE);
    }

    /**
     * 从内核异常中提取可读消息，超长截断。
     *
     * @param cause 内核异常，可为 {@code null}
     * @return 内核消息，永不为 {@code null}
     */
    public static String kernelMessageOf(Throwable cause) {
        if (cause == null) {
            return NO_KERNEL_MESSAGE;
        }
        String message = jsonField(stringMethod(cause, "getResponseBody"), JSON_MSG);
        if (message == null) {
            message = jsonField(cause.getMessage(), JSON_MSG);
        }
        if (message == null) {
            message = cause.getMessage();
        }
        if (message == null || message.trim().isEmpty()) {
            return NO_KERNEL_MESSAGE;
        }
        return truncate(message.trim());
    }

    /**
     * 反射调用返回 {@code String} 的无参方法。
     *
     * @param target     目标对象
     * @param methodName 方法名
     * @return 非空返回值（已 trim）；方法不存在、返回类型不是 {@code String} 或返回空串时为 {@code null}
     */
    private static String stringMethod(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            if (method.getReturnType() != String.class) {
                return null;
            }
            Object value = method.invoke(target);
            if (value == null) {
                return null;
            }
            String text = value.toString().trim();
            return text.isEmpty() ? null : text;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 JSON 文本中取第一个匹配字段。
     *
     * @param json     JSON 文本，可为 {@code null}
     * @param pattern 字段抽取式
     * @return 字段值；未命中时为 {@code null}
     */
    private static String jsonField(String json, Pattern pattern) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1).trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * 截断过长的内核消息。
     *
     * @param message 原始消息
     * @return 长度不超过 {@link #MAX_KERNEL_MESSAGE} 的消息
     */
    private static String truncate(String message) {
        if (message.length() <= MAX_KERNEL_MESSAGE) {
            return message;
        }
        return message.substring(0, MAX_KERNEL_MESSAGE) + TRUNCATED;
    }
}
