package com.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.util.ObjectiveGrading;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 错题本里答案的展示形态。
 *
 * 程序填空题的答案是逐空的数组，直接 asText() 会得到空串——学生打开错题本
 * 只能看到题干、看不到自己填了什么。这里把各题型的渲染钉住。
 */
@DisplayName("错题本答案渲染")
class WrongBookAnswerFormatTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WrongBookService service;

    @BeforeEach
    void setUp() {
        service = new WrongBookService();
    }

    private static JsonNode json(String raw) throws Exception {
        return MAPPER.readTree(raw);
    }

    @Nested
    @DisplayName("程序填空题")
    class ProgramBlank {

        @Test
        @DisplayName("标准答案按空位编号展开，不再是空白")
        void correctAnswerListsBlanks() throws Exception {
            JsonNode q = json("{\"type\":\"prog_blank\",\"answer\":[\"int\",\"i++\"]}");

            assertEquals("空1：int；空2：i++", service.formatCorrect(q, "prog_blank"));
        }

        @Test
        @DisplayName("学生作答同样按空位展开")
        void studentAnswerListsBlanks() throws Exception {
            assertEquals(
                    "空1：int；空2：i--",
                    service.formatStudent(json("[\"int\",\"i--\"]"), "prog_blank"));
        }

        @Test
        @DisplayName("漏填的空显示为空内容，而不是整条消失")
        void keepsEmptyBlank() throws Exception {
            assertEquals("空1：int；空2：", service.formatStudent(json("[\"int\",\"\"]"), "prog_blank"));
        }

        @Test
        @DisplayName("完全未作答显示未作答")
        void notAnswered() throws Exception {
            assertEquals("未作答", service.formatStudent(json("[]"), "prog_blank"));
            assertEquals("未作答", service.formatStudent(null, "prog_blank"));
        }

        @Test
        @DisplayName("单空题以标量下发时也能渲染")
        void scalarAnswer() throws Exception {
            assertEquals("int", service.formatStudent(json("\"int\""), "prog_blank"));
        }
    }

    @Nested
    @DisplayName("其他题型保持原样")
    class OtherTypes {

        @Test
        @DisplayName("单选显示选项字母与内容")
        void single() throws Exception {
            JsonNode q = json("{\"type\":\"single\",\"answer\":1,\"options\":[\"甲\",\"乙\"]}");

            assertEquals("B. 乙", service.formatCorrect(q, "single"));
            assertEquals("选项B", service.formatStudent(json("1"), "single"));
        }

        @Test
        @DisplayName("多选按分隔符列出")
        void multi() throws Exception {
            JsonNode q = json("{\"type\":\"multi\",\"answer\":[0,1],\"options\":[\"甲\",\"乙\"]}");

            assertEquals("A. 甲；B. 乙", service.formatCorrect(q, "multi"));
            assertEquals("A,B", service.formatStudent(json("[0,1]"), "multi"));
        }

        @Test
        @DisplayName("判断题显示中文")
        void judge() throws Exception {
            assertEquals("正确", service.formatStudent(json("true"), "judge"));
            assertEquals("错误", service.formatStudent(json("false"), "judge"));
        }

        @Test
        @DisplayName("填空题空作答显示未作答")
        void blank() throws Exception {
            assertEquals("未作答", service.formatStudent(json("\"   \""), "blank"));
            assertEquals("final", service.formatStudent(json("\"final\""), "blank"));
        }

        @Test
        @DisplayName("题目没有标准答案时显示「无」")
        void noCorrectAnswer() throws Exception {
            assertEquals("无", service.formatCorrect(json("{\"type\":\"blank\"}"), "blank"));
        }
    }

    @Test
    @DisplayName("能自动判分的题型都得能渲染，否则错题本会出现空白条目")
    void everyObjectiveTypeRenders() throws Exception {
        String[] types = {"single", "multi", "judge", "blank", "prog_blank"};

        for (String type : types) {
            assertTrue(ObjectiveGrading.isObjective(type), type);
            String rendered = service.formatStudent(json("null"), type);
            assertEquals("未作答", rendered, type + " 未作答时应有可读文案");
        }
    }
}
