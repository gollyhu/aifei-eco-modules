/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.hg.aifei.cache;

import cn.aifei.aop.AopKit;
import cn.aifei.plugin.Plugin;
import cn.aifei.util.Prop;
import cn.hg.aifei.cache.internal.CacheManager;
import cn.hg.aifei.cache.internal.CacheProvider;
import cn.hg.aifei.cache.internal.CacheProviderRegistry;
import cn.hg.aifei.cache.internal.Config;
import java.util.ArrayList;
import java.util.List;

/**
 * 把配置里的缓存实例装配进 aifei 并管理它们的生命周期：应用启动时创建并注册全部实例，
 * 应用停止时关闭它们。
 *
 * <p>使用方式：在 {@code config(Plugins plugins)} 里 {@code plugins.add(new CachePlugin(prop))}，
 * 之后即可用 {@code @Inject} 取主缓存。</p>
 */
public class CachePlugin implements Plugin {

    private final CacheProviderRegistry registry = new CacheProviderRegistry();

    /**
     * 构造期保留的配置引用，解析发生在 {@link #start()}。
     */
    private final Prop prop;

    /**
     * 本次 {@code start()} 创建的可关闭实例，{@code stop()} 时按此快照关闭。
     */
    private final List<AutoCloseable> created = new ArrayList<>();

    /**
     * 是否已完成装配；{@code start()} 与 {@code stop()} 据此保持幂等。
     */
    private volatile boolean started = false;

    /**
     * 创建插件；只保存配置，不解析、不校验、不建立任何连接（配置校验发生在 {@link #start()}）。
     *
     * @param prop 缓存配置
     * @throws IllegalArgumentException {@code prop} 为 {@code null}
     */
    public CachePlugin(Prop prop) {
        if (prop == null) {
            throw new IllegalArgumentException("prop can not be null");
        }
        this.prop = prop;
    }

    /**
     * 解析配置、创建全部实例并注册；重复启动不会重复创建，配置有误时不留半装配状态。
     *
     * @throws IllegalArgumentException 配置缺失、非法或后端类型未注册
     */
    @Override
    public synchronized void start() {
        if (started) {
            return;
        }
        List<AutoCloseable> closable = new ArrayList<>();
        try {
            for (String instanceName : instanceNames()) {
                String prefix = prefixOf(instanceName);
                CacheProvider provider = registry.getProvider(parseType(prop, prefix));
                Cache cache = provider.buildCache(instanceName, prefix, prop);
                CacheManager.register(instanceName, cache);
                if (cache instanceof AutoCloseable) {
                    closable.add((AutoCloseable) cache);
                }
            }
            // 主 Cache / 主 Counter 的 @Inject 单例必须在注册之后写入（AopKit 按类型注册、无按名通道）。
            AopKit.get().addSingletonObject(Cache.class,
                    CacheManager.getCache(Config.MAIN_INSTANCE_NAME));
            AopKit.get().addSingletonObject(Counter.class,
                    CacheManager.getCounter(Config.MAIN_INSTANCE_NAME));
            created.clear();
            created.addAll(closable);
            started = true;
        } catch (RuntimeException | Error e) {
            CacheManager.clear();
            throw e;
        }
    }

    /**
     * 关闭全部实例；重复停止不会重复关闭。
     *
     * @throws RuntimeException 关闭任一实例时出错，异常不吞
     */
    @Override
    public synchronized void stop() {
        if (!started) {
            return;
        }
        try {
            List<AutoCloseable> snapshot = new ArrayList<>(created);
            CacheManager.clear();
            for (AutoCloseable closeable : snapshot) {
                closeable.close();
            }
            created.clear();
            started = false;
        } catch (Exception e) {
            throw new RuntimeException("Failed to close cache plugin", e);
        }
    }

    // ─────────── 配置解析（全部在 start() 内执行） ───────────

    /**
     * 返回待装配的实例名列表：主缓存在最前，其后是 {@code cache.names} 声明的扩展实例。
     */
    private List<String> instanceNames() {
        List<String> names = new ArrayList<>();
        names.add(Config.MAIN_INSTANCE_NAME);
        names.addAll(Config.parseInstanceNames(prop, Config.NAMES_KEY));
        return names;
    }

    /**
     * 由实例名推导其配置键前缀：主缓存为 {@code cache}，扩展实例为 {@code cache.<name>}。
     */
    private static String prefixOf(String instanceName) {
        return Config.MAIN_INSTANCE_NAME.equals(instanceName)
                ? Config.ROOT_PREFIX
                : Config.ROOT_PREFIX + "." + instanceName;
    }

    /**
     * 解析 {@code type}，必填，取值必须落在已注册的后端类型内。
     */
    private String parseType(Prop prop, String prefix) {
        String key = prefix + ".type";
        return Config.requireCacheType(prop, key, registry.types());
    }
}
