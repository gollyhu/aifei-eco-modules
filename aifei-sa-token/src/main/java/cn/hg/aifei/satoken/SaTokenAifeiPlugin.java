/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.satoken;

import cn.aifei.plugin.Plugin;
import cn.hg.aifei.satoken.internal.SaTokenConfigLoader;
import cn.aifei.util.Prop;
import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.context.SaTokenContextForThreadLocal;
import cn.dev33.satoken.plugin.SaTokenPluginHolder;

/**
 * Sa-Token 在 aifei 下的装配入口。
 *
 * <p>装配示例：</p>
 * <pre>
 * public void config(Plugins plugins) {
 *     plugins.add(new SaTokenAifeiPlugin(PropKit.use("app-config.txt")));
 * }
 * </pre>
 */
public class SaTokenAifeiPlugin implements Plugin {

    private final Prop prop;

    private SaTokenConfig previousConfig;
    private SaTokenContext previousContext;
    private boolean started;

    /**
     * 创建插件实例。
     *
     * @param prop 配置源，不能为空；键前缀为 {@code sa-token.}
     * @throws IllegalArgumentException prop 为空
     */
    public SaTokenAifeiPlugin(Prop prop) {
        if (prop == null) {
            throw new IllegalArgumentException("prop can not be null.");
        }
        this.prop = prop;
    }

    /**
     * 装载配置并接管 Sa-Token 的上下文实现。重复启动不再重复接管。
     */
    @Override
    public void start() {
        if (started) {
            return;
        }
        SaTokenConfig config = SaTokenConfigLoader.load(prop);

        previousConfig = SaManager.getConfig();
        previousContext = SaManager.getSaTokenContext();

        SaManager.setConfig(config);
        SaManager.setSaTokenContext(new SaTokenContextForThreadLocal());

        // 幂等：扫描并安装 classpath 中 META-INF/satoken/ 下声明的第三方 Sa-Token 插件
        SaTokenPluginHolder.instance.init();

        started = true;
    }

    /**
     * 清理当前线程上下文并还原启动前的全局状态。未成功启动时不做任何事。
     */
    @Override
    public void stop() {
        if (!started) {
            return;
        }
        SaManager.getSaTokenContext().clearContext();
        SaManager.setConfig(previousConfig);
        SaManager.setSaTokenContext(previousContext);
        started = false;
    }
}
