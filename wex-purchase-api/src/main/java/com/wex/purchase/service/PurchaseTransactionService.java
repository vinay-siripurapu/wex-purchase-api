package com.wex.purchase.service;

import com.wex.purchase.dto.ConvertedTransactionResponse;
import com.wex.purchase.dto.CreateTransactionRequest;
import com.wex.purchase.dto.TransactionResponse;
import com.wex.purchase.exception.TransactionNotFoundException;
import com.wex.purchase.model.PurchaseTransaction;
import com.wex.purchase.repository.PurchaseTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
public class PurchaseTransactionService {

    private final PurchaseTransactionRepository repository;
    private final TreasuryExchangeRateService exchangeRateService;

    public PurchaseTransactionService(PurchaseTransactionRepository repository,
                                      TreasuryExchangeRateService exchangeRateService) {
        this.repository = repository;
        this.exchangeRateService = exchangeRateService;
    }

    /**
     * Validates and persists a purchase transaction.
     * The purchase amount is stored rounded to the nearest cent.
     */
    @Transactional
    public TransactionResponse createTransaction(CreateTransactionRequest request) {
        PurchaseTransaction transaction = new PurchaseTransaction();
        transaction.setDescription(request.getDescription());
        transaction.setTransactionDate(request.getTransactionDate());
        // Enforce rounding to nearest cent on store
        transaction.setPurchaseAmount(
                request.getPurchaseAmount().setScale(2, RoundingMode.HALF_UP));

        PurchaseTransaction saved = repository.save(transaction);
        return toResponse(saved);
    }

    /**
     * Retrieves a transaction by ID and returns it converted to the specified currency.
     *
     * @param id                  transaction UUID
     * @param countryCurrencyDesc target currency label, e.g. "Canada-Dollar"
     */
    @Transactional(readOnly = true)
    public ConvertedTransactionResponse getTransactionInCurrency(UUID id, String countryCurrencyDesc) {
        PurchaseTransaction transaction = repository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(
                        "Purchase transaction not found for id: " + id));

        BigDecimal exchangeRate = exchangeRateService.getExchangeRate(
                countryCurrencyDesc, transaction.getTransactionDate());

        BigDecimal convertedAmount = transaction.getPurchaseAmount()
                .multiply(exchangeRate)
                .setScale(2, RoundingMode.HALF_UP);

        return new ConvertedTransactionResponse(
                transaction.getId(),
                transaction.getDescription(),
                transaction.getTransactionDate(),
                transaction.getPurchaseAmount(),
                countryCurrencyDesc,
                exchangeRate,
                convertedAmount
        );
    }

    private TransactionResponse toResponse(PurchaseTransaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getDescription(),
                transaction.getTransactionDate(),
                transaction.getPurchaseAmount()
        );
    }
}
