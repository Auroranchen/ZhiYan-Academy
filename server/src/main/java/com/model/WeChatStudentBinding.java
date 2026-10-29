package com.model;

import lombok.Data;
import java.util.Date;

/** 学生账号与微信小程序 OpenID 的绑定关系。 */
@Data
public class WeChatStudentBinding {
    private Long id;
    private Integer studentid;
    private String appid;
    private String openid;
    private String unionid;
    private Date bindtime;
    private Date lastlogintime;
}
