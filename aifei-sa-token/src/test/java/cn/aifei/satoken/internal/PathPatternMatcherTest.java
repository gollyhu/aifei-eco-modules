/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.aifei.satoken.internal;

import org.junit.Assert;
import org.junit.Test;

/** 路径表达式语义：通配符、锚定、动态变量与缓存 */
public class PathPatternMatcherTest {

    @Test
    public void singleStarMatchesExactlyOneSegment() {
        PathPatternMatcher matcher = PathPatternMatcher.get("/a/*");
        Assert.assertTrue(matcher.matches("/a/b"));
        Assert.assertFalse("单星不跨层级", matcher.matches("/a/b/c"));
        Assert.assertFalse("单星不匹配缺省段", matcher.matches("/a"));
    }

    @Test
    public void doubleStarMatchesAnyDepthAndMayBeAbsent() {
        PathPatternMatcher matcher = PathPatternMatcher.get("/a/**");
        Assert.assertTrue(matcher.matches("/a"));
        Assert.assertTrue(matcher.matches("/a/b"));
        Assert.assertTrue(matcher.matches("/a/b/c"));
        Assert.assertFalse("前缀必须完整匹配", matcher.matches("/ab"));
    }

    @Test
    public void wholeMatchIsAnchored() {
        PathPatternMatcher matcher = PathPatternMatcher.get("/a");
        Assert.assertTrue(matcher.matches("/a"));
        Assert.assertFalse(matcher.matches("/a/b"));
        Assert.assertFalse(matcher.matches("/xa"));
    }

    @Test
    public void regexMetaCharactersAreEscaped() {
        PathPatternMatcher matcher = PathPatternMatcher.get("/a.b");
        Assert.assertTrue(matcher.matches("/a.b"));
        Assert.assertFalse("点号应作为字面量", matcher.matches("/axb"));
    }

    @Test
    public void pathVariableMatchesSingleSegment() {
        PathPatternMatcher matcher = PathPatternMatcher.get("/u/{id}");
        Assert.assertTrue(matcher.matches("/u/123"));
        Assert.assertFalse(matcher.matches("/u/1/2"));
    }

    @Test
    public void crossLayerPathVariableMatchesAnyDepth() {
        Assert.assertTrue(PathPatternMatcher.get("/u/{path_}").matches("/u/1/2"));
    }

    @Test
    public void questionMarkMatchesSingleCharacter() {
        PathPatternMatcher matcher = PathPatternMatcher.get("/a?");
        Assert.assertTrue(matcher.matches("/ab"));
        Assert.assertFalse(matcher.matches("/a/"));
    }

    @Test
    public void sameExpressionReusesCompiledInstance() {
        Assert.assertSame(PathPatternMatcher.get("/x/**"), PathPatternMatcher.get("/x/**"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBlankExpression() {
        PathPatternMatcher.get("   ");
    }
}
