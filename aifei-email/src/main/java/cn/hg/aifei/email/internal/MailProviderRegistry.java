/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email.internal;

import cn.hg.aifei.email.spi.MailProvider;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * 通道 Provider 发现入口：经 {@link ServiceLoader} 按 {@link MailProvider#type()} 建立索引。
 *
 * <p>首个调用触发一次发现并缓存结果；同类型存在多实现视为装配期错误。找不到指定类型返回
 * {@code null}，由装配入口明确判空并抛可读异常，避免空指针。</p>
 */
public final class MailProviderRegistry {

    private static final Object LOCK = new Object();

    private static volatile Map<String, MailProvider> providers;
    private static volatile ClassLoader discoveryClassLoader;

    private MailProviderRegistry() {
    }

    /**
     * 按通道类型取 Provider。
     *
     * @param type 通道类型标识（如 {@code "smtp"}）
     * @return Provider 实例，未发现时返回 {@code null}
     * @throws cn.hg.aifei.email.MailException 同一类型存在多个实现
     */
    public static MailProvider getProvider(String type) {
        return type == null ? null : providers().get(type);
    }

    /**
     * @return 已发现通道类型的不可变集合（保持发现顺序）
     * @throws cn.hg.aifei.email.MailException 同一类型存在多个实现
     */
    public static Set<String> types() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(providers().keySet()));
    }

    /**
     * 覆盖发现使用的类加载器并清空缓存；传 {@code null} 恢复默认策略
     * （默认：上下文类加载器优先，空结果回退本类加载器）。测试与示例工程用途，非承诺构件。
     *
     * @param loader 类加载器，{@code null} 表示恢复默认策略
     */
    public static void setDiscoveryClassLoader(ClassLoader loader) {
        synchronized (LOCK) {
            discoveryClassLoader = loader;
            providers = null;
        }
    }

    /**
     * 清空发现缓存与类加载器覆盖，下次调用按默认策略重新发现（测试与示例工程用途）。
     */
    public static void reset() {
        synchronized (LOCK) {
            discoveryClassLoader = null;
            providers = null;
        }
    }

    private static Map<String, MailProvider> providers() {
        Map<String, MailProvider> cached = providers;
        if (cached != null) {
            return cached;
        }
        synchronized (LOCK) {
            if (providers == null) {
                providers = discover();
            }
            return providers;
        }
    }

    private static Map<String, MailProvider> discover() {
        ClassLoader override = discoveryClassLoader;
        if (override != null) {
            // 覆盖模式下不做事后回退：便于真实模拟「服务资源缺失 / 无实现」
            return load(override);
        }
        // 默认：优先用上下文类加载器（消费方自带 Provider 可被发现），不可用时回退本类加载器
        ClassLoader own = MailProviderRegistry.class.getClassLoader();
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        Map<String, MailProvider> indexed = load(contextLoader == null ? own : contextLoader);
        if (indexed.isEmpty() && contextLoader != own) {
            indexed = load(own);
        }
        return indexed;
    }

    private static Map<String, MailProvider> load(ClassLoader loader) {
        Map<String, MailProvider> indexed = new LinkedHashMap<String, MailProvider>();
        Iterator<MailProvider> iterator = ServiceLoader.load(MailProvider.class, loader).iterator();
        while (iterator.hasNext()) {
            MailProvider provider = iterator.next();
            String type = provider.type();
            if (indexed.containsKey(type)) {
                throw MailErrors.duplicateProvider(type);
            }
            indexed.put(type, provider);
        }
        return Collections.unmodifiableMap(indexed);
    }
}
