package br.com.soloibiapaba.repository;
import br.com.soloibiapaba.domain.SampleAuditEvent;
import br.com.soloibiapaba.dto.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Comparator;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;
/** Apenas perfil test; não comprova locks ou rollback PostgreSQL. */
@Repository
@Profile("test")
public class InMemorySampleAuditRepository implements SampleAuditRepository {
    private final CopyOnWriteArrayList<SampleAuditEvent> events=new CopyOnWriteArrayList<>();
    @Override public void append(SampleAuditEvent event) { events.add(event); }
    @Override public AuditPage query(AuditFilter f) {
        var result=events.stream().filter(e->f.sampleId()==null||f.sampleId().equals(e.sampleId()))
            .filter(e->f.actorId()==null||f.actorId().equals(e.actorUserId()))
            .filter(e->f.entityType()==null||f.entityType().equals(e.entityType()))
            .filter(e->f.action()==null||f.action().equals(e.action()))
            .filter(e->f.from()==null||!e.occurredAt().isBefore(f.from()))
            .filter(e->f.to()==null||!e.occurredAt().isAfter(f.to()))
            .sorted(Comparator.comparing(SampleAuditEvent::occurredAt).thenComparingLong(SampleAuditEvent::recordVersion).thenComparing(e->e.id().toString()).reversed()).toList();
        long offset=(long)f.page()*f.size();
        return new AuditPage(result.stream().skip(offset).limit(f.size()).toList(),f.page(),f.size(),result.size(),offset+f.size()<result.size());
    }
}
