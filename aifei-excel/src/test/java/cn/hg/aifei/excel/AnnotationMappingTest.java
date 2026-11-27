/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.excel;

import cn.hg.aifei.excel.support.IndexedRow;
import cn.hg.aifei.excel.support.SampleFactory;
import cn.hg.aifei.excel.support.UserRow;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * 注解表头映射测试：
 * 默认全字段、按名原样匹配、value 表头、index 优先、ExcelIgnore 优先、
 * 重复表头/缺列/大小写敏感、重复 index、无字段、不支持类型、xls 可读。
 */
public class AnnotationMappingTest {

    @Test
    public void defaultFields_joinByNameAndValueHeader() {
        List<List<Object>> sheet = new ArrayList<List<Object>>();
        sheet.add(SampleFactory.cells("name", "Age", "score"));
        sheet.add(SampleFactory.cells("Alice", "30", "95.5"));

        List<UserRow> rows = ExcelKit.readAll(new ByteArrayInputStream(SampleFactory.xlsx(sheet)), UserRow.class);

        assertEquals(1, rows.size());
        assertEquals("Alice", rows.get(0).getName());
        assertEquals(30, rows.get(0).getAge());
        assertEquals(95.5D, rows.get(0).getScore(), 0.0001D);
        assertNull("@ExcelIgnore 字段在读侧不参与", rows.get(0).getInternalNote());
    }

    @Test
    public void unannotatedField_matchesHeaderVerbatimOnly() {
        List<List<Object>> snake = new ArrayList<List<Object>>();
        snake.add(SampleFactory.cells("user_name"));
        snake.add(SampleFactory.cells("Bob"));
        assertEquals("不做驼峰/下划线互转", null,
                ExcelKit.readAll(new ByteArrayInputStream(SampleFactory.xlsx(snake)), PlainNameRow.class)
                        .get(0).userName);

        List<List<Object>> verbatim = new ArrayList<List<Object>>();
        verbatim.add(SampleFactory.cells("userName"));
        verbatim.add(SampleFactory.cells("Bob"));
        assertEquals("按字段名原样匹配", "Bob",
                ExcelKit.readAll(new ByteArrayInputStream(SampleFactory.xlsx(verbatim)), PlainNameRow.class)
                        .get(0).userName);
    }

    @Test
    public void headerMatch_isCaseSensitive() {
        List<List<Object>> sheet = new ArrayList<List<Object>>();
        sheet.add(SampleFactory.cells("name", "age", "score")); // 小写 age，与 @ExcelColumn("Age") 不等
        sheet.add(SampleFactory.cells("Alice", "30", "95.5"));
        UserRow row = ExcelKit.readAll(new ByteArrayInputStream(SampleFactory.xlsx(sheet)), UserRow.class).get(0);
        assertEquals("表头严格相等，不折叠大小写", 0, row.getAge());
        assertEquals("Alice", row.getName());
    }

    @Test
    public void columnValue_asHeader_andIndexOverridesHeaderNameOnRead() {
        // index 优先于表头名：故意把 code 的表头名放列0、seq 的表头名放列2
        List<List<Object>> sheet = new ArrayList<List<Object>>();
        sheet.add(SampleFactory.cells("CODE", "note", "SEQ"));
        sheet.add(SampleFactory.cells("99", "noteA", "888"));
        IndexedRow row = ExcelKit.readAll(new ByteArrayInputStream(SampleFactory.xlsx(sheet)), IndexedRow.class).get(0);
        assertEquals("seq 按 index=0 取列0，忽略表头名 CODE", 99, row.getSeq());
        assertEquals("note 无 index，按表头名匹配列1", "noteA", row.getNote());
        assertEquals("code 按 index=2 取列2，忽略表头名 SEQ", "888", row.getCode());

        // 写侧：value 作为表头名，index 决定列位，未注解字段填空位（列1）
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExcelKit.write(out, IndexedRow.class, null,
                java.util.Collections.singletonList(new IndexedRow(1, "mid", "code1")));
        List<IndexedRow> back = ExcelKit.readAll(new ByteArrayInputStream(out.toByteArray()), IndexedRow.class);
        assertEquals(1, back.size());
        assertEquals(1, back.get(0).getSeq());
        assertEquals("mid", back.get(0).getNote());
        assertEquals("code1", back.get(0).getCode());
    }

    @Test
    public void excelIgnore_precedesExcelColumn_andDoesNotOccupyIndex() {
        List<List<Object>> sheet = new ArrayList<List<Object>>();
        sheet.add(SampleFactory.cells("IGNORED", "name"));
        sheet.add(SampleFactory.cells("secretValue", "Alice"));
        IgnorePriorityRow row = ExcelKit.readAll(
                new ByteArrayInputStream(SampleFactory.xlsx(sheet)), IgnorePriorityRow.class).get(0);
        assertNull("@ExcelIgnore 优先于 @ExcelColumn", row.secret);
        assertEquals("Alice", row.name);
    }

