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

import com.barbershop.backend.model.Services;
import com.barbershop.backend.service.ServiceService;

@RestController
@RequestMapping("/api/services")
public class ServicesController {

    private final ServiceService serviceService;

    public ServicesController(ServiceService serviceService) {
        this.serviceService = serviceService;
    }

    @GetMapping
    public List<Services> getAllServices() {
        return serviceService.getAllServices();
    }

    @GetMapping("/{id}")
    public Services getServiceById(@PathVariable Long id) {
        return serviceService.getServiceById(id);
    }

    @GetMapping("/name/{name}")
    public Services getServiceByName(@PathVariable String name) {
        return serviceService.getServiceByName(name);
    }

    @PostMapping
    public Services createService(@RequestBody Services service) {
        return serviceService.createService(service);
    }

    @PutMapping("/{id}")
    public Services updateService(@PathVariable Long id, @RequestBody Services service) {
        return serviceService.updateService(id, service);
    }

    @DeleteMapping("/{id}")
    public void deleteService(@PathVariable Long id) {
        serviceService.deleteService(id);
    }
}
