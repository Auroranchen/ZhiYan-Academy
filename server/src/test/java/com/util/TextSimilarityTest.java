package com.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 查重算法的判定结果会写进「疑似抄袭」名单，这里锁住它的行为边界：
 * 什么该判高、什么必须判低，以及各种脏输入不能让它算出离谱的数。
 */
@DisplayName("文本相似度")
class TextSimilarityTest {

    @Nested
    @DisplayName("归一化")
    class Normalize {

        @Test
        @DisplayName("大小写统一，避免改个大小写就躲过查重")
        void lowercases() {
            assertEquals("javaisoop", TextSimilarity.normalize("Java Is OOP"));
        }

        @Test
        @DisplayName("中英文标点和空白全部剔除")
        void stripsPunctuation() {
            assertEquals("面向对象三大特征封装继承多态",
                    TextSimilarity.normalize("面向对象，三大特征：封装、继承、多态。"));
            assertEquals("abc", TextSimilarity.normalize("a, b. c!"));
        }

        @Test
        @DisplayName("只改标点和空格不能降低相似度")
        void punctuationOnlyEditsDoNotHelp() {
            String origin = "面向对象的三大特征是封装、继承和多态";
            String tweaked = "面向对象的三大特征是：封装，继承，和多态！！！";

            assertEquals(100.0, TextSimilarity.similarityPercent(origin, tweaked));
        }

        @Test
        @DisplayName("null 与空串返回空串，不抛异常")
        void handlesNull() {
            assertEquals("", TextSimilarity.normalize(null));
            assertEquals("", TextSimilarity.normalize(""));
            assertEquals("", TextSimilarity.normalize("   "));
        }
    }

    @Nested
    @DisplayName("n-gram 切分")
    class Shingles {

        @Test
        @DisplayName("按长度 3 逐字符滑窗")
        void slidesByThree() {
            assertEquals(Set.of("abc", "bcd", "cde"), TextSimilarity.shingles("abcde"));
        }

        @Test
        @DisplayName("重复片段只保留一份")
        void deduplicates() {
            assertEquals(Set.of("aaa"), TextSimilarity.shingles("aaaaa"));
        }

        @Test
        @DisplayName("不足 3 个字符的整体作为一个片段，不会退化成空集")
        void keepsShortTextAsOne() {
            assertEquals(Set.of("ab"), TextSimilarity.shingles("ab"));
            assertEquals(Set.of("a"), TextSimilarity.shingles("a"));
        }

        @Test
        @DisplayName("空输入返回空集")
        void emptyForBlank() {
            assertTrue(TextSimilarity.shingles("").isEmpty());
            assertTrue(TextSimilarity.shingles(null).isEmpty());
        }

        @Test
        @DisplayName("片段数量符合滑窗公式")
        void countMatchesWindow() {
            assertEquals(8, TextSimilarity.shingles("abcdefghij").size());
        }
    }

    @Nested
    @DisplayName("Jaccard 系数")
    class Jaccard {

        @Test
        @DisplayName("完全相同为 1，毫无交集为 0")
        void boundaries() {
            Set<String> a = Set.of("abc", "bcd");

            assertEquals(1.0, TextSimilarity.jaccard(a, Set.of("abc", "bcd")));
            assertEquals(0.0, TextSimilarity.jaccard(a, Set.of("xyz", "yzw")));
        }

        @Test
        @DisplayName("交集除以并集")
        void ratio() {
            // 交集 {b,c} = 2，并集 {a,b,c,d} = 4
            assertEquals(0.5, TextSimilarity.jaccard(Set.of("a", "b", "c"), Set.of("b", "c", "d")));
        }

        @Test
        @DisplayName("与比较顺序无关")
        void symmetric() {
            Set<String> a = Set.of("a", "b", "c", "d");
            Set<String> b = Set.of("c", "d", "e");

            assertEquals(TextSimilarity.jaccard(a, b), TextSimilarity.jaccard(b, a));
        }

