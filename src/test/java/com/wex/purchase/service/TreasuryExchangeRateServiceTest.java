package com.wex.purchase.service;

import com.wex.purchase.dto.TreasuryApiResponse;
import com.wex.purchase.dto.TreasuryExchangeRateRecord;
import com.wex.purchase.exception.ExchangeRateUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TreasuryExchangeRateServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private TreasuryExchangeRateService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "treasuryBaseUrl",
                "https://api.fiscaldata.treasury.gov/services/api/v1/accounting/od/rates_of_exchange");
    }

    @Test
    @DisplayName("getExchangeRate: returns rate when API responds with a valid record")
    void getExchangeRate_validResponse_returnsRate() {
        TreasuryExchangeRateRecord record = buildRecord("Canada-Dollar", new BigDecimal("1.35"), LocalDate.of(2024, 6, 1));
        TreasuryApiResponse apiResponse = new TreasuryApiResponse();
        apiResponse.setData(List.of(record));

        when(restTemplate.getForObject(anyString(), eq(TreasuryApiResponse.class))).thenReturn(apiResponse);

        BigDecimal rate = service.getExchangeRate("Canada-Dollar", LocalDate.of(2024, 6, 15));

        assertThat(rate).isEqualByComparingTo("1.35");
    }

    @Test
    @DisplayName("getExchangeRate: throws exception when API returns empty data list")
    void getExchangeRate_emptyData_throwsExchangeRateUnavailable() {
        TreasuryApiResponse apiResponse = new TreasuryApiResponse();
        apiResponse.setData(List.of());

        when(restTemplate.getForObject(anyString(), eq(TreasuryApiResponse.class))).thenReturn(apiResponse);

        assertThatThrownBy(() -> service.getExchangeRate("Nonexistent-Currency", LocalDate.now()))
                .isInstanceOf(ExchangeRateUnavailableException.class)
                .hasMessageContaining("no exchange rate available");
    }

    @Test
    @DisplayName("getExchangeRate: throws exception when API returns null response")
    void getExchangeRate_nullResponse_throwsExchangeRateUnavailable() {
        when(restTemplate.getForObject(anyString(), eq(TreasuryApiResponse.class))).thenReturn(null);

        assertThatThrownBy(() -> service.getExchangeRate("Canada-Dollar", LocalDate.now()))
                .isInstanceOf(ExchangeRateUnavailableException.class);
    }

    @Test
    @DisplayName("getExchangeRate: throws exception when RestTemplate throws RestClientException")
    void getExchangeRate_restClientException_throwsExchangeRateUnavailable() {
        when(restTemplate.getForObject(anyString(), eq(TreasuryApiResponse.class)))
                .thenThrow(new RestClientException("Connection refused"));

        assertThatThrownBy(() -> service.getExchangeRate("Canada-Dollar", LocalDate.now()))
                .isInstanceOf(ExchangeRateUnavailableException.class)
                .hasMessageContaining("external service error");
    }

    private TreasuryExchangeRateRecord buildRecord(String currency, BigDecimal rate, LocalDate date) {
        TreasuryExchangeRateRecord record = new TreasuryExchangeRateRecord();
        record.setCountryCurrencyDesc(currency);
        record.setExchangeRate(rate);
        record.setRecordDate(date);
        return record;
    }
}
