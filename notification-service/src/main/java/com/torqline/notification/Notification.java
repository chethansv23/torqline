package com.torqline.notification;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification")
public class Notification {

    @Id
    private UUID id;
    private UUID eventId;
    private String eventType;
    @Enumerated(EnumType.STRING)
    private Channel channel;
    private String recipient;
    private String message;
    private Instant sentAt;

    protected Notification() {
    }

    public Notification(UUID eventId, String eventType, Channel channel, String recipient, String message) {
        this.id = UUID.randomUUID();
        this.eventId = eventId;
        this.eventType = eventType;
        this.channel = channel;
        this.recipient = recipient;
        this.message = message;
        this.sentAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public Channel getChannel() { return channel; }
    public String getRecipient() { return recipient; }
    public String getMessage() { return message; }
    public Instant getSentAt() { return sentAt; }
}
