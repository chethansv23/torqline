package com.torqline.inventory.part;

import com.torqline.common.constants.AggregateTypes;
import com.torqline.common.constants.Topics;
import com.torqline.common.events.InventoryEvents.PartLowStock;
import com.torqline.common.events.InventoryEvents.PartsReservationFailed;
import com.torqline.common.events.InventoryEvents.PartsReserved;
import com.torqline.common.events.InventoryEvents.ReservedLine;
import com.torqline.common.events.RepairOrderEvents.PartQuantity;
import com.torqline.common.events.RepairOrderEvents.PartsReservationRequested;
import com.torqline.common.messaging.OutboxWriter;
import com.torqline.common.web.ApiException;
import com.torqline.inventory.reservation.Reservation;
import com.torqline.inventory.reservation.ReservationLine;
import com.torqline.inventory.reservation.ReservationRepository;
import com.torqline.inventory.reservation.ReservationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final PartRepository parts;
    private final ReservationRepository reservations;
    private final OutboxWriter outbox;

    public InventoryService(PartRepository parts, ReservationRepository reservations, OutboxWriter outbox) {
        this.parts = parts;
        this.reservations = reservations;
        this.outbox = outbox;
    }

    /**
     * All-or-nothing: either every line is reserved or none is. Validation happens after taking row
     * locks, so the availability check and the update cannot be interleaved with another request.
     */
    @Transactional
    public void reserve(PartsReservationRequested request) {
        if (reservations.existsByRequestId(request.requestId())) {
            return;
        }
        Map<String, Integer> wanted = request.lines().stream()
                .collect(Collectors.toMap(PartQuantity::sku, PartQuantity::quantity, Integer::sum));
        Map<String, Part> locked = parts.lockForUpdate(request.dealerId(), wanted.keySet()).stream()
                .collect(Collectors.toMap(Part::getSku, Function.identity()));

        List<String> problems = new ArrayList<>();
        wanted.forEach((sku, qty) -> {
            Part part = locked.get(sku);
            if (part == null) {
                problems.add(sku + " is not stocked at " + request.dealerId());
            } else if (part.available() < qty) {
                problems.add(sku + " needs " + qty + ", only " + part.available() + " available");
            }
        });
        if (!problems.isEmpty()) {
            String reason = String.join("; ", problems);
            log.info("Rejecting parts request {} for RO {}: {}", request.requestId(), request.repairOrderId(), reason);
            outbox.append(Topics.INVENTORY_EVENTS, AggregateTypes.RESERVATION, request.repairOrderId(),
                    new PartsReservationFailed(request.requestId(), request.repairOrderId(), reason));
            return;
        }

        List<ReservationLine> lines = new ArrayList<>();
        List<ReservedLine> reserved = new ArrayList<>();
        wanted.forEach((sku, qty) -> {
            Part part = locked.get(sku);
            part.reserve(qty);
            lines.add(new ReservationLine(sku, qty, part.getUnitPrice()));
            reserved.add(new ReservedLine(sku, part.getName(), qty, part.getUnitPrice()));
        });
        reservations.save(new Reservation(request.requestId(), request.repairOrderId(), request.dealerId(), lines));
        outbox.append(Topics.INVENTORY_EVENTS, AggregateTypes.RESERVATION, request.repairOrderId(),
                new PartsReserved(request.requestId(), request.repairOrderId(), reserved));
        log.info("Reserved {} line(s) for RO {}", lines.size(), request.repairOrderId());
    }

    /** Repair order completed: reserved parts were fitted, so they leave stock for good. */
    @Transactional
    public void consumeFor(UUID repairOrderId) {
        settle(repairOrderId, ReservationStatus.CONSUMED);
    }

    /** Repair order cancelled: compensating action that returns reserved stock to the shelf. */
    @Transactional
    public void releaseFor(UUID repairOrderId) {
        settle(repairOrderId, ReservationStatus.RELEASED);
    }

    private void settle(UUID repairOrderId, ReservationStatus outcome) {
        for (Reservation reservation : reservations.findByRepairOrderIdAndStatus(repairOrderId, ReservationStatus.RESERVED)) {
            Map<String, Part> locked = parts.lockForUpdate(reservation.getDealerId(),
                            reservation.getLines().stream().map(ReservationLine::sku).toList()).stream()
                    .collect(Collectors.toMap(Part::getSku, Function.identity()));
            for (ReservationLine line : reservation.getLines()) {
                Part part = locked.get(line.sku());
                if (outcome == ReservationStatus.CONSUMED) {
                    part.consume(line.quantity());
                    if (part.isLowStock()) {
                        outbox.append(Topics.INVENTORY_EVENTS, AggregateTypes.PART, part.getDealerId() + ":" + part.getSku(),
                                new PartLowStock(part.getDealerId(), part.getSku(), part.getName(),
                                        part.available(), part.getReorderLevel()));
                    }
                } else {
                    part.release(line.quantity());
                }
            }
            reservation.settle(outcome);
            log.info("Reservation {} for RO {} {}", reservation.getId(), repairOrderId, outcome);
        }
    }

    @Transactional
    public Part restock(String dealerId, String sku, int quantity) {
        Part part = parts.findByDealerIdAndSku(dealerId, sku)
                .orElseThrow(() -> ApiException.notFound("Part", dealerId + "/" + sku));
        part.restock(quantity);
        return part;
    }
}
