package br.com.soloibiapaba.domain;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
public record SampleAuditEvent(UUID id, UUID sampleId, String entityType, String action, long recordVersion,
        UUID actorUserId, String actorName, String actorEmail, String actorRole, Instant occurredAt,
        JsonNode beforeData, JsonNode afterData) {}
