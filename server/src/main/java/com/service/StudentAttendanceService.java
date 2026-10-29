package com.service;

import com.mapper.StudentAttendanceMapper;
import com.mapper.StudentMapper;
import com.model.Student;
import com.model.StudentAttendance;
import com.response.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 学生考勤业务逻辑层
 */
@Service
public class StudentAttendanceService {

    @Autowired
    private StudentAttendanceMapper studentAttendanceMapper;

    @Autowired
    private StudentMapper studentMapper;

    /**
     * 分页查询考勤记录
     */
    public List<StudentAttendance> getList(Integer courseid, Integer classid, Date attenddate,
            Integer courseTeacherId, Integer page, Integer pageSize) {
        int offset = (page - 1) * pageSize;
        return studentAttendanceMapper.findList(courseid, classid, attenddate, courseTeacherId, offset, pageSize);
    }

    public Long getCount(Integer courseid, Integer classid, Date attenddate, Integer courseTeacherId) {
        return studentAttendanceMapper.count(courseid, classid, attenddate, courseTeacherId);
    }

    /**
     * 获取考勤详情
     */
    public StudentAttendance getById(Integer id) {
        return studentAttendanceMapper.findById(id);
    }

    /**
     * 获取学生的考勤记录
     */
    public List<StudentAttendance> getByStudentid(Integer studentid, Integer courseid) {
        return studentAttendanceMapper.findByStudentid(studentid, courseid);
    }

    /**
     * 添加单条考勤记录
     */
    public Result<Void> add(StudentAttendance attendance) {
        if (attendance.getCourseid() == null) {
            return Result.error("课程ID不能为空");
        }
        if (attendance.getClassid() == null) {
            return Result.error("班级ID不能为空");
        }
        if (attendance.getStudentid() == null) {
            return Result.error("学生ID不能为空");
        }
        if (attendance.getAttenddate() == null) {
            return Result.error("上课日期不能为空");
        }
        if (attendance.getStatus() == null) {
            return Result.error("出勤状态不能为空");
        }

        // 检查是否已存在
        StudentAttendance existing = studentAttendanceMapper.findByUnique(
                attendance.getCourseid(), attendance.getClassid(),
                attendance.getStudentid(), attendance.getAttenddate());
        if (existing != null) {
            return Result.error("该学生当天的考勤记录已存在");
        }

        attendance.setCreatetime(new Date());
        int rows = studentAttendanceMapper.insert(attendance);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("添加失败");
    }

    /**
     * 批量添加考勤记录
     */
    @Transactional
    public Result<Void> batchAdd(Integer courseid, Integer classid, Date attenddate,
            Integer teacherid, List<Map<String, Object>> records) {
        if (courseid == null || classid == null || attenddate == null) {
            return Result.error("参数不完整");
        }
        if (records == null || records.isEmpty()) {
            return Result.error("考勤记录不能为空");
        }

        List<StudentAttendance> list = new ArrayList<>();
        Date now = new Date();
        for (Map<String, Object> record : records) {
            StudentAttendance attendance = new StudentAttendance();
            attendance.setCourseid(courseid);
            attendance.setClassid(classid);
            attendance.setStudentid((Integer) record.get("studentid"));
            attendance.setAttenddate(attenddate);
            attendance.setStatus((Integer) record.get("status"));
            attendance.setRemark((String) record.get("remark"));
            attendance.setTeacherid(teacherid);
            attendance.setCreatetime(now);
            list.add(attendance);
        }

        int rows = studentAttendanceMapper.batchInsert(list);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("批量添加失败");
    }

    /**
     * 更新考勤记录
     */
    public Result<Void> update(StudentAttendance attendance) {
        if (attendance.getId() == null) {
            return Result.error("考勤ID不能为空");
        }
        if (attendance.getStatus() == null) {
            return Result.error("出勤状态不能为空");
        }

        StudentAttendance existing = studentAttendanceMapper.findById(attendance.getId());
        if (existing == null) {
            return Result.error("考勤记录不存在");
        }

        int rows = studentAttendanceMapper.update(attendance);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("更新失败");
    }

    /**
     * 删除考勤记录
     */
    public Result<Void> delete(Integer id) {
        if (id == null) {
            return Result.error("考勤ID不能为空");
        }

        StudentAttendance existing = studentAttendanceMapper.findById(id);
        if (existing == null) {
            return Result.error("考勤记录不存在");
        }

        int rows = studentAttendanceMapper.delete(id);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("删除失败");
    }

    /**
     * 统计考勤情况
     */
    public Map<String, Long> getStatistics(Integer courseid, Integer classid) {
        Long present = studentAttendanceMapper.countByStatus(courseid, classid, 1);
        Long late = studentAttendanceMapper.countByStatus(courseid, classid, 2);
        Long early = studentAttendanceMapper.countByStatus(courseid, classid, 3);
        Long absent = studentAttendanceMapper.countByStatus(courseid, classid, 4);
        Long leave = studentAttendanceMapper.countByStatus(courseid, classid, 5);

        return Map.of(
                "present", present != null ? present : 0L,
                "late", late != null ? late : 0L,
                "early", early != null ? early : 0L,
                "absent", absent != null ? absent : 0L,
                "leave", leave != null ? leave : 0L);
    }
}
