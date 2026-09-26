package com.torqline.common.domain;

import java.math.BigDecimal;

/**
 * Torqline services both four-wheelers and two-wheelers. The vehicle type drives which
 * service bays can be used, how long a job takes and the labour rate charged.
 */
public enum VehicleType {
    CAR(new BigDecimal("800")),
    BIKE(new BigDecimal("400"));

    /** Labour rate in INR per hour. */
    private final BigDecimal hourlyLabourRate;

    VehicleType(BigDecimal hourlyLabourRate) {
        this.hourlyLabourRate = hourlyLabourRate;
    }

    public BigDecimal hourlyLabourRate() {
        return hourlyLabourRate;
    }
}
