package com.torqline.appointment.appointment;

import com.torqline.appointment.constants.AppointmentErrorCodes;
import com.torqline.appointment.dto.BookAppointmentRequest;
import com.torqline.common.domain.ServiceType;
import com.torqline.common.domain.VehicleType;
import com.torqline.common.web.ApiException;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointment")
public class Appointment {

    @Id
    private UUID id;
    private String dealerId;
    private Long bayId;
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    @Enumerated(EnumType.STRING)
    private VehicleType vehicleType;
    private String vehicleNumber;
    private String vehicleMake;
    private String vehicleModel;
    @Enumerated(EnumType.STRING)
    private ServiceType serviceType;
    private Instant slotStart;
    private Instant slotEnd;
    @Enumerated(EnumType.STRING)
    private AppointmentStatus status;
    private String idempotencyKey;
    private String notes;
    private Integer odometerKm;
    private String cancelReason;
    private Instant createdAt;
    private Instant updatedAt;
    @Version
    private Long version;

    protected Appointment() {
    }

    public Appointment(String dealerId, Long bayId, BookAppointmentRequest request, Instant slotStart,
                       Instant slotEnd, String idempotencyKey) {
        this.id = UUID.randomUUID();
        this.dealerId = dealerId;
        this.bayId = bayId;
        this.customerName = request.customerName();
        this.customerPhone = request.customerPhone();
        this.customerEmail = request.customerEmail();
        this.vehicleType = request.vehicleType();
        this.vehicleNumber = request.vehicleNumber().replace(" ", "").toUpperCase();
        this.vehicleMake = request.vehicleMake();
        this.vehicleModel = request.vehicleModel();
        this.serviceType = request.serviceType();
        this.slotStart = slotStart;
        this.slotEnd = slotEnd;
        this.status = AppointmentStatus.BOOKED;
        this.idempotencyKey = idempotencyKey;
        this.notes = request.notes();
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void checkIn(Integer odometerKm) {
        requireBooked("check in");
        this.status = AppointmentStatus.CHECKED_IN;
        this.odometerKm = odometerKm;
        this.updatedAt = Instant.now();
    }

    public void cancel(String reason) {
        requireBooked("cancel");
        this.status = AppointmentStatus.CANCELLED;
        this.cancelReason = reason;
        this.updatedAt = Instant.now();
    }

    private void requireBooked(String action) {
        if (status != AppointmentStatus.BOOKED) {
            throw ApiException.conflict(AppointmentErrorCodes.INVALID_STATE, "Cannot " + action + " an appointment that is " + status);
        }
    }

    public UUID getId() { return id; }
    public String getDealerId() { return dealerId; }
    public Long getBayId() { return bayId; }
    public String getCustomerName() { return customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public String getCustomerEmail() { return customerEmail; }
    public VehicleType getVehicleType() { return vehicleType; }
    public String getVehicleNumber() { return vehicleNumber; }
    public String getVehicleMake() { return vehicleMake; }
    public String getVehicleModel() { return vehicleModel; }
    public ServiceType getServiceType() { return serviceType; }
    public Instant getSlotStart() { return slotStart; }
    public Instant getSlotEnd() { return slotEnd; }
    public AppointmentStatus getStatus() { return status; }
    public String getNotes() { return notes; }
    public Integer getOdometerKm() { return odometerKm; }
    public String getCancelReason() { return cancelReason; }
    public Instant getCreatedAt() { return createdAt; }
}
