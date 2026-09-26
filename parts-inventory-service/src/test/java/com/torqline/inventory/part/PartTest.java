package com.torqline.inventory.part;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartTest {

    private static Part part(int onHand, int reorderLevel) {
        Part part = org.springframework.beans.BeanUtils.instantiateClass(Part.class);
        ReflectionTestUtils.setField(part, "sku", "CHAIN-KIT");
        ReflectionTestUtils.setField(part, "onHand", onHand);
        ReflectionTestUtils.setField(part, "reorderLevel", reorderLevel);
        return part;
    }

    @Test
    void reservingReducesAvailableButNotOnHand() {
        Part part = part(5, 2);
        part.reserve(2);

        assertThat(part.getOnHand()).isEqualTo(5);
        assertThat(part.available()).isEqualTo(3);
    }

    @Test
    void cannotReserveMoreThanAvailable() {
        Part part = part(3, 1);
        part.reserve(2);

        assertThatThrownBy(() -> part.reserve(2)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void consumeRemovesFromShelfAndReleaseReturnsToIt() {
        Part part = part(5, 2);
        part.reserve(2);
        part.consume(1);
        part.release(1);

        assertThat(part.getOnHand()).isEqualTo(4);
        assertThat(part.getReserved()).isZero();
        assertThat(part.available()).isEqualTo(4);
    }

    @Test
    void lowStockWhenAvailableReachesReorderLevel() {
        Part part = part(3, 2);
        assertThat(part.isLowStock()).isFalse();
        part.reserve(1);
        assertThat(part.isLowStock()).isTrue();
    }
}
