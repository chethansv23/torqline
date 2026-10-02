package com.torqline.repairorder.order;

import com.torqline.common.domain.ServiceType;
import com.torqline.common.domain.VehicleType;
import com.torqline.common.events.AppointmentEvents.AppointmentCheckedIn;
import com.torqline.common.events.InventoryEvents.ReservedLine;
import com.torqline.common.web.ApiException;
import com.torqline.repairorder.constants.RepairOrderErrorCodes;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.torqline.repairorder.constants.RepairOrderConstants.GST_RATE;

@Entity
@Table(name = "repair_order")
public class RepairOrder {

    @Id
    private UUID id;
    private String roNumber;
    private UUID appointmentId;
    private String dealerId;
    private String customerName;
    private String customerPhone;
    @Enumerated(EnumType.STRING)
    private VehicleType vehicleType;
    private String vehicleNumber;
    private String vehicleMake;
    private String vehicleModel;
    @Enumerated(EnumType.STRING)
    private ServiceType serviceType;
    private Integer odometerKm;
    @Enumerated(EnumType.STRING)
    private RepairOrderStatus status;
    private String technician;
    private String note;
    private BigDecimal labourAmount;
    private BigDecimal partsAmount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private Instant openedAt;
    private Instant closedAt;
    @Version
    private Long version;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "repair_order_id", nullable = false)
    @OrderBy("sku")
    private List<PartLine> partLines = new ArrayList<>();

    protected RepairOrder() {
    }

    public static RepairOrder openFrom(AppointmentCheckedIn event, String roNumber) {
        RepairOrder ro = new RepairOrder();
        ro.id = UUID.randomUUID();
        ro.roNumber = roNumber;
        ro.appointmentId = event.appointmentId();
        ro.dealerId = event.dealerId();
        ro.customerName = event.customerName();
        ro.customerPhone = event.customerPhone();
        ro.vehicleType = event.vehicleType();
        ro.vehicleNumber = event.vehicleNumber();
        ro.vehicleMake = event.vehicleMake();
        ro.vehicleModel = event.vehicleModel();
        ro.serviceType = event.serviceType();
        ro.odometerKm = event.odometerKm();
        ro.status = RepairOrderStatus.OPEN;
        ro.labourAmount = labourFor(event.serviceType(), event.vehicleType());
        ro.openedAt = Instant.now();
        return ro;
    }

    static BigDecimal labourFor(ServiceType serviceType, VehicleType vehicleType) {
        BigDecimal hours = BigDecimal.valueOf(serviceType.durationFor(vehicleType).toMinutes())
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        return vehicleType.hourlyLabourRate().multiply(hours).setScale(2, RoundingMode.HALF_UP);
    }

    public void assign(String technician) {
        moveTo(RepairOrderStatus.IN_PROGRESS);
        this.technician = technician;
    }

    public void requestParts(UUID requestId, Map<String, Integer> quantities) {
        moveTo(RepairOrderStatus.PARTS_PENDING);
        quantities.forEach((sku, qty) -> partLines.add(new PartLine(requestId, sku, qty)));
    }

    /** @return false if the reply is stale (order cancelled, or request already settled) and was ignored */
    public boolean partsReserved(UUID requestId, List<ReservedLine> reserved) {
        if (!awaiting(requestId)) {
            return false;
        }
        Map<String, ReservedLine> bySku = new java.util.HashMap<>();
        reserved.forEach(line -> bySku.put(line.sku(), line));
        linesFor(requestId).forEach(line -> {
            ReservedLine r = bySku.get(line.getSku());
            line.reserve(r.name(), r.unitPrice());
        });
        this.note = null;
        moveTo(RepairOrderStatus.IN_PROGRESS);
        return true;
    }

    public boolean partsRejected(UUID requestId, String reason) {
        if (!awaiting(requestId)) {
            return false;
        }
        linesFor(requestId).forEach(PartLine::reject);
        this.note = "Parts request rejected: " + reason;
        moveTo(RepairOrderStatus.IN_PROGRESS);
        return true;
    }

    public void complete() {
        moveTo(RepairOrderStatus.COMPLETED);
        this.partsAmount = partLines.stream().map(PartLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal subtotal = labourAmount.add(partsAmount);
        this.taxAmount = subtotal.multiply(GST_RATE).setScale(2, RoundingMode.HALF_UP);
        this.totalAmount = subtotal.add(taxAmount);
        this.closedAt = Instant.now();
    }

    public void cancel(String reason) {
        moveTo(RepairOrderStatus.CANCELLED);
        this.note = reason;
        this.closedAt = Instant.now();
    }

    private boolean awaiting(UUID requestId) {
        return status == RepairOrderStatus.PARTS_PENDING
                && linesFor(requestId).stream().anyMatch(l -> l.getStatus() == PartLineStatus.REQUESTED);
    }

    private List<PartLine> linesFor(UUID requestId) {
        return partLines.stream().filter(l -> l.getRequestId().equals(requestId)).toList();
    }

    private void moveTo(RepairOrderStatus target) {
        if (!status.canMoveTo(target)) {
            throw ApiException.conflict(RepairOrderErrorCodes.INVALID_TRANSITION,
                    "Repair order " + roNumber + " cannot go from " + status + " to " + target);
        }
        this.status = target;
    }

    public UUID getId() { return id; }
    public String getRoNumber() { return roNumber; }
    public UUID getAppointmentId() { return appointmentId; }
    public String getDealerId() { return dealerId; }
    public String getCustomerName() { return customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public VehicleType getVehicleType() { return vehicleType; }
    public String getVehicleNumber() { return vehicleNumber; }
    public String getVehicleMake() { return vehicleMake; }
    public String getVehicleModel() { return vehicleModel; }
    public ServiceType getServiceType() { return serviceType; }
    public Integer getOdometerKm() { return odometerKm; }
    public RepairOrderStatus getStatus() { return status; }
    public String getTechnician() { return technician; }
    public String getNote() { return note; }
    public BigDecimal getLabourAmount() { return labourAmount; }
    public BigDecimal getPartsAmount() { return partsAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getClosedAt() { return closedAt; }
    public List<PartLine> getPartLines() { return partLines; }
}
