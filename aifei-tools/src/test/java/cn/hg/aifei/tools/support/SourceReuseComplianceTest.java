/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.support;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 授权 / 版权静态合规测试。
 *
 * <p>扫描四个新增子包的公共层、internal 与对应测试的全部 .java：
 * 统一原创版权头必须存在；来源授权声明、协议串与来源字段名不得泄漏。
 * RFC 向量字面量仅允许出现在测试独立常量中，并按文件精确白名单放行；被测源码零例外。</p>
 */
public class SourceReuseComplianceTest {

    private static final String COPYRIGHT_LINE = "Copyright 2014-2026 糊搞 (3722711@qq.com)";
    /** 拆成两段避免测试源码出现完整 URL host（架构门禁⑭要求测试零外网字面量）。 */
    private static final String URL_HEAD = "https://";
    private static final String URL_TAIL = "gitee.com/gollyhu";

    private static final String[] LICENSE_FORBIDDEN = {
            "aifei vip 订阅用户", "詹波", "aifei.cn", "Copyright 2011-2035"
    };

    /** RFC 6455 公开向量 GUID，仅在测试内以常量表达，不进入被测源码。 */
    private static final String RFC6455_GUID = "258EAFA5-" + "E914-47DA-95CA-" + "C5AB0DC85B11";

    private static final String[] PROTOCOL_FORBIDDEN = {
            RFC6455_GUID, "sortedSign", "canonical", "\"signature\"", "\"<xml>\"",
            "ToUserName", "FromUserName", "http.gzip", "Accept-Encoding", "HttpConfig",
            "Captcha", "TokenGenerator", "ImageGenerator"
    };

    /** key 为子包相对路径；只放行「公开向量字面量」与「负向断言中的禁用字面量」两类测试源码。 */
    private static final Map<String, Set<String>> TEST_LITERAL_EXCEPTIONS =
            new HashMap<String, Set<String>>();

    static {
        Set<String> crypto = new HashSet<String>();
        crypto.add(RFC6455_GUID);
        TEST_LITERAL_EXCEPTIONS.put("crypto/CryptoTest.java", crypto);

        Set<String> random = new HashSet<String>();
        random.addAll(Arrays.asList("Captcha", "TokenGenerator", "ImageGenerator"));
        TEST_LITERAL_EXCEPTIONS.put("random/PublicApiContractRandomTest.java", random);

        Set<String> xml = new HashSet<String>();
        xml.addAll(Arrays.asList("\"<xml>\"", "ToUserName", "FromUserName"));
        TEST_LITERAL_EXCEPTIONS.put("xml/PublicApiContractXmlTest.java", xml);

        Set<String> codec = new HashSet<String>();
        codec.addAll(Arrays.asList("http.gzip", "Accept-Encoding", "HttpConfig"));
        TEST_LITERAL_EXCEPTIONS.put("codec/PublicApiContractCodecTest.java", codec);
    }

    private static final String[] SUBPACKAGES = {"crypto", "random", "xml", "codec"};

    @Test
    public void allNewFilesCarryOriginalCopyrightHeader() throws IOException {
        List<Path> files = newJavaJavaFiles();
        assertTrue("扫描文件不得为空", !files.isEmpty());
        for (Path file : files) {
            String raw = read(file);
            assertTrue(file + " 缺少统一原创版权行", raw.contains(COPYRIGHT_LINE));
            assertTrue(file + " 缺少仓库地址",
                    raw.contains(URL_HEAD) && raw.contains(URL_TAIL));
        }
    }

    @Test
    public void noAifeiVipLicenseNotice() throws IOException {
        for (Path file : newJavaJavaFiles()) {
            String raw = read(file);
            for (String forbidden : LICENSE_FORBIDDEN) {
                assertFalse(file + " 含来源授权声明: " + forbidden, raw.contains(forbidden));
            }
        }
    }

    @Test
    public void noProtocolStringsLeaked() throws IOException {
        for (Path file : newJavaJavaFiles()) {
            String text = stripComments(read(file));
            Set<String> allowed = TEST_LITERAL_EXCEPTIONS.get(relativeKey(file));
            for (String forbidden : PROTOCOL_FORBIDDEN) {
                if (allowed != null && allowed.contains(forbidden)) {
                    continue;
                }
                assertFalse(file + " 含来源协议串/字段名: " + forbidden,
                        text.contains(forbidden));
            }
        }
    }

    // ---------------------------------------------------------------- 工具

    private static List<Path> newJavaJavaFiles() throws IOException {
        List<Path> files = new ArrayList<Path>();
        for (String subpackage : SUBPACKAGES) {
            files.addAll(javaFiles(Paths.get("src/main/java/cn/hg/aifei/tools/" + subpackage)));
            files.addAll(javaFiles(Paths.get("src/test/java/cn/hg/aifei/tools/" + subpackage)));
        }
        return files;
    }

    private static List<Path> javaFiles(Path root) throws IOException {
        if (!Files.exists(root)) {
            return new ArrayList<Path>();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static String relativeKey(Path file) {
        String path = file.toString().replace('\\', '/');
        String marker = "/cn/hg/aifei/tools/";
        int index = path.indexOf(marker);
        return index >= 0 ? path.substring(index + marker.length()) : path;
    }

    /** 剥离 // 与 /* 注释，保留字符串与 URL 中的双斜杠内容不被误判。 */
    private static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        boolean inString = false;
        boolean inChar = false;
        while (i < n) {
            char c = source.charAt(i);
            char next = i + 1 < n ? source.charAt(i + 1) : '\0';
            if (inString) {
                out.append(c);
                if (c == '\\' && i + 1 < n) {
                    out.append(next);
                    i += 2;
                    continue;
                }
                if (c == '"') {
                    inString = false;
                }
                i++;
                continue;
            }
            if (inChar) {
                out.append(c);
                if (c == '\\' && i + 1 < n) {
                    out.append(next);
                    i += 2;
                    continue;
                }
                if (c == '\'') {
                    inChar = false;
                }
                i++;
                continue;
            }
            if (c == '"') {
                inString = true;
                out.append(c);
                i++;
                continue;
            }
            if (c == '\'') {
                inChar = true;
                out.append(c);
                i++;
                continue;
            }
            if (c == '/' && next == '/') {
                while (i < n && source.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
                continue;
            }
            if (c == '/' && next == '*') {
                out.append(' ').append(' ');
                i += 2;
                while (i < n && !(source.charAt(i) == '*' && i + 1 < n
                        && source.charAt(i + 1) == '/')) {
                    out.append(source.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                if (i < n) {
                    out.append(' ').append(' ');
                    i += 2;
                }
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }
}
