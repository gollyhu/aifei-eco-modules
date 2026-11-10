/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * 任务注册表与任务名判重的唯一定义点：构造期插入并判重，之后只读。
 *
 * <p>同一任务名出现两次即启动失败，不静默去重——多个扫描包存在包含关系时同一方法会被扫到多次，
 * 静默去重会掩盖配置冗余。任务一律按任务名排序暴露，使观测面与日志的顺序稳定。</p>
 */
public final class JobRegistry {

    /**
     * 任务名到任务定义的映射，按任务名排序。
     */
    private final TreeMap<String, JobTask> tasks;

    /**
     * 按任务名排序的不可变任务清单。
     */
    private final List<JobTask> ordered;

    /**
     * 按任务名排序的不可变任务名集合。
     */
    private final Set<String> names;

    /**
     * 建立注册表并在插入期判重。
     *
     * @param tasks 扫描得到的任务清单
     * @throws cn.hg.aifei.job.JobException 同一任务名出现两次
     */
    public JobRegistry(Collection<JobTask> tasks) {
        TreeMap<String, JobTask> map = new TreeMap<>();
        if (tasks != null) {
            for (JobTask task : tasks) {
                if (map.put(task.name(), task) != null) {
                    throw JobErrors.duplicateTask(task.name());
                }
            }
        }
        this.tasks = map;
        this.ordered = Collections.unmodifiableList(new ArrayList<>(map.values()));
        this.names = Collections.unmodifiableSet(new LinkedHashSet<>(map.keySet()));
    }

    /**
     * @return 不可变的、按任务名排序的任务清单
     */
    public List<JobTask> tasks() {
        return ordered;
    }

    /**
     * @return 不可变的、按任务名排序的任务名集合
     */
    public Set<String> names() {
        return names;
    }

    /**
     * 按任务名取任务定义。
     *
     * @param name 任务名（类全名#方法名）
     * @return 任务定义；未注册时返回 {@code null}
     */
    public JobTask get(String name) {
        return tasks.get(name);
    }
}
