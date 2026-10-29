package com.model;

import lombok.Data;

/**
 * 管理员实体类
 */
@Data
public class Admin {

    /**
     * 管理员ID
     */
    private Integer adminid;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 真实姓名
     */
    private String realname;

    /**
     * 头像路径
     */
    private String avatar;

    public Admin() {
    }

    public Admin(Integer adminid, String username, String password, String realname) {
        this.adminid = adminid;
        this.username = username;
        this.password = password;
        this.realname = realname;
    }


}
