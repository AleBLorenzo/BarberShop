package com.barbershop.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.barbershop.backend.model.Worker;

public interface WorkerRepository extends JpaRepository<Worker, Long> {
    Worker findByAvailable(boolean available);

}
