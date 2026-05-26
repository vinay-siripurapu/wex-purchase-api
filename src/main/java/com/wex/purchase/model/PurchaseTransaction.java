package com.wex.purchase.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "purchase_transactions",
       uniqueConstraints = @UniqueConstraint(name = "uc_idempotency_key", columnNames = "idempotency_key"))
public class PurchaseTransaction {

    /**
     * UUID stored as CHAR(36) — compatible with Aurora MySQL.
     * Generated in Java (not DB) for portability and test predictability.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "description", nullable = false, length = 50)
    private String description;

    /**
     * Full timestamp of the purchase (date + time).
     * Stored as DATETIME in Aurora MySQL — no timezone info, UTC expected from caller.
     */
    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    /**
     * Stored in US dollars with exactly 2 decimal places.
     * Input is accepted in USD and stored with exactly 2 decimal places.
     */
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

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDateTime transactionDate) { this.transactionDate = transactionDate; }

    public BigDecimal getPurchaseAmount() { return purchaseAmount; }
    public void setPurchaseAmount(BigDecimal purchaseAmount) { this.purchaseAmount = purchaseAmount; }
}
