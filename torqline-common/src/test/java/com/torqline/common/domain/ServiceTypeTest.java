package com.torqline.common.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceTypeTest {

    @Test
    void bikeJobsAreShorterThanCarJobs() {
        assertThat(ServiceType.GENERAL_SERVICE.durationFor(VehicleType.CAR)).isEqualTo(Duration.ofMinutes(120));
        assertThat(ServiceType.GENERAL_SERVICE.durationFor(VehicleType.BIKE)).isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    void vehicleSpecificJobsAreRejectedForTheOtherType() {
        assertThat(ServiceType.CHAIN_SPROCKET.supports(VehicleType.CAR)).isFalse();
        assertThat(ServiceType.WHEEL_ALIGNMENT.supports(VehicleType.BIKE)).isFalse();
        assertThatThrownBy(() -> ServiceType.AC_SERVICE.durationFor(VehicleType.BIKE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyDurationFitsTheThirtyMinuteGrid() {
        Arrays.stream(ServiceType.values()).forEach(type ->
                Arrays.stream(VehicleType.values()).filter(type::supports).forEach(vehicle ->
                        assertThat(type.durationFor(vehicle).toMinutes() % 30).as(type + "/" + vehicle).isZero()));
    }
}
