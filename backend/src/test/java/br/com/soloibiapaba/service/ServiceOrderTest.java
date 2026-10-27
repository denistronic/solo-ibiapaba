package br.com.soloibiapaba.service;

import br.com.soloibiapaba.dto.CreateSampleRequest;
import br.com.soloibiapaba.dto.SampleResponse;
import br.com.soloibiapaba.repository.InMemorySampleRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

class ServiceOrderTest {
    private CreateSampleRequest request(String os) {
        return new CreateSampleRequest(os, "C", "Q12", "L08", "Acerola",
                LocalDate.of(2026, 10, 18), new BigDecimal("27"), "0–20 cm", null);
    }

    @Test void shouldRequireExactlyEightAsciiDigits() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(request("00123456"))).isEmpty();
            for (String invalid : new String[] {null, "", "1234567", "123456789", "1234ABCD", "12 34567", "１２３４５６７８"}) {
                assertThat(validator.validate(request(invalid))).isNotEmpty();
            }
        }
    }

    @Test void shouldPreserveOrderNumberThroughStorageResponseAndAnalysisUpdates() {
        var service = new SampleService(new InMemorySampleRepository());
        var sample = service.create(request("00123456"));
        assertThat(service.findById(sample.id()).serviceOrderNumber()).isEqualTo("00123456");
        assertThat(SampleResponse.from(sample).serviceOrderNumber()).isEqualTo("00123456");
        assertThat(sample.withAnalysis(null, sample.createdAt()).serviceOrderNumber()).isEqualTo("00123456");
        assertThat(sample.code()).startsWith("AMO-");
    }
}
