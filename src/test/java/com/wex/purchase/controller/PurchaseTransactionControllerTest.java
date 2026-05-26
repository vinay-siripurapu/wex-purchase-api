package com.wex.purchase.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wex.purchase.dto.ConvertedTransactionResponse;
import com.wex.purchase.dto.CreateTransactionRequest;
import com.wex.purchase.dto.TransactionResponse;
import com.wex.purchase.exception.ExchangeRateUnavailableException;
import com.wex.purchase.exception.TransactionNotFoundException;
import com.wex.purchase.service.PurchaseTransactionService;
import com.wex.purchase.service.PurchaseTransactionService.IdempotentResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PurchaseTransactionController.class)
class PurchaseTransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PurchaseTransactionService service;

    private static final UUID TRANSACTION_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String IDEMPOTENCY_KEY = "test-key-001";
    private static final LocalDateTime TRANSACTION_DATETIME = LocalDateTime.of(2024, 6, 15, 14, 30, 0);

    // -------------------------------------------------------------------------
    // POST /api/v1/transactions
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST - 201 Created when new transaction stored")
    void createTransaction_newKey_returns201() throws Exception {
        TransactionResponse stub = new TransactionResponse(
                TRANSACTION_ID, "Office supplies", TRANSACTION_DATETIME, new BigDecimal("99.99"));

        when(service.createTransaction(eq(IDEMPOTENCY_KEY), any()))
                .thenReturn(IdempotentResult.created(stub));

        mockMvc.perform(post("/api/v1/transactions")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildRequest("Office supplies", TRANSACTION_DATETIME, "99.99"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(TRANSACTION_ID.toString()))
                .andExpect(jsonPath("$.transactionDate").value("2024-06-15T14:30:00"))
                .andExpect(jsonPath("$.purchaseAmountUsd").value(99.99));
    }

    @Test
    @DisplayName("POST - 200 OK when duplicate key replays original response")
    void createTransaction_duplicateKey_returns200() throws Exception {
        TransactionResponse stub = new TransactionResponse(
                TRANSACTION_ID, "Office supplies", TRANSACTION_DATETIME, new BigDecimal("99.99"));

        when(service.createTransaction(eq(IDEMPOTENCY_KEY), any()))
                .thenReturn(IdempotentResult.existing(stub));

        mockMvc.perform(post("/api/v1/transactions")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildRequest("Office supplies", TRANSACTION_DATETIME, "99.99"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TRANSACTION_ID.toString()));
    }

    @Test
    @DisplayName("POST - 400 when Idempotency-Key header is missing")
    void createTransaction_missingIdempotencyKey_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildRequest("Office supplies", TRANSACTION_DATETIME, "99.99"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST - 400 when Idempotency-Key exceeds 64 characters")
    void createTransaction_tooLongIdempotencyKey_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .header("Idempotency-Key", "A".repeat(65))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildRequest("Office supplies", TRANSACTION_DATETIME, "99.99"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST - 400 when description exceeds 50 chars")
    void createTransaction_descriptionTooLong_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildRequest("A".repeat(51), TRANSACTION_DATETIME, "99.99"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST - 400 when purchaseAmountUsd is negative")
    void createTransaction_negativeAmount_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildRequest("Item", TRANSACTION_DATETIME, "-0.01"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST - 400 when purchaseAmountUsd has more than 2 decimal places")
    void createTransaction_tooManyDecimalPlaces_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildRequest("Item", TRANSACTION_DATETIME, "9.999"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST - 201 when purchaseAmountUsd is zero (free transaction)")
    void createTransaction_zeroAmount_returns201() throws Exception {
        TransactionResponse stub = new TransactionResponse(
                TRANSACTION_ID, "Free item", TRANSACTION_DATETIME, BigDecimal.ZERO);

        when(service.createTransaction(any(), any())).thenReturn(IdempotentResult.created(stub));

        mockMvc.perform(post("/api/v1/transactions")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildRequest("Free item", TRANSACTION_DATETIME, "0.00"))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST - 400 when transactionDate is missing")
    void createTransaction_missingDate_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Test",
                                  "purchaseAmountUsd": 9.99
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST - 400 when transactionDate is date-only (missing time component)")
    void createTransaction_dateOnlyFormat_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Test",
                                  "transactionDate": "2024-06-15",
                                  "purchaseAmountUsd": 9.99
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/transactions/{id}?currency=
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET - 200 OK with converted amount and datetime in response")
    void getTransaction_validRequest_returns200() throws Exception {
        ConvertedTransactionResponse stub = new ConvertedTransactionResponse(
                TRANSACTION_ID, "Office supplies", TRANSACTION_DATETIME,
                new BigDecimal("99.99"), "Canada-Dollar",
                new BigDecimal("1.35"), new BigDecimal("134.99"));

        when(service.getTransactionInCurrency(eq(TRANSACTION_ID), eq("Canada-Dollar")))
                .thenReturn(stub);

        mockMvc.perform(get("/api/v1/transactions/{id}", TRANSACTION_ID)
                        .param("currency", "Canada-Dollar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionDate").value("2024-06-15T14:30:00"))
                .andExpect(jsonPath("$.purchaseAmountUsd").value(99.99))
                .andExpect(jsonPath("$.convertedAmount").value(134.99));
    }

    @Test
    @DisplayName("GET - 404 when transaction does not exist")
    void getTransaction_unknownId_returns404() throws Exception {
        when(service.getTransactionInCurrency(any(), any()))
                .thenThrow(new TransactionNotFoundException("Not found"));

        mockMvc.perform(get("/api/v1/transactions/{id}", TRANSACTION_ID)
                        .param("currency", "Canada-Dollar"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET - 422 when no exchange rate available")
    void getTransaction_noExchangeRate_returns422() throws Exception {
        when(service.getTransactionInCurrency(any(), any()))
                .thenThrow(new ExchangeRateUnavailableException("No rate available"));

        mockMvc.perform(get("/api/v1/transactions/{id}", TRANSACTION_ID)
                        .param("currency", "Imaginary-Currency"))
                .andExpect(status().isUnprocessableEntity());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private CreateTransactionRequest buildRequest(String description, LocalDateTime dateTime, String amountUsd) {
        CreateTransactionRequest req = new CreateTransactionRequest();
        req.setDescription(description);
        req.setTransactionDate(dateTime);
        req.setPurchaseAmountUsd(new BigDecimal(amountUsd));
        return req;
    }
}
