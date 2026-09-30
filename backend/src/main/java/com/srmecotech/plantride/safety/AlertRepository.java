package com.srmecotech.plantride.safety;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    @Query("""
            select a from Alert a
            left join fetch a.vehicle
            left join fetch a.booking
            left join fetch a.raisedBy
            where a.status in :statuses
            order by a.createdAt desc
            """)
    List<Alert> findByStatuses(@Param("statuses") Collection<AlertStatus> statuses);

    /** True if this condition was already raised and is not resolved, or was raised after {@code since}. */
    @Query("""
            select count(a) > 0 from Alert a
            where a.dedupeKey = :key
              and (a.status <> com.srmecotech.plantride.safety.AlertStatus.RESOLVED or a.createdAt >= :since)
            """)
    boolean existsRecent(@Param("key") String dedupeKey, @Param("since") LocalDateTime since);

    @Query("""
            select a from Alert a
            where a.vehicle.id = :vehicleId
              and a.type = :type
              and a.status <> com.srmecotech.plantride.safety.AlertStatus.RESOLVED
            """)
    List<Alert> findUnresolvedForVehicle(@Param("vehicleId") Long vehicleId, @Param("type") AlertType type);
}
