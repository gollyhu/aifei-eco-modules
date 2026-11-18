/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.support;

import cn.hg.aifei.audit.spi.AuditActorProvider;

/**
 * 操作人提供者替身：固定值 / id 抛异常 / 名称抛异常三态。
 */
public final class StubActorProvider implements AuditActorProvider {

    private final String id;

    private final String name;

    private final boolean failOnId;

    private final boolean failOnName;

    private StubActorProvider(String id, String name, boolean failOnId, boolean failOnName) {
        this.id = id;
        this.name = name;
        this.failOnId = failOnId;
        this.failOnName = failOnName;
    }

    /**
     * @param id   操作人 id
     * @param name 操作人名
     * @return 固定值替身
     */
    public static StubActorProvider of(String id, String name) {
        return new StubActorProvider(id, name, false, false);
    }

    /**
     * @return 取 id 即抛异常的替身
     */
    public static StubActorProvider failingId() {
        return new StubActorProvider(null, null, true, false);
    }

    /**
     * @return 取名称即抛异常的替身
     */
    public static StubActorProvider failingName() {
        return new StubActorProvider("alice", null, false, true);
    }

    @Override
    public String actorId() {
        if (failOnId) {
            throw new IllegalStateException("actor-id-failure");
        }
        return id;
    }

    @Override
    public String actorName() {
        if (failOnName) {
            throw new IllegalStateException("actor-name-failure");
        }
        return name;
    }
}
