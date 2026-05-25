package com.wex.purchase.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wex.purchase.dto.ConvertedTransactionResponse;
import com.wex.purchase.dto.CreateTransactionRequest;
import com.wex.purchase.dto.TransactionResponse;
import com.wex.purchase.exception.ExchangeRateUnavailableException;
import com.wex.purchase.exception.TransactionNotFoundException;
import com.wex.purchase.service.PurchaseTransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    // -------------------------------------------------------------------------
    // POST /api/v1/transactions
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/transactions - 201 Created with valid body")
    void createTransaction_validBody_returns201() throws Exception {
        CreateTransactionRequest request = buildCreateRequest("Office supplies", "2024-06-15", "99.99");
        TransactionResponse stubResponse = new TransactionResponse(
                TRANSACTION_ID, "Office supplies", LocalDate.of(2024, 6, 15), new BigDecimal("99.99"));

        when(service.createTransaction(any())).thenReturn(stubResponse);

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(TRANSACTION_ID.toString()))
                .andExpect(jsonPath("$.description").value("Office supplies"))
                .andExpect(jsonPath("$.purchaseAmountUsd").value(99.99));
    }

    @Test
    @DisplayName("POST /api/v1/transactions - 400 when description is blank")
    void createTransaction_blankDescription_returns400() throws Exception {
        CreateTransactionRequest request = buildCreateRequest("", "2024-06-15", "99.99");

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/v1/transactions - 400 when description exceeds 50 chars")
    void createTransaction_descriptionTooLong_returns400() throws Exception {
        CreateTransactionRequest request = buildCreateRequest(
                "A".repeat(51), "2024-06-15", "99.99");

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/transactions - 400 when purchase amount is negative")
    void createTransaction_negativeAmount_returns400() throws Exception {
        CreateTransactionRequest request = buildCreateRequest("Refund", "2024-06-15", "-10.00");

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/transactions - 400 when purchase amount is zero")
    void createTransaction_zeroAmount_returns400() throws Exception {
        CreateTransactionRequest request = buildCreateRequest("Zero", "2024-06-15", "0.00");

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/transactions - 400 when transaction date is missing")
    void createTransaction_missingDate_returns400() throws Exception {
        String body = """
                {
                  "description": "Test",
                  "purchaseAmount": 10.00
                }
                """;

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/transactions/{id}?currency=
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/transactions/{id} - 200 OK with converted amount")
    void getTransaction_validRequest_returns200WithConversion() throws Exception {
        ConvertedTransactionResponse stubResponse = new ConvertedTransactionResponse(
                TRANSACTION_ID,
                "Office supplies",
                LocalDate.of(2024, 6, 15),
                new BigDecimal("99.99"),
                "Canada-Dollar",
                new BigDecimal("1.35"),
                new BigDecimal("134.99")
        );

        when(service.getTransactionInCurrency(eq(TRANSACTION_ID), eq("Canada-Dollar")))
                .thenReturn(stubResponse);

        mockMvc.perform(get("/api/v1/transactions/{id}", TRANSACTION_ID)
                        .param("currency", "Canada-Dollar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TRANSACTION_ID.toString()))
                .andExpect(jsonPath("$.targetCurrency").value("Canada-Dollar"))
                .andExpect(jsonPath("$.exchangeRate").value(1.35))
                .andExpect(jsonPath("$.convertedAmount").value(134.99));
    }

    @Test
    @DisplayName("GET /api/v1/transactions/{id} - 404 when transaction does not exist")
    void getTransaction_unknownId_returns404() throws Exception {
        when(service.getTransactionInCurrency(any(), any()))
                .thenThrow(new TransactionNotFoundException("Not found"));

        mockMvc.perform(get("/api/v1/transactions/{id}", TRANSACTION_ID)
                        .param("currency", "Canada-Dollar"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Not found"));
    }

    @Test
    @DisplayName("GET /api/v1/transactions/{id} - 422 when no exchange rate available")
    void getTransaction_noExchangeRate_returns422() throws Exception {
        when(service.getTransactionInCurrency(any(), any()))
                .thenThrow(new ExchangeRateUnavailableException("No rate available within 6 months"));

        mockMvc.perform(get("/api/v1/transactions/{id}", TRANSACTION_ID)
                        .param("currency", "Imaginary-Currency"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").exists());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private CreateTransactionRequest buildCreateRequest(String description, String date, String amount) {
        CreateTransactionRequest req = new CreateTransactionRequest();
        req.setDescription(description);
        if (date != null) {
            req.setTransactionDate(LocalDate.parse(date));
        }
        req.setPurchaseAmount(new BigDecimal(amount));
        return req;
    }
}
