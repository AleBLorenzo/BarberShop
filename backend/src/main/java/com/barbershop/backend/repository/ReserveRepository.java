package com.barbershop.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.barbershop.backend.model.Reserve;

public interface ReserveRepository extends JpaRepository<Reserve, Long> {
    List<Reserve> findAllByUserId_Id(Long userId);

}
