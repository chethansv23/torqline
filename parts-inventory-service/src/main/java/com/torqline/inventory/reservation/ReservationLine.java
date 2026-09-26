package com.torqline.inventory.reservation;

import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

@Embeddable
public record ReservationLine(String sku, int quantity, BigDecimal unitPrice) {
}
