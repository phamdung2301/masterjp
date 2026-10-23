package com.masterjp.controller;

import com.masterjp.dto.PaymentDtos;
import com.masterjp.exception.ApiException;
import com.masterjp.exception.GlobalExceptionHandler;
import com.masterjp.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test HTTP cho POST /api/payments/sepay/webhook (Phase D — F-044).
 * Standalone MockMvc + GlobalExceptionHandler — không boot Spring context,
 * không chạm DB thật. Verify tầng controller: header X-Webhook-Secret được
 * truyền xuống service, và 401 có body đúng shape khi secret sai/thiếu.
 * Chay bang: mvn test -Dtest=PaymentControllerTest
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentController — webhook SePay verify X-Webhook-Secret")
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PaymentController(paymentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static final String PAYLOAD = """
            {"transferType":"in","id":"TX-1","transferAmount":49000,"content":"INV-ABC123"}""";

    @Test
    @DisplayName("Secret SAI -> 401, body {code: WEBHOOK_UNAUTHORIZED}")
    void webhook_secretSai_401() throws Exception {
        when(paymentService.handleWebhook(eq("sai-secret"), anyMap()))
                .thenThrow(new ApiException("WEBHOOK_UNAUTHORIZED",
                        "Webhook secret không hợp lệ.", HttpStatus.UNAUTHORIZED));

        mockMvc.perform(post("/api/payments/sepay/webhook")
                        .header("X-Webhook-Secret", "sai-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WEBHOOK_UNAUTHORIZED"));

        verify(paymentService).handleWebhook(eq("sai-secret"), anyMap());
    }

    @Test
    @DisplayName("THIẾU header X-Webhook-Secret -> service nhận null -> 401")
    void webhook_thieuHeader_401_secretNull() throws Exception {
        when(paymentService.handleWebhook(eq(null), anyMap()))
                .thenThrow(new ApiException("WEBHOOK_UNAUTHORIZED",
                        "Webhook secret không hợp lệ.", HttpStatus.UNAUTHORIZED));

        mockMvc.perform(post("/api/payments/sepay/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WEBHOOK_UNAUTHORIZED"));

        // Controller phải truyền null (không tự sinh default) xuống service verify
        ArgumentCaptor<String> secretCaptor = ArgumentCaptor.forClass(String.class);
        verify(paymentService).handleWebhook(secretCaptor.capture(), anyMap());
        assertNull(secretCaptor.getValue());
    }

    @Test
    @DisplayName("Secret ĐÚNG -> service trả matched -> HTTP 200 passthrough")
    void webhook_secretDung_200() throws Exception {
        when(paymentService.handleWebhook(eq("s3cr3t"), any(Map.class)))
                .thenReturn(new PaymentDtos.WebhookMatchedResponse(
                        "matched", "INV-ABC123", "MONTHLY", "2026-05-27T09:26:40+07:00"));

        mockMvc.perform(post("/api/payments/sepay/webhook")
                        .header("X-Webhook-Secret", "s3cr3t")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("matched"))
                .andExpect(jsonPath("$.invoiceCode").value("INV-ABC123"));
    }

    @Test
    @DisplayName("Webhook unmatched từ service -> vẫn 200 (SePay không retry vô hạn)")
    void webhook_unmatched_200() throws Exception {
        when(paymentService.handleWebhook(eq("s3cr3t"), any(Map.class)))
                .thenReturn(new PaymentDtos.WebhookUnmatchedResponse("unmatched", "CODE_NOT_FOUND"));

        mockMvc.perform(post("/api/payments/sepay/webhook")
                        .header("X-Webhook-Secret", "s3cr3t")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("unmatched"))
                .andExpect(jsonPath("$.reason").value("CODE_NOT_FOUND"));
    }
}
