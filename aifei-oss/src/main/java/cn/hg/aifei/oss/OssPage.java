/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.oss;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 分页列举结果（不可变值对象）。
 */
public final class OssPage {

    private final List<OssFileInfo> files;
    private final List<String> directories;
    private final String nextMarker;
    private final boolean truncated;

    /**
     * @param files       当前页对象列表，可为 {@code null}
     * @param directories 当前页子目录路径列表，可为 {@code null}
     * @param nextMarker  下一页游标，无更多数据时为 {@code null}
     * @param truncated   是否还有下一页
     */
    public OssPage(List<OssFileInfo> files, List<String> directories, String nextMarker, boolean truncated) {
        this.files = files == null ? Collections.<OssFileInfo>emptyList()
                : Collections.unmodifiableList(new ArrayList<OssFileInfo>(files));
        this.directories = directories == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(directories));
        this.nextMarker = nextMarker;
        this.truncated = truncated;
    }

    /**
     * @return 当前页对象列表（不可变）
     */
    public List<OssFileInfo> getFiles() {
        return files;
    }

    /**
     * @return 当前页子目录路径列表（不可变）
     */
    public List<String> getDirectories() {
        return directories;
    }

    /**
     * @return 下一页游标；无更多数据时为 {@code null}
     */
    public String getNextMarker() {
        return nextMarker;
    }

    /**
     * @return 是否还有下一页
     */
    public boolean isTruncated() {
        return truncated;
    }
}
