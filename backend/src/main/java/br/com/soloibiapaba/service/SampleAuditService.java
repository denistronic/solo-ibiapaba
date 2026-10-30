package br.com.soloibiapaba.service;
import br.com.soloibiapaba.dto.*;
import br.com.soloibiapaba.repository.SampleAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;
import java.util.Set;
@Service
public class SampleAuditService {
    private final SampleActorService actors;
    private final SampleAuditRepository events;
    public SampleAuditService(SampleActorService actors,SampleAuditRepository events) { this.actors=actors;this.events=events; }
    @Transactional
    public AuditPage query(AuditFilter f) {
        actors.requireAdmin();
        if(f.page()<0||f.page()>1000000||f.size()<1||f.size()>100)throw new IllegalArgumentException("Página deve ser de 0 a 1000000 e tamanho de 1 a 100.");
        if(f.from()!=null&&f.to()!=null&&f.from().isAfter(f.to()))throw new IllegalArgumentException("Período invertido.");
        return events.query(new AuditFilter(f.sampleId(),f.actorId(),choice(f.entityType(),Set.of("SAMPLE","ANALYSIS")),
            choice(f.action(),Set.of("CREATE","UPDATE")),f.from(),f.to(),f.page(),f.size()));
    }
    private String choice(String value,Set<String> allowed) {
        if(value==null)return null;
        var normal=value.strip().toUpperCase(Locale.ROOT);
        if(!allowed.contains(normal))throw new IllegalArgumentException("Filtro de auditoria inválido.");
        return normal;
    }
}
