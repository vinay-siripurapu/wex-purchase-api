package com.wex.purchase.controller;

import com.wex.purchase.dto.ConvertedTransactionResponse;
import com.wex.purchase.dto.CreateTransactionRequest;
import com.wex.purchase.dto.TransactionResponse;
import com.wex.purchase.exception.MissingIdempotencyKeyException;
import com.wex.purchase.service.PurchaseTransactionService;
import com.wex.purchase.service.PurchaseTransactionService.IdempotentResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for Purchase Transaction endpoints.
 *
 * POST /api/v1/transactions
 *   - Store a new purchase transaction (idempotent via Idempotency-Key header)
 *
 * GET  /api/v1/transactions/{id}?currency={countryCurrencyDesc}
 *   - Retrieve a transaction converted to the specified currency
 */
@RestController
@RequestMapping("/api/v1/transactions")
public class PurchaseTransactionController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final PurchaseTransactionService service;

    public PurchaseTransactionController(PurchaseTransactionService service) {
        this.service = service;
    }

    /**
     * Store a purchase transaction idempotently.
     *
     * Requires an {@code Idempotency-Key} header (max 64 chars, e.g. a UUID).
     * If a transaction with that key was already stored, the original response is
     * returned with HTTP 200 instead of 201, and no duplicate is created.
     *
     * @param idempotencyKey client-generated unique key for this request
     * @param request        JSON body with description, transactionDate, purchaseAmountUsd
     * @return 201 Created (new) or 200 OK (duplicate — original returned)
     */
    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody CreateTransactionRequest request) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new MissingIdempotencyKeyException(
                    "Missing required header: Idempotency-Key. " +
                    "Supply a unique value (e.g. a UUID) per intended transaction.");
        }

        if (idempotencyKey.length() > 64) {
            throw new MissingIdempotencyKeyException(
                    "Idempotency-Key must not exceed 64 characters.");
        }

        IdempotentResult<TransactionResponse> result = service.createTransaction(idempotencyKey, request);

        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.value());
    }

    /**
     * Retrieve a purchase transaction converted to a target currency.
     *
     * @param id       UUID of the stored transaction
     * @param currency Country-currency label as used in the Treasury API,
     *                 e.g. "Canada-Dollar", "Euro Zone-Euro", "Japan-Yen"
     * @return 200 OK with the transaction details and converted amount
     */
    @GetMapping("/{id}")
    public ResponseEntity<ConvertedTransactionResponse> getTransaction(
            @PathVariable UUID id,
            @RequestParam String currency) {

        ConvertedTransactionResponse response = service.getTransactionInCurrency(id, currency);
        return ResponseEntity.ok(response);
    }
}
