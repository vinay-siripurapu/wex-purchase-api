package com.wex.purchase.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Maps a single record from the Treasury Reporting Rates of Exchange API.
 * Example API endpoint:
 * https://api.fiscaldata.treasury.gov/services/api/v1/accounting/od/rates_of_exchange
 */
public class TreasuryExchangeRateRecord {

    @JsonProperty("country_currency_desc")
    private String countryCurrencyDesc;

    @JsonProperty("exchange_rate")
    private BigDecimal exchangeRate;

    @JsonProperty("record_date")
    private LocalDate recordDate;

    // --- Getters & Setters ---

    public String getCountryCurrencyDesc() { return countryCurrencyDesc; }
    public void setCountryCurrencyDesc(String countryCurrencyDesc) { this.countryCurrencyDesc = countryCurrencyDesc; }

    public BigDecimal getExchangeRate() { return exchangeRate; }
    public void setExchangeRate(BigDecimal exchangeRate) { this.exchangeRate = exchangeRate; }

    public LocalDate getRecordDate() { return recordDate; }
    public void setRecordDate(LocalDate recordDate) { this.recordDate = recordDate; }
}
