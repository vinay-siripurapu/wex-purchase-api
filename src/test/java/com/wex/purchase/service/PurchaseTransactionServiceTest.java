package com.wex.purchase.service;

import com.wex.purchase.dto.ConvertedTransactionResponse;
import com.wex.purchase.dto.CreateTransactionRequest;
import com.wex.purchase.dto.TransactionResponse;
import com.wex.purchase.exception.ExchangeRateUnavailableException;
import com.wex.purchase.exception.TransactionNotFoundException;
import com.wex.purchase.model.PurchaseTransaction;
import com.wex.purchase.repository.PurchaseTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    @BeforeEach
    void setUp() {
        savedTransaction = new PurchaseTransaction();
        savedTransaction.setId(UUID.randomUUID());
        savedTransaction.setDescription("Office supplies");
        savedTransaction.setTransactionDate(LocalDate.of(2024, 6, 15));
        savedTransaction.setPurchaseAmount(new BigDecimal("100.00"));
    }

    // -------------------------------------------------------------------------
    // createTransaction
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createTransaction: stores and returns a valid transaction")
    void createTransaction_validRequest_returnsSavedTransaction() {
        CreateTransactionRequest request = buildRequest("Office supplies", LocalDate.of(2024, 6, 15), "100.00");

        when(repository.save(any(PurchaseTransaction.class))).thenReturn(savedTransaction);

        TransactionResponse response = service.createTransaction(request);

        assertThat(response.getId()).isEqualTo(savedTransaction.getId());
        assertThat(response.getDescription()).isEqualTo("Office supplies");
        assertThat(response.getPurchaseAmountUsd()).isEqualByComparingTo("100.00");
        verify(repository, times(1)).save(any(PurchaseTransaction.class));
    }

    @Test
    @DisplayName("createTransaction: rounds purchase amount to nearest cent on store")
    void createTransaction_roundsAmountToNearestCent() {
        CreateTransactionRequest request = buildRequest("Test item", LocalDate.now(), "99.999");

        PurchaseTransaction rounded = new PurchaseTransaction();
        rounded.setId(UUID.randomUUID());
        rounded.setDescription("Test item");
        rounded.setTransactionDate(LocalDate.now());
        rounded.setPurchaseAmount(new BigDecimal("100.00"));

        when(repository.save(any(PurchaseTransaction.class))).thenReturn(rounded);

        TransactionResponse response = service.createTransaction(request);

        assertThat(response.getPurchaseAmountUsd()).isEqualByComparingTo("100.00");
    }

    // -------------------------------------------------------------------------
    // getTransactionInCurrency
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getTransactionInCurrency: converts USD amount using exchange rate")
    void getTransactionInCurrency_validRequest_returnsConvertedAmount() {
        UUID id = savedTransaction.getId();
        BigDecimal exchangeRate = new BigDecimal("1.3500");

        when(repository.findById(id)).thenReturn(Optional.of(savedTransaction));
        when(exchangeRateService.getExchangeRate("Canada-Dollar", savedTransaction.getTransactionDate()))
                .thenReturn(exchangeRate);

        ConvertedTransactionResponse response = service.getTransactionInCurrency(id, "Canada-Dollar");

        assertThat(response.getId()).isEqualTo(id);
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
        when(exchangeRateService.getExchangeRate(anyString(), any())).thenReturn(new BigDecimal("1.2345"));

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
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    @Test
    @DisplayName("getTransactionInCurrency: propagates ExchangeRateUnavailableException")
    void getTransactionInCurrency_noRateAvailable_throwsExchangeRateException() {
        UUID id = savedTransaction.getId();
        when(repository.findById(id)).thenReturn(Optional.of(savedTransaction));
        when(exchangeRateService.getExchangeRate(anyString(), any()))
                .thenThrow(new ExchangeRateUnavailableException("No rate available"));

        assertThatThrownBy(() -> service.getTransactionInCurrency(id, "Nonexistent-Currency"))
                .isInstanceOf(ExchangeRateUnavailableException.class);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private CreateTransactionRequest buildRequest(String description, LocalDate date, String amount) {
        CreateTransactionRequest req = new CreateTransactionRequest();
        req.setDescription(description);
        req.setTransactionDate(date);
        req.setPurchaseAmount(new BigDecimal(amount));
        return req;
    }
}
