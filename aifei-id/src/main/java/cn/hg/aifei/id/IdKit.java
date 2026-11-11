/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id;

import cn.hg.aifei.id.internal.IdHolder;

/**
 * 取 ID 的静态门面：不注入也能发号，业务一行代码即可拿到 ID。
 *
 * <p>本类是<b>有状态门面</b>：它操作的是 {@code IdPlugin} 装配出来的运行期对象（生成器、workerId
 * 租约、号段游标），因此<b>未装配即失败</b>——{@code IdPlugin} 未 {@code start()} 或已 {@code stop()}
 * 时调用抛 {@link IllegalStateException}。静默返回 {@code 0} 或回退本地自增会让「忘记装配」变成数据
 * 事故，故一律显式失败。</p>
 *
 * <p>{@link #nextIdStr()} 是全模块<b>唯一</b>的 {@code long → 十进制字符串} 转换点：19 位十进制超出
 * JS {@code Number.MAX_SAFE_INTEGER}（2^53），前端与 JSON 必须用字符串形态，不要在别处自拼。</p>
 *
 * <p>本类无实例状态，全部方法为静态方法，线程安全；异常来自被委托的生成器，本类不转换、不包装。</p>
 */
public final class IdKit {

    /**
     * 禁止创建工具类实例。
     */
    private IdKit() {
    }

    /**
     * 取当前装配的生成器产出的下一个 ID。
     *
     * @return 下一个 ID，恒正且在同一实例上严格递增
     * @throws IllegalStateException {@code IdPlugin} 未启动或已停止
     * @throws IdException 生成器运行期不可用（时钟回拨超阈、取段失败、workerId 租约丢失等）
     */
    public static long nextId() {
        return IdHolder.get().nextId();
    }

    /**
     * 取下一个 ID 的十进制字符串形态，供前端与 JSON 使用。
     *
     * @return 下一个 ID 的十进制字符串（等价于 {@code Long.toString(nextId())}）
     * @throws IllegalStateException {@code IdPlugin} 未启动或已停止
     * @throws IdException 生成器运行期不可用（时钟回拨超阈、取段失败、workerId 租约丢失等）
     */
    public static String nextIdStr() {
        return Long.toString(nextId());
    }
}
