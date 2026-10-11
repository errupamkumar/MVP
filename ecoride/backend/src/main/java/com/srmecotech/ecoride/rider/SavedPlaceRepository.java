package com.srmecotech.ecoride.rider;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedPlaceRepository extends JpaRepository<SavedPlace, Long> {

    List<SavedPlace> findByRiderIdOrderByLabelAscIdAsc(Long riderId);

    Optional<SavedPlace> findFirstByRiderIdAndLabel(Long riderId, String label);
}
