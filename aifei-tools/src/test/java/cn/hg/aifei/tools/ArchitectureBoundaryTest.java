/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 架构门禁（33 例）：按源码文本断言分层边界、公共面收敛与 Java 8 底线。
 *
 * <p>执行位置必须为 aifei-tools 模块根目录（依赖相对路径 src/main/java），
 * 由 surefire workingDirectory 固定为 ${project.basedir} 保证。</p>
 */
public class ArchitectureBoundaryTest {

    private static final Path SOURCE_ROOT = Paths.get("src/main/java");
    private static final Path TEST_ROOT = Paths.get("src/test/java");
    private static final Path RESOURCE_ROOT = Paths.get("src/main/resources");
    private static final Path POM = Paths.get("pom.xml");

    private static final String ROOT_PACKAGE = "cn/hg/aifei/tools/";
    private static final String IPDB_PACKAGE = ROOT_PACKAGE + "ipdb/";
    private static final String CAPTCHA_PACKAGE = ROOT_PACKAGE + "captcha/";
    private static final String QR_PACKAGE = ROOT_PACKAGE + "qr/";
    private static final String MASK_PACKAGE = ROOT_PACKAGE + "mask/";
    private static final String KDF_PACKAGE = ROOT_PACKAGE + "kdf/";
    private static final String IP_INTERNAL = IPDB_PACKAGE + "internal/";
    private static final String CAPTCHA_INTERNAL = CAPTCHA_PACKAGE + "internal/";
    private static final String QR_INTERNAL = QR_PACKAGE + "internal/";
    private static final String KDF_INTERNAL = KDF_PACKAGE + "internal/";

    private static final Set<String> IP_PUBLIC = new LinkedHashSet<String>(Arrays.asList(
            "IpSearcher.java", "IpKit.java", "IpRegion.java", "IpException.java", "IpQueryMode.java"));
    private static final Set<String> CAPTCHA_PUBLIC = new LinkedHashSet<String>(Arrays.asList(
            "Captcha.java", "CaptchaKit.java", "CaptchaType.java", "CaptchaException.java"));
    private static final Set<String> QR_PUBLIC = new LinkedHashSet<String>(Arrays.asList(
            "QrKit.java", "QrOptions.java", "QrErrorCorrection.java", "QrException.java"));
    private static final Set<String> MASK_PUBLIC = new LinkedHashSet<String>(Arrays.asList(
            "MaskUtil.java", "MaskKit.java", "MaskException.java"));
    private static final Set<String> KDF_PUBLIC = new LinkedHashSet<String>(Arrays.asList(
            "KdfKit.java", "KdfSpec.java", "KdfHash.java", "KdfException.java"));

    private static final String CRYPTO_PACKAGE = ROOT_PACKAGE + "crypto/";
    private static final String RANDOM_PACKAGE = ROOT_PACKAGE + "random/";
    private static final String XML_PACKAGE = ROOT_PACKAGE + "xml/";
    private static final String CODEC_PACKAGE = ROOT_PACKAGE + "codec/";
    private static final String CRYPTO_INTERNAL = CRYPTO_PACKAGE + "internal/";
    private static final String RANDOM_INTERNAL = RANDOM_PACKAGE + "internal/";
    private static final String XML_INTERNAL = XML_PACKAGE + "internal/";
    private static final String CODEC_INTERNAL = CODEC_PACKAGE + "internal/";

    private static final Set<String> CRYPTO_PUBLIC = new LinkedHashSet<String>(Arrays.asList(
            "CryptoUtil.java", "CryptoException.java"));
    private static final Set<String> RANDOM_PUBLIC = new LinkedHashSet<String>(Arrays.asList(
            "RandomUtil.java", "RandomException.java"));
    private static final Set<String> XML_PUBLIC = new LinkedHashSet<String>(Arrays.asList(
            "XmlUtil.java", "XmlException.java"));
    private static final Set<String> CODEC_PUBLIC = new LinkedHashSet<String>(Arrays.asList(
            "CodecUtil.java", "CodecException.java"));

    /** ① 根包 cn/hg/aifei/tools/ 直接子级 .java 精确 0。 */
    @Test
    public void rootPackageEmpty() throws IOException {
        List<String> names = directChildren(ROOT_PACKAGE);
        assertEquals("根包公共类型必须为 0", 0, names.size());
    }

