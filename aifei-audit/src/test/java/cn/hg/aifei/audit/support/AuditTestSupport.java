/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.support;

import cn.aifei.argument.Argument;
import cn.aifei.aop.Interceptor;
import cn.aifei.aop.Invocation;
import cn.aifei.core.Input;
import cn.aifei.core.Output;
import cn.aifei.router.Action;
import cn.aifei.util.Prop;
import cn.hg.aifei.audit.internal.AuditConfig;
import cn.hg.aifei.audit.internal.AuditEnvironment;
import cn.hg.aifei.audit.internal.AuditHolder;
import cn.hg.aifei.audit.internal.AuditRuntime;
import cn.hg.aifei.audit.spi.AuditActorProvider;
import cn.hg.aifei.audit.spi.AuditResultJudge;
import cn.hg.aifei.audit.spi.AuditSink;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 测试装配助手：构造与 routes.scan 同形的 {@link Invocation}、构造配置与运行期快照。
 *
 * <p>注解路径不经 HTTP：按框架真实形态 {@code new Action(...) + new Invocation(action, target, null, null)}
 * 驱动拦截器（与前序探针用例使用相同装配）。</p>
 */
public final class AuditTestSupport {

    private AuditTestSupport() {
    }

    /**
     * @param keyValues 交替的键值对
     * @return 配置项
     */
    public static Properties props(String... keyValues) {
        Properties properties = new Properties();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            properties.setProperty(keyValues[i], keyValues[i + 1]);
        }
        return properties;
    }

    /**
     * @param keyValues 交替的键值对
     * @return 框架配置源
     */
    public static Prop prop(String... keyValues) {
        Prop prop = new Prop();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            // Prop(String, String) 是「配置文件 + 编码」构造器，不能用它拼键值；直接写其 Properties
            prop.getProperties().setProperty(keyValues[i], keyValues[i + 1]);
        }
        return prop;
    }

    /**
     * @param keyValues 交替的键值对
     * @return 已解析配置
     */
    public static AuditConfig config(String... keyValues) {
        return AuditConfig.parse(props(keyValues));
    }

    /**
     * @param sink   落点，可为 null
     * @param actor  操作人来源，可为 null
     * @param judge  判定，可为 null
     * @param trace  链路取值器，可为 null
     * @param client 客户端取值器，可为 null
     * @param config 配置键值对
     * @return 运行期快照
     */
    public static AuditRuntime runtime(AuditSink sink, AuditActorProvider actor, AuditResultJudge judge,
                                       Supplier<String> trace, Supplier<String> client, String... config) {
        return new AuditRuntime(config(config), sink, actor, judge, trace, client);
    }

    /**
     * @return 容器内无任何实现的框架能力端口（{@code lookup} 按框架语义抛异常，`scan` 恒空）
     */
    public static AuditEnvironment emptyEnvironment() {
        return new AuditEnvironment() {
            @Override
            public <T> T lookup(Class<T> type) {
                throw new IllegalStateException("容器内无该类型: " + type.getName());
            }

            @Override
            public Set<Class<?>> scan(String packageName) {
                return new LinkedHashSet<Class<?>>();
            }
        };
    }

    /**
     * 直接装快照并清空既有状态。
     *
     * @param runtime 运行期快照
     */
    public static void install(AuditRuntime runtime) {
        AuditHolder.clear();
        AuditHolder.install(runtime);
    }

    /**
     * 按 routes.scan 的真实形态构造调用现场。
     *
     * @param targetClass  被调用的类
     * @param methodName   方法名
     * @param args         实参（类型取实参自身类型）
     * @param interceptors 拦截器数组，下标 0 为最外层
     * @return 调用现场
     * @throws Exception 方法不存在或实例化失败
     */
    public static Invocation invocation(Class<?> targetClass, String methodName, Object[] args,
                                        Interceptor... interceptors) throws Exception {
        Class<?>[] types = new Class<?>[args.length];
        Argument<?, ?, ?>[] arguments = new Argument<?, ?, ?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i] == null ? Object.class : args[i].getClass();
            arguments[i] = fixed(args[i]);
        }
        Method method = targetClass.getMethod(methodName, types);
        Object target = targetClass.getDeclaredConstructor().newInstance();
        Action action = new Action("targetPath", "/test/" + methodName, interceptors, targetClass, method, arguments);
        return new Invocation(action, target, null, null);
    }

    private static Argument<Input, Output, Object> fixed(final Object value) {
        return new Argument<Input, Output, Object>() {
            @Override
            public Object getValue(Input input, Output output) {
                return value;
            }
        };
    }
}
