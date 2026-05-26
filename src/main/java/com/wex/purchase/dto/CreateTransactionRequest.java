package com.wex.purchase.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

public class CreateTransactionRequest {

    @NotBlank(message = "Description must not be blank")
    @Size(max = 50, message = "Description must not exceed 50 characters")
    private String description;

    @NotNull(message = "Transaction date and time is required")
    private LocalDateTime transactionDate;

    /**
     * Purchase amount in cents (last 2 digits are cents).
     * e.g. 9999 = $99.99, 100 = $1.00, 0 = $0.00
     */
    @NotNull(message = "Purchase amount in cents is required")
    @Min(value = 0, message = "Purchase amount in cents must not be negative")
    private Long purchaseAmountCents;

    // --- Getters & Setters ---

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDateTime transactionDate) { this.transactionDate = transactionDate; }

    public Long getPurchaseAmountCents() { return purchaseAmountCents; }
    public void setPurchaseAmountCents(Long purchaseAmountCents) { this.purchaseAmountCents = purchaseAmountCents; }
}
