package dev.titis.incentive.partner;

import dev.titis.incentive.partner.dto.PartnerDtos;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PartnerValidationTest {
    @Test void normalizesBeforeValidation() {
        var input = new PartnerDtos.Create("  agt-001  ", "  Partner Satu  ");
        assertThat(input.code()).isEqualTo("AGT-001");
        assertThat(input.name()).isEqualTo("Partner Satu");
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(input)).isEmpty();
            assertThat(factory.getValidator().validate(new PartnerDtos.Create("!", " "))).isNotEmpty();
        }
    }
}