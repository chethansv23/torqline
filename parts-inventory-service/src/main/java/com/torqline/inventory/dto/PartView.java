package com.torqline.inventory.dto;

import com.torqline.inventory.part.Fitment;
import com.torqline.inventory.part.Part;

import java.math.BigDecimal;

public record PartView(String dealerId, String sku, String name, Fitment fitment, BigDecimal unitPrice,
                       int onHand, int reserved, int available, int reorderLevel, boolean lowStock) {
    public static PartView of(Part p) {
        return new PartView(p.getDealerId(), p.getSku(), p.getName(), p.getFitment(), p.getUnitPrice(),
                p.getOnHand(), p.getReserved(), p.available(), p.getReorderLevel(), p.isLowStock());
    }
}
