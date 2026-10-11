package com.srmecotech.ecoride.pricing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface FareQuoteRepository extends JpaRepository<FareQuote, String> {

    @Modifying
    @Query("delete from FareQuote q where q.expiresAt < :before")
    int deleteExpired(@Param("before") Instant before);
}
