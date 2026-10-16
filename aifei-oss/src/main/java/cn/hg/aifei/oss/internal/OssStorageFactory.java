/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.oss.internal;

import cn.hg.aifei.oss.OssClient;
import cn.hg.aifei.oss.OssException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.dromara.x.file.storage.core.FileStorageProperties;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.FileStorageServiceBuilder;

/**
 * 文件存储服务的构建与平台装配的<b>唯一定义点</b>，并固化「平台类型 → 平台族字段 / 配置类」映射表。
 *
 * <p>映射表是 {@code oss.<name>.type} 合法取值全集的唯一来源（20 项，由平台配置字段名 camel→kebab 机械派生）。
 * 装配时：按类型取配置类并实例化、把平台配置项（kebab-case）映射为配置字段（camelCase）、按平台族归组写入
 * {@link FileStorageProperties}；显式设置默认平台，<b>不</b>静默沿用底层默认值；最后固定以
 * {@code FileStorageServiceBuilder.create(properties).useDefault().build()} 构建——{@code useDefault()} 不可省，
 * 否则会缺失全部文件包装适配器，导致「字节数组 / 输入流」上传路径整体不可用。</p>
 *
 * <p>平台族只在对应列表非空时构建（由底层构建器保证），未配置的平台族不受影响。云厂商 SDK 缺失属运行期故障，
 * 不在本类启动期强制加载。</p>
 *
 * <p>本类属非承诺 API：因装配需要声明为 {@code public}，但不是公共 API，消费方不得引用。</p>
 */
public final class OssStorageFactory {

    /**
     * 平台类型到平台族的映射表；有序，便于异常消息与测试断言稳定。
     */
    private static final Map<String, Family> FAMILIES = createFamilies();

    /**
     * 全部受支持的平台类型（只读）。
     */
    private static final Set<String> SUPPORTED_TYPES =
            Collections.unmodifiableSet(new LinkedHashSet<>(FAMILIES.keySet()));

    /**
     * 禁止创建工具类实例。
     */
    private OssStorageFactory() {
    }

    /**
     * 返回全部受支持的平台类型。
     *
     * <p>即 {@code oss.<name>.type} 的合法取值全集，与 {@link FileStorageProperties} 的平台族字段一一对应。</p>
     *
     * @return 只读的有序类型集合
     */
    public static Set<String> supportedTypes() {
        return SUPPORTED_TYPES;
    }

    /**
     * 返回平台类型在 {@link FileStorageProperties} 中对应的平台族字段名。
     *
     * @param type 平台类型
     * @return 平台族字段名（camelCase）
     * @throws OssException 平台类型未知
     */
    public static String platformFieldOf(String type) {
        return familyOf(type).field;
    }

    /**
     * 返回平台类型对应的平台配置类。
     *
     * @param type 平台类型
     * @return 平台配置类
     * @throws OssException 平台类型未知
     */
    public static Class<?> configTypeOf(String type) {
        return familyOf(type).configType;
    }

    /**
     * 按配置构建已装配的文件存储服务。
     *
     * @param defaultPlatform 默认平台标识；必须显式给定，非空白
     * @param configs 全部平台配置
     * @return 已装配的文件存储服务
     * @throws OssException 默认平台空白、配置为空元素、平台类型未知、配置项无法映射，或构建失败
     */
    public static FileStorageService build(String defaultPlatform, List<OssConfig> configs) {
        if (configs == null) {
            throw new OssException("oss configs can not be null");
        }
        if (defaultPlatform == null || defaultPlatform.trim().isEmpty()) {
            throw new OssException("oss default platform can not be blank");
        }

        FileStorageProperties properties = new FileStorageProperties();
        // 必须显式设置：底层默认值是 local，绝不静默沿用
        properties.setDefaultPlatform(defaultPlatform);

        // 按平台族归组，同族的多个平台追加进同一列表
        Map<String, List<Object>> grouped = new LinkedHashMap<>();
        for (OssConfig config : configs) {
            if (config == null) {
                throw new OssException("oss config can not be null");
            }
            Family family = familyOf(config.type());
            Object platformConfig = newPlatformConfig(family, config);
            List<Object> familyList = grouped.get(family.field);
            if (familyList == null) {
                familyList = new ArrayList<>();
                grouped.put(family.field, familyList);
            }
            familyList.add(platformConfig);
        }
        for (Map.Entry<String, List<Object>> entry : grouped.entrySet()) {
            assignFamily(properties, entry.getKey(), entry.getValue());
        }

        try {
            return FileStorageServiceBuilder.create(properties).useDefault().build();
        } catch (RuntimeException | Error e) {
            throw OssErrors.translate("build storage service", e);
        }
    }