    /** ② ipdb 公共面精确 5 且集合相等。 */
    @Test
    public void ipPublicSurfaceExact() throws IOException {
        assertPublicSurface(IPDB_PACKAGE, IP_PUBLIC, 5, "ipdb");
    }

    /** ③ captcha 公共面精确 4 且集合相等。 */
    @Test
    public void captchaPublicSurfaceExact() throws IOException {
        assertPublicSurface(CAPTCHA_PACKAGE, CAPTCHA_PUBLIC, 4, "captcha");
    }

    /** ④ ip2region import 只允许在 ipdb/internal。 */
    @Test
    public void ip2regionOnlyInIpInternal() throws IOException {
        for (Path file : javaFiles(SOURCE_ROOT)) {
            for (String line : imports(stripComments(read(file)))) {
                if (line.startsWith("org.lionsoul.")) {
                    assertTrue("ip2region 依赖泄漏出 ipdb/internal: " + normalize(file) + " -> " + line,
                            normalize(file).startsWith(IP_INTERNAL));
                }
            }
        }
    }

    /** ⑤ 共享缓存 import 只允许在 captcha/internal。 */
    @Test
    public void cacheOnlyInCaptchaInternal() throws IOException {
        for (Path file : javaFiles(SOURCE_ROOT)) {
            for (String line : imports(stripComments(read(file)))) {
                if (line.startsWith("cn.hg.aifei.cache.")) {
                    assertTrue("共享缓存依赖泄漏出 captcha/internal: " + normalize(file) + " -> " + line,
                            normalize(file).startsWith(CAPTCHA_INTERNAL));
                }
            }
        }
    }

    /** ⑥ 全模块零 cn.aifei.* 引用。 */
    @Test
    public void noFrameworkReference() throws IOException {
        assertNoImportInSources(SOURCE_ROOT, "cn.aifei.", "禁止引用框架包 cn.aifei.*");
    }

    /** ⑦ 零 Web / 零 DB / 零日志绑定。 */
    @Test
    public void noWebSqlOrLogBinding() throws IOException {
        for (Path file : javaFiles(SOURCE_ROOT)) {
            for (String line : imports(stripComments(read(file)))) {
                assertFalse("库模块禁止 Web/SQL/日志门面 import: " + normalize(file) + " -> " + line,
                        line.startsWith("io.undertow.") || line.startsWith("java.sql.")
                                || line.startsWith("org.slf4j."));
            }
        }
        String pom = stripComments(read(POM));
        for (String artifact : Arrays.asList("slf4j-simple", "logback", "log4j")) {
            assertFalse("pom 禁止声明日志绑定: " + artifact, pom.contains(artifact));
        }
    }

    /** ⑧ 公共类型源码零第三方 / 零框架字面量（kdf 子包扩充覆盖：扫描 kdf 公共层第三方/框架字面量，口径同原四子包）。 */
    @Test
    public void publicSignaturesNoThirdParty() throws IOException {
        List<String[]> publicFiles = new ArrayList<String[]>();
        publicFiles.add(new String[]{IPDB_PACKAGE, "ipdb", "5"});
        publicFiles.add(new String[]{CAPTCHA_PACKAGE, "captcha", "4"});
        publicFiles.add(new String[]{QR_PACKAGE, "qr", "4"});
        publicFiles.add(new String[]{MASK_PACKAGE, "mask", "3"});
        publicFiles.add(new String[]{KDF_PACKAGE, "kdf", "4"});
        for (String[] group : publicFiles) {
            Set<String> names = group[1].equals("ipdb") ? IP_PUBLIC
                    : group[1].equals("captcha") ? CAPTCHA_PUBLIC
                    : group[1].equals("qr") ? QR_PUBLIC
                    : group[1].equals("kdf") ? KDF_PUBLIC : MASK_PUBLIC;
            for (String name : names) {
                Path file = SOURCE_ROOT.resolve(group[0]).resolve(name);
                String text = stripComments(read(file));
                for (String forbidden : Arrays.asList("org.lionsoul.", "com.google.zxing.",
                        "cn.aifei.", "cn.hg.aifei.cache.")) {
                    assertFalse("公共类型不得含第三方/框架字面量: " + name + " -> " + forbidden,
                            text.contains(forbidden));
                }
            }
        }
    }

