package com.catsflow.catsflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.catsflow.catsflow.Controller.TransactionController;
import com.catsflow.catsflow.Service.TransactionService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

    @Mock
    private TransactionService transactionService;

    @InjectMocks
    private TransactionController controller;

    @Test
    void getHelloReturnsHelloView() {
        assertThat(controller.getHello()).isEqualTo("hello");
    }

    @Test
    void postRequestAddsInputToModelAndReturnsResponseView() {
        Model model = new ConcurrentModel();

        String view = controller.postRequest("入力テキスト", model);

        assertThat(view).isEqualTo("hello/response");
        assertThat(model.getAttribute("sample")).isEqualTo("入力テキスト");
    }

    @Test
    void postDbRequestQueriesServiceAndAddsTransactionsToModel() {
        List<Map<String, Object>> transactions = List.of(Map.of("id", 1L, "amount", 3000));
        when(transactionService.getTransactions(5L)).thenReturn(transactions);
        Model model = new ConcurrentModel();

        String view = controller.postDbRequest("5", model);

        assertThat(view).isEqualTo("hello/db");
        assertThat(model.getAttribute("transactions")).isEqualTo(transactions);
        verify(transactionService).getTransactions(5L);
    }
}
