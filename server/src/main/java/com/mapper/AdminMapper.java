package com.mapper;

import com.model.Admin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 管理员数据访问接口
 */
@Mapper
public interface AdminMapper {

    /**
     * 根据用户名和密码查询管理员
     * 
     * @param username 用户名
     * @param password 密码
     * @return 管理员对象，不存在返回null
     */
    /**
     * 根据ID查询管理员
     * 
     * @param adminid 管理员ID
     * @return 管理员对象
     */
    Admin findById(@Param("adminid") Integer adminid);

    /** 仅供认证/改密使用，包含密码哈希。 */
    Admin findCredentialById(@Param("adminid") Integer adminid);

    /**
     * 根据用户名查询管理员
     * 
     * @param username 用户名
     * @return 管理员对象
     */
    Admin findByUsername(@Param("username") String username);

    /**
     * 更新管理员密码
     * 
     * @param adminid  管理员ID
     * @param password 新密码
     * @return 影响行数
     */
    int updatePassword(@Param("adminid") Integer adminid, @Param("password") String password);

    /**
     * 更新管理员头像
     * 
     * @param id     管理员ID
     * @param avatar 头像路径
     * @return 影响行数
     */
    int updateAvatar(@Param("id") Integer id, @Param("avatar") String avatar);
}
