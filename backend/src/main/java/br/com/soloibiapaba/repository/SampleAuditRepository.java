package br.com.soloibiapaba.repository;
import br.com.soloibiapaba.domain.SampleAuditEvent;
import br.com.soloibiapaba.dto.*;
public interface SampleAuditRepository {
    void append(SampleAuditEvent event);
    AuditPage query(AuditFilter filter);
}
