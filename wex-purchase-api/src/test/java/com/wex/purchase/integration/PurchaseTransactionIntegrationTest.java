package com.wex.purchase.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.wex.purchase.dto.CreateTransactionRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PurchaseTransactionIntegrationTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @AfterEach
    void resetWireMock() {
        wireMockServer.resetAll();
    }

    @DynamicPropertySource
    static void overrideTreasuryUrl(DynamicPropertyRegistry registry) {
        registry.add("treasury.api.base-url",
                () -> "http://localhost:" + wireMockServer.port());
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Integration: store (cents) then retrieve with currency conversion")
    void storeThenRetrieveWithConversion_returnsConvertedAmount() throws Exception {
        // 1. Store — 20000 cents = $200.00
        CreateTransactionRequest createReq = new CreateTransactionRequest();
        createReq.setDescription("Office supplies");
        createReq.setTransactionDate(LocalDate.of(2024, 3, 15));
        createReq.setPurchaseAmountCents(20000L);

        MvcResult createResult = mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.purchaseAmountUsd").value(200.00))
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        // 2. Stub Treasury API
        wireMockServer.stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "data": [
                                    {
                                      "country_currency_desc": "Canada-Dollar",
                                      "exchange_rate": "1.3500",
                                      "record_date": "2024-03-01"
                                    }
                                  ]
                                }
                                """)));

        // 3. Retrieve with conversion: 200.00 * 1.35 = 270.00
        mockMvc.perform(get("/api/v1/transactions/{id}", id)
                        .param("currency", "Canada-Dollar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchaseAmountUsd").value(200.00))
                .andExpect(jsonPath("$.exchangeRate").value(1.35))
                .andExpect(jsonPath("$.convertedAmount").value(270.00))
                .andExpect(jsonPath("$.targetCurrency").value("Canada-Dollar"));
    }

    @Test
    @DisplayName("Integration: 1 cent stored as $0.01")
    void storeTransaction_oneCent_storedAsOneCentDollar() throws Exception {
        CreateTransactionRequest req = new CreateTransactionRequest();
        req.setDescription("Minimum purchase");
        req.setTransactionDate(LocalDate.now());
        req.setPurchaseAmountCents(1L);

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.purchaseAmountUsd").value(0.01));
    }

    // -------------------------------------------------------------------------
    // Validation failures
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Integration: description over 50 chars is rejected with 400")
    void storeTransaction_descriptionTooLong_returns400() throws Exception {
        CreateTransactionRequest req = new CreateTransactionRequest();
        req.setDescription("A".repeat(51));
        req.setTransactionDate(LocalDate.now());
        req.setPurchaseAmountCents(1000L);

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Integration: zero cents rejected with 400")
    void storeTransaction_zeroAmountCents_returns400() throws Exception {
        CreateTransactionRequest req = new CreateTransactionRequest();
        req.setDescription("Zero");
        req.setTransactionDate(LocalDate.now());
        req.setPurchaseAmountCents(0L);

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Integration: negative cents rejected with 400")
    void storeTransaction_negativeAmountCents_returns400() throws Exception {
        CreateTransactionRequest req = new CreateTransactionRequest();
        req.setDescription("Negative");
        req.setTransactionDate(LocalDate.now());
        req.setPurchaseAmountCents(-500L);

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Integration: invalid date format returns 400")
    void storeTransaction_invalidDateFormat_returns400() throws Exception {
        String body = """
                {
                  "description": "Test",
                  "transactionDate": "15/06/2024",
                  "purchaseAmountCents": 1000
                }
                """;

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // Currency conversion edge cases
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Integration: no exchange rate within 6 months returns 422")
    void retrieveTransaction_noRateWithinSixMonths_returns422() throws Exception {
        CreateTransactionRequest req = new CreateTransactionRequest();
        req.setDescription("Old purchase");
        req.setTransactionDate(LocalDate.of(2023, 1, 10));
        req.setPurchaseAmountCents(5000L);

        MvcResult createResult = mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        wireMockServer.stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\": []}")));

        mockMvc.perform(get("/api/v1/transactions/{id}", id)
                        .param("currency", "Canada-Dollar"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Integration: retrieve unknown transaction ID returns 404")
    void retrieveTransaction_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/{id}", "00000000-0000-0000-0000-000000000000")
                        .param("currency", "Canada-Dollar"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Integration: converted amount is rounded to two decimal places")
    void retrieveTransaction_convertedAmountRoundedToTwoDecimals() throws Exception {
        // 1000 cents = $10.00; 10.00 * 1.2345 = 12.345 → 12.35
        CreateTransactionRequest req = new CreateTransactionRequest();
        req.setDescription("Rounding test");
        req.setTransactionDate(LocalDate.of(2024, 6, 1));
        req.setPurchaseAmountCents(1000L);

        MvcResult createResult = mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        wireMockServer.stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "data": [
                                    {
                                      "country_currency_desc": "Some-Currency",
                                      "exchange_rate": "1.2345",
                                      "record_date": "2024-05-15"
                                    }
                                  ]
                                }
                                """)));

        mockMvc.perform(get("/api/v1/transactions/{id}", id)
                        .param("currency", "Some-Currency"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convertedAmount").value(12.35));
    }
}
