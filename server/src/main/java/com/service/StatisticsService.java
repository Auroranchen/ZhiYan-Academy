package com.service;

import com.mapper.CourseMapper;
import com.mapper.HomeworkMapper;
import com.mapper.StudentHomeworkMapper;
import com.model.Course;
import com.model.Homework;
import com.model.StudentHomework;
import com.util.ObjectiveGrading;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 统计服务层
 * 提供各类统计报表数据
 */
@Service
public class StatisticsService {

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private HomeworkMapper homeworkMapper;

    @Autowired
    private StudentHomeworkMapper studentHomeworkMapper;

    @Autowired
    private com.mapper.CourseClassMapper courseClassMapper;

    @Autowired
    private com.mapper.StudentMapper studentMapper;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 获取课程作业统计数据（柱状图）
     * 统计每个课程的作业数量
     * 
     * @return 统计数据列表
     */
    @Cacheable(value = "courseHomeworkStats", key = "'all'")
    public List<Map<String, Object>> getCourseHomeworkStats() {
        List<Map<String, Object>> result = new ArrayList<>();
        
        // 获取所有课程
        List<Course> courses = courseMapper.findAll();
        
        for (Course course : courses) {
            Map<String, Object> item = new HashMap<>();
            item.put("coursename", course.getCoursename());
            
            // 统计该课程的作业数量
            List<Homework> homeworks = homeworkMapper.findByCourseid(course.getCourseid());
            item.put("homeworkCount", homeworks != null ? homeworks.size() : 0);
            
            result.add(item);
        }
        
        return result;
    }

    public Map<String, Object> getHomeworkErrorRate(Integer homeworkid) {
        Map<String, Object> result = new HashMap<>();
        if (homeworkid == null) {
            result.put("questions", Collections.emptyList());
            result.put("overallAttempts", 0);
            result.put("overallCorrect", 0);
            result.put("overallWrong", 0);
            result.put("overallErrorRate", BigDecimal.ZERO);
            return result;
        }
        Homework homework = homeworkMapper.findById(homeworkid);
        if (homework == null || homework.getContent() == null) {
            result.put("questions", Collections.emptyList());
            result.put("overallAttempts", 0);
            result.put("overallCorrect", 0);
            result.put("overallWrong", 0);
            result.put("overallErrorRate", BigDecimal.ZERO);
            return result;
        }
        JsonNode questionsNode;
        try {
            JsonNode hwNode = OBJECT_MAPPER.readTree(homework.getContent());
            questionsNode = hwNode.path("questions");
            if (!questionsNode.isArray()) {
                result.put("questions", Collections.emptyList());
                result.put("overallAttempts", 0);
                result.put("overallCorrect", 0);
                result.put("overallWrong", 0);
                result.put("overallErrorRate", BigDecimal.ZERO);
                return result;
            }
        } catch (Exception e) {
            result.put("questions", Collections.emptyList());
            result.put("overallAttempts", 0);
            result.put("overallCorrect", 0);
            result.put("overallWrong", 0);
            result.put("overallErrorRate", BigDecimal.ZERO);
            return result;
        }
        List<StudentHomework> submissions = studentHomeworkMapper.findByHomeworkid(homeworkid, null, 0, 10000);
        Map<String, Map<String, Object>> perQuestion = new LinkedHashMap<>();
        for (JsonNode q : questionsNode) {
            String qid = q.path("id").asText();
            String title = q.path("title").asText();
            String type = q.path("type").asText();
            if (!ObjectiveGrading.isObjective(type)) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("id", qid);
            item.put("title", title);
            item.put("type", type);
            item.put("attempts", 0);
            item.put("correct", 0);
            item.put("wrong", 0);
            perQuestion.put(qid, item);
        }
        int overallAttempts = 0;
        int overallCorrect = 0;
        int overallWrong = 0;
        for (StudentHomework sh : submissions) {
            if (sh.getContent() == null || sh.getContent().trim().isEmpty()) {
                continue;
            }
            JsonNode ansNode;
            try {
                JsonNode stu = OBJECT_MAPPER.readTree(sh.getContent());
                ansNode = stu.path("answers");
                if (!ansNode.isObject()) {
                    continue;
                }
            } catch (Exception e) {
                continue;
            }
            for (JsonNode q : questionsNode) {
                String qid = q.path("id").asText();
                String type = q.path("type").asText();
                if (!perQuestion.containsKey(qid)) {
                    continue;
                }
                JsonNode stuAns = ansNode.get(qid);
                if (stuAns == null || stuAns.isNull()) {
                    continue;
                }
                Map<String, Object> item = perQuestion.get(qid);
                int attempts = (Integer) item.get("attempts");
                item.put("attempts", attempts + 1);
                overallAttempts++;
                boolean correct = ObjectiveGrading.isCorrect(q, stuAns);
                if (correct) {
                    int c = (Integer) item.get("correct");
                    item.put("correct", c + 1);
                    overallCorrect++;
                } else {
                    int w = (Integer) item.get("wrong");
                    item.put("wrong", w + 1);
                    overallWrong++;
                }
            }
        }
        List<Map<String, Object>> questionList = new ArrayList<>();
        for (Map<String, Object> item : perQuestion.values()) {
            int attempts = (Integer) item.get("attempts");
            int wrong = (Integer) item.get("wrong");
            BigDecimal rate = attempts > 0
                    ? new BigDecimal(wrong * 100.0 / attempts).setScale(2, BigDecimal.ROUND_HALF_UP)
                    : BigDecimal.ZERO;
            item.put("errorRate", rate);
            questionList.add(item);
        }
        BigDecimal overallErrorRate = overallAttempts > 0
                ? new BigDecimal(overallWrong * 100.0 / overallAttempts).setScale(2, BigDecimal.ROUND_HALF_UP)
                : BigDecimal.ZERO;
        result.put("questions", questionList);
        result.put("overallAttempts", overallAttempts);
        result.put("overallCorrect", overallCorrect);
        result.put("overallWrong", overallWrong);
        result.put("overallErrorRate", overallErrorRate);
        return result;
    }

