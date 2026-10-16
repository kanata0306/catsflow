package com.catsflow.catsflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.catsflow.catsflow.Repository.TransactionRepository;
import com.catsflow.catsflow.Service.TransactionService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository repository;

    @InjectMocks
    private TransactionService service;

    @Test
    void getTransactionsReturnsRepositoryResult() {
        List<Map<String, Object>> expected = List.of(
                Map.of("id", 1L, "amount", 1000),
                Map.of("id", 2L, "amount", -500));
        when(repository.findByUserId(1L)).thenReturn(expected);

        List<Map<String, Object>> actual = service.getTransactions(1L);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void getTransactionsDelegatesWithGivenUserId() {
        Long userId = 42L;
        when(repository.findByUserId(userId)).thenReturn(List.of());

        service.getTransactions(userId);

        verify(repository).findByUserId(userId);
    }

    @Test
    void getTransactionsReturnsEmptyListWhenNoData() {
        when(repository.findByUserId(99L)).thenReturn(List.of());

        List<Map<String, Object>> actual = service.getTransactions(99L);

        assertThat(actual).isEmpty();
    }
}
