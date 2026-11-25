package dev.titis.incentive.ledger.client;

import dev.titis.incentive.ledger.exception.BusinessException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class PartnerClient {
    private final RestClient client;
    public PartnerClient(RestClient partnerRestClient) { this.client = partnerRestClient; }
    public Snapshot requireActive(UUID id) {
        Snapshot partner;
        try {
            partner = client.get().uri("/api/v1/partners/{id}", id).retrieve().body(Snapshot.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) throw new BusinessException(422, "PARTNER_NOT_FOUND", "Referenced partner does not exist");
            if (e.getStatusCode().is5xxServerError()) throw unavailable();
            throw invalidResponse();
        } catch (ResourceAccessException e) {
            throw unavailable();
        } catch (RestClientException e) {
            throw invalidResponse();
        }
        if (partner == null || !id.equals(partner.id()) || partner.code() == null
                || !partner.code().matches("[A-Z0-9_-]{3,30}")
                || !("ACTIVE".equals(partner.status()) || "INACTIVE".equals(partner.status()))) {
            throw invalidResponse();
        }
        if ("INACTIVE".equals(partner.status())) throw new BusinessException(422, "PARTNER_INACTIVE", "Partner is inactive");
        return partner;
    }
    private BusinessException unavailable() { return new BusinessException(503, "PARTNER_UNAVAILABLE", "Partner service is unavailable"); }
    private BusinessException invalidResponse() { return new BusinessException(502, "PARTNER_INVALID_RESPONSE", "Partner service returned an unexpected response"); }
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record Snapshot(UUID id, String code, String status) { }
}