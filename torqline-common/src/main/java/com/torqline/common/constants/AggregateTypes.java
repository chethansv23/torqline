package com.torqline.common.constants;

/** Aggregate names recorded on outbox rows, so events can be traced back to what produced them. */
public final class AggregateTypes {
    public static final String APPOINTMENT = "Appointment";
    public static final String REPAIR_ORDER = "RepairOrder";
    public static final String RESERVATION = "Reservation";
    public static final String PART = "Part";

    private AggregateTypes() {
    }
}
