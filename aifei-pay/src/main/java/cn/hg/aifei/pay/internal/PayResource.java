/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 凭据文本读取的唯一入口：支持内联 PEM 文本与 {@code @file:<路径>} 两种形态。
 *
 * <p>明确不支持 classpath 引用——classpath 资源的解析依赖调用方的类加载器布局，库模块不承担该责任。</p>
 */
public final class PayResource {

    /**
     * 文件引用的前缀。
     */
    private static final String FILE_PREFIX = "@file:";

    /**
     * 禁止创建工具类实例。
     */
    private PayResource() {
    }

    /**
     * 读取凭据文本。
     *
     * @param key      配置键名，用于异常消息
     * @param rawValue 原始配置值：以 {@code @file:} 开头按文件读取，否则视为内联文本
     * @return 去掉首尾空白的凭据文本
     * @throws cn.hg.aifei.pay.AliPayException 值缺失、文件不可读或内容为空
     */
    public static String read(String key, String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            throw PayErrors.missingConfig(key);
        }
        String value = rawValue.trim();
        if (!value.startsWith(FILE_PREFIX)) {
            return value;
        }
        String location = value.substring(FILE_PREFIX.length()).trim();
        if (location.isEmpty()) {
            throw PayErrors.invalidConfig(key, location, "文件引用缺少路径");
        }
        Path path = Paths.get(location);
        String content;
        try {
            content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw PayErrors.invalidConfig(key, location, "凭据文件不存在或不可读：" + location);
        }
        if (content.trim().isEmpty()) {
            throw PayErrors.invalidConfig(key, location, "凭据文件内容为空：" + location);
        }
        return content.trim();
    }
}
