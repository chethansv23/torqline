package com.torqline.repairorder.order;

import com.torqline.common.events.AppointmentEvents.AppointmentCheckedIn;
import com.torqline.common.events.InventoryEvents.PartsReservationFailed;
import com.torqline.common.events.InventoryEvents.PartsReserved;
import com.torqline.common.events.RepairOrderEvents.PartQuantity;
import com.torqline.common.events.RepairOrderEvents.PartsReservationRequested;
import com.torqline.common.events.RepairOrderEvents.RepairOrderCancelled;
import com.torqline.common.events.RepairOrderEvents.RepairOrderCompleted;
import com.torqline.common.events.RepairOrderEvents.RepairOrderCreated;
import com.torqline.common.events.Topics;
import com.torqline.common.messaging.OutboxWriter;
import com.torqline.common.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RepairOrderService {

    private static final Logger log = LoggerFactory.getLogger(RepairOrderService.class);
    private static final String AGGREGATE = "RepairOrder";

    private final RepairOrderRepository orders;
    private final OutboxWriter outbox;
    private final JdbcTemplate jdbc;

    public RepairOrderService(RepairOrderRepository orders, OutboxWriter outbox, JdbcTemplate jdbc) {
        this.orders = orders;
        this.outbox = outbox;
        this.jdbc = jdbc;
    }

    @Transactional
    public void openFromCheckIn(AppointmentCheckedIn event) {
        if (orders.existsByAppointmentId(event.appointmentId())) {
            log.info("Repair order already exists for appointment {}", event.appointmentId());
            return;
        }
        RepairOrder ro = orders.save(RepairOrder.openFrom(event, nextRoNumber()));
        outbox.append(Topics.REPAIR_ORDER_EVENTS, AGGREGATE, ro.getId(), new RepairOrderCreated(ro.getId(),
                ro.getRoNumber(), ro.getAppointmentId(), ro.getDealerId(), ro.getCustomerName(),
                ro.getCustomerPhone(), ro.getVehicleNumber()));
        log.info("Opened {} for {} {}", ro.getRoNumber(), ro.getVehicleType(), ro.getVehicleNumber());
    }

    @Transactional(readOnly = true)
    public RepairOrder get(UUID id) {
        return orders.findById(id).orElseThrow(() -> ApiException.notFound("Repair order", id));
    }

    @Transactional(readOnly = true)
    public List<RepairOrder> list(String dealerId, RepairOrderStatus status) {
        return status == null
                ? orders.findByDealerIdOrderByOpenedAtDesc(dealerId)
                : orders.findByDealerIdAndStatusOrderByOpenedAtDesc(dealerId, status);
    }

    @Transactional
    public RepairOrder assign(UUID id, String technician) {
        RepairOrder ro = get(id);
        ro.assign(technician);
        return ro;
    }

    /** Saga step 1: hold the order in PARTS_PENDING and ask inventory to reserve stock. */
    @Transactional
    public RepairOrder requestParts(UUID id, List<PartQuantity> lines) {
        RepairOrder ro = get(id);
        Map<String, Integer> merged = new LinkedHashMap<>();
        lines.forEach(l -> merged.merge(l.sku().trim().toUpperCase(), l.quantity(), Integer::sum));
        UUID requestId = UUID.randomUUID();
        ro.requestParts(requestId, merged);
        outbox.append(Topics.REPAIR_ORDER_EVENTS, AGGREGATE, ro.getId(), new PartsReservationRequested(requestId,
                ro.getId(), ro.getDealerId(),
                merged.entrySet().stream().map(e -> new PartQuantity(e.getKey(), e.getValue())).toList()));
        return ro;
    }

    @Transactional
    public void onPartsReserved(PartsReserved event) {
        orders.findById(event.repairOrderId()).ifPresent(ro -> {
            if (!ro.partsReserved(event.requestId(), event.lines())) {
                log.info("Ignoring stale PartsReserved for {}", ro.getRoNumber());
            }
        });
    }

    @Transactional
    public void onPartsReservationFailed(PartsReservationFailed event) {
        orders.findById(event.repairOrderId()).ifPresent(ro -> {
            if (!ro.partsRejected(event.requestId(), event.reason())) {
                log.info("Ignoring stale PartsReservationFailed for {}", ro.getRoNumber());
            }
        });
    }

    @Transactional
    public RepairOrder complete(UUID id) {
        RepairOrder ro = get(id);
        ro.complete();
        outbox.append(Topics.REPAIR_ORDER_EVENTS, AGGREGATE, ro.getId(), new RepairOrderCompleted(ro.getId(),
                ro.getRoNumber(), ro.getDealerId(), ro.getCustomerName(), ro.getCustomerPhone(),
                ro.getVehicleNumber(), ro.getTotalAmount()));
        return ro;
    }

    /** Compensation: inventory releases whatever it reserved for this order. */
    @Transactional
    public RepairOrder cancel(UUID id, String reason) {
        RepairOrder ro = get(id);
        ro.cancel(reason);
        outbox.append(Topics.REPAIR_ORDER_EVENTS, AGGREGATE, ro.getId(),
                new RepairOrderCancelled(ro.getId(), ro.getDealerId(), reason));
        return ro;
    }

    private String nextRoNumber() {
        Long seq = jdbc.queryForObject("select nextval('ro_number_seq')", Long.class);
        return "RO-%d-%06d".formatted(Year.now().getValue(), seq);
    }
}
