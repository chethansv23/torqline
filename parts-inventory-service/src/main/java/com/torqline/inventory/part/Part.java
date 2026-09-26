package com.torqline.inventory.part;

import com.torqline.common.web.ApiException;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;

/**
 * Stock for one SKU at one dealer. {@code reserved} is stock promised to open repair orders but not
 * yet fitted; {@code available = onHand - reserved} is what new requests can draw from.
 */
@Entity
@Table(name = "part")
public class Part {

    @Id
    private Long id;
    private String dealerId;
    private String sku;
    private String name;
    @Enumerated(EnumType.STRING)
    private Fitment fitment;
    private BigDecimal unitPrice;
    private int onHand;
    private int reserved;
    private int reorderLevel;
    @Version
    private Long version;

    protected Part() {
    }

    public int available() {
        return onHand - reserved;
    }

    public boolean isLowStock() {
        return available() <= reorderLevel;
    }

    void reserve(int quantity) {
        if (quantity > available()) {
            throw new IllegalStateException("Reserving " + quantity + " of " + sku + " but only " + available() + " available");
        }
        reserved += quantity;
    }

    void release(int quantity) {
        reserved -= quantity;
    }

    void consume(int quantity) {
        reserved -= quantity;
        onHand -= quantity;
    }

    void restock(int quantity) {
        if (quantity <= 0) {
            throw ApiException.badRequest("INVALID_QUANTITY", "Restock quantity must be positive");
        }
        onHand += quantity;
    }

    public Long getId() { return id; }
    public String getDealerId() { return dealerId; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public Fitment getFitment() { return fitment; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public int getOnHand() { return onHand; }
    public int getReserved() { return reserved; }
    public int getReorderLevel() { return reorderLevel; }
}
