/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.demo.mq;

import cn.aifei.demo.common.arch.Out;
import cn.aifei.demo.common.arch.OutCode;
import cn.hg.aifei.mq.Consumer;
import cn.hg.aifei.mq.ConsumerUtil;
import cn.hg.aifei.mq.Producer;
import cn.hg.aifei.mq.ProducerUtil;
import cn.hg.aifei.mq.internal.MqConfig;
import cn.hg.aifei.mq.internal.MqProviderRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * 实例清单：demo 侧唯一的实例枚举点，也是 {@code instance} 参数<b>唯一</b>的校验点。
 *
 * <p>实例清单 = 主实例 {@code __MAIN__}（恒在首位，配置走根级 {@code mq.*}）+ {@code mq.names} 声明的命名实例
 * （配置走 {@code mq.<name>.*}，按声明顺序）。清单来源是 {@code MqPlugin} 的私有逻辑，这里按同一套规则重解析
 * 配置：{@link MqConfig} 与 {@link MqProviderRegistry} 属<b>同厂 internal 依赖</b>，只允许出现在本类，不扩散为
 * 公共契约。</p>
 *
 * <p>错误码与文案也只在本类定义：参数非法（空白 / 含 {@code .}、{@code =}、{@code :}、空白字符 / 保留名 /
 * 未在清单中）为 {@code 10001}，应用未装配为 {@code 90000}。调用方（{@code MqDemoService}）只写
 * {@code if (!it.ok()) return it.toOut();}，不得自写校验。</p>
 */
public final class MqInstanceCatalog {

    /**
     * 主实例名；编译期常量，可直接用作 {@code @Para(defaultValue = ...)}。
     */
    public static final String MAIN = MqConfig.MAIN_INSTANCE_NAME;

    /**
     * 全部配置项；由 {@link #init(Properties)} 在工作在装配早期写入，之后只读。
     */
    private static volatile Properties properties;

    /**
     * {@code mq.names} 解析失败的原因；正常启动不可达（{@code MqPlugin.start()} 会先失败）。
     */
    private static volatile String configError;

    /**
     * 后端类型白名单来源，只建一次。
     */
    private static final MqProviderRegistry REGISTRY = new MqProviderRegistry();

    /**
     * 禁止创建工具类实例。
     */
    private MqInstanceCatalog() {
    }

    /**
     * 注入配置项；由 {@code DemoAppConfig.config(Settings)} 在注解装配阶段调用，重复调用即覆盖。
     *
     * @param props 全部配置项
     */
    public static void init(Properties props) {
        properties = props;
        configError = null;
    }

    /**
     * 返回实例名清单：主实例在首，其后按 {@code mq.names} 声明顺序。
     *
     * <p>本方法不抛异常：{@code mq.names} 非法时只返回主实例，并把原因记入 {@link #configError()}。</p>
     *
     * @return 实例名副本；至少含主实例
     */
    public static List<String> names() {
        List<String> names = new ArrayList<String>();
        names.add(MAIN);
        Properties props = properties;
        if (props == null) {
            configError = null;
            return names;
        }
        try {
            names.addAll(MqConfig.parseInstanceNames(props, MqConfig.NAMES_KEY));
            configError = null;
        } catch (RuntimeException e) {
            configError = describe(e);
        }
        return names;
    }

    /**
     * 返回实例明细清单；逐项判定类型与就绪状态，某实例未装配只体现为 {@code ready=false}。
     *
     * @return 实例明细副本；不为 {@code null}，不为空（至少含主实例）
     */
    public static List<Instance> list() {
        List<String> names = names();
        List<Instance> instances = new ArrayList<Instance>(names.size());
        for (String name : names) {
            instances.add(describe(name));
        }
        return instances;
    }

    /**
     * 校验并解析 {@code instance} 参数——本 demo 唯一的校验点。
     *
     * <p>{@code null} 视为省略参数，落到主实例；其余取值依次校验名称合法性、是否在清单中、是否已装配。</p>
     *
     * @param raw 请求传入的原始参数值；{@code null} 表示省略
     * @return 解析结果；失败时 {@link Instance#ok()} 为 {@code false}，由调用方直接回 {@link Instance#toOut()}
     */
    public static Instance resolve(String raw) {
        String name = raw == null ? MAIN : raw;
        try {
            MqConfig.requireInstanceName(name);
        } catch (IllegalArgumentException e) {
            return invalid(raw, name);
        }
        if (!names().contains(name)) {
            return invalid(raw, name);
        }
        Instance instance = describe(name);
        if (!instance.ready) {
            instance.errorCode = OutCode.FAIL.code();
            instance.errorMsg = "MQ 尚未装配完成，请确认 config(Plugins) 已注册 MqPlugin";
            return instance;
        }
        return instance;
    }

    /**
     * 返回最近一次 {@code mq.names} 解析失败的原因。
     *
     * @return 形如 {@code IllegalArgumentException: xxx} 的描述；无错误时返回 {@code null}
     */
    public static String configError() {
        return configError;
    }

