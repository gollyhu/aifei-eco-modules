/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.demo.common.arch;

import cn.aifei.core.Input;
import cn.aifei.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.undertow.server.HttpServerExchange;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * In
 */
public class In implements Input {

    static final InParser IN_PARSER = new InParser();
    static final String[] EMPTY_PATH_PARA = new String[0];

    final HttpServerExchange exchange;

    JSONObject data;
    JSONArray arrayData;

    String[] pathPara;
    String pathParaStr;

    public In(HttpServerExchange exchange) {
        this.exchange = exchange;
    }

    public void pathPara(String pathParaStr) {
        this.pathParaStr = pathParaStr;
    }

    public HttpServerExchange getExchange() {
        return exchange;
    }

    JSONObject getData() {
        if (data == null) {          // 延迟解析数据
            data = IN_PARSER.parse(exchange, this);
        }
        return data;
    }

    JSONArray getArrayData() {
        if (data == null) {
            data = IN_PARSER.parse(exchange, this);
        }
        return arrayData;
    }

    /**
     * 判断参数是否存在。多 action 共享同一 actionPath (方法重载)时需使用该方法进一步匹配参数
     */
    @Override
    public boolean has(String name) {
        return getData().containsKey(name);
    }

    /**
     * 判断参数是否存在。多 action 共享同一 actionPath (方法重载)时需使用该方法进一步匹配参数
     */
    @Override
    public boolean has(int index) {
        return getStr(index) != null || pathPara.length > index;
    }

    /**
     * 获取 Bean。若参数 name 为 ""，则将 json 整体而非内部字段转换为 bean
     */
    @Override
    public <T> T getBean(String name, Class<T> type) {
        // 支持参数名为 "" 以及 @Para(name = "") 用法
        if ("".equals(name)) {
            return getData().to(type);
        } else {
            return getData().getObject(name, type);
        }
    }

    /**
     * 获取 List
     *
     * <pre>
     * getList 设计：
     *  1: 若参数 name 为 ""，则将 json 整体而非内部字段转换为 array。
     *  2: 可被 getArray 转调并满足其需求。
     *  3: 支持内部元素为上传文件 UploadedFile。
     * </pre>
     */
    @Override
    public <T> List<T> getList(String name, Class<T> type) {
        getData();    // 确保解析

        if ("".equals(name) && arrayData != null) {
            return arrayData.toList(type);
        } else {
            return data.getList(name, type);
        }
    }

    /**
     * 获取数组。内部始终转调 getList(...) 共享其所支持的所有功能，如支持 name 值为 ""
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] getArray(String name, Class<T> type) {
        List<T> ret = getList(name, type);
        return ret != null ? ret.toArray((T[]) Array.newInstance(type, ret.size())) : null;
    }

    /**
     * 获取 getMap。若参数 name 为 ""，则将 json 整体而非内部字段转换为 Map
     */
    @Override
    public Map<String, Object> getMap(String name) {
        // 支持参数名为空字符串 "" 用法，以及 @Para(name = "") 用法
        return "".equals(name) ? getData() : getData().getJSONObject(name);
    }

    @Override
    public String getStr(String name) {
        return getData().getString(name);
    }

    @Override
    public String getStr(int index) {
        if (index < 0) {
            return pathParaStr;
        }

        if (pathPara == null) {
            // pathParaStr 可能为 ""，见 Router.getAction(String)
            if (pathParaStr == null || pathParaStr.isEmpty()) {
                pathPara = EMPTY_PATH_PARA;
            } else {
                // pathParaSeparator 改由用户直接定制，而非安排配置
                pathPara = pathParaStr.split("-");
            }

            for (int i = 0; i < pathPara.length; i++) {
                if ("".equals(pathPara[i])) {
                    pathPara[i] = null;
                }
            }
        }
        return pathPara.length > index ? pathPara[index] : null;
    }

    @Override
    public Integer getInt(String name) {
        return getData().getInteger(name);
    }

    @Override
    public Integer getInt(int index) {
        String str = getStr(index);
        // paraPath 可产生 ""、" "、" a " 这类字符串，需使用 isBlank(str)、trim()
        return StrUtil.isBlank(str) ? null : Integer.valueOf(str.trim());
    }

    @Override
    public Long getLong(String name) {
        return getData().getLong(name);
    }

    @Override
    public Long getLong(int index) {
        String str = getStr(index);
        return StrUtil.isBlank(str) ? null : Long.valueOf(str.trim());
    }

    @Override
    public Double getDouble(String name) {
        return getData().getDouble(name);
    }

    @Override
    public Double getDouble(int index) {
        String str = getStr(index);
        return StrUtil.isBlank(str) ? null : Double.valueOf(str.trim());
    }

    @Override
    public BigDecimal getBigDecimal(String name) {
        return getData().getBigDecimal(name);
    }

    @Override
    public BigDecimal getBigDecimal(int index) {
        String str = getStr(index);
        return StrUtil.isBlank(str) ? null : new BigDecimal(str.trim());
    }

    @Override
    public Boolean getBoolean(String name) {
        // 不使用 fastjson 转换，其仅明确了 true 值条件，未明确 false 值条件
        // return getData().getBoolean(name);
        Object b = getData().get(name);
        if (b instanceof Boolean) {
            return (Boolean) b;
        } else if (b == null) {
            return null;
        } else {
            return IN_PARSER.toBoolean(b);
        }
    }

    @Override
    public Boolean getBoolean(int index) {
        String str = getStr(index);
        return str == null ? null : IN_PARSER.toBoolean(str);
    }

    @Override
    public Date getDate(String name) {
        return getData().getDate(name);
    }

    @Override
    public LocalDate getLocalDate(String name) {
        return getData().getLocalDate(name);
    }

    @Override
    public LocalTime getLocalTime(String name) {
        return getData().getLocalTime(name);
    }

    @Override
    public LocalDateTime getLocalDateTime(String name) {
        return getData().getLocalDateTime(name);
    }
}


