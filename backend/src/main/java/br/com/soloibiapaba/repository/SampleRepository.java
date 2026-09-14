package br.com.soloibiapaba.repository;

import br.com.soloibiapaba.domain.Sample;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SampleRepository {

    List<Sample> findAll();

    Optional<Sample> findById(UUID id);

    Sample save(Sample sample);

    int nextSequenceForYear(int year);
}
