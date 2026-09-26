package com.torqline.repairorder.order;

import com.torqline.common.domain.ServiceType;
import com.torqline.common.domain.VehicleType;
import com.torqline.common.events.RepairOrderEvents.PartQuantity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/repair-orders")
public class RepairOrderController {

    public record AssignRequest(@NotBlank @Size(max = 80) String technician) {
    }

    public record PartLineRequest(@NotBlank String sku, @Min(1) @Max(50) int quantity) {
    }

    public record PartsRequest(@NotEmpty @Size(max = 20) List<@Valid PartLineRequest> lines) {
    }

    public record CancelRequest(@NotBlank @Size(max = 200) String reason) {
    }

    public record PartLineView(String sku, String name, int quantity, BigDecimal unitPrice, PartLineStatus status) {
    }

    public record RepairOrderView(UUID id, String roNumber, UUID appointmentId, String dealerId,
                                  RepairOrderStatus status, String customerName, String customerPhone,
                                  VehicleType vehicleType, String vehicleNumber, String vehicleMake,
                                  String vehicleModel, ServiceType serviceType, Integer odometerKm,
                                  String technician, String note, List<PartLineView> parts,
                                  BigDecimal labourAmount, BigDecimal partsAmount, BigDecimal taxAmount,
                                  BigDecimal totalAmount, Instant openedAt, Instant closedAt) {

        static RepairOrderView of(RepairOrder ro) {
            return new RepairOrderView(ro.getId(), ro.getRoNumber(), ro.getAppointmentId(), ro.getDealerId(),
                    ro.getStatus(), ro.getCustomerName(), ro.getCustomerPhone(), ro.getVehicleType(),
                    ro.getVehicleNumber(), ro.getVehicleMake(), ro.getVehicleModel(), ro.getServiceType(),
                    ro.getOdometerKm(), ro.getTechnician(), ro.getNote(),
                    ro.getPartLines().stream().map(l -> new PartLineView(l.getSku(), l.getName(), l.getQuantity(),
                            l.getUnitPrice(), l.getStatus())).toList(),
                    ro.getLabourAmount(), ro.getPartsAmount(), ro.getTaxAmount(), ro.getTotalAmount(),
                    ro.getOpenedAt(), ro.getClosedAt());
        }
    }

    private final RepairOrderService service;

    public RepairOrderController(RepairOrderService service) {
        this.service = service;
    }

    @GetMapping
    public List<RepairOrderView> list(@RequestParam String dealerId,
                                      @RequestParam(required = false) RepairOrderStatus status) {
        return service.list(dealerId, status).stream().map(RepairOrderView::of).toList();
    }

    @GetMapping("/{id}")
    public RepairOrderView get(@PathVariable UUID id) {
        return RepairOrderView.of(service.get(id));
    }

    @PostMapping("/{id}/assign")
    public RepairOrderView assign(@PathVariable UUID id, @Valid @RequestBody AssignRequest body) {
        return RepairOrderView.of(service.assign(id, body.technician()));
    }

    /** Asynchronous: returns 202 with the order in PARTS_PENDING; poll until inventory answers. */
    @PostMapping("/{id}/parts")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RepairOrderView requestParts(@PathVariable UUID id, @Valid @RequestBody PartsRequest body) {
        return RepairOrderView.of(service.requestParts(id,
                body.lines().stream().map(l -> new PartQuantity(l.sku(), l.quantity())).toList()));
    }

    @PostMapping("/{id}/complete")
    public RepairOrderView complete(@PathVariable UUID id) {
        return RepairOrderView.of(service.complete(id));
    }

    @PostMapping("/{id}/cancel")
    public RepairOrderView cancel(@PathVariable UUID id, @Valid @RequestBody CancelRequest body) {
        return RepairOrderView.of(service.cancel(id, body.reason()));
    }
}
