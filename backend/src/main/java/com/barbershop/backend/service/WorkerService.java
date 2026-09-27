package com.barbershop.backend.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.barbershop.backend.model.Worker;
import com.barbershop.backend.repository.WorkerRepository;

@Service 

public class WorkerService {

    private final  WorkerRepository workerRepository;

    @Autowired
    public WorkerService(WorkerRepository workerRepository) {
        this.workerRepository = workerRepository;
    }

    public List<Worker> getAllWorkers() {
        return workerRepository.findAll();
    }
    public Worker getWorkerById(Long id) {
        return workerRepository.findById(id).orElse(null);
    }
    public Worker getWorkerByAvailable(boolean availability) {
        return workerRepository.findByAvailable(availability);
    }

    public Worker createWorker(Worker worker) {
        return workerRepository.save(worker);
    }
    public Worker updateWorker(Long id, Worker worker) {
        Worker existingWorker = workerRepository.findById(id).orElse(null);
        if (existingWorker != null) {
            existingWorker.setName(worker.getName());
            existingWorker.setAvailable(worker.getAvailable());
            return workerRepository.save(existingWorker);
        }
        return null;
    }
    public void deleteWorker(Long id) {
        if (workerRepository.existsById(id)) {
            workerRepository.deleteById(id);
        }
    }
    
    


}
