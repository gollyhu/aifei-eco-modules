/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.aifei;

import cn.aifei.aop.Aop;
import cn.aifei.aop.AopKit;
import cn.aifei.aop.Singleton;
import cn.hg.aifei.job.internal.JobContainer;

/**
 * {@link JobContainer} 的框架实现，也是「容器语义映射」的唯一定义点：把两个与框架无关的判定方法映射到 aifei 的
 * {@link Aop#get(Class)} 与单例 / 多例标记上。
 *
 * <p>任务实例一律<b>从容器按类型取</b>（取出的是代理对象，事务与日志生效），不做反射新建 + 手工注入的兜底；
 * 取不到即视为配置错误。</p>
 *
 * <p>单例判定规则与框架 {@code AopFactory#doGet} 保持<b>逐条一致</b>：先按注册的映射把「声明类型」解析成
 * 「实际类型」（接口到实现类 / 父类到子类），再取该类型上的 {@link Singleton} 显式声明；<b>未声明时</b>回落全局默认
 * {@link AopKit#isSingleton()}。{@link Singleton#value()} 无默认值，故「未声明」等价于注解不存在。</p>
 */
public final class AifeiJobContainer implements JobContainer {

    /**
     * 按类型从容器取实例。
     *
     * @param type 类型
     * @param <T> 类型参数
     * @return 容器内的实例（可能为代理对象）
     */
    @Override
    public <T> T get(Class<T> type) {
        return Aop.get(type);
    }

    /**
     * 判定类型的单例 / 多例语义：映射解析后的显式 {@link Singleton} 优先，未声明则回落全局默认。
     *
     * @param type 类型（可为接口：先解析出实现类再判定，与 {@code AopFactory#doGet} 一致）
     * @return {@code true} 表示单例；{@code false} 表示多例（多例对任务实例与监听器都属配置错误）
     */
    @Override
    public boolean isSingleton(Class<?> type) {
        AopKit kit = AopKit.get();
        Class<?> targetType = kit.getAopFactory().getMappingClass(type);
        Singleton singleton = targetType.getAnnotation(Singleton.class);
        if (singleton != null) {
            return singleton.value();
        }
        return kit.isSingleton();
    }
}
