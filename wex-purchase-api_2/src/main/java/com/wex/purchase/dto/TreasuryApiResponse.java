package com.wex.purchase.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class TreasuryApiResponse {

    @JsonProperty("data")
    private List<TreasuryExchangeRateRecord> data;

    public List<TreasuryExchangeRateRecord> getData() { return data; }
    public void setData(List<TreasuryExchangeRateRecord> data) { this.data = data; }
}
