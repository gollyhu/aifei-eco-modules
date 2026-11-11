/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.support;

/**
 * 单测用类加载器：对指定类名一律回答「不存在」，其余类全部委派给父加载器。
 *
 * <p>用途：{@code aifei-cache} / {@code aifei-lock} 在测试 classpath 上恒存在，无法用「删依赖」的方式
 * 复现可选依赖缺失。{@code internal.IdDeps} 的探测走线程上下文类加载器，因此用例临时把上下文加载器
 * 换成隐藏目标类的本类，即可让「依赖缺失」成为可控输入——且探测结果由 {@code IdDeps} 转成携带坐标的
 * {@code IdException}，不应出现 {@code NoClassDefFoundError}。</p>
 *
 * <p>非线程安全：只用于在单个用例内包裹一次装配调用，用完即恢复原上下文加载器。</p>
 */
public final class HidingClassLoader extends ClassLoader {

    /**
     * 需要伪装成「不存在」的全限定类名。
     */
    private final String hiddenClassName;

    /**
     * 以指定父加载器构造。
     *
     * @param parent 父加载器，承担其余全部类的加载
     * @param hiddenClassName 需要隐藏的全限定类名
     */
    public HidingClassLoader(ClassLoader parent, String hiddenClassName) {
        super(parent);
        this.hiddenClassName = hiddenClassName;
    }

    /**
     * 加载类：命中隐藏名单即回答「类不存在」，其余全部委派父加载器。
     *
     * @param name 全限定类名
     * @param resolve 是否链接
     * @return 加载到的类
     * @throws ClassNotFoundException 目标是隐藏类，或父加载器同样找不到
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (hiddenClassName.equals(name)) {
            throw new ClassNotFoundException(name);
        }
        return super.loadClass(name, resolve);
    }
}
