/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http.internal;

import cn.hg.aifei.http.HttpException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

/**
 * 编解码：{@code Content-Type} 里的 charset 解析、{@code String} 与 {@code byte[]} 双向转换，只在本类定义。
 *
 * <p>缺省 charset 为 UTF-8；charset 名不可识别时抛 {@link HttpException}，不静默产出半截字符串。</p>
 */
public final class HttpCodec {

    /**
     * 缺省 charset。
     */
    public static final String DEFAULT_CHARSET = "UTF-8";

    /**
     * charset 参数前缀（大小写不敏感匹配）。
     */
    private static final String CHARSET_PARAM = "charset=";

    /**
     * 禁止创建工具类实例。
     */
    private HttpCodec() {
    }

    /**
     * 从 {@code Content-Type} 解析 charset 名。
     *
     * @param contentType 媒体类型，可为 {@code null}
     * @return charset 的规范名（如 {@code GBK}），无法解析或未声明时返回 {@link #DEFAULT_CHARSET}
     * @throws HttpException charset 名不可识别（消息含原值）
     */
    public static String charsetOf(String contentType) {
        if (contentType == null) {
            return DEFAULT_CHARSET;
        }
        int start = indexOfCharsetParam(contentType);
        if (start < 0) {
            return DEFAULT_CHARSET;
        }
        String name = contentType.substring(start + CHARSET_PARAM.length()).trim();
        int end = name.indexOf(';');
        if (end >= 0) {
            name = name.substring(0, end).trim();
        }
        name = unquote(name);
        if (name.isEmpty()) {
            return DEFAULT_CHARSET;
        }
        try {
            return Charset.forName(name).name();
        } catch (IllegalArgumentException e) {
            throw new HttpException("响应 charset 非法: " + name, new UnsupportedEncodingException(name));
        }
    }

    /**
     * 按 {@code Content-Type} 的 charset 把文本编码为字节。
     *
     * @param text        文本，可为 {@code null}
     * @param contentType 媒体类型，可为 {@code null}（charset 缺省 UTF-8）
     * @return 编码结果，{@code text} 为 {@code null} 时返回 {@code null}
     * @throws HttpException charset 名不可识别
     */
    public static byte[] encode(String text, String contentType) {
        if (text == null) {
            return null;
        }
        return text.getBytes(charset(charsetOf(contentType)));
    }

    /**
     * 按 {@code Content-Type} 的 charset 把字节解码为文本。
     *
     * @param bytes       字节，可为 {@code null}
     * @param contentType 媒体类型，可为 {@code null}（charset 缺省 UTF-8）
     * @return 解码结果，{@code bytes} 为 {@code null} 时返回 {@code null}
     * @throws HttpException charset 名不可识别
     */
    public static String decode(byte[] bytes, String contentType) {
        if (bytes == null) {
            return null;
        }
        return new String(bytes, charset(charsetOf(contentType)));
    }

    /**
     * 取 charset 对象；此处不再失败，{@link #charsetOf(String)} 已校验过可识别性。
     *
     * @param name charset 名
     * @return charset 对象
     */
    private static Charset charset(String name) {
        return Charset.forName(name);
    }

    /**
     * 大小写不敏感地定位 {@code charset=} 参数。
     *
     * @param contentType 媒体类型
     * @return 参数起始下标，未出现时返回 -1
     */
    private static int indexOfCharsetParam(String contentType) {
        int length = CHARSET_PARAM.length();
        for (int i = 0; i + length <= contentType.length(); i++) {
            if (contentType.regionMatches(true, i, CHARSET_PARAM, 0, length)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 去掉 charset 取值两侧的双引号。
     *
     * @param value charset 取值
     * @return 去引号后的取值
     */
    private static String unquote(String value) {
        String result = value.trim();
        if (result.length() >= 2 && result.charAt(0) == '"' && result.charAt(result.length() - 1) == '"') {
            result = result.substring(1, result.length() - 1).trim();
        }
        return result;
    }

}
