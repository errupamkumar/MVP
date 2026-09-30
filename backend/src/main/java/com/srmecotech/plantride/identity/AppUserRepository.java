package com.srmecotech.plantride.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** The column collation is case-insensitive, so "lhs-40218" finds "LHS-40218". */
    Optional<AppUser> findByUsername(String username);

    boolean existsByIdAndActiveTrue(Long id);
}
