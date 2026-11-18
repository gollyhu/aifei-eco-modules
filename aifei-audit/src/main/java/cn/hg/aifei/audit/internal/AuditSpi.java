/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.internal;

import cn.hg.aifei.audit.spi.AuditActorProvider;
import cn.hg.aifei.audit.spi.AuditResultJudge;
import cn.hg.aifei.audit.spi.AuditSink;

/**
 * 启动期 SPI 探测的唯一实现点（非承诺 API）：容器按类型取，取不到即「未装配」。
 *
 * <p>容器访问全部经 {@link AuditEnvironment} 端口；框架对未注册类型抛异常而非返回 null，
 * 故三个方法都用「异常即未装配」判定。模块不向容器注册任何对象，无半装配回滚面。</p>
 */
public final class AuditSpi {

    private AuditSpi() {
    }

    /**
     * @param env 框架能力端口
     * @return 容器内的落点实现；未装配为 {@code null}
     */
    public static AuditSink sink(AuditEnvironment env) {
        try {
            return env.lookup(AuditSink.class);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * @param env 框架能力端口
     * @return 容器内的操作人实现；未装配为 {@code null}
     */
    public static AuditActorProvider actorProvider(AuditEnvironment env) {
        try {
            return env.lookup(AuditActorProvider.class);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * @param env 框架能力端口
     * @return 容器内的结果判定实现；未装配为 {@code null}
     */
    public static AuditResultJudge judge(AuditEnvironment env) {
        try {
            return env.lookup(AuditResultJudge.class);
        } catch (Throwable t) {
            return null;
        }
    }
}
