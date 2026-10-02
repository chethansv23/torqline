package com.torqline.repairorder.constants;

import java.math.BigDecimal;

public final class RepairOrderConstants {

    /** GST on vehicle servicing in India. */
    public static final BigDecimal GST_RATE = new BigDecimal("0.18");

    /** Repair-order number, e.g. RO-2026-001042: year and a zero-padded database sequence value. */
    public static final String RO_NUMBER_FORMAT = "RO-%d-%06d";

    public static final String RO_NUMBER_SEQUENCE_QUERY = "select nextval('ro_number_seq')";

    private RepairOrderConstants() {
    }
}
