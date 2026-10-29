package com.service;

import com.mapper.CourseEvaluationMapper;
import com.model.CourseEvaluation;
import com.response.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 课程评价业务逻辑层
 */
@Service
public class CourseEvaluationService {

    @Autowired
    private CourseEvaluationMapper courseEvaluationMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 根据课程ID获取评价列表
     */
    public List<CourseEvaluation> getByCourseid(Integer courseid, Integer page, Integer pageSize) {
        int offset = (page - 1) * pageSize;
        return courseEvaluationMapper.findByCourseid(courseid, offset, pageSize);
    }

    public Long getCountByCourseid(Integer courseid) {
        return courseEvaluationMapper.countByCourseid(courseid);
    }

    /**
     * 获取评价详情
     */
    public CourseEvaluation getById(Integer id) {
        return courseEvaluationMapper.findById(id);
    }

    /**
     * 获取学生对某课程的评价
     */
    public CourseEvaluation getByStudentAndCourse(Integer studentid, Integer courseid) {
        return courseEvaluationMapper.findByStudentAndCourse(studentid, courseid);
    }

    /**
     * 获取学生的所有评价
     */
    public List<CourseEvaluation> getByStudentid(Integer studentid) {
        return courseEvaluationMapper.findByStudentid(studentid);
    }

    /**
     * 添加评价
     */
    public Result<Void> add(CourseEvaluation evaluation) {
        if (evaluation.getCourseid() == null) {
            return Result.error("课程ID不能为空");
        }
        if (evaluation.getStudentid() == null) {
            return Result.error("学生ID不能为空");
        }
        if (!isStudentInCourse(evaluation.getStudentid(), evaluation.getCourseid())) {
            return Result.error(403, "只能评价自己所学课程");
        }

        // 检查是否已评价
        CourseEvaluation existing = courseEvaluationMapper.findByStudentAndCourse(
            evaluation.getStudentid(), evaluation.getCourseid());
        if (existing != null) {
            return Result.error("您已经评价过该课程");
        }

        // 验证评分范围
        if (!isValidScore(evaluation.getTeachingscore()) ||
            !isValidScore(evaluation.getContentscore()) ||
            !isValidScore(evaluation.getDifficultyscore()) ||
            !isValidScore(evaluation.getOverallscore())) {
            return Result.error("评分必须在1-5之间");
        }

        if (evaluation.getAnonymous() == null) {
            evaluation.setAnonymous(1); // 默认匿名
        }
        evaluation.setCreatetime(new Date());

        int rows = courseEvaluationMapper.insert(evaluation);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("添加失败");
    }

    /**
     * 更新评价
     */
    public Result<Void> update(CourseEvaluation evaluation) {
        if (evaluation.getId() == null) {
            return Result.error("评价ID不能为空");
        }

        CourseEvaluation existing = courseEvaluationMapper.findById(evaluation.getId());
        if (existing == null) {
            return Result.error("评价不存在");
        }
        Integer courseid = evaluation.getCourseid() != null ? evaluation.getCourseid() : existing.getCourseid();
        Integer studentid = evaluation.getStudentid() != null ? evaluation.getStudentid() : existing.getStudentid();
        if (!isStudentInCourse(studentid, courseid)) {
            return Result.error(403, "只能评价自己所学课程");
        }

        // 验证评分范围
        if (!isValidScore(evaluation.getTeachingscore()) ||
            !isValidScore(evaluation.getContentscore()) ||
            !isValidScore(evaluation.getDifficultyscore()) ||
            !isValidScore(evaluation.getOverallscore())) {
            return Result.error("评分必须在1-5之间");
        }

        int rows = courseEvaluationMapper.update(evaluation);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("更新失败");
    }

    /**
     * 删除评价
     */
    public Result<Void> delete(Integer id) {
        if (id == null) {
            return Result.error("评价ID不能为空");
        }

        CourseEvaluation existing = courseEvaluationMapper.findById(id);
        if (existing == null) {
            return Result.error("评价不存在");
        }

        int rows = courseEvaluationMapper.delete(id);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("删除失败");
    }

    /**
     * 获取课程评价统计
     */
    public Map<String, Object> getAverageScores(Integer courseid) {
        return courseEvaluationMapper.getAverageScores(courseid);
    }

    /** 学生是否通过班级关联修读该课程 */
    public boolean isStudentInCourse(Integer studentid, Integer courseid) {
        if (studentid == null || courseid == null) return false;
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM courseclass cc " +
                            "JOIN student s ON s.classid = cc.classid " +
                            "WHERE cc.courseid=? AND s.studentid=?",
                    Integer.class, courseid, studentid);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isValidScore(Integer score) {
        return score != null && score >= 1 && score <= 5;
    }
}
