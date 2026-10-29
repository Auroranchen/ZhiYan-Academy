package com.service;

import com.exception.WeChatAuthException;
import com.mapper.WeChatStudentBindingMapper;
import com.model.Student;
import com.model.WeChatStudentBinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeChatAuthServiceTest {
    private WeChatMiniProgramClient client;
    private WeChatStudentBindingMapper bindings;
    private StudentService students;
    private WeChatAuthService service;

    @BeforeEach
    void setUp() {
        client = mock(WeChatMiniProgramClient.class);
        bindings = mock(WeChatStudentBindingMapper.class);
        students = mock(StudentService.class);
        service = new WeChatAuthService(client, bindings, students);
        when(client.appId()).thenReturn("test-app");
    }

    @Test
    void loginReturnsExistingBoundStudentAndTouchesLoginTime() {
        var identity = new WeChatMiniProgramClient.WeChatIdentity("openid-1", "unionid-1");
        WeChatStudentBinding binding = binding(7, "openid-1");
        Student student = student(7, "2023001001");
        when(client.exchangeCode("code-1")).thenReturn(identity);
        when(bindings.findByOpenid("test-app", "openid-1")).thenReturn(binding);
        when(students.getById(7)).thenReturn(student);

        Student result = service.login("code-1");

        assertSame(student, result);
        assertEquals("unionid-1", binding.getUnionid());
        verify(bindings).touchLogin(binding);
    }

    @Test
    void loginRejectsWechatWithoutBinding() {
        when(client.exchangeCode("new-code"))
                .thenReturn(new WeChatMiniProgramClient.WeChatIdentity("new-openid", null));

        WeChatAuthException exception = assertThrows(WeChatAuthException.class,
                () -> service.login("new-code"));

        assertEquals(428, exception.getCode());
    }

    @Test
    void firstBindCreatesUniqueBindingForExistingStudentAccount() {
        Student credential = student(7, "2023001001");
        Student enriched = student(7, "2023001001");
        when(students.login("2023001001", "correct-password")).thenReturn(credential);
        when(students.getById(7)).thenReturn(enriched);
        when(client.exchangeCode("bind-code"))
                .thenReturn(new WeChatMiniProgramClient.WeChatIdentity("openid-1", "unionid-1"));
        when(bindings.insert(any(WeChatStudentBinding.class))).thenReturn(1);

        Student result = service.bind("bind-code", "2023001001", "correct-password");

        assertSame(enriched, result);
        ArgumentCaptor<WeChatStudentBinding> captor = ArgumentCaptor.forClass(WeChatStudentBinding.class);
        verify(bindings).insert(captor.capture());
        assertEquals(7, captor.getValue().getStudentid());
        assertEquals("test-app", captor.getValue().getAppid());
        assertEquals("openid-1", captor.getValue().getOpenid());
    }

    @Test
    void bindDoesNotReplaceAnotherWechatAlreadyLinkedToStudent() {
        Student credential = student(7, "2023001001");
        when(students.login("2023001001", "correct-password")).thenReturn(credential);
        when(client.exchangeCode("bind-code"))
                .thenReturn(new WeChatMiniProgramClient.WeChatIdentity("new-openid", null));
        when(bindings.findByStudent("test-app", 7)).thenReturn(binding(7, "old-openid"));

        WeChatAuthException exception = assertThrows(WeChatAuthException.class,
                () -> service.bind("bind-code", "2023001001", "correct-password"));

        assertEquals(409, exception.getCode());
        verify(bindings, never()).insert(any());
    }

    @Test
    void unbindRequiresStudentPassword() {
        Student student = student(7, "2023001001");
        when(students.getById(7)).thenReturn(student);
        when(students.login("2023001001", "wrong-password")).thenReturn(null);

        WeChatAuthException exception = assertThrows(WeChatAuthException.class,
                () -> service.unbind(7, "wrong-password"));

        assertEquals(401, exception.getCode());
        verify(bindings, never()).deleteByStudent(any(), any());
    }

    private WeChatStudentBinding binding(Integer studentid, String openid) {
        WeChatStudentBinding binding = new WeChatStudentBinding();
        binding.setId(1L);
        binding.setStudentid(studentid);
        binding.setAppid("test-app");
        binding.setOpenid(openid);
        return binding;
    }

    private Student student(Integer id, String studentno) {
        Student student = new Student();
        student.setStudentid(id);
        student.setStudentno(studentno);
        student.setStudentname("测试学生");
        return student;
    }
}
