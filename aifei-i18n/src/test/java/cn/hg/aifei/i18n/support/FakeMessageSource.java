/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.support;

import cn.hg.aifei.i18n.MessageSource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 测试替身来源：可配置稳定名、数据、是否可列举、是否在 get/list/refresh 抛异常，并录制调用次数。
 *
 * <p>用于覆盖回退、叠加、故障与导出场景；与来源形态无关。</p>
 */
public final class FakeMessageSource implements MessageSource {

    private final String name;
    private final Map<String, String> data = new LinkedHashMap<String, String>();
    private boolean listable = true;
    private boolean failing;
    private boolean listFailing;
    private boolean refreshFailing;
    private int getCalls;
    private int listCalls;
    private int refreshCalls;

    /**
     * @param name 稳定名
     */
    public FakeMessageSource(String name) {
        this.name = name;
    }

    /**
     * 追加一条 {@code locale|key → 原文} 数据。
     *
     * @param localeTag 已规范化 locale
     * @param key       文案 key
     * @param value     原文（可空串）
     * @return 自身（链式）
     */
    public FakeMessageSource put(String localeTag, String key, String value) {
        data.put(localeTag + "|" + key, value);
        return this;
    }

    /**
     * 设置是否支持整包列举（默认支持）。
     *
     * @param listable 是否可列举
     * @return 自身
     */
    public FakeMessageSource listable(boolean listable) {
        this.listable = listable;
        return this;
    }

    /**
     * 设置单点命中是否抛运行时异常（默认否）。
     *
     * @param failing 是否故障
     * @return 自身
     */
    public FakeMessageSource failing(boolean failing) {
        this.failing = failing;
        return this;
    }

    /**
     * 设置整包列举是否抛运行时异常（默认否）。
     *
     * @param listFailing 是否故障
     * @return 自身
     */
    public FakeMessageSource listFailing(boolean listFailing) {
        this.listFailing = listFailing;
        return this;
    }

    /**
     * 设置运行期刷新是否抛运行时异常（默认否）。
     *
     * @param refreshFailing 是否故障
     * @return 自身
     */
    public FakeMessageSource refreshFailing(boolean refreshFailing) {
        this.refreshFailing = refreshFailing;
        return this;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String get(String key, String localeTag) {
        getCalls++;
        if (failing) {
            throw new RuntimeException("模拟来源故障: " + name);
        }
        return data.get(localeTag + "|" + key);
    }

    @Override
    public Map<String, String> list(String localeTag) {
        listCalls++;
        if (listFailing) {
            throw new RuntimeException("模拟列举故障: " + name);
        }
        if (!listable) {
            return null;
        }
        Map<String, String> result = new LinkedHashMap<String, String>();
        String prefix = localeTag + "|";
        for (Map.Entry<String, String> entry : data.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                result.put(entry.getKey().substring(prefix.length()), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    @Override
    public void refresh() {
        refreshCalls++;
        if (refreshFailing) {
            throw new RuntimeException("模拟刷新故障: " + name);
        }
    }

    /**
     * @return 单点命中调用次数
     */
    public int getCalls() {
        return getCalls;
    }

    /**
     * @return 整包列举调用次数
     */
    public int listCalls() {
        return listCalls;
    }

    /**
     * @return 运行期刷新调用次数
     */
    public int refreshCalls() {
        return refreshCalls;
    }

    /**
     * 重置调用计数。
     */
    public void resetCalls() {
        getCalls = 0;
        listCalls = 0;
        refreshCalls = 0;
    }
}
