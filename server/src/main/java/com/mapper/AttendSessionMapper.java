package com.mapper;

import com.model.AttendSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AttendSessionMapper {

    int insert(AttendSession session);

    AttendSession findById(@Param("id") Integer id);

    AttendSession findByCode(@Param("checkinCode") String checkinCode, @Param("status") Integer status);

    AttendSession findActiveByCodeAndClass(@Param("checkinCode") String checkinCode, @Param("classid") Integer classid);

    List<AttendSession> findByTeacher(@Param("teacherid") Integer teacherid);

    List<AttendSession> findActiveByClass(@Param("classid") Integer classid);

    int updateStatus(@Param("id") Integer id, @Param("status") Integer status);

    int closeExpired();
}
