package com.service;

import com.mapper.HomeworkMapper;
import com.mapper.StudentHomeworkMapper;
import com.model.Homework;
import com.model.Student;
import com.model.StudentHomework;
import com.response.Result;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 学生作业业务逻辑层
 */
@Service
public class StudentHomeworkService {

    private static final Logger logger = LoggerFactory.getLogger(StudentHomeworkService.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private StudentHomeworkMapper studentHomeworkMapper;

    @Autowired
    private HomeworkMapper homeworkMapper;

    @Autowired
    private ZhipuAIService zhipuAIService;

    @Autowired
    private ExamGradingService examGradingService;

    @Autowired
    private WordService wordService;

    @Autowired
    private FileService fileService;

    /**
     * 自身代理引用。@Async 依赖 Spring AOP 代理生效，
     * retryGrade 内部若用 this 直接调用异步方法会绕过代理、退化为同步阻塞，
     * 故通过 @Lazy 自注入拿到代理对象再调用。
     */
    @Lazy
    @Autowired
    private StudentHomeworkService self;

    /**
     * 分页查询学生作业列表（支持搜索）
     * 
     * @param keyword  搜索关键字（学生姓名或作业标题）
     * @param page     页码
     * @param pageSize 每页数量
     * @return 学生作业列表
     */
    public List<StudentHomework> getList(String keyword, Integer page, Integer pageSize) {
        int offset = (page - 1) * pageSize;
        if (keyword != null && keyword.trim().isEmpty()) {
            keyword = null;
        }
        return studentHomeworkMapper.findList(keyword, offset, pageSize);
    }

    /**
     * 获取学生作业总数（支持搜索条件）
     * 
     * @param keyword 搜索关键字
     * @return 总数
     */
    public Long getCount(String keyword) {
        if (keyword != null && keyword.trim().isEmpty()) {
            keyword = null;
        }
        return studentHomeworkMapper.count(keyword);
    }

    /**
     * 根据ID获取学生作业
     * 
     * @param id 学生作业ID
     * @return 学生作业对象
     */
    public StudentHomework getById(Integer id) {
        if (id == null) {
            return null;
        }
        return studentHomeworkMapper.findById(id);
    }

    /**
     * 根据作业ID分页获取学生提交列表
     * 
     * @param homeworkid 作业ID
     * @param keyword    搜索关键字
     * @param page       页码
     * @param pageSize   每页数量
     * @return 学生作业列表
     */
    public List<StudentHomework> getByHomeworkId(Integer homeworkid, String keyword, Integer page, Integer pageSize) {
        if (homeworkid == null) {
            return null;
        }
        int offset = (page - 1) * pageSize;
        if (keyword != null && keyword.trim().isEmpty()) {
            keyword = null;
        }
        return studentHomeworkMapper.findByHomeworkid(homeworkid, keyword, offset, pageSize);
    }

    /**
     * 根据作业ID获取学生提交总数
     * 
     * @param homeworkid 作业ID
     * @param keyword    搜索关键字
     * @return 总数
     */
    public Long getCountByHomeworkId(Integer homeworkid, String keyword) {
        if (homeworkid == null) {
            return 0L;
        }
        if (keyword != null && keyword.trim().isEmpty()) {
            keyword = null;
        }
        return studentHomeworkMapper.countByHomeworkid(homeworkid, keyword);
    }


    /**
     * 根据学生ID分页获取作业列表
     * 
     * @param studentid 学生ID
     * @param courseid  课程ID
     * @param semester  学期
     * @param page      页码
     * @param pageSize  每页数量
     * @return 学生作业列表
     */
    public List<StudentHomework> getByStudentId(Integer studentid, Integer courseid, String semester, Integer page, Integer pageSize) {
        if (studentid == null) {
            return null;
        }
        int offset = (page - 1) * pageSize;
        return studentHomeworkMapper.findByStudentid(studentid, courseid, semester, offset, pageSize);
    }

    /**
     * 根据学生ID获取作业总数
     * 
     * @param studentid 学生ID
     * @param courseid  课程ID
     * @param semester  学期
     * @return 总数
     */
    public Long getCountByStudentId(Integer studentid, Integer courseid, String semester) {
        if (studentid == null) {
            return 0L;
        }
        return studentHomeworkMapper.countByStudentid(studentid, courseid, semester);
    }

    /**
     * 根据作业ID和学生ID获取学生作业
     * 
     * @param homeworkid 作业ID
     * @param studentid  学生ID
     * @return 学生作业对象
     */
    public StudentHomework getByHomeworkAndStudent(Integer homeworkid, Integer studentid) {
        if (homeworkid == null || studentid == null) {
            return null;
        }
        return studentHomeworkMapper.findByHomeworkAndStudent(homeworkid, studentid);
    }

    /**
     * 提交作业（在线答题）
     * 
     * @param studentHomework 学生作业对象
     * @return 操作结果
     */
    public Result<StudentHomework> submit(StudentHomework studentHomework) {
        // 参数校验：作业ID不能为空
        if (studentHomework.getHomeworkid() == null) {
            return Result.error("作业ID不能为空");
        }
        
        // 参数校验：学生ID不能为空
        if (studentHomework.getStudentid() == null) {
            return Result.error("学生ID不能为空");
        }
        
        // 参数校验：作业内容不能为空
        if (studentHomework.getContent() == null || studentHomework.getContent().trim().isEmpty()) {
            return Result.error("作业内容不能为空");
        }
        
        // 检查作业是否存在
        Homework homework = homeworkMapper.findById(studentHomework.getHomeworkid());
        if (homework == null) {
            return Result.error("作业不存在");
        }
        
        // 检查作业类型是否为在线答题
        if (homework.getType() != 1) {
            return Result.error("该作业不支持在线答题");
        }
        
        // 检查是否已过截止时间
        if (homework.getDeadline() != null && new Date().after(homework.getDeadline())) {
            return Result.error("作业已过截止时间，无法提交");
        }

        // 同步判分，学生提交后立即看到分数和评语
        ExamGradingService.ExamGradeResult gradeResult =
                examGradingService.gradeExam(homework.getContent(), studentHomework.getContent());
        
        // 检查是否已提交过
        StudentHomework existing = studentHomeworkMapper.findByHomeworkAndStudent(
                studentHomework.getHomeworkid(), studentHomework.getStudentid());

        Result<Void> block = assertResubmitAllowed(existing);
        if (block != null) {
            return Result.error(block.getMsg());
        }
        
        if (existing != null) {
            // 已存在记录，更新（仅允许退回重交 status=3）
            existing.setContent(studentHomework.getContent());
            existing.setStatus(1);
            existing.setSubmittime(new Date());
            if (gradeResult != null) {
                existing.setScore(gradeResult.getTotalScore());
                existing.setComment(gradeResult.getComment());
                existing.setGradetime(new Date());
                existing.setGradingStatus(gradeResult.isRequiresReview() ? "REVIEW_REQUIRED" : "COMPLETED");
            }
            int rows = studentHomeworkMapper.updateSubmitWithGrade(existing);
            if (rows <= 0) {
                return Result.error("提交失败");
            }
        } else {
            // 新建记录
            studentHomework.setStatus(1);
            studentHomework.setSubmittime(new Date());
            if (gradeResult != null) {
                studentHomework.setScore(gradeResult.getTotalScore());
                studentHomework.setComment(gradeResult.getComment());
                studentHomework.setGradetime(new Date());
                studentHomework.setGradingStatus(gradeResult.isRequiresReview() ? "REVIEW_REQUIRED" : "COMPLETED");
            }
            int rows = studentHomeworkMapper.insert(studentHomework);
            if (rows <= 0) {
                return Result.error("提交失败");
            }
        }
        
        // 构建返回数据，让前端立即显示分数和评语
        StudentHomework resultData = new StudentHomework();
        if (gradeResult != null) {
            resultData.setScore(gradeResult.getTotalScore());
            resultData.setComment(gradeResult.getComment());
        }
        return Result.success(resultData);
    }

    /**
     * 异步补一次在线答题的完整判分。
     * 与 {@link #submit} 走的是同一套判分逻辑，只是多了状态流转与落库。
     */
    @Async("aiGradingExecutor")
    public void asyncExamAIGrade(Integer studentHomeworkId, String homeworkContent, String studentContent) {
        try {
            studentHomeworkMapper.updateGradingStatus(studentHomeworkId, "GRADING", null);

            ExamGradingService.ExamGradeResult result =
                    examGradingService.gradeExam(homeworkContent, studentContent);
            if (result == null) {
                studentHomeworkMapper.updateGradingStatus(studentHomeworkId, "FAILED", "评分数据解析失败");
                return;
            }

            StudentHomework gradeData = new StudentHomework();
            gradeData.setId(studentHomeworkId);
            gradeData.setScore(result.getTotalScore());
            gradeData.setComment(result.getComment());
            gradeData.setGradetime(new Date());
            gradeData.setGradingStatus(result.isRequiresReview() ? "REVIEW_REQUIRED" : "COMPLETED");
            studentHomeworkMapper.grade(gradeData);

            logger.info("在线答题AI评分完成，学生作业ID: {}, 总分: {}", studentHomeworkId, result.getTotalScore());
        } catch (Exception e) {
            logger.error("在线答题AI评分失败，学生作业ID: {}, 错误: {}", studentHomeworkId, e.getMessage(), e);
            studentHomeworkMapper.updateGradingStatus(studentHomeworkId, "FAILED",
                    "AI评分暂时不可用，请稍后刷新重试或等待教师手动批改");
        }
    }

    /**
     * 上传作业文件（文档上传）
     * 上传后自动读取Word内容并调用AI评分
     * 
     * @param studentHomework 学生作业对象
     * @return 操作结果
     */
    public Result<StudentHomework> upload(StudentHomework studentHomework) {
        // 参数校验：作业ID不能为空
        if (studentHomework.getHomeworkid() == null) {
            return Result.error("作业ID不能为空");
        }
        
        // 参数校验：学生ID不能为空
        if (studentHomework.getStudentid() == null) {
            return Result.error("学生ID不能为空");
        }
        
        // 参数校验：文件路径不能为空
        if (studentHomework.getFilepath() == null || studentHomework.getFilepath().trim().isEmpty()) {
            return Result.error("文件路径不能为空");
        }
        
        // 检查作业是否存在
        Homework homework = homeworkMapper.findById(studentHomework.getHomeworkid());
        if (homework == null) {
            return Result.error("作业不存在");
        }
        
        // 检查作业类型是否为文档上传
        if (homework.getType() != 2) {
            return Result.error("该作业不支持文档上传");
        }
        
        // 检查是否已过截止时间
        if (homework.getDeadline() != null && new Date().after(homework.getDeadline())) {
            return Result.error("作业已过截止时间，无法提交");
        }
        
        // 读取Word文档内容
        String wordContent = null;
        try {
            String absolutePath = fileService.getAbsolutePath(studentHomework.getFilepath());
            wordContent = wordService.readWordContent(absolutePath);
            studentHomework.setContent(wordContent); // 保存提取的文本内容
        } catch (Exception e) {
            // 读取失败不影响上传，但记录日志
            logger.warn("读取Word文档内容失败: {}", e.getMessage());
        }
        
        // 同步调用AI评分
        ZhipuAIService.GradeResult aiResult = null;
        if (wordContent != null && !wordContent.trim().isEmpty()) {
            try {
                aiResult = zhipuAIService.gradeHomework(homework.getTitle(), homework.getContent(), wordContent);
            } catch (Exception e) {
                logger.error("AI评分失败: {}", e.getMessage());
            }
        }
        
        // 检查是否已提交过
        StudentHomework existing = studentHomeworkMapper.findByHomeworkAndStudent(
                studentHomework.getHomeworkid(), studentHomework.getStudentid());

        Result<Void> block = assertResubmitAllowed(existing);
        if (block != null) {
            return Result.error(block.getMsg());
        }
        
        if (existing != null) {
            // 已存在记录，更新（仅允许退回重交 status=3）
            existing.setFilepath(studentHomework.getFilepath());
            existing.setContent(wordContent);
            existing.setStatus(1);
            existing.setSubmittime(new Date());
            if (aiResult != null) {
                existing.setScore(aiResult.getScore());
                existing.setComment(gradeComment(aiResult));
                existing.setGradetime(new Date());
                existing.setGradingStatus(gradeStatus(aiResult));
            }
            int rows = studentHomeworkMapper.update(existing);
            if (rows <= 0) {
                return Result.error("上传失败");
            }
        } else {
            // 新建记录
            studentHomework.setStatus(1);
            studentHomework.setSubmittime(new Date());
            if (aiResult != null) {
                studentHomework.setScore(aiResult.getScore());
                studentHomework.setComment(gradeComment(aiResult));
                studentHomework.setGradetime(new Date());
                studentHomework.setGradingStatus(gradeStatus(aiResult));
            }
            int rows = studentHomeworkMapper.insert(studentHomework);
            if (rows <= 0) {
                return Result.error("上传失败");
            }
        }
        
        // 构建返回数据，让前端立即显示分数和评语
        StudentHomework resultData = new StudentHomework();
        if (aiResult != null) {
            resultData.setScore(aiResult.getScore());
            resultData.setComment(gradeComment(aiResult));
        }
        return Result.success(resultData);
    }

    /**
     * 异步调用AI进行评分
     * 
     * @param studentHomeworkId 学生作业ID
     * @param homeworkTitle 作业标题
     * @param homeworkRequirement 作业要求
     * @param studentContent 学生提交内容
     */
    @Async("aiGradingExecutor")
    public void asyncAIGrade(Integer studentHomeworkId, String homeworkTitle, String homeworkRequirement, String studentContent) {
        try {
            // 更新状态为评分中
            studentHomeworkMapper.updateGradingStatus(studentHomeworkId, "GRADING", null);

            // 调用AI评分
            ZhipuAIService.GradeResult result = zhipuAIService.gradeHomework(homeworkTitle, homeworkRequirement, studentContent);
            
            // 更新评分结果
            StudentHomework gradeData = new StudentHomework();
            gradeData.setId(studentHomeworkId);
            gradeData.setScore(result.getScore());
            gradeData.setComment(gradeComment(result));
            gradeData.setGradetime(new Date());
            gradeData.setGradingStatus(gradeStatus(result));
            
            studentHomeworkMapper.grade(gradeData);
            
            logger.info("AI评分完成，学生作业ID: {}, 分数: {}", studentHomeworkId, result.getScore());
        } catch (Exception e) {
            logger.error("AI评分失败，学生作业ID: {}, 错误: {}", studentHomeworkId, e.getMessage(), e);
            studentHomeworkMapper.updateGradingStatus(studentHomeworkId, "FAILED",
                    "AI评分暂时不可用，请稍后刷新重试或等待教师手动批改");
        }
    }

    private String gradeStatus(ZhipuAIService.GradeResult result) {
        return result.isRequiresReview() ? "REVIEW_REQUIRED" : "COMPLETED";
    }

    private String gradeComment(ZhipuAIService.GradeResult result) {
        String prefix = result.isRequiresReview()
                ? "[AI预评分·待教师复核] "
                : "[AI自动评分] ";
        return prefix + result.getComment();
    }

    /**
     * 手动重试AI评分（仅当 gradingStatus 为 FAILED 时允许）
     *
     * @param id 学生作业ID
     * @return 操作结果
     */
    public Result<Void> retryGrade(Integer id) {
        if (id == null) {
            return Result.error("学生作业ID不能为空");
        }
        StudentHomework sh = studentHomeworkMapper.findById(id);
        if (sh == null) {
            return Result.error("学生作业不存在");
        }
        if (!"FAILED".equals(sh.getGradingStatus())) {
            return Result.error("仅评分失败的作业可以重试");
        }

        // 重置状态为 PENDING
        studentHomeworkMapper.updateGradingStatus(id, "PENDING", null);

        // 获取作业信息
        Homework homework = homeworkMapper.findById(sh.getHomeworkid());
        if (homework == null) {
            return Result.error("关联作业不存在");
        }

        // 根据作业类型调用对应的异步评分
        if (homework.getType() == 2) {
            // 文档上传
            self.asyncAIGrade(id, homework.getTitle(), homework.getContent(), sh.getContent());
        } else if (homework.getType() == 1) {
            // 在线答题
            self.asyncExamAIGrade(id, homework.getContent(), sh.getContent());
        }

        return Result.success();
    }


    /**
     * 批改作业
     * 
     * @param studentHomework 学生作业对象（包含成绩和评语）
     * @return 操作结果
     */
    public Result<Void> grade(StudentHomework studentHomework) {
        // 参数校验：学生作业ID不能为空
        if (studentHomework.getId() == null) {
            return Result.error("学生作业ID不能为空");
        }
        
        // 参数校验：成绩不能为空
        if (studentHomework.getScore() == null) {
            return Result.error("成绩不能为空");
        }
        
        // 参数校验：成绩范围验证（0-100）
        BigDecimal score = studentHomework.getScore();
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(new BigDecimal("100")) > 0) {
            return Result.error("成绩必须在0-100之间");
        }
        
        // 检查学生作业是否存在
        StudentHomework existing = studentHomeworkMapper.findById(studentHomework.getId());
        if (existing == null) {
            return Result.error("学生作业不存在");
        }
        
        // 检查是否已提交
        if (existing.getStatus() == 0) {
            return Result.error("该学生尚未提交作业");
        }
        
        // 设置批改时间
        studentHomework.setGradetime(new Date());
        
        // 教师确认批改，设置状态为已批改
        studentHomework.setStatus(2);
        studentHomework.setGradingStatus("COMPLETED");
        
        // 更新成绩和评语
        int rows = studentHomeworkMapper.grade(studentHomework);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("批改失败");
    }

