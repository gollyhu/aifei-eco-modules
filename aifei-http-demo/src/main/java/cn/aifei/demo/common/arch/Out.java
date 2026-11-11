/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.demo.common.arch;

import cn.aifei.core.Output;
import cn.aifei.db.transaction.RollbackDecision;
import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Out
 */
public class Out implements Output, RollbackDecision {

    int code;
    String msg;
    Object data;
    boolean modelAsRow;

    String view;    // enjoy 引擎

    public Out() {}

    Out(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    // 有状态静态构造方法 ------------------------------------------------------------------------

    /**
     * 创建 out，状态置为成功
     */
    public static Out ok() {
        return new Out(OutCode.OK.code(), OutCode.OK.msg());
    }

    /**
     * 创建 out，状态置为成功并传入 msg
     */
    public static Out ok(String msg) {
        return new Out(OutCode.OK.code(), msg);
    }

    /**
     * 创建 out，状态置为成功并使用 MessageFormat 格式化 msg
     *
     * 例：Out.ok("转账成功，转账后账户 {0} 余额为 {1}", accountId, balance)
     */
    public static Out ok(String format, Object... args) {
        return new Out(OutCode.OK.code(), MessageFormat.format(format, args));
    }

    /**
     * 创建 out，状态置为失败并传入 msg。不提供无参 fail() 方法，明确失败原因
     */
    public static Out fail(String msg) {
        return new Out(OutCode.FAIL.code(), msg);
    }

    /**
     * 创建 out，状态置为失败并使用 MessageFormat 格式化 msg
     *
     * 例：Out.fail("转账失败，账户 {0} 余额不足 {1}", accountId, balance)
     */
    public static Out fail(String format, Object... args) {
        return new Out(OutCode.FAIL.code(), MessageFormat.format(format, args));
    }

    /**
     * 创建 Out，传入 Code 接口默认实现枚举类，例如：Out.fail(OutCode.UNAUTHORIZED)
     * 也可传入Code 接口自定义实现枚举类，例如：Out.fail(AppCode.ORDER_NOT_FOUND)
     */
    public static Out fail(Code code) {
        Objects.requireNonNull(code, "code can not be null.");
        return new Out(code.code(), code.msg());
    }

    // 无状态静态构造方法 ------------------------------------------------------------------------

    /**
     * 创建 out 并传入 data 数据
     *
     * <pre>
     *  例：
     *    public Out list() {
     *        // code 默认值为 0，所以 json 响应状态为 OK，符合多数场景
     *        return Out.of(User.findAll());
     *    }
     * </pre>
     */
    public static Out of(Object data) {
        return new Out().data(data);
    }

    /**
     * 创建 out 并传入 field、value 数据
     *
     * <pre>
     *  例：
     *    public Out list() {
     *        return Out.of("list", User.findAll(...)).view("list.html");
     *    }
     * </pre>
     */
    public static Out of(String field, Object value) {
        return new Out().set(field, value);
    }

    /**
     * 创建 out
     */
    public static Out of() {
        return new Out();
    }

    // 链式 setters -----------------------------------------------------------------------

    /**
     * 设置为成功状态
     */
    public Out setOk() {
        this.code = OutCode.OK.code();
        if (this.msg == null) {
            this.msg = OutCode.OK.msg();
        }
        return this;
    }

    /**
     * 设置为失败状态
     */
    public Out setFail() {
        this.code = OutCode.FAIL.code();
        if (this.msg == null) {
            this.msg = OutCode.FAIL.msg();
        }
        return this;
    }

    /**
     * 设置为失败状态，例如：setFail(OutCode.FORBIDDEN)
     */
    public Out setFail(Code code) {
        Objects.requireNonNull(code, "code can not be null.");
        this.code = code.code();
        this.msg = code.msg();
        return this;
    }

    /**
     * 设置 msg
     */
    public Out msg(String msg) {
        this.msg = msg;
        return this;
    }

    /**
     * 设置通过 MessageFormat 格式化得到的 msg
     */
    public Out msg(String format, Object... args) {
        this.msg = MessageFormat.format(format, args);
        return this;
    }

    /**
     * 设置 data
     */
    public Out data(Object data) {
        this.data = data;
        return this;
    }

    /**
     * 设置 Model 当成 Row 去转换 json，而非通过 getter 方法取值转换，用于 sql 关联查询返回 Model 对象，
     * 并且需要将关联表字段转换为 json 的场景。默认值为 false，也即会调用 Model 的 getter 方法取值去转换。
     */
    public Out modelAsRow() {
        this.modelAsRow = true;
        return this;
    }

    /**
     * 与 modelAsRow 相反，调用 getter 方法取值转换 json
     */
    public Out modelAsBean() {
        this.modelAsRow = false;
        return this;
    }

    /**
     * 将 key、value 存入 data
     */
    @SuppressWarnings("unchecked")
    public Out set(String field, Object value) {
        if (data instanceof Map) {
            ((Map<String, Object>) data).put(field, value);
        } else if (data == null) {
            data = new LinkedHashMap<String, Object>();
            ((LinkedHashMap<String, Object>) data).put(field, value);
        } else {
            throw new IllegalStateException("Out 状态异常，data 必须为 Map 类型或 null");
        }
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> T get(String field) {
        return data instanceof Map ? (T) ((Map) data).get(field) : null;
    }

    public <T> T get(String field, T defaultValue) {
        T value = get(field);
        return value != null ? value : defaultValue;
    }

    /**
     * 设置 enjoy 模板
     */
    public Out view(String view) {
        this.view = view;
        return this;
    }

    /**
     * 清除 data、code、msg 数据
     */
    public Out clear() {
        this.code = 0;
        this.msg = null;
        if (data instanceof Map) {
            ((Map<?, ?>)data).clear();
        } else {
            data = null;
        }

        this.view = null;
        return this;
    }

    // ----------------------------------------------------------------------------------

    /**
     * 控制是否回滚事务。
     *
     * <pre>
     *   例子：
     *      // aifei-db 事务方法返回 Out 对象控制事务回滚
     *      Db.transaction(tx -> {
     *         // ... 业务代码省略
     *         if (xxx) {
     *             return Out.fail("余额不足");
     *         }
     *         return Out.of(data);
     *      });
     *
     *      以上代码若返回 Out.fail(...)，则其 shouldRollback() 返回 true，从而事务将被回滚。
     * </pre>
     */
    @Override
    public boolean shouldRollback() {
        return code != OutCode.OK.code();
    }

    // 供 json 转换用 getter、setter 方法 ------------------------------------------------------

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}


