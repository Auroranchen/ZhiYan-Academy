package com.service;

import com.dto.AcademicTermRequest;
import com.mapper.AcademicTermMapper;
import com.model.AcademicTerm;
import com.response.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcademicTermServiceTest {
    @Mock
    private AcademicTermMapper mapper;
    @InjectMocks
    private AcademicTermService service;

    @Test
    void createBuildsPlannedTerm() {
        when(mapper.insert(any(AcademicTerm.class))).thenAnswer(invocation -> {
            invocation.<AcademicTerm>getArgument(0).setTermId(9);
            return 1;
        });

        Result<AcademicTerm> result = service.create(request("2026-2027-1"));

        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertEquals(AcademicTermService.PLANNED, result.getData().getStatus());
        assertEquals(false, result.getData().getCurrent());
    }

    @Test
    void createRejectsDuplicateCode() {
        when(mapper.countByCodeExcludingId("2026-2027-1", null)).thenReturn(1);

        Result<AcademicTerm> result = service.create(request("2026-2027-1"));

        assertEquals(409, result.getCode());
        verify(mapper, never()).insert(any());
    }

    @Test
    void activateArchivesPreviousCurrentInsideWorkflow() {
        AcademicTerm term = term(7, "2026-2027-1", false);
        when(mapper.findById(7)).thenReturn(term);
        when(mapper.updateState(7, AcademicTermService.ACTIVE, true)).thenReturn(1);

        Result<Void> result = service.activate(7);

        assertEquals(200, result.getCode());
        verify(mapper).archiveActiveTerms();
        verify(mapper).updateState(7, AcademicTermService.ACTIVE, true);
    }

    @Test
    void archiveRejectsCurrentTerm() {
        when(mapper.findById(7)).thenReturn(term(7, "2026-2027-1", true));

        Result<Void> result = service.archive(7);

        assertEquals(409, result.getCode());
        verify(mapper, never()).updateState(7, AcademicTermService.ARCHIVED, false);
    }

    @Test
    void activateFailureRaisesExceptionSoTransactionCanRollback() {
        when(mapper.findById(7)).thenReturn(term(7, "2026-2027-1", false));
        when(mapper.updateState(7, AcademicTermService.ACTIVE, true)).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.activate(7));
        verify(mapper).archiveActiveTerms();
    }

    @Test
    void deleteProtectsTermsUsedByCourses() {
        when(mapper.findById(7)).thenReturn(term(7, "2026-2027-1", false));
        when(mapper.countCourses("2026-2027-1")).thenReturn(3);

        Result<Void> result = service.delete(7);

        assertEquals(409, result.getCode());
        verify(mapper, never()).delete(7);
    }

    @Test
    void updateProtectsReferencedTermCode() {
        when(mapper.findById(7)).thenReturn(term(7, "2026-2027-1", false));
        when(mapper.countCourses("2026-2027-1")).thenReturn(2);

        Result<Void> result = service.update(7, request("2026-2027-2"));

        assertEquals(409, result.getCode());
        verify(mapper, never()).update(any());
    }

    private AcademicTermRequest request(String code) {
        return new AcademicTermRequest(code, "测试学期",
                LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 31));
    }

    private AcademicTerm term(int id, String code, boolean current) {
        AcademicTerm term = new AcademicTerm();
        term.setTermId(id);
        term.setTermCode(code);
        term.setCurrent(current);
        return term;
    }
}
