package com.barbershop.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.barbershop.backend.model.Services;
import com.barbershop.backend.repository.ServiceRepository;

@Service 

public class ServiceService {

    private final ServiceRepository serviceRepository;

    public ServiceService(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    public List<Services> getAllServices() {
        return serviceRepository.findAll();
    }

    public Services getServiceById(Long id) {
        return serviceRepository.findById(id).orElse(null);
    }

    public Services getServiceByName(String name) {
        return serviceRepository.findByName(name);
    }

    public Services createService(Services service) {
        return serviceRepository.save(service);
    }

    public Services updateService(Long id, Services service) {
        Services existingService = serviceRepository.findById(id).orElse(null);
        if (existingService != null) {
            existingService.setName(service.getName());
            existingService.setPrice(service.getPrice());
            existingService.setDuration(service.getDuration());
            return serviceRepository.save(existingService);
        }
        return null;
    }

    public void deleteService(Long id) {
        if (serviceRepository.existsById(id)) {
            serviceRepository.deleteById(id);
        }
    }

}