    /**
     * 按配置构建存储服务，并为每个平台装配一个客户端。
     *
     * <p>这是接入层唯一的装配入口：底层 {@link FileStorageService} 类型<b>不</b>外泄到接入层，接入层只
     * 拿到 {@link OssClient} 一族公共类型。每个客户端共享同一份已装配的服务，并各自获得平台标识与于该
     * 平台下定位对象所需的上下文（{@code base-path}）。</p>
     *
     * @param defaultPlatform 默认平台标识；必须显式给定，非空白
     * @param configs 全部平台配置
     * @return 平台标识到客户端的映射，保持 {@code configs} 的给定顺序
     * @throws OssException 平台配置为空元素、平台类型未知、配置项无法映射，或构建失败
     */
    public static Map<String, OssClient> buildClients(String defaultPlatform, List<OssConfig> configs) {
        if (configs == null) {
            throw new OssException("oss configs can not be null");
        }
        FileStorageService service = build(defaultPlatform, configs);
        Map<String, OssClient> clients = new LinkedHashMap<>();
        for (OssConfig config : configs) {
            if (config == null) {
                throw new OssException("oss config can not be null");
            }
            clients.put(config.platform(), new XfsOssClient(config.platform(), basePathOf(config), service));
        }
        return clients;
    }

    /**
     * 读取平台配置项 {@code base-path}（kebab-case），作为该平台下对象定位所需的上下文。
     *
     * <p>配置项键名到平台配置字段的映射口径由本类统一承担（见 {@link #applyOption}），故此处直接读取
     * kebab 键，不复制到其它类。</p>
     *
     * @param config 平台配置
     * @return {@code base-path} 取值；未配置时为 {@code null}
     */
    private static String basePathOf(OssConfig config) {
        return config.options().get("base-path");
    }

    /**
     * 按类型取平台族；未知类型即失败。
     *
     * @param type 平台类型
     * @return 平台族
     * @throws OssException 平台类型未知
     */
    private static Family familyOf(String type) {
        Family family = FAMILIES.get(type);
        if (family == null) {
            throw new OssException(
                    "unknown oss platform type: " + type + ", supported types: " + SUPPORTED_TYPES);
        }
        return family;
    }

    /**
     * 实例化平台配置类并回填平台标识与配置项。
     *
     * @param family 平台族
     * @param config 平台配置
     * @return 平台配置实例
     * @throws OssException 配置类实例化失败，或配置项无法映射
     */
    private static Object newPlatformConfig(Family family, OssConfig config) {
        Object bean;
        try {
            bean = family.configType.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new OssException("can not create platform config for type: " + family.type, e);
        }
        ((FileStorageProperties.BaseConfig) bean).setPlatform(config.platform());
        for (Map.Entry<String, String> option : config.options().entrySet()) {
            applyOption(bean, option.getKey(), option.getValue());
        }
        return bean;
    }

    /**
     * 把一个 kebab-case 配置项映射为配置对象的 camelCase 字段。
     *
     * @param bean 平台配置实例
     * @param key kebab-case 配置项键名
     * @param value 配置项取值
     * @throws OssException 键名无法映射、取值无法转换，或字段不可写
     */
    private static void applyOption(Object bean, String key, String value) {
        String fieldName = toCamelCase(key);
        Field field = findField(bean.getClass(), fieldName);
        if (field == null || Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
            throw new OssException("unknown oss platform config option: " + key);
        }
        Object converted;
        try {
            converted = convert(key, value, field.getType());
        } catch (RuntimeException e) {
            if (e instanceof OssException) {
                throw (OssException) e;
            }
            throw new OssException("invalid oss platform config option value: " + key, e);
        }
        try {
            field.setAccessible(true);
            field.set(bean, converted);
        } catch (IllegalAccessException e) {
            throw new OssException("can not set oss platform config option: " + key, e);
        }
    }

