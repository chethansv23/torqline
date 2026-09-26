package com.torqline.appointment.slot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * Read-through Redis cache of a dealer's booked intervals for one day. Availability is the hottest
 * read path (every customer browsing slots), while bookings are comparatively rare.
 *
 * <p>The cache is only an optimisation: correctness comes from the Postgres exclusion constraint,
 * so a stale entry can at worst show a slot that then fails to book with 409. Redis being down
 * degrades to reading from Postgres rather than failing the request.
 */
@Component
public class BusySlotCache {

    private static final Logger log = LoggerFactory.getLogger(BusySlotCache.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final Duration ttl;

    public BusySlotCache(StringRedisTemplate redis, ObjectMapper mapper,
                         @Value("${torqline.cache.enabled:true}") boolean enabled,
                         @Value("${torqline.cache.busy-slots-ttl:60s}") Duration ttl) {
        this.redis = redis;
        this.mapper = mapper;
        this.enabled = enabled;
        this.ttl = ttl;
    }

    public List<BusyInterval> get(String dealerId, LocalDate date, Supplier<List<BusyInterval>> loader) {
        if (!enabled) {
            return loader.get();
        }
        String key = key(dealerId, date);
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return Arrays.asList(mapper.readValue(cached, BusyInterval[].class));
            }
        } catch (RuntimeException e) {
            log.warn("Redis read failed for {}, falling back to database: {}", key, e.getMessage());
            return loader.get();
        }
        List<BusyInterval> loaded = loader.get();
        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(loaded), ttl);
        } catch (RuntimeException e) {
            log.warn("Redis write failed for {}: {}", key, e.getMessage());
        }
        return loaded;
    }

    public void evict(String dealerId, LocalDate date) {
        if (!enabled) {
            return;
        }
        try {
            redis.delete(key(dealerId, date));
        } catch (RuntimeException e) {
            log.warn("Redis evict failed for {} {}: {}", dealerId, date, e.getMessage());
        }
    }

    private static String key(String dealerId, LocalDate date) {
        return "torqline:busy:" + dealerId + ":" + date;
    }
}