    /**
     * 清空缓存的配置引用，仅供测试与重启演练。
     */
    public static void reset() {
        properties = null;
        configError = null;
    }

    /**
     * 组装单个实例的明细；本方法不抛异常。
     *
     * @param name 实例名
     * @return 实例明细
     */
    private static Instance describe(String name) {
        Instance instance = new Instance(name);
        instance.type = typeOf(name);
        try {
            instance.producer = ProducerUtil.use(name);
            instance.consumer = ConsumerUtil.use(name);
        } catch (RuntimeException e) {
            instance.producer = null;
            instance.consumer = null;
        }
        instance.ready = instance.producer != null && instance.consumer != null;
        return instance;
    }

    /**
     * 组装一个参数校验失败的实例明细。
     *
     * @param raw 请求原始参数值
     * @param name 实际参与校验的实例名
     * @return 携带 {@code 10001} 的实例明细
     */
    private static Instance invalid(String raw, String name) {
        Instance instance = new Instance(raw == null ? MAIN : raw);
        instance.errorCode = OutCode.INVALID_PARAM.code();
        instance.errorMsg = "未知实例: " + name + "，可用实例: " + names();
        return instance;
    }

    /**
     * 读取某个实例配置的后端类型；未配置或解析失败时退回默认类型。
     *
     * @param name 实例名
     * @return 后端类型；不为 {@code null}
     */
    private static String typeOf(String name) {
        Properties props = properties;
        if (props == null) {
            return MqConfig.DEFAULT_TYPE;
        }
        try {
            return MqConfig.parse(props, REGISTRY.types(), name).type();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 由实例名派生配置键前缀：主实例为 {@code mq}，命名实例为 {@code mq.<name>}。
     *
     * <p>这是 {@code MqConfig#prefixOf} 的镜像（库侧为 private）：契约以 {@link MqConfig} 为准，规则一旦变化
     * 只需改这一处。</p>
     *
     * @param name 实例名
     * @return 配置键前缀
     */
    private static String prefixOf(String name) {
        return MAIN.equals(name) ? MqConfig.ROOT_PREFIX : MqConfig.ROOT_PREFIX + "." + name;
    }

    /**
     * 把运行期异常整理成一行可读描述。
     *
     * @param e 捕获到的异常
     * @return 形如 {@code IllegalArgumentException: xxx} 的描述
     */
    private static String describe(RuntimeException e) {
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    /**
     * 单个实例的明细：实例名、配置前缀、后端类型、就绪状态与已装配的生产者 / 消费者。
     *
     * <p>失败时携带错误码与文案，由 {@link #toOut()} 直接转成端点响应，避免每个端点各写一套校验与文案。</p>
     */
    public static final class Instance {

        private final String instanceName;

        private String type;

        private final String configPrefix;

        private final boolean main;

        private boolean ready;

        private Producer producer;

        private Consumer consumer;

        private int errorCode;

        private String errorMsg;

        private Instance(String instanceName) {
            this.instanceName = instanceName;
            this.configPrefix = prefixOf(instanceName);
            this.main = MAIN.equals(instanceName);
        }

        /**
         * 返回实例名。
         *
         * @return 实例名
         */
        public String instanceName() {
            return instanceName;
        }

        /**
         * 返回该实例配置的后端类型。
         *
         * @return 后端类型；配置异常时为 {@code null}
         */
        public String type() {
            return type;
        }

        /**
         * 返回该实例的配置键前缀。
         *
         * @return {@code mq}（主实例）或 {@code mq.<name>}
         */
        public String configPrefix() {
            return configPrefix;
        }

        /**
         * 是否主实例。
         *
         * @return 主实例返回 {@code true}
         */
        public boolean main() {
            return main;
        }

        /**
         * 是否已装配（生产者与消费者都已就绪）。
         *
         * @return 已装配返回 {@code true}
         */
        public boolean ready() {
            return ready;
        }

        /**
         * 返回该实例的生产者。
         *
         * @return 生产者；未装配时为 {@code null}
         */
        public Producer producer() {
            return producer;
        }

        /**
         * 返回该实例的消费者。
         *
         * @return 消费者；未装配时为 {@code null}
         */
        public Consumer consumer() {
            return consumer;
        }

        /**
         * 返回错误码。
         *
         * @return {@code 0} 表示可用；{@code 10001} 参数非法；{@code 90000} 未装配
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * 返回错误文案。
         *
         * @return 错误文案；可用时为 {@code null}
         */
        public String errorMsg() {
            return errorMsg;
        }

        /**
         * 是否可用。
         *
         * @return 校验通过且已装配返回 {@code true}
         */
        public boolean ok() {
            return errorCode == OutCode.OK.code();
        }

        /**
         * 把失败原因转成端点响应；仅在本实例不可用时调用。
         *
         * @return 携带错误码与文案的响应
         */
        public Out toOut() {
            if (errorCode == OutCode.INVALID_PARAM.code()) {
                return Out.fail(OutCode.INVALID_PARAM).msg(errorMsg);
            }
            return Out.fail(errorMsg);
        }
    }
}
