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
import java.util.UUID;

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
        registry.add("treasury.api.base-url", () -> "http://localhost:" + wireMockServer.port());
    }

    // -------------------------------------------------------------------------
    // Idempotency
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Integration: duplicate Idempotency-Key returns 200 with original, no duplicate stored")
    void duplicateIdempotencyKey_returns200AndOriginalTransaction() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        String body = objectMapper.writeValueAsString(buildRequest("Duplicate test", LocalDate.now(), 5000L));

        // First request → 201
        MvcResult first = mockMvc.perform(post("/api/v1/transactions")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();

        // Second request with same key → 200 with same id
        MvcResult second = mockMvc.perform(post("/api/v1/transactions")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        String secondId = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText();

        org.assertj.core.api.Assertions.assertThat(firstId).isEqualTo(secondId);
    }

    @Test
    @DisplayName("Integration: different Idempotency-Keys create separate transactions")
    void differentIdempotencyKeys_createSeparateTransactions() throws Exception {
        String body = objectMapper.writeValueAsString(buildRequest("Same data", LocalDate.now(), 1000L));

        MvcResult first = mockMvc.perform(post("/api/v1/transactions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult second = mockMvc.perform(post("/api/v1/transactions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();
        String secondId = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText();

        org.assertj.core.api.Assertions.assertThat(firstId).isNotEqualTo(secondId);
    }

    @Test
    @DisplayName("Integration: missing Idempotency-Key header returns 400")
    void missingIdempotencyKey_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("No key", LocalDate.now(), 1000L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Integration: store then retrieve with currency conversion")
    void storeThenRetrieveWithConversion_returnsConvertedAmount() throws Exception {
        CreateTransactionRequest req = buildRequest("Office supplies", LocalDate.of(2024, 3, 15), 20000L);

        MvcResult createResult = mockMvc.perform(post("/api/v1/transactions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.purchaseAmountUsd").value(200.00))
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        wireMockServer.stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "data": [{
                                    "country_currency_desc": "Canada-Dollar",
                                    "exchange_rate": "1.3500",
                                    "record_date": "2024-03-01"
                                  }]
                                }
                                """)));

        mockMvc.perform(get("/api/v1/transactions/{id}", id)
                        .param("currency", "Canada-Dollar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convertedAmount").value(270.00))
                .andExpect(jsonPath("$.exchangeRate").value(1.35));
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Integration: description over 50 chars returns 400")
    void storeTransaction_descriptionTooLong_returns400() throws Exception {
        CreateTransactionRequest req = buildRequest("A".repeat(51), LocalDate.now(), 1000L);

        mockMvc.perform(post("/api/v1/transactions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Integration: zero cents allowed — stored as $0.00")
    void storeTransaction_zeroAmountCents_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Free item", LocalDate.now(), 0L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.purchaseAmountUsd").value(0.00));
    }

    @Test
    @DisplayName("Integration: negative cents returns 400")
    void storeTransaction_negativeAmountCents_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Negative", LocalDate.now(), -500L))))
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
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // Currency edge cases
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Integration: no exchange rate within 6 months returns 422")
    void retrieveTransaction_noRateWithinSixMonths_returns422() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/transactions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Old purchase", LocalDate.of(2023, 1, 10), 5000L))))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        wireMockServer.stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\": []}")));

        mockMvc.perform(get("/api/v1/transactions/{id}", id)
                        .param("currency", "Canada-Dollar"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Integration: unknown transaction ID returns 404")
    void retrieveTransaction_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/{id}", "00000000-0000-0000-0000-000000000000")
                        .param("currency", "Canada-Dollar"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Integration: converted amount rounded to two decimal places")
    void retrieveTransaction_convertedAmountRoundedCorrectly() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/transactions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("Rounding test", LocalDate.of(2024, 6, 1), 1000L))))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        wireMockServer.stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"data": [{"country_currency_desc": "X", "exchange_rate": "1.2345", "record_date": "2024-05-15"}]}
                                """)));

        // 10.00 * 1.2345 = 12.345 → 12.35
        mockMvc.perform(get("/api/v1/transactions/{id}", id).param("currency", "X"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convertedAmount").value(12.35));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private CreateTransactionRequest buildRequest(String description, LocalDate date, Long amountCents) {
        CreateTransactionRequest req = new CreateTransactionRequest();
        req.setDescription(description);
        req.setTransactionDate(date);
        req.setPurchaseAmountCents(amountCents);
        return req;
    }
}
