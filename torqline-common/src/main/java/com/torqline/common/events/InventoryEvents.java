package com.torqline.common.events;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Events published by parts-inventory-service on {@link Topics#INVENTORY_EVENTS}. */
public final class InventoryEvents {

    public record ReservedLine(String sku, String name, int quantity, BigDecimal unitPrice) {
    }

    public record PartsReserved(UUID requestId, UUID repairOrderId, List<ReservedLine> lines) {
    }

    public record PartsReservationFailed(UUID requestId, UUID repairOrderId, String reason) {
    }

    public record PartLowStock(String dealerId, String sku, String name, int available, int reorderLevel) {
    }

    private InventoryEvents() {
    }
}
