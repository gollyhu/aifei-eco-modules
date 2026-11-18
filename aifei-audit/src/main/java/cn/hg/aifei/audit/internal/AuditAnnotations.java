/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.internal;

import cn.hg.aifei.audit.Audit;
import java.lang.reflect.Method;

/**
 * 注解属性读取唯一实现点（非承诺 API）：调用期反射读，不建运行期索引（无共享可变状态、无清理面）。
 */
public final class AuditAnnotations {

    private AuditAnnotations() {
    }

    /**
     * @param method 被拦截的方法
     * @return 注解规格；方法未标注 {@link Audit} 时为 {@code null}
     */
    public static ActionSpec of(Method method) {
        if (method == null) {
            return null;
        }
        Audit annotation = method.getAnnotation(Audit.class);
        if (annotation == null) {
            return null;
        }
        return new ActionSpec(annotation.action(), annotation.desc(), annotation.args(), annotation.mask());
    }

    /**
     * 注解属性的不可变快照。
     */
    public static final class ActionSpec {

        private final String action;

        private final String desc;

        private final boolean args;

        private final int[] mask;

        /**
         * @param action 动作码
         * @param desc   描述
         * @param args   是否记录入参
         * @param mask   剔除的参数位
         */
        public ActionSpec(String action, String desc, boolean args, int[] mask) {
            this.action = action;
            this.desc = desc;
            this.args = args;
            this.mask = mask == null ? new int[0] : mask.clone();
        }

        public String getAction() {
            return action;
        }

        public String getDesc() {
            return desc;
        }

        public boolean isArgs() {
            return args;
        }

        public int[] getMask() {
            return mask.clone();
        }
    }
}
