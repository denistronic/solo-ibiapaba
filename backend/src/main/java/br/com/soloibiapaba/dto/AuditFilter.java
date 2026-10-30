package br.com.soloibiapaba.dto;
import java.time.Instant;
import java.util.UUID;
public record AuditFilter(UUID sampleId,UUID actorId,String entityType,String action,Instant from,Instant to,int page,int size) {}
