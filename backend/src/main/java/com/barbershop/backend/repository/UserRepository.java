package com.barbershop.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.barbershop.backend.model.User;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByName(String name);
    User findByPhone(String phone);

}
