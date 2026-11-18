/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.internal;

import cn.aifei.log.Log;
import cn.hg.aifei.audit.AuditEvent;
import cn.hg.aifei.audit.spi.AuditActorProvider;
import cn.hg.aifei.audit.spi.AuditResultJudge;
import cn.hg.aifei.audit.spi.AuditSink;
import java.util.function.Supplier;

/**
 * 运行期失败隔离的唯一实现点，也是模块日志的唯一写入点（非承诺 API）。
 *
 * <p>日志必须惰性获取：应用未装配 {@code LogFactory} 时 {@code Log.get} 直接抛异常，
 * 本类吞掉并放弃本条日志（不得改用标准输出）。</p>
 */
public final class SafeInvoker {

    private static final String PREFIX = "[aifei-audit] ";

    private static volatile Log logInstance;

    private SafeInvoker() {
    }

    /**
     * 交付事件：落点异常按 fail-fast 处理。
     *
     * @param rt             运行期快照
     * @param event          已构造完成的事件
     * @param businessFailed 业务方法是否已抛异常（为 true 时业务原异常优先，落点异常只记日志）
     */
    public static void deliver(AuditRuntime rt, AuditEvent event, boolean businessFailed) {
        AuditSink sink = rt.sink();
        if (sink == null) {
            return;
        }
        try {
            sink.accept(event);
        } catch (Throwable t) {
            if (!businessFailed && rt.config().failFast()) {
                if (t instanceof RuntimeException) {
                    throw (RuntimeException) t;
                }
                if (t instanceof Error) {
                    throw (Error) t;
                }
                throw new RuntimeException(t);
            }
            warn("落点交付失败，已按失败隔离吞掉: " + t);
        }
    }

    /**
     * 模块内部缺陷隔离：事件构造 / 脱敏截断 / 时钟等环节异常时只记日志、本次不产事件。
     *
     * @param builder 事件构造闭包
     * @return 构造成功的事件；内部缺陷时为 {@code null}
     */
    public static AuditEvent capture(Supplier<AuditEvent> builder) {
        try {
            return builder.get();
        } catch (Throwable t) {
            warn("采集失败，本次不产事件: " + t);
            return null;
        }
    }

    /**
     * @param rt 运行期快照
     * @return 操作人 id；无 provider 或取值失败为 {@code null}
     */
    public static String actorId(AuditRuntime rt) {
        AuditActorProvider provider = rt.actorProvider();
        if (provider == null) {
            return null;
        }
        try {
            return blankToNull(provider.actorId());
        } catch (Throwable t) {
            warn("操作人 id 取值失败，已按失败隔离吞掉: " + t);
            return null;
        }
    }

    /**
     * @param rt 运行期快照
     * @return 操作人名；无 provider 或取值失败为 {@code null}
     */
    public static String actorName(AuditRuntime rt) {
        AuditActorProvider provider = rt.actorProvider();
        if (provider == null) {
            return null;
        }
        try {
            return blankToNull(provider.actorName());
        } catch (Throwable t) {
            warn("操作人名取值失败，已按失败隔离吞掉: " + t);
            return null;
        }
    }

    /**
     * 判定业务码失败；判定实现异常时退回默认口径（{@code false} = 不因判定而失败）。
     *
     * @param judge       判定实现，可为 null
     * @param action      动作码
     * @param returnValue 业务返回值
     * @return 判定为失败时 {@code true}
     */
    public static boolean judge(AuditResultJudge judge, String action, Object returnValue) {
        if (judge == null) {
            return false;
        }
        try {
            return judge.isFailure(action, returnValue);
        } catch (Throwable t) {
            warn("结果判定失败，退回默认口径: " + t);
            return false;
        }
    }

    /**
     * @param supplier 注入的取值器，可为 null
     * @return 取值结果；空白归一为 {@code null}
     */
    public static String supply(Supplier<String> supplier) {
        if (supplier == null) {
            return null;
        }
        try {
            return blankToNull(supplier.get());
        } catch (Throwable t) {
            warn("注入值取值失败，已按失败隔离吞掉: " + t);
            return null;
        }
    }

    /**
     * 反射加载扫描类；失败只记日志并返回 {@code null}（调用方据此报配置错误）。
     *
     * @param className 全限定类名
     * @return 已加载的类；加载失败为 {@code null}
     */
    public static Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (Throwable t) {
            warn("扫描类加载失败: " + className);
            return null;
        }
    }

    /**
     * 记一条模块告警；日志通道不可用时静默放弃。
     *
     * @param message 消息
     */
    public static void warn(String message) {
        Log logger = log();
        if (logger == null) {
            return;
        }
        try {
            logger.warn(PREFIX + message);
        } catch (Throwable t) {
            // 日志失败不影响业务
        }
    }

    private static Log log() {
        Log current = logInstance;
        if (current == null) {
            try {
                current = Log.get(SafeInvoker.class);
                logInstance = current;
            } catch (Throwable t) {
                return null;
            }
        }
        return current;
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }
}
