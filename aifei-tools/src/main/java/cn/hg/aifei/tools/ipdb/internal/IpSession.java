/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.ipdb.internal;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.lionsoul.ip2region.service.Config;
import org.lionsoul.ip2region.service.ConfigBuilder;
import org.lionsoul.ip2region.service.InvalidConfigException;
import org.lionsoul.ip2region.service.Ip2Region;
import org.lionsoul.ip2region.xdb.InetAddressException;
import org.lionsoul.ip2region.xdb.XdbException;

import cn.hg.aifei.tools.ipdb.IpQueryMode;
import cn.hg.aifei.tools.ipdb.IpRegion;

/**
 * ip2region 会话内核（Config 构建 / 双库创建 / close 幂等的唯一定义点）。
 *
 * <p>每个会话独占一个内核 {@link Ip2Region}，由内核自带 SearcherPool 保证线程安全；
 * 本类不自建 ThreadLocal、不裸用底层 Searcher。三模式只影响 Config 缓存策略，
 * 查询结果由内核按 IP 版本自动路由双库。</p>
 */
public final class IpSession {

    /** 内核池大小：按可用处理器数设定，不暴露为公共配置项。 */
    private static final int SEARCHERS = Math.max(1, Runtime.getRuntime().availableProcessors());

    private final Ip2Region region;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private IpSession(Ip2Region region) {
        this.region = region;
    }

    /**
     * 构建双库会话。
     *
     * @param v4Xdb IPv4 xdb 路径，可为 null（禁用 v4 检索）
     * @param v6Xdb IPv6 xdb 路径，可为 null（禁用 v6 检索）
     * @param mode 查询模式
     * @return 会话
     * @throws cn.hg.aifei.tools.ipdb.IpException 参数非法、xdb 不可读或构建失败
     */
    public static IpSession build(String v4Xdb, String v6Xdb, IpQueryMode mode) {
        if (v4Xdb == null && v6Xdb == null) {
            throw IpErrors.noPath();
        }
        if (mode == null) {
            throw IpErrors.nullArgument("open", "mode");
        }
        int policy = cachePolicy(mode);
        Config v4Config = null;
        Config v6Config = null;
        try {
            if (v4Xdb != null) {
                v4Config = config(v4Xdb, policy).asV4();
            }
            if (v6Xdb != null) {
                v6Config = config(v6Xdb, policy).asV6();
            }
        } catch (IOException | XdbException | InvalidConfigException e) {
            throw IpErrors.unreadableXdb(v4Xdb != null ? v4Xdb : v6Xdb, e);
        }
        try {
            return new IpSession(Ip2Region.create(v4Config, v6Config));
        } catch (IOException e) {
            throw IpErrors.unreadableXdb(v4Xdb != null ? v4Xdb : v6Xdb, e);
        }
    }

    /**
     * 查询归属地；内核按 IP 版本自动路由 v4/v6 双库。
     *
     * @param ip IP 文本
     * @return 五段结果，字段永不为 null
     * @throws cn.hg.aifei.tools.ipdb.IpException 会话已关闭、参数非法、IP 非法或查询失败
     */
    public IpRegion search(String ip) {
        if (closed.get()) {
            throw IpErrors.closed();
        }
        if (ip == null) {
            throw IpErrors.nullArgument("search", "ip");
        }
        String raw;
        try {
            raw = region.search(ip);
        } catch (InetAddressException e) {
            throw IpErrors.invalidIp(ip, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw IpErrors.searchFailed(ip, e);
        } catch (IOException e) {
            throw IpErrors.searchFailed(ip, e);
        }
        return RegionParser.parse(raw);
    }

    /**
     * 关闭会话，幂等；重复调用不重复关闭内核。
     *
     * @throws cn.hg.aifei.tools.ipdb.IpException 内核关闭时中断
     */
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                region.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw IpErrors.closeFailed(e);
            }
        }
    }

    private static ConfigBuilder config(String xdbPath, int policy) {
        return new ConfigBuilder()
                .setCachePolicy(policy)
                .setXdbPath(xdbPath)
                .setSearchers(SEARCHERS)
                .setFairLock(false);
    }

    private static int cachePolicy(IpQueryMode mode) {
        switch (mode) {
            case FILE_ONLY:
                return Config.NoCache;
            case CONTENT:
                return Config.FullCache;
            case VECTOR_INDEX:
            default:
                return Config.VIndexCache;
        }
    }
}
