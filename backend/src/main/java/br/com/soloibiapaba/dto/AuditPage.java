package br.com.soloibiapaba.dto;
import br.com.soloibiapaba.domain.SampleAuditEvent;
import java.util.List;
public record AuditPage(List<SampleAuditEvent> items,int page,int size,long total,boolean hasNext) {}
