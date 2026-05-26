package com.wex.purchase.service;

import com.wex.purchase.dto.ConvertedTransactionResponse;
import com.wex.purchase.dto.CreateTransactionRequest;
import com.wex.purchase.dto.TransactionResponse;
import com.wex.purchase.exception.ExchangeRateUnavailableException;
import com.wex.purchase.exception.TransactionNotFoundException;
import com.wex.purchase.model.PurchaseTransaction;
import com.wex.purchase.repository.PurchaseTransactionRepository;
import com.wex.purchase.service.PurchaseTransactionService.IdempotentResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseTransactionServiceTest {

    @Mock
    private PurchaseTransactionRepository repository;

    @Mock
    private TreasuryExchangeRateService exchangeRateService;

    @InjectMocks
    private PurchaseTransactionService service;

    private PurchaseTransaction savedTransaction;
    private static final LocalDateTime TRANSACTION_DATETIME = LocalDateTime.of(2024, 6, 15, 14, 30, 0);

    @BeforeEach
    void setUp() {
        savedTransaction = new PurchaseTransaction();
        savedTransaction.setId(UUID.randomUUID());
        savedTransaction.setIdempotencyKey("key-001");
        savedTransaction.setDescription("Office supplies");
        savedTransaction.setTransactionDate(TRANSACTION_DATETIME);
        savedTransaction.setPurchaseAmount(new BigDecimal("100.00"));
    }

    // -------------------------------------------------------------------------
    // createTransaction
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createTransaction: stores and returns a new transaction (created=true)")
    void createTransaction_newKey_createsAndReturns() {
        CreateTransactionRequest request = buildRequest("Office supplies", TRANSACTION_DATETIME, 10000L);

        when(repository.findByIdempotencyKey("key-001")).thenReturn(Optional.empty());
        when(repository.save(any(PurchaseTransaction.class))).thenReturn(savedTransaction);

        IdempotentResult<TransactionResponse> result = service.createTransaction("key-001", request);

        assertThat(result.created()).isTrue();
        assertThat(result.value().getDescription()).isEqualTo("Office supplies");
        assertThat(result.value().getPurchaseAmountUsd()).isEqualByComparingTo("100.00");
        assertThat(result.value().getTransactionDate()).isEqualTo(TRANSACTION_DATETIME);
        verify(repository).save(any(PurchaseTransaction.class));
    }

    @Test
    @DisplayName("createTransaction: returns existing transaction for duplicate key (created=false)")
    void createTransaction_duplicateKey_returnsExistingWithoutSaving() {
        CreateTransactionRequest request = buildRequest("Office supplies", TRANSACTION_DATETIME, 10000L);

        when(repository.findByIdempotencyKey("key-001")).thenReturn(Optional.of(savedTransaction));

        IdempotentResult<TransactionResponse> result = service.createTransaction("key-001", request);

        assertThat(result.created()).isFalse();
        assertThat(result.value().getId()).isEqualTo(savedTransaction.getId());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("createTransaction: converts cents to dollars correctly (9999 → 99.99)")
    void createTransaction_convertsCentsToDollars() {
        CreateTransactionRequest request = buildRequest("Test item", LocalDateTime.now(), 9999L);

        PurchaseTransaction stored = new PurchaseTransaction();
        stored.setId(UUID.randomUUID());
        stored.setDescription("Test item");
        stored.setTransactionDate(LocalDateTime.now());
        stored.setPurchaseAmount(new BigDecimal("99.99"));

        when(repository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(repository.save(any())).thenReturn(stored);

        IdempotentResult<TransactionResponse> result = service.createTransaction("key-002", request);

        assertThat(result.value().getPurchaseAmountUsd()).isEqualByComparingTo("99.99");
    }

    @Test
    @DisplayName("createTransaction: 1 cent → $0.01")
    void createTransaction_oneCent_convertsCorrectly() {
        CreateTransactionRequest request = buildRequest("Tiny", LocalDateTime.now(), 1L);

        PurchaseTransaction stored = new PurchaseTransaction();
        stored.setId(UUID.randomUUID());
        stored.setDescription("Tiny");
        stored.setTransactionDate(LocalDateTime.now());
        stored.setPurchaseAmount(new BigDecimal("0.01"));

        when(repository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(repository.save(any())).thenReturn(stored);

        IdempotentResult<TransactionResponse> result = service.createTransaction("key-003", request);

        assertThat(result.value().getPurchaseAmountUsd()).isEqualByComparingTo("0.01");
    }

    // -------------------------------------------------------------------------
    // getTransactionInCurrency
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getTransactionInCurrency: extracts date from datetime for Treasury API lookup")
    void getTransactionInCurrency_extractsDateFromDatetime() {
        UUID id = savedTransaction.getId();
        when(repository.findById(id)).thenReturn(Optional.of(savedTransaction));
        when(exchangeRateService.getExchangeRate(eq("Canada-Dollar"), eq(LocalDate.of(2024, 6, 15))))
                .thenReturn(new BigDecimal("1.3500"));

        ConvertedTransactionResponse response = service.getTransactionInCurrency(id, "Canada-Dollar");

        // Verify date extracted correctly from 2024-06-15T14:30:00
        verify(exchangeRateService).getExchangeRate("Canada-Dollar", LocalDate.of(2024, 6, 15));
        assertThat(response.getTransactionDate()).isEqualTo(TRANSACTION_DATETIME);
    }

    @Test
    @DisplayName("getTransactionInCurrency: converts USD amount using exchange rate")
    void getTransactionInCurrency_validRequest_returnsConvertedAmount() {
        UUID id = savedTransaction.getId();
        when(repository.findById(id)).thenReturn(Optional.of(savedTransaction));
        when(exchangeRateService.getExchangeRate(eq("Canada-Dollar"), any(LocalDate.class)))
                .thenReturn(new BigDecimal("1.3500"));

        ConvertedTransactionResponse response = service.getTransactionInCurrency(id, "Canada-Dollar");

        assertThat(response.getExchangeRate()).isEqualByComparingTo("1.3500");
        assertThat(response.getConvertedAmount()).isEqualByComparingTo("135.00");
        assertThat(response.getTargetCurrency()).isEqualTo("Canada-Dollar");
    }

    @Test
    @DisplayName("getTransactionInCurrency: rounds converted amount to two decimal places")
    void getTransactionInCurrency_roundsConvertedAmount() {
        savedTransaction.setPurchaseAmount(new BigDecimal("10.00"));
        UUID id = savedTransaction.getId();

        when(repository.findById(id)).thenReturn(Optional.of(savedTransaction));
        when(exchangeRateService.getExchangeRate(anyString(), any(LocalDate.class)))
                .thenReturn(new BigDecimal("1.2345"));

        ConvertedTransactionResponse response = service.getTransactionInCurrency(id, "Some-Currency");

        // 10.00 * 1.2345 = 12.345 → rounds to 12.35
        assertThat(response.getConvertedAmount()).isEqualByComparingTo("12.35");
    }

    @Test
    @DisplayName("getTransactionInCurrency: throws TransactionNotFoundException for unknown id")
    void getTransactionInCurrency_unknownId_throwsNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        when(repository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTransactionInCurrency(unknownId, "Canada-Dollar"))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    @DisplayName("getTransactionInCurrency: propagates ExchangeRateUnavailableException")
    void getTransactionInCurrency_noRateAvailable_throwsExchangeRateException() {
        UUID id = savedTransaction.getId();
        when(repository.findById(id)).thenReturn(Optional.of(savedTransaction));
        when(exchangeRateService.getExchangeRate(anyString(), any(LocalDate.class)))
                .thenThrow(new ExchangeRateUnavailableException("No rate available"));

        assertThatThrownBy(() -> service.getTransactionInCurrency(id, "Nonexistent-Currency"))
                .isInstanceOf(ExchangeRateUnavailableException.class);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private CreateTransactionRequest buildRequest(String description, LocalDateTime dateTime, Long amountCents) {
        CreateTransactionRequest req = new CreateTransactionRequest();
        req.setDescription(description);
        req.setTransactionDate(dateTime);
        req.setPurchaseAmountCents(amountCents);
        return req;
    }
}