    /**
     * 打回作业
     * 
     * @param id 学生作业ID
     * @return 操作结果
     */
    public Result<Void> returnHomework(Integer id) {
        StudentHomework studentHomework = studentHomeworkMapper.findById(id);
        if (studentHomework == null) {
            return Result.error("学生作业不存在");
        }
        studentHomework.setStatus(3); // 设置为已打回
        // 可以在这里清除成绩，或者保留成绩作为历史记录，这里选择保留，待重新提交后覆盖
        // 但为了逻辑清晰，如果打回，通常意味着成绩无效
        studentHomework.setScore(null); 
        int rows = studentHomeworkMapper.update(studentHomework);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("操作失败");
    }

    /**
     * 删除学生作业
     * 
     * @param id 学生作业ID
     * @return 操作结果
     */
    public Result<Void> delete(Integer id) {
        // 参数校验
        if (id == null) {
            return Result.error("学生作业ID不能为空");
        }
        
        // 检查学生作业是否存在
        StudentHomework existing = studentHomeworkMapper.findById(id);
        if (existing == null) {
            return Result.error("学生作业不存在");
        }
        
        // 删除数据
        int rows = studentHomeworkMapper.delete(id);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("删除失败");
    }

    /**
     * 统计某作业的提交数量
     * 
     * @param homeworkid 作业ID
     * @return 提交数量
     */
    public int countSubmitted(Integer homeworkid) {
        if (homeworkid == null) {
            return 0;
        }
        return studentHomeworkMapper.countSubmitted(homeworkid);
    }