    @Test
    public void duplicatedHeader_throwsConvertError() {
        List<List<Object>> sheet = new ArrayList<List<Object>>();
        sheet.add(SampleFactory.cells("name", "name", "score"));
        sheet.add(SampleFactory.cells("A", "B", "1"));
        ExcelException ex = assertThrows(ExcelException.class,
                () -> ExcelKit.readAll(new ByteArrayInputStream(SampleFactory.xlsx(sheet)), UserRow.class));
        assertTrue(ex.getMessage(), ex.getMessage().startsWith("excel: duplicated header \"name\" at column"));
        assertTrue(ex.getMessage(), ex.getMessage().contains("0") && ex.getMessage().contains("1"));
    }

    @Test
    public void missingColumn_fieldKeepsJavaDefault() {
        List<List<Object>> sheet = new ArrayList<List<Object>>();
        sheet.add(SampleFactory.cells("name"));
        sheet.add(SampleFactory.cells("Alice"));
        UserRow row = ExcelKit.readAll(new ByteArrayInputStream(SampleFactory.xlsx(sheet)), UserRow.class).get(0);
        assertEquals("Alice", row.getName());
        assertEquals(0, row.getAge());
        assertEquals(0.0D, row.getScore(), 0.0D);
    }

    @Test
    public void duplicatedColumnIndex_throwsOnBuild() {
        ExcelException ex = assertThrows(ExcelException.class,
                () -> ExcelKit.write(new ByteArrayOutputStream(), DupIndexRow.class, null));
        assertTrue(ex.getMessage(), ex.getMessage().startsWith("excel: duplicated column index 0 on fields"));
        assertTrue(ex.getMessage(), ex.getMessage().contains("alpha") && ex.getMessage().contains("beta"));
    }

    @Test
    public void allFieldsIgnored_writeOnlyHeader_readNoThrow() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExcelKit.write(out, AllIgnoredRow.class, null,
                java.util.Collections.singletonList(new AllIgnoredRow()));
        assertTrue("写出文件须非空", out.size() > 0);
        List<AllIgnoredRow> back = ExcelKit.readAll(new ByteArrayInputStream(out.toByteArray()), AllIgnoredRow.class);
        assertNotNull("无字段类读不抛异常", back);
    }

    @Test
    public void unsupportedFieldType_throwsOnWrite() {
        UnsupportedRow row = new UnsupportedRow();
        row.label = "L";
        row.attributes = java.util.Collections.singletonMap("k", "v");
        ExcelException ex = assertThrows(ExcelException.class,
                () -> ExcelKit.write(new ByteArrayOutputStream(), UnsupportedRow.class, null,
                        java.util.Collections.singletonList(row)));
        assertEquals("excel: unsupported field type java.util.Map on field attributes", ex.getMessage());
    }

    @Test
    public void badCellValue_throwsConvertErrorWithLocation() {
        List<List<Object>> sheet = new ArrayList<List<Object>>();
        sheet.add(SampleFactory.cells("name", "Age", "score"));
        sheet.add(SampleFactory.cells("Alice", "not-a-number", "1.0"));
        ExcelException ex = assertThrows(ExcelException.class,
                () -> ExcelKit.readAll(new ByteArrayInputStream(SampleFactory.xlsx(sheet)), UserRow.class));
        assertTrue(ex.getMessage(), ex.getMessage().startsWith("excel: convert failed at row 1, column 1"));
        assertTrue(ex.getMessage(), ex.getMessage().contains("field age") && ex.getMessage().contains("type int"));
    }

    @Test
    public void xlsSample_isReadable() {
        List<List<Object>> sheet = new ArrayList<List<Object>>();
        sheet.add(SampleFactory.cells("name", "Age", "score"));
        sheet.add(SampleFactory.cells("XlsUser", "28", "88.5"));
        List<UserRow> rows = ExcelKit.readAll(new ByteArrayInputStream(SampleFactory.xls(sheet)), UserRow.class);
        assertEquals(1, rows.size());
        assertEquals("XlsUser", rows.get(0).getName());
        assertEquals(28, rows.get(0).getAge());
    }

    // ---------------------------------------------------------------- 测试用行模型

    public static class PlainNameRow {
        public String userName;
    }

    public static class DupIndexRow {
        @ExcelColumn(index = 0)
        public String alpha;
        @ExcelColumn(index = 0)
        public String beta;
    }

    public static class IgnorePriorityRow {
        @ExcelIgnore
        @ExcelColumn(value = "IGNORED", index = 0)
        public String secret;
        public String name;
    }

    public static class AllIgnoredRow {
        @ExcelIgnore
        public String secret;
        public static String SHARED = "shared";
        public transient String temp;
    }

    public static class UnsupportedRow {
        public String label;
        public Map<String, String> attributes;
    }
}
