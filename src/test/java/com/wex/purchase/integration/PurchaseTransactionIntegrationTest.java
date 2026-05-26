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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class PurchaseTransactionIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("purchasedb")
            .withUsername("wex_user")
            .withPassword("wex_pass")
            .withReuse(true);

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
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("treasury.api.base-url",
                () -> "http://localhost:" + wireMockServer.port());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Idempotency
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Integration: duplicate Idempotency-Key returns 200 with original, no duplicate stored")
    void duplicateIdempotencyKey_returns200AndOriginalTransaction() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = objectMapper.writeValueAsString(
                buildRequest("Duplicate test", LocalDateTime.now(), 5000L));

        MvcResult first = mockMvc.perform(post("/api/v1/transactions")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();

        String firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();

        MvcResult second = mockMvc.perform(post("/api/v1/transactions")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();

        String secondId = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText();
        assertThat(firstId).isEqualTo(secondId);
    }

    @Test
    @DisplayName("Integration: different Idempotency-Keys create separate transactions")
    void differentIdempotencyKeys_createSeparateTransactions() throws Exception {
        String body = objectMapper.writeValueAsString(
                buildRequest("Same data", LocalDateTime.now(), 1000L));

        MvcResult first = mockMvc.perform(post("/api/v1/transactions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();

        MvcResult second = mockMvc.perform(post("/api/v1/transactions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();

        String firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();
        String secondId = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText();
        assertThat(firstId).isNotEqualTo(secondId);
    }

    @Test
    @DisplayName("Integration: missing Idempotency-Key header returns 400")
    void missingIdempotencyKey_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildRequest("No key", LocalDateTime.now(), 1000L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Happy path
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Integration: store then retrieve with currency conversion")
    void storeThenRetrieveWithConversion_returnsConvertedAmount() throws Exception {
        // transaction on 2024-03-15 at 09:15 — date portion used for Treasury API
        LocalDateTime txDateTime = LocalDateTime.of(2024, 3, 15, 9, 15, 0);

        MvcResult createResult = mockMvc.perform(post("/api/v1/transactions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildRequest("Office supplies", txDateTime, 20000L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.purchaseAmountUsd").value(200.00))
                .andExpect(jsonPath("$.transactionDate").value("2024-03-15T09:15:00"))
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

        // 200.00 * 1.35 = 270.00
        mockMvc.perform(get("/api/v1/transactions/{id}", id).param("currency", "Canada-Dollar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchaseAmountUsd").value(200.00))
                .andExpect(jsonPath("$.transactionDate").value("2024-03-15T09:15:00"))
                .andExpect(jsonPath("$.exchangeRate").value(1.35))
                .andExpect(jsonPath("$.convertedAmount").value(270.00));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Validation
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Integration: description over 50 chars returns 400")
    void storeTransaction_descriptionTooLong_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildRequest("A".repeat(51), LocalDateTime.now(), 1000L))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Integration: zero cents allowed — stored as $0.00")
    void storeTransaction_zeroAmountCents_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildRequest("Free item", LocalDateTime.now(), 0L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.purchaseAmountUsd").value(0.00));
    }

    @Test
    @DisplayName("Integration: negative cents returns 400")
    void storeTransaction_negativeAmountCents_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildRequest("Negative", LocalDateTime.now(), -500L))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Integration: date-only format rejected — datetime required")
    void storeTransaction_dateOnlyFormat_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Test",
                                  "transactionDate": "2024-06-15",
                                  "purchaseAmountCents": 1000
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Currency conversion edge cases
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Integration: no exchange rate within 6 months returns 422")
    void retrieveTransaction_noRateWithinSixMonths_returns422() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/transactions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildRequest("Old purchase", LocalDateTime.of(2023, 1, 10, 12, 0), 5000L))))
                .andExpect(status().isCreated()).andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        wireMockServer.stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\": []}")));

        mockMvc.perform(get("/api/v1/transactions/{id}", id).param("currency", "Canada-Dollar"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").exists());
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
                        .content(objectMapper.writeValueAsString(
                                buildRequest("Rounding test", LocalDateTime.of(2024, 6, 1, 10, 0), 1000L))))
                .andExpect(status().isCreated()).andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        wireMockServer.stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"data": [{"country_currency_desc": "X",
                                           "exchange_rate": "1.2345",
                                           "record_date": "2024-05-15"}]}
                                """)));

        // 10.00 * 1.2345 = 12.345 → 12.35
        mockMvc.perform(get("/api/v1/transactions/{id}", id).param("currency", "X"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convertedAmount").value(12.35));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private CreateTransactionRequest buildRequest(String description, LocalDateTime dateTime, Long amountCents) {
        CreateTransactionRequest req = new CreateTransactionRequest();
        req.setDescription(description);
        req.setTransactionDate(dateTime);
        req.setPurchaseAmountCents(amountCents);
        return req;
    }
}
