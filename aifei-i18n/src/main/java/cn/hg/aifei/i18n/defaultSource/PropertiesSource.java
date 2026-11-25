/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.defaultSource;

import cn.aifei.log.Log;
import cn.hg.aifei.i18n.MessageSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 默认来源·properties：零第三方依赖，启动期一次全量载入 classpath 资源到内存快照。
 *
 * <p>资源格式：默认读取 classpath 根下的 {@code i18n.properties}，UTF-8 编码，每行形如
 * {@code <locale>|<key>=<原文>}（例如 {@code zh-CN|login.title=登录}）。改值须重启（选修要素⑤缺失）。</p>
 *
 * <p>可列举：{@link #list(String)} 返回该 locale 的全量映射；{@link #refresh()} 无操作。</p>
 */
public final class PropertiesSource implements MessageSource {

    /** 默认 classpath 资源名。 */
    public static final String DEFAULT_RESOURCE = "i18n.properties";
    /** locale 与 key 的分隔符。 */
    private static final char LOCALE_SEPARATOR = '|';

    private final Map<String, Map<String, String>> data;

    /**
     * 从默认 classpath 资源 {@link #DEFAULT_RESOURCE} 全量载入。资源不存在时为空来源（不启动失败）。
     */
    public PropertiesSource() {
        this(DEFAULT_RESOURCE);
    }

    /**
     * 从指定 classpath 资源全量载入。
     *
     * @param resourceName classpath 资源名（根路径）
     */
    public PropertiesSource(String resourceName) {
        this(load(resourceName));
    }

    /**
     * 以内存数据构造（测试替身 / 消费方内嵌快照用）。
     *
     * @param data locale → (key → 原文) 的映射，方法内做不可变深拷贝
     */
    public PropertiesSource(Map<String, Map<String, String>> data) {
        Map<String, Map<String, String>> copy = new HashMap<String, Map<String, String>>();
        if (data != null) {
            for (Map.Entry<String, Map<String, String>> entry : data.entrySet()) {
                Map<String, String> inner = entry.getValue() == null
                        ? Collections.<String, String>emptyMap()
                        : Collections.unmodifiableMap(new HashMap<String, String>(entry.getValue()));
                copy.put(entry.getKey(), inner);
            }
        }
        this.data = Collections.unmodifiableMap(copy);
    }

    @Override
    public String name() {
        return "properties";
    }

    @Override
    public String get(String key, String localeTag) {
        Map<String, String> messages = data.get(localeTag);
        return messages == null ? null : messages.get(key);
    }

    @Override
    public Map<String, String> list(String localeTag) {
        Map<String, String> messages = data.get(localeTag);
        return messages == null ? Collections.<String, String>emptyMap() : messages;
    }

    /**
     * properties 来源不支持运行期刷新：资源改动须重启应用（README 已声明）。
     */
    @Override
    public void refresh() {
        // 无操作：启动快照不可变
    }

    private static Map<String, Map<String, String>> load(String resourceName) {
        Map<String, Map<String, String>> result = new HashMap<String, Map<String, String>>();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = PropertiesSource.class.getClassLoader();
        }
        InputStream stream = loader.getResourceAsStream(resourceName);
        if (stream == null) {
            return result;
        }
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                        continue;
                    }
                    int eq = trimmed.indexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    String compositeKey = trimmed.substring(0, eq);
                    String value = trimmed.substring(eq + 1);
                    int bar = compositeKey.indexOf(LOCALE_SEPARATOR);
                    if (bar <= 0) {
                        continue;
                    }
                    String locale = compositeKey.substring(0, bar).trim();
                    String key = compositeKey.substring(bar + 1).trim();
                    if (locale.isEmpty() || key.isEmpty()) {
                        continue;
                    }
                    Map<String, String> bucket = result.get(locale);
                    if (bucket == null) {
                        bucket = new HashMap<String, String>();
                        result.put(locale, bucket);
                    }
                    bucket.put(key, value);
                }
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            warn(resourceName, e);
            return result;
        }
        return result;
    }

    private static void warn(String resourceName, IOException cause) {
        try {
            Log log = Log.get(PropertiesSource.class);
            log.warn("i18n properties 资源读取失败，已按空来源处理: resource=" + resourceName + ", 原因=" + cause);
        } catch (Throwable ignored) {
            // 日志通道不可用不影响启动
        }
    }
}
