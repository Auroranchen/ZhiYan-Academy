package com.service;

import com.mapper.HomeworkMapper;
import com.mapper.StudentHomeworkMapper;
import com.mapper.CourseMapper;
import com.mapper.CourseClassMapper;
import com.mapper.StudentMapper;
import com.mapper.MessageMapper;
import com.mapper.TeacherMapper;
import com.model.Homework;
import com.model.Course;
import com.model.Student;
import com.model.Message;
import com.model.Teacher;
import com.response.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 作业业务逻辑层
 */
@Service
public class HomeworkService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(HomeworkService.class);

    @Autowired
    private HomeworkMapper homeworkMapper;

    @Autowired
    private StudentHomeworkMapper studentHomeworkMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private CourseClassMapper courseClassMapper;

    @Autowired
    private StudentMapper studentMapper;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private TeacherMapper teacherMapper;

    /**
     * 分页查询作业列表（支持搜索）
     * 
     * @param keyword  搜索关键字（作业标题）
     * @param page     页码
     * @param pageSize 每页数量
     * @return 作业列表
     */
    public List<Homework> getList(String keyword, Integer page, Integer pageSize) {
        // 计算偏移量
        int offset = (page - 1) * pageSize;
        // 处理空字符串
        if (keyword != null && keyword.trim().isEmpty()) {
            keyword = null;
        }
        return homeworkMapper.findList(keyword, offset, pageSize);
    }

    /**
     * 获取作业总数（支持搜索条件）
     * 
     * @param keyword 搜索关键字
     * @return 总数
     */
    public Long getCount(String keyword) {
        // 处理空字符串
        if (keyword != null && keyword.trim().isEmpty()) {
            keyword = null;
        }
        return homeworkMapper.count(keyword);
    }

    /**
     * 根据ID获取作业
     * 
     * @param homeworkid 作业ID
     * @return 作业对象
     */
    public Homework getById(Integer homeworkid) {
        if (homeworkid == null) {
            return null;
        }
        return homeworkMapper.findById(homeworkid);
    }

    /**
     * 根据课程ID获取作业列表
     * 
     * @param courseid 课程ID
     * @return 作业列表
     */
    public List<Homework> getByCourseId(Integer courseid) {
        if (courseid == null) {
            return null;
        }
        return homeworkMapper.findByCourseid(courseid);
    }

    /**
     * 根据教师ID分页获取作业列表
     * 
     * @param teacherid 教师ID
     * @param keyword   搜索关键字
     * @param semester  学期
     * @param page      页码
     * @param pageSize  每页数量
     * @return 作业列表
     */
    public List<Homework> getByTeacherId(Integer teacherid, Integer classid, String keyword, String semester, Integer page, Integer pageSize) {
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
        return homeworkMapper.findByTeacherid(teacherid, classid, keyword, semester, offset, pageSize);
    }

    /**
     * 根据教师ID获取作业总数
     * 
     * @param teacherid 教师ID
     * @param keyword   搜索关键字
     * @param semester  学期
     * @return 总数
     */
    public Long getCountByTeacherId(Integer teacherid, Integer classid, String keyword, String semester) {
        if (teacherid == null) {
            return 0L;
        }
        if (keyword != null && keyword.trim().isEmpty()) {
            keyword = null;
        }
        if (semester != null && semester.trim().isEmpty()) {
            semester = null;
        }
        return homeworkMapper.countByTeacherid(teacherid, classid, keyword, semester);
    }


    /**
     * 根据班级ID分页获取作业列表（学生可见的作业）
     * 
     * @param classid  班级ID
     * @param semester 学期
     * @param page     页码
     * @param pageSize 每页数量
     * @return 作业列表
     */
    public List<Homework> getByClassId(Integer classid, String semester, Integer page, Integer pageSize) {
        if (classid == null) {
            return null;
        }
        int offset = (page - 1) * pageSize;
        if (semester != null && semester.trim().isEmpty()) {
            semester = null;
        }
        return homeworkMapper.findByClassid(classid, semester, offset, pageSize);
    }

    /**
     * 根据班级ID获取作业总数
     * 
     * @param classid 班级ID
     * @param semester 学期
     * @return 总数
     */
    public Long getCountByClassId(Integer classid, String semester) {
        if (classid == null) {
            return 0L;
        }
        if (semester != null && semester.trim().isEmpty()) {
            semester = null;
        }
        return homeworkMapper.countByClassid(classid, semester);
    }

    /**
     * 添加作业
     * 
     * @param homework 作业对象
     * @return 操作结果
     */
    public Result<Void> add(Homework homework) {
        // 参数校验：作业标题不能为空
        if (homework.getTitle() == null || homework.getTitle().trim().isEmpty()) {
            return Result.error("作业标题不能为空");
        }
        
        // 参数校验：所属课程不能为空
        if (homework.getCourseid() == null) {
            return Result.error("所属课程不能为空");
        }
        
        // 参数校验：发布教师不能为空
        if (homework.getTeacherid() == null) {
            return Result.error("发布教师不能为空");
        }
        
        // 参数校验：作业类型不能为空
        if (homework.getType() == null) {
            return Result.error("作业类型不能为空");
        }
        
        // 参数校验：截止时间不能为空
        if (homework.getDeadline() == null) {
            return Result.error("截止时间不能为空");
        }
        
        // 去除首尾空格
        homework.setTitle(homework.getTitle().trim());
        
        // 设置创建时间
        homework.setCreatetime(new Date());
        // 同步所属课程的学期到作业
        if (homework.getCourseid() != null) {
            Course course = courseMapper.findById(homework.getCourseid());
            if (course != null && course.getSemester() != null && !course.getSemester().trim().isEmpty()) {
                homework.setSemester(course.getSemester());
            }
        }
        
        // 插入数据
        int rows = homeworkMapper.insert(homework);
        if (rows > 0) {
            // 发布成功后，自动给相关班级的学生发送消息通知
            try {
                sendHomeworkNotification(homework);
            } catch (Exception e) {
                // 通知发送失败不影响作业发布
                log.error("作业「{}」的通知发送失败", homework.getTitle(), e);
            }
            return Result.success();
        }
        return Result.error("添加失败");
    }

    /**
     * 发送作业发布通知给相关班级的所有学生
     */
    private void sendHomeworkNotification(Homework homework) {
        // 获取课程关联的所有班级ID
        List<Integer> classIds = courseClassMapper.findClassIdsByCourseId(homework.getCourseid());
        log.debug("作业通知：课程 {} 关联 {} 个班级", homework.getCourseid(), classIds == null ? 0 : classIds.size());
        if (classIds == null || classIds.isEmpty()) {
            return;
        }

        // 获取教师姓名
        String teacherName = "老师";
        Teacher teacher = teacherMapper.findById(homework.getTeacherid());
        if (teacher != null && teacher.getTeachername() != null) {
            teacherName = teacher.getTeachername();
        }

        // 获取课程名称
        String courseName = "";
        Course course = courseMapper.findById(homework.getCourseid());
        if (course != null && course.getCoursename() != null) {
            courseName = course.getCoursename();
        }

        // 格式化截止时间
        String deadlineStr = "";
        if (homework.getDeadline() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日 HH:mm");
            deadlineStr = sdf.format(homework.getDeadline());
        }

        String title = "新作业通知：" + homework.getTitle();
        String content = teacherName + " 老师发布了新作业「" + homework.getTitle() + "」"
                + (courseName.isEmpty() ? "" : "（课程：" + courseName + "）")
                + (deadlineStr.isEmpty() ? "" : "，截止时间：" + deadlineStr)
                + "，请及时完成。";

        Date now = new Date();
        List<Message> messages = new ArrayList<>();

        for (Integer classId : classIds) {
            List<Student> students = studentMapper.findByClassid(classId);
            if (students == null || students.isEmpty()) {
                continue;
            }
            for (Student student : students) {
                Message msg = new Message();
                msg.setTitle(title);
                msg.setContent(content);
                msg.setSendertype(2); // 教师
                msg.setSenderid(homework.getTeacherid());
                msg.setReceivertype(1); // 学生
                msg.setReceiverid(student.getStudentid());
                msg.setIsread(0);
                msg.setCreatetime(now);
                messages.add(msg);
            }
        }

        if (!messages.isEmpty()) {
            messageMapper.batchInsert(messages);
            log.info("作业通知：已向 {} 名学生发送", messages.size());
        } else {
            log.warn("作业通知：课程 {} 下没有找到需要通知的学生", homework.getCourseid());
        }
    }

    /**
     * 更新作业
     * 
     * @param homework 作业对象
     * @return 操作结果
     */
    public Result<Void> update(Homework homework) {
        // 参数校验：作业ID不能为空
        if (homework.getHomeworkid() == null) {
            return Result.error("作业ID不能为空");
        }
        
        // 参数校验：作业标题不能为空
        if (homework.getTitle() == null || homework.getTitle().trim().isEmpty()) {
            return Result.error("作业标题不能为空");
        }
        
        // 参数校验：所属课程不能为空
        if (homework.getCourseid() == null) {
            return Result.error("所属课程不能为空");
        }
        
        // 参数校验：作业类型不能为空
        if (homework.getType() == null) {
            return Result.error("作业类型不能为空");
        }
        
        // 参数校验：截止时间不能为空
        if (homework.getDeadline() == null) {
            return Result.error("截止时间不能为空");
        }
        
        // 检查作业是否存在
        Homework existing = homeworkMapper.findById(homework.getHomeworkid());
        if (existing == null) {
            return Result.error("作业不存在");
        }
        
        // 去除首尾空格
        homework.setTitle(homework.getTitle().trim());
        // 同步所属课程的学期到作业（避免历史数据为空）
        if (homework.getCourseid() != null && (homework.getSemester() == null || homework.getSemester().trim().isEmpty())) {
            Course course = courseMapper.findById(homework.getCourseid());
            if (course != null && course.getSemester() != null && !course.getSemester().trim().isEmpty()) {
                homework.setSemester(course.getSemester());
            }
        }
        
        // 更新数据
        int rows = homeworkMapper.update(homework);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("更新失败");
    }

    /**
     * 删除作业
     * 
     * @param homeworkid 作业ID
     * @return 操作结果
     */
    @Transactional
    public Result<Void> delete(Integer homeworkid) {
        // 参数校验
        if (homeworkid == null) {
            return Result.error("作业ID不能为空");
        }
        
        // 检查作业是否存在
        Homework existing = homeworkMapper.findById(homeworkid);
        if (existing == null) {
            return Result.error("作业不存在");
        }
        
        // 先删除学生作业记录
        studentHomeworkMapper.deleteByHomeworkid(homeworkid);
        
        // 删除作业
        int rows = homeworkMapper.delete(homeworkid);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("删除失败");
    }

    /**
     * 检查作业是否已过截止时间
     * 
     * @param homeworkid 作业ID
     * @return true-已过期，false-未过期
     */
    public boolean isExpired(Integer homeworkid) {
        Homework homework = homeworkMapper.findById(homeworkid);
        if (homework == null || homework.getDeadline() == null) {
            return false;
        }
        return new Date().after(homework.getDeadline());
    }
}
