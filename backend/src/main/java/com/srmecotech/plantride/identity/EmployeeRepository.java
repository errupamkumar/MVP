package com.srmecotech.plantride.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    @Query("select e from Employee e join fetch e.user join fetch e.costCentre where e.user.id = :userId")
    Optional<Employee> findByUserId(@Param("userId") Long userId);
}