    /**
     * 把配置项文本转换为目标字段类型。
     *
     * @param key kebab-case 配置项键名（仅用于异常消息）
     * @param value 配置项取值
     * @param type 目标字段类型
     * @return 转换后的取值
     * @throws OssException 目标类型不受支持
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object convert(String key, String value, Class<?> type) {
        if (type == String.class) {
            return value;
        }
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.valueOf(value);
        }
        if (type == int.class || type == Integer.class) {
            return Integer.valueOf(value);
        }
        if (type == long.class || type == Long.class) {
            return Long.valueOf(value);
        }
        if (type == double.class || type == Double.class) {
            return Double.valueOf(value);
        }
        if (type == float.class || type == Float.class) {
            return Float.valueOf(value);
        }
        if (type == short.class || type == Short.class) {
            return Short.valueOf(value);
        }
        if (type == byte.class || type == Byte.class) {
            return Byte.valueOf(value);
        }
        if (type == char.class || type == Character.class) {
            return value.charAt(0);
        }
        if (type == Charset.class) {
            return Charset.forName(value);
        }
        if (type == Duration.class) {
            return Duration.parse(value);
        }
        if (type.isEnum()) {
            return Enum.valueOf((Class) type, value);
        }
        throw new OssException("unsupported oss platform config option: " + key);
    }

    /**
     * 把 kebab-case 键名转为 camelCase 字段名。
     *
     * @param key kebab-case 键名
     * @return camelCase 字段名
     */
    private static String toCamelCase(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(key.length());
        boolean upperNext = false;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '-') {
                upperNext = true;
                continue;
            }
            sb.append(upperNext ? Character.toUpperCase(c) : c);
            upperNext = false;
        }
        return sb.toString();
    }

    /**
     * 沿类层次查找字段；找不到返回 {@code null}。
     *
     * @param type 起始类
     * @param name 字段名
     * @return 字段，或 {@code null}
     */
    private static Field findField(Class<?> type, String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // 继续沿父类查找
            }
        }
        return null;
    }

    /**
     * 把某个平台族的配置列表写入 {@link FileStorageProperties} 的对应字段。
     *
     * @param properties 配置对象
     * @param fieldName 平台族字段名
     * @param values 该族的平台配置列表
     * @throws OssException 字段不存在或不可写
     */
    private static void assignFamily(FileStorageProperties properties, String fieldName, List<Object> values) {
        try {
            Field field = FileStorageProperties.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(properties, values);
        } catch (ReflectiveOperationException e) {
            throw new OssException("can not assign oss platform family: " + fieldName, e);
        }
    }

    /**
     * 固化「平台类型 → 平台族字段 / 配置类」映射表。
     *
     * <p>类型名由 {@link FileStorageProperties} 平台族字段名 camel→kebab 机械派生，与 2.3.0 版实测一致；
     * 注意火山引擎的正确类型键是 {@code volcengine-tos}。</p>
     *
     * @return 有序映射表
     */
    @SuppressWarnings("deprecation")
    private static Map<String, Family> createFamilies() {
        Map<String, Family> map = new LinkedHashMap<>();
        for (Family family : Arrays.asList(
                new Family("local", "local", FileStorageProperties.LocalConfig.class),
                new Family("local-plus", "localPlus", FileStorageProperties.LocalPlusConfig.class),
                new Family("huawei-obs", "huaweiObs", FileStorageProperties.HuaweiObsConfig.class),
                new Family("aliyun-oss", "aliyunOss", FileStorageProperties.AliyunOssConfig.class),
                new Family("qiniu-kodo", "qiniuKodo", FileStorageProperties.QiniuKodoConfig.class),
                new Family("tencent-cos", "tencentCos", FileStorageProperties.TencentCosConfig.class),
                new Family("baidu-bos", "baiduBos", FileStorageProperties.BaiduBosConfig.class),
                new Family("upyun-uss", "upyunUss", FileStorageProperties.UpyunUssConfig.class),
                new Family("minio", "minio", FileStorageProperties.MinioConfig.class),
                new Family("amazon-s3", "amazonS3", FileStorageProperties.AmazonS3Config.class),
                new Family("amazon-s3-v2", "amazonS3V2", FileStorageProperties.AmazonS3V2Config.class),
                new Family("ftp", "ftp", FileStorageProperties.FtpConfig.class),
                new Family("sftp", "sftp", FileStorageProperties.SftpConfig.class),
                new Family("webdav", "webdav", FileStorageProperties.WebDavConfig.class),
                new Family("google-cloud-storage", "googleCloudStorage",
                        FileStorageProperties.GoogleCloudStorageConfig.class),
                new Family("fastdfs", "fastdfs", FileStorageProperties.FastDfsConfig.class),
                new Family("azure-blob", "azureBlob", FileStorageProperties.AzureBlobStorageConfig.class),
                new Family("mongo-gridfs", "mongoGridFs", FileStorageProperties.MongoGridFsConfig.class),
                new Family("go-fastdfs", "goFastdfs", FileStorageProperties.GoFastDfsConfig.class),
                new Family("volcengine-tos", "volcengineTos", FileStorageProperties.VolcengineTosConfig.class)
        )) {
            map.put(family.type, family);
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * 一个存储平台族的映射信息：类型名、{@link FileStorageProperties} 字段名与平台配置类。
     */
    private static final class Family {

        /**
         * 平台类型（kebab-case），即 {@code oss.<name>.type} 的合法取值。
         */
        private final String type;

        /**
         * {@link FileStorageProperties} 中对应的平台族字段名（camelCase）。
         */
        private final String field;

        /**
         * 平台配置类。
         */
        private final Class<?> configType;

        private Family(String type, String field, Class<?> configType) {
            this.type = type;
            this.field = field;
            this.configType = configType;
        }
    }
}
