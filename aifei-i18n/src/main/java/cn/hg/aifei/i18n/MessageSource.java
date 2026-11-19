/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n;

import java.util.Map;

/**
 * 文案来源的开放扩展点：任何满足本接口契约的实现都可按稳定名注册并按序参与叠加与回退，
 * 无需修改模块代码或 pom。
 *
 * <p><b>必修要素</b>（契约唯一定义点）：</p>
 * <ol>
 *   <li>{@link #name()} — 稳定名，非空且应用内唯一；与保留名 {@code properties} / {@code db} / {@code kv} 冲突在启动期报错。</li>
 *   <li>{@link #get(String, String)} — 单点命中：{@code localeTag} 已由模块规范化，实现方直接用；
 *       返回译文（可为空串），<b>{@code null} 表示无译文</b>，空串是合法命中（必修要素③：无译文 ≠ 空串）。</li>
 * </ol>
 *
 * <p><b>选修要素</b>（缺失时降级而非失败）：</p>
 * <ul>
 *   <li>{@link #list(String)} — 整包列举：返回 {@code key → 原文}（{@code {0}} 不替换）；
 *       返回 {@code null} 表示不支持（整包导出时跳过 + WARN）。</li>
 *   <li>{@link #refresh()} — 运行期刷新：默认无操作（{@code properties} 改值须重启的降级表现）。</li>
 * </ul>
 *
 * <p>实现方应无状态或自行保证线程安全；模块在启动期构造并持有唯一实例。</p>
 */
public interface MessageSource {

    /**
     * 来源稳定名：非空、应用内唯一；{@code i18n.sources} 以该名引用。
     *
     * @return 稳定名
     */
    String name();

    /**
     * 单点命中：取 {@code key + locale} 的译文。
     *
     * @param key       文案 key，非空
     * @param localeTag 已由模块规范化的 locale tag（如 {@code zh-CN}），实现方直接用
     * @return 译文（可为空串，空串 = 命中）；{@code null} = 无译文；抛运行时异常 = 本次无译文 + WARN
     */
    String get(String key, String localeTag);

    /**
     * 选修·整包列举：返回该 locale 掌握的全部 {@code key → 原文}。
     *
     * @param localeTag 已规范化的 locale tag
     * @return key 到原文的映射；{@code null} 表示不支持整包列举（导出时跳过 + WARN）
     */
    default Map<String, String> list(String localeTag) {
        return null;
    }

    /**
     * 选修·运行期刷新：默认无操作。
     */
    default void refresh() {
    }
}
