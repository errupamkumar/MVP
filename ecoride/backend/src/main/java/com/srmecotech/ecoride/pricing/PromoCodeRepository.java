package com.srmecotech.ecoride.pricing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PromoCodeRepository extends JpaRepository<PromoCode, String> {

    List<PromoCode> findAllByOrderByCreatedAtDesc();

    List<PromoCode> findByActiveTrueOrderByCreatedAtDesc();

    /** Rides that used the code and were not cancelled before a driver came. */
    @Query(value = """
            SELECT COUNT(*) FROM ride WHERE rider_id = :riderId AND promo_code = :code
            AND status IN ('SEARCHING', 'ACCEPTED', 'ARRIVED', 'IN_PROGRESS', 'COMPLETED')
            """, nativeQuery = true)
    int usesBy(@Param("riderId") long riderId, @Param("code") String code);

    @Query(value = "SELECT COUNT(*) FROM ride WHERE promo_code = :code AND status = 'COMPLETED'", nativeQuery = true)
    int totalUses(@Param("code") String code);
}
