package com.torqline.inventory.reservation;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reservation")
public class Reservation {

    @Id
    private UUID id;
    private UUID requestId;
    private UUID repairOrderId;
    private String dealerId;
    @Enumerated(EnumType.STRING)
    private ReservationStatus status;
    private Instant createdAt;
    private Instant settledAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "reservation_line", joinColumns = @JoinColumn(name = "reservation_id"))
    private List<ReservationLine> lines = new ArrayList<>();

    protected Reservation() {
    }

    public Reservation(UUID requestId, UUID repairOrderId, String dealerId, List<ReservationLine> lines) {
        this.id = UUID.randomUUID();
        this.requestId = requestId;
        this.repairOrderId = repairOrderId;
        this.dealerId = dealerId;
        this.status = ReservationStatus.RESERVED;
        this.createdAt = Instant.now();
        this.lines.addAll(lines);
    }

    public void settle(ReservationStatus outcome) {
        this.status = outcome;
        this.settledAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getRequestId() { return requestId; }
    public UUID getRepairOrderId() { return repairOrderId; }
    public String getDealerId() { return dealerId; }
    public ReservationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSettledAt() { return settledAt; }
    public List<ReservationLine> getLines() { return lines; }
}
