package com.mapper;

import com.model.HomeworkComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 作业评论数据访问接口
 */
@Mapper
public interface HomeworkCommentMapper {

    List<HomeworkComment> findByHomeworkid(@Param("homeworkid") Integer homeworkid);

    List<HomeworkComment> findReplies(@Param("parentid") Integer parentid);

    HomeworkComment findById(@Param("id") Integer id);

    int insert(HomeworkComment comment);

    int delete(@Param("id") Integer id);

    int deleteByHomeworkid(@Param("homeworkid") Integer homeworkid);

    Long countByHomeworkid(@Param("homeworkid") Integer homeworkid);
}
