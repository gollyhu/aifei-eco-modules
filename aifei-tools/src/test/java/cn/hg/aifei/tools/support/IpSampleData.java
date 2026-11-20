/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.support;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

/**
 * 内嵌最小 xdb 测试样本（gzip + base64 固化，源码零二进制、测试期零联网）。
 *
 * <p>样本按 ip2region 3.3.7 的 xdb 结构 3.0 一次性生成后压缩固化；首次取用解码到
 * {@code target/}（构建临时目录，不入仓），覆盖境内 IPv4 中文五段、境外 IPv4 英文、
 * Reserved 保留段、含 "0" 哨兵段、IPv6 中文段与 IPv6 保留段。</p>
 */
public final class IpSampleData {

    /** 境内 IPv4：中文五段。 */
    public static final String V4_CN_IP = "1.2.3.4";
    public static final String V4_CN_REGION = "中国|北京市|北京市|联通|CN";
    /** 境外 IPv4：英文五段。 */
    public static final String V4_FOREIGN_IP = "8.8.8.8";
    public static final String V4_FOREIGN_REGION = "United States|California|Los Angeles|Comcast|US";
    /** 保留段：五段全为保留/0，解析后应全空串。 */
    public static final String V4_RESERVED_IP = "10.1.1.1";
    /** 含 "0" 哨兵段：国家与 ISO 保留，省份/城市为 0。 */
    public static final String V4_ZERO_IP = "100.1.1.1";
    /** IPv6：中文五段。 */
    public static final String V6_CN_IP = "2001:db8::1";
    public static final String V6_CN_REGION = "中国|广东省|深圳市|电信|CN";
    /** IPv6 保留段。 */
    public static final String V6_RESERVED_IP = "2001:db9::1";
    /** 未收录 IP：内核返回空串。 */
    public static final String UNKNOWN_IP = "203.0.113.5";

    private static final String V4_XDB_GZ_B64 =
            "H4sIAAAAAAAC/+3boUtDQRzA8XtoeCxblQW7i6uyKgbHmmW45xjoBtvD9MK0ahwImoyCNoUhiP/MhvpXzBtbENRmUT6f4+D3vXL1XngrIQnDh/V2SNJQjXs1LgCAf2357vmxAQAAAAAAAAAAAAAAAIC/x/+CAAAA4DseAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAL6TpKEaNwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAPySZDabJZuhmqTzSmOlW2F/UaVYpY1wvqhWrNZauI01Gd9Pr5+L6cXl5OluOj77NL2djt6HV0Vtt9Ht5FmrXM+beTYoas2jzmGv3+00i53eoLzdbWdH8+Pe8UFzkBeN+l42yPonWav4OlSKyvLCOBWvo8fJy0284AOPxZfmvQEIAA==";
    private static final String V6_XDB_GZ_B64 =
            "H4sIAAAAAAAC/+3LMUoDURAG4Hkoso1HUCw8QO5gu6TIGVxsxYDVKxLPYOc2OYCFRHG3kL3MbnIMXbuQHEAh3wc/zAz/nESKxfvFXaQiyjFncRoAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABwtFIR5Zg/+wcAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAOBfukrnzf7+veM6ylT8Xtu9Vrvbuoz7sdU3r8PLVx7arm/qbb3Mm8+3oV4PzVPePn/03SrfTGfVvHp4rG7z4TDJkx/jgJ+qjgEIAA==";

    private static Path v4;
    private static Path v6;

    private IpSampleData() {
    }

    /** 解码并返回 IPv4 xdb 文件绝对路径（首次调用写入 target/）。 */
    public static synchronized String v4Path() {
        if (v4 == null) {
            v4 = decode(V4_XDB_GZ_B64, "ip2region_v4_qa.xdb");
        }
        return v4.toAbsolutePath().toString();
    }

    /** 解码并返回 IPv6 xdb 文件绝对路径（首次调用写入 target/）。 */
    public static synchronized String v6Path() {
        if (v6 == null) {
            v6 = decode(V6_XDB_GZ_B64, "ip2region_v6_qa.xdb");
        }
        return v6.toAbsolutePath().toString();
    }

    private static Path decode(String base64, String fileName) {
        try {
            byte[] gz = Base64.getDecoder().decode(base64);
            byte[] raw;
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz));
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                raw = out.toByteArray();
            }
            Path dir = Paths.get("target");
            Files.createDirectories(dir);
            Path file = dir.resolve(fileName);
            Files.write(file, raw);
            return file;
        } catch (IOException e) {
            throw new IllegalStateException("failed to decode embedded xdb sample", e);
        }
    }
}
