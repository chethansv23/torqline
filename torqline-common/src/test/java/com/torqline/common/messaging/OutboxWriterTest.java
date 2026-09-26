package com.torqline.common.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class OutboxWriterTest {

    @Test
    void refusesToWriteOutsideATransaction() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        OutboxWriter writer = new OutboxWriter(jdbc, JsonMapper.builder().build());

        // Without the surrounding business transaction the event could be saved while the state change rolls back.
        assertThatThrownBy(() -> writer.append("topic", "Appointment", "id", new Object()))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(jdbc);
    }
}
