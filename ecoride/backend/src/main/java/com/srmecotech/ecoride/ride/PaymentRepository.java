package com.srmecotech.ecoride.ride;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByRideIdOrderByIdAsc(Long rideId);

    Optional<Payment> findFirstByRideIdAndKindOrderByIdDesc(Long rideId, String kind);

    Page<Payment> findAllByOrderByIdDesc(Pageable pageable);
}