    /**
     * 获取学生成绩统计数据（水平条形图）
     * 统计每个学生的平均成绩
     * 
     * @param teacherid 教师ID（可选，用于筛选教师任教课程的学生）
     * @param courseid 课程ID（可选，用于筛选指定课程的学生成绩）
     * @param semester 学期（可选，用于筛选指定学期的成绩）
     * @return 统计数据列表
     */
    public List<Map<String, Object>> getStudentScoreStats(Integer teacherid, Integer courseid, String semester) {
        List<Map<String, Object>> result = new ArrayList<>();
        
        // 获取所有学生作业（已批改的）
        List<StudentHomework> allHomeworks = studentHomeworkMapper.findList(null, 0, 10000);
        
        // 按学生分组统计
        Map<String, List<BigDecimal>> studentScores = new HashMap<>();
        Map<String, String> studentNames = new HashMap<>();
        
        for (StudentHomework sh : allHomeworks) {
            if (sh.getStatus() == 2 && sh.getScore() != null) {
                Homework homework = homeworkMapper.findById(sh.getHomeworkid());
                if (homework == null) {
                    continue;
                }
                
                // 如果指定了课程ID，需要过滤
                if (courseid != null && !courseid.equals(homework.getCourseid())) {
                    continue;
                }
                
                // 如果指定了教师ID，需要过滤
                if (teacherid != null && !teacherid.equals(homework.getTeacherid())) {
                    continue;
                }

                // 如果指定了学期，需要过滤（兼容历史数据：作业semester为空时用课程semester判断）
                if (semester != null && !semester.isEmpty()) {
                    String hwSem = homework.getSemester();
                    if (hwSem == null || hwSem.isEmpty() || !hwSem.equals(semester)) {
                        Course course = courseMapper.findById(homework.getCourseid());
                        String courseSem = course != null ? course.getSemester() : null;
                        if (courseSem == null || !courseSem.equals(semester)) {
                            continue;
                        }
                    }
                }
                
                String key = String.valueOf(sh.getStudentid());
                List<BigDecimal> scores = studentScores.computeIfAbsent(key, k -> new ArrayList<>());
                scores.add(sh.getScore());
                
                if (sh.getStudentname() != null) {
                    studentNames.put(key, sh.getStudentname());
                }
            }
        }
        
        // 计算平均分并构建结果
        for (Map.Entry<String, List<BigDecimal>> entry : studentScores.entrySet()) {
            String studentIdStr = entry.getKey();
            List<BigDecimal> scores = entry.getValue();
            
            BigDecimal sum = BigDecimal.ZERO;
            for (BigDecimal s : scores) {
                sum = sum.add(s);
            }
            
            BigDecimal avg = sum.divide(new BigDecimal(scores.size()), 2, BigDecimal.ROUND_HALF_UP);
            
            Map<String, Object> item = new HashMap<>();
            item.put("studentid", studentIdStr);
            item.put("studentname", studentNames.getOrDefault(studentIdStr, "未知"));
            item.put("avgScore", avg);
            item.put("homeworkCount", scores.size());
            
            result.add(item);
        }
        
        // 按平均分降序排序
        result.sort((a, b) -> {
            BigDecimal s1 = (BigDecimal) a.get("avgScore");
            BigDecimal s2 = (BigDecimal) b.get("avgScore");
            return s2.compareTo(s1);
        });
        
        return result;
    }


