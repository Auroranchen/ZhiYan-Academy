package com.service;

import com.dto.AcademicTermRequest;
import com.mapper.AcademicTermMapper;
import com.model.AcademicTerm;
import com.response.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AcademicTermService {
    public static final String PLANNED = "PLANNED";
    public static final String ACTIVE = "ACTIVE";
    public static final String ARCHIVED = "ARCHIVED";

    private final AcademicTermMapper termMapper;

    public AcademicTermService(AcademicTermMapper termMapper) {
        this.termMapper = termMapper;
    }

    public List<AcademicTerm> list(String keyword, String status) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        String normalizedStatus = normalizeStatus(status);
        return termMapper.findList(normalizedKeyword, normalizedStatus);
    }

    public List<AcademicTerm> options() {
        return termMapper.findOptions();
    }

    public AcademicTerm current() {
        return termMapper.findCurrent();
    }

    @Transactional
    public Result<AcademicTerm> create(AcademicTermRequest request) {
        String code = request.termCode().trim();
        if (termMapper.countByCodeExcludingId(code, null) > 0) {
            return Result.error(409, "学期编码已存在");
        }
        AcademicTerm term = fromRequest(request);
        term.setStatus(PLANNED);
        term.setCurrent(false);
        if (termMapper.insert(term) != 1) {
            return Result.error("新增学期失败");
        }
        return Result.success("学期创建成功", term);
    }

    @Transactional
    public Result<Void> update(Integer termId, AcademicTermRequest request) {
        AcademicTerm existing = termMapper.findById(termId);
        if (existing == null) return Result.error(404, "学期不存在");

        String newCode = request.termCode().trim();
        if (termMapper.countByCodeExcludingId(newCode, termId) > 0) {
            return Result.error(409, "学期编码已存在");
        }
        if (!existing.getTermCode().equals(newCode) && termMapper.countCourses(existing.getTermCode()) > 0) {
            return Result.error(409, "该学期已有课程，不能修改学期编码");
        }

        AcademicTerm term = fromRequest(request);
        term.setTermId(termId);
        return termMapper.update(term) == 1
                ? Result.successMsg("学期更新成功")
                : Result.error("学期更新失败");
    }

    @Transactional
    public Result<Void> activate(Integer termId) {
        AcademicTerm term = termMapper.findById(termId);
        if (term == null) return Result.error(404, "学期不存在");
        if (Boolean.TRUE.equals(term.getCurrent())) return Result.successMsg("该学期已是当前学期");

        termMapper.archiveActiveTerms();
        if (termMapper.updateState(termId, ACTIVE, true) != 1) {
            // 通过异常触发事务回滚，避免旧学期已归档但新学期未启用的中间状态。
            throw new IllegalStateException("当前学期切换失败");
        }
        return Result.successMsg("当前学期切换成功");
    }

    @Transactional
    public Result<Void> archive(Integer termId) {
        AcademicTerm term = termMapper.findById(termId);
        if (term == null) return Result.error(404, "学期不存在");
        if (Boolean.TRUE.equals(term.getCurrent())) {
            return Result.error(409, "当前学期不能直接归档，请先启用另一个学期");
        }
        return termMapper.updateState(termId, ARCHIVED, false) == 1
                ? Result.successMsg("学期归档成功")
                : Result.error("学期归档失败");
    }

    @Transactional
    public Result<Void> delete(Integer termId) {
        AcademicTerm term = termMapper.findById(termId);
        if (term == null) return Result.error(404, "学期不存在");
        if (Boolean.TRUE.equals(term.getCurrent())) return Result.error(409, "当前学期不能删除");
        if (termMapper.countCourses(term.getTermCode()) > 0) {
            return Result.error(409, "该学期已有课程，只能归档不能删除");
        }
        return termMapper.delete(termId) == 1
                ? Result.successMsg("学期删除成功")
                : Result.error("学期删除失败");
    }

    private AcademicTerm fromRequest(AcademicTermRequest request) {
        AcademicTerm term = new AcademicTerm();
        term.setTermCode(request.termCode().trim());
        term.setTermName(request.termName().trim());
        term.setStartDate(request.startDate());
        term.setEndDate(request.endDate());
        return term;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return null;
        String normalized = status.trim().toUpperCase();
        if (!List.of(PLANNED, ACTIVE, ARCHIVED).contains(normalized)) {
            throw new IllegalArgumentException("无效的学期状态");
        }
        return normalized;
    }
}
