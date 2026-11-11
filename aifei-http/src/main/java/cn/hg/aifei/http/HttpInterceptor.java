/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http;

/**
 * 出站请求的双时机横切钩子：{@link #before} 进入、{@link #after} 收尾。
 *
 * <p>链语义：</p>
 * <ul>
 *   <li>顺序：按 {@code HttpPlugin#addInterceptor} 的注册顺序进入（正序），收尾逆序，保证资源释放对称；</li>
 *   <li>次数：正常路径下每个拦截器的 {@code before} / {@code after} 各恰好一次；</li>
 *   <li>可中断：任一 {@code before} 抛异常（含 {@link HttpException} 与其它运行期异常）→ 后续拦截器不再进入、
 *       真实发送不发生、不重试，异常原样向调用方传播；</li>
 *   <li>收尾范围：只有 {@code before} 已被调用过的拦截器才收到 {@code after}，抛出者自己也收到（异常可在
 *       {@link HttpAttempt#error()} 中看到）；传输层失败时全部照常收尾；</li>
 *   <li>不支持短路：方法无返回值，无法合成响应，中止只有抛异常一条路；</li>
 *   <li>线程：同步执行在发起调用的线程上，不引入额外线程。</li>
 * </ul>
 *
 * <p>只写 {@code before} 的场景可直接用 lambda；需要成对收尾（如指标进入 / 退出配对）时再覆写 {@code after}。
 * 鉴权类拦截器必须用 {@link HttpRequest#header(String, String)}（覆盖）而非 {@link HttpRequest#addHeader}，
 * 否则请求头会随重试次数累积。</p>
 */
public interface HttpInterceptor {

    /**
     * 发送前进入；可读资源名与尝试序号，可改请求头与 query。
     *
     * @param attempt 本次尝试的只读视图
     * @throws HttpException            中止本次调用（原样传播给调用方，不重试）
     * @throws IllegalArgumentException 调用方参数当场写错
     */
    void before(HttpAttempt attempt);

    /**
     * 收尾；默认空实现。
     *
     * @param attempt 本次尝试的只读视图，已含结果或异常
     */
    default void after(HttpAttempt attempt) {
    }

    /**
     * 一次尝试的只读视图：{@code before} 阶段可读方法 / URL / 资源名 / 序号，{@code after} 阶段另可读结果与耗时。
     */
    interface HttpAttempt {

        /**
         * 当前请求；{@code before} 阶段可改头与 query。
         *
         * @return 请求构建器
         */
        HttpRequest request();

        /**
         * 请求方法（大写）。
         *
         * @return 方法名
         */
        String method();

        /**
         * 完整请求 URL（含 query，进入阶段与收尾阶段取值一致）。
         *
         * @return 完整 URL
         */
        String url();

        /**
         * 最终资源名，本次调用内恒定（不随重试与拦截器改写 query 变化）。
         *
         * @return 资源名
         */
        String resource();

        /**
         * 本次尝试序号，从 1 起。
         *
         * @return 尝试序号
         */
        int attempt();

        /**
         * 本次尝试的响应。
         *
         * @return {@code before} 阶段与失败时为 {@code null}，成功时为该次响应
         */
        HttpResponse response();

        /**
         * 本次尝试的失败原因。
         *
         * @return 失败时的异常（传输层 {@link HttpException} 或拦截器抛出的任意异常），成功时为 {@code null}
         */
        Throwable error();

        /**
         * 本次尝试耗时（不含重试间隔）。
         *
         * @return 毫秒数；{@code before} 阶段为 0
         */
        long elapsedMillis();
    }
}
