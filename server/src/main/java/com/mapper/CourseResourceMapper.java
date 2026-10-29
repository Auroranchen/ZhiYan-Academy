package com.mapper;

import com.model.CourseResource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 课程资源数据访问接口
 */
@Mapper
public interface CourseResourceMapper {

    List<CourseResource> findByCourseid(@Param("courseid") Integer courseid,
                                        @Param("offset") Integer offset,
                                        @Param("limit") Integer limit);

    Long countByCourseid(@Param("courseid") Integer courseid);

    List<CourseResource> findByTeacherid(@Param("teacherid") Integer teacherid,
                                         @Param("keyword") String keyword,
                                         @Param("semester") String semester,
                                         @Param("courseid") Integer courseid,
                                         @Param("offset") Integer offset,
                                         @Param("limit") Integer limit);

    Long countByTeacherid(@Param("teacherid") Integer teacherid,
                          @Param("keyword") String keyword,
                          @Param("semester") String semester,
                          @Param("courseid") Integer courseid);

    CourseResource findById(@Param("id") Integer id);

    int insert(CourseResource resource);

    int update(CourseResource resource);

    int delete(@Param("id") Integer id);

    int incrementDownloadCount(@Param("id") Integer id);
}
