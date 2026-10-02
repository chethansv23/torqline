package com.torqline.repairorder.order;

import com.torqline.common.events.RepairOrderEvents.PartQuantity;
import com.torqline.repairorder.dto.AssignRequest;
import com.torqline.repairorder.dto.CancelRequest;
import com.torqline.repairorder.dto.PartsRequest;
import com.torqline.repairorder.dto.RepairOrderView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/repair-orders")
public class RepairOrderController {

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
