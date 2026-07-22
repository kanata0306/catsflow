package com.catsflow.catsflow.hello;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

import com.catsflow.catsflow.hello.Repository.HelloRepository;

@Service
public class HelloService {

    private final HelloRepository repository;

    public HelloService(HelloRepository helloRepository) {
        this.repository = helloRepository;
    }

    public List<Map<String, Object>> getTransactions(Long userId) {
        return repository.findByUserId(userId);
    }
}