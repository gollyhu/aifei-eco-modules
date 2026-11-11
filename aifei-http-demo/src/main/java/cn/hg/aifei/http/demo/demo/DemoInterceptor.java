/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http.demo.demo;

import cn.aifei.util.StrUtil;
import cn.hg.aifei.http.HttpInterceptor;

/**
 * 演示用拦截器：<b>只做记录</b>——不短路（模块本身也不支持短路）、不抛异常、不改请求。
 *
 * <p>进入（{@code before}）与收尾（{@code after}）都写一条事件到 {@link HttpTrace}，页面据此看到
 * 「进入正序 a→b→c、收尾逆序 c→b→a」以及每次尝试读到的资源名与耗时。多个实例共用一个类，
 * 靠构造器传入的名称区分，名称顺序即 {@code HttpPlugin#addInterceptor} 的登记顺序。</p>
 *
 * <p>{@code primary} 标记只给第一个实例：涉及「请求数 / 尝试数 / 重试数」的计数必须只由一处贡献，
 * 否则三个实例会把计数放大 3 倍。</p>
 */
public class DemoInterceptor implements HttpInterceptor {

    private final String name;

    private final boolean primary;

    /**
     * 创建一个不参与计数的记录型拦截器。
     *
     * @param name 拦截器名称（页面展示用，也是登记顺序的标识）
     */
    public DemoInterceptor(String name) {
        this(name, false);
    }

    /**
     * 创建一个记录型拦截器。
     *
     * @param name 拦截器名称
     * @param primary 是否为主拦截器（参与累计计数）
     */
    public DemoInterceptor(String name, boolean primary) {
        if (StrUtil.isBlank(name)) {
            throw new IllegalArgumentException("拦截器名称不能为空");
        }
        this.name = name.trim();
        this.primary = primary;
        HttpTrace.register(this.name);
    }

    /**
     * 拦截器名称。
     *
     * @return 名称
     */
    public String name() {
        return name;
    }

    /**
     * 进入拦截器：记录一条 {@code before} 事件；不修改请求、不中断、不抛异常。
     *
     * @param attempt 当前尝试的只读视图
     */
    @Override
    public void before(HttpAttempt attempt) {
        HttpTrace.onBefore(name, primary, attempt);
    }

    /**
     * 收尾拦截器：记录一条 {@code after} 事件（成功读 {@code response()}，失败读 {@code error()}）。
     *
     * @param attempt 当前尝试的只读视图
     */
    @Override
    public void after(HttpAttempt attempt) {
        HttpTrace.onAfter(name, primary, attempt);
    }
}
