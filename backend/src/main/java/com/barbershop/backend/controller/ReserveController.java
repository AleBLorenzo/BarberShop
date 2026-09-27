package com.barbershop.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.barbershop.backend.model.Reserve;
import com.barbershop.backend.service.ReserveService;

@RestController
@RequestMapping("/api/reserves")
public class ReserveController {

    private final ReserveService reserveService;

    public ReserveController(ReserveService reserveService) {
        this.reserveService = reserveService;
    }

    @GetMapping
    public List<Reserve> getAllReserves() {
        return reserveService.getAllReserves();
    }

    @GetMapping("/{id}")
    public Reserve getReserveById(@PathVariable Long id) {
        return reserveService.getReserveById(id);
    }

    @GetMapping("/user/{userId}")
    public List<Reserve> getReserveByUserId(@PathVariable Long userId) {
        return reserveService.getReserveByUserId(userId);
    }

    @PostMapping
    public Reserve createReserve(@RequestBody Reserve reserve) {
        return reserveService.saveReserve(reserve);
    }

    @PutMapping("/{id}")
    public Reserve updateReserve(@PathVariable Long id, @RequestBody Reserve reserve) {
        return reserveService.updateReserve(id, reserve);
    }

    @DeleteMapping("/{id}")
    public void deleteReserve(@PathVariable Long id) {
        reserveService.deleteReserve(id);
    }
}
