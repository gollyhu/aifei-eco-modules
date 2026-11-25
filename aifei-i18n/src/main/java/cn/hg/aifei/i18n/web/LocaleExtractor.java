/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.web;

import cn.aifei.core.Input;

/**
 * 从请求提取原始 {@code Accept-Language} 头的函数式接口。
 *
 * <p>框架 {@code Input} 无 header 访问 API：消费方自定义 Input 暴露 header 后以 lambda 提供，
 * 如 {@code input -> input.getHeader("Accept-Language")}。无法提取返回 {@code null}。
 * 抛出的异常由 {@link I18nHandler} 吞掉；「解析 / 规范化 / 白名单匹配」由
 * {@code internal.LocaleResolver#parseAcceptLanguage} 唯一实现。</p>
 *
 * @param <I> 请求输入类型，须为框架 Input 子类型
 */
@FunctionalInterface
public interface LocaleExtractor<I extends Input> {

    /**
     * @param input 请求输入对象
     * @return 原始 Accept-Language 头字符串；无法提取返回 {@code null}
     */
    String acceptLanguage(I input);
}
