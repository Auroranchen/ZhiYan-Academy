package com.service;

import com.mapper.AcademicTermMapper;
import com.mapper.CourseClassMapper;
import com.mapper.CourseMapper;
import com.model.Course;
import com.model.CourseClass;
import com.response.Result;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Random;

/**
 * 课程业务逻辑层
 */
@Service
public class CourseService {

    private final CourseMapper courseMapper;
    private final CourseClassMapper courseClassMapper;
    private final AcademicTermMapper academicTermMapper;

    public CourseService(CourseMapper courseMapper,
                         CourseClassMapper courseClassMapper,
                         AcademicTermMapper academicTermMapper) {
        this.courseMapper = courseMapper;
        this.courseClassMapper = courseClassMapper;
        this.academicTermMapper = academicTermMapper;
    }

    /**
     * 分页查询课程列表（支持搜索）
     * 
     * @param keyword  搜索关键字（课程名称）
     * @param page     页码
     * @param pageSize 每页数量
     * @return 课程列表
     */
    public List<Course> getList(String keyword, Integer page, Integer pageSize) {
        // 计算偏移量
        int offset = (page - 1) * pageSize;
        // 处理空字符串
        if (keyword != null && keyword.trim().isEmpty()) {
            keyword = null;
        }
        return courseMapper.findList(keyword, offset, pageSize);
    }

    /**
     * 获取课程总数（支持搜索条件）
     * 
     * @param keyword 搜索关键字
     * @return 总数
     */
    public Long getCount(String keyword) {
        // 处理空字符串
        if (keyword != null && keyword.trim().isEmpty()) {
            keyword = null;
        }
        return courseMapper.count(keyword);
    }

    /**
     * 获取所有课程（下拉选择用）
     * 
     * @return 课程列表
     */
    @Cacheable(value = "courseAll")
    public List<Course> getAll() {
        return courseMapper.findAll();
    }

    /**
     * 根据ID获取课程
     * 
     * @param courseid 课程ID
     * @return 课程对象
     */
    public Course getById(Integer courseid) {
        if (courseid == null) {
            return null;
        }
        Course course = courseMapper.findById(courseid);
        if (course != null) {
            // 查询关联的班级ID列表
            List<Integer> classIds = courseClassMapper.findClassIdsByCourseId(courseid);
            course.setClassIds(classIds);
        }
        return course;
    }

    /**
     * 根据教师ID获取课程列表
     * 
     * @param teacherid 教师ID
     * @param semester  学期（可选）
     * @return 课程列表
     */
    @Cacheable(value = "courseByTeacher", key = "#teacherid + '-' + #semester", unless = "#result == null")
    public List<Course> getByTeacherId(Integer teacherid, String semester) {
        if (teacherid == null) {
            return null;
        }
        return courseMapper.findByTeacherId(teacherid, semester);
    }

    /**
     * 根据班级ID获取课程列表
     * 
     * @param classid  班级ID
     * @param semester 学期（可选）
     * @return 课程列表
     */
    @Cacheable(value = "courseByClass", key = "#classid + '-' + #semester", unless = "#result == null")
    public List<Course> getByClassId(Integer classid, String semester) {
        if (classid == null) {
            return null;
        }
        return courseMapper.findByClassId(classid, semester);
    }

    /**
     * 添加课程
     * 
     * @param course   课程对象
     * @param classIds 关联的班级ID列表
     * @return 操作结果，包含新创建的课程对象
     */
    @Caching(evict = {
            @CacheEvict(value = "courseAll", allEntries = true),
            @CacheEvict(value = "courseByTeacher", allEntries = true),
            @CacheEvict(value = "courseByClass", allEntries = true)
    })
    @Transactional
    public Result<Course> add(Course course, List<Integer> classIds) {
        // 参数校验：课程名称不能为空
        if (course.getCoursename() == null || course.getCoursename().trim().isEmpty()) {
            return Result.error(400, "课程名称不能为空");
        }

        // 参数校验：任课教师不能为空
        if (course.getTeacherid() == null) {
            return Result.error(400, "任课教师不能为空");
        }

        if (course.getSemester() == null || course.getSemester().trim().isEmpty()) {
            return Result.error(400, "学期不能为空");
        }
        course.setSemester(course.getSemester().trim());
        if (academicTermMapper.findByCode(course.getSemester()) == null) {
            return Result.error(400, "学期不存在或尚未配置");
        }

        // 去除首尾空格
        course.setCoursename(course.getCoursename().trim());

        // 设置创建时间
        course.setCreatetime(new Date());
        // 生成课程码
        String code = generateUniqueCode();
        course.setCoursecode(code);

        // 插入课程数据
        int rows = courseMapper.insert(course);
        if (rows <= 0) {
            return Result.error("添加失败");
        }

        // 添加课程班级关联
        if (classIds != null && !classIds.isEmpty()) {
            for (Integer classid : classIds) {
                CourseClass cc = new CourseClass();
                cc.setCourseid(course.getCourseid());
                cc.setClassid(classid);
                courseClassMapper.insert(cc);
            }
        }

        return Result.success(course);
    }