        @Test
        @DisplayName("空集与 null 一律返回 0，不会算成完全相同")
        void emptyIsZero() {
            Set<String> a = Set.of("abc");

            assertEquals(0.0, TextSimilarity.jaccard(a, Collections.emptySet()));
            assertEquals(0.0, TextSimilarity.jaccard(Collections.emptySet(), a));
            assertEquals(0.0, TextSimilarity.jaccard(Collections.emptySet(), Collections.emptySet()));
            assertEquals(0.0, TextSimilarity.jaccard(a, null));
            assertEquals(0.0, TextSimilarity.jaccard(null, a));
        }
    }

    @Nested
    @DisplayName("相似度百分比")
    class Percent {

        @Test
        @DisplayName("原样照抄判为 100")
        void identical() {
            String text = "封装是把数据和操作数据的方法绑定在一起，对外只暴露必要的接口。";

            assertEquals(100.0, TextSimilarity.similarityPercent(text, text));
        }

        @Test
        @DisplayName("较长答案改动个别词，仍判为高相似")
        void longAnswerParaphraseStaysHigh() {
            String origin = "面向对象编程的核心思想是把现实世界的事物抽象成对象，通过封装继承和多态组织代码结构。";
            String copy = "面向对象编程的核心理念是把现实世界的事物抽象成对象，通过封装继承和多态组织代码结构。";

            assertTrue(TextSimilarity.similarityPercent(origin, copy) >= 70,
                    "改词的抄袭应触发默认 70 阈值");
        }

        @Test
        @DisplayName("已知局限：短答案改动两个字就能压到阈值以下")
        void shortAnswerParaphraseEscapesThreshold() {
            String origin = "封装是把数据和操作数据的方法绑定在一起，对外只暴露必要的接口。";
            String copy = "封装是把数据和操作数据的方法绑定在一起，对外仅暴露必需的接口。";

            double sim = TextSimilarity.similarityPercent(origin, copy);

            // 改一个字会波及 3 个 gram，答案越短占比越大：29 字里改 2 个字就掉到约 64%。
            // 这是字符 n-gram 的固有特性，短主观题需要教师把阈值调低到 60 左右。
            assertTrue(sim > 55 && sim < 70, "实际相似度：" + sim);
        }

        @Test
        @DisplayName("同题目的不同作答不应被误判")
        void differentAnswersStayLow() {
            String a = "封装是把数据和方法绑定在一起，隐藏内部实现细节。";
            String b = "多态指同一个方法调用在不同对象上产生不同的行为表现。";

            assertTrue(TextSimilarity.similarityPercent(a, b) < 70,
                    "不同内容不能达到默认 70 阈值，否则会冤枉学生");
        }

        @Test
        @DisplayName("结果始终落在 0 到 100 之间")
        void withinRange() {
            String[] samples = {"", "a", "抄袭文本", "Java Virtual Machine", "  ,,,  "};

            for (String x : samples) {
                for (String y : samples) {
                    double sim = TextSimilarity.similarityPercent(x, y);
                    assertTrue(sim >= 0 && sim <= 100, "越界：" + sim);
                }
            }
        }

        @Test
        @DisplayName("空文本之间不算相似，避免未作答的学生互相配对")
        void blanksAreNotSimilar() {
            assertEquals(0.0, TextSimilarity.similarityPercent("", ""));
            assertEquals(0.0, TextSimilarity.similarityPercent(null, null));
            assertEquals(0.0, TextSimilarity.similarityPercent("，。！", "；：？"));
        }

        @Test
        @DisplayName("保留一位小数，不会因浮点误差抖动")
        void roundsToOneDecimal() {
            double sim = TextSimilarity.similarityPercent("abcdefg", "abcdxyz");

            assertEquals(sim, Math.round(sim * 10) / 10.0);
        }

        @Test
        @DisplayName("长答案里插入一小段无关内容，相似度只小幅下降")
        void insertionOnlyLowersSlightly() {
            String origin = "面向对象编程的核心思想是把现实世界的事物抽象成对象，通过封装继承和多态组织代码结构。";
            String padded = origin + "另外我还查阅了一些资料。";

            assertTrue(TextSimilarity.similarityPercent(origin, padded) >= 70,
                    "追加少量内容不足以规避查重");
        }
    }
}
