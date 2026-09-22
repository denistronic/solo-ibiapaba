package br.com.soloibiapaba.repository;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresSampleRepositoryTest {

    @Test
    void shouldConvertInstantToJdbcTimestampWithoutChangingTheMoment() {
        Instant instant = Instant.parse("2026-09-22T17:20:26.356Z");

        var timestamp = PostgresSampleRepository.toSqlTimestamp(instant);

        assertThat(timestamp).isNotNull();
        assertThat(timestamp.toInstant()).isEqualTo(instant);
    }

    @Test
    void shouldKeepNullTimestampForAnalysisWithoutReport() {
        assertThat(PostgresSampleRepository.toSqlTimestamp(null)).isNull();
    }
}
