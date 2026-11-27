/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.excel.internal.easyexcel;

import cn.hg.aifei.excel.ExcelColumn;
import cn.hg.aifei.excel.ExcelIgnore;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 字段参与、表头名、列序与类型校验的唯一定义点。
 *
 * <p>读写两侧共用同一映射规则：构建时扫描当前类声明字段（不含父类，跳过 static / transient），
 * {@code ExcelIgnore} 优先排除；读时按「列序优先、表头名原样匹配」定位列，写时按「列序优先、
 * 声明顺序填空位」生成列。类型仅支持内置常用类型，无自定义转换器入口。</p>
 *
 * <p>本类为非承诺实现层类型，不出现在任何公共签名中。</p>
 *
 * @param <T> 行模型类型
 */
public final class RowMapper<T> {

    private static final Set<Class<?>> SUPPORTED_TYPES = Collections.unmodifiableSet(
            new HashSet<Class<?>>(Arrays.asList(
                    String.class,
                    int.class, Integer.class, long.class, Long.class,
                    double.class, Double.class, float.class, Float.class,
                    short.class, Short.class, byte.class, Byte.class,
                    boolean.class, Boolean.class, char.class, Character.class,
                    BigDecimal.class, BigInteger.class, Date.class,
                    LocalDate.class, LocalDateTime.class, LocalTime.class)));

    private final Class<T> type;
    private final List<FieldMeta> fields;
    private final int[] writeColumns;
    private final int[] readColumns;
    private final int columnCount;

