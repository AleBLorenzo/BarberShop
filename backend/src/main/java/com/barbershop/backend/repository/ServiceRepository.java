package com.barbershop.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.barbershop.backend.model.Services;

public interface ServiceRepository extends JpaRepository<Services, Long> {
    Services findByName(String name);

}
