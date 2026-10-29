package com.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapper.AiExplainMapper;
import com.mapper.HomeworkMapper;
import com.mapper.StudentHomeworkMapper;
import com.model.AiExplainLog;
import com.model.Homework;
import com.model.StudentFavorite;
import com.model.StudentHomework;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class AiExplainService {

    // 方法内已有名为 log 的 AiExplainLog 局部变量，日志器改用 LOGGER 以免混淆
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(AiExplainService.class);

    @Autowired
    private AiExplainMapper aiExplainMapper;

    @Autowired
    private StudentHomeworkMapper studentHomeworkMapper;

    @Autowired
    private HomeworkMapper homeworkMapper;

    @Autowired
    private ZhipuAIService zhipuAIService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 创建一个固定大小的线程池用于并发处理AI请求
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    // 提交状态与受限讲解的判定口径已上移到 AiAccessPolicy，
    // 与作业辅导对话（HomeworkChatPolicyResolver）共用同一定义，避免两处口径漂移。

    /**
     * 获取作业讲解
     * @param requesterStudentId 当前学生（可空）
     * @param privileged 教师/管理员可查看任意记录
     */
    public List<Map<String, Object>> getExplain(Integer studentHomeworkId, Integer requesterStudentId, boolean privileged) {
        StudentHomework sh = studentHomeworkMapper.findById(studentHomeworkId);
        if (sh == null) return null;
        if (!privileged) {
            if (requesterStudentId == null || !requesterStudentId.equals(sh.getStudentid())) {
                throw new SecurityException("无权查看该作业讲解");
            }
        }
        Homework homework = homeworkMapper.findById(sh.getHomeworkid());
        if (homework == null) return null;

        try {
            // 2. 解析题目和答案
            if (homework.getContent() == null || homework.getContent().trim().isEmpty()) {
                LOGGER.warn("作业 {} 没有题目内容，无法生成讲解", homework.getHomeworkid());
                return null;
            }

            JsonNode homeworkNode = null;
            try {
                homeworkNode = objectMapper.readTree(homework.getContent());
            } catch (Exception e) {
                LOGGER.warn("作业 {} 的题目内容不是合法 JSON: {}", homework.getHomeworkid(), e.getMessage());
            }

            JsonNode questionsNode = null;
            if (homeworkNode != null) {
                questionsNode = homeworkNode.get("questions");
                if (questionsNode == null && homeworkNode.isArray()) {
                    questionsNode = homeworkNode;
                }
            }
            
            // 构造默认题目
            if (questionsNode == null || !questionsNode.isArray()) {
                Map<String, Object> defaultQuestion = new HashMap<>();
                defaultQuestion.put("id", "q1");
                defaultQuestion.put("title", homework.getContent()); 
                defaultQuestion.put("type", "essay");
                defaultQuestion.put("score", 100);
                questionsNode = objectMapper.valueToTree(Collections.singletonList(defaultQuestion));
            }
            
            // 解析学生答案
            Map<String, Object> studentAnswers = new HashMap<>();
            List<String> answerKeyOrder = new ArrayList<>(); // 保存答案key的顺序
            String rawStudentContent = sh.getContent();
            if (rawStudentContent != null && !rawStudentContent.isEmpty()) {
                try {
                     JsonNode shNode = objectMapper.readTree(rawStudentContent);
                     if (shNode.has("answers")) {
                         studentAnswers = objectMapper.convertValue(shNode.get("answers"), new TypeReference<Map<String, Object>>(){});
                         // 记录key顺序
                         Iterator<String> fieldNames = shNode.get("answers").fieldNames();
                         while (fieldNames.hasNext()) {
                             answerKeyOrder.add(fieldNames.next());
                         }
                     } else if (shNode.isObject()) {
                         studentAnswers = objectMapper.convertValue(shNode, new TypeReference<Map<String, Object>>(){});
                     } else {
                         studentAnswers.put("q1", rawStudentContent);
                     }
                } catch (Exception e) {
                    studentAnswers.put("q1", rawStudentContent);
                }
            } else {
                studentAnswers.put("q1", "未提交内容");
            }
            
            // 构建按位置索引的答案映射（用于key不匹配时的回退）
            final Map<Integer, Object> indexedAnswers = new HashMap<>();
            int ansIdx = 0;
            for (String key : answerKeyOrder) {
                indexedAnswers.put(ansIdx++, studentAnswers.get(key));
            }

            // 3. 并发处理每个题目
            List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
            if (questionsNode != null && questionsNode.isArray()) {
                final Map<String, Object> finalStudentAnswers = studentAnswers;
                int questionIndex = 0;
                for (JsonNode q : questionsNode) {
                    final int qIdx = questionIndex++;
                    futures.add(CompletableFuture.supplyAsync(() -> processSingleQuestion(q, homework, sh, finalStudentAnswers, indexedAnswers, qIdx), executorService));
                }
            }

            // 等待所有任务完成并收集结果
            return futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            LOGGER.error("批量生成题目讲解失败", e);
            return null;
        }
    }

    private Map<String, Object> processSingleQuestion(JsonNode q, Homework homework, StudentHomework sh, Map<String, Object> studentAnswers, Map<Integer, Object> indexedAnswers, int questionIndex) {
        try {
            // 安全获取字段
            String qId = q.has("id") ? q.get("id").asText() : UUID.randomUUID().toString();
            String qTitle = q.has("title") ? q.get("title").asText() : "无标题";
            String qType = q.has("type") ? q.get("type").asText() : "unknown";
            
            // 获取正确答案
            String correctAnswer = "";
            if (q.has("answer")) {
                JsonNode ansNode = q.get("answer");
                correctAnswer = ansNode.isContainerNode() ? ansNode.toString() : ansNode.asText();
            }
            
            // 获取学生答案（多种key匹配策略）
            Object stuAnsObj = studentAnswers.get(qId);
            if (stuAnsObj == null && !studentAnswers.isEmpty()) {
                LOGGER.debug("学生答案 key 不匹配，题目 {}，可用 key: {}", qId, studentAnswers.keySet());
            }
            // 回退策略1：尝试用数字索引匹配（如 "0", "1", "2"）
            if (stuAnsObj == null) {
                for (Map.Entry<String, Object> entry : studentAnswers.entrySet()) {
                    String key = entry.getKey();
                    // 如果qId包含key或key包含qId（模糊匹配）
                    if (key.contains(qId) || qId.contains(key)) {
                        stuAnsObj = entry.getValue();
                        break;
                    }
                }
            }
            // 回退策略2：如果只有一个题目且只有一个答案，直接匹配
            if (stuAnsObj == null && studentAnswers.size() == 1) {
                stuAnsObj = studentAnswers.values().iterator().next();
            }
            // 回退策略3：按题目顺序索引匹配（"q1"→第0个答案）
            if (stuAnsObj == null) {
                // 尝试从qId提取数字索引
                String numPart = qId.replaceAll("[^0-9]", "");
                if (!numPart.isEmpty()) {
                    stuAnsObj = studentAnswers.get(numPart);
                }
            }
            // 回退策略4：按题目在试卷中的位置索引匹配
            if (stuAnsObj == null && indexedAnswers.containsKey(questionIndex)) {
                stuAnsObj = indexedAnswers.get(questionIndex);
                if (stuAnsObj != null) {
                    LOGGER.debug("通过位置索引匹配到学生答案，题目索引 {}", questionIndex);
                }
            }
            String studentAnswer = stuAnsObj != null ? stuAnsObj.toString() : "未作答";

            boolean restrictedExplain = AiAccessPolicy.useRestrictedExplain(sh, studentAnswer);
            String explainCacheVersion = restrictedExplain ? "v2.0-r" : "v2.0-f";

            // 检查缓存（受限/完整分列版本，避免作答状态变化后继续误用旧讲解）
            AiExplainLog cache = null;
            try {
                cache = aiExplainMapper.findCache(homework.getHomeworkid(), sh.getStudentid(), qId, explainCacheVersion);
            } catch (Exception cacheEx) {
                LOGGER.warn("讲解缓存查询失败，回退到基础查询: {}", cacheEx.getMessage());
                try {
                    cache = aiExplainMapper.findCacheBasic(homework.getHomeworkid(), sh.getStudentid(), qId, explainCacheVersion);
                } catch (Exception basicEx) {
                    LOGGER.warn("基础缓存查询也失败，本次将重新生成讲解: {}", basicEx.getMessage());
                }
            }
            
            Map<String, Object> explainData;
            if (cache != null) {
                // 命中缓存
                explainData = new HashMap<>();
                explainData.put("keyPoints", cache.getKeyPoints());
                explainData.put("errorAnalysis", cache.getErrorAnalysis());
                try {
                    explainData.put("steps", objectMapper.readValue(cache.getSteps(), List.class));
                    explainData.put("expansion", objectMapper.readValue(cache.getExpansion(), Map.class));
                    // 新增字段：参考代码和常见错误
                    if (cache.getReferenceCode() != null && !cache.getReferenceCode().isEmpty()) {
                        explainData.put("referenceCode", cache.getReferenceCode());
                    }
                    if (cache.getCommonMistakes() != null && !cache.getCommonMistakes().isEmpty()) {
                        try {
                            explainData.put("commonMistakes", objectMapper.readValue(cache.getCommonMistakes(), List.class));
                        } catch (Exception ignored) {}
                    }
                } catch (Exception e) {
                    // 缓存解析失败，视为未命中
                    explainData = null;
                }
                if (explainData != null) {
                    explainData.put("isCache", true);
                    explainData.put("explainRestricted", restrictedExplain);
                    explainData.put("explainPolicy", restrictedExplain ? "restricted" : "full");
                }
            } else {
                explainData = null;
            }

            if (explainData == null) {
                // 未命中，调用AI
                long startTime = System.currentTimeMillis();

                Map<String, Object> qInfo = new HashMap<>();
                qInfo.put("title", qTitle);
                qInfo.put("type", qType);
                qInfo.put("options", q.get("options"));
                if (restrictedExplain) {
                    qInfo.put("correctAnswer", "");
                    qInfo.put("explainPolicy", "restricted");
                    qInfo.put("policyNotice", "本题处于题库/未成稿作答保护：禁止向 AI 索要可直接照抄的答案。");
                } else {
                    qInfo.put("correctAnswer", correctAnswer);
                    qInfo.put("explainPolicy", "full");
                }
                qInfo.put("studentAnswer", studentAnswer);
                qInfo.put("homeworkSubmitted", AiAccessPolicy.homeworkSubmitted(sh));

                String aiJson = zhipuAIService.explainQuestion(objectMapper.writeValueAsString(qInfo), restrictedExplain);
                
                if (aiJson != null) {
                    try {
                        explainData = objectMapper.readValue(aiJson, Map.class);
                        
                        // 存入日志/缓存
                        AiExplainLog log = new AiExplainLog();
                        log.setHomeworkId(homework.getHomeworkid());
                        log.setStudentId(sh.getStudentid());
                        log.setQuestionId(qId);
                        log.setKeyPoints((String) explainData.get("keyPoints"));
                        log.setErrorAnalysis((String) explainData.get("errorAnalysis"));
                        log.setSteps(objectMapper.writeValueAsString(explainData.get("steps")));
                        log.setExpansion(objectMapper.writeValueAsString(explainData.get("expansion")));
                        // 新增字段
                        Object refCode = explainData.get("referenceCode");
                        if (refCode != null) {
                            log.setReferenceCode(String.valueOf(refCode));
                        }
                        Object mistakes = explainData.get("commonMistakes");
                        if (mistakes != null) {
                            log.setCommonMistakes(objectMapper.writeValueAsString(mistakes));
                        }
                        log.setVersion(explainCacheVersion);
                        log.setDuration((int)(System.currentTimeMillis() - startTime));
                        log.setIsCacheHit(0);
                        
                        try {
                            aiExplainMapper.insertLog(log);
                        } catch (Exception insertEx) {
                            // 插入失败通常是库里缺少较新的字段，退回基础插入
                            LOGGER.warn("讲解缓存写入失败，回退到基础插入: {}", insertEx.getMessage());
                            try {
                                aiExplainMapper.insertLogBasic(log);
                            } catch (Exception retryEx) {
                                LOGGER.warn("基础插入也失败，本次讲解不缓存: {}", retryEx.getMessage());
                            }
                        }
                        explainData.put("isCache", false);
                    } catch (Exception e) {
                        LOGGER.error("AI 讲解返回内容解析失败，原始响应: {}", aiJson, e);
                        explainData = null;
                    }
                }
            }
            
            if (explainData != null) {
                explainData.put("questionId", qId);
                explainData.put("questionTitle", qTitle);
                explainData.put("questionType", qType);
                explainData.put("score", q.has("score") ? q.get("score").asInt() : 0);
                if (restrictedExplain) {
                    explainData.put("correctAnswer", null);
                    explainData.put("correctAnswerMasked", Boolean.TRUE);
                } else {
                    explainData.put("correctAnswer", correctAnswer);
                    explainData.put("correctAnswerMasked", Boolean.FALSE);
                }
                explainData.put("studentAnswer", studentAnswer);
                explainData.put("explainRestricted", restrictedExplain);
                explainData.put("explainPolicy", restrictedExplain ? "restricted" : "full");
                // 返回选项列表（选择题需要在前端展示A/B/C/D）
                if (q.has("options") && q.get("options").isArray()) {
                    List<String> optionsList = new ArrayList<>();
                    for (JsonNode opt : q.get("options")) {
                        optionsList.add(opt.asText());
                    }
                    explainData.put("options", optionsList);
                }
                explainData.put("isFavorite", aiExplainMapper.checkFavorite(sh.getStudentid(), qId) > 0);
                return explainData;
            }
        } catch (Exception e) {
            LOGGER.error("生成单题讲解失败", e);
        }
        return null;
    }

    /**
     * 添加收藏
     */
    public boolean addFavorite(StudentFavorite favorite) {
        if (aiExplainMapper.checkFavorite(favorite.getStudentId(), favorite.getQuestionId()) > 0) {
            return true; // 已收藏
        }
        return aiExplainMapper.insertFavorite(favorite) > 0;
    }

    /** 当前学生的收藏列表 */
    public List<StudentFavorite> listFavorites(Integer studentId) {
        if (studentId == null) return List.of();
        List<StudentFavorite> list = aiExplainMapper.getFavorites(studentId);
        return list != null ? list : List.of();
    }

    /** 取消收藏（仅本人） */
    public boolean removeFavorite(Integer studentId, Integer favoriteId) {
        if (studentId == null || favoriteId == null) return false;
        return aiExplainMapper.deleteFavorite(favoriteId, studentId) > 0;
    }
}
