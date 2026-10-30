package br.com.soloibiapaba.domain;
import java.time.Instant;
import java.util.UUID;
public record SampleTracking(long recordVersion, UUID createdBy, UUID updatedBy, Instant updatedAt,
        UUID analysisCreatedBy, Instant analysisCreatedAt, UUID analysisUpdatedBy) {
    public static SampleTracking unknown() { return new SampleTracking(0,null,null,null,null,null,null); }
    public SampleTracking created(UUID actor) { return new SampleTracking(1,actor,null,null,null,null,null); }
    public SampleTracking details(UUID actor, Instant at) { return new SampleTracking(Math.incrementExact(recordVersion),createdBy,actor,at,analysisCreatedBy,analysisCreatedAt,analysisUpdatedBy); }
    public SampleTracking analysis(UUID actor, Instant at, boolean first) { return new SampleTracking(Math.incrementExact(recordVersion),createdBy,updatedBy,updatedAt,first?actor:analysisCreatedBy,first?at:analysisCreatedAt,actor); }
}
