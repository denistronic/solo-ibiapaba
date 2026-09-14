package br.com.soloibiapaba.dto;

import br.com.soloibiapaba.domain.Sample;
import br.com.soloibiapaba.domain.SoilAnalysis;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SampleResponse(
        UUID id,
        String code,
        String block,
        String plot,
        String row,
        String crop,
        LocalDate sampledAt,
        String temperature,
        String depth,
        Instant createdAt,
        SoilAnalysis analysis,
        Instant analysisUpdatedAt
) {
    public static SampleResponse from(Sample sample) {
        return new SampleResponse(
                sample.id(),
                sample.code(),
                sample.block(),
                sample.plot(),
                sample.row(),
                sample.crop(),
                sample.sampledAt(),
                sample.temperature() == null ? null : sample.temperature().stripTrailingZeros().toPlainString(),
                sample.depth(),
                sample.createdAt(),
                sample.analysis(),
                sample.analysisUpdatedAt()
        );
    }
}
