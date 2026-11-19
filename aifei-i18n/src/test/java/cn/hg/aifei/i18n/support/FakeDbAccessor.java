/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.support;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * db 访问替身：以内存表模拟 {@code i18n_message} 的只读查询（参数化语义等价），
 * 用于在不引入真实数据库 / 不联网的前提下验证 DbSource 的 SQL 契约与只读、可列举语义。
 *
 * <p>真实 DbSource 走框架 {@code cn.aifei.db.core.Db}（随 aifei-all provided）；本替身仅用于测试隔离。</p>
 */
public final class FakeDbAccessor {

    private final Map<String, String> rows = new LinkedHashMap<String, String>();

    /**
     * 写入一行内存数据（测试准备用）。
     *
     * @param localeTag 规范化 locale
     * @param key       文案 key
     * @param content   原文
     * @return 自身
     */
    public FakeDbAccessor put(String localeTag, String key, String content) {
        rows.put(localeTag + "|" + key, content);
        return this;
    }

    /**
     * 模拟参数化单点查询。
     *
     * @param localeTag 规范化 locale
     * @param key       文案 key
     * @return content；未命中返回 {@code null}
     */
    public String selectContent(String localeTag, String key) {
        return rows.get(localeTag + "|" + key);
    }

    /**
     * 模拟整包列举。
     *
     * @param localeTag 规范化 locale
     * @return 该 locale 的 key 到原文映射（不可变）
     */
    public Map<String, String> list(String localeTag) {
        String prefix = localeTag + "|";
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : rows.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                result.put(entry.getKey().substring(prefix.length()), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * @return 内存表行数
     */
    public int size() {
        return rows.size();
    }
}
