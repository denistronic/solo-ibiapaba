package br.com.soloibiapaba.service;

import br.com.soloibiapaba.dto.CreateSampleRequest;
import br.com.soloibiapaba.dto.SampleResponse;
import br.com.soloibiapaba.repository.InMemorySampleRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class SampleObservationsTest {
    private CreateSampleRequest request(String observations) {
        return new CreateSampleRequest("00123456", "C", "Q12", "L08", "Acerola",
                LocalDate.of(2026, 10, 26), new BigDecimal("27"), "0–20 cm", observations);
    }

    @Test void shouldAcceptOptionalObservationsAndRejectMoreThan250Characters() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String text : new String[]{null, "", "Amostra úmida", "a".repeat(250)}) {
                assertThat(validator.validate(request(text))).isEmpty();
            }
            assertThat(validator.validate(request("a".repeat(251))))
                    .anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("observations"));
        }
    }

    @Test void shouldRetainObservationsInSavedSampleResponseAndAnalysisUpdates() {
        var service = new SampleService(new InMemorySampleRepository());
        String text = "Solo úmido após chuva.\nColeta próxima à irrigação.";
        var sample = service.create(request("  " + text + "  "));
        assertThat(service.findById(sample.id()).observations()).isEqualTo(text);
        assertThat(SampleResponse.from(sample).observations()).isEqualTo(text);
        assertThat(sample.withAnalysis(null, sample.createdAt()).observations()).isEqualTo(text);
        assertThat(service.create(request(null)).observations()).isNull();
    }
}
