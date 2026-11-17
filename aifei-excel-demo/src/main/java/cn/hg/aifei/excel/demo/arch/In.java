/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.excel.demo.arch;

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
 * In：请求输入（demo 简化版）。
 *
 * <p>与参考实现的关键差异：新增 {@link #getHeader(String)} —— {@code cn.aifei.core.Input} 公开接口
 * 只有参数读取能力、无 header 访问（官方已核实的边界）。id-demo 的端点只用查询参数，
 * 该能力为与参考实现保持逐字一致而保留，本 demo 未使用。</p>
 */
public class In implements Input {

    static final InParser IN_PARSER = new InParser();
    static final String[] EMPTY_PATH_PARA = new String[0];

    final HttpServerExchange exchange;

    JSONObject data;
    JSONArray arrayData;

    // multipart 文件上传解析结果（由 InParser 在解析表单时填充）
    byte[] uploadBytes;
    String uploadFileName;

    String[] pathPara;
    String pathParaStr;

    public In(HttpServerExchange exchange) {
        this.exchange = exchange;
    }

    /**
     * 取上传文件名（multipart 文件体）；无上传时返回 null。触发延迟解析。
     *
     * @return 原始文件名或 null
     */
    public String getUploadFileName() {
        getData();
        return uploadFileName;
    }

    /**
     * 取上传文件字节（multipart 文件体）；无上传时返回 null。触发延迟解析。
     *
     * @return 文件字节或 null
     */
    public byte[] getUploadBytes() {
        getData();
        return uploadBytes;
    }

    public void pathPara(String pathParaStr) {
        this.pathParaStr = pathParaStr;
    }

    public HttpServerExchange getExchange() {
        return exchange;
    }

    /**
     * 读取请求头首个值；头不存在返回 null（与参考实现逐字一致，本 demo 未使用）。
     *
     * @param name 请求头名，大小写不敏感（undertow 按 RFC 处理）
     * @return 头值或 null
     */
    public String getHeader(String name) {
        return exchange.getRequestHeaders().getFirst(name);
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
     * 判断下标参数是否存在。多 action 共享同一 actionPath (方法重载)时需使用该方法进一步匹配参数
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
     * 获取 List。若参数 name 为 ""，且请求体本身是 json 数组，则整体转换
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
        // pathPara 可能产生 ""、" "、" a " 这类字符串，需使用 isBlank(str)、trim()
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