    /** ⑨ 零 Java 8 之后的语法 / API。 */
    @Test
    public void java8Compatibility() throws IOException {
        String[] forbidden = {"List.of(", "Map.of(", "Set.of(", "record ", "var ",
                "\"\"\"", "sealed "};
        java.util.regex.Pattern patternMatch = java.util.regex.Pattern
                .compile("instanceof\\s+[\\w.<>]+\\s+\\w+");
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String text = stripComments(read(file));
            for (String token : forbidden) {
                assertFalse("Java 8 不兼容字面量: " + normalize(file) + " -> " + token,
                        text.contains(token));
            }
            assertFalse("Java 8 不支持 instanceof 模式匹配: " + normalize(file),
                    patternMatch.matcher(text).find());
        }
    }

    /** ⑩ Class.forName 只允许在 IpDeps / CaptchaDeps。 */
    @Test
    public void classForNameOnlyInDeps() throws IOException {
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String text = stripComments(read(file));
            if (!text.contains("Class.forName")) {
                continue;
            }
            String path = normalize(file);
            assertTrue("Class.forName 只允许出现在 IpDeps.java / CaptchaDeps.java: " + path,
                    path.equals(IP_INTERNAL + "IpDeps.java")
                            || path.equals(CAPTCHA_INTERNAL + "CaptchaDeps.java"));
        }
    }

    /** ⑪ 四类消息前缀只允许在对应 Errors 类（覆盖 ip/captcha/qr/mask，kdf 扩充覆盖 kdf）。 */
    @Test
    public void errorMessagesOnlyInErrors() throws IOException {
        String[][] rules = {
                {"ip: ", IP_INTERNAL + "IpErrors.java"},
                {"captcha: ", CAPTCHA_INTERNAL + "CaptchaErrors.java"},
                {"qr: ", QR_INTERNAL + "QrErrors.java"},
                {"mask: ", "cn/hg/aifei/tools/mask/internal/MaskErrors.java"},
                {"kdf: ", KDF_INTERNAL + "KdfErrors.java"}
        };
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            String text = stripComments(read(file));
            for (String[] rule : rules) {
                if (!path.equals(rule[1])) {
                    assertFalse("异常消息前缀只允许在对应 Errors 类: " + path + " -> " + rule[0],
                            text.contains(rule[0]));
                }
            }
        }
        for (String[] rule : rules) {
            Path errors = SOURCE_ROOT.resolve(rule[1]);
            assertTrue("Errors 类必须承载消息前缀: " + rule[1],
                    stripComments(read(errors)).contains(rule[0]));
        }
    }

    /** ⑫ region 分隔符与保留值归空串只在 RegionParser。 */
    @Test
    public void regionParsingOnlyInParser() throws IOException {
        String parser = IP_INTERNAL + "RegionParser.java";
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            String text = stripComments(read(file));
            if (!path.equals(parser)) {
                assertFalse("region 分隔符只在 RegionParser: " + path,
                        text.contains("\\|"));
                assertFalse("region 保留值归空串只在 RegionParser: " + path,
                        text.contains("\"Reserved\""));
            }
        }
        String text = stripComments(read(SOURCE_ROOT.resolve(parser)));
        assertTrue("RegionParser 必须声明分隔符", text.contains("\\|"));
        assertTrue("RegionParser 必须归一保留值", text.contains("\"Reserved\""));
    }

    /** ⑬ 零 provider / 零 SPI。 */
    @Test
    public void noServiceProvider() throws IOException {
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String text = stripComments(read(file));
            for (String forbidden : Arrays.asList("ServiceLoader", "META-INF/services")) {
                assertFalse("禁止 provider/SPI: " + normalize(file) + " -> " + forbidden,
                        text.contains(forbidden));
            }
        }
        Path services = RESOURCE_ROOT.resolve("META-INF/services");
        assertFalse("不得存在 META-INF/services 目录", Files.exists(services));
    }

    /** ⑭ 测试源码零外网：http(s) 字面量 host 只能是 127.0.0.1 或 localhost。 */
    @Test
    public void testSourcesOfflineOnly() throws IOException {
        for (Path file : javaFiles(TEST_ROOT)) {
            String text = stripComments(read(file));
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("https?://([^\\s\"'()/]+)").matcher(text);
            while (matcher.find()) {
                String host = matcher.group(1).toLowerCase();
                assertTrue("测试禁止外网地址: " + normalize(file) + " -> " + matcher.group(),
                        "127.0.0.1".equals(host) || "localhost".equals(host));
            }
        }
    }

    /** ⑮ qr 公共面精确 4 且集合相等。 */
    @Test
    public void qrPublicSurfaceExact() throws IOException {
        assertPublicSurface(QR_PACKAGE, QR_PUBLIC, 4, "qr");
    }

    /** ⑯ ZXing import 只允许在 qr/internal。 */
    @Test
    public void zxingOnlyInQrInternal() throws IOException {
        for (Path file : javaFiles(SOURCE_ROOT)) {
            for (String line : imports(stripComments(read(file)))) {
                if (line.startsWith("com.google.zxing.")) {
                    assertTrue("ZXing 依赖泄漏出 qr/internal: " + normalize(file) + " -> " + line,
                            normalize(file).startsWith(QR_INTERNAL));
                }
            }
        }
    }

    /** ⑰ mask 公共面精确 3 且集合相等。 */
    @Test
    public void maskPublicSurfaceExact() throws IOException {
        assertPublicSurface(MASK_PACKAGE, MASK_PUBLIC, 3, "mask");
    }

    /** ⑱ kdf 公共面精确 4 且集合相等。 */
    @Test
    public void kdfPublicSurfaceExact() throws IOException {
        assertPublicSurface(KDF_PACKAGE, KDF_PUBLIC, 4, "kdf");
    }

    /**
     * ⑲ javax.crypto. / java.security. import 仅允许 kdf/internal、captcha/internal、
     * crypto/internal、random/internal（captcha 既有的 SecureRandom 用于 token 随机数，
     * 加入 crypto/internal 与 random/internal）。
     */
    @Test
    public void cryptoOnlyInKdfInternal() throws IOException {
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            for (String line : imports(stripComments(read(file)))) {
                if (line.startsWith("javax.crypto.")) {
                    assertTrue("javax.crypto 泄漏出 kdf/internal: " + path + " -> " + line,
                            path.startsWith(KDF_INTERNAL) || path.startsWith(CAPTCHA_INTERNAL)
                                    || path.startsWith(CRYPTO_INTERNAL) || path.startsWith(RANDOM_INTERNAL));
                }
                if (line.startsWith("java.security.")) {
                    assertTrue("java.security 泄漏出 kdf/internal 或 captcha/internal: " + path + " -> " + line,
                            path.startsWith(KDF_INTERNAL) || path.startsWith(CAPTCHA_INTERNAL)
                                    || path.startsWith(CRYPTO_INTERNAL) || path.startsWith(RANDOM_INTERNAL));
                }
            }
        }
    }

    /** ⑳ kdf: 消息前缀仅允许 KdfErrors.java（扩充门禁⑪，口径同 ip/captcha/qr/mask）。 */
    @Test
    public void kdfPrefixOnlyInErrors() throws IOException {
        String prefix = "kdf: ";
        String errorsFile = KDF_INTERNAL + "KdfErrors.java";
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            if (path.equals(errorsFile)) {
                continue;
            }
            assertFalse("kdf: 前缀只允许在 KdfErrors: " + path,
                    stripComments(read(file)).contains(prefix));
        }
        assertTrue("KdfErrors 必须承载 kdf: 前缀",
                stripComments(read(SOURCE_ROOT.resolve(errorsFile))).contains(prefix));
    }

    /** ⑳′ kdf 公共层零 JDK 加密类型、零第三方字面量（扩充门禁⑧扫描覆盖 kdf 公共层）。 */
    @Test
    public void publicSignaturesNoThirdPartyKdf() throws IOException {
        for (String name : KDF_PUBLIC) {
            Path file = SOURCE_ROOT.resolve(KDF_PACKAGE).resolve(name);
            String text = stripComments(read(file));
            for (String forbidden : Arrays.asList(
                    "javax.crypto.", "java.security.",
                    "org.lionsoul.", "com.google.zxing.",
                    "cn.aifei.", "cn.hg.aifei.cache.")) {
                assertFalse("kdf 公共类型不得含 JDK 加密/第三方/框架字面量: " + name + " -> " + forbidden,
                        text.contains(forbidden));
            }
        }
    }

    /** ㉑ crypto 公共面精确 2 且集合相等。 */
    @Test
    public void cryptoPublicSurfaceExact() throws IOException {
        assertPublicSurface(CRYPTO_PACKAGE, CRYPTO_PUBLIC, 2, "crypto");
    }

    /** ㉒ random 公共面精确 2 且集合相等。 */
    @Test
    public void randomPublicSurfaceExact() throws IOException {
        assertPublicSurface(RANDOM_PACKAGE, RANDOM_PUBLIC, 2, "random");
    }

    /** ㉓ xml 公共面精确 2 且集合相等。 */
    @Test
    public void xmlPublicSurfaceExact() throws IOException {
        assertPublicSurface(XML_PACKAGE, XML_PUBLIC, 2, "xml");
    }

    /** ㉔ codec 公共面精确 2 且集合相等。 */
    @Test
    public void codecPublicSurfaceExact() throws IOException {
        assertPublicSurface(CODEC_PACKAGE, CODEC_PUBLIC, 2, "codec");
    }

    /** ㉕ crypto: 消息前缀仅允许 CryptoErrors.java（扩充门禁⑪，口径同⑳）。 */
    @Test
    public void cryptoPrefixOnlyInErrors() throws IOException {
        String prefix = "crypto: ";
        String errorsFile = CRYPTO_INTERNAL + "CryptoErrors.java";
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            if (path.equals(errorsFile)) {
                continue;
            }
            assertFalse("crypto: 前缀只允许在 CryptoErrors: " + path,
                    stripComments(read(file)).contains(prefix));
        }
        assertTrue("CryptoErrors 必须承载 crypto: 前缀",
                stripComments(read(SOURCE_ROOT.resolve(errorsFile))).contains(prefix));
    }

    /** ㉖ random: 消息前缀仅允许 RandomErrors.java。 */
    @Test
    public void randomPrefixOnlyInErrors() throws IOException {
        String prefix = "random: ";
        String errorsFile = RANDOM_INTERNAL + "RandomErrors.java";
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            if (path.equals(errorsFile)) {
                continue;
            }
            assertFalse("random: 前缀只允许在 RandomErrors: " + path,
                    stripComments(read(file)).contains(prefix));
        }
        assertTrue("RandomErrors 必须承载 random: 前缀",
                stripComments(read(SOURCE_ROOT.resolve(errorsFile))).contains(prefix));
    }

    /** ㉗ xml: 消息前缀仅允许 XmlErrors.java。 */
    @Test
    public void xmlPrefixOnlyInErrors() throws IOException {
        String prefix = "xml: ";
        String errorsFile = XML_INTERNAL + "XmlErrors.java";
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            if (path.equals(errorsFile)) {
                continue;
            }
            assertFalse("xml: 前缀只允许在 XmlErrors: " + path,
                    stripComments(read(file)).contains(prefix));
        }
        assertTrue("XmlErrors 必须承载 xml: 前缀",
                stripComments(read(SOURCE_ROOT.resolve(errorsFile))).contains(prefix));
    }

    /** ㉘ codec: 消息前缀仅允许 CodecErrors.java。 */
    @Test
    public void codecPrefixOnlyInErrors() throws IOException {
        String prefix = "codec: ";
        String errorsFile = CODEC_INTERNAL + "CodecErrors.java";
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            if (path.equals(errorsFile)) {
                continue;
            }
            assertFalse("codec: 前缀只允许在 CodecErrors: " + path,
                    stripComments(read(file)).contains(prefix));
        }
        assertTrue("CodecErrors 必须承载 codec: 前缀",
                stripComments(read(SOURCE_ROOT.resolve(errorsFile))).contains(prefix));
    }

    /** ㉙ crypto 公共层零 JDK 加密 / 第三方 / 框架字面量与来源协议串。 */
    @Test
    public void publicSignaturesNoThirdPartyCrypto() throws IOException {
        for (String name : CRYPTO_PUBLIC) {
            Path file = SOURCE_ROOT.resolve(CRYPTO_PACKAGE).resolve(name);
            String text = stripComments(read(file));
            for (String forbidden : Arrays.asList(
                    "javax.crypto.", "java.security.",
                    "org.lionsoul.", "com.google.zxing.",
                    "cn.aifei.", "cn.hg.aifei.cache.",
                    "258EAFA5-E914-47DA-95CA-C5AB0DC85B11", "sortedSign",
                    "canonical", "\"signature\"")) {
                assertFalse("crypto 公共类型不得含 JDK 加密/第三方/协议字面量: " + name + " -> " + forbidden,
                        text.contains(forbidden));
            }
        }
    }

    /** ㉚ random 公共层零 JDK 加密 / 第三方 / 框架字面量与来源类型名。 */
    @Test
    public void publicSignaturesNoThirdPartyRandom() throws IOException {
        for (String name : RANDOM_PUBLIC) {
            Path file = SOURCE_ROOT.resolve(RANDOM_PACKAGE).resolve(name);
            String text = stripComments(read(file));
            for (String forbidden : Arrays.asList(
                    "javax.crypto.", "java.security.",
                    "org.lionsoul.", "com.google.zxing.",
                    "cn.aifei.", "cn.hg.aifei.cache.",
                    "Captcha", "TokenGenerator", "ImageGenerator")) {
                assertFalse("random 公共类型不得含 JDK 加密/第三方/来源类型字面量: " + name + " -> " + forbidden,
                        text.contains(forbidden));
            }
        }
    }

    /** ㉛ xml 公共层零 JDK 加密 / 第三方 / 框架字面量与来源协议串。 */
    @Test
    public void publicSignaturesNoThirdPartyXml() throws IOException {
        for (String name : XML_PUBLIC) {
            Path file = SOURCE_ROOT.resolve(XML_PACKAGE).resolve(name);
            String text = stripComments(read(file));
            for (String forbidden : Arrays.asList(
                    "javax.crypto.", "java.security.",
                    "org.lionsoul.", "com.google.zxing.",
                    "cn.aifei.", "cn.hg.aifei.cache.",
                    "\"<xml>\"", "ToUserName", "FromUserName")) {
                assertFalse("xml 公共类型不得含 JDK 加密/第三方/协议字面量: " + name + " -> " + forbidden,
                        text.contains(forbidden));
            }
        }
    }

    /** ㉜ codec 公共层零 JDK 加密 / 第三方 / 框架字面量与来源配置键、传输头。 */
    @Test
    public void publicSignaturesNoThirdPartyCodec() throws IOException {
        for (String name : CODEC_PUBLIC) {
            Path file = SOURCE_ROOT.resolve(CODEC_PACKAGE).resolve(name);
            String text = stripComments(read(file));
            for (String forbidden : Arrays.asList(
                    "javax.crypto.", "java.security.",
                    "org.lionsoul.", "com.google.zxing.",
                    "cn.aifei.", "cn.hg.aifei.cache.",
                    "http.gzip", "Accept-Encoding", "HttpConfig")) {
                assertFalse("codec 公共类型不得含 JDK 加密/第三方/配置键字面量: " + name + " -> " + forbidden,
                        text.contains(forbidden));
            }
        }
    }

    // ---------------------------------------------------------------- 工具方法

    private static void assertPublicSurface(String packagePath, Set<String> expected,
                                            int count, String label) throws IOException {
        List<String> names = directChildren(packagePath);
        assertEquals(label + " 公共类型数量必须为 " + count, count, names.size());
        assertEquals(label + " 公共类型文件名集合不符", expected, new LinkedHashSet<String>(names));
    }

    private static List<String> directChildren(String packagePath) throws IOException {
        List<String> names = new ArrayList<String>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            if (isDirectChild(normalize(file), packagePath)) {
                names.add(file.getFileName().toString());
            }
        }
        return names;
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static String normalize(Path file) {
        String path = file.toString().replace('\\', '/');
        String prefixDir = SOURCE_ROOT.toString().replace('\\', '/') + "/";
        String testPrefix = TEST_ROOT.toString().replace('\\', '/') + "/";
        if (path.startsWith(prefixDir)) {
            return path.substring(prefixDir.length());
        }
        if (path.startsWith(testPrefix)) {
            return path.substring(testPrefix.length());
        }
        return path;
    }

    private static List<Path> javaFiles(Path root) throws IOException {
        if (!Files.exists(root)) {
            return new ArrayList<Path>();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }
    }

    private static boolean isDirectChild(String path, String packagePath) {
        if (!path.startsWith(packagePath)) {
            return false;
        }
        String rest = path.substring(packagePath.length());
        return !rest.contains("/");
    }

    private static List<String> imports(String javaSource) {
        List<String> result = new ArrayList<String>();
        for (String line : javaSource.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("import ")) {
                result.add(trimmed.substring("import ".length()).trim());
            }
        }
        return result;
    }

    private static void assertNoImportInSources(Path root, String forbidden, String message)
            throws IOException {
        for (Path file : javaFiles(root)) {
            for (String line : imports(stripComments(read(file)))) {
                assertFalse(message + ": " + normalize(file) + " -> " + line,
                        line.startsWith(forbidden));
            }
        }
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
                while (i < n && !(source.charAt(i) == '*' && i + 1 < n && source.charAt(i + 1) == '/')) {
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
