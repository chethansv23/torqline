package com.torqline.repairorder.order;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "part_line")
public class PartLine {

    @Id
    private UUID id;
    private UUID requestId;
    private String sku;
    private String name;
    private int quantity;
    private BigDecimal unitPrice;
    @Enumerated(EnumType.STRING)
    private PartLineStatus status;

    protected PartLine() {
    }

    PartLine(UUID requestId, String sku, int quantity) {
        this.id = UUID.randomUUID();
        this.requestId = requestId;
        this.sku = sku;
        this.quantity = quantity;
        this.status = PartLineStatus.REQUESTED;
    }

    void reserve(String name, BigDecimal unitPrice) {
        this.name = name;
        this.unitPrice = unitPrice;
        this.status = PartLineStatus.RESERVED;
    }

    void reject() {
        this.status = PartLineStatus.REJECTED;
    }

    BigDecimal amount() {
        return status == PartLineStatus.RESERVED ? unitPrice.multiply(BigDecimal.valueOf(quantity)) : BigDecimal.ZERO;
    }

    public UUID getId() { return id; }
    public UUID getRequestId() { return requestId; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public PartLineStatus getStatus() { return status; }
}
