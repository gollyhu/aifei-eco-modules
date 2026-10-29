/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.demo.mq;

import cn.aifei.json.Json;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * E2 协议唯一定义点：业务域 topic 白名单、域到事件类型的清单、payload 编解码。
 *
 * <p>E2 用法是「topic 表达业务域（少量、启动前决定、终身不变），事件类型降级为 payload 内字段，订阅侧一个
 * 入口 + 本地按 {@code type} 分发」。因此这里只有<b>两个</b>域 topic，各自的事件类型是编译期常量清单，
 * 新增类型只改本类，不新增 topic。</p>
 *
 * <p>payload 形状固定为 {@code {"type":..,"version":1,"bizId":"A1001"}}，编解码也放在本类：payload 的字段名
 * 属协议定义，拆到别的类会让同一组字段名出现两个定义点。编解码一律走框架内置
 * {@link Json}（{@code Json.of(Map).toJson()} / {@code Json.of(String).toMap()}），不经第三方 JSON 库。</p>
 */
public final class E2Topics {

    /**
     * 订单域 topic；编译期常量，可直接用作 {@code @Para(defaultValue = ...)}。
     */
    public static final String ORDER_EVENTS = "order-events";

    /**
     * 支付域 topic。
     */
    public static final String PAY_EVENTS = "pay-events";

    /**
     * 省略 {@code domain} 时的缺省业务域。
     */
    public static final String DEFAULT_DOMAIN = ORDER_EVENTS;

    /**
     * 省略 {@code bizId} 时的缺省业务字段值。
     */
    public static final String DEFAULT_BIZ_ID = "A1001";

    /**
     * payload 内的协议版本；本演示固定为 {@code 1}，不演示版本演进。
     */
    public static final int VERSION = 1;

    /**
     * payload 字段名：事件类型。
     */
    private static final String FIELD_TYPE = "type";

    /**
     * payload 字段名：协议版本。
     */
    private static final String FIELD_VERSION = "version";

    /**
     * payload 字段名：业务字段（订单号 / 支付单号统一叫 {@code bizId}）。
     */
    private static final String FIELD_BIZ_ID = "bizId";

    /**
     * 域 topic 清单，按声明顺序；接口输出的 {@code domainTopics} 即此顺序。
     */
    private static final List<String> DOMAINS =
            Collections.unmodifiableList(Arrays.asList(ORDER_EVENTS, PAY_EVENTS));

    /**
     * 域 topic 到其事件类型清单的映射；值保持声明顺序，供端点直接作为候选输出。
     */
    private static final Map<String, List<String>> DOMAIN_TYPES = domainTypes();

    /**
     * 禁止创建工具类实例。
     */
    private E2Topics() {
    }

    /**
     * 返回全部业务域 topic，按声明顺序。
     *
     * @return 域 topic 只读清单；不为 {@code null}
     */
    public static List<String> all() {
        return DOMAINS;
    }

    /**
     * 判断是否为已知业务域。
     *
     * @param domain 待判定的域 topic，可为 {@code null}
     * @return 已知域返回 {@code true}
     */
    public static boolean isDomain(String domain) {
        return domain != null && DOMAIN_TYPES.containsKey(domain);
    }

    /**
     * 返回某业务域的事件类型清单，按声明顺序。
     *
     * <p>未知域返回空清单而不抛异常，调用方据此直接判参数非法。</p>
     *
     * @param domain 业务域 topic
     * @return 类型只读清单；未知域时为空清单
     */
    public static List<String> typesOf(String domain) {
        List<String> types = DOMAIN_TYPES.get(domain);
        return types == null ? Collections.<String>emptyList() : types;
    }

    /**
     * 返回某业务域的缺省事件类型（即清单首个）。
     *
     * @param domain 业务域 topic
     * @return 缺省类型；未知域时返回 {@code null}
     */
    public static String defaultTypeOf(String domain) {
        List<String> types = DOMAIN_TYPES.get(domain);
        return types == null || types.isEmpty() ? null : types.get(0);
    }

    /**
     * 生成 payload 文本。
     *
     * <p>必须传 {@code Map} 给 {@code Json.of(Object)}：该重载对 {@code String} 入参主动抛
     * {@code IllegalArgumentException}（要求改用 {@code Json.of(String)}）。</p>
     *
     * @param type 事件类型
     * @param bizId 业务字段值
     * @return payload 文本；不为 {@code null}
     * @throws IllegalArgumentException 任一参数只含空白
     */
    public static String buildPayload(String type, String bizId) {
        requireText(type, "type");
        requireText(bizId, "bizId");
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put(FIELD_TYPE, type);
        body.put(FIELD_VERSION, Integer.valueOf(VERSION));
        body.put(FIELD_BIZ_ID, bizId);
        return Json.of(body).toJson();
    }

    /**
     * 解析 payload 文本；本方法不抛异常，畸形 JSON 视为「type 无法解析」。
     *
     * <p>实时总线上的 payload 是字节，由监听器按 UTF-8 解码成文本后传入。</p>
     *
     * @param payload payload 文本，可为 {@code null} 或空白
     * @return 解析结果；任一字段缺失或 payload 不可解析时对应字段为 {@code null}
     */
    public static Event decode(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            return new Event(null, null);
        }
        try {
            Map<String, Object> body = Json.of(payload).toMap();
            if (body == null) {
                return new Event(null, null);
            }
            return new Event(textOf(body.get(FIELD_TYPE)), textOf(body.get(FIELD_BIZ_ID)));
        } catch (RuntimeException e) {
            // 畸形 JSON：交给调用方计入 unknown 兜底桶，监听器不因一条坏消息抛异常
            return new Event(null, null);
        }
    }

    /**
     * 建立域到类型清单的映射；新增域 / 类型只改这里与 {@link #DOMAINS}。
     */
    private static Map<String, List<String>> domainTypes() {
        Map<String, List<String>> types = new LinkedHashMap<String, List<String>>();
        types.put(ORDER_EVENTS, Collections.unmodifiableList(Arrays.asList("created", "paid", "cancelled")));
        types.put(PAY_EVENTS, Collections.unmodifiableList(Arrays.asList("paid", "refunded", "failed")));
        return Collections.unmodifiableMap(types);
    }

    /**
     * 把 payload 字段值转成去空白文本。
     *
     * @param value 原始字段值，可为 {@code null}
     * @return 去空白后的文本；{@code null} 或空白时返回 {@code null}
     */
    private static String textOf(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 校验必填文本。
     */
    private static void requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " can not be blank");
        }
    }

    /**
     * 解析后的 payload：只有协议关心的两个字段。
     *
     * <p>不可变；{@code type} 为 {@code null} 表示 payload 不可解析或缺少类型。</p>
     */
    public static final class Event {

        private final String type;

        private final String bizId;

        /**
         * 创建事件。
         *
         * @param type 事件类型；无法解析时为 {@code null}
         * @param bizId 业务字段值；缺失时为 {@code null}
         */
        public Event(String type, String bizId) {
            this.type = type;
            this.bizId = bizId;
        }

        /**
         * 返回事件类型。
         *
         * @return 事件类型；无法解析时为 {@code null}
         */
        public String type() {
            return type;
        }

        /**
         * 返回业务字段值。
         *
         * @return 业务字段值；缺失时为 {@code null}
         */
        public String bizId() {
            return bizId;
        }
    }
}
