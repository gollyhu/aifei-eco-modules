/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.captcha.internal;

import java.security.SecureRandom;
import java.util.Base64;

import cn.hg.aifei.tools.captcha.CaptchaType;

/**
 * 令牌与题目生成的唯一定义点。
 *
 * <p>token 由安全随机数取 32 字节后按 Base64 URL 无填充编码，不可从图片或 token 反推答案；
 * 字符题剔除易混淆字符，算式为一位数加减乘（减法保证非负）。</p>
 */
public final class TokenGenerator {

    private static final int TOKEN_BYTES = 32;
    private static final int CHAR_COUNT = 4;
    private static final char[] CHAR_POOL = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();
    private static final char[] OPERATORS = {'+', '-', '*'};
    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenGenerator() {
    }

    /**
     * 生成不可猜测的令牌。
     *
     * @return Base64 URL 无填充令牌
     */
    public static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 按类型生成题目与答案。
     *
     * @param type 题目类型
     * @return 题目文本与标准答案
     */
    public static Question newQuestion(CaptchaType type) {
        if (type == CaptchaType.ARITHMETIC) {
            int a = 1 + RANDOM.nextInt(9);
            int b = 1 + RANDOM.nextInt(9);
            char op = OPERATORS[RANDOM.nextInt(OPERATORS.length)];
            if (op == '-' && a < b) {
                int swap = a;
                a = b;
                b = swap;
            }
            int result;
            if (op == '+') {
                result = a + b;
            } else if (op == '-') {
                result = a - b;
            } else {
                result = a * b;
            }
            return new Question(a + String.valueOf(op) + b + "=?", String.valueOf(result));
        }
        StringBuilder text = new StringBuilder(CHAR_COUNT);
        for (int i = 0; i < CHAR_COUNT; i++) {
            text.append(CHAR_POOL[RANDOM.nextInt(CHAR_POOL.length)]);
        }
        return new Question(text.toString(), text.toString());
    }

    /**
     * 题目与标准答案的不可变载体。
     */
    public static final class Question {

        private final String text;
        private final String answer;

        private Question(String text, String answer) {
            this.text = text;
            this.answer = answer;
        }

        /**
         * 题目文本。
         *
         * @return 绘制到图片上的题目
         */
        public String getText() {
            return text;
        }

        /**
         * 标准答案。
         *
         * @return 与题目匹配的答案
         */
        public String getAnswer() {
            return answer;
        }
    }
}
