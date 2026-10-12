/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.demo.arch;

import cn.aifei.argument.NoMatch;
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

/** 请求参数适配：懒解析 query + body，实现内核 Input 接口 */
public class In implements Input, NoMatch {

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
        if ("".equals(name)) {
            return getData().to(type);
        }
        return getData().getObject(name, type);
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
