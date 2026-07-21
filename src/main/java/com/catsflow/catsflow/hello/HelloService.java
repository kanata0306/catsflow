package com.catsflow.catsflow.hello;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.catsflow.catsflow.hello.Repository.HelloRepository;

@Service
public class HelloService {
    @Autowired
    private HelloRepository repository;

    public List<Map<String, Object>> getTransactions(Long userId) {
        return repository.findByUserId(userId);
    }
}