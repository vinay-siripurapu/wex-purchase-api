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
     * Stores a purchase transaction idempotently.
     *
     * If a transaction with the given idempotencyKey already exists, the original
     * stored response is returned without creating a duplicate. Otherwise a new
     * transaction is persisted and returned.
     *
     * @param idempotencyKey client-supplied unique key for this request
     * @param request        transaction details
     * @return the stored (or previously stored) transaction and whether it was newly created
     */
    @Transactional
    public IdempotentResult<TransactionResponse> createTransaction(String idempotencyKey,
                                                                    CreateTransactionRequest request) {
        return repository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> IdempotentResult.existing(toResponse(existing)))
                .orElseGet(() -> {
                    PurchaseTransaction transaction = new PurchaseTransaction();
                    transaction.setIdempotencyKey(idempotencyKey);
                    transaction.setDescription(request.getDescription());
                    transaction.setTransactionDate(request.getTransactionDate());
                    // Convert cents to dollars: 9999 → 99.99
                    BigDecimal amountInDollars = BigDecimal.valueOf(request.getPurchaseAmountCents())
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    transaction.setPurchaseAmount(amountInDollars);

                    PurchaseTransaction saved = repository.save(transaction);
                    return IdempotentResult.created(toResponse(saved));
                });
    }

    /**
     * Retrieves a transaction by ID and returns it converted to the specified currency.
     * The exchange rate lookup uses the date portion of the transaction datetime,
     * as the Treasury API operates at day granularity.
     *
     * @param id                  transaction UUID
     * @param countryCurrencyDesc target currency label, e.g. "Canada-Dollar"
     */
    @Transactional(readOnly = true)
    public ConvertedTransactionResponse getTransactionInCurrency(UUID id, String countryCurrencyDesc) {
        PurchaseTransaction transaction = repository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(
                        "Purchase transaction not found for id: " + id));

        // Treasury API works at date granularity — extract date from the datetime
        BigDecimal exchangeRate = exchangeRateService.getExchangeRate(
                countryCurrencyDesc, transaction.getTransactionDate().toLocalDate());

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

    /**
     * Wraps a service result to indicate whether it was newly created or already existed.
     */
    public record IdempotentResult<T>(T value, boolean created) {
        public static <T> IdempotentResult<T> created(T value) { return new IdempotentResult<>(value, true); }
        public static <T> IdempotentResult<T> existing(T value) { return new IdempotentResult<>(value, false); }
    }
}
