package com.srmecotech.plantride.masterdata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RouteStopRepository extends JpaRepository<RouteStop, Long> {

    /**
     * Bulk delete, executed immediately. Resequencing must remove the old rows
     * before inserting the new ones: left to Hibernate's flush order, inserts
     * run before deletes and hit uk_route_stop_seq.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RouteStop rs where rs.route.id = :routeId")
    int deleteByRouteId(@Param("routeId") Long routeId);
}
