package com.torqline.inventory.part;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PartRepository extends JpaRepository<Part, Long> {

    List<Part> findByDealerIdOrderBySku(String dealerId);

    List<Part> findByDealerIdAndFitmentInOrderBySku(String dealerId, Collection<Fitment> fitments);

    Optional<Part> findByDealerIdAndSku(String dealerId, String sku);

    /**
     * Row-locks the requested SKUs. Locks are always taken in SKU order so two reservations that
     * touch overlapping parts queue behind each other instead of deadlocking.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Part p where p.dealerId = :dealerId and p.sku in :skus order by p.sku")
    List<Part> lockForUpdate(@Param("dealerId") String dealerId, @Param("skus") Collection<String> skus);
}
