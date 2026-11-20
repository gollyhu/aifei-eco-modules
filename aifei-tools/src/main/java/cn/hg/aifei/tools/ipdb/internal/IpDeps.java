/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.ipdb.internal;

/**
 * ip2region 可选依赖探测（依赖探测唯一定义点）。
 *
 * <p>全模块仅本类允许 {@code Class.forName}，采用两参重载避免触发静态初始化；
 * 缺失时收敛为可读的 {@link cn.hg.aifei.tools.ipdb.IpException}，不向调用方裸抛链接错误。</p>
 */
public final class IpDeps {

    private static final String IP2REGION_CLASS = "org.lionsoul.ip2region.service.Ip2Region";

    private IpDeps() {
    }

    /**
     * 确保 ip2region 内核在类路径上可用，否则抛可读异常。
     *
     * @throws cn.hg.aifei.tools.ipdb.IpException 依赖缺失或链接失败
     */
    public static void requireIp2Region() {
        try {
            Class.forName(IP2REGION_CLASS, false, IpDeps.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw IpErrors.missingDependency(e);
        } catch (LinkageError e) {
            throw IpErrors.missingDependency(e);
        }
    }
}
