package dev.titis.incentive.ledger;

import dev.titis.incentive.ledger.client.PartnerClient;
import dev.titis.incentive.ledger.exception.BusinessException;
import java.time.*;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SalesApiIT {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.11-alpine");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PartnerClient partners;
    @MockitoBean Clock clock;
    private final UUID partnerId = UUID.randomUUID();

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE settlement_entries, settlement_runs, sales CASCADE");
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Jakarta"));
        when(clock.instant()).thenReturn(Instant.parse("2026-11-25T10:00:00Z"));
        when(partners.requireActive(partnerId)).thenReturn(new PartnerClient.Snapshot(partnerId, "AGT-001", "ACTIVE"));
    }
    @Test void createsListsVoidsAndRejectsDuplicate() throws Exception {
        var body = mvc.perform(post("/api/v1/sales").contentType("application/json").content(sale(" ref-1 ", "1.25", "2026-10-15")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.externalReference").value("ref-1"))
                .andExpect(jsonPath("$.partnerCode").value("AGT-001")).andExpect(jsonPath("$.period").value("2026-10"))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(body).get("id").asText();
        mvc.perform(post("/api/v1/sales").contentType("application/json").content(sale("ref-1", "1.25", "2026-10-15")))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/sales/" + id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/sales?period=2026-10&status=VALID&partnerId=" + partnerId))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(patch("/api/v1/sales/" + id + "/void").contentType("application/json").content("{\"reason\":\" Cancelled sale \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VOID"))
                .andExpect(jsonPath("$.voidReason").value("Cancelled sale")).andExpect(jsonPath("$.voidedAt").exists());
        mvc.perform(patch("/api/v1/sales/" + id + "/void").contentType("application/json").content("{\"reason\":\"Cancelled sale\"}"))
                .andExpect(status().isConflict());
    }
    @Test void rejectsBadInputsAndPartnerBusinessFailures() throws Exception {
        for (String amount : new String[]{"0", "-1", "1.001", "10000000000000000.00"}) {
            mvc.perform(post("/api/v1/sales").contentType("application/json").content(sale("bad", amount, "2026-10-15")))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/sales").contentType("application/json").content(sale("future", "1", "2026-11-26")))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/sales?period=2026-13")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/sales?size=0")).andExpect(status().isBadRequest());
        when(partners.requireActive(partnerId)).thenThrow(new BusinessException(422, "PARTNER_INACTIVE", "Inactive"));
        mvc.perform(post("/api/v1/sales").contentType("application/json").content(sale("inactive", "1", "2026-10-15")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("PARTNER_INACTIVE"));
        doThrow(new BusinessException(422, "PARTNER_NOT_FOUND", "Not found")).when(partners).requireActive(partnerId);
        mvc.perform(post("/api/v1/sales").contentType("application/json").content(sale("missing", "1", "2026-10-15")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("PARTNER_NOT_FOUND"));
    }
    private String sale(String ref, String amount, String date) {
        return "{\"externalReference\":\"" + ref + "\",\"partnerId\":\"" + partnerId + "\",\"saleDate\":\"" + date + "\",\"amount\":" + amount + "}";
    }
}