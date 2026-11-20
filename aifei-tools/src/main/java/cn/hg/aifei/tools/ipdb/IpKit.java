/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.ipdb;

import cn.hg.aifei.tools.ipdb.internal.IpDeps;
import cn.hg.aifei.tools.ipdb.internal.IpSession;

/**
 * IP 归属地便捷门面：一次查询即开即关，无需手动管理会话生命周期。
 *
 * <p>批量查询请用 {@link IpSearcher} 复用会话以减少 xdb 打开开销。</p>
 */
public final class IpKit {

    private IpKit() {
    }

    /**
     * 以默认 {@link IpQueryMode#VECTOR_INDEX} 模式查询。
     *
     * @param v4Xdb IPv4 xdb 文件路径，可为 null
     * @param v6Xdb IPv6 xdb 文件路径，可为 null
     * @param ip IP 文本
     * @return 五段结果，字段永不为 null；未命中时五字段均为空串
     * @throws IpException 参数非法、路径不可读、IP 非法或内核依赖缺失
     */
    public static IpRegion search(String v4Xdb, String v6Xdb, String ip) {
        return search(v4Xdb, v6Xdb, ip, IpQueryMode.VECTOR_INDEX);
    }

    /**
     * 以指定模式查询。
     *
     * @param v4Xdb IPv4 xdb 文件路径，可为 null
     * @param v6Xdb IPv6 xdb 文件路径，可为 null
     * @param ip IP 文本
     * @param mode 查询模式
     * @return 五段结果，字段永不为 null；未命中时五字段均为空串
     * @throws IpException 参数非法、路径不可读、IP 非法或内核依赖缺失
     */
    public static IpRegion search(String v4Xdb, String v6Xdb, String ip, IpQueryMode mode) {
        IpDeps.requireIp2Region();
        IpSession session = IpSession.build(v4Xdb, v6Xdb, mode);
        try {
            return session.search(ip);
        } finally {
            session.close();
        }
    }
}
