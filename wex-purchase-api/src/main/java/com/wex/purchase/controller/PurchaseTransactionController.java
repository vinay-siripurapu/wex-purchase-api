package com.wex.purchase.controller;

import com.wex.purchase.dto.ConvertedTransactionResponse;
import com.wex.purchase.dto.CreateTransactionRequest;
import com.wex.purchase.dto.TransactionResponse;
import com.wex.purchase.service.PurchaseTransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for Purchase Transaction endpoints.
 *
 * POST /api/v1/transactions
 *   - Store a new purchase transaction
 *
 * GET  /api/v1/transactions/{id}?currency={countryCurrencyDesc}
 *   - Retrieve a transaction converted to the specified currency
 */
@RestController
@RequestMapping("/api/v1/transactions")
public class PurchaseTransactionController {

    private final PurchaseTransactionService service;

    public PurchaseTransactionController(PurchaseTransactionService service) {
        this.service = service;
    }

    /**
     * Store a purchase transaction.
     *
     * @param request JSON body with description, transactionDate, purchaseAmount
     * @return 201 Created with the stored transaction (including its generated UUID)
     */
    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(
            @Valid @RequestBody CreateTransactionRequest request) {

        TransactionResponse response = service.createTransaction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
