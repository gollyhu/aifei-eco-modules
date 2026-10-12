/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * keys 区间换算与切片的唯一定义点，语义对齐 Redis {@code LRANGE}。
 */
public final class KeyRanges {

    private KeyRanges() {
    }

    /**
     * 对已排序键列表做闭区间切片：{@code begin} 与 {@code end} 均包含，负数从末尾计数，
     * 越界钳制；空列表或换算后 {@code begin > end} 返回空列表。
     *
     * @param sortedKeys 已按字典序升序排序的键列表
     * @param begin 起始索引（包含），负数从末尾计数
     * @param end 结束索引（包含），负数从末尾计数
     * @return 区间切片；不为 {@code null}
     */
    public static List<String> slice(List<String> sortedKeys, int begin, int end) {
        int size = sortedKeys.size();
        if (size == 0) {
            return Collections.emptyList();
        }
        int from = begin < 0 ? size + begin : begin;
        int to = end < 0 ? size + end : end;
        if (from < 0) {
            from = 0;
        }
        if (to >= size) {
            to = size - 1;
        }
        if (from > to) {
            return Collections.emptyList();
        }
        return new ArrayList<>(sortedKeys.subList(from, to + 1));
    }
}
