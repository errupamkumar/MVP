package com.srmecotech.plantride.masterdata;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RouteRepository extends JpaRepository<Route, Long> {

    @Query("""
            select distinct r from Route r
            left join fetch r.stops rs
            left join fetch rs.stop
            where r.active = true
            order by r.code
            """)
    List<Route> findActiveWithStops();

    @Query("""
            select distinct r from Route r
            left join fetch r.stops rs
            left join fetch rs.stop
            order by r.code
            """)
    List<Route> findAllWithStops();

    @Query("""
            select r from Route r
            left join fetch r.stops rs
            left join fetch rs.stop
            where r.id = :id
            """)
    Optional<Route> findWithStops(@Param("id") Long id);

    /**
     * SELECT ... FOR UPDATE on the route row. Matching holds this lock while it
     * reads seat loads and attaches a booking, so two riders can never both
     * take the last seat on the same route.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Route r where r.id = :id")
    Optional<Route> lockById(@Param("id") Long id);
}
