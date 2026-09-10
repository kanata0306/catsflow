package com.catsflow.catsflow.hello.Servise;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

import com.catsflow.catsflow.hello.Repository.TransactionRepository;

@Service
public class TransactionService {

    private final TransactionRepository repository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.repository = transactionRepository;
    }

    public List<Map<String, Object>> getTransactions(Long userId) {
        return repository.findByUserId(userId);
    }
}