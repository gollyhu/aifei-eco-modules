/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.qr.internal;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

import javax.imageio.ImageIO;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import cn.hg.aifei.tools.qr.QrErrorCorrection;

/**
 * 二维码矩阵与 PNG 互转的唯一定义点（BitMatrix ↔ BufferedImage/PNG）。
 *
 * <p>编码用内核 QRCodeWriter 出矩阵后自绘 PNG；解码自写亮度源后交内核 MultiFormatReader，
 * 不引 javase。所有 ZXing 类型仅停留在本层，公共面零第三方类型。</p>
 */
public final class ImageCodec {

    private static final String CHARSET = "UTF-8";
    private static final String IMAGE_FORMAT = "png";
    /** 内核拒绝空内容，用私有区字符承载空串，解码时再归一，正常文本不受影响。 */
    private static final String EMPTY_SENTINEL = "\uE000";

    private ImageCodec() {
    }

    /**
     * 把文本编码为 PNG 字节。
     *
     * @param text 待编码文本，可为空串
     * @param size 图片边长像素
     * @param margin 静区边距
     * @param ec 纠错等级
     * @return PNG 字节
     * @throws cn.hg.aifei.tools.qr.QrException 编码或写出失败
     */
    public static byte[] encodePng(String text, int size, int margin, QrErrorCorrection ec) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<EncodeHintType, Object>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, CHARSET);
            hints.put(EncodeHintType.MARGIN, Integer.valueOf(margin));
            hints.put(EncodeHintType.ERROR_CORRECTION, levelOf(ec));
            String content = text.isEmpty() ? EMPTY_SENTINEL : text;
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints);
            return toPng(matrix);
        } catch (WriterException | RuntimeException e) {
            throw QrErrors.encodeFailed(e);
        }
    }

    /**
     * 从 PNG 字节解码文本。
     *
     * @param imageBytes PNG 字节
     * @return 原始文本，可为空串
     * @throws cn.hg.aifei.tools.qr.QrException 图片非法或无法识别
     */
    public static String decodePng(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw QrErrors.emptyImage();
        }
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        } catch (IOException e) {
            throw QrErrors.decodeFailed(e);
        }
        if (image == null) {
            throw QrErrors.decodeFailed(new IOException("unsupported image format"));
        }
        return decodeImage(image);
    }

    /**
     * 从输入流解码文本，不关闭调用方的流。
     *
     * @param in 输入流
     * @return 原始文本，可为空串
     * @throws cn.hg.aifei.tools.qr.QrException 图片非法或无法识别
     */
    public static String decodeStream(InputStream in) {
        if (in == null) {
            throw QrErrors.nullStream();
        }
        BufferedImage image;
        try {
            image = ImageIO.read(in);
        } catch (IOException e) {
            throw QrErrors.decodeFailed(e);
        }
        if (image == null) {
            throw QrErrors.decodeFailed(new IOException("unsupported image format"));
        }
        return decodeImage(image);
    }

    private static String decodeImage(BufferedImage image) {
        try {
            LuminanceSource source = new ImageLuminanceSource(image);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Map<DecodeHintType, Object> hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
            hints.put(DecodeHintType.CHARACTER_SET, CHARSET);
            Result result = new MultiFormatReader().decode(bitmap, hints);
            String text = result.getText();
            if (text == null || EMPTY_SENTINEL.equals(text)) {
                return "";
            }
            return text;
        } catch (NotFoundException e) {
            throw QrErrors.decodeFailed(e);
        } catch (RuntimeException e) {
            throw QrErrors.decodeFailed(e);
        }
    }

    private static byte[] toPng(BitMatrix matrix) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setColor(Color.BLACK);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (matrix.get(x, y)) {
                        g.fillRect(x, y, 1, 1);
                    }
                }
            }
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, IMAGE_FORMAT, out);
        } catch (IOException e) {
            throw QrErrors.encodeFailed(e);
        }
        return out.toByteArray();
    }

    private static ErrorCorrectionLevel levelOf(QrErrorCorrection ec) {
        if (ec == null) {
            throw QrErrors.nullArgument("of", "errorCorrection");
        }
        switch (ec) {
            case L:
                return ErrorCorrectionLevel.L;
            case Q:
                return ErrorCorrectionLevel.Q;
            case H:
                return ErrorCorrectionLevel.H;
            case M:
            default:
                return ErrorCorrectionLevel.M;
        }
    }

    /** 纯 JDK 亮度源：由 BufferedImage 像素换算灰度，替代 javase 的亮度源实现。 */
    private static final class ImageLuminanceSource extends LuminanceSource {

        private final int[] pixels;

        private ImageLuminanceSource(BufferedImage image) {
            super(image.getWidth(), image.getHeight());
            pixels = image.getRGB(0, 0, getWidth(), getHeight(), null, 0, getWidth());
        }

        @Override
        public byte[] getRow(int y, byte[] row) {
            byte[] target = row == null || row.length < getWidth() ? new byte[getWidth()] : row;
            int offset = y * getWidth();
            for (int x = 0; x < getWidth(); x++) {
                int rgb = pixels[offset + x];
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                target[x] = (byte) ((r * 306 + g * 601 + b * 117) >> 10);
            }
            return target;
        }

        @Override
        public byte[] getMatrix() {
            byte[] matrix = new byte[getWidth() * getHeight()];
            for (int y = 0; y < getHeight(); y++) {
                byte[] row = getRow(y, new byte[getWidth()]);
                System.arraycopy(row, 0, matrix, y * getWidth(), getWidth());
            }
            return matrix;
        }
    }
}
