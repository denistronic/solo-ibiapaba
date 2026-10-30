package br.com.soloibiapaba.service;

import br.com.soloibiapaba.dto.*;
import br.com.soloibiapaba.domain.SoilAnalysis;
import br.com.soloibiapaba.repository.InMemorySampleRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

class LegacyAnalysisEditTest {
    private final JsonMapper json=JsonMapper.builder().build();
    private SoilAnalysis edit(String original) {
        var repository=new InMemorySampleRepository();
        var service=new SampleService(repository);
        var sample=service.create(new CreateSampleRequest("00123456","B","Q","L","Café",LocalDate.of(2026,10,29),new BigDecimal("25"),"0-20",null));
        repository.save(sample.withAnalysis(json.readValue(original,SoilAnalysis.class),Instant.now()));
        return service.updateAnalysis(sample.id(),json.readValue("{\"organicMatter\":\"25.1234567890\"}",SoilAnalysisRequest.class)).analysis();
    }
    @Test void shouldKeepHistoricalWaterPhWhenEditingOtherResultsWithoutCaCl2() {
        var edited=edit("{\"phWater\":5.6}");
        assertThat(edited.phWater()).isEqualByComparingTo("5.6");
        assertThat(edited.phCaCl2()).isNull();
        assertThat(edited.organicMatter()).isEqualByComparingTo("25.1234567890");
    }
    @Test void shouldClearCalculatedWaterPhWhenPreviousCaCl2IsExplicitlyCleared() {
        assertThat(edit("{\"phWater\":5.6,\"phCaCl2\":5.0}").phWater()).isNull();
    }
}
