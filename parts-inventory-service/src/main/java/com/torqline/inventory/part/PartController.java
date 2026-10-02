package com.torqline.inventory.part;

import com.torqline.common.web.ApiException;
import com.torqline.inventory.dto.PartView;
import com.torqline.inventory.dto.RestockRequest;
import com.torqline.inventory.reservation.Reservation;
import com.torqline.inventory.reservation.ReservationRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class PartController {

    private final PartRepository parts;
    private final ReservationRepository reservations;
    private final InventoryService inventory;

    public PartController(PartRepository parts, ReservationRepository reservations, InventoryService inventory) {
        this.parts = parts;
        this.reservations = reservations;
        this.inventory = inventory;
    }

    /** @param fitment CAR or BIKE also returns UNIVERSAL parts, since those fit both */
    @GetMapping("/api/parts")
    public List<PartView> list(@RequestParam String dealerId, @RequestParam(required = false) Fitment fitment) {
        List<Part> found = fitment == null || fitment == Fitment.UNIVERSAL
                ? parts.findByDealerIdOrderBySku(dealerId)
                : parts.findByDealerIdAndFitmentInOrderBySku(dealerId, List.of(fitment, Fitment.UNIVERSAL));
        return found.stream().map(PartView::of).toList();
    }

    @GetMapping("/api/parts/low-stock")
    public List<PartView> lowStock(@RequestParam String dealerId) {
        return parts.findByDealerIdOrderBySku(dealerId).stream().filter(Part::isLowStock).map(PartView::of).toList();
    }

    @GetMapping("/api/parts/{dealerId}/{sku}")
    public PartView get(@PathVariable String dealerId, @PathVariable String sku) {
        return parts.findByDealerIdAndSku(dealerId, sku).map(PartView::of)
                .orElseThrow(() -> ApiException.notFound("Part", dealerId + "/" + sku));
    }

    @PostMapping("/api/parts/{dealerId}/{sku}/restock")
    public PartView restock(@PathVariable String dealerId, @PathVariable String sku,
                            @Valid @RequestBody RestockRequest body) {
        return PartView.of(inventory.restock(dealerId, sku, body.quantity()));
    }

    @GetMapping("/api/reservations")
    public List<Reservation> reservations(@RequestParam UUID repairOrderId) {
        return reservations.findByRepairOrderIdOrderByCreatedAt(repairOrderId);
    }
}
