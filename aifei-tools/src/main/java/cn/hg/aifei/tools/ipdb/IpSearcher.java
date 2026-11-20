/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.ipdb;

import cn.hg.aifei.tools.ipdb.internal.IpDeps;
import cn.hg.aifei.tools.ipdb.internal.IpSession;

/**
 * 有状态 IP 查询会话（工厂 + 查询 + 关闭）。
 *
 * <p>每个会话独占一个内核引擎，线程安全由内核池保证；同一会话可并发查询，
 * 使用完毕须 {@link #close()}（幂等）。内核按 IP 版本自动路由 v4/v6 双库。</p>
 */
public final class IpSearcher implements AutoCloseable {

    private final IpSession session;

    private IpSearcher(IpSession session) {
        this.session = session;
    }

    /**
     * 以默认 {@link IpQueryMode#VECTOR_INDEX} 模式打开会话。
     *
     * @param v4Xdb IPv4 xdb 文件路径，可为 null（禁用 v4 检索）
     * @param v6Xdb IPv6 xdb 文件路径，可为 null（禁用 v6 检索）
     * @return 查询会话
     * @throws IpException 两路径都为 null、路径不可读或内核依赖缺失
     */
    public static IpSearcher open(String v4Xdb, String v6Xdb) {
        return open(v4Xdb, v6Xdb, IpQueryMode.VECTOR_INDEX);
    }

    /**
     * 以指定模式打开会话。
     *
     * @param v4Xdb IPv4 xdb 文件路径，可为 null（禁用 v4 检索）
     * @param v6Xdb IPv6 xdb 文件路径，可为 null（禁用 v6 检索）
     * @param mode 查询模式
     * @return 查询会话
     * @throws IpException 参数非法、路径不可读或内核依赖缺失
     */
    public static IpSearcher open(String v4Xdb, String v6Xdb, IpQueryMode mode) {
        IpDeps.requireIp2Region();
        return new IpSearcher(IpSession.build(v4Xdb, v6Xdb, mode));
    }

    /**
     * 查询 IP 归属地。
     *
     * @param ip IPv4 或 IPv6 文本
     * @return 五段结果，字段永不为 null；未命中时五字段均为空串
     * @throws IpException 会话已关闭、IP 文本非法或查询失败
     */
    public IpRegion search(String ip) {
        return session.search(ip);
    }

    /**
     * 关闭会话，幂等；关闭后再查询抛可读异常。
     *
     * @throws IpException 内核关闭过程被中断
     */
    @Override
    public void close() {
        session.close();
    }
}
