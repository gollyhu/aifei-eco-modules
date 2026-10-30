/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.oss;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import cn.hg.aifei.oss.internal.OssClients;
import cn.hg.aifei.oss.internal.OssConfig;
import cn.hg.aifei.oss.internal.OssStorageFactory;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * 真实 MinIO（S3 兼容）对象存储集成测试（<b>默认关闭</b>）。
 *
 * <p>开启后连不上 MinIO 必须失败，不得静默跳过：</p>
 *
 * <pre>
 * mvn verify -Doss.integration=true
 * </pre>
 *
 * <p>可用系统属性覆盖连接参数：{@code oss.minio.endpoint} / {@code oss.minio.access-key} /
 * {@code oss.minio.secret-key} / {@code oss.minio.bucket}。默认连接 {@code http://127.0.0.1:9000}，
 * 桶 {@code aifei-oss-test} <b>需预先创建</b>。</p>
 */
public class MinioIntegrationTest {

    @Test
    public void uploadDownloadListCopyDelete() {
        assumeTrue("默认关闭：需 -Doss.integration=true 开启", Boolean.getBoolean("oss.integration"));

        String endpoint = System.getProperty("oss.minio.endpoint", "http://127.0.0.1:9000");
        String accessKey = System.getProperty("oss.minio.access-key", "minioadmin");
        String secretKey = System.getProperty("oss.minio.secret-key", "minioadmin");
        String bucket = System.getProperty("oss.minio.bucket", "aifei-oss-test");

        Map<String, String> options = new LinkedHashMap<String, String>();
        options.put("end-point", endpoint);
        options.put("access-key", accessKey);
        options.put("secret-key", secretKey);
        options.put("bucket-name", bucket);

        OssConfig config = new OssConfig("minio-1", "minio", options);
        Map<String, OssClient> clients =
                OssStorageFactory.buildClients("minio-1", Collections.singletonList(config));
        OssClient client = clients.get("minio-1");
        try {
            String path = "upload/it/" + System.currentTimeMillis() + ".txt";
            String copyPath = path + ".copy";
            byte[] content = "minio integration".getBytes(StandardCharsets.UTF_8);

            client.upload(content, path, "text/plain");
            assertTrue("对象应存在", client.exists(path));
            assertArrayEquals(content, client.download(path).bytes());

            List<OssFileInfo> files = client.listFiles("upload/it");
            assertNotNull(files);
            assertFalse("MinIO 平台应支持列举", files.isEmpty());

            assertTrue("复制应成功", client.copy(path, copyPath));
            assertArrayEquals(content, client.download(copyPath).bytes());

            assertTrue(client.delete(path));
            assertTrue(client.delete(copyPath));
        } finally {
            OssClients.clear();
        }
    }
}
