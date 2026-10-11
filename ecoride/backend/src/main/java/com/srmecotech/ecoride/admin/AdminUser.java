package com.srmecotech.ecoride.admin;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** An operations account for the admin console (full access). */
@Entity
@Table(name = "admin_user")
@Getter
@Setter
public class AdminUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;
    private String passwordHash;
    private Instant createdAt;
    private Instant lastLoginAt;
}
