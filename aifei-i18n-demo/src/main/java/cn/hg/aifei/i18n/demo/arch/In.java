/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.demo.arch;

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
 * In：请求输入（demo 简化版，参照 excel-demo）。
 *
 * <p>本类属从其它 demo 复制的通用脚手架，职责是把 Undertow 的 {@code HttpServerExchange} 适配为
 * 内核 {@code Input}，提供参数 / 路径参数的便捷读取；仅供 demo 演示，不含生产级校验与错误处理。
 * 以下 getter 类方法语义同方法名，不逐方法重复说明。</p>
 */
public class In implements Input {

    static final InParser IN_PARSER = new InParser();
    static final String[] EMPTY_PATH_PARA = new String[0];

    final HttpServerExchange exchange;

    JSONObject data;
    JSONArray arrayData;
    byte[] uploadBytes;
    String uploadFileName;
    String[] pathPara;
    String pathParaStr;

    public In(HttpServerExchange exchange) {
        this.exchange = exchange;
    }

    public String getUploadFileName() {
        getData();
        return uploadFileName;
    }

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

    /** 读取请求头首个值；头不存在返回 null。 */
    public String getHeader(String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    JSONObject getData() {
        if (data == null) {
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

    @Override
    public boolean has(String name) {
        return getData().containsKey(name);
    }

    @Override
    public boolean has(int index) {
        return getStr(index) != null || pathPara.length > index;
    }

    @Override
    public <T> T getBean(String name, Class<T> type) {
        return "".equals(name) ? getData().to(type) : getData().getObject(name, type);
    }

    @Override
    public <T> List<T> getList(String name, Class<T> type) {
        getData();
        if ("".equals(name) && arrayData != null) {
            return arrayData.toList(type);
        }
        return data.getList(name, type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] getArray(String name, Class<T> type) {
        List<T> ret = getList(name, type);
        return ret != null ? ret.toArray((T[]) Array.newInstance(type, ret.size())) : null;
    }

    @Override
    public Map<String, Object> getMap(String name) {
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
        Object b = getData().get(name);
        if (b instanceof Boolean) {
            return (Boolean) b;
        } else if (b == null) {
            return null;
        }
        return IN_PARSER.toBoolean(b);
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