    /**
     * 根据教师ID分页获取学生作业列表
     * 
     * @param teacherid  教师ID
     * @param homeworkid 作业ID（可选）
     * @param keyword    搜索关键字
     * @param semester   学期
     * @param page       页码
     * @param pageSize   每页数量
     * @return 学生作业列表
     */
    public List<StudentHomework> getByTeacherId(Integer teacherid, Integer homeworkid, Integer courseid, Integer classid, String keyword, String semester, Integer page, Integer pageSize) {
        if (teacherid == null) {
            return null;
        }
        int offset = (page - 1) * pageSize;
        if (keyword != null && keyword.trim().isEmpty()) {
            keyword = null;
        }
        if (semester != null && semester.trim().isEmpty()) {
            semester = null;
        }
        return studentHomeworkMapper.findByTeacherid(teacherid, homeworkid, courseid, classid, keyword, semester, offset, pageSize);
    }

    /**
     * 根据教师ID获取学生作业总数
     * 
     * @param teacherid  教师ID
     * @param homeworkid 作业ID（可选）
     * @param keyword    搜索关键字
     * @param semester   学期
     * @return 总数
     */
    public Long getCountByTeacherId(Integer teacherid, Integer homeworkid, Integer courseid, Integer classid, String keyword, String semester) {
        if (teacherid == null) {
            return 0L;
        }
        if (keyword != null && keyword.trim().isEmpty()) {
            keyword = null;
        }
        if (semester != null && semester.trim().isEmpty()) {
            semester = null;
        }
        return studentHomeworkMapper.countByTeacherid(teacherid, homeworkid, courseid, classid, keyword, semester);
    }

    /**
     * 查询某作业未提交的学生列表
     */
    public List<Student> getUnsubmittedStudents(Integer homeworkid) {
        if (homeworkid == null) {
            return new java.util.ArrayList<>();
        }
        return studentHomeworkMapper.findUnsubmittedStudents(homeworkid);
    }

    /**
     * 仅允许首次提交或教师退回（status=3）后重交，阻止覆盖已提交/已批改作业。
     * @return null 表示允许；否则返回错误结果
     */
    private Result<Void> assertResubmitAllowed(StudentHomework existing) {
        if (existing == null) return null;
        Integer status = existing.getStatus();
        if (status != null && status == 3) return null;
        if (status != null && status >= 1) {
            return Result.error("作业已提交，无法重复提交。如需修改请等待教师退回后重交。");
        }
        return null;
    }
}
