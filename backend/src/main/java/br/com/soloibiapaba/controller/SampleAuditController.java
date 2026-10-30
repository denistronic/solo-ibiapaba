package br.com.soloibiapaba.controller;
import br.com.soloibiapaba.dto.*;
import br.com.soloibiapaba.service.SampleAuditService;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;
@RestController
@RequestMapping("/api/audit/events")
public class SampleAuditController {
    private final SampleAuditService service;
    public SampleAuditController(SampleAuditService service) { this.service=service; }
    @GetMapping public AuditPage list(@RequestParam(required=false) UUID sampleId,@RequestParam(required=false) UUID actorId,
        @RequestParam(required=false) String entityType,@RequestParam(required=false) String action,
        @RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.query(new AuditFilter(sampleId,actorId,entityType,action,from,to,page,size));
    }
}
