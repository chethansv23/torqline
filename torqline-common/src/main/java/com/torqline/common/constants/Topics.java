package com.torqline.common.constants;

import java.util.List;

/** Kafka topic names. Events are keyed by aggregate id so one aggregate's events stay in order. */
public final class Topics {
    public static final String APPOINTMENT_EVENTS = "torqline.appointment.events";
    public static final String REPAIR_ORDER_EVENTS = "torqline.repair-order.events";
    public static final String INVENTORY_EVENTS = "torqline.inventory.events";

    public static final List<String> ALL = List.of(APPOINTMENT_EVENTS, REPAIR_ORDER_EVENTS, INVENTORY_EVENTS);

    private Topics() {
    }
}
