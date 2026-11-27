/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.ipdb.internal;

import cn.hg.aifei.tools.ipdb.IpRegion;

/**
 * region 五段解析的唯一定义点（「0」/「Reserved」→ 空串）。
 *
 * <p>内核 region 格式为「国家|省份|城市|ISP|ISO 码」；保留段与全空结果统一归一为
 * 五字段均为空串的非 null {@link IpRegion}，调用方无需识别内核哨兵值。</p>
 */
public final class RegionParser {

    private static final String SEPARATOR = "\\|";
    private static final String SENTINEL_ZERO = "0";
    private static final String SENTINEL_RESERVED = "Reserved";
    private static final int FIELD_COUNT = 5;

    private RegionParser() {
    }

    /**
     * 解析内核 region 串。
     *
     * @param raw 原始 region 串，可为 null 或空串（表示未命中）
     * @return 五段结果，字段永不为 null
     */
    public static IpRegion parse(String raw) {
        if (raw == null || raw.length() == 0) {
            return IpRegion.of("", "", "", "", "");
        }
        String[] parts = raw.split(SEPARATOR, -1);
        return IpRegion.of(
                field(parts, 0),
                field(parts, 1),
                field(parts, 2),
                field(parts, 3),
                field(parts, 4));
    }

    private static String field(String[] parts, int index) {
        if (index >= parts.length || index >= FIELD_COUNT) {
            return "";
        }
        String value = parts[index];
        if (SENTINEL_ZERO.equals(value) || SENTINEL_RESERVED.equals(value)) {
            return "";
        }
        return value;
    }
}
