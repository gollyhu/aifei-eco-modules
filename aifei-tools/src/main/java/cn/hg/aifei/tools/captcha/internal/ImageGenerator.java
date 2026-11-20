/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.captcha.internal;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;

import javax.imageio.ImageIO;

/**
 * 验证码图片生成：纯 JDK {@link Graphics2D} 绘制 PNG，不引第三方图形库。
 */
public final class ImageGenerator {

    private static final int HEIGHT = 40;
    private static final int FONT_SIZE = 22;
    private static final SecureRandom RANDOM = new SecureRandom();

    private ImageGenerator() {
    }

    /**
     * 绘制题目文本为 PNG 字节。
     *
     * @param text 题目文本
     * @return PNG 图片字节
     * @throws cn.hg.aifei.tools.captcha.CaptchaException 图片写出失败
     */
    public static byte[] draw(String text) {
        int width = Math.max(90, 20 + text.length() * 18);
        BufferedImage image = new BufferedImage(width, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, HEIGHT);
            for (int i = 0; i < 6; i++) {
                g.setColor(lightColor());
                g.drawLine(RANDOM.nextInt(width), RANDOM.nextInt(HEIGHT),
                        RANDOM.nextInt(width), RANDOM.nextInt(HEIGHT));
            }
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, FONT_SIZE));
            g.setColor(new Color(20, 60, 140));
            g.drawString(text, 10, 28);
            for (int i = 0; i < 24; i++) {
                g.setColor(lightColor());
                g.fillRect(RANDOM.nextInt(width), RANDOM.nextInt(HEIGHT), 2, 2);
            }
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw CaptchaErrors.imageFailed(e);
        }
        return out.toByteArray();
    }

    private static Color lightColor() {
        return new Color(160 + RANDOM.nextInt(80), 160 + RANDOM.nextInt(80), 160 + RANDOM.nextInt(80));
    }
}
