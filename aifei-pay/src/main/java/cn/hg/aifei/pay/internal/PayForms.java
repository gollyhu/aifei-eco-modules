/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 报文参数的唯一解析点与防御性拷贝点。
 *
 * <p>同步 {@code return_url} 的 query string 与异步 {@code notify_url} 的 form body 都是
 * {@code application/x-www-form-urlencoded}，在模块内统一收敛为 {@code Map<String,String>}。</p>
 */
public final class PayForms {

    /**
     * 解码字符集。
     */
    private static final String ENCODING = "UTF-8";

    /**
     * 键值对分隔符。
     */
    private static final char PAIR_SEPARATOR = '&';

    /**
     * 键值分隔符。
     */
    private static final char VALUE_SEPARATOR = '=';

    /**
     * 禁止创建工具类实例。
     */
    private PayForms() {
    }

    /**
     * 解析 urlencoded 报文。
     *
     * @param formBody 报文文本，可为 {@code null} / 空白
     * @return 新的可变 Map（保持出现顺序），永不为 {@code null}
     * @throws IllegalArgumentException 报文含非法转义
     */
    public static Map<String, String> parse(String formBody) {
        Map<String, String> params = new LinkedHashMap<>();
        if (formBody == null || formBody.trim().isEmpty()) {
            return params;
        }
        String body = formBody.trim();
        int start = 0;
        while (start <= body.length()) {
            int end = body.indexOf(PAIR_SEPARATOR, start);
            String pair = end < 0 ? body.substring(start) : body.substring(start, end);
            putPair(params, pair);
            if (end < 0) {
                break;
            }
            start = end + 1;
        }
        return params;
    }

    /**
     * 防御性拷贝：内核的 {@code getSignCheckContentV1} 会原地 {@code remove("sign")} /
     * {@code remove("sign_type")}，故每次验签前都必须用新的可变副本（`docs/design.md` §2.4.4）。
     *
     * @param params 原始参数，可为 {@code null}
     * @return 新的可变 Map；{@code null} 键跳过、空值保留
     */
    public static Map<String, String> copy(Map<String, String> params) {
        Map<String, String> copy = new LinkedHashMap<>();
        if (params == null) {
            return copy;
        }
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getKey() != null) {
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        return copy;
    }

    /**
     * 解析并写入单个键值对。
     *
     * @param params 目标 Map
     * @param pair   形如 {@code key=value} 的片段
     */
    private static void putPair(Map<String, String> params, String pair) {
        if (pair.isEmpty()) {
            return;
        }
        int separator = pair.indexOf(VALUE_SEPARATOR);
        String key = separator < 0 ? pair : pair.substring(0, separator);
        String value = separator < 0 ? "" : pair.substring(separator + 1);
        if (key.isEmpty()) {
            return;
        }
        params.put(decode(key), decode(value));
    }

    /**
     * 按 UTF-8 解码。
     *
     * @param value 编码后的文本
     * @return 解码结果
     */
    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, ENCODING);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("不支持 " + ENCODING + " 编码", e);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("回调报文不是合法的 urlencoded 格式", e);
        }
    }
}
