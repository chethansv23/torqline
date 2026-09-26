package com.torqline.appointment.appointment;

import com.torqline.common.domain.ServiceType;
import com.torqline.common.domain.VehicleType;
import com.torqline.common.web.ApiException;
import com.torqline.common.web.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP contract of the booking API: status codes, headers and the problem-response shape. */
@ExtendWith(MockitoExtension.class)
class AppointmentControllerTest {

    private static final String VALID_BODY = """
            {"dealerId":"TQ-BLR-IND","customerName":"Asha","customerPhone":"9845000001","vehicleType":"BIKE",
             "vehicleNumber":"KA03HB1234","serviceType":"OIL_CHANGE","slotStart":"2030-01-07T10:00"}
            """;

    @Mock AppointmentService service;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new AppointmentController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    private static AppointmentResponse response(UUID id) {
        LocalDateTime start = LocalDateTime.of(2030, 1, 7, 10, 0);
        return new AppointmentResponse(id, "TQ-BLR-IND", 4L, AppointmentStatus.BOOKED, "Asha", "9845000001",
                VehicleType.BIKE, "KA03HB1234", null, null, ServiceType.OIL_CHANGE, start, start.plusMinutes(30),
                Instant.parse("2030-01-07T04:30:00Z"), Instant.parse("2030-01-07T05:00:00Z"), null, null, null);
    }

    @Test
    void newBookingReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.book(any(), eq("key-1"))).thenReturn(new AppointmentService.BookingResult(response(id), false));

        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "key-1").content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("BOOKED"));
    }

    @Test
    void replayedBookingReturns200() throws Exception {
        when(service.book(any(), eq("key-1")))
                .thenReturn(new AppointmentService.BookingResult(response(UUID.randomUUID()), true));

        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "key-1").content(VALID_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void fullSlotIsA409ProblemWithStableCode() throws Exception {
        when(service.book(any(), isNull()))
                .thenThrow(ApiException.conflict("SLOT_UNAVAILABLE", "No BIKE bay is free"));

        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value("No BIKE bay is free"));
    }

    @Test
    void invalidPhoneIsRejectedBeforeReachingTheService() throws Exception {
        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("9845000001", "12ab")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.customerPhone").exists());
        verifyNoInteractions(service);
    }

    @Test
    void cancelRequiresAReason() throws Exception {
        mvc.perform(post("/api/appointments/{id}/cancel", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
