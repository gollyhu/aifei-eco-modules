/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.oss;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.oss.internal.OssClients;
import cn.hg.aifei.oss.internal.OssConfig;
import cn.hg.aifei.oss.internal.OssStorageFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * 本地存储平台的功能测试：验证门面到 x-file-storage 的完整链路（不联网）。
 *
 * <p>使用 {@code local-plus} 平台：它是 X File Storage 推荐的本地实现，支持列举。</p>
 */
public class LocalStorageTest {

    @Test
    public void uploadDownloadDeleteExists() throws IOException {
        Path baseDir = Files.createTempDirectory("aifei-oss-test");
        try {
            OssClient client = localClient(baseDir);
            String path = "upload/demo/a.txt";
            byte[] content = "hello aifei-oss".getBytes(StandardCharsets.UTF_8);

            OssFileInfo info = client.upload(content, path, "text/plain");
            assertNotNull(info);
            assertEquals("local-1", info.getPlatform());
            assertEquals(path, info.getPath());

            assertTrue("对象应存在", client.exists(path));

            OssObject object = client.download(path);
            assertNotNull(object);
            assertArrayEquals(content, object.bytes());

            OssFileInfo loaded = client.getFileInfo(path);
            assertEquals(path, loaded.getPath());

            assertTrue("删除应成功", client.delete(path));
            assertFalse("删除后应不存在", client.exists(path));
        } finally {
            OssClients.clear();
            deleteRecursively(baseDir);
        }
    }

    @Test
    public void copyAndListFiles() throws IOException {
        Path baseDir = Files.createTempDirectory("aifei-oss-test-copy");
        try {
            OssClient client = localClient(baseDir);
            byte[] content = "copy me".getBytes(StandardCharsets.UTF_8);
            client.upload(content, "upload/demo/a.txt", "text/plain");

            List<OssFileInfo> files = client.listFiles("upload/demo");
            assertNotNull(files);
            assertFalse("local-plus 应支持列举", files.isEmpty());

            assertTrue("复制应成功", client.copy("upload/demo/a.txt", "upload/demo/b.txt"));
            assertTrue("目标对象应存在", client.exists("upload/demo/b.txt"));
            assertArrayEquals(content, client.download("upload/demo/b.txt").bytes());
        } finally {
            OssClients.clear();
            deleteRecursively(baseDir);
        }
    }

    @Test
    public void missingObjectSemantics() throws IOException {
        Path baseDir = Files.createTempDirectory("aifei-oss-test-missing");
        try {
            OssClient client = localClient(baseDir);
            try {
                // Downloader 为惰性：download 本身不读流，读取内容时才真正访问存储
                client.download("nope/missing.txt").bytes();
                org.junit.Assert.fail("读取不存在的对象应抛 OssException");
            } catch (OssException expected) {
                // 期望：底层异常已由适配层翻译为 OssException
            }
            assertTrue("删除为幂等：对象不存在也返回 true", client.delete("nope/missing.txt"));
            assertTrue("列举空目录应返回空列表", client.listFiles("nope/empty").isEmpty());
        } finally {
            OssClients.clear();
            deleteRecursively(baseDir);
        }
    }

    @Test
    public void uploadIfAbsentDoesNotOverwrite() throws IOException {
        Path baseDir = Files.createTempDirectory("aifei-oss-test-absent");
        try {
            OssClient client = localClient(baseDir);
            byte[] first = "first".getBytes(StandardCharsets.UTF_8);
            byte[] second = "second".getBytes(StandardCharsets.UTF_8);

            client.uploadIfAbsent(first, "keep/a.txt", "text/plain");
            client.uploadIfAbsent(second, "keep/a.txt", "text/plain");

            assertArrayEquals("已存在时不应覆盖", first, client.download("keep/a.txt").bytes());
        } finally {
            OssClients.clear();
            deleteRecursively(baseDir);
        }
    }

    @Test
    public void pagedListFilesReturnsPage() throws IOException {
        Path baseDir = Files.createTempDirectory("aifei-oss-test-page");
        try {
            OssClient client = localClient(baseDir);
            for (int i = 0; i < 3; i++) {
                client.upload(("v" + i).getBytes(StandardCharsets.UTF_8), "p/" + i + ".txt", "text/plain");
            }
            OssPage page = client.listFiles("p", 2, null);
            assertNotNull(page);
            assertFalse("分页列举应返回对象", page.getFiles().isEmpty());
            assertNotNull("子目录列表不应为 null", page.getDirectories());
        } finally {
            OssClients.clear();
            deleteRecursively(baseDir);
        }
    }

    private static OssClient localClient(Path baseDir) {
        Map<String, String> options = new LinkedHashMap<String, String>();
        options.put("base-path", baseDir.toAbsolutePath().toString());
        options.put("domain", "http://localhost/files/");
        OssConfig config = new OssConfig("local-1", "local-plus", options);
        Map<String, OssClient> clients =
                OssStorageFactory.buildClients("local-1", Collections.singletonList(config));
        return clients.get("local-1");
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        Files.walk(dir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignore) {
                        // 临时目录清理失败不影响测试结果
                    }
                });
    }
}
