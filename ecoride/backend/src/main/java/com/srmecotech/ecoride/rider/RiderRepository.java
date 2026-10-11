package com.srmecotech.ecoride.rider;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface RiderRepository extends JpaRepository<Rider, Long> {

    Optional<Rider> findByPhone(String phone);

    Optional<Rider> findByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);

    boolean existsByEmailIgnoreCase(String email);

    @Query("""
            select r from Rider r
            where (r.simulated = false or :includeSimulated = true)
            and (:q is null or lower(r.name) like lower(concat('%', :q, '%')) or r.phone like concat('%', :q, '%'))
            """)
    Page<Rider> search(@Param("q") String q, @Param("includeSimulated") boolean includeSimulated, Pageable pageable);

    /** Debit only if the balance covers it; returns 0 when it does not. */
    @Modifying(flushAutomatically = true)
    @Query("update Rider r set r.walletBalance = r.walletBalance - :amount where r.id = :id and r.walletBalance >= :amount")
    int debitWallet(@Param("id") Long id, @Param("amount") BigDecimal amount);

    @Modifying(flushAutomatically = true)
    @Query("update Rider r set r.walletBalance = r.walletBalance + :amount where r.id = :id")
    int creditWallet(@Param("id") Long id, @Param("amount") BigDecimal amount);

    @Query("select r.walletBalance from Rider r where r.id = :id")
    BigDecimal walletBalance(@Param("id") Long id);

    /** Running average; the right-hand side reads the values before this update. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE rider SET rating_avg = ROUND((rating_avg * rating_count + :stars) / (rating_count + 1), 2),
                             rating_count = rating_count + 1
            WHERE id = :id
            """, nativeQuery = true)
    int addRating(@Param("id") Long id, @Param("stars") int stars);
}
