/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.support;

import cn.hg.aifei.audit.spi.AuditResultJudge;

/**
 * 结果判定替身：恒成功 / 恒失败 / 抛异常三态。
 */
public final class StubResultJudge implements AuditResultJudge {

    private final boolean failure;

    private final boolean throwing;

    private StubResultJudge(boolean failure, boolean throwing) {
        this.failure = failure;
        this.throwing = throwing;
    }

    /**
     * @return 恒判成功的替身
     */
    public static StubResultJudge success() {
        return new StubResultJudge(false, false);
    }

    /**
     * @return 恒判失败的替身
     */
    public static StubResultJudge failure() {
        return new StubResultJudge(true, false);
    }

    /**
     * @return 判定即抛异常的替身
     */
    public static StubResultJudge throwing() {
        return new StubResultJudge(false, true);
    }

    @Override
    public boolean isFailure(String action, Object returnValue) {
        if (throwing) {
            throw new IllegalStateException("judge-failure");
        }
        return failure;
    }
}
