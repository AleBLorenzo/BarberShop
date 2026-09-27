package com.barbershop.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.barbershop.backend.model.Reserve;
import com.barbershop.backend.repository.ReserveRepository;

@Service

public class ReserveService {

    private final ReserveRepository reserveRepository;

    public ReserveService(ReserveRepository reserveRepository) {
        this.reserveRepository = reserveRepository;
    }

    public List<Reserve> getAllReserves() {
        return reserveRepository.findAll();
    }

    public Reserve getReserveById(Long id) {
        return reserveRepository.findById(id).orElse(null);
    }

    public List<Reserve> getReserveByUserId(Long userId) {
        return reserveRepository.findAllByUserId_Id(userId);
    }

    public Reserve saveReserve(Reserve reserve) {
        return reserveRepository.save(reserve);
    }

    public Reserve updateReserve(Long id, Reserve reserve) {
        Reserve existingReserve = reserveRepository.findById(id).orElse(null);
        if (existingReserve != null) {
            existingReserve.setUserId(reserve.getUserId());
            existingReserve.setWorkerId(reserve.getWorkerId());
            existingReserve.setServiceId(reserve.getServiceId());
            existingReserve.setFecha(reserve.getFecha());
            existingReserve.setHoraInicio(reserve.getHoraInicio());
            existingReserve.setHoraFin(reserve.getHoraFin());
            existingReserve.setEstado(reserve.getEstado());
            return reserveRepository.save(existingReserve);
        }
        return null;
    }

    public void deleteReserve(Long id) {
        if (reserveRepository.existsById(id)) {
            reserveRepository.deleteById(id);
        }
    }

}