    @Transactional(readOnly = true)
    public Course getByCode(String coursecode) {
        if (coursecode == null || coursecode.trim().isEmpty()) {
            return null;
        }
        return courseMapper.findByCode(coursecode.trim());
    }

    @CacheEvict(value = "courseByClass", allEntries = true)
    @Transactional
    public Result<Void> joinByCode(String coursecode, Integer classid) {
        if (coursecode == null || coursecode.trim().isEmpty()) {
            return Result.error(400, "课程码不能为空");
        }
        if (classid == null) {
            return Result.error(400, "班级ID不能为空，请确认您已分配班级");
        }
        Course course = courseMapper.findByCode(coursecode.trim());
        if (course == null) {
            return Result.error(404, "课程码不存在，请检查是否输入正确");
        }
        int count = courseClassMapper.countByCourseIdAndClassId(course.getCourseid(), classid);
        if (count > 0) {
            // 区分已加入和新加入的情况，返回不同提示
            return Result.successMsg("您已经加入该课程");
        }
        CourseClass cc = new CourseClass();
        cc.setCourseid(course.getCourseid());
        cc.setClassid(classid);
        courseClassMapper.insert(cc);
        return Result.successMsg("加入课程成功");
    }

    private String generateUniqueCode() {
        String code;
        do {
            code = generateCode();
        } while (courseMapper.findByCode(code) != null);
        return code;
    }

    private String generateCode() {
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append(r.nextInt(10));
        }
        return sb.toString();
    }

    /**
     * 更新课程
     * 
     * @param course   课程对象
     * @param classIds 关联的班级ID列表
     * @return 操作结果
     */
    @Caching(evict = {
            @CacheEvict(value = "courseAll", allEntries = true),
            @CacheEvict(value = "courseByTeacher", allEntries = true),
            @CacheEvict(value = "courseByClass", allEntries = true)
    })
    @Transactional
    public Result<Void> update(Course course, List<Integer> classIds) {
        // 参数校验：课程ID不能为空
        if (course.getCourseid() == null) {
            return Result.error(400, "课程ID不能为空");
        }

        // 参数校验：课程名称不能为空
        if (course.getCoursename() == null || course.getCoursename().trim().isEmpty()) {
            return Result.error(400, "课程名称不能为空");
        }

        // 参数校验：任课教师不能为空
        if (course.getTeacherid() == null) {
            return Result.error(400, "任课教师不能为空");
        }

        if (course.getSemester() == null || course.getSemester().trim().isEmpty()) {
            return Result.error(400, "学期不能为空");
        }
        course.setSemester(course.getSemester().trim());
        if (academicTermMapper.findByCode(course.getSemester()) == null) {
            return Result.error(400, "学期不存在或尚未配置");
        }

        // 检查课程是否存在
        Course existing = courseMapper.findById(course.getCourseid());
        if (existing == null) {
            return Result.error(404, "课程不存在");
        }

        // 课程码只能由服务端创建；更新时始终保留原值，避免被空请求覆盖。
        course.setCoursecode(existing.getCoursecode());
        course.setCoursename(course.getCoursename().trim());

        // 更新课程数据
        int rows = courseMapper.update(course);
        if (rows <= 0) {
            return Result.error("更新失败");
        }

        // 先删除原有的班级关联
        courseClassMapper.deleteByCourseId(course.getCourseid());

        // 重新添加班级关联
        if (classIds != null && !classIds.isEmpty()) {
            for (Integer classid : classIds) {
                CourseClass cc = new CourseClass();
                cc.setCourseid(course.getCourseid());
                cc.setClassid(classid);
                courseClassMapper.insert(cc);
            }
        }

        return Result.success();
    }

    /**
     * 删除课程
     * 
     * @param courseid 课程ID
     * @return 操作结果
     */
    @Caching(evict = {
            @CacheEvict(value = "courseAll", allEntries = true),
            @CacheEvict(value = "courseByTeacher", allEntries = true),
            @CacheEvict(value = "courseByClass", allEntries = true)
    })
    @Transactional
    public Result<Void> delete(Integer courseid) {
        // 参数校验
        if (courseid == null) {
            return Result.error(400, "课程ID不能为空");
        }

        // 检查课程是否存在
        Course existing = courseMapper.findById(courseid);
        if (existing == null) {
            return Result.error(404, "课程不存在");
        }

        // 检查课程下是否有作业
        int homeworkCount = courseMapper.countHomework(courseid);
        if (homeworkCount > 0) {
            return Result.error(409, "该课程下存在作业，无法删除");
        }

        // 先删除班级关联
        courseClassMapper.deleteByCourseId(courseid);

        // 删除课程
        int rows = courseMapper.delete(courseid);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("删除失败");
    }
}
