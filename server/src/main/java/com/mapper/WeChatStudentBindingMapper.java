package com.mapper;

import com.model.WeChatStudentBinding;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface WeChatStudentBindingMapper {
    @Select("SELECT id, studentid, appid, openid, unionid, bindtime, lastlogintime " +
            "FROM student_wechat_binding WHERE appid=#{appid} AND openid=#{openid} LIMIT 1")
    WeChatStudentBinding findByOpenid(@Param("appid") String appid, @Param("openid") String openid);

    @Select("SELECT id, studentid, appid, openid, unionid, bindtime, lastlogintime " +
            "FROM student_wechat_binding WHERE appid=#{appid} AND studentid=#{studentid} LIMIT 1")
    WeChatStudentBinding findByStudent(@Param("appid") String appid, @Param("studentid") Integer studentid);

    @Insert("INSERT INTO student_wechat_binding(studentid, appid, openid, unionid, bindtime, lastlogintime) " +
            "VALUES(#{studentid}, #{appid}, #{openid}, #{unionid}, #{bindtime}, #{lastlogintime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WeChatStudentBinding binding);

    @Update("UPDATE student_wechat_binding SET lastlogintime=#{lastlogintime}, " +
            "unionid=COALESCE(#{unionid}, unionid) WHERE id=#{id}")
    int touchLogin(WeChatStudentBinding binding);

    @Delete("DELETE FROM student_wechat_binding WHERE appid=#{appid} AND studentid=#{studentid}")
    int deleteByStudent(@Param("appid") String appid, @Param("studentid") Integer studentid);
}
