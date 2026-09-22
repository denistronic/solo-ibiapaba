package br.com.soloibiapaba.service;

import br.com.soloibiapaba.domain.Sample;
import br.com.soloibiapaba.domain.SoilAnalysis;
import br.com.soloibiapaba.dto.CreateSampleRequest;
import br.com.soloibiapaba.dto.SoilAnalysisRequest;
import br.com.soloibiapaba.exception.SampleNotFoundException;
import br.com.soloibiapaba.repository.SampleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class SampleService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Fortaleza");

    private final SampleRepository repository;
    private final Clock clock;

    public SampleService(SampleRepository repository) {
        this.repository = repository;
        this.clock = Clock.system(BUSINESS_ZONE);
    }

    public List<Sample> findAll() {
        return repository.findAll();
    }

    public Sample findById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new SampleNotFoundException(id));
    }

    @Transactional
    public Sample create(CreateSampleRequest request) {
        int year = clock.instant().atZone(BUSINESS_ZONE).getYear();
        int sequence = repository.nextSequenceForYear(year);
        String code = "AMO-%d-%04d".formatted(year, sequence);

        var sample = new Sample(
                UUID.randomUUID(),
                code,
                normalize(request.block()),
                normalize(request.plot()),
                normalize(request.row()),
                normalize(request.crop()),
                request.sampledAt(),
                request.temperature(),
                normalize(request.depth()),
                Instant.now(clock),
                null,
                null
        );

        return repository.save(sample);
    }

    @Transactional
    public Sample updateAnalysis(UUID id, SoilAnalysisRequest request) {
        Sample current = findById(id);
        SoilAnalysis analysis = toDomain(request);
        Sample updated = current.withAnalysis(analysis, Instant.now(clock));
        return repository.save(updated);
    }

    private SoilAnalysis toDomain(SoilAnalysisRequest request) {
        return new SoilAnalysis(
                request.phWater(),
                request.phCaCl2(),
                request.organicMatter(),
                request.phosphorus(),
                request.potassium(),
                request.sodium(),
                request.calcium(),
                request.magnesium(),
                request.aluminum(),
                request.hAl(),
                request.sulfur(),
                request.boron(),
                request.copper(),
                request.iron(),
                request.manganese(),
                request.zinc(),
                request.cecReported(),
                request.baseSatReported(),
                request.clay(),
                request.sand(),
                request.silt(),
                request.ec()
        );
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
