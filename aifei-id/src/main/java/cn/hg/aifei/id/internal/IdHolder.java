/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.internal;

import cn.hg.aifei.id.IdGenerator;

/**
 * 运行期生成器的持有器：{@code IdKit} 的唯一读写通道，也是「未装配」异常与消息的唯一定义点。
 *
 * <p>写侧只由装配入口（{@code aifei.IdPlugin}）调用：{@link #install(IdGenerator)} 在启动成功后写入，
 * {@link #clear()} 在停机与装配失败回滚时清空。读侧只由门面调用。</p>
 *
 * <p>持有的是 {@code volatile} 引用，写入与读取均无锁；生成器自身线程安全，因此持有器只需保证引用的
 * 可见性。未安装时 {@link #get()} 显式抛 {@link IllegalStateException}，绝不返回 {@code null}。</p>
 *
 * <p>非承诺 API。</p>
 */
public final class IdHolder {

    /**
     * 未装配时的异常消息（唯一措辞来源）。
     */
    private static final String NOT_INSTALLED_MESSAGE =
            "IdGenerator not installed, add IdPlugin to AppConfig.config(Plugins) before using IdKit";

    /**
     * 当前装配的生成器；未装配或已停机时为 {@code null}。
     */
    private static volatile IdGenerator generator;

    /**
     * 禁止创建实例。
     */
    private IdHolder() {
    }

    /**
     * 安装生成器，供装配入口在启动成功后调用。
     *
     * @param generator 已就绪的生成器；传 {@code null} 等价于 {@link #clear()}
     */
    public static void install(IdGenerator generator) {
        IdHolder.generator = generator;
    }

    /**
     * 清空持有器；重复调用无副作用。
     */
    public static void clear() {
        IdHolder.generator = null;
    }

    /**
     * 取当前装配的生成器。
     *
     * @return 已装配的生成器，非 {@code null}
     * @throws IllegalStateException 装配入口尚未启动或已经停止
     */
    public static IdGenerator get() {
        IdGenerator installed = generator;
        if (installed == null) {
            throw new IllegalStateException(NOT_INSTALLED_MESSAGE);
        }
        return installed;
    }
}
