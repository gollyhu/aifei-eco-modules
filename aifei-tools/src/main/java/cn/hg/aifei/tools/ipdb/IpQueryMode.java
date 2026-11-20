/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.ipdb;

/**
 * 查询模式：仅决定内核缓存策略，不影响归属地结果。
 *
 * <p>三模式对同一 IP 与同一 xdb 的查询结果逐字一致，差异只在内存占用与查询耗时。</p>
 */
public enum IpQueryMode {

    /** 每次磁盘 IO，内存占用最低。 */
    FILE_ONLY,

    /** 固定 512 KiB 索引向量缓存，内存与性能折中，为默认模式。 */
    VECTOR_INDEX,

    /** 全文件内存驻留，查询最快。 */
    CONTENT
}