    /**
     * 获取成绩区间统计数据（饼图）
     * 统计各成绩区间的人数
     * 
     * @param teacherid 教师ID（可选，用于筛选教师任教课程的学生）
     * @param courseid 课程ID（可选，用于筛选指定课程的成绩分布）
     * @param semester 学期（可选，用于筛选指定学期的成绩）
     * @return 统计数据列表
     */
    public List<Map<String, Object>> getScoreRangeStats(Integer teacherid, Integer courseid, String semester) {
        List<Map<String, Object>> result = new ArrayList<>();
        
        // 初始化成绩区间
        int[] ranges = {0, 0, 0, 0, 0}; // 不及格、及格、中等、良好、优秀
        String[] rangeNames = {"不及格(0-59)", "及格(60-69)", "中等(70-79)", "良好(80-89)", "优秀(90-100)"};
        
        // 获取所有学生作业（已批改的）
        List<StudentHomework> allHomeworks = studentHomeworkMapper.findList(null, 0, 10000);
        
        for (StudentHomework sh : allHomeworks) {
            if (sh.getStatus() == 2 && sh.getScore() != null) {
                Homework homework = homeworkMapper.findById(sh.getHomeworkid());
                if (homework == null) {
                    continue;
                }
                
                // 如果指定了课程ID，需要过滤
                if (courseid != null && !courseid.equals(homework.getCourseid())) {
                    continue;
                }
                
                // 如果指定了教师ID，需要过滤
                if (teacherid != null && !teacherid.equals(homework.getTeacherid())) {
                    continue;
                }

                // 如果指定了学期，需要过滤（兼容历史数据：作业semester为空时用课程semester判断）
                if (semester != null && !semester.isEmpty()) {
                    String hwSem = homework.getSemester();
                    if (hwSem == null || hwSem.isEmpty() || !hwSem.equals(semester)) {
                        Course course = courseMapper.findById(homework.getCourseid());
                        String courseSem = course != null ? course.getSemester() : null;
                        if (courseSem == null || !courseSem.equals(semester)) {
                            continue;
                        }
                    }
                }
                
                int score = sh.getScore().intValue();
                if (score < 60) {
                    ranges[0]++;
                } else if (score < 70) {
                    ranges[1]++;
                } else if (score < 80) {
                    ranges[2]++;
                } else if (score < 90) {
                    ranges[3]++;
                } else {
                    ranges[4]++;
                }
            }
        }
        
        // 构建结果
        for (int i = 0; i < rangeNames.length; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("name", rangeNames[i]);
            item.put("value", ranges[i]);
            result.add(item);
        }
        
        return result;
    }

    /**
     * 获取教师课程统计数据
     * 统计教师任教课程的作业和提交情况
     * 
     * @param teacherid 教师ID
     * @return 统计数据列表
     */
    public List<Map<String, Object>> getTeacherCourseStats(Integer teacherid) {
        List<Map<String, Object>> result = new ArrayList<>();
        
        if (teacherid == null) {
            return result;
        }
        
        // 获取教师的课程
        List<Course> courses = courseMapper.findByTeacherId(teacherid,null);
        
        for (Course course : courses) {
            Map<String, Object> item = new HashMap<>();
            item.put("coursename", course.getCoursename());
            
            // 统计该课程的作业数量
            List<Homework> homeworks = homeworkMapper.findByCourseid(course.getCourseid());
            int homeworkCount = homeworks != null ? homeworks.size() : 0;
            item.put("homeworkCount", homeworkCount);
            
            // 统计提交数量和批改数量
            int submitCount = 0;
            int gradeCount = 0;
            if (homeworks != null) {
                for (Homework hw : homeworks) {
                    List<StudentHomework> submissions = studentHomeworkMapper.findByHomeworkid(
                            hw.getHomeworkid(), null, 0, 10000);
                    if (submissions != null) {
                        for (StudentHomework sh : submissions) {
                            if (sh.getStatus() >= 1) {
                                submitCount++;
                            }
                            if (sh.getStatus() == 2) {
                                gradeCount++;
                            }
                        }
                    }
                }
            }
            item.put("submitCount", submitCount);
            item.put("gradeCount", gradeCount);
            
            result.add(item);
        }
        
        return result;
    }

