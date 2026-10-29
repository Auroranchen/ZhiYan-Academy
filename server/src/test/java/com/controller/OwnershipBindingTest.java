package com.controller;

import com.model.Message;
import com.model.StudentHomework;
import com.response.Result;
import com.service.AttendSessionService;
import com.service.GamificationService;
import com.service.MessageService;
import com.service.StudentHomeworkService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OwnershipBindingTest {

    @Test
    void homeworkSubmissionUsesSessionStudentId() {
        StudentHomeworkService service = mock(StudentHomeworkService.class);
        GamificationService gamification = mock(GamificationService.class);
        StudentHomeworkController controller = new StudentHomeworkController();
        ReflectionTestUtils.setField(controller, "studentHomeworkService", service);
        ReflectionTestUtils.setField(controller, "gamificationService", gamification);

        StudentHomework submission = new StudentHomework();
        submission.setStudentid(999);
        when(service.submit(submission)).thenReturn(Result.success(submission));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("studentid", 7);

        controller.submit(submission, session);

        assertEquals(7, submission.getStudentid());
        verify(gamification).onHomeworkSubmit(7);
    }

    @Test
    void checkinIgnoresStudentIdFromRequestBody() {
        AttendSessionService service = mock(AttendSessionService.class);
        AttendSessionController controller = new AttendSessionController();
        ReflectionTestUtils.setField(controller, "attendSessionService", service);
        when(service.checkin(eq("ABC123"), eq(7))).thenReturn(Result.success(new HashMap<>()));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("studentid", 7);
        Map<String, Object> body = new HashMap<>();
        body.put("studentid", 999);
        body.put("code", "ABC123");

        controller.checkin(body, session);

        verify(service).checkin("ABC123", 7);
    }

    @Test
    void messageSenderAlwaysComesFromSession() {
        MessageService service = mock(MessageService.class);
        MessageController controller = new MessageController();
        ReflectionTestUtils.setField(controller, "messageService", service);
        when(service.send(any(Message.class))).thenReturn(Result.success());

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("teacherid", 3);
        Message message = new Message();
        message.setSendertype(1);
        message.setSenderid(999);

        controller.send(message, session);

        assertEquals(2, message.getSendertype());
        assertEquals(3, message.getSenderid());
        verify(service).send(message);
    }
}
