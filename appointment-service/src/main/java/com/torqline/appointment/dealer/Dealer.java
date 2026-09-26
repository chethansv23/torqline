package com.torqline.appointment.dealer;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalTime;
import java.time.ZoneId;

@Entity
@Table(name = "dealer")
public class Dealer {

    @Id
    private String id;
    private String name;
    private String city;
    private String timezone;
    private LocalTime openTime;
    private LocalTime closeTime;

    protected Dealer() {
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCity() {
        return city;
    }

    public ZoneId zone() {
        return ZoneId.of(timezone);
    }

    public LocalTime getOpenTime() {
        return openTime;
    }

    public LocalTime getCloseTime() {
        return closeTime;
    }
}
