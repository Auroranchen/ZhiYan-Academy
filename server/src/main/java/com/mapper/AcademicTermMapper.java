package com.mapper;

import com.model.AcademicTerm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;


@Mapper
public interface AcademicTermMapper {
    List<AcademicTerm> findList(@Param("keyword") String keyword, @Param("status") String status);
    List<AcademicTerm> findOptions();
    AcademicTerm findCurrent();
    AcademicTerm findById(@Param("termId") Integer termId);
    AcademicTerm findByCode(@Param("termCode") String termCode);
    int countByCodeExcludingId(@Param("termCode") String termCode, @Param("termId") Integer termId);
    int insert(AcademicTerm term);
    int update(AcademicTerm term);
    int archiveActiveTerms();
    int updateState(@Param("termId") Integer termId,
                    @Param("status") String status,
                    @Param("current") boolean current);
    int countCourses(@Param("termCode") String termCode);
    int delete(@Param("termId") Integer termId);
}
