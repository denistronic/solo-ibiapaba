package br.com.soloibiapaba.repository;

import br.com.soloibiapaba.domain.Sample;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Repository
@Profile("test")
public class InMemorySampleRepository implements SampleRepository {

    private final ConcurrentMap<UUID, Sample> samples = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, AtomicInteger> sequencesByYear = new ConcurrentHashMap<>();

    @Override
    public List<Sample> findAll() {
        var result = new ArrayList<>(samples.values());
        result.sort(Comparator.comparing(Sample::createdAt).reversed());
        return List.copyOf(result);
    }

    @Override
    public Optional<Sample> findById(UUID id) {
        return Optional.ofNullable(samples.get(id));
    }

    @Override
    public Sample save(Sample sample) {
        samples.put(sample.id(), sample);
        return sample;
    }

    @Override
    public int nextSequenceForYear(int year) {
        return sequencesByYear.computeIfAbsent(year, ignored -> new AtomicInteger()).incrementAndGet();
    }
}
