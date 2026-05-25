package com.wex.purchase.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class TransactionResponse {

    private UUID id;
    private String description;
    private LocalDate transactionDate;
    private BigDecimal purchaseAmountUsd;

    public TransactionResponse() {}

    public TransactionResponse(UUID id, String description, LocalDate transactionDate, BigDecimal purchaseAmountUsd) {
        this.id = id;
        this.description = description;
        this.transactionDate = transactionDate;
        this.purchaseAmountUsd = purchaseAmountUsd;
    }

    // --- Getters & Setters ---

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }

    public BigDecimal getPurchaseAmountUsd() { return purchaseAmountUsd; }
    public void setPurchaseAmountUsd(BigDecimal purchaseAmountUsd) { this.purchaseAmountUsd = purchaseAmountUsd; }
}
