package com.wex.purchase.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "purchase_transactions")
public class PurchaseTransaction {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @NotBlank(message = "Description must not be blank")
    @Size(max = 50, message = "Description must not exceed 50 characters")
    @Column(nullable = false, length = 50)
    private String description;

    @NotNull(message = "Transaction date is required")
    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @NotNull(message = "Purchase amount is required")
    @DecimalMin(value = "0.01", message = "Purchase amount must be a positive value")
    @Digits(integer = 15, fraction = 2, message = "Purchase amount must be a valid monetary value")
    @Column(name = "purchase_amount", nullable = false, precision = 17, scale = 2)
    private BigDecimal purchaseAmount;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
    }

    // --- Getters & Setters ---

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }

    public BigDecimal getPurchaseAmount() { return purchaseAmount; }
    public void setPurchaseAmount(BigDecimal purchaseAmount) { this.purchaseAmount = purchaseAmount; }
}
