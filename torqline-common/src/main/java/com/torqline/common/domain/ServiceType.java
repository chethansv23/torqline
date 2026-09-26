package com.torqline.common.domain;

import java.time.Duration;

/**
 * Catalogue of service jobs. Each job has a bay duration per vehicle type; {@code null} means
 * the job does not apply to that vehicle type (a bike has no wheel alignment, a car has no chain).
 * Durations are multiples of the 30-minute booking grid.
 */
public enum ServiceType {
    GENERAL_SERVICE("Periodic maintenance service", 120, 60),
    OIL_CHANGE("Engine oil and filter change", 30, 30),
    BRAKE_SERVICE("Brake inspection and pad replacement", 90, 60),
    TYRE_REPLACEMENT("Tyre replacement and balancing", 60, 30),
    CLUTCH_OVERHAUL("Clutch overhaul", 180, 90),
    WHEEL_ALIGNMENT("Wheel alignment", 60, null),
    AC_SERVICE("Air-conditioning service", 90, null),
    CHAIN_SPROCKET("Chain and sprocket kit replacement", null, 60);

    private final String description;
    private final Integer carMinutes;
    private final Integer bikeMinutes;

    ServiceType(String description, Integer carMinutes, Integer bikeMinutes) {
        this.description = description;
        this.carMinutes = carMinutes;
        this.bikeMinutes = bikeMinutes;
    }

    public String description() {
        return description;
    }

    public boolean supports(VehicleType vehicleType) {
        return minutesFor(vehicleType) != null;
    }

    public Duration durationFor(VehicleType vehicleType) {
        Integer minutes = minutesFor(vehicleType);
        if (minutes == null) {
            throw new IllegalArgumentException(name() + " is not offered for " + vehicleType);
        }
        return Duration.ofMinutes(minutes);
    }

    private Integer minutesFor(VehicleType vehicleType) {
        return switch (vehicleType) {
            case CAR -> carMinutes;
            case BIKE -> bikeMinutes;
        };
    }
}
