/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.aifei;

import cn.aifei.plugin.Plugin;
import cn.aifei.util.Prop;
import cn.hg.aifei.i18n.I18nException;
import cn.hg.aifei.i18n.MessageSource;
import cn.hg.aifei.i18n.internal.I18nConfig;
import cn.hg.aifei.i18n.internal.I18nEngine;
import cn.hg.aifei.i18n.internal.I18nHolder;
import cn.hg.aifei.i18n.internal.SourceRegistry;
import java.util.ArrayList;
import java.util.List;

/**
 * 装配插件：解析 {@code i18n.*} 配置、构建来源注册表、安装引擎到门面，管理生命周期（设计 §5.9）。
 *
 * <p>构造期只持 {@link Prop} 引用与来源列表，不解析、不校验；{@link #start()} 统一完成配置解析、
 * 注册表构建与门面安装，失败 {@code catch(RuntimeException|Error)} 回滚（清门面），不留半装配。</p>
 *
 * <p>用法：{@code plugins.add(new I18nPlugin(prop).addSource(new MySource()))}。</p>
 */
public class I18nPlugin implements Plugin {

    private final Prop prop;
    private final List<MessageSource> sources = new ArrayList<MessageSource>();
    private volatile boolean started;

    /**
     * @param prop 配置源
     * @throws IllegalArgumentException prop 为 {@code null}
     */
    public I18nPlugin(Prop prop) {
        if (prop == null) {
            throw new IllegalArgumentException("prop can not be null");
        }
        this.prop = prop;
    }

    /**
     * 显式注册自定义来源，按调用顺序累积；重名在 {@link #start()} 统一判定（含与保留名冲突）。
     *
     * @param source 来源实例
     * @return 本插件（链式）
     * @throws IllegalArgumentException source 为 {@code null}
     * @throws I18nException            已启动
     */
    public synchronized I18nPlugin addSource(MessageSource source) {
        if (source == null) {
            throw new IllegalArgumentException("source can not be null");
        }
        if (started) {
            throw new I18nException("i18n 已启动，不能再注册来源");
        }
        this.sources.add(source);
        return this;
    }

    @Override
    public synchronized void start() {
        if (started) {
            return;
        }
        try {
            I18nConfig config = I18nConfig.parse(prop.getProperties());
            SourceRegistry registry = SourceRegistry.build(config, sources);
            I18nEngine engine = new I18nEngine(config, registry.orderedSources());
            I18nHolder.install(engine);
            this.started = true;
        } catch (RuntimeException | Error e) {
            I18nHolder.clear();
            throw e;
        }
    }

    @Override
    public synchronized void stop() {
        if (!started) {
            return;
        }
        I18nHolder.clear();
        this.started = false;
    }
}
