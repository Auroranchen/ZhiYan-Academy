package com.controller;

import com.config.RequireRole;
import com.dto.AcademicTermRequest;
import com.model.AcademicTerm;
import com.response.Result;
import com.service.AcademicTermService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/academic-term")
public class AcademicTermController {
    private final AcademicTermService termService;

    public AcademicTermController(AcademicTermService termService) {
        this.termService = termService;
    }

    @GetMapping("/list")
    @RequireRole("admin")
    public Result<List<AcademicTerm>> list(@RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String status) {
        return Result.success(termService.list(keyword, status));
    }

    /** 所有已登录角色都可以读取开课下拉选项。 */
    @GetMapping("/options")
    public Result<List<AcademicTerm>> options() {
        return Result.success(termService.options());
    }

    @GetMapping("/current")
    public Result<AcademicTerm> current() {
        return Result.success(termService.current());
    }

    @PostMapping
    @RequireRole("admin")
    public Result<AcademicTerm> create(@Valid @RequestBody AcademicTermRequest request) {
        return termService.create(request);
    }

    @PutMapping("/{id}")
    @RequireRole("admin")
    public Result<Void> update(@PathVariable("id") Integer id,
                               @Valid @RequestBody AcademicTermRequest request) {
        return termService.update(id, request);
    }

    @PostMapping("/{id}/activate")
    @RequireRole("admin")
    public Result<Void> activate(@PathVariable("id") Integer id) {
        return termService.activate(id);
    }

    @PostMapping("/{id}/archive")
    @RequireRole("admin")
    public Result<Void> archive(@PathVariable("id") Integer id) {
        return termService.archive(id);
    }

    @DeleteMapping("/{id}")
    @RequireRole("admin")
    public Result<Void> delete(@PathVariable("id") Integer id) {
        return termService.delete(id);
    }
}
