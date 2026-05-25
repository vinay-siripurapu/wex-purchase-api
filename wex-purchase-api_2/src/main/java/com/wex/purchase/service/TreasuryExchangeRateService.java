package com.wex.purchase.service;

import com.wex.purchase.dto.TreasuryApiResponse;
import com.wex.purchase.dto.TreasuryExchangeRateRecord;
import com.wex.purchase.exception.ExchangeRateUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Fetches exchange rates from the US Treasury Reporting Rates of Exchange API.
 *
 * Strategy:
 *  - Query the API for records matching the target currency within the 6-month window
 *    [purchaseDate - 6 months, purchaseDate], ordered descending by record_date.
 *  - Take the most recent rate (≤ purchaseDate).
 *  - If none found, throw ExchangeRateUnavailableException per requirements.
 */
@Service
public class TreasuryExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(TreasuryExchangeRateService.class);

    private final RestTemplate restTemplate;

    @Value("${treasury.api.base-url:https://api.fiscaldata.treasury.gov/services/api/v1/accounting/od/rates_of_exchange}")
    private String treasuryBaseUrl;

    public TreasuryExchangeRateService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Returns the most recent exchange rate for the given currency that is
     * on or before the purchase date and within the last 6 months.
     *
     * @param countryCurrencyDesc  e.g. "Canada-Dollar", "Euro Zone-Euro"
     * @param purchaseDate         the transaction date
     * @return exchange rate as BigDecimal
     * @throws ExchangeRateUnavailableException if no valid rate exists
     */
    public BigDecimal getExchangeRate(String countryCurrencyDesc, LocalDate purchaseDate) {
        LocalDate sixMonthsBeforePurchase = purchaseDate.minusMonths(6);

        String url = UriComponentsBuilder.fromHttpUrl(treasuryBaseUrl)
                .queryParam("fields", "country_currency_desc,exchange_rate,record_date")
                .queryParam("filter",
                        "country_currency_desc:eq:" + countryCurrencyDesc
                        + ",record_date:lte:" + purchaseDate
                        + ",record_date:gte:" + sixMonthsBeforePurchase)
                .queryParam("sort", "-record_date")
                .queryParam("page[size]", "1")
                .build(false)
                .toUriString();

        log.info("Fetching exchange rate from Treasury API: {}", url);

        try {
            TreasuryApiResponse response = restTemplate.getForObject(url, TreasuryApiResponse.class);

            if (response == null || response.getData() == null || response.getData().isEmpty()) {
                throw new ExchangeRateUnavailableException(
                        "Purchase cannot be converted to the target currency '%s': no exchange rate available within 6 months of the purchase date."
                                .formatted(countryCurrencyDesc));
            }

            // API is sorted desc by record_date; take the first (most recent ≤ purchaseDate)
            TreasuryExchangeRateRecord record = response.getData().get(0);
            log.info("Using exchange rate {} (record date: {}) for currency: {}",
                    record.getExchangeRate(), record.getRecordDate(), countryCurrencyDesc);

            return record.getExchangeRate();

        } catch (RestClientException ex) {
            log.error("Failed to fetch exchange rates from Treasury API", ex);
            throw new ExchangeRateUnavailableException(
                    "Unable to retrieve exchange rate due to an external service error. Please try again later.");
        }
    }
}
