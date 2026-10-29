package com.model;

import lombok.Data;
import java.util.Date;

/**
 * 学生实体类
 */
@Data
public class Student {

    /**
     * 学生ID
     */
    private Integer studentid;

    /**
     * 学号
     */
    private String studentno;

    /**
     * 学生姓名
     */
    private String studentname;

    /**
     * 密码
     */
    private String password;

    /**
     * 性别
     */
    private String sex;

    /**
     * 联系电话
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 地址
     */
    private String address;

    /**
     * 所属班级ID
     */
    private Integer classid;

    /**
     * 所属专业ID
     */
    private Integer majorid;

    /**
     * 创建时间
     */
    private Date createtime;

    /**
     * 班级名称（关联查询）
     */
    private String classname;

    /**
     * 专业名称（关联查询）
     */
    private String majorname;

    /**
     * 头像路径
     */
    private String avatar;


}
