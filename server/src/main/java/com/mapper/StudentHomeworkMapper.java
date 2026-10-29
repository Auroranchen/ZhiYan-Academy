package com.mapper;

import com.model.Student;
import com.model.StudentHomework;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 学生作业数据访问接口
 */
@Mapper
public interface StudentHomeworkMapper {

    /**
     * 查询学生作业列表（分页、搜索）
     * 
     * @param keyword 搜索关键字（学生姓名或作业标题）
     * @param offset  偏移量
     * @param limit   每页数量
     * @return 学生作业列表
     */
    List<StudentHomework> findList(@Param("keyword") String keyword,
                                   @Param("offset") Integer offset,
                                   @Param("limit") Integer limit);

    /**
     * 查询学生作业总数（带搜索条件）
     * 
     * @param keyword 搜索关键字
     * @return 总数
     */
    Long count(@Param("keyword") String keyword);

    /**
     * 根据ID查询学生作业
     * 
     * @param id 学生作业ID
     * @return 学生作业对象
     */
    StudentHomework findById(@Param("id") Integer id);

    /**
     * 根据作业ID查询学生提交列表
     * 
     * @param homeworkid 作业ID
     * @param keyword    搜索关键字
     * @param offset     偏移量
     * @param limit      每页数量
     * @return 学生作业列表
     */
    List<StudentHomework> findByHomeworkid(@Param("homeworkid") Integer homeworkid,
                                           @Param("keyword") String keyword,
                                           @Param("offset") Integer offset,
                                           @Param("limit") Integer limit);

    /**
     * 根据作业ID查询学生提交总数
     * 
     * @param homeworkid 作业ID
     * @param keyword    搜索关键字
     * @return 总数
     */
    Long countByHomeworkid(@Param("homeworkid") Integer homeworkid,
                           @Param("keyword") String keyword);

    /**
     * 根据学生ID查询作业列表
     * 
     * @param studentid 学生ID
     * @param courseid  课程ID
     * @param semester  学期
     * @param offset    偏移量
     * @param limit     每页数量
     * @return 学生作业列表
     */
    List<StudentHomework> findByStudentid(@Param("studentid") Integer studentid,
                                          @Param("courseid") Integer courseid,
                                          @Param("semester") String semester,
                                          @Param("offset") Integer offset,
                                          @Param("limit") Integer limit);

    /**
     * 根据学生ID查询作业总数
     * 
     * @param studentid 学生ID
     * @param courseid  课程ID
     * @param semester  学期
     * @return 总数
     */
    Long countByStudentid(@Param("studentid") Integer studentid,
                          @Param("courseid") Integer courseid,
                          @Param("semester") String semester);

    /**
     * 根据作业ID和学生ID查询
     * 
     * @param homeworkid 作业ID
     * @param studentid  学生ID
     * @return 学生作业对象
     */
    StudentHomework findByHomeworkAndStudent(@Param("homeworkid") Integer homeworkid,
                                             @Param("studentid") Integer studentid);

    /**
     * 添加学生作业
     * 
     * @param studentHomework 学生作业对象
     * @return 影响行数
     */
    int insert(StudentHomework studentHomework);

    /**
     * 更新学生作业（提交作业）
     * 
     * @param studentHomework 学生作业对象
     * @return 影响行数
     */
    int update(StudentHomework studentHomework);

    /**
     * 更新学生作业（在线答题自动评分）
     *
     * @param studentHomework 学生作业对象
     * @return 影响行数
     */
    int updateSubmit(StudentHomework studentHomework);

    /**
     * 更新学生作业（在线答题自动评分含评语）
     *
     * @param studentHomework 学生作业对象
     * @return 影响行数
     */
    int updateSubmitWithGrade(StudentHomework studentHomework);

    /**
     * 批改作业（更新成绩和评语）
     * 
     * @param studentHomework 学生作业对象
     * @return 影响行数
     */
    int grade(StudentHomework studentHomework);

    /**
     * 删除学生作业
     * 
     * @param id 学生作业ID
     * @return 影响行数
     */
    int delete(@Param("id") Integer id);

    /**
     * 根据作业ID删除所有学生作业
     * 
     * @param homeworkid 作业ID
     * @return 影响行数
     */
    int deleteByHomeworkid(@Param("homeworkid") Integer homeworkid);

    /**
     * 统计某作业的提交数量
     * 
     * @param homeworkid 作业ID
     * @return 提交数量
     */
    int countSubmitted(@Param("homeworkid") Integer homeworkid);

    /**
     * 根据教师ID查询学生作业列表
     * 
     * @param teacherid  教师ID
     * @param homeworkid 作业ID（可选）
     * @param keyword    搜索关键字
     * @param semester   学期
     * @param offset     偏移量
     * @param limit      每页数量
     * @return 学生作业列表
     */
    List<StudentHomework> findByTeacherid(@Param("teacherid") Integer teacherid,
                                          @Param("homeworkid") Integer homeworkid,
                                          @Param("courseid") Integer courseid,
                                          @Param("classid") Integer classid,
                                          @Param("keyword") String keyword,
                                          @Param("semester") String semester,
                                          @Param("offset") Integer offset,
                                          @Param("limit") Integer limit);

    /**
     * 根据教师ID查询学生作业总数
     * 
     * @param teacherid  教师ID
     * @param homeworkid 作业ID（可选）
     * @param keyword    搜索关键字
     * @param semester   学期
     * @return 总数
     */
    Long countByTeacherid(@Param("teacherid") Integer teacherid,
                          @Param("homeworkid") Integer homeworkid,
                          @Param("courseid") Integer courseid,
                          @Param("classid") Integer classid,
                          @Param("keyword") String keyword,
                          @Param("semester") String semester);

    /**
     * 查询某作业未提交的学生列表（按班级分组）
     * 通过课程关联的班级找到所有应提交学生，排除已提交的
     *
     * @param homeworkid 作业ID
     * @return 未提交的学生列表（含班级信息）
     */
    List<Student> findUnsubmittedStudents(@Param("homeworkid") Integer homeworkid);

    /**
     * 更新AI评分状态
     * 
     * @param id            学生作业ID
     * @param gradingStatus 评分状态
     * @param comment       评语（可选，失败时记录原因）
     * @return 影响行数
     */
    int updateGradingStatus(@Param("id") Integer id, @Param("gradingStatus") String gradingStatus, @Param("comment") String comment);

}