    /**
     * 教师统计仪表盘：核心指标 + 各作业成绩趋势
     *
     * @param teacherid 教师ID
     * @param courseid 课程ID（可选）
     * @param semester 学期（可选，传 null 或 "ALL" 表示全部）
     * @return 仪表盘数据
     */
    public Map<String, Object> getTeacherDashboard(Integer teacherid, Integer courseid, String semester) {
        Map<String, Object> result = new HashMap<>();
        boolean filterSemester = semester != null && !semester.isEmpty() && !"ALL".equalsIgnoreCase(semester);

        // 1. 教师课程（按课程/学期过滤）
        List<Course> courses = courseMapper.findByTeacherId(teacherid, null);
        if (courses == null) courses = Collections.emptyList();
        List<Course> filtered = new ArrayList<>();
        for (Course c : courses) {
            if (courseid != null && !courseid.equals(c.getCourseid())) continue;
            if (filterSemester && (c.getSemester() == null || !c.getSemester().equals(semester))) continue;
            filtered.add(c);
        }

        // 2. 覆盖学生数：课程关联班级（去重）的学生总数
        Set<Integer> classIds = new LinkedHashSet<>();
        for (Course c : filtered) {
            List<Integer> ids = courseClassMapper.findClassIdsByCourseId(c.getCourseid());
            if (ids != null) classIds.addAll(ids);
        }
        long studentCount = 0;
        for (Integer cid : classIds) {
            Long n = studentMapper.countByClassid(cid);
            if (n != null) studentCount += n;
        }

        // 3. 作业与提交情况
        int homeworkCount = 0;
        long expected = 0;      // 应交份数（每个作业 × 覆盖学生数）
        int submitted = 0;      // 已提交（含已批改）
        int pendingGrade = 0;   // 待批改
        int gradedCount = 0;
        BigDecimal totalScore = BigDecimal.ZERO;
        List<Map<String, Object>> trend = new ArrayList<>();

        for (Course c : filtered) {
            List<Homework> homeworks = homeworkMapper.findByCourseid(c.getCourseid());
            if (homeworks == null) continue;
            for (Homework hw : homeworks) {
                homeworkCount++;
                List<StudentHomework> submissions = studentHomeworkMapper.findByHomeworkid(hw.getHomeworkid(), null, 0, 10000);
                if (submissions == null) submissions = Collections.emptyList();

                int hwGraded = 0;
                BigDecimal hwSum = BigDecimal.ZERO;
                BigDecimal hwMax = null;
                BigDecimal hwMin = null;
                for (StudentHomework sh : submissions) {
                    if (sh.getStatus() != null && sh.getStatus() >= 1) submitted++;
                    if (sh.getStatus() != null && sh.getStatus() == 1) pendingGrade++;
                    if (sh.getStatus() != null && sh.getStatus() == 2 && sh.getScore() != null) {
                        gradedCount++;
                        totalScore = totalScore.add(sh.getScore());
                        hwGraded++;
                        hwSum = hwSum.add(sh.getScore());
                        if (hwMax == null || sh.getScore().compareTo(hwMax) > 0) hwMax = sh.getScore();
                        if (hwMin == null || sh.getScore().compareTo(hwMin) < 0) hwMin = sh.getScore();
                    }
                }

                // 趋势点：仅统计有批改成绩的作业
                if (hwGraded > 0) {
                    Map<String, Object> point = new HashMap<>();
                    point.put("title", hw.getTitle());
                    point.put("coursename", c.getCoursename());
                    point.put("avg", hwSum.divide(new BigDecimal(hwGraded), 2, BigDecimal.ROUND_HALF_UP));
                    point.put("max", hwMax);
                    point.put("min", hwMin);
                    point.put("gradedCount", hwGraded);
                    point.put("time", hw.getCreatetime() != null ? hw.getCreatetime() : hw.getDeadline());
                    trend.add(point);
                }
            }
            expected += (long) (homeworks.size()) * studentCount;
        }

        // 按发布时间升序，最多保留最近 12 次
        trend.sort((a, b) -> {
            Date ta = (Date) a.get("time");
            Date tb = (Date) b.get("time");
            if (ta == null) return -1;
            if (tb == null) return 1;
            return ta.compareTo(tb);
        });
        if (trend.size() > 12) {
            trend = trend.subList(trend.size() - 12, trend.size());
        }

        BigDecimal avgScore = gradedCount > 0
                ? totalScore.divide(new BigDecimal(gradedCount), 2, BigDecimal.ROUND_HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal submitRate = expected > 0
                ? new BigDecimal(submitted * 100.0 / expected).setScale(1, BigDecimal.ROUND_HALF_UP)
                : BigDecimal.ZERO;
        if (submitRate.compareTo(new BigDecimal(100)) > 0) submitRate = new BigDecimal(100);

        result.put("studentCount", studentCount);
        result.put("courseCount", filtered.size());
        result.put("homeworkCount", homeworkCount);
        result.put("submitRate", submitRate);
        result.put("avgScore", avgScore);
        result.put("pendingGrade", pendingGrade);
        result.put("trend", trend);
        return result;
    }

    /**
     * 获取首页统计概览数据
     * 
     * @return 统计数据
     */
    @Cacheable(value = "overviewStats", key = "'all'")
    public Map<String, Object> getOverviewStats() {
        Map<String, Object> result = new HashMap<>();
        
        // 课程总数
        result.put("courseCount", courseMapper.count(null));
        
        // 作业总数
        result.put("homeworkCount", homeworkMapper.count(null));
        
        // 学生作业提交总数
        result.put("submitCount", studentHomeworkMapper.count(null));
        
        return result;
    }

    /**
     * 获取学生个人统计数据
     * 
     * @param studentid 学生ID
     * @param semester 学期
     * @return 统计数据 map
     */
    public Map<String, Object> getStudentPersonalStats(Integer studentid, String semester) {
        Map<String, Object> result = new HashMap<>();
        
        // 1. 获取该学生的所有作业（带学期筛选）
        List<StudentHomework> submissions = studentHomeworkMapper.findByStudentid(studentid, null, semester, 0, 1000);
        
        // 2. 统计数据
        int submittedCount = 0;
        int gradedCount = 0;
        BigDecimal totalScore = BigDecimal.ZERO;
        
        // 课程维度的统计
        Map<String, List<BigDecimal>> courseScores = new HashMap<>();
        // 作业趋势（取最近10次已批改作业）
        List<Map<String, Object>> scoreTrend = new ArrayList<>();
        
        for (StudentHomework sh : submissions) {
            // 状态：1已提交，2已批改
            if (sh.getStatus() >= 1) {
                submittedCount++;
            }
            
            if (sh.getStatus() == 2 && sh.getScore() != null) {
                gradedCount++;
                totalScore = totalScore.add(sh.getScore());
                
                // 按课程收集成绩
                String courseName = sh.getCoursename();
                if (courseName != null) {
                    courseScores.computeIfAbsent(courseName, k -> new ArrayList<>()).add(sh.getScore());
                }
                
                // 趋势数据
                Map<String, Object> trendItem = new HashMap<>();
                trendItem.put("title", sh.getHomeworktitle());
                trendItem.put("score", sh.getScore());
                trendItem.put("date", sh.getGradetime());
                scoreTrend.add(trendItem);
            }
        }
        
        // 计算平均分
        BigDecimal avgScore = gradedCount > 0 
            ? totalScore.divide(new BigDecimal(gradedCount), 2, BigDecimal.ROUND_HALF_UP) 
            : BigDecimal.ZERO;
            
        // 计算各课程平均分
        List<Map<String, Object>> courseAvgScores = new ArrayList<>();
        for (Map.Entry<String, List<BigDecimal>> entry : courseScores.entrySet()) {
            List<BigDecimal> scores = entry.getValue();
            BigDecimal sum = BigDecimal.ZERO;
            for (BigDecimal s : scores) sum = sum.add(s);
            BigDecimal avg = sum.divide(new BigDecimal(scores.size()), 2, BigDecimal.ROUND_HALF_UP);
            
            Map<String, Object> item = new HashMap<>();
            item.put("name", entry.getKey());
            item.put("value", avg);
            courseAvgScores.add(item);
        }
        
        // 整理结果
        result.put("submittedCount", submittedCount);
        result.put("gradedCount", gradedCount);
        result.put("avgScore", avgScore);
        result.put("courseScores", courseAvgScores);
        
        // 趋势数据按时间倒序，取前10，然后反转为正序显示
        if (scoreTrend.size() > 10) {
            scoreTrend = scoreTrend.subList(0, 10);
        }
        Collections.reverse(scoreTrend);
        result.put("scoreTrend", scoreTrend);
        
        return result;
    }
}
