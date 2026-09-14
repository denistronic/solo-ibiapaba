package br.com.soloibiapaba.repository;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySampleRepositoryTest {

    @Test
    void shouldKeepIndependentSequencesPerYear() {
        var repository = new InMemorySampleRepository();

        assertThat(repository.nextSequenceForYear(2026)).isEqualTo(1);
        assertThat(repository.nextSequenceForYear(2026)).isEqualTo(2);
        assertThat(repository.nextSequenceForYear(2027)).isEqualTo(1);
    }
}
