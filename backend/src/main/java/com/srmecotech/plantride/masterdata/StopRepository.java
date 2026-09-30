package com.srmecotech.plantride.masterdata;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StopRepository extends JpaRepository<Stop, Long> {

    List<Stop> findByActiveTrueOrderByNameAsc();
}
