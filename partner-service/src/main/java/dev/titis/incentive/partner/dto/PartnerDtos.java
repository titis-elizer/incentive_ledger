package dev.titis.incentive.partner.dto;

import dev.titis.incentive.partner.persistence.Partner;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public final class PartnerDtos {
    private PartnerDtos() { }
    public record Create(@NotBlank @Pattern(regexp = "[A-Z0-9_-]{3,30}") String code,
                         @NotBlank @Size(min = 2, max = 120) String name) {
        public Create {
            code = code == null ? null : code.trim().toUpperCase(Locale.ROOT);
            name = name == null ? null : name.trim();
        }
    }
    public record ChangeStatus(@NotNull Partner.Status status) { }
    public record View(UUID id, String code, String name, Partner.Status status, Instant createdAt, Instant updatedAt) {
        public static View from(Partner p) {
            return new View(p.getId(), p.getCode(), p.getName(), p.getStatus(), p.getCreatedAt(), p.getUpdatedAt());
        }
    }
}