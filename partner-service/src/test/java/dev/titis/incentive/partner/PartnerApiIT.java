package dev.titis.incentive.partner;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PartnerApiIT {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.11-alpine");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test void partnerLifecycleAndValidation() throws Exception {
        var body = mvc.perform(post("/api/v1/partners").contentType("application/json")
                .content("{\"code\":\" agt-001 \",\"name\":\" Partner Satu \"}"))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.code").value("AGT-001")).andReturn().getResponse().getContentAsString();
        String id = json.readTree(body).get("id").asText();
        mvc.perform(post("/api/v1/partners").contentType("application/json")
                .content("{\"code\":\"AGT-001\",\"name\":\"Duplicate\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DATA_CONFLICT"));
        mvc.perform(patch("/api/v1/partners/" + id + "/status").contentType("application/json")
                .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
        mvc.perform(get("/api/v1/partners/" + id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/partners?status=ACTIVE")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/partners?size=101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/partners/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/partners").contentType("application/json")
                .content("{\"code\":\"!\",\"name\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors").isArray());
    }
}