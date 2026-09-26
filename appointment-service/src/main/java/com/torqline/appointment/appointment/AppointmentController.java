package com.torqline.appointment.appointment;

import com.torqline.common.domain.ServiceType;
import com.torqline.common.domain.VehicleType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    public record CheckInRequest(@Min(0) @Max(2_000_000) Integer odometerKm) {
    }

    public record CancelRequest(@NotBlank @Size(max = 200) String reason) {
    }

    private final AppointmentService service;

    public AppointmentController(AppointmentService service) {
        this.service = service;
    }

    @GetMapping("/availability")
    public AppointmentService.Availability availability(
            @RequestParam String dealerId, @RequestParam VehicleType vehicleType,
            @RequestParam ServiceType serviceType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.availability(dealerId, vehicleType, serviceType, date);
    }

    /**
     * Clients should send an {@code Idempotency-Key} so a retried request (timeout, double tap)
     * returns the original booking with 200 instead of creating a second one.
     */
    @PostMapping
    public ResponseEntity<AppointmentResponse> book(
            @Valid @RequestBody BookAppointmentRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) @Size(max = 80) String idempotencyKey) {
        AppointmentService.BookingResult result = service.book(request, idempotencyKey);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(result.appointment());
    }

    @GetMapping
    public List<AppointmentResponse> forDay(@RequestParam String dealerId,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.forDay(dealerId, date);
    }

    @GetMapping("/{id}")
    public AppointmentResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/check-in")
    public AppointmentResponse checkIn(@PathVariable UUID id, @Valid @RequestBody(required = false) CheckInRequest body) {
        return service.checkIn(id, body == null ? null : body.odometerKm());
    }

    @PostMapping("/{id}/cancel")
    public AppointmentResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelRequest body) {
        return service.cancel(id, body.reason());
    }
}