    private RowMapper(Class<T> type, List<FieldMeta> fields) {
        this.type = type;
        this.fields = Collections.unmodifiableList(fields);
        this.writeColumns = new int[fields.size()];
        this.readColumns = new int[fields.size()];
        Arrays.fill(this.readColumns, -1);

        Set<Integer> used = new HashSet<Integer>();
        for (int i = 0; i < fields.size(); i++) {
            int idx = fields.get(i).index();
            if (idx >= 0) {
                writeColumns[i] = idx;
                used.add(Integer.valueOf(idx));
            }
        }
        int next = 0;
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).index() < 0) {
                while (used.contains(Integer.valueOf(next))) {
                    next++;
                }
                writeColumns[i] = next;
                used.add(Integer.valueOf(next));
            }
        }

        int max = -1;
        for (int column : writeColumns) {
            if (column > max) {
                max = column;
            }
        }
        this.columnCount = max + 1;
    }

    /**
     * 构建行映射：扫描声明字段、解析注解、校验重复列序。
     *
     * @param type 行模型类型，需可访问的无参构造器
     * @param <T>  行模型类型
     * @return 该类型的唯一映射定义
     */
    public static <T> RowMapper<T> build(Class<T> type) {
        List<FieldMeta> metas = new ArrayList<FieldMeta>();
        Map<Integer, String> indexOwner = new HashMap<Integer, String>();
        for (Field field : type.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
                continue;
            }
            if (field.isAnnotationPresent(ExcelIgnore.class)) {
                continue;
            }
            String header = field.getName();
            int index = -1;
            ExcelColumn column = field.getAnnotation(ExcelColumn.class);
            if (column != null) {
                if (column.value() != null && column.value().length() > 0) {
                    header = column.value();
                }
                if (column.index() >= 0) {
                    index = column.index();
                }
            }
            if (index >= 0) {
                String previous = indexOwner.put(Integer.valueOf(index), field.getName());
                if (previous != null) {
                    throw ExcelErrors.duplicatedIndex(index, previous, field.getName());
                }
            }
            field.setAccessible(true);
            metas.add(new FieldMeta(field.getName(), header, index, field.getType(), field));
        }
        return new RowMapper<T>(type, metas);
    }

    /**
     * 参与映射的字段元数据（只读视图）。
     *
     * @return 字段元数据列表
     */
    public List<FieldMeta> fields() {
        return fields;
    }

    /**
     * 解析表头行：检测重复表头，确定每个字段的读列位置。
     *
     * @param headerCells 表头行单元格（列号到文本）
     */
    public void resolveHeader(Map<Integer, String> headerCells) {
        Map<String, Integer> byHeader = new HashMap<String, Integer>();
        for (Map.Entry<Integer, String> entry : headerCells.entrySet()) {
            String header = entry.getValue();
            if (header == null) {
                continue;
            }
            Integer previous = byHeader.put(header, entry.getKey());
            if (previous != null) {
                throw ExcelErrors.duplicatedHeader(header, previous.intValue(), entry.getKey().intValue());
            }
        }
        for (int i = 0; i < fields.size(); i++) {
            FieldMeta meta = fields.get(i);
            if (meta.index() >= 0) {
                readColumns[i] = meta.index();
            } else {
                Integer column = byHeader.get(meta.header());
                readColumns[i] = column == null ? -1 : column.intValue();
            }
        }
    }

    /**
     * 把一个数据行映射为模型对象；调用前须先 {@link #resolveHeader(Map)}。
     *
     * @param cells 数据行单元格（列号到文本）
     * @param row   行号（从 0 起，表头为第 0 行），用于异常定位
     * @return 已映射的模型对象
     */
    public T mapRow(Map<Integer, String> cells, int row) {
        T instance = newInstance();
        for (int i = 0; i < fields.size(); i++) {
            int column = readColumns[i];
            if (column < 0) {
                continue;
            }
            String raw = cells.get(Integer.valueOf(column));
            if (raw == null || raw.length() == 0) {
                continue;
            }
            FieldMeta meta = fields.get(i);
            Object value = parse(raw.trim(), meta, row, column);
            if (value == null) {
                continue;
            }
            try {
                meta.field().set(instance, value);
            } catch (IllegalAccessException e) {
                throw ExcelErrors.convert(row, column, meta.name(), meta.javaType(), e);
            }
        }
        return instance;
    }

    /**
     * 生成写表头的单元格值列表（含未占用列的空串占位）。
     *
     * @return 表头行单元格值
     */
    public List<Object> headerValues() {
        List<Object> header = new ArrayList<Object>(Collections.nCopies(columnCount, ""));
        for (int i = 0; i < fields.size(); i++) {
            header.set(writeColumns[i], fields.get(i).header());
        }
        return header;
    }

    /**
     * 把一个模型对象转换为写数据行的单元格值列表（按列序排列）。
     *
     * @param row 模型对象，为 null 时返回整行空值
     * @return 数据行单元格值
     */
    public List<Object> rowValues(T row) {
        List<Object> values = new ArrayList<Object>(Collections.nCopies(columnCount, null));
        if (row == null) {
            return values;
        }
        for (int i = 0; i < fields.size(); i++) {
            FieldMeta meta = fields.get(i);
            if (!isSupportedType(meta.javaType())) {
                throw ExcelErrors.unsupportedType(meta.javaType(), meta.name());
            }
            Object raw;
            try {
                raw = meta.field().get(row);
            } catch (IllegalAccessException e) {
                throw ExcelErrors.convert(-1, writeColumns[i], meta.name(), meta.javaType(), e);
            }
            values.set(writeColumns[i], toCellValue(raw));
        }
        return values;
    }

    /**
     * 字段类型是否在内置支持范围内。
     *
     * @param type 字段类型
     * @return 支持返回 true，否则 false
     */
    public static boolean isSupportedType(Class<?> type) {
        return type != null && SUPPORTED_TYPES.contains(type);
    }

    private static Object toCellValue(Object value) {
        if (value instanceof Character) {
            return value.toString();
        }
        return value;
    }

    private static Object parse(String raw, FieldMeta meta, int row, int column) {
        Class<?> type = meta.javaType();
        try {
            return parseValue(raw, type);
        } catch (RuntimeException e) {
            throw ExcelErrors.convert(row, column, meta.name(), type, e);
        }
    }

    private static Object parseValue(String value, Class<?> type) {
        if (type == String.class) {
            return value;
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
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.valueOf(value);
        }
        if (type == char.class || type == Character.class) {
            if (value.length() != 1) {
                throw new IllegalArgumentException("expect exactly one character");
            }
            return Character.valueOf(value.charAt(0));
        }
        if (type == BigDecimal.class) {
            return new BigDecimal(value);
        }
        if (type == BigInteger.class) {
            return new BigInteger(value);
        }
        if (type == LocalDate.class) {
            return LocalDate.parse(value);
        }
        if (type == LocalDateTime.class) {
            return LocalDateTime.parse(value);
        }
        if (type == LocalTime.class) {
            return LocalTime.parse(value);
        }
        if (type == Date.class) {
            return parseDate(value);
        }
        throw new IllegalArgumentException("unsupported field type: " + type.getName());
    }

    private static Date parseDate(String value) {
        try {
            return Date.from(LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant());
        } catch (RuntimeException ignored) {
            // 继续尝试仅日期格式
        }
        try {
            return Date.from(LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toInstant());
        } catch (RuntimeException ignored) {
            // 继续尝试 Excel 日期序列号
        }
        try {
            double serial = Double.parseDouble(value);
            return new Date(Math.round((serial - 25569D) * 86400000D));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid date value: " + value, e);
        }
    }

    private T newInstance() {
        try {
            return type.newInstance();
        } catch (Exception e) {
            throw ExcelErrors.noDefaultConstructor(type);
        }
    }
}
