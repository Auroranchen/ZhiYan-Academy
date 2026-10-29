package com.model;

import lombok.Data;
import java.util.Date;

/**
 * 教师实体类
 */
@Data
public class Teacher {

    /**
     * 教师ID
     */
    private Integer teacherid;

    /**
     * 工号
     */
    private String teacherno;

    /**
     * 教师姓名
     */
    private String teachername;

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
     * 职称
     */
    private String title;

    /**
     * 所属部门
     */
    private String department;

    /**
     * 创建时间
     */
    private Date createtime;

    /**
     * 头像路径
     */
    private String avatar;

}
