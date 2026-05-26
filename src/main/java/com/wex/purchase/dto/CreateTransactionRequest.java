package com.wex.purchase.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CreateTransactionRequest {

    @NotBlank(message = "Description must not be blank")
    @Size(max = 50, message = "Description must not exceed 50 characters")
    private String description;

    @NotNull(message = "Transaction date and time is required")
    private LocalDateTime transactionDate;

    /**
     * Purchase amount in US dollars, rounded to the nearest cent.
     * e.g. 99.99 = $99.99, 1.00 = $1.00, 0.00 = $0.00
     * Must be a non-negative value with at most 2 decimal places.
     */
    @NotNull(message = "Purchase amount is required")
    @DecimalMin(value = "0.00", message = "Purchase amount must not be negative")
    @Digits(integer = 15, fraction = 2,
            message = "Purchase amount must be a valid dollar amount with at most 2 decimal places")
    private BigDecimal purchaseAmountUsd;

    // --- Getters & Setters ---

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDateTime transactionDate) { this.transactionDate = transactionDate; }

    public BigDecimal getPurchaseAmountUsd() { return purchaseAmountUsd; }
    public void setPurchaseAmountUsd(BigDecimal purchaseAmountUsd) { this.purchaseAmountUsd = purchaseAmountUsd; }
}
