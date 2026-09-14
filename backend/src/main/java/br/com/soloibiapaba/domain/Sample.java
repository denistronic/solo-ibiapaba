package br.com.soloibiapaba.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record Sample(
        UUID id,
        String code,
        String block,
        String plot,
        String row,
        String crop,
        LocalDate sampledAt,
        BigDecimal temperature,
        String depth,
        Instant createdAt,
        SoilAnalysis analysis,
        Instant analysisUpdatedAt
) {
    public Sample withAnalysis(SoilAnalysis newAnalysis, Instant updatedAt) {
        return new Sample(
                id,
                code,
                block,
                plot,
                row,
                crop,
                sampledAt,
                temperature,
                depth,
                createdAt,
                newAnalysis,
                updatedAt
        );
    }
}
