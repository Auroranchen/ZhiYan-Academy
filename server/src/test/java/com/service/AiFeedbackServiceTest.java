package com.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AiFeedbackServiceTest {

    @Test
    void savesBoundedPrivacyAwarePreview() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AiFeedbackService service = new AiFeedbackService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);

        service.save("student", 7, "12345678_abcd", "assistant.chat", -1,
                "内容不准确", "问题".repeat(400), "回答".repeat(400));

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(contains("ON DUPLICATE KEY UPDATE"), args.capture());
        assertEquals(-1, args.getValue()[4]);
        assertTrue(String.valueOf(args.getValue()[6]).length() <= 500);
        assertTrue(String.valueOf(args.getValue()[7]).length() <= 500);
    }

    @Test
    void rejectsInvalidRatingAndMessageKey() {
        AiFeedbackService service = new AiFeedbackService();
        assertThrows(IllegalArgumentException.class,
                () -> service.save("student", 1, "bad", "assistant.chat", 1,
                        null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.save("student", 1, "12345678", "assistant.chat", 0,
                        null, null, null));
    }
}
