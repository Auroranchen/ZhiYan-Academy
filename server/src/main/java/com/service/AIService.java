package com.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * AI服务 - 调用智谱AI进行试题识别
 * 委托给 ZhipuAIService 统一管理API配置和HTTP调用
 */
@Service
public class AIService {

    @Autowired
    private ZhipuAIService zhipuAIService;

    @Autowired
    private AiSafeCallService aiSafeCallService;

    private final ObjectMapper objectMapper;

    public AIService() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 识别试题文本，返回结构化JSON
     */
    public String parseQuestions(String text) throws IOException {
        String systemPrompt = "你是一个试题解析助手。请将用户输入的试题文本解析为JSON格式。\n\n" +
            "输出格式：\n" +
            "{\"success\":true,\"questions\":[{\"type\":\"类型\",\"title\":\"纯题干\",\"score\":5,\"options\":[],\"answer\":null}],\"message\":\"成功识别X道试题\"}\n\n" +
            "【极其重要】title字段要求：\n" +
            "1. 只放纯题干文字，绝对不能包含任何选项内容(A/B/C/D开头的内容)\n" +
            "2. 绝对不能包含答案标记，如：(A)、（B）、(√)、(×)、（对）、（错）等\n" +
            "3. 绝对不能包含答案符号，如：√、×、对、错\n" +
            "4. 题干末尾的括号答案必须删除\n\n" +
            "【示例】\n" +
            "输入：在Java中，final关键字修饰的变量只能被赋值一次。（√）\n" +
            "输出title：在Java中，final关键字修饰的变量只能被赋值一次。\n\n" +
            "输入：以下哪个是正确的？（C）A.选项1 B.选项2 C.选项3\n" +
            "输出title：以下哪个是正确的？\n" +
            "输出options：[\"选项1\",\"选项2\",\"选项3\"]\n\n" +
            "题型type：\n" +
            "- single: 单选题，answer为索引(A=0,B=1,C=2,D=3)\n" +
            "- multi: 多选题，answer为数组如[0,2]\n" +
            "- judge: 判断题，answer为true/false，options为[]\n" +
            "- short: 简答题，answer为参考答案字符串（如有参考答案则填写，无则为null），options为[]\n\n" +
            "判断题答案转换：√、对、正确、T->true；×、错、错误、F->false\n" +
            "只输出纯JSON，不要其他文字";

        String sanitized = AiAssistContentPolicy.sanitizeModelInput(text != null ? text : "");
        String sys = AiAssistContentPolicy.withJsonTaskBrief(systemPrompt);
        String userPrompt = "请解析以下试题（title中不要包含任何答案标记和选项）：\n\n" + sanitized;

        try {
            // 走安全网关：补上 ai_call_log 记录与输出过滤。
            // 用 callJsonRaw 而非 callText/callJson——本题需要保留上面 withJsonTaskBrief
            // 的「只输出结构化结果」要求，并且解析与错误话术都由本方法自己负责。
            String response = aiSafeCallService.callJsonRaw("ai.parseQuestions", sys, userPrompt);
            
            response = cleanJsonResponse(response);
            
            try {
                objectMapper.readTree(response);
            } catch (Exception e) {
                return "{\"success\":false,\"questions\":[],\"message\":\"AI返回格式异常，请重试\"}";
            }
            
            return response;
        } catch (Exception e) {
            throw new IOException("AI识别失败: " + e.getMessage());
        }
    }
    
    /**
     * 清理AI返回的JSON字符串
     */
    private String cleanJsonResponse(String response) {
        if (response == null) return "{}";
        
        String cleaned = response.trim();
        
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();
        
        int startIndex = cleaned.indexOf("{");
        int endIndex = cleaned.lastIndexOf("}");
        if (startIndex >= 0 && endIndex > startIndex) {
            cleaned = cleaned.substring(startIndex, endIndex + 1);
        }
        
        cleaned = cleaned.replace("\u201c", "\"").replace("\u201d", "\"");
        cleaned = cleaned.replace("\u2018", "'").replace("\u2019", "'");
        
        StringBuilder sb = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (escaped) {
                if (c == 'n' || c == 'r' || c == 't') {
                    sb.append('\\').append(c);
                } else if (c == '"' || c == '\\') {
                    sb.append('\\').append(c);
                } else {
                    sb.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                inString = !inString;
                sb.append(c);
            } else if (inString && (c == '\n' || c == '\r')) {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        
        return sb.toString();
    }
}
