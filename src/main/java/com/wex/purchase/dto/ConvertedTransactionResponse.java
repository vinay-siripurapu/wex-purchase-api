package com.wex.purchase.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class ConvertedTransactionResponse {

    private UUID id;
    private String description;
    private LocalDateTime transactionDate;
    private BigDecimal purchaseAmountUsd;
    private String targetCurrency;
    private BigDecimal exchangeRate;
    private BigDecimal convertedAmount;

    public ConvertedTransactionResponse() {}

    public ConvertedTransactionResponse(UUID id, String description, LocalDateTime transactionDate,
                                        BigDecimal purchaseAmountUsd, String targetCurrency,
                                        BigDecimal exchangeRate, BigDecimal convertedAmount) {
        this.id = id;
        this.description = description;
        this.transactionDate = transactionDate;
        this.purchaseAmountUsd = purchaseAmountUsd;
        this.targetCurrency = targetCurrency;
        this.exchangeRate = exchangeRate;
        this.convertedAmount = convertedAmount;
    }

    // --- Getters & Setters ---

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDateTime transactionDate) { this.transactionDate = transactionDate; }

    public BigDecimal getPurchaseAmountUsd() { return purchaseAmountUsd; }
    public void setPurchaseAmountUsd(BigDecimal purchaseAmountUsd) { this.purchaseAmountUsd = purchaseAmountUsd; }

    public String getTargetCurrency() { return targetCurrency; }
    public void setTargetCurrency(String targetCurrency) { this.targetCurrency = targetCurrency; }

    public BigDecimal getExchangeRate() { return exchangeRate; }
    public void setExchangeRate(BigDecimal exchangeRate) { this.exchangeRate = exchangeRate; }

    public BigDecimal getConvertedAmount() { return convertedAmount; }
    public void setConvertedAmount(BigDecimal convertedAmount) { this.convertedAmount = convertedAmount; }
}
